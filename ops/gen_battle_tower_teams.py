#!/usr/bin/env python3
"""Generate the battle-tower NORMAL trainers from the challenge trainers.

The battle tower has its own `bt_`-prefixed trainers, decoupled from the overworld gyms
(`gym_NN_type`). The CHALLENGE trainers `bt_NN_type_challenge` are the hand-maintained source
(ShepskyDad's competitive L50 team, `pe` AI, fought at a flat-L50 cap). This script derives the
NORMAL-track trainer from each:

  - `bt_NN_type` — same team as `bt_NN_type_challenge` but `ai → rb` (Run & Bun) and no cap (the
    tower applies the cap only to the challenge track). Easier: weaker AI + the player can out-level.

For each it writes the trainer to the server-gyms datapack + cobblemon-npc resources (skin), and
copies the challenge's mob config + loot table. Run from anywhere; idempotent:

    python3 ops/gen_battle_tower_teams.py
"""
from __future__ import annotations
import glob
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GYMS = os.path.join(ROOT, "modpack/server-overrides/datapacks/server-gyms/data/rctmod")
NPC = os.path.join(ROOT, "custom-mods/cobblemon-npc/src/main/resources/data/rctmod")
RB_AI = {"type": "rb", "data": {}}


def write_json(path: str, obj: dict) -> None:
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def copy_json(src: str, dst: str) -> None:
    if os.path.exists(src):
        write_json(dst, json.load(open(src)))


def main() -> None:
    count = 0
    for trainer_path in sorted(glob.glob(os.path.join(GYMS, "trainers", "bt_*_challenge.json"))):
        bt_challenge = os.path.basename(trainer_path)[: -len(".json")]    # bt_16_ice_challenge
        bt_normal = bt_challenge[: -len("_challenge")]                    # bt_16_ice

        normal = json.load(open(trainer_path))
        normal["ai"] = dict(RB_AI)                                        # pe -> rb; team/texture kept

        # trainer (datapack + npc/skins)
        for d in (GYMS, NPC):
            write_json(os.path.join(d, "trainers", f"{bt_normal}.json"), normal)

        # mob config + loot table (datapack only) — copy the challenge variant's verbatim
        for sub in ("mobs/trainers/single", "loot_table/trainers/single"):
            src = os.path.join(GYMS, sub, f"{bt_challenge}.json")
            copy_json(src, os.path.join(GYMS, sub, f"{bt_normal}.json"))

        count += 1
        print(f"  {bt_normal} (rb)  <- {bt_challenge}")
    print(f"generated {count} battle-tower normal trainers")


if __name__ == "__main__":
    main()
