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

### Gimmick confinement

Mega Showdown implements Tera/Dynamax **mod-side with items and world state**, so
`BattleFormat.ruleSet` is not the lever, and the `mega`/`teralization`/`dynamax` config
booleans are **global** — useless for "on in one place only".

Actual gates: Dynamax = `dynamax_band` + within `powerSpotRange: 20` of a
`mega_showdown:power_spot`; Tera = `tera_orb` + 50 shards. All three items are craftable and
`power_spot` has no worldgen.

Confinement plan:

- Craft-ban `power_spot`, `dynamax_band`, `tera_orb` via `server-craft-bans`.
- Keep `dynamaxAnywhere: false`; place `power_spot` blocks in run arenas only.
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

- **Concurrency:** N simultaneous runs need instanced arenas. The tower's per-floor teleport
  is the starting point.
- **Test loop:** no working local dev server for these mods; everything is runtime-tested on
  the dev VM. This sets iteration cadence more than code volume does.
- **Craft-ban sequencing:** shipping the craft-bans before the mode exists removes Tera and
  Dynamax access server-wide with nothing replacing it. Land them together.
