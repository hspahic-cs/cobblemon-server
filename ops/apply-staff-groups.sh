#!/usr/bin/env bash
# ops/apply-staff-groups.sh — define the default/moderator/admin permission groups on a server.
#
# WHY THIS IS A SCRIPT AND NOT A CONFIG FILE
#
# `config/neoessentials/permissions.json` looks like config but is really *state*: NeoEssentials
# loads it into a PermissionManager at boot and `PermissionSystem.shutdown()` calls
# `PermissionStorage.save()` on the way down, rewriting the file from memory. Shipping it through
# modpack/server-overrides/ therefore does nothing — the deploy rsyncs configs while the OLD server
# is still running, then restarts it, and the shutdown save clobbers the file before the new
# process ever reads it. Observed on the 0.33.0 dev deploy: rsync wrote the file at 23:50 owned by
# `deployer`, the restart at 23:55 left it owned by `sysadmin` with the pre-deploy contents.
#
# So groups are applied through NeoEssentials' own `/permissions` commands, which mutate the live
# PermissionManager. They then persist through the same shutdown save that used to be the problem.
# Same reasoning applies to permissions/playerdata.json (group *membership*) — that stays manual,
# see docs/staff-roles.md.
#
# Idempotent: `permissions group <g> add <node>` on a node the group already has is a no-op, and
# the groups are created only when missing. Safe to re-run after any deploy.
#
# Run AS the service user (sysadmin) — the screen session is owned by sysadmin:
#   ssh cobblemon bash -s -- dev   < ops/apply-staff-groups.sh
#   ssh cobblemon bash -s -- prod  < ops/apply-staff-groups.sh
#
# Use --dry-run to print the console lines instead of sending them:
#   ssh cobblemon bash -s -- dev --dry-run < ops/apply-staff-groups.sh
#
# This sets up the ROLES only. It never assigns a person to a group — that is deliberate, so
# re-running can't silently change who is staff. Assign with:
#   permissions user <name> setgroup admin|moderator|default
set -euo pipefail

ENVNAME="${1:?usage: apply-staff-groups.sh <dev|prod> [--dry-run]}"
DRY_RUN="${2:-}"

case "$ENVNAME" in
  dev|prod) ;;
  *) echo "apply-staff-groups: env must be 'dev' or 'prod', got '$ENVNAME'" >&2; exit 1 ;;
esac

SCREEN="cobblemon-${ENVNAME}"          # systemd ExecStart: screen -DmS cobblemon-<env>

# ── Group definitions ────────────────────────────────────────────────────────────────────────────
# Tier rules, spelled out because the failure modes are silent:
#   * `moderator` must NEVER appear in ops.json. NeoEssentials' opsBypassPermissions defaults to
#     true and its op test is hasPermissions(2), so op level >=2 grants EVERY NeoEssentials
#     permission — including /permissions, which mints more admins.
#   * `admin` members are op level 4 in ops.json, so they'd bypass all of this anyway; the explicit
#     grants exist so the tier still works if they're ever de-opped.
#   * cobblemon.staff.* are our own nodes, read by StaffPermissions in cobblemon-bridge/-auction/
#     /-feedback/-ranked. NeoEssentials' isValidPermission only enforces ^[a-z0-9._-]+$, so a
#     non-neoessentials namespace is fine.

DEFAULT_NODES="
neoessentials.afk
neoessentials.chat.ignore
neoessentials.chat.msg
neoessentials.chat.msgtoggle
neoessentials.chat.reply
neoessentials.chat.unignore
neoessentials.economy.balance
neoessentials.economy.baltop
neoessentials.economy.pay
neoessentials.economy.pay.toggle
neoessentials.info
neoessentials.item.dispose
neoessentials.item.powertool
neoessentials.item.repair
neoessentials.kits.list
neoessentials.kits.use
neoessentials.teleport.back
neoessentials.teleport.home
neoessentials.teleport.home.delete
neoessentials.teleport.home.list
neoessentials.teleport.home.set
neoessentials.teleport.request.accept
neoessentials.teleport.request.cancel
neoessentials.teleport.request.deny
neoessentials.teleport.request.tpa
neoessentials.teleport.request.tpahere
neoessentials.teleport.spawn
neoessentials.teleport.top
neoessentials.teleport.warp
neoessentials.teleport.warp.list
"

# Standard mod kit. Deliberately EXCLUDES: neoessentials.moderation.ban / .banip (permanent and IP
# bans escalate to an admin), neoessentials.item.* (item spawning), economy edits, kit/warp
# management, spawn.set, and permission management.
#
# .unban IS granted while .ban is not. That asymmetry is intentional — a moderator needs to reverse
# their own tempbans, and lifting a ban is the lower-risk half of the pair. It does mean a moderator
# can undo an admin's permanent ban; moderation.json has logBanActions on, so it's auditable. Drop
# the line if you'd rather it didn't.
MODERATOR_NODES="
neoessentials.afk.exempt
neoessentials.chat.mute
neoessentials.chat.mute.exempt
neoessentials.chat.mutelist
neoessentials.chat.socialspy
neoessentials.chat.spam.bypass
neoessentials.chat.staff
neoessentials.chat.unmute
neoessentials.economy.balance.others
neoessentials.permissions.list
neoessentials.moderation.banlist
neoessentials.moderation.freeze
neoessentials.moderation.freezelist
neoessentials.moderation.jail
neoessentials.moderation.jail.timed
neoessentials.moderation.jailinfo
neoessentials.moderation.jaillist
neoessentials.moderation.kick
neoessentials.moderation.notify
neoessentials.moderation.tempban
neoessentials.moderation.unban
neoessentials.moderation.unfreeze
neoessentials.moderation.unjail
neoessentials.moderation.vanish
neoessentials.moderation.vanishlist
neoessentials.teleport.admin.tp
neoessentials.teleport.admin.tphere
neoessentials.teleport.admin.tppos
neoessentials.teleport.home.others
neoessentials.teleport.jump
neoessentials.teleport.jumpto
neoessentials.teleport.tpr
cobblemon.staff.auctionadmin
cobblemon.staff.bp
cobblemon.staff.gymreturn
cobblemon.staff.tournament
cobblemon.staff.whois
cobblemon.staff.wild
"

# NOT granted to moderator: cobblemon.staff.wild.admin (wilderness box config is not a moderation
# duty). /ranked admin, /wildreset, /gacha grant etc. never got nodes at all — still op-4 only.
ADMIN_NODES="
neoessentials.*
cobblemon.staff.*
"

if [ "$DRY_RUN" = "--dry-run" ]; then
  console() { printf '%s\n' "$1"; }
  echo "# apply-staff-groups: DRY RUN for ${ENVNAME} — console lines that WOULD be sent:"
else
  command -v screen >/dev/null || { echo "apply-staff-groups: screen not found" >&2; exit 1; }
  screen -list 2>/dev/null | grep -q "[.]${SCREEN}[[:space:]]" \
    || { echo "apply-staff-groups: no running screen session '$SCREEN' — is cobblemon-${ENVNAME} up?" >&2; exit 1; }
  # Same console-injection pattern as ops/wilderness-arm-restart.sh. The short sleep keeps the
  # server's command queue from coalescing lines when we push ~70 of them back to back.
  console() { screen -S "$SCREEN" -p 0 -X stuff "$1"$'\r'; sleep 0.15; }
  echo "[$(date '+%F %T %Z')] ${ENVNAME}: applying staff group definitions"
fi

apply_group() {
  local group="$1" prefix="$2" inherits="$3" nodes="$4" node
  # Exact syntax matters and the failures are quiet — a wrong form logs "Incorrect argument for
  # command" to the console and the script sails on, because we're pushing lines into a screen
  # session and never see a return code. Verified against 1.0.2.5+build.1074:
  #   create   -> `permissions create group <name>`   (NOT `group create <name>`)
  #   inherit  -> `permissions group <g> inherit add <parent>`  (NOT `inherit <parent>`)
  console "permissions create group ${group}"

  # Authoritative, not additive: clear first so a node dropped from the lists above actually goes
  # away. Without this the script can only ever ADD — the first dev run left the old moderator
  # group's neoessentials.item.* (item spawning), economy.admin, kits.admin.*, warp.create/delete,
  # spawn.set and permissions.reload in place, i.e. exactly the powers this tier excludes.
  # Consequence: any group-level node granted by hand is wiped on the next run. That's intended —
  # these lists are the source of truth. Per-USER grants (`permissions user <n> add …`) are
  # untouched, so purchased /sethome slots and the like survive.
  console "permissions group ${group} clear"

  # NeoEssentials trims the prefix, so a trailing space cannot survive `setprefix` no matter how
  # it's quoted (greedyString reads it, then the handler strips it — the console log confirms:
  # "Set prefix '&2[Mod]' for group 'moderator'"). The tag/name separator therefore lives in
  # tablist.json's playerFormat instead, and chat.json hardcodes its own spacing.
  console "permissions group ${group} setprefix ${prefix}"

  [ -n "$inherits" ] && console "permissions group ${group} inherit add ${inherits}"
  while read -r node; do
    [ -n "$node" ] || continue
    console "permissions group ${group} add ${node}"
  done <<< "$nodes"
}

apply_group default   "&7"        ""          "$DEFAULT_NODES"
apply_group moderator "&2[Mod]"   "default"   "$MODERATOR_NODES"
apply_group admin     "&c[Admin]" "moderator" "$ADMIN_NODES"

# NO `permissions reload` here. PermissionSystem.reload() re-reads permissions.json from disk, which
# would discard everything above — the whole point is that the on-disk file is stale. The command
# handlers already persist each mutation via PermissionManager.save().

if [ "$DRY_RUN" != "--dry-run" ]; then
  cat <<EOF
[$(date '+%F %T %Z')] ${ENVNAME}: done. Verify in-game or on the console with:
  permissions list groups
  permissions info group moderator

Roles only — nobody was assigned. Assign people with:
  permissions user <name> setgroup admin
  permissions user <name> setgroup moderator

Group changes need a RELOG before the new commands tab-complete (brigadier caches
the per-player command tree at login). The chat tag updates immediately.
EOF
fi
