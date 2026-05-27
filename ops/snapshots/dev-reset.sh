#!/bin/bash
# /opt/snapshots/dev-reset.sh — reset cobblemon-dev's world to the latest prod snapshot.
#
# Manual: run when you want a fresh prod-like dev. Stops dev, replaces world/ and
# config/cobblemon-*, starts dev.
#
# Usage: sudo /opt/snapshots/dev-reset.sh [snapshot-name]
#   With no arg: uses the most recent prod-* snapshot
#   With arg:    uses /opt/snapshots/<arg> (e.g. prod-2026-05-26)

set -euo pipefail

DEV_DIR="/opt/cobblemon-dev"
SNAPSHOT_BASE="/opt/snapshots"

if [[ -n "${1:-}" ]]; then
  SRC="$SNAPSHOT_BASE/$1"
else
  SRC=$(ls -1dt "$SNAPSHOT_BASE"/prod-* 2>/dev/null | head -1 || true)
fi

if [[ -z "$SRC" || ! -d "$SRC" ]]; then
  echo "No snapshot found. Available:" >&2
  ls "$SNAPSHOT_BASE" 2>/dev/null | grep ^prod- >&2 || echo "  (none)" >&2
  exit 1
fi

echo "==> Resetting cobblemon-dev from snapshot: $SRC"
echo "==> Snapshot age: $(stat -c %y "$SRC" | cut -d. -f1)"
read -r -p "==> Continue? [y/N] " confirm
[[ "$confirm" =~ ^[Yy]$ ]] || { echo "Aborted."; exit 1; }

echo "[$(date)] Stopping cobblemon-dev"
systemctl stop cobblemon-dev || true
sleep 3

# Backup current dev state in case we need to roll back the reset.
ROLLBACK="$DEV_DIR/world.before-reset-$(date +%Y%m%d-%H%M%S)"
echo "[$(date)] Backing up current dev world to $ROLLBACK"
mv "$DEV_DIR/world" "$ROLLBACK"

# Remove any cobblemon-* configs (they'll be re-laid from the snapshot)
shopt -s nullglob
for d in "$DEV_DIR"/config/cobblemon-* "$DEV_DIR"/config/cobblemon_*; do
  echo "[$(date)] Removing existing config dir: $d"
  rm -rf "$d"
done
shopt -u nullglob

echo "[$(date)] Restoring world from snapshot"
cp -a "$SRC/world" "$DEV_DIR/world"

if [[ -d "$SRC/config" ]]; then
  echo "[$(date)] Restoring config/cobblemon-* from snapshot"
  cp -a "$SRC/config/." "$DEV_DIR/config/"
fi

# Ownership: the cobblemon-dev systemd unit runs as sysadmin; ensure files are
# owned correctly. (cp -a preserves the source's owner; if the snapshot was
# made under sysadmin, this is already right, but enforce to be safe.)
chown -R sysadmin:sysadmin "$DEV_DIR/world" "$DEV_DIR/config"

# Reset the deployed-version marker so the next CI deploy treats this as fresh
# (and will reapply mods on top of the snapshotted world).
rm -f "$DEV_DIR/.deployed_version"

echo "[$(date)] Starting cobblemon-dev"
systemctl start cobblemon-dev

echo
echo "Done. Rolled back state preserved at: $ROLLBACK"
echo "Delete it with: sudo rm -rf $ROLLBACK"
