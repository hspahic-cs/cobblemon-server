#!/usr/bin/env python3
"""
Keep the custom RCT gym / E4 / battle-tower bosses OFF the trainer cards.

The bosses live in physical gym buildings (spawner blocks), have spawnWeightFactor=0
(never wild-spawn), and their progression is gated by cobblemon-bridge's advancement
system (GymPrereqHook) — not by RCT's series/requiredDefeats. So they don't need to be
part of any RCT series progression.

RCT draws a trainer on a series card only if it's part of that series' *progression
graph* — i.e. it has requiredDefeats links to other trainers (SeriesGraph.getRemaining
filters out "alone" nodes with no ancestors/successors). An earlier attempt wired these
bosses into a `server_bosses` series with a linear chain, which made them show up on
every pack card (bdsp/radicalred/unbound), because the bosses are series-less (= belong
to every series).

This strips that wiring back off: no `series`, no `requiredDefeats`, no `optional`. With
no progression links the bosses are "alone", so RCT excludes them from every card — while
they stay series-less (fightable from any series at the gym buildings) and weight-0.

Idempotent. Targets gyms 1-10, the Elite Four + champion (gym_20-24), and bt_* trainers;
skips legacy gym_11-19 and the native bdsp gym_leader_* overrides.

Run from repo root:
    python3 ops/keep_bosses_off_cards.py
"""
from __future__ import annotations

import json
from pathlib import Path

DATAPACK = Path("modpack/server-overrides/datapacks/server-gyms/data/rctmod")

# The custom server bosses: gyms 1-10 and the Elite Four + champion (gym_20-24).
# bt_* (battle tower, incl. challenge variants) are matched by prefix.
BOSS_GYMS = {
    "gym_01_ground", "gym_02_grass", "gym_03_fighting", "gym_04_steel",
    "gym_05_fire", "gym_06_electric", "gym_07_water", "gym_08_psychic",
    "gym_09_dragon", "gym_10_ghost",
    "gym_20_alder", "gym_21_cynthia", "gym_22_ash", "gym_23_lance", "gym_24_n",
}
STRIP_FIELDS = ("series", "requiredDefeats", "optional")


def is_boss(stem: str) -> bool:
    return stem in BOSS_GYMS or stem.startswith("bt_")


def main() -> None:
    mobs = DATAPACK / "mobs" / "trainers" / "single"
    if not mobs.is_dir():
        raise SystemExit(f"not found: {mobs} (run from repo root)")

    stripped = skipped = 0
    for fp in sorted(mobs.glob("*.json")):
        if not is_boss(fp.stem):
            skipped += 1
            continue
        d = json.loads(fp.read_text())
        if any(k in d for k in STRIP_FIELDS):
            for k in STRIP_FIELDS:
                d.pop(k, None)
            fp.write_text(json.dumps(d, indent=2) + "\n")
            stripped += 1
    print(f"  stripped card wiring from {stripped} boss files, skipped {skipped}")


if __name__ == "__main__":
    main()
