#!/usr/bin/env python3
"""Generate `server-gym-ai-test` datapack — 9 trainer JSONs for AI A/B/C testing.

Sources Sabrina, Surge, Blaine hardmode teams from the live `server-gyms` datapack,
clamps every Pokemon's level to 50, and emits 3 variants per leader (one per AI type).

Run from repo root:
    python3 ops/gen_gym_ai_test_datapack.py

Output: modpack/server-overrides/datapacks/server-gym-ai-test/data/rctmod/trainers/
"""

import json
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
GYMS_IN = REPO / "modpack/server-overrides/datapacks/server-gyms/data/rctmod/trainers"
OUT_DIR = REPO / "modpack/server-overrides/datapacks/server-gym-ai-test/data/rctmod/trainers"

LEVEL_CAP = 50

# (source_file, leader_short_name, display_name)
LEADERS = [
    ("gym_08_sabrina_challenge.json", "sabrina", "Sabrina"),
    ("gym_13_lt_surge.json", "surge", "Surge"),
    ("gym_05_blaine_challenge.json", "blaine", "Blaine"),
]

# (label, ai.type registered with rctapi's JTO.registerParser)
# Verified by decompiling rctapi-0.15.2 + rbrctai-0.15.3:
#   "rb"  -> RunBunAI       (rbrctai add-on; current production AI)
#   "cbl" -> StrongBattleAI  (rctapi; ships in Cobblemon's own battle.ai package)
#   "rct" -> RCTBattleAI     (rctapi default)
# Also registered but unused here: "sd5" -> SelfdotGen5AI (experimental gen-5-style).
VARIANTS = [
    ("a", "rb"),
    ("b", "cbl"),
    ("c", "sd5"),
]


def main():
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    for src_name, short, display in LEADERS:
        src = json.loads((GYMS_IN / src_name).read_text())
        for label, ai_type in VARIANTS:
            out = json.loads(json.dumps(src))  # deep copy
            out["name"] = f"AI Test [{label.upper()}]: {display}"
            out["ai"] = {"type": ai_type, "data": {}}
            for mon in out.get("team", []):
                if mon.get("level", 0) > LEVEL_CAP:
                    mon["level"] = LEVEL_CAP
                elif mon.get("level", 0) < LEVEL_CAP:
                    mon["level"] = LEVEL_CAP
            dest = OUT_DIR / f"aitest_{short}_{label}.json"
            dest.write_text(json.dumps(out, indent=2) + "\n")
            print(f"wrote {dest.relative_to(REPO)}")


if __name__ == "__main__":
    main()
