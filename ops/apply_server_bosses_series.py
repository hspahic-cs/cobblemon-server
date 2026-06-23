#!/usr/bin/env python3
"""
Wire the custom RCT boss trainers into a dedicated `server_bosses` series so they
stop polluting every other series' Trainer Card list, and give them a linear
progression chain for the card.

Series-local initialLevelCap=15 so the progress graph shows real trainer levels
(not the global 200) and the per-player cap tracks progression. allowOverLeveling
still lets players level past it freely.

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
# Elite Four + Champion: continues the chain after the 10 gyms.
E4 = ["gym_20_alder", "gym_21_cynthia", "gym_22_ash", "gym_23_lance", "gym_24_n"]
CHAIN = PROGRESSION + E4
CHAIN_REQ = {tid: ([] if i == 0 else [[CHAIN[i - 1]]]) for i, tid in enumerate(CHAIN)}


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
        d["series"] = [SERIES_ID]
        d["requiredDefeats"] = req
        d["optional"] = optional
        fp.write_text(json.dumps(d, indent=2) + "\n")
        edited += 1
    print(f"  edited {edited} mob files, skipped {skipped} (legacy + bdsp leaders)")


if __name__ == "__main__":
    main()
