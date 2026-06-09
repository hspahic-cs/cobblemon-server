#!/usr/bin/env python3
"""Replace assetless gym species with same-type alternates that have models in
Cobblemon 1.7.3. Preserves IVs/EVs/nature/level/heldItem; swaps species + ability
+ moveset to ones legal on the new species."""
import json
import sys
from pathlib import Path

ROOT = Path("modpack/server-overrides/datapacks/server-gyms/data/rctmod/trainers")

# (file_stem, old_species, new_species, new_ability, new_moveset)
SWAPS = [
    ("gym_03_fighting", "pancham", "timburr", "ironfist",
        ["drainpunch", "machpunch", "rockslide", "bulkup"]),
    ("gym_03_fighting_challenge", "pancham", "timburr", "ironfist",
        ["drainpunch", "machpunch", "rockslide", "bulkup"]),
    ("gym_04_steel", "pawniard", "lairon", "rockhead",
        ["ironhead", "rockslide", "earthquake", "stealthrock"]),
    ("gym_04_steel_challenge", "pawniard", "lairon", "rockhead",
        ["ironhead", "rockslide", "earthquake", "stealthrock"]),
    ("gym_11_bug", "vikavolt", "galvantula", "compoundeyes",
        ["thunder", "bugbuzz", "stickyweb", "thunderwave"]),
    ("gym_18_dark", "lokix", "absol", "superluck",
        ["nightslash", "psychocut", "suckerpunch", "swordsdance"]),
    ("gym_21_cynthia", "bisharp", "tyranitar", "sandstream",
        ["stoneedge", "crunch", "earthquake", "dragondance"]),
]


def swap_one(file_stem: str, old: str, new: str, ability: str, moveset: list[str]) -> bool:
    fp = ROOT / f"{file_stem}.json"
    data = json.loads(fp.read_text(encoding="utf-8"))
    found = False
    for entry in data.get("team", []):
        if entry.get("species") == old:
            entry["species"] = new
            entry["ability"] = ability
            entry["moveset"] = moveset
            found = True
            break
    if not found:
        print(f"  WARN: {file_stem}.json — no {old} found")
        return False
    fp.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
    return True


def main() -> int:
    changed = 0
    for stem, old, new, ability, moveset in SWAPS:
        if swap_one(stem, old, new, ability, moveset):
            print(f"  {stem}.json: {old} → {new} ({ability}, {moveset})")
            changed += 1
    print(f"\nswapped {changed}/{len(SWAPS)} files")
    return 0


if __name__ == "__main__":
    sys.exit(main())
