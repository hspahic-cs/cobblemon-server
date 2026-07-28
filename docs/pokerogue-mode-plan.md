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
| Inventory | No personal bag inside a run; run-granted items only | §2.11 |
| World | Runs happen in run arenas, not in the shared world | §4 |
| Gimmicks | Tera/Dynamax exist inside a run and nowhere else | §2.5 |
| Economy | **One metered channel out: the currency/BP payout.** Deliberate, not an exception | §2.2 |

**Isolation is one-directional.** It governs what *leaves* a run, not what enters. Information
may flow inward — §2.15 gates the starter offer on the player's server Pokédex — because that
duplicates nothing and moves no value out. A run's possibility space depending on server history
is a feature; a run's contents reaching the server is not.

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

**Consequence 2 — retry, and it is the larger one.** The seed prevents rerolling *opponents*. It
does nothing about re-fighting a *losing battle*: with checkpoints plus permadeath, pulling the
plug mid-battle would rewind to the wave boundary with the party intact, making any losing
battle re-fightable. Resolved in §2.10.

### 2.10 Interrupted battles are attributed, not forfeited

**Considered:** forfeit the run · rewind to the last milestone checkpoint · attribute the
disconnect and penalise only the player's own drops.

**Chosen:** attribution. **A disconnect never ends the run.**

**Mechanism.** When a wave battle starts, stamp the run with a battle-in-progress marker
carrying the **server's boot identity**. On reconnect, compare it against the current boot:

- **The server restarted** since the marker was written → the interruption was not the player's
  doing → clean resume of the wave, no penalty.
- **The server is still on the same boot** → the player's connection dropped → penalty.

Neither side of that comparison is forgeable by the player, which is what makes it work.

**Penalty:** the Pokémon that were **on the field** when the connection dropped are killed
(permadeath, via `RunState.kill()`), and the run continues at the next wave. The player loses
roughly what losing the battle would have cost them, so quitting buys nothing — without the
run-ending harshness that would punish a bad home connection.

**Why not milestone rewind alone.** Rewinding to the last checkpoint every N waves was
considered and kept only as persistence granularity, not as the deterrent: its cost depends
entirely on where in the cycle the player is. Nineteen waves in with milestones every ten costs
nine waves; eleven waves in costs one. The cheapest moment to rage-quit would be immediately
after a checkpoint, and players would find it. It also punishes a genuine crash exactly as hard
as an exploit, which attribution avoids.

**Consequence:** `RunState` must carry the battle-in-progress marker, the boot identity, and the
on-field Pokémon at the time of the last battle start.

### 2.11 No personal bag inside a run

**Chosen:** players may not use their own items inside the mode. The run bag is exclusively
run-granted.

**Why:** a player's own potions and revives make permadeath negotiable and bypass the reward
loop entirely — the isolation contract (§1.1) is meaningless if healing is unlimited and
externally supplied.

**Mechanism:** reject bag-item actions outright for run battles. There is no format-level
switch — `BagItem.canUse` is per-item — so this is enforced at the action layer. A blanket
rejection is simpler than a per-item allowlist, and is what the decision permits.

### 2.12 The reward table is fully configurable

**Chosen:** rewards are **data**, not code. Contents and rarity curve are decided later and are
expected to change often.

**Format: a datapack**, not a config file, so tables are reloadable and so a server owner running
a published build can write their own without touching the jar (§1.2).

### 2.13 Starter draft: one starter from an offer, party grown by catching

**Considered:** points budget · random offers, pick one, repeat · single starter plus in-run
recruitment · prebuilt archetype packs · snake draft · egg/gacha draft.

**Chosen:** a combination of *random offers* and *build by catching* — the player picks a single
starter from a small randomised offer, and the party grows through the run by catching.

**Why:** it carries the most run-to-run variance of the options while keeping player agency at
the moment of choice, and it makes **catching the engine of the run** rather than a side
feature — the most Cobblemon-native of the structures considered, which is the bar set in §1.
The points-budget alternative is the most expressive but the least replayable, since strong
players converge on the same picks.

**Consequence — this moves catching into phase 1.** With a party that starts at one Pokémon,
catch-into-run-party is not an enhancement, it is the party system; the vertical slice cannot
ship without it. §3 is updated accordingly.

### 2.14 Wave composition: mostly wild, trainers at intervals

**Chosen:** mirror PokéRogue's structure — most waves are **wild encounters**, a **trainer**
battle at a regular interval (theirs: every 5th wave), and a **boss or leader** at a wider one
(theirs: every 10th). **Wild Pokémon are catchable; trainer-owned Pokémon never are.**

**Why this forced a revision of §2.6.** That decision made every opponent an authored RCT
trainer. Under §2.13 catching is the party system — so if most waves are trainers, almost
nothing is catchable and the party cannot grow. The two decisions could not both stand. The
split resolves it:

| Wave type | Built as | Catchable | Level scaling |
| --- | --- | --- | --- |
| Wild (most) | Runtime-generated wild Pokémon, Cobblemon's own battle + capture flow | Yes | Trivial — set at spawn |
| Trainer (interval) | Authored RCT trainer | No | Runtime level mutation (task #1) |
| Boss (wider interval) | Authored RCT trainer | No | Runtime level mutation, plus a boss multiplier |

**This de-risks the RCT decision.** Wild-wave scaling does not depend on NPC-side level mutation
working at all, so if that spike fails only the trainer waves are affected — and those can fall
back to fixed-level authored bands per segment. RCT stops being load-bearing for the whole mode.

**Level curve — ours, not theirs.** PokéRogue derives enemy level from the wave index as a
linear term plus a quadratic tail, with a multiplier on boss waves and a Gaussian jitter that
narrows as waves deepen. The *shape* ports; the constants do not, because that curve is tuned
for a 200-wave run and would have our ~10-wave slice fighting level 6 Pokémon. Constants live in
the config table.

### 2.15 Meta-progression is gated on the server Pokédex

**Considered:** catching a species *inside a run* unlocks it for later runs · catching a species
**on the server** unlocks it for runs · no meta-progression.

**Chosen:** the server Pokédex. Species the player has **caught on the server** become available
in the starter offer.

**Why:** it ties the server's existing progression to the mode's replayability in the direction
that benefits both — overworld catching earns run variety, and the run mode gives ordinary
catching a purpose beyond collection. The run-internal alternative is self-contained but makes
the mode a closed loop that the rest of the server never touches.

**Mechanism:** Cobblemon already maintains a per-player `PokedexManager` with `NONE` /
`ENCOUNTERED` / `CAUGHT` per species, readable server-side. We read `CAUGHT`; no new tracking is
needed, and because the Pokédex is a Cobblemon feature rather than ours, this survives
publication and works in single-player.

**Two constraints this must respect:**

- **A baseline pool is mandatory.** A new player has caught almost nothing, so a purely
  dex-gated offer makes the mode worst exactly when someone first tries it. There must be an
  always-available starter set with dex unlocks layered on top.
- **Unlocking is not power.** The dex decides *which* species can appear, never how strong the
  offer is. Offer weighting and tiering stay independent, or a player who has caught a
  pseudo-legendary gets one handed to them at wave 1.

**Note the separation:** in-run catching builds the *run party* (§2.13); server catching builds
the *starter pool*. Catching inside a run does not unlock anything, which keeps "nothing leaves
the run" intact.

**Positioning consequence, deliberate:** this makes the roguelite a reward for overworld play
rather than a standalone attraction. That is the point of the decision, but it is a choice.

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

**Chosen:** A — but for **trainer and boss waves only**. §2.14 makes most waves wild encounters,
which are not trainers at all and take a separate path. Revised 2026-07-27, see §2.14.

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

### 2.16 The seed fixes a run in progress; the entry fee prices restarting

**Chosen:** every run start mints a **new** seed — including a start that follows an abandon —
and **everything** in that run derives from it: the starter offer, every wave's species and
level, reward draws. The seed is persisted at run start, before anything derived from it is
shown.

**What this protects, and what it deliberately does not.** The seed exists so a run *in
progress* cannot change under the player: leaving the server and coming back must return the
same starter offer, the same wave 7 opponent, the same everything. That is a correctness
property, and it is what the seed is for.

It is **not** the anti-reroll mechanism. Abandoning a run and starting a fresh one gives a
genuinely different run, on purpose — a player who dislikes their draft should be able to walk
away rather than being locked into a run they do not want to play. **The entry fee is what
prices that**, which is the whole reason §2.18 charges one.

**Consequence — the free allowance must be consumed at run *start*.** If a free daily or weekly
run is only consumed on completion, it is an unlimited free reroll: start, dislike the draft,
abandon, start again. Charging the allowance at the door costs an honest player nothing and
closes the loop entirely.

**Consequence — a rich player can still reroll**, at the fee, repeatedly. That is accepted: the
fee is a throttle rather than a wall, and it only has to make rerolling less attractive than
playing. It does mean the fee cannot be trivial relative to what players hold.

**Consequence:** anything generated must be *derivable* from `(seed, wave)` or *persisted*.
Cobblemon's own unseeded RNG inside `PokemonProperties.create()` — IVs, nature, gender, shiny,
ability — satisfies neither today, so those values must be written into the properties string
rather than left to roll. See §2.17 for where the IV floor comes from.

**Rates, decided 2026-07-28: match PokéRogue.** Shiny **1/1024** (their `BASE_SHINY_CHANCE = 64`,
under a notation where chance is x/65536) and hidden ability **1/256**
(`BASE_HIDDEN_ABILITY_RATE`). Note our overworld runs `shinyRate: 8192`, so run shinies are eight
times more common than server ones — accepted deliberately, since a run shiny cannot leave the
run and costs the economy nothing. Their shiny *variant* palette tiers have no Cobblemon
equivalent and are not modelled.

### 2.17 IV floors come from a personal high-water mark, not current possession

**Considered:** IVs rolled uniformly and pinned by seed · scan the player's current party and PC
for their best of that species · track the best IVs the player has **ever** held, per species.

**Chosen:** the high-water mark.

**Why not scanning current possession:** it silently makes hoarding the optimal play. A player
who trades away a 6IV specimen would *lose* run power for having done so, and the server wants a
trade economy, not six hundred boxes of insurance. A high-water mark is kept once earned, so
trading a Pokémon away costs nothing.

**Why not Unchained catch streaks** (considered and rejected): streaks are per-species and do not
carry across species, so they measure recent grinding rather than a collection, and they would
reward repetition of one species over breadth.

**Mechanism:** per-player, per-species record of the best IVs ever held. Unlike §2.15's Pokédex
gating this *is* new persistent state — Cobblemon's Pokédex stores forms, genders and shiny
states, not IVs — but it is module-internal and small.

**Seed it with a one-time backfill.** On a player's first contact with the system, scan their
current party and PC to establish the marks, then let ongoing tracking take over. Without this,
launch day resets every veteran to zero and the feature reads as a punishment for having played
before it existed.

### 2.18 Depth is gated on badges; entry costs currency

**Chosen:** how deep a run may go is gated on the **first ten gym badges** (`gym_01`–`gym_10`).
E4 clears are deliberately *not* part of the gate. Starting a run costs currency.

**Why badges:** it is the most Pokémon-canonical progression gate there is, it points players at
content that already exists, and it costs almost nothing to implement — gym progress is stored as
**vanilla advancements** (`GymPrereqHook` reads `player.advancements.getOrStartProgress(...)`),
so the module can read it without importing anything of ours. The clean shape is a **configured
list of advancement ids**: we point it at our ten gyms, and a published build lets an owner point
it at whatever their server uses.

**Why an entry fee:** the mode should be a money *sink*. That constrains the payout — see §5,
where the payout currency question is open — because a run that returns more currency than it
costs is a faucet with extra steps, and would make the roguelite the best money loop on the
server, starving the activities meant to feed it.

---

## 3. Preliminary plan

**Phase 1 — vertical slice.** Starter offer, ~10 waves, **catch-into-run-party**, reward picks,
permadeath, checkpoint and resume with disconnect attribution, run party in its own store.
Deliberately de-risks the unknowns: run-store lifecycle, runtime-scaled RCT opponents, and the
run loop reimplemented free of the tower. Catching is in this phase because §2.13 makes it the
party system, not an enhancement.

**Phase 2 — full mode.** Boss trainers, run variance and branching, concurrent runs and arena
instancing, gimmick confinement.

**Phase 3 — balance.** Content and tuning.

**Landed so far:** `RunState` (run model with NBT round-tripping) and `RunStore` (world-save-data
persistence for every active run), both in the new standalone module. Nothing is wired up: there
is no run loop, no battle, no command.

---

## 4. Risks

- **Replayability rests on one mechanism.** §2.15's dex-gated starter pool is currently the only
  answer to "why start run #12", and it is front-loaded: it varies what a run *starts* with, not
  what happens during one. Reward paths, branching and run modifiers are still open (§5). This
  remains the risk most likely to make the mode land flat.
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

- **Run-party visibility.** Cobblemon's party HUD reads Cobblemon storage, not our run store, so
  a player mid-run may see their real party while battling with run mons. Needs checking on dev;
  the answer decides whether phase 1 needs a custom GUI. Now sharper than before: under §2.13 the
  party changes *during* a run as the player catches, so whatever shows it has to stay live.
- **Starter offer:** the **baseline pool** every player starts with regardless of Pokédex
  (§2.15 makes this mandatory, not optional), how many species are shown per offer, and how the
  offer is weighted. §2.13 and §2.15 fix the structure; the contents are open.
- **Catch rules inside a run:** catch rate, and whether balls are earned/purchased or unlimited.
  §2.14 settles *what* is catchable (wild only, never trainer-owned).
- **Wave interval constants:** how often trainer and boss waves land, and the level-curve
  constants (§2.14 fixes the shape, not the numbers).
- **Waves per run** — now blocking, because §2.14's level curve is being taken from PokéRogue and
  theirs is a function of a 200-wave run. Their `1 + wave/2 + (wave/25)²` puts wave 10 at level 6
  and only reaches level 100 around wave 130. Adopting their constants literally means either
  running 200-wave runs or fighting level-6 opponents in a ten-wave slice. The fix that keeps
  their pacing exactly is to feed the curve a *scaled* wave index — map our run length onto their
  200 — so a 50-wave run compresses their curve 4× and feels the same shape at 4× the rate. That
  needs a run length before it can be written down.
- **Do trade-ins update the IV high-water mark (§2.17)?** Counting them rewards trading, which is
  the point — but it also means one perfect specimen passed around can water-mark a whole server.
  Counting only self-caught Pokémon keeps the mark personal at the cost of some of the trade
  incentive.
- **What the payout is denominated in** (§2.18). If entry costs currency and the payout is also
  currency, the sink only works when the average run loses money, which is a hard thing to make
  feel good. Paying out in things that are *not* money — egg vouchers or gacha pulls, cosmetics,
  profile titles, leaderboard standing, run-only unlocks — keeps the currency flow negative while
  the reward still reads as generous. Needs a decision before the payout curve can be set.
- **Reward table contents and rarity curve** — the schema is buildable now (§2.12); the data is
  not blocking until balance.

### Blocking phase 2

- **Replayability model** beyond §2.15's dex unlocks: what else varies between runs — reward
  paths, branching routes, run modifiers.
- **Boss roster:** who they are, at what intervals.
- Waves per run; milestone checkpoint interval (§2.10 keeps milestones as persistence
  granularity — how coarse is still open).
- **Run expiry.** Runs checkpoint indefinitely and nothing voids an abandoned one, so world save
  data accumulates dead runs forever. The tower voids on daily rotation; this has no equivalent.
  Likely a configurable expiry after some period offline.
- Escalation ladder: which wave bands unlock Mega / Tera / Dynamax / legendaries.
- Entry gating: what starting a run costs, and what stops abandon-and-restart from rerolling a
  bad draft.

### Blocking publication only

- Whether to publish at all, and under what licence and name (§2.9).
- RCT's licence, if it is to be more than a soft dependency (§2.6).
