#!/usr/bin/env python3
"""
Generate the `server-loot-nerf` datapack: overrides of RCT's generic wild-trainer
loot tables to retune drop rarity and strip abusable items.

Changes:
  - type gems (incl. dragon_gem)  Uncommon/Rare -> Legendary
  - mint leaves                   Uncommon      -> Epic
  - remove exp candies (epic m/l, legendary xl), sniffer_egg, ancient_origin_ball,
    the whole legendary diverse set, and master_ball (all sources).

Reads the ORIGINAL tables from an extracted RCT 0.18.1 jar (rctmod-neoforge-
1.21.1-0.18.1-beta.jar -> data/rctmod/loot_table). Point RCT_LOOT_SRC at that dir:

    RCT_LOOT_SRC=/path/to/rctmod/data/rctmod/loot_table \\
        python3 ops/build_loot_nerf.py

Run from repo root. Re-run after an RCT version bump to keep the overrides in sync.
"""
from __future__ import annotations

import copy
import json
import os
from pathlib import Path

REF = Path(os.environ.get(
    "RCT_LOOT_SRC",
    # local extracted-reference default; override via RCT_LOOT_SRC for reproducibility
    "/Users/scorpio/Repos/personal/cobblemon-mods/cobblemon-server/reference/rctmod"
    "/common/src/main/resources/data/rctmod/loot_table",
))
OUT = Path("modpack/server-overrides/datapacks/server-loot-nerf/data/rctmod/loot_table")


def load(rel):
    return json.loads((REF / rel).read_text())

def entries(t):
    return t["pools"][0]["entries"]

def named(es, *suffixes):
    return [e for e in es if any(e.get("name", "").endswith(s) for s in suffixes)]

def without(es, names):
    return [e for e in es if e.get("name") not in names]

def write(rel, t):
    p = OUT / rel
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(json.dumps(t, indent=2) + "\n")
    print(f"  {rel}")


def main():
    if not REF.is_dir():
        raise SystemExit(f"RCT loot source not found: {REF}\nset RCT_LOOT_SRC to an extracted rctmod 0.18.1 jar")

    TYPE_GEMS = named(entries(load("generic/uncommon/battle.json")), "_gem")          # 17 (uncommon)
    DRAGON_GEM = named(entries(load("generic/rare/battle.json")), "dragon_gem")       # 1  (rare)
    ALL_GEMS = TYPE_GEMS + DRAGON_GEM                                                  # -> legendary
    MINT_LEAVES = named(entries(load("generic/uncommon/nature.json")), "_mint_leaf")  # 6  -> epic
    print(f"moving {len(ALL_GEMS)} type gems -> legendary, {len(MINT_LEAVES)} mint leaves -> epic\n")

    # uncommon: strip gems / mint leaves
    t = load("generic/uncommon/battle.json")
    t["pools"][0]["entries"] = [e for e in entries(t) if not e.get("name", "").endswith("_gem")]
    write("generic/uncommon/battle.json", t)
    t = load("generic/uncommon/nature.json")
    t["pools"][0]["entries"] = [e for e in entries(t) if not e.get("name", "").endswith("_mint_leaf")]
    write("generic/uncommon/nature.json", t)

    # rare: strip dragon_gem (-> legendary)
    t = load("generic/rare/battle.json")
    t["pools"][0]["entries"] = without(entries(t), {"cobblemon:dragon_gem"})
    write("generic/rare/battle.json", t)

    # epic: remove exp candies + sniffer egg; gain mint leaves
    t = load("generic/epic/medicine.json")
    t["pools"][0]["entries"] = without(entries(t), {"cobblemon:exp_candy_m", "cobblemon:exp_candy_l"})
    write("generic/epic/medicine.json", t)
    t = load("generic/epic/diverse.json")
    t["pools"][0]["entries"] = without(entries(t), {"minecraft:sniffer_egg"})
    write("generic/epic/diverse.json", t)
    t = load("generic/epic/nature.json")
    entries(t).extend(copy.deepcopy(MINT_LEAVES))
    write("generic/epic/nature.json", t)

    # legendary: remove xl candy / origin ball / diverse / masterball; gain type gems
    t = load("generic/legendary/medicine.json")
    t["pools"][0]["entries"] = without(entries(t), {"cobblemon:exp_candy_xl"})
    write("generic/legendary/medicine.json", t)
    t = load("generic/legendary/pokeballs.json")
    t["pools"][0]["entries"] = without(entries(t), {"cobblemon:ancient_origin_ball"})
    write("generic/legendary/pokeballs.json", t)
    t = load("generic/legendary/archeology.json")
    t["pools"][0]["entries"] = without(entries(t), {"cobblemon:ancient_origin_ball"})
    write("generic/legendary/archeology.json", t)
    t = load("generic/legendary/diverse.json")
    t["pools"] = []
    write("generic/legendary/diverse.json", t)
    t = load("generic/legendary/battle.json")
    entries(t).extend(copy.deepcopy(ALL_GEMS))
    write("generic/legendary/battle.json", t)
    t = load("generic/legendary/masterball.json")
    t["pools"] = []                                       # strips master_ball (boss Giovanni + unique/pokeballs)
    write("generic/legendary/masterball.json", t)

    mcmeta = OUT.parents[2] / "pack.mcmeta"
    mcmeta.write_text(json.dumps({"pack": {"pack_format": 48,
        "description": "Retunes RCT wild-trainer loot (gems->Legendary, mints->Epic, strips abusable items)."}}, indent=2) + "\n")
    print(f"  pack.mcmeta")


if __name__ == "__main__":
    main()
