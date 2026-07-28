# PokéRogue Mode — Preliminary Plan & Decision Record

Status: **planning**. Nothing here is player-facing yet.

This document holds the *intent*, the *decisions and why they were made*, and the
*preliminary plan*. The architecture that follows from it lives in
[`pokerogue-mode-design.md`](./pokerogue-mode-design.md) — plan is why/what/when, design is
how.

Decisions dated 2026-07-26 / 2026-07-27.

---

## 1. Intent

Build a [PokéRogue](https://pokerogue.net/)-style roguelite run mode natively in Cobblemon:
a self-contained run where a player takes a drafted team through escalating waves, picks
rewards between fights, and loses the run on a wipe.

The original framing was to adapt the CurseForge
[Cobblemon Battle Tower](https://www.curseforge.com/minecraft/mc-mods/cobblemon-battle-tower)
mod. That was rejected on investigation (§2.1). The mode is instead built on the tower code
we already own in `cobblemon-bridge`.

**Explicitly not the goal:** a faithful clone. PokéRogue's defining mechanic — stacking
modifiers — has no Showdown equivalent, so the design deliberately reshapes it (§2.4)
rather than chasing parity.

---

## 2. Decisions

Each entry records what was considered, what was chosen, and why — so a future reader can
tell a deliberate choice from an accident.

### 2.1 Build on our own tower, not the CurseForge mod

**Considered:** fork/adjust the CurseForge mod · build on our own `cobblemon-bridge` tower.

**Chosen:** our own tower.

**Why:** the mod is **All Rights Reserved with no public source**. Adapting it means
decompiling a closed jar and redistributing a modified build in our `.mrpack` — a license
violation, not merely inconvenient. It is also on a fast release train, so a decompiled fork
rots on every upstream bump. And it buys little: it is RCT-API-based like ours, and its Boss
Mode depends on Mega Showdown.

We already own most of the scaffolding — run loop, arena teleports, disconnect-tolerant
resume, run persistence, roster rotation, and the synthetic-party-store primitive in
`RankedBattle.buildTempParty()`.

### 2.2 Run-caught Pokémon do not persist

**Considered:** run catches persist to the player's storage · run is self-contained, payout
in currency/BP.

**Chosen:** self-contained. Nothing leaves the run.

**Why:** a roguelite that hands out legendaries and shinies is a faucet that would move the
server economy. It also has a large secondary benefit: because nothing persists, the run
party can live entirely in our own store and **the player's real party is never touched**.
No crash, restart, or botched restore can cost anyone real Pokémon — which removes what was
otherwise the single largest correctness risk in the project.

### 2.3 Runs are checkpointable

**Considered:** run dies with the session (tower's current behaviour) · run survives across
sessions.

**Chosen:** checkpointable.

**Why:** a run is long enough that session-death would make the mode hostile to play. The
mechanism already exists — `TowerGauntletHook.persist()` writes a resume snapshot to player
NBT at each checkpoint, restored on login. Ours is heavier: the tower only needs a
`Set<UUID>` because it battles the player's real party, whereas we must serialize the
Pokémon themselves (`Pokemon.saveToNBT` / `loadFromNBT`).

**Consequence:** checkpointing is a reroll exploit unless wave generation is seeded. The run
carries a `seed` for exactly this reason.

### 2.4 Stacking modifiers are reshaped, not reproduced

**Considered:** patch Cobblemon's Showdown bundle to support stacking modifiers · express
the same progression through supported mechanisms.

**Chosen:** supported mechanisms only.

**Why:** patching the bundle is a permanent maintenance tax on every Cobblemon bump, and it
would feed illegal battle states to the AI bridge, which assumes vanilla-legal battles. The
cost is ongoing and the blast radius is the whole battle stack.

**What replaces it**, both fully supported:

1. **Persistent run-party state** applied between waves — EVs/vitamins, levels, nature
   mints, ability patches, evolution, held items, TMs. This is where "stacking" power lives.
2. **Active consumables via datapack bag-item scripts.** Cobblemon loads bag-item JS from
   `data/<ns>/bag_items/*.js` and pushes it to Showdown at reload. The scripts receive the
   **raw Showdown battle object** — Cobblemon's own `revive.js` mutates `pokemon.fainted`,
   pushes an `instaswitch` onto `battle.queue`, and emits battle messages. Arbitrary
   in-battle effects are reachable as a datapack plus a small `BagItem` registration, with
   no fork.

**Accepted loss:** passive auto-triggering modifiers (auto-berry at 50% HP, Multi Lens extra
hits) are not reachable. They get expressed as (1) instead.

### 2.5 Tera and Dynamax are enabled inside a run only

**Considered:** global config toggle · Megas-only ladder (cheapest) · physical confinement to
run arenas.

**Chosen:** physical confinement.

**Why the obvious tool doesn't work:** Mega Showdown implements Tera/Dynamax **mod-side with
items and world state**, not as Showdown format rules. So Cobblemon's per-battle
`BattleFormat.ruleSet` is not the lever, and the `mega`/`teralization`/`dynamax` config
booleans are **global** — the right instrument for "off server-wide", the wrong one for "on
in exactly one place".

**Actual gates:** Dynamax = `dynamax_band` + within `powerSpotRange: 20` of a
`mega_showdown:power_spot`; Tera = `tera_orb` + 50 shards of a type. All three items are
craftable, and `power_spot` has **no worldgen** — it exists only where a player places one.

**Therefore:** craft-ban `power_spot`/`dynamax_band`/`tera_orb` via `server-craft-bans`, keep
`dynamaxAnywhere: false`, place power spots in run arenas only, and issue run-scoped
items — revoked on exit *and* on login, since `TowerGauntletHook.onPlayerLoggedIn` already
reconciles interrupted runs and is where crash-mid-run leakage gets caught.

**Incidental finding:** nothing currently enforces the server's Tera ban. `server-craft-bans`
contains only `ash_cap.json`, and `teralization`/`dynamax` are both `true` on dev. The rule
is social, not enforced. Since Dynamax is unused and already bannable, the craft-bans can
land ahead of the mode without anyone losing access to something they use.

### 2.6 Wave opponents are built in Kotlin

**Considered:** (A) pre-generate RCT trainer JSONs across level bands via
`ops/gen_battle_tower_teams.py` · (B) build opponent teams in Kotlin as synthetic party
stores and drive the battle directly.

**Chosen:** B. A is rejected outright, not held as a fallback.

**Why:** RCT trainers are datapack-registered JSON and cannot express a team scaled at
runtime, so A quantizes difficulty into bands. B gives continuous scaling and is the shape
the rewritten AI wants. Cost: the AI bridge has to come along.

### 2.7 Fixed boss trainers every N waves

**Chosen:** keep PokéRogue's pattern — a fixed trainer with an authored team at set wave
intervals, distinct from the scaled wave opponents.

**Two constraints on sourcing from PokéRogue:**

- **License.** PokéRogue code is **AGPL-3.0-only**; docs/assets are **CC-BY-NC-SA-4.0**.
  AGPL is strongly copyleft with a network clause. No PokéRogue source is vendored into this
  repo. Team *compositions* — which species a trainer runs — may be transcribed as data into
  our own JSON format, with attribution.
- **Their teams are not literally fixed.** Party generation is seeded and tiered — signature
  species plus filtered pools plus wave-dependent templates — so a leader's team varies by
  wave and run. Their schedule doesn't transfer either (E4 at waves 182/184/186/188, champion
  at 190; our runs are far shorter). What ports is the **pattern**, not the data.

**Open:** whether the boss roster is transcribed from PokéRogue or reuses our own 24 authored
leaders (`gym_01`–`gym_24`, including the E4 rebuild). Ours carries no license question and
makes the mode feel like this server. Content call — see §5.

### 2.8 Design for the AI that's coming, not the one we have

The battle AI is being rewritten from scratch. The wave → format/gimmick ladder is therefore
a **config table**, not code, and the bridge payload carries Tera type and gimmick
availability **from day one** even while the current AI ignores it — otherwise the new AI
arrives to find the data was being stripped at the boundary.

Gimmick waves default **off** on dev until the new AI lands, so balance testing isn't skewed
by an opponent that structurally cannot answer them.

---

## 3. Preliminary plan

**Phase 1 — vertical slice.** Starter draft, ~10 waves, reward picks, permadeath, checkpoint
and resume, run party in its own store. Deliberately de-risks the two unknowns: run-store
lifecycle and runtime-built enemy teams.

**Phase 2 — full mode.** Fixed boss trainers, biome branching, catch-into-run-party,
concurrent runs and arena instancing, gimmick confinement (craft-bans, power spots,
run-scoped items).

**Phase 3 — balance.** Content and tuning.

**Landed so far:** `RunState` — the run model with NBT checkpointing.

---

## 4. Risks

- **Concurrency.** N simultaneous runs need instanced arenas. The tower's per-floor teleport
  is the starting point, not the answer.
- **Test loop.** There is no working local dev server for these mods; everything is
  runtime-tested on the dev VM. This sets iteration cadence more than code volume does.
- **AI dependency.** Gimmick waves are unbalanceable until the rewritten AI can use gimmicks.
  Mitigated by defaulting them off, not by blocking on the AI.
- **Craft-ban sequencing.** Landing the bans before the mode removes Tera/Dynamax
  server-wide. Currently harmless (§2.5), but it stops being harmless if usage changes.

---

## 5. Open questions (game design — user's call)

None of these block phases 1–2.

- Boss roster: PokéRogue teams transcribed, or our own 24 leaders (§2.7).
- Waves per run; checkpoint granularity.
- Starter draft: pool, budget, party size.
- Reward table contents and rarity curve.
- Escalation ladder: which wave bands unlock Mega / Tera / Dynamax / legendaries.
- Payout curve for currency/BP at run end.
