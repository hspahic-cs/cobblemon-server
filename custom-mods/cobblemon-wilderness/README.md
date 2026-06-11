# Cobblemon Wilderness Reset

Caps wilderness world growth by regenerating chunks that lie **wholly outside** a
persistent keep-box. Server-side only. Ships **disabled** — it does nothing until you
deliberately turn it on.

## How it works

- A region file (`r.X.Z.mca`, 512×512 blocks) is deleted only if it does **not touch**
  the keep-box. Regions straddling an edge are always kept, so we never delete a chunk that
  touches the box.
- By default (`snapToRegions: true`) the box is expanded outward to whole-region boundaries
  before use, so the enforced keep-zone is exactly region-aligned — **what you configure is
  what gets kept, no hidden rounding.** `/wildreset status` shows both the configured and
  the enforced box. To pick a clean box yourself, use region-boundary coordinates: mins at a
  multiple of 512, maxes at a multiple of 512 minus 1 (e.g. `-20480 .. 20479` = regions
  −40..39). Set `snapToRegions: false` to use the box verbatim.
- Deleted chunks regenerate fresh next time a player visits. The matching files in
  `region/`, `entities/`, and `poi/` are all removed together.
- **All deletion happens at server boot only** (`ServerAboutToStartEvent`, before any
  level loads — chunks guaranteed unloaded, no open region files). Live commands only
  preview or arm the next boot's pass. Nothing destructive ever runs on a live world.

## Config — `config/cobblemon-wilderness/authored/config.json`

```json
{
  "enabled": false,
  "dryRun": true,
  "intervalDays": 14,
  "dimensions": ["minecraft:overworld"],
  "box": { "minX": -20480, "minZ": -20480, "maxX": 20479, "maxZ": 20479 },
  "snapToRegions": true
}
```

Two independent safety gates, both default-safe:
- `enabled` — master switch. `false` = the mod is inert.
- `dryRun` — when `true`, runs only **log** what they would delete (no deletion).

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
