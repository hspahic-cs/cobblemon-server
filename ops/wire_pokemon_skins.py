#!/usr/bin/env python3
"""Wire the custom NPC battle skins onto every live gym / battle-tower trainer.

Copies the 24 source PNGs into the cobblemon-npc jar assets, then repoints the
`textureResource` field of each live trainer JSON (in BOTH the server-gyms
datapack and the cobblemon-npc mod source) at its skin:

  - 18 type skins  -> gym_NN_<type>, bt_NN_<type>, bt_NN_<type>_challenge
  - 6 named skins  -> gym_19_oak / 20_alder / 21_cynthia / 22_ash / 23_lance / 24_n

Idempotent: re-running just re-copies and re-points to the same targets.
"""
import re
import shutil
from pathlib import Path

SRC_SKINS = Path.home() / "Desktop" / "PokemonSkins"
ASSETS = Path(
    "custom-mods/cobblemon-npc/src/main/resources/assets/rctmod/textures/trainers/single"
)
TRAINER_DIRS = [
    Path("modpack/server-overrides/datapacks/server-gyms/data/rctmod/trainers"),
    Path("custom-mods/cobblemon-npc/src/main/resources/data/rctmod/trainers"),
]

TYPES = {
    "bug", "dark", "dragon", "electric", "fairy", "fighting", "fire", "flying",
    "ghost", "grass", "ground", "ice", "normal", "poison", "psychic", "rock",
    "steel", "water",
}

# Named trainers: trainer-id stem -> source PNG on the Desktop.
NAMED = {
    "gym_19_oak": "prof-oak.png",
    "gym_20_alder": "alder.png",
    "gym_21_cynthia": "cynthia.png",
    "gym_22_ash": "ash.png",
    "gym_23_lance": "lance.png",
    "gym_24_n": "champion-n.png",
}

TEX_RE = re.compile(r'("textureResource":\s*)"[^"]*"')


def asset_name(stem: str) -> str | None:
    """Return the bundled texture filename for a trainer stem, or None to skip."""
    core = stem[:-10] if stem.endswith("_challenge") else stem
    if core in NAMED:
        return f"{core}.png"
    m = re.match(r"(?:gym|bt)_\d+_(\w+)$", core)
    if m and m.group(1) in TYPES:
        return f"type_{m.group(1)}.png"
    return None


def copy_skins() -> None:
    ASSETS.mkdir(parents=True, exist_ok=True)
    for t in sorted(TYPES):
        shutil.copyfile(SRC_SKINS / f"{t}.png", ASSETS / f"type_{t}.png")
    for stem, src in NAMED.items():
        shutil.copyfile(SRC_SKINS / src, ASSETS / f"{stem}.png")
    print(f"copied {len(TYPES) + len(NAMED)} skins -> {ASSETS}")


def wire() -> None:
    for d in TRAINER_DIRS:
        changed = skipped = 0
        for fp in sorted(d.glob("*.json")):
            tex = asset_name(fp.stem)
            if tex is None:
                skipped += 1
                continue
            loc = f"rctmod:textures/trainers/single/{tex}"
            src = fp.read_text(encoding="utf-8")
            new, n = TEX_RE.subn(rf'\1"{loc}"', src, count=1)
            if n and new != src:
                fp.write_text(new, encoding="utf-8")
                changed += 1
        print(f"{d}: wired {changed}, skipped {skipped}")


def main() -> int:
    copy_skins()
    wire()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
