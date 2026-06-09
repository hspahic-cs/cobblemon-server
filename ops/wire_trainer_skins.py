#!/usr/bin/env python3
"""Insert a `textureResource` field into each named gym-trainer JSON pointing
at a bundled RCT skin. Inserted between `identity` and `ai` to keep the file
ordering consistent across the directory."""
import re
from pathlib import Path

ROOT = Path("modpack/server-overrides/datapacks/server-gyms/data/rctmod/trainers")

# Gym basename → RCT texture filename (within textures/trainers/single/).
SKINS: dict[str, str] = {
    "gym_02_grass":     "gym_leader_gardenia_03d6.png",
    "gym_04_steel":        "gym_leader_byron_0399.png",
    "gym_05_fire":       "leader_blaine_01a3.png",
    "gym_07_water": "gym_leader_wake_03d7.png",
    "gym_08_psychic":      "leader_sabrina_01a4.png",
    "gym_10_ghost":        "leader_morty_001c.png",
    "gym_13_poison":     "leader_koga_01a2.png",
    "gym_19_oak":          "prof_prof_oak_01ff.png",
    "gym_20_lorelei":      "elite_four_lorelei_004d.png",
    "gym_21_cynthia":      "champion_cynthia_03a5.png",
    "gym_22_agatha":       "elite_four_agatha_0053.png",
    "gym_23_lance":        "champion_lance_0027.png",
}

# Inject after the "identity" line, before "ai".
PATTERN = re.compile(r'("identity":\s*"[^"]*",)\n(\s*)("ai":)')


def process(path: Path, skin_file: str) -> bool:
    src = path.read_text(encoding="utf-8")
    if '"textureResource"' in src:
        return False
    resource_loc = f"rctmod:textures/trainers/single/{skin_file}"
    replacement = (
        rf'\1\n\2"textureResource": "{resource_loc}",\n\2\3'
    )
    new_src, n = PATTERN.subn(replacement, src, count=1)
    if n == 0:
        return False
    path.write_text(new_src, encoding="utf-8")
    return True


def main() -> int:
    total = 0
    changed = 0
    for stem, skin_file in sorted(SKINS.items()):
        for variant in (stem, f"{stem}_challenge"):
            fp = ROOT / f"{variant}.json"
            if not fp.exists():
                print(f"  missing: {fp.name}")
                continue
            total += 1
            if process(fp, skin_file):
                changed += 1
                print(f"  wired: {fp.name} → {skin_file}")
    print(f"\nwired {changed}/{total} trainer JSONs")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
