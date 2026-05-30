#!/usr/bin/env python3
"""
0.7.25 — migrate every gym advancement to use rctmod:defeat_count as the trigger
(the authoritative RCT-side defeat event) instead of relying on our cobblemon-bridge
GymDefeatHook + Cobblemon BATTLE_VICTORY chain. The reflection path was unreliable
across RCT upstream renames; the rctmod:defeat_count trigger is fired by RCT itself
on every trainer defeat and matches against the specific trainer_ids list.

Also moves the gym bounty payment from `GymDefeatHook.payGymBounty` (Kotlin) into
each beat_gym_*.mcfunction reward function using `/eco give @s <amount>`. This way
the bounty fires whenever the advancement awards — regardless of which code path
awarded it — eliminating the "advancement awarded but bounty missed" race.

Run from repo root:
    python3 ops/migrate_gym_quests_to_rct_trigger.py
"""
from __future__ import annotations

import json
import re
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
ADVANCEMENT_DIR = REPO / "modpack/server-overrides/datapacks/server-quests/data/server/advancement"
REWARDS_DIR = REPO / "modpack/server-overrides/datapacks/server-quests/data/server/function/quests/rewards"

# Gym → list of trainer ids. Most gyms have one trainer; gym 6 has two
# (gym_06_roxie + gym_06_volkner) and defeating EITHER counts as gym 6.
# Trainer id list sourced from modpack/server-overrides/datapacks/server-gyms/data/rctmod/trainers/
# (the repo's authoritative gym definitions — earlier mistakenly thought this lived only on
# the server; it's in the repo, just under server-overrides/datapacks/).
GYM_TRAINERS: dict[int, list[str]] = {
    1: ["gym_01_clay"],
    2: ["gym_02_gardenia"],
    3: ["gym_03_korrina"],
    4: ["gym_04_byron"],
    5: ["gym_05_blaine"],
    6: ["gym_06_roxie", "gym_06_volkner"],
    7: ["gym_07_crasher_wake"],
    8: ["gym_08_sabrina"],
    9: ["gym_09_drayden"],
    10: ["gym_10_morty"],
    11: ["gym_11_viola"],
    12: ["gym_12_cheren"],
    13: ["gym_13_lt_surge"],
    14: ["gym_14_grant"],
    15: ["gym_15_skyla"],
    16: ["gym_16_brycen"],
    17: ["gym_17_valerie"],
    18: ["gym_18_marnie"],
    19: ["gym_19_oak"],
    20: ["gym_20_lorelei"],
    21: ["gym_21_cynthia"],
    22: ["gym_22_agatha"],
    23: ["gym_23_lance"],
    24: ["gym_24_champion"],
}

# Per the GymDefeatHook code: bounty = 150 * gymId for gyms 1..24.
def bounty_for(gym_id: int) -> int:
    return 150 * gym_id

def has_challenge(gym_id: int) -> bool:
    # Challenge variants only exist for gyms 1..10 (confirmed via server data dir).
    return 1 <= gym_id <= 10


def update_advancement(gym_id: int, challenge: bool) -> None:
    """Replace minecraft:impossible with rctmod:defeat_count + trainer_ids."""
    fname = f"beat_gym_{gym_id}{'_challenge' if challenge else ''}.json"
    path = ADVANCEMENT_DIR / fname
    if not path.exists():
        print(f"  SKIP {fname} (not found)")
        return
    d = json.loads(path.read_text())
    base_ids = GYM_TRAINERS[gym_id]
    trainer_ids = [f"{tid}_challenge" if challenge else tid for tid in base_ids]
    # Preserve every other field; only swap criteria + clear requirements (single-criterion default).
    d["criteria"] = {
        "defeated": {
            "trigger": "rctmod:defeat_count",
            "conditions": {
                "count": 1,
                "trainer_ids": trainer_ids,
                "trainer_type": None,
            },
        },
    }
    d.pop("requirements", None)
    path.write_text(json.dumps(d, indent=2) + "\n")
    print(f"  wrote {fname:42s} trainers={trainer_ids}")


# Match a `tag @s add cq_reward_*` line so we can insert `eco give` right before it
# (we want the bounty to fire AFTER the tellraw but BEFORE the _finalize schedule).
TAG_LINE = re.compile(r"^tag @s add cq_reward_", re.MULTILINE)
BOUNTY_MARKER = "# 0.7.25 — gym bounty paid here via /eco give"


def update_mcfunction(gym_id: int, challenge: bool) -> None:
    """Insert `/eco give @s <bounty>` into the existing gym mcfunction (idempotent —
    re-runs replace any prior insert)."""
    fname = f"beat_gym_{gym_id}{'_challenge' if challenge else ''}.mcfunction"
    path = REWARDS_DIR / fname
    if not path.exists():
        print(f"  SKIP {fname} (not found)")
        return
    text = path.read_text()
    bounty = bounty_for(gym_id)
    # Strip any prior insert (idempotency).
    text = re.sub(
        rf"(?m)^{re.escape(BOUNTY_MARKER)}\n^eco give @s \d+\n",
        "",
        text,
    )
    insert = f"{BOUNTY_MARKER}\neco give @s {bounty}\n"
    new_text = TAG_LINE.sub(insert + r"\g<0>", text, count=1)
    if new_text == text:
        # No `tag @s add` line — must be a non-standard mcfunction. Append at end.
        new_text = text.rstrip() + "\n" + insert
    path.write_text(new_text)
    print(f"  wrote {fname:42s} bounty=${bounty}")


def main() -> None:
    print("Migrating gym advancements to rctmod:defeat_count + adding /eco give bounty…\n")
    for gym_id in sorted(GYM_TRAINERS.keys()):
        update_advancement(gym_id, challenge=False)
        update_mcfunction(gym_id, challenge=False)
        if has_challenge(gym_id):
            update_advancement(gym_id, challenge=True)
            update_mcfunction(gym_id, challenge=True)
    print(f"\nDone. {len(GYM_TRAINERS)} gyms + {sum(1 for g in GYM_TRAINERS if has_challenge(g))} challenge variants migrated.")


if __name__ == "__main__":
    main()
