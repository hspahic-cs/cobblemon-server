# Cobblemon Wilderness Reset

Caps wilderness world growth by regenerating chunks that lie **wholly outside** a
persistent keep-box. Server-side only. Ships **disabled** — it does nothing until you
deliberately turn it on.

## How it works

- A region file (`r.X.Z.mca`, 512×512 blocks) is deleted only if it does **not touch**
  the keep-box. Regions straddling an edge are always kept, so we never delete a chunk that
  touches the box.
- A **circuit breaker** (`maxDeleteFraction`, default 0.9) aborts a run that would delete more
  than that fraction of a dimension's regions and deletes nothing — a safety net against a
  mis-typed box (e.g. collapsed to a point). `/wildreset preview` flags this too. Set to 1.0
  to disable.
- By default (`snapToRegions: true`) the box is expanded outward to whole-region boundaries
  before use, so the enforced keep-zone is exactly region-aligned — **what you configure is
  what gets kept, no hidden rounding.** `/wildreset status` shows both the configured and
  the enforced box. To pick a clean box yourself, use region-boundary coordinates: mins at a
  multiple of 512, maxes at a multiple of 512 minus 1 (e.g. `-20480 .. 20479` = regions
  −40..39). Set `snapToRegions: false` to use the box verbatim.
- Deleted chunks regenerate fresh next time a player visits. The matching files in
  `region/`, `entities/`, and `poi/` are all removed together.
- **A snapshot is taken right before the prune** (`backupBeforeReset`, default on): on a real
  run, each to-be-deleted file is **moved** into a timestamped dir under `backupDir` rather than
  unlinked — the move *is* the deletion (the chunk still regenerates), so it leaves a restore
  copy at ~no extra disk on the same filesystem. The newest `backupRetention` snapshots are kept.
  This is a per-prune safety net **separate from** (not a replacement for) any scheduled
  world-snapshot — by default it lands at `<server-dir>/wilderness-snapshots/`, outside a typical
  `world/`-only snapshot's scope.
- **All deletion happens at server boot only** (`ServerAboutToStartEvent`, before any
  level loads — chunks guaranteed unloaded, no open region files). Live commands only
  preview or arm the next boot's pass. Nothing destructive ever runs on a live world.
- **Optional: relocating structures** (`reseedStructuresOutsideBox`, off by default). Deleting a
  chunk regenerates it from the same seed — *identical* terrain. With this on, structures and
  monuments in chunks **outside the box** are moved to new spots each reset cycle, so a
  pruned-then-revisited frontier has its landmarks somewhere new. It works by mixing a per-cycle
  salt (bumped on every real prune, stored in runtime state) into vanilla structure placement
  (`RandomSpreadStructurePlacement`) for grid cells lying wholly outside the box — so it covers
  **every** structure set at once (vanilla *and* modded, e.g. Legendary Monuments), no list to
  maintain. **Terrain is unchanged** (only placement moves), and inside the box placement is
  byte-identical. Caveats: structures still only appear where their biome allows, so variety is
  bounded; the hook has no dimension context, so it applies to all dimensions' structures beyond
  the X/Z box (only the pruned overworld actually regenerates them).

## Config — `config/cobblemon-wilderness/authored/config.json`

```json
{
  "enabled": false,
  "dryRun": true,
  "intervalDays": 14,
  "dimensions": ["minecraft:overworld"],
  "box": { "minX": -20480, "minZ": -20480, "maxX": 20479, "maxZ": 20479 },
  "snapToRegions": true,
  "warnPlayersOutsideBox": true,
  "displayTimeZone": "America/New_York",
  "maxDeleteFraction": 0.9,
  "backupBeforeReset": true,
  "backupDir": "wilderness-snapshots",
  "backupRetention": 5,
  "reseedStructuresOutsideBox": false
}
```

Two independent safety gates, both default-safe:
- `enabled` — master switch. `false` = the mod is inert.
- `dryRun` — when `true`, runs only **log** what they would delete (no deletion).

Snapshot knobs:
- `backupBeforeReset` — `true` (default) moves pruned files into a snapshot before deletion; `false` deletes outright.
- `backupDir` — snapshot location. Relative paths resolve against the server dir; absolute paths used as-is. Keep it outside your scheduled world-snapshot's scope.
- `backupRetention` — how many recent prune snapshots to keep (`0` = keep all).

Frontier knob:
- `reseedStructuresOutsideBox` — `false` (default). `true` relocates structures/monuments outside the box each reset cycle (see "relocating structures" above). Takes effect from the first real prune after you enable it; needs the mixin jar (this build).

## Restore from a prune snapshot

Each prune writes `<backupDir>/<timestamp>/<dimension>/{region,entities,poi}/r.X.Z.mca`. To bring
pruned terrain back, **stop the server** and move the files back into the world (the chunks then
load from the restored data instead of regenerating):

```sh
ts=wilderness-snapshots/2026-06-25_02-55-18/minecraft_overworld
for sub in region entities poi; do
  cp -an "$ts/$sub/." "world/$sub/"      # -n: never clobber a newer file
done
```

Note `minecraft:overworld` is written as `minecraft_overworld` (the `:` is path-sanitized).

## Player warnings

While `enabled` is true and `warnPlayersOutsideBox` is on, players who stray outside the
keep-box get a chat warning — once when they cross the boundary outward, and again on login
if they're already outside — naming the safe build zone and when the area resets. A short
"back inside, builds are safe" message fires when they return. Warnings are suppressed while
`enabled` is false, so nobody is alarmed during the confirm-bases phase.

## Commands (`/wildreset`, op level 4)

- `status`  — show config, box, and per-dimension last/next reset.
- `preview` — read-only scan of the live world; reports regions/MB that *would* be deleted. Safe anytime.
- `now`     — arm a reset for the **next restart** (warns if `enabled=false` or `dryRun=true`).
- `cancel`  — disarm a pending `now`.

## Safe rollout

1. Confirm with every player that their base sits inside `box`. Adjust `box` as needed.
2. Run `/wildreset preview` (or enable with `dryRun=true`) and read the "would delete" report.
   Cross-check the deleted region coords against known builds.
3. Once satisfied, set `dryRun=false`. The first real reset runs on the next interval, or
   immediately if you `/wildreset now` + restart.

> First boot with `enabled=true` records a baseline and does **not** reset — this prevents
> a surprise wipe the moment you flip the switch. Use `/wildreset now` to force the first one.
