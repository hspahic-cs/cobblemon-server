# Staff roles: Admin and Moderator

Two staff tiers, both driven by NeoEssentials' built-in permission groups.

**Neither the groups nor their membership can be deployed as config files.** Both
live under `config/neoessentials/` but are really *runtime state* that
NeoEssentials owns — see [Why groups aren't deployed](#why-groups-arent-deployed).
Setting up a server is therefore two manual steps, each run once per instance:

1. **Define the roles** — `ops/apply-staff-groups.sh`, below.
2. **Assign the people** — `permissions user <name> setgroup …`, below.

The chat and tablist *formatting* (`chat.json`, `tablist.json`) does deploy
normally — NeoEssentials only rewrites the permission store, not those. So a
fresh server renders staff tags correctly the moment groups exist, but shows
nothing until step 1 has been run.

| Tier | Vanilla op | Chat tag | What they can do |
|---|---|---|---|
| **Admin** | `ops.json` level 4 | `&c[Admin]` (red) | Everything. Op level ≥2 bypasses every NeoEssentials permission check. |
| **Moderator** | **none** | `&2[Mod]` (green) | Day-to-day moderation + tournaments. No item spawning, no permanent bans, no `/op`, no permission edits. |
| Player | none | no tag | Default group. |

## The one rule that shapes everything

NeoEssentials' `opsBypassPermissions` defaults to **true**, and its op test is
`hasPermissions(2)`. So **any player with op level 2 or higher silently receives
every NeoEssentials permission** — including `/permissions`, which is how you
mint more admins.

A moderator must therefore have **no entry in `ops.json` at all**. Adding them
at level 1 doesn't help either: level 1 grants nothing useful and still doesn't
bypass. The moderator tier only works because NeoEssentials' own moderation
commands (`/kick`, `/tempban`, `/mute`, `/jail`, `/freeze`, `/vanish`, `/tp`)
gate purely on permission nodes and need no op.

## Defining the roles

Once per server, and again after anything resets the permission store. Run as
the service user (`sysadmin`) — the screen session it writes into is owned by
`sysadmin`:

```
ssh cobblemon bash -s -- dev  < ops/apply-staff-groups.sh
ssh cobblemon bash -s -- prod < ops/apply-staff-groups.sh
```

Add `--dry-run` to print the console lines instead of sending them. It defines
**roles only** and never assigns a person to a group, so re-running can't
silently change who is staff.

**It is authoritative, not additive.** Each group is `clear`ed before its nodes
are re-added, so a node dropped from the script actually goes away. Without that
the script could only ever add: the first dev run left the pre-existing
moderator group's `neoessentials.item.*` (item spawning), `economy.admin`,
`kits.admin.*`, `warp.create`/`delete`, `spawn.set` and `permissions.reload` in
place — precisely the powers this tier is supposed to exclude. The cost is that
any **group**-level node granted by hand is wiped on the next run; per-**user**
grants (`permissions user <n> add …`) are untouched, so purchased `/sethome`
slots and the like survive.

!!! warning "Failures here are silent"

    The script pushes lines into the server's screen session and never sees a
    return code. A malformed command logs `Incorrect argument for command` to the
    console and the script carries on reporting success. If you change it, run it
    once and grep the server log:

    ```
    grep -iE 'incorrect|unknown|not found' /opt/cobblemon-dev/logs/latest.log
    ```

    The exact forms, verified against `1.0.2.5+build.1074` — all three differ
    from the obvious guess:

    | Action | Correct | Wrong |
    |---|---|---|
    | create | `permissions create group <g>` | `permissions group create <g>` |
    | inherit | `permissions group <g> inherit add <parent>` | `… inherit <parent>` |
    | clear | `permissions group <g> clear` | — |

### Why groups aren't deployed

`config/neoessentials/permissions.json` looks like config but is state.
NeoEssentials loads it into a `PermissionManager` at boot, and
`PermissionSystem.shutdown()` calls `PermissionStorage.save()` on the way down,
rewriting the file from memory.

A deploy rsyncs configs **while the old server is still running**, then restarts
it — so the shutdown save clobbers the new file before the new process ever
reads it. Observed on the 0.33.0 dev deploy: the rsync wrote the file at 23:50
owned by `deployer`; after the 23:55 restart it was owned by `sysadmin` with the
pre-deploy contents, in NeoEssentials' own field order. Shipping the file through
`modpack/server-overrides/` is silently useless, which is why it isn't there.

Applying the groups through `/permissions` commands instead mutates the live
`PermissionManager`, and each command persists via `PermissionManager.save()`.
They then survive the same shutdown save that used to be the problem.

!!! warning "Never run `permissions reload` to apply groups"

    `PermissionSystem.reload()` re-reads `permissions.json` **from disk**, which
    discards anything applied in-memory. If the on-disk file is stale — and after
    a deploy it usually is — a reload silently reverts your setup. The script
    deliberately does not issue one.

## Appointing staff

Run from the server console (or in-game as an admin). The player must have
joined at least once so the server knows their UUID.

```
permissions user Titan1190X setgroup admin
permissions user SixthSense setgroup admin
permissions user <name> setgroup moderator
permissions user <name> setgroup default
```

Check who is what:

```
permissions users
permissions user <name> info
permissions groups
```

!!! warning "Group changes need a relog"

    Brigadier caches each player's command tree at login. After a `setgroup`,
    the newly-permitted commands won't tab-complete (and get rejected as
    unknown) until that player reconnects. This applies to NeoEssentials'
    commands and ours alike. The chat tag and tablist colour update
    immediately; only the command tree is stale.

!!! danger "Don't op a moderator"

    Adding a moderator to `ops.json` at level 2+ silently promotes them to full
    admin, because of the bypass above. If you need to grant one extra
    capability, add the single permission node instead:
    `permissions user <name> add neoessentials.teleport.warp.create`

## What a Moderator gets

**Moderation** — `/kick`, `/tempban`, `/unban`, `/banlist`, `/mute`, `/unmute`,
`/mutelist`, `/jail`, `/unjail`, `/freeze`, `/unfreeze`, `/vanish`,
`/socialspy`, `/staff` (staff chat channel), plus staff moderation
notifications.

`/unban` is granted even though `/ban` is not. That's deliberate and it is
asymmetric: a moderator needs to reverse their own tempban mistakes, and
lifting a ban is the lower-risk half of the pair. It does mean a moderator can
undo an Admin's permanent ban — `logBanActions` is on in `moderation.json`, so
that shows up in the moderation log. Drop
`neoessentials.moderation.unban` from the group if you'd rather it didn't.

**Movement** — `/tp`, `/tphere`, `/tppos`, `/jump`, `/jumpto`, `/tpr`, and
`/home <player> <home>` to reach a player's saved homes.

**Our mods** — `/wild <player>` (relocate someone to the wilderness),
`/feedback whois <anon-id>` (de-anonymise a bug reporter), `/bp add|set`
(grant Battle Points), `/ranked tournament open|close|bracket|cancel|play`,
`/gymreturn`, `/auctionadmin spawn|delete`.

## What a Moderator does *not* get

Permanent bans and IP bans (`/ban`, `/banip`) — escalate to an Admin. Also
excluded: item spawning (`neoessentials.item.*`), economy edits (`/eco`), kit
and warp management, `/setspawn`, permission management, `/op`, `/gamemode`,
every vanilla op command, and every custom-mod command not listed above —
including `/ranked admin` (ELO edits, arena geometry, decay), `/wild admin`
(wilderness box config), `/wildreset`, `/gacha grant`, `/market admin`, `/e4`,
`/monument`, `/hologram`, `/battletower`, `/gymtp`, `/testteam`,
`/battlespeed`, `/clearspawn`.

## How our commands hook into this

Every custom-mod command gates on vanilla `hasPermission(n)`, which only reads
`ops.json` — a non-op moderator would fail all of them. The six commands above
instead go through a small per-mod reflection bridge,
`StaffPermissions.check(source, node, opLevel)`, which is
`vanilla op ≥ opLevel` **OR** *player holds the NeoEssentials node*:

| Node | Command | Granted to |
|---|---|---|
| `cobblemon.staff.wild` | `/wild <player>` | moderator, admin |
| `cobblemon.staff.wild.admin` | `/wild admin …` | admin only |
| `cobblemon.staff.whois` | `/feedback whois` | moderator, admin |
| `cobblemon.staff.bp` | `/bp add\|set` | moderator, admin |
| `cobblemon.staff.tournament` | `/ranked tournament …` | moderator, admin |
| `cobblemon.staff.gymreturn` | `/gymreturn` | moderator, admin |
| `cobblemon.staff.auctionadmin` | `/auctionadmin` | moderator, admin |

The op arm is checked first, so admins and the server console behave exactly as
they did before this existed, and everything still works if NeoEssentials is
ever removed. If NeoEssentials is absent the node arm degrades to `false` with a
warn-once — it never throws, because these are `.requires()` predicates that
brigadier evaluates while building the command tree sent to every joining
client.

To open one more command to moderators: add
`StaffPermissions.check(it, "cobblemon.staff.<name>", <oldOpLevel>)` in place of
`it.hasPermission(<oldOpLevel>)`, then add the node to `MODERATOR_NODES` in
`ops/apply-staff-groups.sh` and re-run it on each server. Copy
`StaffPermissions.kt` into the mod if it isn't there yet — it is duplicated per
mod on purpose, same as the `EconomyBridge` pattern, so the mods stay
dependency-free of each other.

Note the split: the *code* half (which node gates which command) deploys
normally; the *grant* half (which group holds that node) does not, and needs the
script re-run. A node added in code but never granted just means moderators
silently don't get the command.

## Chat and tablist tags

Chat formats live in `modpack/server-overrides/config/neoessentials/chat.json`
under `chat.chat-format`. Keys are matched **literally** against the group name:

```
"default":          "&f{neoessentials_username}&7: &r{MESSAGE}"
"group:moderator":  "&2[Mod] &f{neoessentials_username}&7: &r{MESSAGE}"
"group:admin":      "&c[Admin] &f{neoessentials_username}&7: &r{MESSAGE}"
```

`ChatManager` resolves `group:<g>:world:<w>` → `group:<g>` → `world:<w>` →
`default`. The group is named `moderator`, so a `group:mod` key never matches —
that typo is why staff tags were invisible before 0.33.0, and the same typo was
in `tablist.json`'s `groupColors`. Only the `[Tag]` is coloured; the name and
message stay neutral so a staff line doesn't read as a server error.

**Group prefixes cannot carry a trailing space.** NeoEssentials trims them on
`setprefix`, so `&2[Mod] ` is stored as `&2[Mod]` — the console confirms it
(`Set prefix '&2[Mod]' for group 'moderator'`). Chat is unaffected because
`chat.json` hardcodes its own spacing rather than using `{prefix}`, but the
tablist reads `{prefix}` directly, so its `playerFormat` carries the separator:
`&f{prefix}&r {player}{suffix}`. Side effect: non-staff, whose prefix is the
glyph-less colour code `&7`, render with one leading space in the tablist.

Reload either file without a restart:

```
neoessentials reload
```

(`tablist reload` is not a console command, despite what `tablist.json`'s own
header comment says.)

Emoji rank badges (`chat.badges`) are **off on purpose**. The configured badges
are `⭐`/`🛡️`, and Minecraft's default font has no glyphs for them — with groups
actually assigned they would render as a missing-glyph box in front of every
staff message. The `[Admin]`/`[Mod]` prefixes are the indicator instead. Turning
badges back on requires `useCustomImages` plus a hosted resource pack.
