#!/usr/bin/env bash
# Remove stale AllTheMons datapack zips from the live world's datapacks dir.
#
# The datapack deploy is intentionally non-destructive (rsync without --delete, to protect
# world-only datapacks). But AllTheMons ships as a single VERSION-NAMED zip ("AllTheMons [R3.5].zip"),
# so a version bump (R3.4 -> R3.5) leaves the OLD zip sitting next to the new one. Two AllTheMons
# datapacks = the same Cobblemon species/posers registered twice = the client crashes rendering an
# overridden Pokemon ("Unknown animation group: _atm", e.g. Mewtwo). This removes any AllTheMons
# zip that isn't the version we currently ship.
#
# Scoped to AllTheMons*.zip ONLY, so manually-added / world-only datapacks are never touched.
# Restart the server afterwards — Cobblemon species load once per server instance (no hot reload).
#
# Usage: prune-stale-allthemons.sh <world-datapacks-dir> <current-allthemons-zip-filename>
set -euo pipefail

dir="${1:?usage: prune-stale-allthemons.sh <datapacks-dir> <current-allthemons-zip>}"
current="${2:?missing current AllTheMons filename}"

[ -d "$dir" ] || { echo "prune-stale-allthemons: no datapacks dir at $dir"; exit 0; }
cd "$dir"

shopt -s nullglob
removed=0
for f in AllTheMons*.zip; do
  if [ "$f" != "$current" ]; then
    rm -f -- "$f"
    echo "prune-stale-allthemons: removed stale $f"
    removed=$((removed + 1))
  fi
done
echo "prune-stale-allthemons: keeping '$current' (pruned $removed stale)"
