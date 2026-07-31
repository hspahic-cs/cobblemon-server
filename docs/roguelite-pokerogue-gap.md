# PokéRogue feature gap analysis

Written 2026-07-31 against PokéRogue `main` (the same source reads behind
`docs/roguelite-economy-reference.md`) and against what `cobblemon-roguelite` actually ships today.
Sorted by kind, because "missing" means four different things: already designed and waiting on a
build, buildable without any decision, blocked on a decision the plan records as open, and
deliberately out of scope. Nothing here re-litigates a settled decision.

## Missing, buildable, no decision needed

- **Mid-run evolution.** PokéRogue evolves after the battle that qualifies; our run Pokémon level
  through Cobblemon's own EXP flow. Audited 2026-07-31 against the arena-keyed swaps: a run Pokémon
  now exists in a real store **only while the player is inside the arena**, so the evolve prompt can
  only ever be answered there — and `RunDexGuard.isInsideARun` includes `RunArenas.isInArena`, so
  the evolution's Pokédex write is vetoed by the existing gate. What remains open is H4's general
  case (Cobblemon's *advancement criteria* on evolution are not cancelled — host-side closure, same
  as catches) and the cosmetic question of whether the evolution animation/prompt behaves inside a
  ChestMenu-heavy flow. Downgraded from "can corrupt" to "verify in play".
- **Held-item transfer between party members.** A core PokéRogue loop (move the Leftovers to who
  needs them). Ours grants a held item once and it stays. Cobblemon's party UI may already allow
  taking items off — which under the isolation design means the item lands in the inventory as a
  *marked* stack and survives to the next wave via the run bag. So the mechanic may exist by
  accident; making it deliberate is a small `BetweenWaveMenu` addition (a "move items" screen).
- **Biome-keyed wild pools.** We have biomes and we have wild pools, and they do not talk: the pool
  is global with wave windows. `WaveSpeciesPool`'s own docs already sketch the widening
  (`eligibleAt(wave, biome)`) and name its costs. PokéRogue's biome-species linkage is most of what
  makes its biomes feel different; ours are currently scenery plus an arena palette.
- **Catching bosses.** PokéRogue lets you catch the boss at low HP. Our `plan.catchable` flag
  already routes this per-wave — the smoke roster just never sets it on boss waves. Possibly a
  datapack-only change; verify `UncatchableProperty` is the only gate.
- **Money items** (Nugget / Big Nugget / Relic Gold as sellable pickups). Ours pays ₽ directly.
  Would only matter once the run bag has a sell flow; low value until then.
- **Shop purchase quantity scaling** (their items reprice as `baseCost × waveValue` continuously —
  ours does now too via `cost_multiplier`; what is missing is only their *stock-row unlock cadence*,
  the 30-wave `slice`, which our `shopSlotsAt` bands approximate but were not tuned to match).

- **Stat stages and battle forms persisting across wild waves** (playtest request 2026-07-31,
  PokéRogue's rule: boosts and forms carry between wild waves, reset at trainer battles). Ours
  resets everything every wave, because each wave is its own Cobblemon/Showdown battle and battle
  state dies with the battle by construction. Carrying it means capturing the player side's stat
  stages (and volatile form changes) at battle end and re-injecting them at the next battle's start
  — most plausibly by dispatching `|boost|` instructions into the new Showdown battle the way
  mega_showdown injects its own custom instructions, keyed off a new `RunState` field, cleared on
  trainer/boss waves. Genuinely delicate battle-engine work: the injection point, timing against the
  first request, and the client's stat display all need live verification. Deferred to its own pass
  rather than rushed — a wrong `|boost|` injection corrupts battles rather than merely missing.

## Designed or half-built, waiting on existing plans

- **Trainer/boss/rival battles** — the largest gap in play, and not an oversight: `RunTrainerBattles`
  is a seam, RCT's licence is §2.6's open question, and the roster/team-generation half already
  exists. Every third wave of the smoke schedule refuses until this is resolved.
- **Player-side stacking modifiers** (their Ultra/Rogue tier items: Leftovers, Shell Bell, Grip
  Claw...) — §2.33 chose tiered datapack held items; the tiering harness exists, the item set was
  left for the human to bless (§2.34 open question).
- **Boss shields** — built (§2.32), smoke-tested via the roster's `boss_shields`, waiting on trainer
  battles to be visible in play.
- **X items / Dire Hit** (five-battle team-wide buffs) — blocked on the recorded §2.11 reversal note
  ("run-issued bag items are allowed" is decided; the *implementation* of run state applied at
  battle start is not built).
- **Egg gacha / vouchers** — §2.28 decided egg payout bands as the run's payout currency; the
  gacha-style egg *reward* inside runs was never scoped. The payout half covers most of the value.
- **Candy / passive unlocks** — built (§2.15/§2.17, candy shop, IV floors, hidden-ability unlocks).
  What PokéRogue has that ours does not: **passives** (a second ability slot unlocked by candy) —
  §2.33's "unspent ability axis" note is exactly this and is deliberately parked.

## Blocked on a decision (open questions, most already recorded)

- **Luck + reroll-lock rarities.** The free roll's 75/19/4.7/1.2/0.1 tier odds with the looping luck
  upgrade (economy reference) needs a luck stat to hang on. Ours rolls from per-wave weight curves
  instead — a *different* mechanism, not a missing one; converging is a design choice.
- **Endless mode.** Theirs continues past 200 with scaling; §2.19's "flat last third" open question
  is the same territory. Nothing to build until that shape is decided.
- **Double battles.** Wave kinds and `BattleFormat` both allow it in principle; nothing in the plan
  mentions it. Genuinely new scope — needs the human to want it.
- **Run modifiers / daily runs / challenges** — §5's replayability bucket, explicitly deferred
  until someone has played the base mode.
- **Starter select filters beyond cost** (gen/type tabs, shiny/variant pickers). The draft screen
  has cost tabs; PokéRogue's Gen/Type filter rows would want the tab row rethought — cheap to build,
  but it is UI the human has been steering personally, so it gets a decision first.

## Out of scope or already diverged on purpose

- **Client-rendered UI** (their whole interface) — §2.32/§1.2: server-side ChestMenus, client mod
  deferred deliberately.
- **Per-wave money on wild waves** — deliberately diverged; trainer waves are the income.
- **IVs/nature rerolling via items mid-run** — ours flows through §2.17's floors and the reward
  table instead.
- **Their save/session model** (browser account, daily seed sharing) — meaningless server-side.

## The three worth doing next, in order

1. **Biome-keyed wild pools** — the single highest play-feel win per line of code, and the design
   note for it is already written in `WaveSpeciesPool`.
2. **Boss catchability via the datapack** — possibly zero code for a real PokéRogue-feeling moment.
3. **Held-item transfer screen** — small, and it completes the between-wave loop the auto-advance
   just tightened.

(The mid-run evolution audit that used to lead this list was done while writing it — see above; the
arena-keyed swap closed the corrupting half by construction.)
