#!/usr/bin/env python3
"""Spawn-rate hotfix datapack — narrow scope.

Three changes only:

  1. Slash ultra-rare bucket roll % to 1/3 of upstream (0.2 → 0.0667).
     Reduces ALL ultra-rare encounters globally by ~3×. Single
     buckets.json override.

  2. Promote paradox spawn entries from `rare` bucket → `ultra-rare`
     (weight unchanged from AllTheMons upstream). Without this, the
     paradox per-attempt rate drops only with the bucket slash; with
     this, they also share the (now smaller) ultra-rare bucket
     alongside legendaries.

  3. Biome-specific filler for biomes whose ultra-rare bucket is
     dominated by legendaries with no (or only pseudo-legend) non-
     competitive species. Adds thematic species — promoted from
     lower buckets in the same biome — at weights calibrated so the
     existing competitive species end up roughly 20% of the bucket
     instead of 100%.

A full per-tier per-biome calibration was scoped to a separate WIP PR.
This is a hotfix.

Inputs:
  /tmp/cobblemon-pools/*.json (extracted Cobblemon-base spawn pools)
  modpack/server-overrides/datapacks/AllTheMons [R3.5].zip

Output:
  modpack/server-overrides/datapacks/server-spawn-nerfs/
"""
from __future__ import annotations

import json
import shutil
import zipfile
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
COBBLEMON_POOLS = Path("/tmp/cobblemon-pools")
ATM_ZIP = REPO / "modpack/server-overrides/datapacks/AllTheMons [R3.5].zip"
OUT_BASE = REPO / "modpack/server-overrides/datapacks/server-spawn-nerfs"

# Ultra-rare bucket roll % after the slash. Upstream defaults:
#   common 94.3%, uncommon 5%, rare 0.5%, ultra-rare 0.2%
# Slashing UR to 1/3 means: 0.2 → 0.0667. Other buckets renormalize at
# roll time so their effective % grows slightly to fill the gap.
NEW_BUCKETS = {
    "buckets": [
        {"name": "common", "weight": 94.3},
        {"name": "uncommon", "weight": 5.0},
        {"name": "rare", "weight": 0.5},
        {"name": "ultra-rare", "weight": 0.0667},
    ]
}

# Biomes where the entire UR category is legendary+ (or only pseudo-
# legend on the non-comp side). Filler species are pulled from
# Cobblemon's own biome→species data (species that already spawn in
# this biome at common/uncommon/rare buckets), guaranteeing thematic
# fit. Target: filler total ≈ 4× existing comp weight so comp ends up
# ~20% of UR bucket in that biome.
FILLER_BIOMES: dict[str, list[str]] = {
    "#cobblemon:nether/is_soul_sand": ["duskull", "dusclops", "dusknoir"],
    "#cobblemon:nether/is_desert":    ["cubone", "bramblin", "salandit", "ekans"],
    "#cobblemon:is_deep_dark":        ["golett", "spiritomb", "golurk", "mawile"],
    "#cobblemon:is_end":              ["unown", "gothita", "elgyem", "sigilyph"],
    "#cobblemon:is_island":           ["wattrel", "kilowattrel", "cramorant", "poltchageist"],
    "#cobblemon:is_sky":              ["chatot", "squawkabilly", "murkrow", "pidgey"],
    "#cobblemon:is_peak":             ["growlithe", "meditite", "medicham", "delibird"],
}

# Per-biome existing comp weight (from our earlier analysis). Used to
# size filler so comp lands at ~20% of UR bucket. Kept as a static map
# to avoid re-walking spawn pools at runtime; re-derive if Cobblemon /
# ATM bumps significantly shift these numbers.
COMP_WEIGHT_PER_BIOME = {
    "#cobblemon:nether/is_soul_sand": 2.5,   # Yveltal
    "#cobblemon:nether/is_desert":    5.0,   # Blacephalon
    "#cobblemon:is_deep_dark":        9.0,   # Cosmog, Marshadow, Darkrai, Regidrago
    "#cobblemon:is_end":              5.0,   # placeholder — re-measure if needed
    "#cobblemon:is_island":          34.0,   # Tapus, Azelf/Uxie/Mesprit, Victini
    "#cobblemon:is_sky":              5.0,   # placeholder
    "#cobblemon:is_peak":            10.0,   # placeholder (Bagon line is pseudo)
}
FILLER_RATIO = 4.0  # filler_total = comp_weight × FILLER_RATIO → comp ≈ 20% of bucket

PACK_MCMETA = {
    "pack": {
        "pack_format": 48,
        "description": (
            "Spawn-rate hotfix: ultra-rare bucket × 1/3, paradoxes promoted to "
            "ultra-rare, biome-specific filler for legendary-only biomes."
        ),
    }
}


def species_template_from_pools(species: str) -> dict | None:
    """Find an existing spawn entry for `species` in Cobblemon-base pools so
    we can clone its spawnablePositionType + level range for filler entries.
    Filenames are `<dexnum>_<species>.json` so we glob and read the first match.
    """
    for fp in COBBLEMON_POOLS.glob(f"*_{species}.json"):
        try:
            d = json.loads(fp.read_text())
        except Exception:
            continue
        spawns = d.get("spawns", [])
        if spawns:
            return spawns[0]
    return None


def main() -> None:
    if not COBBLEMON_POOLS.exists():
        raise SystemExit(f"Cobblemon pools not found at {COBBLEMON_POOLS}")
    if not ATM_ZIP.exists():
        raise SystemExit(f"AllTheMons zip not found: {ATM_ZIP}")

    # Wipe + recreate output dir
    data_dir = OUT_BASE / "data"
    if data_dir.exists():
        shutil.rmtree(data_dir)
    (OUT_BASE / "pack.mcmeta").write_text(json.dumps(PACK_MCMETA, indent=2) + "\n")

    # ─── 1. Bucket weight override ────────────────────────────────────
    buckets_path = OUT_BASE / "data/cobblemon/spawn_data/buckets.json"
    buckets_path.parent.mkdir(parents=True, exist_ok=True)
    buckets_path.write_text(json.dumps(NEW_BUCKETS, indent=2) + "\n")
    print(f"✓ Wrote buckets.json — ultra-rare slashed to {NEW_BUCKETS['buckets'][3]['weight']}")

    # ─── 2. Paradox bucket promotion ──────────────────────────────────
    # Copy each ATM paradox file in full, change every entry's bucket to
    # ultra-rare. Weights stay at upstream values per the hotfix scope.
    paradox_count = 0
    with zipfile.ZipFile(ATM_ZIP) as z:
        for name in z.namelist():
            if not name.startswith("data/special_spawns/spawn_pool_world/paradox/"):
                continue
            if not name.endswith(".json"):
                continue
            d = json.loads(z.read(name).decode("utf-8"))
            for entry in d.get("spawns", []):
                entry["bucket"] = "ultra-rare"
            d["_comment"] = "Hotfix: paradox bucket promoted from rare → ultra-rare (weight unchanged)"
            out_path = OUT_BASE / name
            out_path.parent.mkdir(parents=True, exist_ok=True)
            out_path.write_text(json.dumps(d, indent=2) + "\n")
            paradox_count += 1
    print(f"✓ Paradox bucket-promoted: {paradox_count} species files")

    # ─── 3. Biome-specific filler ─────────────────────────────────────
    # New spawn entries live in our own namespace `server_spawn_filler`
    # so we don't clobber existing species files. Cobblemon scans all
    # `<namespace>/spawn_pool_world/` paths regardless of namespace.
    filler_count = 0
    for biome, species_list in FILLER_BIOMES.items():
        comp_w = COMP_WEIGHT_PER_BIOME[biome]
        per_species_weight = round((comp_w * FILLER_RATIO) / len(species_list), 4)
        for species in species_list:
            template = species_template_from_pools(species)
            if template is None:
                print(f"  ⚠ skipping {species} in {biome} — no template entry found")
                continue
            file_safe_biome = (
                biome.replace("#", "")
                     .replace(":", "-")
                     .replace("/", "-")
            )
            entry = {
                "id": f"{species}-hotfix-{file_safe_biome}",
                "pokemon": template.get("pokemon", species),
                "presets": ["natural"],
                "type": "pokemon",
                "spawnablePositionType": template.get("spawnablePositionType", "grounded"),
                "bucket": "ultra-rare",
                "level": template.get("level", "20-40"),
                "weight": per_species_weight,
                "condition": {"biomes": [biome]},
            }
            data = {
                "enabled": True,
                "neededInstalledMods": [],
                "neededUninstalledMods": [],
                "spawns": [entry],
                "_comment": f"Hotfix filler: {species} added to {biome} ultra-rare bucket to dilute legendary dominance",
            }
            out_path = OUT_BASE / f"data/server_spawn_filler/spawn_pool_world/{species}_{file_safe_biome}.json"
            out_path.parent.mkdir(parents=True, exist_ok=True)
            out_path.write_text(json.dumps(data, indent=2) + "\n")
            filler_count += 1
    print(f"✓ Filler entries: {filler_count} (across {len(FILLER_BIOMES)} biomes)")


if __name__ == "__main__":
    main()
