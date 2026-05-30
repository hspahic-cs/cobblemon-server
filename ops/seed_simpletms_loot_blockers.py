#!/usr/bin/env python3
"""Mirror every SimpleTMs injection loot table path under the
server-no-tm-loot datapack with an empty pool, neutralising the
mod's structure injections so TMs/TRs never appear in chest loot.

Pulls the path list from the live SimpleTMs jar on the dev VM so we
don't have to hand-maintain it.
"""
import json
import subprocess
import sys
from pathlib import Path

OUT_ROOT = Path("modpack/server-overrides/datapacks/server-no-tm-loot/data")
EMPTY = {"type": "minecraft:chest", "pools": []}


def list_injection_paths() -> list[str]:
    # SSH out, ask Python on the VM to enumerate.
    cmd = [
        "ssh", "cobblemon",
        "python3 -c \"import zipfile; z=zipfile.ZipFile('/opt/cobblemon-dev/mods/SimpleTMs-neoforge-2.3.3.jar'); "
        "print('\\n'.join(n for n in z.namelist() if 'loot_table/injection' in n and n.endswith('.json')))\"",
    ]
    out = subprocess.run(cmd, capture_output=True, text=True, check=True)
    return [line.strip() for line in out.stdout.splitlines() if line.strip()]


def main() -> int:
    paths = list_injection_paths()
    if not paths:
        print("no injection paths found", file=sys.stderr)
        return 1
    OUT_ROOT.mkdir(parents=True, exist_ok=True)
    written = 0
    for jar_path in paths:
        # jar_path looks like: data/simpletms/loot_table/injection/minecraft/chests/desert_pyramid.json
        # Output path mirrors that under our datapack's data/ dir.
        rel = jar_path[len("data/"):]
        out_path = OUT_ROOT / rel
        out_path.parent.mkdir(parents=True, exist_ok=True)
        out_path.write_text(json.dumps(EMPTY, indent=2) + "\n", encoding="utf-8")
        written += 1
    print(f"wrote {written} empty-pool overrides under {OUT_ROOT}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
