#!/usr/bin/env python3
"""Regenerate the gym-trainer tables in docs/gym-trainers.md from the live RCTmod trainer JSON.

Source of truth: modpack/server-overrides/datapacks/server-gyms/data/rctmod/trainers/gym_*.json
The prose intro (everything before the first `???` collapsible) is preserved verbatim; only the
per-trainer tables are regenerated, so leader names / teams / abilities / items / moves stay in sync
with what's actually deployed.

Run: python3 ops/gen_gym_docs.py
"""
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TRAINERS = ROOT / "modpack/server-overrides/datapacks/server-gyms/data/rctmod/trainers"
DOC = ROOT / "docs/gym-trainers.md"


def fmt_word(s: str) -> str:
    return s[:1].upper() + s[1:] if s else s


def fmt_item(held: str | None) -> str:
    if not held:
        return ""
    name = held.split(":")[-1]
    return " ".join(w.capitalize() for w in name.split("_"))


def fmt_moves(moveset) -> str:
    return ", ".join(fmt_word(m) for m in (moveset or []))


def team_table(team) -> list[str]:
    lines = [
        "    | Lv | Pokémon | Ability | Item | Moves |",
        "    |---:|---|---|---|---|",
    ]
    for p in team:
        lvl = p.get("level", "")
        species = fmt_word(p.get("species", ""))
        ability = fmt_word(p.get("ability", ""))
        item = fmt_item(p.get("heldItem"))
        moves = fmt_moves(p.get("moveset"))
        lines.append(f"    | {lvl} | {species} | {ability} | {item} | {moves} |")
    return lines


def header_for(num: int, name: str, identity: str) -> str:
    if 1 <= num <= 10:
        return f'??? abstract "Gym {num}: {name}, {identity}-type"'
    if 11 <= num <= 19:
        return f'??? abstract "Gym {num} (optional rotating): {name}, {identity}-type"'
    if 20 <= num <= 23:
        clean = re.sub(r"^Elite Four ", "", name)
        return f'??? abstract "Elite Four {num - 19}: {clean}"'
    return f'??? abstract "Champion: {name}"'  # gym 24


def main() -> None:
    blocks = []
    for path in sorted(TRAINERS.glob("gym_*.json")):
        m = re.match(r"gym_(\d+)_(\w+)\.json", path.name)
        if not m:
            continue
        num = int(m.group(1))
        d = json.loads(path.read_text())
        name = d.get("name", path.stem)
        identity = d.get("identity", "")
        block = [header_for(num, name, identity), ""]
        block += team_table(d.get("team", []))
        block.append("")
        blocks.append((num, "\n".join(block)))

    blocks.sort(key=lambda x: x[0])
    generated = "\n".join(b for _, b in blocks)

    # Preserve everything before the first collapsible block (the prose intro + callouts).
    existing = DOC.read_text()
    idx = existing.index("??? abstract")
    intro = existing[:idx].rstrip() + "\n\n"
    DOC.write_text(intro + generated + "\n")
    print(f"Wrote {DOC} — {len(blocks)} trainer blocks regenerated.")


if __name__ == "__main__":
    main()
