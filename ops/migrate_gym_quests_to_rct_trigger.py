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
# (gym_06_roxie + gym_06_electric) and defeating EITHER counts as gym 6.
# Trainer id list sourced from modpack/server-overrides/datapacks/server-gyms/data/rctmod/trainers/
# (the repo's authoritative gym definitions — earlier mistakenly thought this lived only on
# the server; it's in the repo, just under server-overrides/datapacks/).
GYM_TRAINERS: dict[int, list[str]] = {
    1: ["gym_01_ground"],
    2: ["gym_02_grass"],
    3: ["gym_03_fighting"],
    4: ["gym_04_steel"],
    5: ["gym_05_fire"],
    6: ["gym_06_roxie", "gym_06_electric"],
    7: ["gym_07_water"],
    8: ["gym_08_psychic"],
    9: ["gym_09_dragon"],
    10: ["gym_10_ghost"],
    11: ["gym_11_bug"],
    12: ["gym_12_normal"],
    13: ["gym_13_poison"],
    14: ["gym_14_rock"],
    15: ["gym_15_flying"],
    16: ["gym_16_ice"],
    17: ["gym_17_fairy"],
    18: ["gym_18_dark"],
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


# 0.7.29 — the `eco give` insertion was removed entirely. The 0.7.26 migration that put
# `/eco give @s <bounty>` into each gym mcfunction caused all 28 gym reward functions to
# fail loading silently — NeoEssentials' /eco command isn't registered at datapack
# function-load time, so brigadier rejected the parse and the WHOLE function file went
# unloaded (no chat, no key, no bounty for gym wins). Bounty payment moved to the new
# Kotlin AdvancementHook (subscribes to NeoForge AdvancementEarnEvent for
# server:beat_gym_N + server:defeat_elite_four — fires from Kotlin where the eco bridge
# is available). This function now ONLY strips the stale `eco give` line if a prior
# 0.7.26-vintage mcfunction is still on disk.
BOUNTY_MARKER = "# 0.7.25 — gym bounty paid here via /eco give"


def update_mcfunction(gym_id: int, challenge: bool) -> None:
    """Strip any 0.7.26-vintage `/eco give @s <bounty>` insert from the gym mcfunction.
    Idempotent — safe to re-run on already-clean files."""
    fname = f"beat_gym_{gym_id}{'_challenge' if challenge else ''}.mcfunction"
    path = REWARDS_DIR / fname
    if not path.exists():
        print(f"  SKIP {fname} (not found)")
        return
    text = path.read_text()
    new_text = re.sub(
        rf"(?m)^{re.escape(BOUNTY_MARKER)}\n^eco give @s \d+\n",
        "",
        text,
    )
    if new_text != text:
        path.write_text(new_text)
        print(f"  stripped eco give from {fname}")


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
