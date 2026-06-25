#!/usr/bin/env bash
# ops/wilderness-reset.sh — preview or execute a cobblemon-wilderness world prune.
#
# The cobblemon-wilderness mod deletes region files (r.X.Z.mca + matching entities/poi)
# that lie WHOLLY OUTSIDE the keep-box, and it only does so at server boot
# (ServerAboutToStartEvent, before any level loads). So the workflow is always:
#   flip config -> arm "/wildreset now" -> warn players -> restart (the prune runs at boot).
# Players are disconnected only for the normal restart window (~1-2 min); the prune itself
# is a fraction of a second. There is no in-session wipe.
#
# Run AS the service user (sysadmin) on the host, NOT root: screen sessions and the config
# file are owned by sysadmin, and writing them as root would break ownership (g+w). Only the
# restart escalates via `sudo systemctl`, exactly as we drive it by hand.
#
#   Preview only (read-only, safe — prints the mod's would-delete report and exits):
#     ssh cobblemon bash -s -- prod < ops/wilderness-reset.sh
#
#   Execute (flip config -> arm -> broadcast countdown -> restart -> verify):
#     ssh cobblemon bash -s -- prod confirm < ops/wilderness-reset.sh
#
#   Optional 3rd arg = countdown lead seconds (default 120):
#     ssh cobblemon bash -s -- prod confirm 300 < ops/wilderness-reset.sh
#
# Idempotent-ish: re-running preview is always safe. Re-running execute re-arms and prunes
# again at the next boot (a no-op once the world is already inside the box).
set -euo pipefail

ENVNAME="${1:?usage: wilderness-reset.sh <dev|prod> [confirm] [lead_seconds]}"
MODE="${2:-preview}"
LEAD="${3:-120}"

case "$ENVNAME" in
  dev|prod) ;;
  *) echo "wilderness-reset: env must be 'dev' or 'prod', got '$ENVNAME'" >&2; exit 1 ;;
esac

DIR="/opt/cobblemon-${ENVNAME}"
SERVICE="cobblemon-${ENVNAME}"
SCREEN="cobblemon-${ENVNAME}"          # systemd ExecStart: screen -DmS cobblemon-<env>
CFG="${DIR}/config/cobblemon-wilderness/authored/config.json"
LOG="${DIR}/logs/latest.log"
REGION="${DIR}/world/region"

[ -d "$DIR" ]  || { echo "wilderness-reset: no install dir $DIR" >&2; exit 1; }
[ -f "$CFG" ]  || { echo "wilderness-reset: no config $CFG (mod writes it on first boot)" >&2; exit 1; }
command -v screen >/dev/null || { echo "wilderness-reset: screen not found" >&2; exit 1; }
command -v python3 >/dev/null || { echo "wilderness-reset: python3 not found" >&2; exit 1; }

# Send a console line to the running server's screen session.
console() { screen -S "$SCREEN" -p 0 -X stuff "$1"$'\r'; }

# --- Preview (read-only) -----------------------------------------------------
# Drive the mod's own /wildreset preview so the report reflects the mod's exact predicate,
# not a re-derivation. Mark the log, send the command, then echo back the new matching lines.
echo "[$(date '+%F %T')] ${ENVNAME}: running /wildreset preview (read-only)"
before=$(wc -l < "$LOG")
console "wildreset preview"
sleep 6
preview=$(tail -n +"$((before + 1))" "$LOG" | grep -iE "would delete|would.*region|preview" || true)
if [ -z "$preview" ]; then
  echo "wilderness-reset: no preview output captured — is $SERVICE running and the mod enabled in jar?" >&2
  echo "  (check: screen -ls ; tail $LOG)" >&2
  exit 1
fi
echo "$preview"

if [ "$MODE" != "confirm" ]; then
  echo
  echo "Preview only. To execute: re-run with 'confirm' as the 2nd arg."
  echo "Before executing on prod, confirm every player's base sits inside the keep-box above."
  exit 0
fi

# --- Execute -----------------------------------------------------------------
echo "[$(date '+%F %T')] ${ENVNAME}: CONFIRMED — flipping config and arming"

# Flip the two safety gates. Back up first; write as the current (service) user to preserve
# ownership. Leaves box / interval / every other field untouched.
cp "$CFG" "${CFG}.bak.$(date +%s)"
python3 - "$CFG" <<'PY'
import json, sys
p = sys.argv[1]
d = json.load(open(p))
d["enabled"] = True
d["dryRun"] = False
json.dump(d, open(p, "w"), indent=2)
print("config: enabled=%s dryRun=%s box=%s" % (d["enabled"], d["dryRun"], d["box"]))
PY

# Arm the prune for the next boot. (Without this, first boot at enabled=true only records a
# baseline and skips — a deliberate guard against a surprise wipe the moment you flip enabled.)
console "wildreset now"
sleep 2

# --- Broadcast countdown -----------------------------------------------------
# The "brief kick" is the restart itself; this just gives players warning first.
echo "[$(date '+%F %T')] ${ENVNAME}: broadcasting ${LEAD}s countdown"
mins=$(( LEAD / 60 ))
console "title @a times 10 70 20"
console "title @a subtitle {\"text\":\"You'll be disconnected ~1-2 min\",\"color\":\"gray\"}"
console "title @a title {\"text\":\"Maintenance in ${mins} min\",\"color\":\"yellow\"}"
console "say [Maintenance] Wilderness reset in ${mins} min — brief restart (~1-2 min). Builds inside the safe zone are untouched."
sleep "$(( LEAD > 30 ? LEAD - 30 : 1 ))"

console "title @a title {\"text\":\"Restarting in 30s\",\"color\":\"gold\"}"
console "say [Maintenance] Restarting in 30 seconds — reconnect shortly."
sleep 30

# --- Restart (prune runs at boot) --------------------------------------------
echo "[$(date '+%F %T')] ${ENVNAME}: restarting $SERVICE (prune executes at boot)"
before_count=$(find "$REGION" -name '*.mca' | wc -l)
log_mark=$(wc -l < "$LOG")
sudo -n /usr/bin/systemctl restart "$SERVICE"

# Wait for the actual prune to finish, NOT just for systemd "active" — the mod runs at
# ServerAboutToStartEvent, which fires AFTER systemd reports active (active = the screen
# process started, not the world loaded). Poll the log (from the pre-restart mark) for the
# mod's terminal line: a real run ("deleted ... region"), a baseline skip, or an abort.
# A fixed sleep here races the prune and can report "removed 0" before it has run.
result=""
for i in $(seq 1 60); do
  sleep 5
  result=$(tail -n +"$((log_mark + 1))" "$LOG" 2>/dev/null \
    | grep -iE 'cobblemon_wilderness' \
    | grep -iE 'deleted [0-9]+ region|recorded baseline|baseline.*no reset|aborted|circuit breaker' \
    | tail -1 || true)
  [ -n "$result" ] && { echo "[$(date '+%F %T')] prune settled after ~$((i * 5))s"; break; }
done
[ -z "$result" ] && echo "[$(date '+%F %T')] WARNING: no prune result line seen after 300s — check $LOG manually" >&2

# --- Verify ------------------------------------------------------------------
after_count=$(find "$REGION" -name '*.mca' | wc -l)
echo "[$(date '+%F %T')] regions: ${before_count} -> ${after_count} (removed $(( before_count - after_count )))"
echo "=== boot prune log ==="
grep -iE 'cobblemon_wilderness' "$LOG" | grep -iE 'deleted|kept|reset|box|abort|baseline' | tail -8
