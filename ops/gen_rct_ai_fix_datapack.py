#!/usr/bin/env python3
"""
0.7.29 — generate `server-rct-ai-fix` datapack that overrides every vanilla rctmod
trainer JSON to include `"ai": {"type": "rb", "data": {}}`.

WHY THIS EXISTS
================

Vanilla rctmod ships ~1559 trainer JSONs and ~1222 of them omit the `ai` field
entirely. RCT then runs those battles through Cobblemon's default trainer AI,
which (on this server, against Cobblemon 1.7.3 + Sinytra Connector) does not
emit `CobblemonEvents.BATTLE_VICTORY` reliably to subscribers in cobblemon-bridge.

Symptom: `GymDefeatHook.applyToVictory` never fires for non-gym battles, so
`payNpcBounty` never runs, so players don't get the per-defeat NPC bounty.
Confirmed by inspection of dev logs across multiple Titan1190X battles:
default-AI trainer fights produce ZERO `npc-defeat:` log lines, while
rb-AI gym fights (`gym_*_*.json` shipped with `ai: rb`) do route through
`rbrctai` and the BATTLE_VICTORY chain works there.

FIX
====

Override every vanilla trainer JSON missing an `ai` field with the same
`"ai": {"type": "rb", "data": {}}` block our gym JSONs use. Output goes to:

    modpack/server-overrides/datapacks/server-rct-ai-fix/data/rctmod/trainers/

Trainers that ALREADY have an `ai` field are skipped (don't disturb upstream
configs that opted into a specific AI). Idempotent: re-running regenerates
the same files byte-for-byte.

USAGE
======

Pull the rctmod jar locally first (so the script doesn't need internet):

    # On the dev VM (already extracted):
    scp sysadmin@192.168.1.20:/opt/cobblemon-dev/mods/rctmod-neoforge-*.jar /tmp/

    # Then from repo root:
    python3 ops/gen_rct_ai_fix_datapack.py /tmp/rctmod-neoforge-1.21.1-0.18.1-beta.jar

The script reports counts: total trainers seen, ai-missing (overridden),
ai-present (skipped), and any malformed JSONs (logged + skipped).
"""
from __future__ import annotations

import json
import shutil
import sys
import zipfile
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
DATAPACK_DIR = REPO / "modpack/server-overrides/datapacks/server-rct-ai-fix"
TRAINERS_OUT = DATAPACK_DIR / "data/rctmod/trainers"
PACK_MCMETA = DATAPACK_DIR / "pack.mcmeta"
README = DATAPACK_DIR / "README.md"

AI_BLOCK = {"type": "rb", "data": {}}

PACK_MCMETA_CONTENT = {
    "pack": {
        "description": (
            "Force every vanilla RCT trainer to use the rb AI so Cobblemon's "
            "BATTLE_VICTORY event fires correctly for non-gym trainer battles. "
            "See ops/gen_rct_ai_fix_datapack.py."
        ),
        "pack_format": 48,
    }
}

README_CONTENT = """# server-rct-ai-fix

Auto-generated datapack — **do not hand-edit**.

Overrides every vanilla rctmod trainer JSON that's missing an `ai` field, injecting
`"ai": {"type": "rb", "data": {}}` so the trainer routes through `rbrctai`. Without
this, the default Cobblemon trainer AI runs the battle but `BATTLE_VICTORY` doesn't
fire reliably on this server (Cobblemon 1.7.3 + Sinytra Connector), which silently
skips the NPC bounty payout in `GymDefeatHook`.

Regenerate from a fresh rctmod jar with:

```
python3 ops/gen_rct_ai_fix_datapack.py /path/to/rctmod-neoforge-*.jar
```

Trainers that already have an `ai` field set in the rctmod jar are NOT touched —
this datapack only overrides the ai-missing subset.
"""


def main(jar_path: Path) -> int:
    if not jar_path.exists():
        print(f"error: {jar_path} not found", file=sys.stderr)
        return 1

    if TRAINERS_OUT.exists():
        shutil.rmtree(TRAINERS_OUT)
    TRAINERS_OUT.mkdir(parents=True, exist_ok=True)

    PACK_MCMETA.write_text(json.dumps(PACK_MCMETA_CONTENT, indent=2) + "\n")
    README.write_text(README_CONTENT)

    seen = 0
    skipped_has_ai = 0
    skipped_malformed = 0
    written = 0

    with zipfile.ZipFile(jar_path) as zf:
        for name in zf.namelist():
            if not name.startswith("data/rctmod/trainers/") or not name.endswith(".json"):
                continue
            seen += 1
            try:
                data = json.loads(zf.read(name).decode("utf-8"))
            except (json.JSONDecodeError, UnicodeDecodeError) as e:
                print(f"  malformed: {name} ({e.__class__.__name__})", file=sys.stderr)
                skipped_malformed += 1
                continue

            if data.get("ai") is not None:
                skipped_has_ai += 1
                continue

            data["ai"] = AI_BLOCK
            stem = name[len("data/rctmod/trainers/"):]
            out = TRAINERS_OUT / stem
            out.parent.mkdir(parents=True, exist_ok=True)
            # Stable formatting — sorted keys + 2-space indent + trailing newline so re-runs
            # don't show spurious diffs.
            out.write_text(json.dumps(data, indent=2, sort_keys=True) + "\n")
            written += 1

    print(f"seen:               {seen}")
    print(f"skipped (has ai):   {skipped_has_ai}")
    print(f"skipped (malformed):{skipped_malformed}")
    print(f"written:            {written}")
    print(f"output:             {TRAINERS_OUT.relative_to(REPO)}")
    return 0


if __name__ == "__main__":
    if len(sys.argv) != 2:
        print(f"usage: {sys.argv[0]} <rctmod-jar-path>", file=sys.stderr)
        sys.exit(2)
    sys.exit(main(Path(sys.argv[1])))
