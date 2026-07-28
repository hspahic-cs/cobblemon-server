# Roguelite Mode — Design

A run-based roguelite mode, inspired by [PokéRogue](https://pokerogue.net/), built as the
standalone mod `custom-mods/cobblemon-roguelite`. It depends on Cobblemon and nothing else of
ours (plan §2.9).

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

## Decisions

The decision record lives in [`pokerogue-mode-plan.md`](./pokerogue-mode-plan.md) §2 and is the
authority — it is kept current and there are now twenty-odd entries, several of which reverse
earlier ones. This document does not restate them; where a section here depends on a decision it
cites the section number.

## Module layout

What exists today. Nothing is wired to a battle yet.

| Package | Holds |
| --- | --- |
| `run/` | The run model and its lifecycle: `RunState` / `RunStore` (NBT round-tripping, permadeath, world save data), `RunStart` (the start *order*), `RunProgress` (between-wave decisions), `RunDepthGate` (§2.18's badge gate), `RunController` (the wiring), `RunCommands`, and `RunWaves` — the wave-battle seam, which nothing implements |
| `integration/` | Host seams: `RunCharges` (entry fee), `RunPayouts` (optional bonus on top of the payout), `RunBattleAi` (opponent AI). Each has a working default so the mod runs standalone |
| `data/` | `RogueliteDataRegistry`, the datapack convention: `data/<ns>/roguelite/<folder>/<name>.json` |
| `data/reward/` | Between-wave reward tables — a weighted draw, because variance inside a run is the point |
| `data/payout/` | End-of-run payout tables — a deterministic filter, because the payout is the audited channel out and two identical runs must pay identically |
| `wave/` | Deterministic `(seed, wave)` → wild species and level; the shared level curve |
| `starter/` | The Pokédex-gated starter offer |
| `composition/` | Wave number → encounter kind, level and reward table id |

Two conventions worth knowing before adding to it. Everything data-driven goes through
`RogueliteDataRegistry` rather than a second loader. And anything server-specific — our economy,
our arenas, the poke-engine AI bridge — is registered *into* the module through `integration/`,
never compiled against; nothing here may import `com.cobblemonbridge`.

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

### Wave composition — two paths, not one

A run is **200 waves** (plan §2.19) and they are not all the same kind of thing (§2.14). The
split is what makes catching possible at all: §2.13 makes catching the party system, and
trainer-owned Pokémon are never catchable, so a run of pure trainer battles could not grow a
party.

| Kind | Count in a run | Built as | Catchable | Levels |
| --- | --- | --- | --- | --- |
| Wild | 160 | Runtime-generated, on Cobblemon's own wild-battle and capture flow | Yes | Set at spawn |
| Trainer | 20 | Authored RCT trainer | No | Mutated at battle start |
| Boss | 20 | Authored RCT trainer | No | Same, plus the ×1.2 boss multiplier |

`composition/WaveComposition` owns which is which. It is a pure function of wave number and
config — deliberately *not* of the seed, since which wave is a boss must not vary between runs —
and it returns the level by delegating to the shared curve rather than reimplementing it.

**The level curve is PokéRogue's, verbatim**: `1 + wave/2 + (wave/25)²`, bosses ×1.2, with a
jitter that narrows as waves deepen. It clamps at 100, because Cobblemon's `maxPokemonLevel` is
100 and global — so from about wave 138 (bosses ~120) the last third of a run is flat, and its
difficulty has to come from teams, EVs, items and gimmicks instead (§2.19).

**Trainer-side level mutation: verified on dev 2026-07-28.** Forcing a live RCT trainer's team to
L50 at `BattleStartedEvent.Pre` worked — the battle showed L50, stats rescaled, and a recheck
three seconds later still read L50, so RCT does not re-derive its team afterwards.

The timing is structural rather than lucky: `startBattle` posts `BATTLE_STARTED_PRE` and only
then calls `startShowdown`, which packs the team by reading `effectedPokemon` fresh. Every Pre
subscriber has run before the engine is handed anything.

It is also simpler than assumed. The NPC team is a `safeCopyOf` battle clone, so mutating it
never touches the authored trainer and needs **no** restore machinery —
`GymBattleAdjustHook`'s NBT save/restore exists only because `playerOwned()` makes
`effectedPokemon === originalPokemon` on the player's side.

Level scaling does **not** scale movesets, EVs or held items, so trainer and boss waves want a
few authored bands rather than one team stretched across 200 waves.

### Boss trainers

Bosses are authored trainers, distinct from the scaled wave opponents, and they follow
PokéRogue's rosters — transcribed into our own trainer format as **server-side datapack content
only**. A published build ships the schema and a neutral authored default instead; nothing is
vendored into mod source either way (plan §2.7, and note the licence reasoning there).

**Their schedule does not fit the interval rule.** PokéRogue puts the E4 at waves 182/184/186/188
and the champion at 190 — none of them multiples of ten. A single "boss every 10th wave" cannot
express that, so it needs an explicit authored-wave list or a fixed-encounter override above the
interval rule. `WaveComposition` is shaped to accept one; nothing has designed it yet.

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

**Rewards past the level cap.** The party levels by battle EXP on the same curve as the opponents
(§2.21) and hits 100 at roughly the same wave they do. From about wave 138 an EXP or level reward
is dead weight, so the table has to change character over that band — EVs, held items, ability
patches, gimmicks. That is the same band whose escalation §5 lists as unresolved; it is one
problem, not two.

### Rewards vs payout — two tables, deliberately different

They look alike and behave oppositely, so the distinction is worth stating once:

| | Reward tables (`data/reward/`) | Payout tables (`data/payout/`) |
| --- | --- | --- |
| When | Between waves, inside a run | Once, at run end |
| Selection | **Weighted draw** — variance inside a run is the point | **Deterministic filter** — two identical runs must pay identically |
| Pays | Run-scoped state that dies with the run | Real items that leave the run |
| Why | Roguelite texture | The single audited channel out of a sealed run (§1.1) |

The payout being auditable is the whole reason it is not a draw. It is also why `PayoutGrant` is
sealed to items with no command kind: a `"run": "/give …"` field would let a datapack pay
currency or permissions, leaving the isolation contract enforced by whatever an owner typed into
JSON rather than by the code.

### Which table a run pays from is pinned at run start

`RunState.payoutTable` is written when the run is created, from `RunConfig.payoutTable`, and read
back at run end. The alternative — read the live config when the run finishes — was rejected
because §2.19 makes a run a multi-session commitment: an owner retuning between somebody's wave 3
and their wave 200 would change what an in-flight run pays, invisibly, which is the same class of
"a run in progress changed under the player" the seed exists to prevent.

What it pins is the table **id**, not the table **contents**. A datapack reload still changes what
the entries pay. Freezing contents would mean copying the resolved table into every checkpoint and
versioning it — a guarantee nobody asked for, against a change only an operator can make.

A run started before any table was configured stores null and falls back to
`PayoutTables.DEFAULT_TABLE` at the end, which is also the shipped state: nothing ships at that id
(§2.20 deferred the contents), so a finished run today resolves an empty payout and logs the miss.

### Money in, items out

Entry costs currency and the payout is not currency (plan §2.18, §2.20). That asymmetry shapes
the seams: the module grants payout items *itself*, so a published build pays out correctly with
nothing registered — but it has no economy and must never grow one, so charging goes through
`integration/RunCharges`.

Two defaults, both failing toward "the mode is playable": an unregistered charge provider means
runs are **free** (refusing would make a standalone install unplayable, and free is the only
coherent price where no currency exists), and an unregistered payout provider adds **nothing** on
top of the table the module already paid. The first has a real cost on our server — a forgotten
registration silently makes runs free and reopens the reroll loop — so it warns once and exposes
`isRegistered()` for a boot-time assertion. Wire that assertion when we deploy.

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
- **The dimension itself: verified on dev 2026-07-28.** The `dimension_type` + `dimension` pair
  shipped in the mod's `data/` registered with no owner action and **attached to the existing dev
  world** — which was the claim taken on faith from reading `WorldDimensions.bake`, and is the one
  that would have been expensive to be wrong about. `execute in cobblemon_roguelite:arena`
  resolves, `/opt/cobblemon-dev/world/dimensions/cobblemon_roguelite/arena/` was created on first
  access, a `setblock` at `0 64 0` took, and `0 60 0` is air — the void generator does nothing, as
  intended.

  Two incidentals confirmed at the same time. Chunks in the arena are **not loaded without a
  player**: the first `setblock` failed with "That position is not loaded" and needed
  `forceload add`, exactly the chunk-ticket caveat noted above — so anything placing an arena
  before its player arrives must take a ticket. And the datapack convention works end to end on a
  real server: both registries registered with Cobblemon and each loaded its example table with
  zero rejected.
- **Does Cobblemon *battle and capture* behave in there? Verified on dev 2026-07-28: yes.** A
  wild Pokémon spawned onto a platform in the arena, battled and was caught normally.
  `PokemonEntity` placement over void — the specific risk — is not a problem.

**The arena design is therefore fully verified.** Nothing in this section rests on an untested
assumption any more: the dimension attaches to existing worlds, the void generator behaves,
`power_spot` needs no block entity, chunks need tickets, and Cobblemon's battle and capture flow
work inside it. What remains is implementation (the slot allocator, the template, the entry and
exit paths), not discovery.
- ~~**Does `mega_showdown:power_spot` work when placed programmatically?**~~ **Verified on dev
  2026-07-28: yes, and more cleanly than hoped.** `/setblock` placed one and Dynamax activated
  next to it. `/data get block` answered "block target is not an entity" — the block has **no
  block entity at all**, so there is no multiblock, no activation step and no NBT to reproduce.
  A `StructureTemplate` stamp is therefore sufficient, and the confinement plan holds as written.
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
