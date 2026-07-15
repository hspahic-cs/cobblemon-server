#!/bin/bash
# ops/snapshots/world-snapshot.sh <dev|prod> — point-in-time snapshot of one cobblemon
# environment's world/ + config/cobblemon-* into /opt/snapshots/<env>-YYYY-MM-DD/.
#
# Generalized from the original prod-only prod-snapshot.sh (which is now a thin wrapper that
# calls `world-snapshot.sh prod`, preserving the weekly prod-snapshot.timer behavior verbatim).
#
# Mechanism: pause world saves + flush via RCON, rsync world/ and config/cobblemon-* with
# --link-dest hardlink dedup against the most recent prior snapshot, resume saves, keep newest N.
# Safe to run while the server is UP (RCON save-off makes the copy quiescent) — which is how the
# unified maintenance pipeline (wilderness-maintenance.sh) uses it as the PRE-WIPE rollback point:
# snapshot first (server up), then arm + restart so the wipe happens at the following boot.
#
# Exit non-zero on any failure so a caller can abort a reset when no fresh backup exists.

set -euo pipefail

ENVNAME="${1:?usage: world-snapshot.sh <dev|prod>}"
case "$ENVNAME" in
  dev|prod) ;;
  *) echo "world-snapshot: env must be 'dev' or 'prod', got '$ENVNAME'" >&2; exit 1 ;;
esac

DIR="/opt/cobblemon-${ENVNAME}"
SERVICE="cobblemon-${ENVNAME}"
SNAPSHOT_BASE="/opt/snapshots"
KEEP="${SNAPSHOT_KEEP:-5}"
TS=$(date +%Y-%m-%d)
DEST="$SNAPSHOT_BASE/${ENVNAME}-$TS"

[ -d "$DIR" ] || { echo "world-snapshot: no install dir $DIR" >&2; exit 1; }

if [[ -d "$DEST" ]]; then
  echo "Snapshot $DEST already exists; aborting (don't double-run)." >&2
  exit 1
fi

# Single-instance guard — don't let two snapshots run at once.
mkdir -p "$SNAPSHOT_BASE"
exec 9>"$SNAPSHOT_BASE/.lock"
flock -n 9 || { echo "Another snapshot in progress; aborting." >&2; exit 1; }

# RCON helper. Reads password AND port from the env's server.properties on the fly (ports differ
# per env — prod 25575, dev 25576 — so hardcoding would make the snapshot fail on any non-25575 env
# and, since a failed snapshot aborts the reset, silently make the pipeline inert there).
rcon() {
  local cmd="$1" pw port
  pw=$(grep ^rcon.password "$DIR/server.properties" | cut -d= -f2)
  port=$(grep ^rcon.port "$DIR/server.properties" | cut -d= -f2)
  port=${port:-25575}
  python3 -c "
import socket, struct, sys
s = socket.socket(); s.settimeout(10); s.connect(('127.0.0.1', $port))
def pkt(rid, t, b):
    body = struct.pack('<ii', rid, t) + b.encode() + b'\x00\x00'
    return struct.pack('<i', len(body)) + body
s.send(pkt(1, 3, '$pw')); s.recv(4096)
s.send(pkt(2, 2, '''$cmd''')); print(s.recv(8192)[12:-2].decode(errors='replace'))
"
}

RUNNING=false
if systemctl is-active --quiet "$SERVICE"; then
  RUNNING=true
  echo "[$(date)] Pausing ${ENVNAME} world saves"
  rcon "save-off" >/dev/null
  rcon "save-all flush" >/dev/null
  sleep 5   # let Minecraft finish writing
fi

mkdir -p "$DEST"
# Match ONLY date-stamped snapshot dirs (<env>-YYYY-MM-DD), never sibling ops files like
# dev-reset.sh or dev-config-*.tar.gz — otherwise the prune below could rm a non-snapshot.
SNAP_GLOB="${ENVNAME}-[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]"
LATEST_PREV=$(ls -1dt "$SNAPSHOT_BASE"/$SNAP_GLOB 2>/dev/null | grep -v "$DEST" | head -1 || true)
LINK_DEST=()
[[ -n "$LATEST_PREV" ]] && LINK_DEST=(--link-dest "$LATEST_PREV")

echo "[$(date)] Copying world/ to $DEST"
rsync -a "${LINK_DEST[@]}" \
  --exclude='session.lock' \
  --exclude='*.lock' \
  "$DIR/world/" "$DEST/world/"

echo "[$(date)] Copying config/cobblemon-* to $DEST"
mkdir -p "$DEST/config"
shopt -s nullglob
for d in "$DIR"/config/cobblemon-* "$DIR"/config/cobblemon_*; do
  rsync -a "${LINK_DEST[@]}" "$d" "$DEST/config/"
done
shopt -u nullglob

if $RUNNING; then
  echo "[$(date)] Resuming ${ENVNAME} world saves"
  rcon "save-on" >/dev/null
fi

# Prune: keep most-recent N snapshots for this env (date-stamped dirs only — see SNAP_GLOB).
ls -1dt "$SNAPSHOT_BASE"/$SNAP_GLOB | tail -n +$((KEEP + 1)) | while read -r old; do
  echo "[$(date)] Pruning old snapshot: $old"
  rm -rf "$old"
done

echo "[$(date)] Snapshot complete: $DEST ($(du -sh "$DEST" | awk '{print $1}'))"
