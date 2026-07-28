# Roguelite Mode — Preliminary Plan & Decision Record

Status: **planning**. Nothing here is player-facing yet.

This document holds the *intent*, the *decisions and why they were made*, and the
*preliminary plan*. The architecture that follows from it lives in
[`pokerogue-mode-design.md`](./pokerogue-mode-design.md) — plan is why/what/when, design is
how.

Decisions dated 2026-07-26 / 2026-07-27.

---

## 1. Intent

Build a roguelite run mode natively in Cobblemon: a self-contained run where a player takes a
drafted team through escalating waves, picks rewards between fights, and loses the run on a
wipe. [PokéRogue](https://pokerogue.net/) is the inspiration and the reference point, not the
specification.

**The bar is: fun, replayable, and built out of roguelike elements that already exist in
Cobblemon** — catching, evolution, type matchups, held items, EV training, a party of six.
Not parity with PokéRogue. Where a PokéRogue mechanic has no natural Cobblemon expression, it
is dropped or reshaped rather than emulated (§2.4).

### 1.1 The isolation contract

A run is an **isolated instance**. That is the defining property of the mode and it holds on
every axis, not just the obvious one:

| Axis | Rule | Where |
| --- | --- | --- |
| Party | Run party lives in our own store; the player's real party is never touched | §2.2 |
| Progression | Levels, EVs, evolutions gained in a run die with it | §2.2 |
| Catches | Nothing caught in a run leaves it | §2.2 |
| Inventory | **Open** — the player's own potions and revives must not trivialise permadeath | §5 |
| World | Runs happen in run arenas, not in the shared world | §4 |
| Gimmicks | Tera/Dynamax exist inside a run and nowhere else | §2.5 |
| Economy | **One metered channel out: the currency/BP payout.** Deliberate, not an exception | §2.2 |

### 1.2 Possible publication

The mode may eventually be published for other players and servers to use, if it turns out
good. That is not a commitment, but it is now a constraint on architecture: the mode is built
as a **standalone mod** that depends on Cobblemon and nothing else of ours (§2.9). Deciding
this late would be expensive; deciding it now costs nothing.

---

## 2. Decisions

Each entry records what was considered, what was chosen, and why — so a future reader can
tell a deliberate choice from an accident.

### 2.1 Build our own, not the CurseForge mod

**Considered:** fork/adjust the CurseForge
[Cobblemon Battle Tower](https://www.curseforge.com/minecraft/mc-mods/cobblemon-battle-tower)
mod · build our own.

**Chosen:** our own.

**Why:** the mod is **All Rights Reserved with no public source**. Adapting it means
decompiling a closed jar and redistributing a modified build — a license violation, not merely
inconvenient, and fatal to §1.2. It is also on a fast release train, so a decompiled fork rots
on every upstream bump. And it buys little: it is RCT-API-based like ours, and its Boss Mode
depends on Mega Showdown.

**Revised cost estimate.** This decision was originally justified partly by "we already own
most of the scaffolding" — the tower's run loop, arena teleports, disconnect-tolerant resume,
and persistence in `cobblemon-bridge`. Independence (§2.9) forbids importing any of it. The
license argument is untouched and still decisive, but the scaffolding either gets reimplemented
inside the new module or extracted into something shareable. That work was not previously
counted.

### 2.2 Run-caught Pokémon do not persist

**Considered:** run catches persist to the player's storage · run is self-contained, payout in
currency/BP.

**Chosen:** self-contained. Nothing leaves the run except the payout.

**Why:** a roguelite that hands out legendaries and shinies is a faucet that would move the
server economy. It also has a large secondary benefit: because nothing persists, the run party
can live entirely in our own store and **the player's real party is never touched**. No crash,
restart, or botched restore can cost anyone real Pokémon — which removes what was otherwise the
single largest correctness risk in the project.

**The payout is a deliberate hole in the isolation**, not an oversight: exactly one channel
out, and it is the metered one. Everything else is sealed.

**Consequence — meta-progression is unresolved.** "Nothing leaves the run" is about Pokémon and
items. Cross-run *unlocks* (new starters, run modifiers) are a different thing: they touch no
economy and duplicate no Pokémon, and they are the standard answer to "why start run #12". They
are neither permitted nor forbidden by this decision. See §5.

### 2.3 Runs are checkpointable

**Considered:** run dies with the session (tower's current behaviour) · run survives across
sessions.

**Chosen:** checkpointable.

**Why:** a run is long enough that session-death would make the mode hostile to play.

**Mechanism — not the tower's.** The tower writes its resume snapshot to raw
`player.persistentData`. That is safe for the tower, whose snapshot is a `Set<UUID>` pointing at
Pokémon in the player's real party, and unsafe for us, whose store is the *only* copy of six
Pokémon: `ServerPlayer.restoreFrom` copies only the `PlayerPersisted` subtag across a
death-respawn clone, so a player killed by a creeper between waves would return with the run
party deleted. Runs are therefore persisted as **world save data** (`RunStore`), which is
untouched by player clone semantics, flushed at wave boundaries, and enumerable for concurrent
runs.

**Consequence 1 — reroll.** Checkpointing is a reroll exploit unless wave generation is seeded.
The run carries a `seed` for exactly this reason.

**Consequence 2 — retry, and it is the larger one.** The seed prevents rerolling *opponents*.
It does nothing about re-fighting a *losing battle*: with checkpoints plus permadeath, pulling
the plug mid-battle rewinds to the wave boundary with the party intact. Any run-based mode with
stakes has to commit the battle at its start — checkpoint with a `battleInProgress` flag before
the first turn, and resolve an interrupted battle as a loss rather than replaying it. **Open,
and it changes what `RunState` carries, so it is phase-1 blocking.**

### 2.4 Stacking modifiers are reshaped, not reproduced

**Considered:** patch Cobblemon's Showdown bundle to support stacking modifiers · express the
same progression through supported mechanisms.

**Chosen:** supported mechanisms only.

**Why:** patching the bundle is a permanent maintenance tax on every Cobblemon bump, it would
feed illegal battle states to the AI, and it is incompatible with shipping the mode to anyone
else.

**What replaces it:**

1. **Persistent run-party state** applied between waves — EVs/vitamins, levels, nature mints,
   ability patches, evolution, held items, TMs. Real Cobblemon state, no sim involvement. This
   is where "stacking" power lives, and on its own it is a complete progression loop.
2. ~~**Active consumables via datapack bag-item scripts.**~~ **Deferred.** Cobblemon loads
   bag-item JS from `data/<ns>/bag_items/*.js` and hands the scripts the raw Showdown battle
   object, so arbitrary in-battle effects are reachable without a fork. It is also the most
   expensive machinery in the design, and it exists to approximate a PokéRogue mechanic rather
   than to serve the bar in §1. Not in phase 1; possibly not at all.

**Accepted loss:** passive auto-triggering modifiers (auto-berry at 50% HP, Multi Lens extra
hits) are not reachable. They get expressed as (1) instead.

### 2.5 Tera and Dynamax are enabled inside a run only

**Considered:** global config toggle · Megas-only ladder (cheapest) · physical confinement to
run arenas.

**Chosen:** physical confinement. **Reaffirmed 2026-07-27** after the scope of the confinement
work was re-raised — Tera and Dynamax stay in.

**Why the obvious tool doesn't work:** Mega Showdown implements Tera/Dynamax **mod-side with
items and world state**, not as Showdown format rules. Cobblemon's per-battle
`BattleFormat.ruleSet` is not the lever, and the `mega`/`teralization`/`dynamax` config booleans
are **global** — the right instrument for "off server-wide", the wrong one for "on in exactly
one place".

**Actual gates:** Dynamax = `dynamax_band` + within `powerSpotRange: 20` of a
`mega_showdown:power_spot`; Tera = `tera_orb` + 50 shards of a type. All three items are
craftable, and `power_spot` has **no worldgen** — it exists only where a player places one.

**Therefore:** craft-ban `power_spot`/`dynamax_band`/`tera_orb`, keep `dynamaxAnywhere: false`,
place power spots in run arenas only, and issue run-scoped items revoked on exit and on login.

**Known gap:** revoking on exit does not stop a player dropping a run-issued `tera_orb` into a
chest mid-run and collecting it afterwards. Craft-bans stop new ones, not stashed ones. Tagging
issued items and voiding them when the holder is not in a run is the likely fix.

**For publication:** Mega Showdown is a **soft** dependency. Absent it, the gimmick ladder
degrades to off and the mode still runs.

**Incidental finding:** nothing currently enforces the server's Tera ban. `server-craft-bans`
contains only `ash_cap.json`, and `teralization`/`dynamax` are both `true` on dev. The rule is
social, not enforced. Since Dynamax is unused, the craft-bans can land ahead of the mode without
anyone losing access to something they use.

### 2.6 Opponents are RCT trainers, scaled at runtime

**Considered:** (A) authored RCT trainer JSONs · (B) opponent teams built in Kotlin as synthetic
party stores, driving the battle directly.

**Chosen:** A, for bosses certainly (§2.7) and probably for wave opponents too.

**Why the earlier reasoning was wrong.** B was originally chosen because "RCT trainers are
datapack JSON and cannot express a team scaled at runtime". They can.
`GymBattleAdjustHook.applyToBattle` already mutates `bp.effectedPokemon.level` at
`BattleStartedEvent.Pre` in production — it just does it to the *player's* side for gym level
caps. The same shape applied to the NPC actor scales an authored team to any wave level, with
crash-safe restore already solved there.

**What A buys:** trainer skins, names, dialogue, summon/tag/cleanup machinery, and per-trainer
AI configuration — none of which we would have to write. It also avoids building a bespoke
battle driver.

**Honest limitation:** scaling level does not scale movesets, EVs, or held items. A team
authored at L15 and stretched to L60 still runs L15 moves. So the ladder wants a handful of
authored bands (early/mid/late) with level scaling smoothing difficulty *within* each band,
rather than one team stretched across the whole run.

**Unverified:** NPC-side level mutation is proven for the player side only. It must be confirmed
on the dev VM before the plan commits to it. **Also unverified: RCT's license**, which must be
checked before it becomes anything more than a soft dependency (§1.2).

### 2.7 Boss trainers are dedicated authored trainers

**Chosen:** a fixed trainer with an authored team at set wave intervals, authored **specifically
for this mode** as RCT trainers.

**Rejected — reusing our own `gym_01`–`gym_24`.** Considered and turned down: the server's gym
leaders and E4 are content players already fight, and recycling them makes the mode a rerun
rather than its own thing.

**Rejected — transcribing PokéRogue's rosters.** Their code is **AGPL-3.0-only** and their docs
are **CC-BY-NC-SA-4.0**. Transcribing team compositions was a tolerable shortcut while the mode
was server-internal; under §1.2 it becomes a published derivative. It is also a poor fit: their
parties are seeded and tiered rather than fixed, and their schedule (E4 at waves 182–188,
champion at 190) doesn't transfer to runs an order of magnitude shorter. What ports is the
**pattern**, not the data.

### 2.8 AI: design for what ships, not for our bridge

**PokéRogue's AI, for reference** (behaviour described in our own words — their docs are
CC-BY-NC-SA):

- A **single-turn heuristic scorer with no search**. Each usable move is scored, roughly type
  effectiveness × stat advantage plus a power term, adjusted for effectiveness and STAB; moves
  are sorted and the top one usually taken. One refinement: if any move would KO, only KO-ing
  moves are considered. No minimax, no rollouts, no full damage simulation.
- **Difficulty is deliberate randomness over the sorted list**, not a weaker evaluator. Wild
  Pokémon take the best move most of the time and otherwise step down a rank; bosses and trainer
  Pokémon use a variant where the chance of stepping down scales with how close adjacent scores
  are — near-optimal when one move is clearly right, loose when options are genuinely close.
- **Switching uses matchup scores with hysteresis**: switch only if the best benched Pokémon
  scores several times the active one, plus a penalty that scales with how often it has already
  switched.

**Nothing there is worth porting** — our poke-engine bridge is a deeper evaluator than any of
it. Two ideas transfer as *concepts*: score-then-randomise as a difficulty dial (the same shape
as the temperature dial already planned for poke-engine), and switch hysteresis with a frequency
penalty, which targets the exact failure mode our RL experiments showed (never switching).

**The shipped default cannot be our bridge.** `PokeEngineAI` calls out to a self-hosted Python
service; nobody downloading this mod gets one. So the default AI is RCT's own types or
Cobblemon's `StrongBattleAI`, and our bridge becomes an *optional server-side upgrade* behind an
interface (§2.9). This is a second, independent reason §2.6 lands on RCT trainers: they carry
their own AI configuration.

**Still true:** the wave → format/gimmick ladder is a config table, not code, and the bridge
payload carries Tera type and gimmick availability from day one even while the current AI
ignores it. Gimmick waves default off on dev until an AI can use them.

### 2.9 A standalone module, not part of cobblemon-bridge

**Considered:** build inside `cobblemon-bridge` alongside the tower · build as its own mod.

**Chosen:** its own mod — `custom-mods/cobblemon-roguelite`, mod id `cobblemon_roguelite`.

**Why:** §1.2. A publishable mod cannot import `cobblemon-bridge`, `cobblemon-ranked`, or
`cobblemon-poke-ai`, and the coupling the plan previously recommended (`TowerGauntletHook`,
`TowerStore`, `TowerManager`, `RankedBattle.buildTempParty`) is precisely the coupling that
would make separation expensive later. At two files it costs nothing; it only gets worse.

**Rule:** the module depends on Cobblemon and nothing else of ours. Server-specific integrations
— our economy, our arenas, the poke-engine AI bridge — are reached through interfaces declared
in the module and implemented in `cobblemon-bridge`. Nothing in `cobblemon-roguelite` may import
`com.cobblemonbridge`.

**Deferred, deliberately:** whether to actually publish. Publishing carries real ongoing cost —
config surface, compatibility across Cobblemon bumps, docs, support — that the mode should earn
first. This decision only keeps the option open. The mod is licensed All Rights Reserved until
that call is made, and it is not published under the PokéRogue name.

---

## 3. Preliminary plan

**Phase 1 — vertical slice.** Starter draft, ~10 waves, reward picks, permadeath, checkpoint and
resume, run party in its own store. Deliberately de-risks the unknowns: run-store lifecycle,
runtime-scaled RCT opponents, and the run loop reimplemented free of the tower.

**Phase 2 — full mode.** Boss trainers, run variance and branching, catch-into-run-party,
concurrent runs and arena instancing, gimmick confinement.

**Phase 3 — balance.** Content and tuning.

**Landed so far:** `RunState` (run model with NBT round-tripping) and `RunStore` (world-save-data
persistence for every active run), both in the new standalone module. Nothing is wired up: there
is no run loop, no battle, no command.

---

## 4. Risks

- **Replayability is unaddressed.** It is now a stated goal (§1) and there is no mechanism for
  it — a seed and a linear wave ladder produce a similar run every time. Variance sources and
  possible meta-progression are open (§5) and this is the risk most likely to make the mode
  land flat.
- **Reimplementation cost.** §2.1: the tower scaffolding cannot be imported.
- **World isolation.** Runs need arenas that are actually isolated, and for a published mod they
  cannot be hand-placed builds on our server. Structures, a dedicated dimension, or config.
- **Concurrency.** N simultaneous runs need instanced arenas.
- **Test loop.** No working local dev server for these mods; everything is runtime-tested on the
  dev VM. This sets iteration cadence more than code volume does.
- **Craft-ban sequencing.** Landing the bans before the mode removes Tera/Dynamax server-wide.
  Currently harmless (§2.5), but it stops being harmless if usage changes.

---

## 5. Open questions

### Blocking phase 1

- **Interrupted battles** (§2.3). Does disconnecting mid-battle forfeit the run, forfeit the
  wave, or resume? Changes `RunState`.
- **The player's bag.** Can a player use their own potions, revives, and berries inside a run?
  If yes, permadeath is negotiable and the reward loop is bypassed. There is no format-level
  switch for this — `BagItem.canUse` is per-item — so the lever is rejecting bag-item actions in
  run battles, which has to be deliberate.
- **Run-party visibility.** Cobblemon's party HUD reads Cobblemon storage, not our run store, so
  a player mid-run may see their real party while battling with run mons. Needs checking on dev;
  the answer decides whether phase 1 needs a custom GUI.
- **Starter draft:** pool, budget, party size.
- **Reward table** contents and rarity curve.

### Blocking phase 2

- **Replayability model** (§4): what varies between runs — starters, reward paths, branching
  routes — and whether cross-run unlocks are in scope (§2.2).
- **Boss roster:** who they are, at what intervals.
- Waves per run; checkpoint granularity.
- Escalation ladder: which wave bands unlock Mega / Tera / Dynamax / legendaries.
- Entry gating: what starting a run costs, and what stops abandon-and-restart from rerolling a
  bad draft.

### Blocking publication only

- Whether to publish at all, and under what licence and name (§2.9).
- RCT's licence, if it is to be more than a soft dependency (§2.6).
