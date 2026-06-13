#!/usr/bin/env bash
# Remove retired server-* datapacks from the live world's datapacks dir.
#
# The datapack deploy is intentionally non-destructive (rsync without --delete, to protect
# world-only datapacks). The downside: a datapack we DELETE from the repo is never removed from
# the live world — it lingers forever. That bit us with server-spawn-nerfs: its spawn overrides
# kept fighting AllTheMons, and an old server_spawn_filler namespace (retired at 0.7.43) kept
# diluting the ultra-rare bucket long after it left the repo.
#
# This prunes any `server-*` datapack directory on the server that is NOT in the set we currently
# ship from the repo. Scoped to the `server-*` prefix ONLY, so AllTheMons*.zip and any manually-
# added / world-only datapacks are never touched. Self-cleaning: handles every future retirement
# with no per-pack cruft. Restart the server afterwards (Cobblemon data loads once per instance).
#
# Usage: prune-removed-server-datapacks.sh <world-datapacks-dir> <space-separated repo server-* names>
set -euo pipefail

dir="${1:?usage: prune-removed-server-datapacks.sh <datapacks-dir> <repo server-* names>}"
keep="${2:-}"

[ -d "$dir" ] || { echo "prune-removed-server-datapacks: no datapacks dir at $dir"; exit 0; }
cd "$dir"

# Space-pad the keep-list so we can do whole-token membership tests (portable to bash 3.2 — no
# associative arrays).
keep=" $keep "

shopt -s nullglob
removed=0
for path in server-*; do
  [ -d "$path" ] || continue
  if [[ "$keep" != *" $path "* ]]; then
    rm -rf -- "$path"
    echo "prune-removed-server-datapacks: removed retired $path"
    removed=$((removed + 1))
  fi
done
echo "prune-removed-server-datapacks: pruned $removed retired pack(s)"
