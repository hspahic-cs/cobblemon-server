#!/usr/bin/env bash
# ops/wilderness-maintenance.sh <dev|prod> [warn_seconds] — unified daily 05:30 maintenance pipeline.
#
# Consolidates ALL scheduled server-down work into one window and enforces the ordering that keeps
# the border wipe safe. Every day it: (1) warns players, (2) takes a world snapshot, (3) ON A RESET
# DAY arms the border wipe, then (4) restarts. The cobblemon-wilderness mod wipes outside-border
# regions (keeping legendary-monument regions) ONCE at the next boot, and only when armed.
#
#   ┌───────────────────────── ORDERING INVARIANT (the whole point) ─────────────────────────┐
#   │ The world snapshot (step 2) ALWAYS runs BEFORE the arm+restart (steps 3–4). The wipe    │
#   │ executes at the post-restart boot, so the snapshot always captures the PRE-WIPE world.  │
#   │ A failed snapshot ABORTS the run before arming — never wipe without a fresh rollback.   │
#   │ Do NOT reorder these steps.                                                             │
#   └────────────────────────────────────────────────────────────────────────────────────────┘
#
# Reset cadence is date-driven and pipeline-owned (the mod has no clock): a reset day is every
# RESET_PERIOD_DAYS (default 14) counting from RESET_ANCHOR_DATE — predictable, announceable dates.
# On non-reset days this is just snapshot + restart (an unarmed boot is inert: no wipe).
#
# Runs AS the service user (sysadmin); only the restart escalates via `sudo -n systemctl` (NOPASSWD),
# same as the scripts it supersedes. Replaces wilderness-arm-restart.sh as the timer ExecStart;
# that script stays for manual one-off arming.
#
#   Manual test (dev):  ssh cobblemon /usr/local/bin/wilderness-maintenance.sh dev
#   Via systemd timer:  ops/systemd/cobblemon-wilderness-reset@.{timer,service}
set -euo pipefail

ENVNAME="${1:?usage: wilderness-maintenance.sh <dev|prod> [warn_seconds]}"
WARN="${2:-30}"                                   # heads-up before restart; 0 disables the broadcast
RESET_PERIOD_DAYS="${RESET_PERIOD_DAYS:-14}"      # wipe every N days...
RESET_ANCHOR_DATE="${RESET_ANCHOR_DATE:-2026-01-01}"  # ...counting from this date (a reset day)

case "$ENVNAME" in
  dev|prod) ;;
  *) echo "wilderness-maintenance: env must be 'dev' or 'prod', got '$ENVNAME'" >&2; exit 1 ;;
esac

DIR="/opt/cobblemon-${ENVNAME}"
SERVICE="cobblemon-${ENVNAME}"
SCREEN="cobblemon-${ENVNAME}"                     # systemd ExecStart: screen -DmS cobblemon-<env>
WILD_CFG="$DIR/config/cobblemon-wilderness/authored/config.json"

[ -d "$DIR" ] || { echo "wilderness-maintenance: no install dir $DIR" >&2; exit 1; }
command -v screen >/dev/null || { echo "wilderness-maintenance: screen not found" >&2; exit 1; }

# Locate world-snapshot.sh: env override, then repo-sibling (manual runs), then deploy dir.
SELF_DIR="$(dirname "$(readlink -f "$0")")"
SNAPSHOT_SH="${WORLD_SNAPSHOT_SH:-}"
if [ -z "$SNAPSHOT_SH" ]; then
  if   [ -x "$SELF_DIR/snapshots/world-snapshot.sh" ]; then SNAPSHOT_SH="$SELF_DIR/snapshots/world-snapshot.sh"
  elif [ -x "$SELF_DIR/world-snapshot.sh" ];           then SNAPSHOT_SH="$SELF_DIR/world-snapshot.sh"
  else SNAPSHOT_SH="/usr/local/bin/world-snapshot.sh"; fi
fi

console() { screen -S "$SCREEN" -p 0 -X stuff "$1"$'\r'; }
log() { echo "[$(date '+%F %T %Z')] ${ENVNAME}: $*"; }

# --- Is today a reset day? (elapsed-from-anchor is an exact multiple of the period) -----------------
today_days=$(( $(date -d "$(date +%F)" +%s) / 86400 ))
anchor_days=$(( $(date -d "$RESET_ANCHOR_DATE" +%s) / 86400 ))
elapsed=$(( today_days - anchor_days ))
RESET_DAY=0
if (( elapsed >= 0 && elapsed % RESET_PERIOD_DAYS == 0 )); then RESET_DAY=1; fi
# NOTE: a missed 05:30 run on the exact reset day skips THAT cycle until the next reset day (the timer
# is Persistent=false by design). Acceptable — resets are periodic and the wipe is idempotent.

if [ "$RESET_DAY" = 1 ]; then
  log "RESET DAY (elapsed=${elapsed}d, period=${RESET_PERIOD_DAYS}d) — snapshot, then arm border wipe."
else
  log "not a reset day (elapsed=${elapsed}d, period=${RESET_PERIOD_DAYS}d) — snapshot + restart only."
fi

# --- 1. Warn -----------------------------------------------------------------------------------------
if [ "$WARN" -gt 0 ] 2>/dev/null; then
  if [ "$RESET_DAY" = 1 ]; then
    console "say [Maintenance] Nightly backup + wilderness reset — brief restart in ${WARN}s (builds inside the safe zone are untouched)."
  else
    console "say [Maintenance] Nightly backup — brief restart in ${WARN}s."
  fi
fi

# --- 2. WORLD SNAPSHOT — the pre-wipe rollback point. Must succeed before we arm anything. ----------
log "taking world snapshot via $SNAPSHOT_SH"
if ! "$SNAPSHOT_SH" "$ENVNAME"; then
  log "SNAPSHOT FAILED — aborting: NOT arming a wipe and NOT restarting (no fresh rollback point)."
  exit 1
fi

# --- 3. Arm the wipe (reset days only) ---------------------------------------------------------------
if [ "$RESET_DAY" = 1 ]; then
  # F2 precondition: the pipeline owns backups, so the mod must NOT also snapshot the whole outside
  # world each cycle. Force backupBeforeReset=false (idempotent). The mod reads config at boot, so
  # editing before the restart takes effect for this wipe. Uses python3 (already required by the
  # snapshot's RCON helper) — no jq dependency, which isn't installed on the servers.
  if [ -f "$WILD_CFG" ] && command -v python3 >/dev/null; then
    if python3 - "$WILD_CFG" <<'PY'
import json, sys
p = sys.argv[1]
try:
    with open(p) as f: cfg = json.load(f)
except Exception as e:
    sys.stderr.write("could not parse config: %s\n" % e); sys.exit(2)
if cfg.get("backupBeforeReset", False) is not False:
    cfg["backupBeforeReset"] = False
    with open(p, "w") as f: json.dump(cfg, f, indent=2)
    sys.stderr.write("flipped backupBeforeReset -> false\n")
PY
    then
      log "ensured mod backupBeforeReset=false (pipeline owns backups)"
    else
      log "WARN: could not enforce backupBeforeReset=false in $WILD_CFG — mod default is false, but verify"
    fi
  else
    log "WARN: python3 or $WILD_CFG missing — ensure backupBeforeReset=false manually"
  fi

  [ "$WARN" -gt 0 ] 2>/dev/null && sleep "$WARN"
  log "arming wilderness wipe (/wildreset now)"
  console "wildreset now"
  sleep 2
else
  [ "$WARN" -gt 0 ] 2>/dev/null && sleep "$WARN"
fi

# --- 4. Restart (wipe executes at boot iff armed) ---------------------------------------------------
log "restarting $SERVICE"
sudo -n /usr/bin/systemctl restart "$SERVICE"
log "restart issued — $([ "$RESET_DAY" = 1 ] && echo 'wipe runs once at boot, then the arm flag is consumed' || echo 'inert boot, no wipe')."
