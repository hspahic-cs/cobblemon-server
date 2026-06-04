#!/usr/bin/env python3
"""Generate `server-gym-ai-test` datapack — head-to-head rb vs pe AI test.

Sources Sabrina, Surge, Blaine hardmode teams from the live `server-gyms` datapack,
clamps every Pokemon's level to 50, and emits 2 trainer JSONs per leader (one rb,
one pe). Each per-leader spawn places both side-by-side so a play-tester can
fight one then the other and compare directly.

Run from repo root:
    python3 ops/gen_gym_ai_test_datapack.py

Output:
    modpack/server-overrides/datapacks/server-gym-ai-test/data/rctmod/trainers/
    modpack/server-overrides/datapacks/server-gym-ai-test/data/server/function/aitest/
"""

import json
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
PACK_DIR = REPO / "modpack/server-overrides/datapacks/server-gym-ai-test"
GYMS_IN = REPO / "modpack/server-overrides/datapacks/server-gyms/data/rctmod/trainers"
TRAINERS_OUT = PACK_DIR / "data/rctmod/trainers"
FUNCTIONS_OUT = PACK_DIR / "data/server/function/aitest"

LEVEL_CAP = 50

# (source_file, leader_short_name, display_name)
LEADERS = [
    ("gym_08_sabrina_challenge.json", "sabrina", "Sabrina"),
    ("gym_13_lt_surge.json", "surge", "Surge"),
    ("gym_05_blaine_challenge.json", "blaine", "Blaine"),
]

# Head-to-head matchup: rb (current prod) vs pe (poke-engine bridge).
# rb registered by rbrctai 0.15.3 (RunBunAI). pe registered by cobblemon-poke-ai
# (this repo; routes to the local poke-engine bridge service).
VARIANTS = [
    ("rb", "rb"),
    ("pe", "pe"),
]


def write_trainer_jsons():
    TRAINERS_OUT.mkdir(parents=True, exist_ok=True)
    for src_name, short, display in LEADERS:
        src = json.loads((GYMS_IN / src_name).read_text())
        for label, ai_type in VARIANTS:
            out = json.loads(json.dumps(src))  # deep copy
            out["name"] = f"AI Test [{label}]: {display}"
            out["ai"] = {"type": ai_type, "data": {}}
            for mon in out.get("team", []):
                if mon.get("level", 0) != LEVEL_CAP:
                    mon["level"] = LEVEL_CAP
            dest = TRAINERS_OUT / f"aitest_{short}_{label}.json"
            dest.write_text(json.dumps(out, indent=2) + "\n")
            print(f"wrote {dest.relative_to(REPO)}")


def write_spawn_function(short: str, display: str):
    """Spawn rb + pe of the same leader 3 blocks apart."""
    rb_id = f"aitest_{short}_rb"
    pe_id = f"aitest_{short}_pe"
    body = f"""# Spawn rb + pe variants of {display} side-by-side at caller's position.
# rb at caller, pe 3 blocks east. Run /function server:aitest/cleanup to remove.

# Clear any stale {display} aitest trainers first.
kill @e[type=rctmod:trainer,tag=aitest,tag=aitest.leader.{short}]

# {display} [rb]
execute at @s run rctmod trainer summon_persistent {rb_id}
execute at @s run tag @e[type=rctmod:trainer,distance=..10,limit=1,sort=nearest,nbt={{TrainerId:"{rb_id}"}}] add aitest
execute at @s run tag @e[type=rctmod:trainer,distance=..10,limit=1,sort=nearest,nbt={{TrainerId:"{rb_id}"}}] add aitest.leader.{short}
execute at @s run tag @e[type=rctmod:trainer,distance=..10,limit=1,sort=nearest,nbt={{TrainerId:"{rb_id}"}}] add aitest.variant.rb
execute at @s run data merge entity @e[type=rctmod:trainer,distance=..10,limit=1,sort=nearest,nbt={{TrainerId:"{rb_id}"}}] {{Invulnerable:1b,PersistenceRequired:1b}}

# {display} [pe] — 3 blocks east
execute at @s positioned ^3 ^ ^ run rctmod trainer summon_persistent {pe_id}
execute at @s run tag @e[type=rctmod:trainer,distance=..10,limit=1,sort=nearest,nbt={{TrainerId:"{pe_id}"}}] add aitest
execute at @s run tag @e[type=rctmod:trainer,distance=..10,limit=1,sort=nearest,nbt={{TrainerId:"{pe_id}"}}] add aitest.leader.{short}
execute at @s run tag @e[type=rctmod:trainer,distance=..10,limit=1,sort=nearest,nbt={{TrainerId:"{pe_id}"}}] add aitest.variant.pe
execute at @s run data merge entity @e[type=rctmod:trainer,distance=..10,limit=1,sort=nearest,nbt={{TrainerId:"{pe_id}"}}] {{Invulnerable:1b,PersistenceRequired:1b}}

tellraw @s [{{"text":"§a✓ Spawned {display}: [rb] vs [pe]","bold":true}},{{"text":"\\n§7Cleanup: §f/function server:aitest/cleanup"}}]
"""
    dest = FUNCTIONS_OUT / f"spawn_{short}.mcfunction"
    dest.write_text(body)
    print(f"wrote {dest.relative_to(REPO)}")


def write_cleanup_function():
    body = """# Remove all AI test trainers (every leader, every variant).
kill @e[type=rctmod:trainer,tag=aitest]
tellraw @s [{"text":"§a✓ Cleared all AI test trainers","bold":true}]
"""
    dest = FUNCTIONS_OUT / "cleanup.mcfunction"
    dest.write_text(body)
    print(f"wrote {dest.relative_to(REPO)}")


def remove_stale_old_files():
    """Drop the pre-rb-vs-pe outputs so no orphans linger if someone bisects."""
    for old in TRAINERS_OUT.glob("aitest_*_a.json"):
        old.unlink()
        print(f"removed {old.relative_to(REPO)}")
    for old in TRAINERS_OUT.glob("aitest_*_b.json"):
        old.unlink()
        print(f"removed {old.relative_to(REPO)}")
    for old in TRAINERS_OUT.glob("aitest_*_c.json"):
        old.unlink()
        print(f"removed {old.relative_to(REPO)}")
    for old_name in ("spawn_a.mcfunction", "spawn_b.mcfunction", "spawn_c.mcfunction"):
        old = FUNCTIONS_OUT / old_name
        if old.exists():
            old.unlink()
            print(f"removed {old.relative_to(REPO)}")


def main():
    FUNCTIONS_OUT.mkdir(parents=True, exist_ok=True)
    remove_stale_old_files()
    write_trainer_jsons()
    for _, short, display in LEADERS:
        write_spawn_function(short, display)
    write_cleanup_function()


if __name__ == "__main__":
    main()
