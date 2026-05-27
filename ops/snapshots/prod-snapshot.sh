#!/bin/bash
# /opt/snapshots/prod-snapshot.sh — weekly snapshot of cobblemon-prod's world + cobblemon configs.
#
# Pauses world saves + flushes via RCON, copies world/ and config/cobblemon-* to
# /opt/snapshots/prod-YYYY-MM-DD/, resumes saves. Keeps the 5 most recent.
# Triggered weekly by prod-snapshot.timer.

set -euo pipefail

PROD_DIR="/opt/cobblemon-prod"
SNAPSHOT_BASE="/opt/snapshots"
KEEP=5
TS=$(date +%Y-%m-%d)
DEST="$SNAPSHOT_BASE/prod-$TS"

if [[ -d "$DEST" ]]; then
  echo "Snapshot $DEST already exists; aborting (don't double-run)."
  exit 1
fi

# Single-instance guard — don't let two snapshots run at once.
exec 9>"$SNAPSHOT_BASE/.lock"
flock -n 9 || { echo "Another snapshot in progress; aborting."; exit 1; }

# RCON helper. Reads password from prod's server.properties on the fly so
# it survives password rotations.
rcon() {
  local cmd="$1"
  local pw
  pw=$(grep ^rcon.password "$PROD_DIR/server.properties" | cut -d= -f2)
  python3 -c "
import socket, struct, sys
s = socket.socket(); s.settimeout(10); s.connect(('127.0.0.1', 25575))
def pkt(rid, t, b):
    body = struct.pack('<ii', rid, t) + b.encode() + b'\x00\x00'
    return struct.pack('<i', len(body)) + body
s.send(pkt(1, 3, '$pw')); s.recv(4096)
s.send(pkt(2, 2, '''$cmd''')); print(s.recv(8192)[12:-2].decode(errors='replace'))
"
}

PROD_RUNNING=false
if systemctl is-active --quiet cobblemon-prod; then
  PROD_RUNNING=true
  echo "[$(date)] Pausing prod world saves"
  rcon "save-off" >/dev/null
  rcon "save-all flush" >/dev/null
  # Give Minecraft time to finish writing.
  sleep 5
fi

mkdir -p "$DEST"
echo "[$(date)] Copying world/ to $DEST"
# -a: archive; --link-dest: hardlink unchanged files against the most recent
# prior snapshot to keep disk usage minimal.
LATEST_PREV=$(ls -1dt "$SNAPSHOT_BASE"/prod-* 2>/dev/null | head -1 || true)
LINK_DEST=()
[[ -n "$LATEST_PREV" ]] && LINK_DEST=(--link-dest "$LATEST_PREV")

rsync -a "${LINK_DEST[@]}" \
  --exclude='session.lock' \
  --exclude='*.lock' \
  "$PROD_DIR/world/" "$DEST/world/"

echo "[$(date)] Copying config/cobblemon-* to $DEST"
mkdir -p "$DEST/config"
# May not exist if prod hasn't run friend's mods yet. Glob handles 0 matches.
shopt -s nullglob
for d in "$PROD_DIR"/config/cobblemon-* "$PROD_DIR"/config/cobblemon_*; do
  rsync -a "${LINK_DEST[@]}" "$d" "$DEST/config/"
done
shopt -u nullglob

if $PROD_RUNNING; then
  echo "[$(date)] Resuming prod world saves"
  rcon "save-on" >/dev/null
fi

# Prune: keep most-recent N snapshots
ls -1dt "$SNAPSHOT_BASE"/prod-* | tail -n +$((KEEP + 1)) | while read -r old; do
  echo "[$(date)] Pruning old snapshot: $old"
  rm -rf "$old"
done

echo "[$(date)] Snapshot complete: $DEST ($(du -sh "$DEST" | awk '{print $1}'))"
