# PokéRogue Mode — Design

A run-based roguelite mode for the server, inspired by [PokéRogue](https://pokerogue.net/).
Built on our existing tower code in `cobblemon-bridge`.

> **Start with [`pokerogue-mode-plan.md`](./pokerogue-mode-plan.md)** — it holds the intent,
> the decisions and the reasoning behind them, and the phased plan. This document is the
> *how*: the architecture those decisions imply.

## Why not the CurseForge mod

The [Cobblemon Battle Tower](https://www.curseforge.com/minecraft/mc-mods/cobblemon-battle-tower)
mod is **All Rights Reserved with no public source**. Adapting it would mean decompiling a
closed jar and redistributing a modified build in our `.mrpack` — a license violation, and a
fork that rots on every upstream release. It also offers little we lack: it is RCT-API-based
like ours, and its Boss Mode depends on Mega Showdown.

What we already own covers most of the scaffolding:

| Piece | Where |
| --- | --- |
| Run loop: floor gating, arena teleports, victory/loss/flee, disconnect-tolerant resume | `custom-mods/cobblemon-bridge/.../battle/TowerGauntletHook.kt` |
| Run persistence | `custom-mods/cobblemon-bridge/.../tower/TowerStore.kt` |
| Roster, rotation, trainer summon/tag/cleanup | `custom-mods/cobblemon-bridge/.../tower/TowerManager.kt` |
| Synthetic party store (the run-party primitive) | `custom-mods/cobblemon-ranked/.../battle/RankedBattle.kt:525` |
| Shop GUI + economy | cobblemon-shop / Shopkeeper tabs |

> **These are references, not dependencies.** The mode ships as a standalone module
> (`custom-mods/cobblemon-roguelite`) that may not import `com.cobblemonbridge` — see plan §2.9.
> The table above is a guide to prior art worth reading before reimplementing, and to the
> integrations that belong behind an interface.

## Locked decisions

1. **Run-caught Pokémon do not persist.** Runs are self-contained; payout is currency/BP.
   This keeps the mode from becoming a legendary/shiny faucet that moves the server economy.
2. **Checkpointing is supported.** A run survives across sessions.
3. **Tera and Dynamax are enabled inside the run only**, gated physically (see below).
4. **The battle AI is being rewritten.** Design assumes an AI that can use gimmicks; do not
   build the ladder around today's AI limits.

## Architecture

### Run party — never touch the real party

Because nothing persists (decision 1), the run party lives **entirely in our own store**. We
never swap or mutate the player's `PlayerPartyStore`. Each battle is handed a synthetic
store built along the lines of `RankedBattle.buildTempParty()`. Captures during a run
write to the run store, not to Cobblemon storage.

**Two changes from that method, both load-bearing.** It calls `pokemon.clone()`, whose signature
is `clone(newUUID: Boolean = true, …)` — copied literally, every battle Pokémon gets a fresh
UUID, `RunState.kill()` (which matches on UUID) silently no-ops, and permadeath never fires. It
also calls `heal()` on each clone, which would full-restore the party every wave and remove the
attrition the mode is built on. Use `clone(newUUID = false)` and no heal, or hand the run
Pokémon over uncloned.

This removes the largest correctness risk in the whole design: no crash, restart, or botched
restore can cost a player their real Pokémon.

### Wave generation

**Decided: authored RCT trainers, scaled at runtime** (plan §2.6, revised 2026-07-27). The
earlier decision — building opponent teams in Kotlin and driving the battle directly — rested on
the claim that RCT teams cannot be scaled at runtime. They can:
`GymBattleAdjustHook.applyToBattle` already mutates `bp.effectedPokemon.level` at
`BattleStartedEvent.Pre`, today for the player's side under gym level caps. The same shape
applied to the NPC actor scales an authored team to any wave level.

This buys trainer skins, names, dialogue, summon/cleanup machinery, and per-trainer AI
configuration for free, and avoids a bespoke battle driver.

Level scaling does **not** scale movesets, EVs, or held items, so the ladder wants a few authored
bands (early/mid/late) with level smoothing difficulty *within* a band, rather than one team
stretched across the whole run.

*Unverified:* NPC-side level mutation is proven for the player side only. Confirm on the dev VM
before committing to it.

### Fixed boss trainers

Every N waves the run serves a **fixed trainer with an authored team**, distinct from the scaled
wave opponents. These are authored specifically for this mode, as RCT trainers.

Rejected: reusing our own `gym_01`–`gym_24`, which players already fight, and transcribing
PokéRogue's rosters, which becomes a published derivative under plan §1.2. PokéRogue code is
AGPL-3.0-only and its docs/assets are CC-BY-NC-SA-4.0 — no source is vendored here, and their
behaviour is described in our own words. What ports is the **pattern**, not the data.

### Rewards — two mechanisms, no Showdown fork

PokéRogue's stacking modifiers have no Showdown equivalent (one held item per mon). Split
the fantasy across two supported mechanisms:

1. **Persistent run-party state**, applied between waves — EVs/vitamins, levels, nature
   mints, ability patches, evolution, held items, TMs. Real Cobblemon state, no sim
   involvement. This is where "stacking" power lives.
2. **Active consumables via datapack bag-item scripts — deferred** (plan §2.4), kept here as a
   record of what was investigated. Cobblemon loads bag-item JS from
   `data/<ns>/bag_items/*.js` and pushes it to Showdown at reload
   (`BattleFormat`/`BagItems.reload()`, `sendRegistryData(..., "bagItem")`). The scripts
   receive the **raw Showdown battle object** — Cobblemon's own `revive.js` mutates
   `pokemon.fainted`, pushes an `instaswitch` onto `battle.queue`, and emits battle
   messages. Arbitrary in-battle effects are reachable this way, as a datapack plus a small
   `BagItem` registration.

Not reachable: passive auto-triggering modifiers (auto-berry at 50%, Multi Lens extra hits).
Those get expressed as mechanism 1 instead.

### Run arenas

Plan §1.1 says runs happen in run arenas, not in the shared world; §2.5 needs somewhere to put
`power_spot` blocks that isn't the shared world; §4 says N concurrent runs need instancing. Those
are one problem. Plan §1.2 constrains the answer: the mode ships as a standalone mod, so the
arena cannot be a hand-built place on our server, cannot use our warp system, and has to work in
single-player on a world the mod has never seen.

**Decided: one static, mod-declared arena dimension, instanced by a coordinate slot grid, with a
config override for owners who want their own arenas.** Reasoning below.

#### Options considered

**A — a dimension created dynamically per run.** One `ServerLevel` per run, registered at run
start, unregistered at run end.

This *is* possible on NeoForge 1.21.1 — more so than expected, so it was checked rather than
dismissed. `MappedRegistry.unfreeze()` is public, `MinecraftServer.forgeGetWorldMap()` and
`markWorldsDirty()` both survive in 1.21.1 (marked "Forge Internal use Only", not removed), and
`createLevels` posts `LevelEvent.Load`, so the whole Infiniverse-shaped recipe is reachable
without reflection. Feasibility is not the objection.

The objections are cost and blast radius. Every level ticks every server tick, carries its own
chunk source, entity storage and POI storage, and gets its own region directory on disk — so N
runs is N ticking worlds. Constructing a `ServerLevel` by hand means reproducing `createLevels`'
argument list (`DerivedLevelData`, shared `RandomSequences`, the world-border delegate listener)
and keeping it correct across Minecraft bumps. Unregistering a level while anything still
references it is the genuinely dangerous part, and deleting its region directory afterwards is
manual work on a live server. Worst, `WorldGenSettings` is encoded from the dimension registry at
save time, so runtime-registered stems can be **baked into `level.dat`** — a published mod that
accretes one dead dimension entry per run ever started, on someone else's world, is not a thing
to ship. *Unverified:* whether the baking actually happens on a normal save path, or only on
world creation. It did not need resolving, because the cost argument already loses.

**B — one static dimension declared by the mod, instanced by coordinates.** A
`dimension_type` + `dimension` JSON pair in the mod's own `data/`, a void flat generator, and a
grid of arena slots inside it. Chosen; detail below.

**C — a generated structure placed in the existing world.** No dimension, arena stamped
somewhere far out in the overworld.

Rejected. It fails the isolation contract at the first hop: the arena is somewhere players can
walk to, mine, grief and log out in, and other players are in the same world. It has to find
empty land on a world the mod did not generate, which cannot be guaranteed; it collides with
claim/protection mods and, on our server specifically, with the wilderness prune box. It buys
nothing that B doesn't, since concurrency still needs a coordinate grid.

**D — config-declared arena coordinates the owner builds by hand.** This is what the tower does
today (`/tower setfloor`, positions in `TowerStore`) and what our `multiworld:arena*` dimensions
are.

Rejected *as the default*, kept as an override. It is the best-looking option and the worst
default: it requires manual setup before the mode works at all, which is hostile for a published
mod and unusable in single-player; N concurrent runs means N hand-built arenas configured up
front; there is nothing to guarantee a `power_spot` was placed; and cleanup of an abandoned arena
is a human with a command. As an *override* it is exactly right — see "Owner override" below.

**E — a custom `ChunkGenerator` that emits the arena at every slot.** A refinement of B rather
than a rival: the arena becomes a property of the terrain, so there is no placement step, no
re-stamping, and a deleted region file regenerates identically. Costs a `ChunkGenerator`
implementation plus codec registration, and gives up datapack-overridable arena builds. Worth
revisiting if template stamping turns out to be the annoying part; not worth it first.

**F — no arena; battle where the player stands, in a "run bubble".** Cobblemon battles do not
strictly need a stage. Rejected: it is the one option that plainly violates §1.1, and it leaves
`power_spot` with nowhere to live that isn't the shared world.

#### The recommendation in detail

**The dimension.** `data/cobblemon_roguelite/dimension_type/arena.json` and
`data/cobblemon_roguelite/dimension/arena.json`, shipped in the mod jar. NeoForge loads every
mod's `data/` as an always-on server datapack (`ResourcePackLoader`, `PackType.SERVER_DATA`), so
this needs no owner action and works in single-player.

It also lands on **existing** worlds, which was the thing worth verifying:
`WorldDimensions.bake` unions the datapack `LevelStem` registry with the world's saved dimension
map, so installing the mod into a world in progress adds the dimension on next load. Generator is
`minecraft:flat` with `layers: []`, `lakes: false`, `features: false`,
`structure_overrides: []`, biome `minecraft:the_void` — nothing generates, and nothing but our
own placement exists in it.

The `dimension_type` does real work here. `monster_spawn_light_level: 0` plus
`monster_spawn_block_light_limit: 0` kills vanilla hostile spawning; `has_raids: false`,
`piglin_safe: true`, `bed_works: false`, `respawn_anchor_works: false`; `fixed_time` locks the
arena to daylight if we want it. All confirmed present in 1.21.1's `DimensionType` codec.

Cobblemon's own spawner is not covered by any of that — it is player-driven and does not consult
dimension type. Cobblemon has **no global dimension blacklist** (`SpawningCondition` has a
per-spawn-detail `dimensions` allowlist, which is the wrong direction for us). The lever is
`CobblemonEvents.ENTITY_SPAWN`, which is a `CancelableObservable` — cancel any natural spawn whose
level is the arena dimension. Cheap and total. *Unverified:* whether the void biome would produce
any Cobblemon spawns at all; the cancel is belt-and-braces either way.

**Instancing.** A slot grid. Slot *n* maps to a fixed `(x, z)` by `x = (n % width) * spacing`,
`z = (n / width) * spacing`, spacing configurable and defaulting to something comfortably past
max render distance (32 chunks = 512 blocks), so 1024. Slots are allocated at run start,
recorded on `RunState`, and released on run end or expiry. `RunStore` already enumerates every
active run, so it is the allocator's source of truth — no second registry to keep consistent.
`maxConcurrentRuns` is a config bound, and because slots are *reused*, disk growth is bounded by
that number rather than by runs ever played. That bound is the main practical advantage over
option A.

**The arena itself.** A `StructureTemplate` from `data/cobblemon_roguelite/structure/*.nbt` —
1.21.1's structure resource directory is `structure`, singular — placed at the slot on
assignment. Shipping it as a datapack structure means a server owner can replace the arena build
without touching the jar, matching the reward-table decision (§2.12).

**Stamp on assignment, not on release.** Re-place the template and sweep entities in the slot box
when the slot is handed out. Doing it on release means a crash between run-end and cleanup leaves
a dirty arena for the next player; doing it on assignment makes cleanup idempotent and
crash-proof, which is the same reasoning §2.10 applies to interrupted battles.

**Getting in and out.** Store the entry point — dimension, position, rotation — on `RunState`
before the first teleport, and return the player to it on completion, wipe, or abandon. On login
inside the arena dimension: if the player has an active run, resume it (§2.10 decides whether
that costs them anything); if they do not, eject to the stored entry point, falling back to world
spawn if that dimension is gone. Without that reconciliation the arena is a void trap for anyone
whose run was voided while they were offline.

**Server restart mid-run is the option's best property.** The dimension is declared statically,
so it exists on every boot with nothing to recreate and no ordering hazard between our mod's
startup and player login. The slot assignment is persisted in `RunStore` alongside the rest of
the run. Nothing about the arena has to survive a restart *as state*, because it is derived: slot
index in, coordinates out. Option A has to rebuild a live `ServerLevel` before the first player
logs in or the player is silently dumped in the overworld.

**Uninstalling the mod is survivable, which matters for publication.** If the dimension
disappears, `PlayerList` logs `Unknown respawn dimension …, defaulting to overworld` and places
the player in the overworld rather than failing; and NeoForge patches the saved dimension map to
use `LenientUnboundedMapCodec`, which drops entries it cannot decode instead of failing the whole
`level.dat` parse. A player who removes the mod mid-run loses the run, not the save.

**Gimmick confinement falls out of it.** `power_spot` goes in the arena template, placed only if
Mega Showdown is loaded (registry lookup by id, soft dependency preserved — see below). Because
slots are 1024 apart and `powerSpotRange` is 20, no arena's power spot can reach another's, and
none of them can reach the shared world.

**Owner override.** Config: `arena.dimension` (default ours), `arena.template`, `arena.spacing`,
`arena.maxConcurrentRuns`, and an explicit list of arena origins that, when set, replaces the
slot grid. That is option D as a first-class configuration rather than the default — it lets our
own server point the mode at hand-built `multiworld:` arenas later without a code change, and it
covers hosts that refuse extra dimensions.

#### Open questions this leaves

- **Chunk tickets.** A present player keeps the arena loaded, so no forced chunks are needed for
  the common path. If an RCT trainer has to be summoned *before* the player arrives, the arena
  chunks need a brief force-load first — the tower hit exactly this (`forceload add` before the
  midnight rotation, plus a settle delay because `summon_persistent` materialises on a later
  tick). Whether our summon ordering needs the same treatment is unknown until the wave loop
  exists.
- ~~**One room for the whole run, or a room per wave band?**~~ **Decided 2026-07-27: re-stamp per
  wave band**, for the aesthetic — a run that visibly changes scenery as it deepens reads better
  than one room for fifty waves, and the machinery makes it nearly free. The slot stays allocated
  for the whole run; only the template stamped into it changes at band boundaries. Which templates
  and how many bands are still a content call. Note this makes stamp-on-assignment (above) the
  rule for *every* stamp, not just the first: a band transition re-stamps and sweeps the same way,
  so a crash mid-transition is no different from a crash mid-run.
- **Does Cobblemon behave in a void-biome dimension?** *Unverified.* Battle flow, capture flow and
  `PokemonEntity` placement on a floating platform have not been tested there. This is the single
  biggest unknown in the section and it is dev-VM-testable as soon as there is a run loop.
- **Does `mega_showdown:power_spot` work when placed programmatically?** *Unverified* — no source
  available. If it needs a block entity, a multiblock, or an activation step, template placement
  may not be enough.
- **Slot reuse vs. region-file growth over long uptimes.** Bounded by `maxConcurrentRuns` in
  theory; not measured.
- **Run expiry is a prerequisite, not an extra.** Plan §5 already lists it as open. It is what
  frees slots, so the grid silently fills up without it.

### Gimmick confinement

Mega Showdown implements Tera/Dynamax **mod-side with items and world state**, so
`BattleFormat.ruleSet` is not the lever, and the `mega`/`teralization`/`dynamax` config
booleans are **global** — useless for "on in one place only".

Actual gates: Dynamax = `dynamax_band` + within `powerSpotRange: 20` of a
`mega_showdown:power_spot`; Tera = `tera_orb` + 50 shards. All three items are craftable and
`power_spot` has no worldgen.

Confinement plan:

- Craft-ban `power_spot`, `dynamax_band`, `tera_orb` via `server-craft-bans`.
- Keep `dynamaxAnywhere: false`; place `power_spot` blocks in run arenas only — in the arena
  template, so every slot gets one and nothing outside the arena dimension does
  ([Run arenas](#run-arenas)).
- Issue run-scoped orb/band on entry, revoke on exit **and** on login, from the mode's own
  interrupted-run reconciliation (covers crash-mid-run leakage). `TowerGauntletHook` does this
  for the tower and is worth reading, but cannot be reused — plan §2.9.
- **Gap:** none of that stops a player stashing a run-issued `tera_orb` in a chest mid-run and
  collecting it later. Craft-bans stop new ones, not stashed ones. Likely fix is tagging issued
  items and voiding them when the holder is not in a run.

Mega Showdown is a **soft** dependency: absent it, the gimmick ladder degrades to off and the
mode still runs.

Wave → format/gimmick mapping is a **config table**, not code, so the ladder can change
without a build. Gimmick waves default off on dev until the new AI lands.

### AI and the bridge payload

The **shipped default AI** is RCT's own types or Cobblemon's `StrongBattleAI` — not our
poke-engine bridge, which calls a self-hosted Python service nobody else has (plan §2.8). The
bridge is an optional server-side upgrade behind an interface declared in this module.

Carry Tera type and gimmick availability in the AI payload from day one, even while today's AI
ignores it — otherwise a better AI arrives to find the data was stripped at the boundary.

## Phasing and open questions

Both live in [`pokerogue-mode-plan.md`](./pokerogue-mode-plan.md) — §3 and §5 — so there is one
copy to keep current.

## Risks

- **Concurrency:** N simultaneous runs need instanced arenas. Addressed by
  [Run arenas](#run-arenas) — a mod-declared arena dimension with a coordinate slot grid. The
  tower's per-floor teleport is prior art, not the mechanism.
- **Test loop:** no working local dev server for these mods; everything is runtime-tested on
  the dev VM. This sets iteration cadence more than code volume does.
- **Craft-ban sequencing:** shipping the craft-bans before the mode exists removes Tera and
  Dynamax access server-wide with nothing replacing it. Land them together.
