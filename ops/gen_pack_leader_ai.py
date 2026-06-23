#!/usr/bin/env python3
"""
Generate `server-pack-leader-ai`: override the bdsp/radicalred/unbound pack gym
leaders + Elite Four to use the `pe` (poke-engine) AI, matching our custom gyms.

The pack gym leaders (gym_leader_*, leader_*, elite_four_*) are bundled in the
rctmod jar with `ai: {"type": "rct", ...}` (the basic built-in AI), and they're
not in the repo (only their mob/spawn files are). To put them on the same smart
poke-engine AI our custom gym leaders use, we ship a datapack that re-emits each
leader's full jar definition with the `ai` block swapped to `pe`.

Scope: leaders only (the wild `hl_` filler stays on the lightweight in-process
`rb` AI, so poke-engine load is limited to boss fights). The set is derived from
the repo mob files: trainers in a pack series (bdsp/radicalred/unbound) named
gym_leader_* / leader_* / elite_four_*, excluding our custom building gyms.

Run from repo root (needs the rctmod jar):
    python3 ops/gen_pack_leader_ai.py [path/to/rctmod.jar]
"""
from __future__ import annotations

import json
import re
import sys
import zipfile
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
OUT = REPO / "modpack/server-overrides/datapacks/server-pack-leader-ai"
PACK_SERIES = {"bdsp", "radicalred", "unbound"}
PE_AI = {"type": "pe", "data": {"temperature": 0.5}}


def is_custom(stem: str) -> bool:
    return bool(re.match(r"gym_(0[1-9]|1[0-9]|2[0-4])_", stem)) or stem.startswith("bt_")


def is_pack_leader(stem: str) -> bool:
    return stem.startswith(("gym_leader_", "leader_", "elite_four_")) and not is_custom(stem)


def main() -> None:
    jar_path = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("/tmp/rctmod.jar")
    jar = zipfile.ZipFile(jar_path)
    jar_names = set(jar.namelist())

    # Derive the leader set from repo mob files (they carry the series).
    ids = set()
    for mob in REPO.glob("modpack/server-overrides/datapacks/*/data/rctmod/mobs/trainers/single/*.json"):
        stem = mob.stem
        if not is_pack_leader(stem):
            continue
        series = json.loads(mob.read_text()).get("series") or []
        if any(s in PACK_SERIES for s in series):
            ids.add(stem)

    out_dir = OUT / "data/rctmod/trainers"
    out_dir.mkdir(parents=True, exist_ok=True)
    (OUT / "pack.mcmeta").write_text(json.dumps({
        "pack": {
            "description": "Switch the bdsp/radicalred/unbound pack gym leaders + E4 to the pe "
                           "(poke-engine) AI, matching our custom gyms. See ops/gen_pack_leader_ai.py.",
            "pack_format": 48,
        }
    }, indent=2) + "\n")

    written, missing = 0, []
    for tid in sorted(ids):
        entry = f"data/rctmod/trainers/{tid}.json"
        if entry not in jar_names:
            missing.append(tid)
            continue
        d = json.loads(jar.read(entry))
        d["ai"] = dict(PE_AI)
        (out_dir / f"{tid}.json").write_text(json.dumps(d, indent=2) + "\n")
        written += 1

    print(f"wrote {written} pe-AI leader overrides to {out_dir}")
    if missing:
        print("NOT IN JAR (skipped):")
        for m in missing:
            print(f"  - {m}")


if __name__ == "__main__":
    main()
