#!/usr/bin/env bash
# ops/wilderness-arm-restart.sh — arm the wilderness prune and restart the server.
#
# This is the ACTIVATION step of the rolling reset. The cobblemon-wilderness mod only prunes at
# boot (ServerAboutToStartEvent), and only when ARMED (state.forceNextBoot). Unarmed boots (deploys,
# apt restarts) are inert by construction. So a scheduled job must do exactly two things, in order:
#   1. arm the prune   -> send "/wildreset now" into the running server's screen session
#   2. restart         -> the prune executes once at the next boot, then the arm flag is consumed
#
# Intended to run once a day at a low-population time (05:30 America/New_York) from the systemd
# timer in ops/systemd/. Nothing here flips enabled/dryRun — those stay whatever the config says,
# so the first supervised run should be driven by hand via ops/wilderness-reset.sh; after that this
# job just re-arms the same steady-state prune each day.
#
# Run AS the service user (sysadmin), NOT root: the screen session is owned by sysadmin, and only
# the restart escalates via `sudo -n systemctl` (NOPASSWD), exactly as ops/wilderness-reset.sh does.
#
#   Manual test (dev):   ssh cobblemon bash -s -- dev  < ops/wilderness-arm-restart.sh
#   Via systemd timer:   see ops/systemd/README.md
#
# Idempotent: re-running just re-arms + restarts. Once the world is already inside the keep-box the
# prune is a no-op, so an extra run costs only a restart.
set -euo pipefail

ENVNAME="${1:?usage: wilderness-arm-restart.sh <dev|prod> [warn_seconds]}"
WARN="${2:-30}"   # brief heads-up before the restart; 0 disables the broadcast

case "$ENVNAME" in
  dev|prod) ;;
  *) echo "wilderness-arm-restart: env must be 'dev' or 'prod', got '$ENVNAME'" >&2; exit 1 ;;
esac

DIR="/opt/cobblemon-${ENVNAME}"
SERVICE="cobblemon-${ENVNAME}"
SCREEN="cobblemon-${ENVNAME}"          # systemd ExecStart: screen -DmS cobblemon-<env>

[ -d "$DIR" ] || { echo "wilderness-arm-restart: no install dir $DIR" >&2; exit 1; }
command -v screen >/dev/null || { echo "wilderness-arm-restart: screen not found" >&2; exit 1; }

# Send a console line to the running server's screen session (same pattern as wilderness-reset.sh).
console() { screen -S "$SCREEN" -p 0 -X stuff "$1"$'\r'; }

echo "[$(date '+%F %T %Z')] ${ENVNAME}: arming wilderness prune (/wildreset now)"
console "wildreset now"
sleep 2

if [ "$WARN" -gt 0 ] 2>/dev/null; then
  echo "[$(date '+%F %T %Z')] ${ENVNAME}: broadcasting ${WARN}s restart warning"
  console "say [Maintenance] Nightly wilderness reset — brief restart in ${WARN}s (builds inside the safe zone are untouched)."
  sleep "$WARN"
fi

echo "[$(date '+%F %T %Z')] ${ENVNAME}: restarting $SERVICE (prune executes at boot)"
sudo -n /usr/bin/systemctl restart "$SERVICE"

echo "[$(date '+%F %T %Z')] ${ENVNAME}: restart issued — the prune runs once at boot, then the arm flag is consumed."
