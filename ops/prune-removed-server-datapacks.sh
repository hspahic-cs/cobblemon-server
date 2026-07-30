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
# FAIL-OPEN BY DESIGN. A pack this can't delete is warned about and skipped; the script still
# exits 0. Pruning is housekeeping, and it sits upstream of the config rsync, the atomic swap and
# the restart in deploy-{dev,prod}.yml — so a hard failure here doesn't just skip a cleanup, it
# leaves the deploy *half-applied*: new mods staged, configs never copied, server never restarted,
# `.deployed_version` never written. The run goes red while the server quietly keeps running the
# old version, which reads like a no-op rather than a failure.
#
# That is not hypothetical: a hand-made `server-roguelite-smoketest` datapack, created on the dev
# VM as `sysadmin` with mode 0755, blocked the 0.33.0 deploy outright. Deploys run as `deployer`,
# which is in the `sysadmin` group but had no group-write bit on that directory, so it couldn't
# unlink the children. Any `server-*` directory dropped on a VM by hand could do the same again.
#
# The tradeoff of fail-open: a retired pack we can't delete now *lingers*, which is exactly what
# this script exists to prevent (see server-spawn-nerfs above). That's the lesser evil — a lingering
# datapack is a visible warning and a one-line manual fix, whereas a half-applied deploy is silent.
# Fix a warned pack at the source: `sudo chown -R deployer <path>` on the VM, or delete it by hand.
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
skipped=0
for path in server-*; do
  [ -d "$path" ] || continue
  [[ "$keep" != *" $path "* ]] || continue

  # `|| true` so set -e can't abort the loop, and the on-disk check — not rm's exit code — is the
  # authority: a partial removal can leave the directory behind while rm still reports failure.
  err=$(rm -rf -- "$path" 2>&1) || true
  if [ -e "$path" ]; then
    echo "prune-removed-server-datapacks: WARNING could not remove retired $path — leaving it in place: ${err%%$'\n'*}"
    skipped=$((skipped + 1))
  else
    echo "prune-removed-server-datapacks: removed retired $path"
    removed=$((removed + 1))
  fi
done
echo "prune-removed-server-datapacks: pruned $removed retired pack(s), skipped $skipped"

# GitHub Actions annotation, so a skip is visible on the run summary instead of only in the log.
# Harmless plain text when run outside CI. Deliberately does NOT affect the exit code.
if [ "$skipped" -gt 0 ]; then
  echo "::warning::prune-removed-server-datapacks skipped $skipped retired datapack(s) it could not delete in $dir — they are still live on the server. Fix ownership on the VM (chown -R deployer) or remove them by hand."
fi
