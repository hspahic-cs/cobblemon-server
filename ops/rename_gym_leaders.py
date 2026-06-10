#!/usr/bin/env python3
"""Rename the 18 elemental gym leaders to the custom cast (bare names, no
'Gym Leader' prefix). Two passes:

  1. Trainer JSON `name` field, keyed by the type in the filename, across both
     the server-gyms datapack and the cobblemon-npc source, for gym_NN_<type>,
     bt_NN_<type> and the _challenge twins. (E4/Champion gym_19-24 untouched.)
  2. Quest display text in server-quests: swap each changing OLD canon name for
     its NEW name across spawn/delete/list/HUD/reward/advancement files.

`reach_income_*` is skipped in pass 2 ('grant' there is the gacha verb, not a
name). Gardenia (grass) and Koga (poison) keep their names; only the prefix is
dropped, handled by pass 1.
"""
import json
import re
from pathlib import Path

TYPE_NAME = {
    "ground": "Dusty", "grass": "Gardenia", "fighting": "Lee Sin", "steel": "Jarvis",
    "fire": "Zuko", "electric": "Stan", "water": "Kai", "psychic": "Juniper",
    "dragon": "Quinn", "ghost": "Grimm", "bug": "Flik", "normal": "Penny",
    "poison": "Koga", "rock": "Caesar", "flying": "Amos", "ice": "Tux",
    "fairy": "Flora", "dark": "Hobie",
}

# Old canon display name -> new (quest text). Gardenia/Koga unchanged so omitted.
OLD_NEW = {
    "Crasher Wake": "Kai",  # multi-word first
    "Clay": "Dusty", "Korrina": "Lee Sin", "Byron": "Jarvis", "Blaine": "Zuko",
    "Volkner": "Stan", "Sabrina": "Juniper", "Drayden": "Quinn", "Morty": "Grimm",
    "Viola": "Flik", "Cheren": "Penny", "Grant": "Caesar", "Skyla": "Amos",
    "Brycen": "Tux", "Valerie": "Flora", "Marnie": "Hobie",
}

TRAINER_DIRS = [
    Path("modpack/server-overrides/datapacks/server-gyms/data/rctmod/trainers"),
    Path("custom-mods/cobblemon-npc/src/main/resources/data/rctmod/trainers"),
]
QUESTS = Path("modpack/server-overrides/datapacks/server-quests/data/server")

NAME_RE = re.compile(r'("name":\s*)"[^"]*"')


def type_of(stem: str) -> str | None:
    core = stem[:-10] if stem.endswith("_challenge") else stem
    m = re.match(r"(?:gym|bt)_\d+_(\w+)$", core)
    return m.group(1) if m and m.group(1) in TYPE_NAME else None


def rename_trainers() -> None:
    for d in TRAINER_DIRS:
        changed = 0
        for fp in sorted(d.glob("*.json")):
            t = type_of(fp.stem)
            if t is None:
                continue
            src = fp.read_text(encoding="utf-8")
            new, n = NAME_RE.subn(rf'\1"{TYPE_NAME[t]}"', src, count=1)
            if n and new != src:
                fp.write_text(new, encoding="utf-8")
                changed += 1
        print(f"{d}: renamed {changed} trainer name fields")


def sweep_quest_text() -> None:
    patterns = [(re.compile(rf'\b{re.escape(old)}\b'), new) for old, new in OLD_NEW.items()]
    files = list(QUESTS.rglob("*.mcfunction")) + list(QUESTS.rglob("*.json"))
    touched = 0
    for fp in files:
        if fp.name.startswith("reach_income_"):
            continue
        src = fp.read_text(encoding="utf-8")
        new = src
        for rx, repl in patterns:
            new = rx.sub(repl, new)
        if new != src:
            fp.write_text(new, encoding="utf-8")
            touched += 1
    print(f"server-quests: updated text in {touched} files")


def main() -> int:
    rename_trainers()
    sweep_quest_text()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
