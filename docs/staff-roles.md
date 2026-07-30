# Staff roles: Admin and Moderator

Two staff tiers, both driven by NeoEssentials' built-in permission groups. The
groups are defined in version control at
`modpack/server-overrides/config/neoessentials/permissions.json` and deploy with
every release. **Who is in each group is runtime state** — it lives in
`config/neoessentials/permissions/playerdata.json` on each server and is never
overwritten by a deploy, so you assign people once per instance with a command.

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
`it.hasPermission(<oldOpLevel>)`, then add the node to the `moderator` group in
`permissions.json`. Copy `StaffPermissions.kt` into the mod if it isn't there
yet — it is duplicated per mod on purpose, same as the `EconomyBridge` pattern,
so the mods stay dependency-free of each other.

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

Emoji rank badges (`chat.badges`) are **off on purpose**. The configured badges
are `⭐`/`🛡️`, and Minecraft's default font has no glyphs for them — with groups
actually assigned they would render as a missing-glyph box in front of every
staff message. The `[Admin]`/`[Mod]` prefixes are the indicator instead. Turning
badges back on requires `useCustomImages` plus a hosted resource pack.
