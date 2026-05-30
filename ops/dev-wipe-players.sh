#!/bin/bash
# ops/dev-wipe-players.sh — wipe per-player state on cobblemon-dev and restart the server.
#
# Use when you want a clean slate for testing without restoring from a prod snapshot.
# Players keep their accounts/UUIDs but lose: inventory, position, advancements, stats,
# Cobblemon party/PC/pokedex, counter mod state, gacha login history, NeoEssentials
# homes / balances / pay-toggles / transaction history / AFK state.
#
# Preserves: world terrain, dimensions, structures, market stock, ranked config,
# datapacks, mods, ops.json, whitelist.json, server.properties, NeoEssentials
# server-wide config (spawn, warps, dashboard users, scheduled tasks, moderation/bans).
#
# Run on the dev host (or via ssh):
#   sudo /opt/cobblemon-dev-tools/dev-wipe-players.sh
#   ssh cobblemon sudo bash -s < ops/dev-wipe-players.sh
#
# Idempotent — safe to re-run.

set -euo pipefail

DEV_DIR="/opt/cobblemon-dev"
SERVICE="cobblemon-dev.service"

if [[ ! -d "$DEV_DIR" ]]; then
  echo "Dev directory not found: $DEV_DIR" >&2
  exit 1
fi

# Per-player directories inside world/ — wipe contents, keep the directory.
# Excludes:
#   cobblenav/ — only contains shared spawndata, not per-player
#   pokemon/ — has pcstore/ and playerpartystore/ subdirs we wipe individually
#   neoessentials/playerdata/homes — per-player home locations (added 0.7.30)
PLAYER_DIRS=(
  "$DEV_DIR/world/playerdata"
  "$DEV_DIR/world/advancements"
  "$DEV_DIR/world/stats"
  "$DEV_DIR/world/cobblemonplayerdata"
  "$DEV_DIR/world/pokedex"
  "$DEV_DIR/world/pokemon/pcstore"
  "$DEV_DIR/world/pokemon/playerpartystore"
  "$DEV_DIR/world/counter"
  "$DEV_DIR/neoessentials/playerdata/homes"
)

# Files/JSONs that track per-player state outside world/.
# starterkit/tracking.json: starter-kit mod's "already given" registry — without this, players
#   re-joining after a wipe won't be offered the kit again.
# rctmod.player.*.stat.dat: RCT trainer-defeat counters — separate from advancements.
# NeoEssentials (0.7.30):
#   neoessentials/balances.json: per-player economy balances (UUID-keyed map)
#   neoessentials/balances_activity.json: recent balance-change activity log
#   neoessentials/paytoggles.json: per-player /paytoggle preferences
#   neoessentials/transaction_history.json: server-wide transaction log (also per-player audit)
#   neoessentials/afk_data.json: per-player AFK state
#   NOT wiped (server-wide, not per-player): spawn.json, warps.json, dashboard_users.json,
#                                            languages/, scheduler/, moderation/
PLAYER_FILES=(
  "$DEV_DIR/usercache.json"
  "$DEV_DIR/usernamecache.json"
  "$DEV_DIR/config/cobblemon-gacha/runtime/players.json"
  "$DEV_DIR/world/data/starterkit/tracking.json"
  "$DEV_DIR/neoessentials/balances.json"
  "$DEV_DIR/neoessentials/balances_activity.json"
  "$DEV_DIR/neoessentials/paytoggles.json"
  "$DEV_DIR/neoessentials/transaction_history.json"
  "$DEV_DIR/neoessentials/afk_data.json"
)
PLAYER_GLOBS=(
  "$DEV_DIR/world/data/rctmod.player.*.stat.dat"
)

echo "[$(date '+%F %T')] Stopping $SERVICE"
systemctl stop "$SERVICE" || true
# Give the JVM a moment to flush and release file locks.
sleep 3

for d in "${PLAYER_DIRS[@]}"; do
  if [[ -d "$d" ]]; then
    n=$(find "$d" -mindepth 1 -maxdepth 1 | wc -l)
    echo "[$(date '+%F %T')] Wiping $d ($n entries)"
    # -mindepth 1 so we keep the dir itself but remove every child.
    find "$d" -mindepth 1 -delete
  else
    echo "[$(date '+%F %T')] Skipping (missing): $d"
  fi
done

for f in "${PLAYER_FILES[@]}"; do
  if [[ -e "$f" ]]; then
    echo "[$(date '+%F %T')] Removing $f"
    rm -f "$f"
  fi
done

# Glob expansion happens here (not in array assignment) so missing-match is benign.
shopt -s nullglob
for pattern in "${PLAYER_GLOBS[@]}"; do
  matches=( $pattern )
  if (( ${#matches[@]} > 0 )); then
    echo "[$(date '+%F %T')] Removing ${#matches[@]} files matching $pattern"
    rm -f "${matches[@]}"
  fi
done
shopt -u nullglob

# Ownership: the cobblemon-dev systemd unit runs as sysadmin. The wipe ran as root
# (sudo), but since we only deleted entries, the parent dirs already have the right
# owner. No chown needed.

echo "[$(date '+%F %T')] Starting $SERVICE"
systemctl start "$SERVICE"

echo
echo "Done. Players will need to rejoin to regenerate state."
echo "Tail logs: sudo journalctl -u $SERVICE -f"
