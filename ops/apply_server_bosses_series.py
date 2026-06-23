#!/usr/bin/env python3
"""
Give the custom RCT boss trainers a progression chain, and surface them as a
`server_bosses` series WITHOUT removing them from the bdsp/radicalred/unbound
packs.

Key point about RCT's data model: a trainer with NO `series` field belongs to
EVERY series (`isOfSeries` is true when the list is empty). So we leave the boss
trainers series-less — they show up in all the pack paths (the "old system") AND
in `server_bosses` (which, being defined, renders a boss-only view since the 80
pack wild-trainers DO carry an explicit series and are excluded from it).

`requiredDefeats` in RCT is global, not per-series, so this one chain is shared by
every series the trainer appears in (that's the accepted tradeoff for keeping the
bosses shared rather than duplicating 55 entries):
  - gyms 1-10: linear, each requires the previous.
  - Elite Four (Alder, Cynthia, Ash, Lance): each requires only gym 10 -> beatable
    in ANY order.
  - Champion (N): requires all four Elite Four members.

Series-local initialLevelCap=15 (and the global cap is also lowered to 15 in
rctmod-server.toml) so the progress graph shows real trainer levels instead of the
old global 200. allowOverLeveling still lets players level past the cap freely.

Idempotent. Skips legacy gym_11..gym_19 and the native bdsp gym_leader_* overrides.
Battle-tower entries (bt_*, incl. bt_19/bt_20) are matched by prefix, so new tower
leaders are picked up automatically.

Run from repo root:
    python3 ops/apply_server_bosses_series.py
"""
from __future__ import annotations

import copy
import json
import os
from pathlib import Path

DATAPACK = Path("modpack/server-overrides/datapacks/server-gyms/data/rctmod")
SERIES_ID = "server_bosses"

# Main progression: linear, each requires the previous (always-accessible once unlocked).
PROGRESSION = [
    "gym_01_ground", "gym_02_grass", "gym_03_fighting", "gym_04_steel",
    "gym_05_fire", "gym_06_electric", "gym_07_water", "gym_08_psychic",
    "gym_09_dragon", "gym_10_ghost",
]
LAST_GYM = PROGRESSION[-1]
# Elite Four: each gated on gym 10 only -> beatable in any order.
E4_MEMBERS = ["gym_20_alder", "gym_21_cynthia", "gym_22_ash", "gym_23_lance"]
# Champion: requires all four Elite Four members defeated.
CHAMPION = "gym_24_n"

# requiredDefeats is List[Set] = AND of ORs. A single-option OR per clause means
# "this exact trainer"; multiple clauses mean "all of them" (the N champion gate).
CHAIN_REQ = {}
for _i, _tid in enumerate(PROGRESSION):
    CHAIN_REQ[_tid] = [] if _i == 0 else [[PROGRESSION[_i - 1]]]
for _tid in E4_MEMBERS:
    CHAIN_REQ[_tid] = [[LAST_GYM]]
CHAIN_REQ[CHAMPION] = [[m] for m in E4_MEMBERS]


def write(path: Path, data) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n")
    print(f"  {path.relative_to(DATAPACK.parents[2])}")


def write_series() -> None:
    write(DATAPACK / "series" / f"{SERIES_ID}.json", {
        "title": {"literal": "Server Bosses"},
        "description": {"literal": "Gym Leaders, the Battle Tower, and the Elite Four."},
        "difficulty": 8,
        "requiredSeries": [],
        "initialLevelCap": 15,
    })


def classify(name: str):
    """(should_edit, required_defeats, optional) for a mob filename stem."""
    if name in CHAIN_REQ:
        return True, CHAIN_REQ[name], False           # gyms 1-10 + E4: core, ordered
    if name.startswith("bt_"):
        return True, [], True                          # battle tower (+challenge): optional, no prereq
    return False, None, None                            # legacy gym_11-19, gym_leader_*: skip


def main() -> None:
    mobs = DATAPACK / "mobs" / "trainers" / "single"
    if not mobs.is_dir():
        raise SystemExit(f"not found: {mobs} (run from repo root)")

    write_series()
    edited = skipped = 0
    for fp in sorted(mobs.glob("*.json")):
        do, req, optional = classify(fp.stem)
        if not do:
            skipped += 1
            continue
        d = json.loads(fp.read_text())
        # No `series` field -> trainer belongs to every series (pack paths + server_bosses).
        # Undoes #251's `series: [server_bosses]`, which had pulled them out of the packs.
        d.pop("series", None)
        d["requiredDefeats"] = req
        d["optional"] = optional
        fp.write_text(json.dumps(d, indent=2) + "\n")
        edited += 1
    print(f"  edited {edited} mob files, skipped {skipped} (legacy + bdsp leaders)")


if __name__ == "__main__":
    main()
