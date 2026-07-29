#!/usr/bin/env bash
# ops/wilderness-snapshot-test.sh — end-to-end test of the pre-prune snapshot on cobblemon-dev.
#
# Proves the new backupBeforeReset behaviour on the REAL server without touching any real
# terrain: it plants a dummy region file far OUTSIDE the keep-box (r.500.500 ≈ block 256000,
# empty wilderness), forces a prune, and verifies the mod MOVED the dummy into a snapshot
# instead of deleting it — then cleans the dummy + its snapshot back out. The real world is
# untouched (region count returns to its starting value).
#
# Prereqs: the NEW cobblemon-wilderness jar is already deployed to dev and the server has
# booted on it once (so the config migration has added the backup* fields). Run AS sysadmin:
#   ssh -i ~/.ssh/id_ed25519 sysadmin@192.168.1.20 'bash -s' < ops/wilderness-snapshot-test.sh
set -euo pipefail

DIR="/opt/cobblemon-dev"
SERVICE="cobblemon-dev"
SCREEN="cobblemon-dev"
LOG="$DIR/logs/latest.log"
WORLD="$DIR/world"
SNAPDIR="$DIR/wilderness-snapshots"
RX=500; RZ=500                       # far outside the ±20480 box → always classified deletable
DUMMY="r.${RX}.${RZ}.mca"
SUBS=(region entities poi)
fail() { echo "TEST FAILED: $*" >&2; exit 1; }

console() { screen -S "$SCREEN" -p 0 -X stuff "$1"$'\r'; }
count_regions() { find "$WORLD/region" -name '*.mca' | wc -l | tr -d ' '; }

echo "== preflight =="
cfg="$DIR/config/cobblemon-wilderness/authored/config.json"
grep -q '"backupBeforeReset": true' "$cfg" || fail "backupBeforeReset not true in $cfg — is the new jar deployed + booted? (migration should have set it)"
echo "  backupBeforeReset=true confirmed; jar: $(ls "$DIR"/mods/cobblemon-wilderness-*.jar | xargs -n1 basename)"

baseline=$(count_regions)
echo "  baseline region count: $baseline"

echo "== plant dummy out-of-box region $DUMMY in ${SUBS[*]} =="
# Copy a real region file's bytes so each dummy is a valid-looking, non-empty .mca.
src=$(find "$WORLD/region" -name 'r.*.mca' | head -1)
[ -n "$src" ] || fail "no source region file to clone"
for sub in "${SUBS[@]}"; do
  mkdir -p "$WORLD/$sub"
  cp "$src" "$WORLD/$sub/$DUMMY"
done
[ "$(count_regions)" -eq "$((baseline + 1))" ] || fail "dummy not planted (count != baseline+1)"

echo "== arm + restart (prune runs at boot) =="
mark=$(wc -l < "$LOG")
console "wildreset now"; sleep 2
sudo -n /usr/bin/systemctl restart "$SERVICE"
for i in $(seq 1 60); do
  sleep 5
  line=$(tail -n +"$((mark + 1))" "$LOG" 2>/dev/null | grep -iE 'cobblemon_wilderness' \
    | grep -iE 'deleted [0-9]+ region|snapshotted [0-9]+ file|aborted|circuit breaker' | tail -1 || true)
  [ -n "$line" ] && { echo "  prune settled: $line"; break; }
done
[ -n "${line:-}" ] || fail "no prune result line after 300s — check $LOG"

echo "== verify =="
# 1. dummy moved OUT of the live world
for sub in "${SUBS[@]}"; do
  [ ! -e "$WORLD/$sub/$DUMMY" ] || fail "$sub/$DUMMY still in world/ — not removed"
done
# 2. dummy now lives in the newest snapshot dir
snap=$(ls -1dt "$SNAPDIR"/*/ 2>/dev/null | head -1 || true)
[ -n "$snap" ] || fail "no snapshot dir created under $SNAPDIR"
snapped=0
for sub in "${SUBS[@]}"; do
  if [ -e "$snap/minecraft_overworld/$sub/$DUMMY" ]; then snapped=$((snapped + 1)); fi
done
[ "$snapped" -eq 3 ] || fail "expected 3 snapshotted files (region/entities/poi), found $snapped in $snap"
echo "  ✓ dummy moved out of world/ and into $snap (3/3 files)"
# 3. real world untouched
now=$(count_regions)
[ "$now" -eq "$baseline" ] || fail "real region count changed: baseline=$baseline now=$now"
echo "  ✓ real world intact (region count back to $baseline)"

echo "== cleanup test artifacts =="
rm -rf "$snap"
echo "  removed test snapshot $snap"
echo
echo "TEST PASSED — snapshot-before-prune works on dev; real terrain untouched."
