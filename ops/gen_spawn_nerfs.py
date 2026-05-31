#!/usr/bin/env python3
"""Regenerate the server-spawn-nerfs datapack from AllTheMons.

The AllTheMons [R3.5] zip ships legendary + paradox spawn pools under
`data/special_spawns/spawn_pool_world/{legendary,paradox}/<species>.json`.
We override those at the same path with reduced weights so legendaries
spawn 1/3 as often and paradoxes spawn 1/5 as often as upstream.

Re-run this script after bumping AllTheMons to pick up any new entries
(e.g. a new legendary added in a future release) or to change the nerf
factors:

    python3 ops/gen_spawn_nerfs.py

Inputs:  modpack/server-overrides/datapacks/AllTheMons [R3.5].zip
Outputs: modpack/server-overrides/datapacks/server-spawn-nerfs/...

If the upstream zip is renamed (e.g. R3.6), update ATM_ZIP_PATH below.
"""
from __future__ import annotations

import json
import shutil
import zipfile
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
ATM_ZIP_PATH = REPO / "modpack/server-overrides/datapacks/AllTheMons [R3.5].zip"
OUT_BASE = REPO / "modpack/server-overrides/datapacks/server-spawn-nerfs"

# Tune these to adjust nerf magnitudes. Keys are category subdirectories
# under AllTheMons' data/special_spawns/spawn_pool_world/. Values are
# multiplicative weight factors (smaller = rarer).
FACTORS: dict[str, float] = {
    "legendary": 1.0 / 3.0,   # 1/3 of upstream weight
    "paradox":   1.0 / 5.0,   # 1/5 of upstream weight (more aggressive than legendaries)
}

PACK_MCMETA = {
    "pack": {
        "pack_format": 48,
        "description": "Reduce natural spawn rates for legendary + paradox Pokemon (overrides AllTheMons spawn pools).",
    }
}


def main() -> None:
    if not ATM_ZIP_PATH.exists():
        raise SystemExit(f"AllTheMons zip not found: {ATM_ZIP_PATH}")

    # Wipe the override dir so deleted upstream files don't linger as stale overrides.
    data_dir = OUT_BASE / "data/special_spawns/spawn_pool_world"
    if data_dir.exists():
        shutil.rmtree(data_dir)
    for cat in FACTORS:
        (data_dir / cat).mkdir(parents=True, exist_ok=True)

    (OUT_BASE / "pack.mcmeta").write_text(json.dumps(PACK_MCMETA, indent=2) + "\n")

    file_count = 0
    entry_count = 0
    with zipfile.ZipFile(ATM_ZIP_PATH) as z:
        for cat, factor in FACTORS.items():
            prefix = f"data/special_spawns/spawn_pool_world/{cat}/"
            for name in z.namelist():
                if not name.startswith(prefix) or not name.endswith(".json"):
                    continue
                d = json.loads(z.read(name).decode("utf-8"))
                for entry in d.get("spawns", []):
                    if "weight" in entry:
                        entry["weight"] = round(entry["weight"] * factor, 4)
                d["_comment"] = (
                    f"Override of AllTheMons {cat} spawn pool — weight scaled by "
                    f"{factor:.4f} (1/{int(round(1.0/factor))} of upstream)"
                )
                out_name = Path(name).name
                (data_dir / cat / out_name).write_text(json.dumps(d, indent=2) + "\n")
                file_count += 1
                entry_count += len(d.get("spawns", []))

    print(f"Wrote {file_count} files / {entry_count} spawn entries to {OUT_BASE}")


if __name__ == "__main__":
    main()
