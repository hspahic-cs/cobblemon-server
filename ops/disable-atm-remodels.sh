#!/usr/bin/env bash
# Disable specific AllTheMons remodels so the affected Pokémon render with base Cobblemon's model.
#
# WHY: many AllTheMons remodels declare a GENERIC geometry id (e.g. "geometry.hooh" — the same id
# base Cobblemon uses). On heavy/winged models that id clash mispairs the AllTheMons animation onto
# the wrong model and renders garbled ("overlapping skins", confirmed on Ho-Oh). Removing the
# remodel's assets (resolver + model + poser + animation + texture) from the pack makes Cobblemon
# fall back to its OWN base resolver for that species → a clean base model. No need to know base
# asset paths; the base resolver lives in the Cobblemon jar.
#
# This only edits the RESOURCEPACK copy (rendering is client-side). The species DATA (data/...) and
# the server datapack copy are left untouched, so spawns/stats are unaffected.
#
# Re-run after an AllTheMons update (swap in the new zip, run again with the same list).
#
# Usage: disable-atm-remodels.sh <allthemons-resourcepack.zip> <dex_folder> [<dex_folder>...]
#   e.g. disable-atm-remodels.sh "AllTheMons [R3.5.1].zip" 0250_hooh 0643_reshiram
set -euo pipefail

zip="${1:?usage: disable-atm-remodels.sh <zip> <dex_folder>...}"
shift
[ -f "$zip" ] || { echo "disable-atm-remodels: no zip at $zip" >&2; exit 1; }
[ "$#" -gt 0 ] || { echo "disable-atm-remodels: no dex folders given" >&2; exit 1; }

for folder in "$@"; do
  before=$(unzip -l "$zip" | tail -1 | awk '{print $2}')
  # All asset locations AllTheMons uses for a remodel, keyed by the NNNN_species folder.
  zip -q -d "$zip" \
    "assets/atm_remodels/bedrock/pokemon/resolvers/$folder/*" \
    "assets/atm_remodels/bedrock/pokemon/models/$folder/*" \
    "assets/atm_remodels/bedrock/pokemon/posers/$folder/*" \
    "assets/atm_remodels/bedrock/pokemon/animations/$folder/*" \
    "assets/atm_remodels/textures/pokemon/$folder/*" 2>/dev/null || true
  after=$(unzip -l "$zip" | tail -1 | awk '{print $2}')
  removed=$((before - after))
  if [ "$removed" -gt 0 ]; then
    echo "disable-atm-remodels: $folder → removed $removed files (falls back to base Cobblemon model)"
  else
    echo "disable-atm-remodels: WARNING $folder — nothing matched (already gone or wrong folder name)" >&2
  fi
done
