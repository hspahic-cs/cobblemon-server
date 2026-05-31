#!/usr/bin/env python3
"""Regenerate the server-spawn-nerfs datapack from AllTheMons.

The AllTheMons [R3.5] zip ships legendary + paradox spawn pools under
`data/special_spawns/spawn_pool_world/{legendary,paradox}/<species>.json`.
We override those at the same path with:

  - legendary: weight × 1/3 (rarer than upstream, bucket unchanged)
  - paradox:   weight unchanged, bucket promoted from "rare" to
               "ultra-rare" so they join legendaries + starters in the
               same rarity tier. Net effect: ~2.4× rarer than upstream
               (rare bucket rolls 0.5% of attempts, ultra-rare rolls
               0.2%) and per-attempt rate ends up ~1.6× rarer than
               post-nerf legendaries. The asymmetric path matters:
               legendaries have a LegendaryMonuments pedestal fallback,
               paradoxes have no alternate acquisition method, so
               keeping paradoxes slightly more common than legendaries
               in raw spawn rate produces roughly-equivalent practical
               acquisition difficulty.

Re-run after bumping AllTheMons to pick up new entries, or to change
the nerf approach (CATEGORIES below):

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

# Per-category overrides applied to every spawn entry in AllTheMons'
# data/special_spawns/spawn_pool_world/<category>/. `weight_factor`
# multiplies the entry's weight; `bucket` (when not None) replaces
# the entry's bucket. Tune to retarget the nerf.
CATEGORIES: dict[str, dict] = {
    "legendary": {"weight_factor": 1.0 / 3.0, "bucket": None},
    "paradox":   {"weight_factor": 1.0,       "bucket": "ultra-rare"},
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
    for cat in CATEGORIES:
        (data_dir / cat).mkdir(parents=True, exist_ok=True)

    (OUT_BASE / "pack.mcmeta").write_text(json.dumps(PACK_MCMETA, indent=2) + "\n")

    file_count = 0
    entry_count = 0
    with zipfile.ZipFile(ATM_ZIP_PATH) as z:
        for cat, cfg in CATEGORIES.items():
            factor = cfg["weight_factor"]
            new_bucket = cfg["bucket"]
            prefix = f"data/special_spawns/spawn_pool_world/{cat}/"
            for name in z.namelist():
                if not name.startswith(prefix) or not name.endswith(".json"):
                    continue
                d = json.loads(z.read(name).decode("utf-8"))
                for entry in d.get("spawns", []):
                    if factor != 1.0 and "weight" in entry:
                        entry["weight"] = round(entry["weight"] * factor, 4)
                    if new_bucket is not None:
                        entry["bucket"] = new_bucket
                pieces: list[str] = [f"Override of AllTheMons {cat} spawn pool"]
                if factor != 1.0:
                    pieces.append(
                        f"weight × {factor:.4f} (1/{int(round(1.0/factor))} of upstream)"
                    )
                if new_bucket is not None:
                    pieces.append(f"bucket promoted to {new_bucket!r}")
                d["_comment"] = " — ".join(pieces)
                out_name = Path(name).name
                (data_dir / cat / out_name).write_text(json.dumps(d, indent=2) + "\n")
                file_count += 1
                entry_count += len(d.get("spawns", []))

    print(f"Wrote {file_count} files / {entry_count} spawn entries to {OUT_BASE}")


if __name__ == "__main__":
    main()
