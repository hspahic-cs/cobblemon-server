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

**Isolation governs value, not information** (restated 2026-07-28; it was previously
"one-directional"). Nothing of *value* leaves a run — no Pokémon, no items, no currency beyond
the metered payout. **Progression does flow both ways**, and deliberately:

- *Inward:* the species a player may start with are gated on their server Pokédex (§2.15).
- *Outward:* catching inside a run earns candy and raises that species' IV floor for future runs
  (§2.13, §2.17).

That is a change from the original stance, which forbade an in-run catch from unlocking anything.
The economic argument is untouched — candy and IV floors duplicate no Pokémon and mint no
currency — but the contract is now "value is sealed", not "nothing gets out".

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

**REVERSED 2026-07-31 — the run party becomes the player's real party.** The isolation above holds
for *persistence* (nothing still leaves the run) but not for *storage*: on entering a run the
player's own party is moved to their PC and the run party is put into the party slots, and on
leaving, the run Pokémon are removed and the stashed party comes back.

**Why the reversal.** The run party being invisible to Cobblemon means the player cannot open their
own party screen, cannot see the run team in the HUD, and cannot use any of Cobblemon's party UI on
the Pokémon they are actually playing. That is not a small friction — it is most of what a player
does between battles, and every part of it would otherwise have to be rebuilt inside chest menus.

**What this costs, stated plainly, because the sentence above called it "the single largest
correctness risk in the project" and that assessment was not wrong.** A crash, restart, disconnect
or botched restore between the two swaps leaves a player's real team in the PC and run Pokémon in
their party. So the implementation is not "move six Pokémon"; the load-bearing parts are:

- **Restore is idempotent and runs on login**, not only on run end. The stash is persisted run state
  and the recovery path is the *normal* path, exercised every session, not an error branch.
- **Nothing the player owned is ever released.** Only Pokémon the run created may be removed, and
  they carry a marker saying so. A release path that decides by position in the party is one
  off-by-one away from deleting somebody's Garchomp.
- **A run refuses to start if the PC cannot hold the stash.** Refusing is free; making room is not.
- **The stash records identity, not just contents**, so a restore can prove it is putting back the
  same Pokémon it took.

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

**Raised and dismissed 2026-07-28.** Not advancing the wave means a drop hands the fight back, so
dropping is technically a retry — the seed fixes the *opponent* but not the turn-by-turn RNG, so
a player could pay a Pokémon to re-roll their opening against a boss. **Judged not worth closing:
the expected value is very low.** A permadeath party is six Pokémon against 200 waves, so
spending one to re-roll a single opening is a bad trade in almost every position a player can be
in. Recorded so it is not rediscovered and re-litigated as though it were an oversight.

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
(permadeath, via `RunState.kill()`), and the run stays on the same wave — the player still owes
the fight they fled. That combination is what makes quitting cost more than fighting, without
the run-ending harshness that would punish a bad home connection. (The wave originally advanced;
see the resolved note below for why that inverted the incentive.)

**Why not milestone rewind alone.** Rewinding to the last checkpoint every N waves was
considered and kept only as persistence granularity, not as the deterrent: its cost depends
entirely on where in the cycle the player is. Nineteen waves in with milestones every ten costs
nine waves; eleven waves in costs one. The cheapest moment to rage-quit would be immediately
after a checkpoint, and players would find it. It also punishes a genuine crash exactly as hard
as an exploit, which attribution avoids.

**Consequence:** `RunState` must carry the battle-in-progress marker, the boot identity, and the
on-field Pokémon at the time of the last battle start.

**Implemented 2026-07-28** (`run/ServerBootId`, `RunBattleMarker`, `DisconnectAttribution`).

**Resolved 2026-07-28 — the penalty was cheaper than losing, which inverted the incentive.**
Losing a wave costs the whole party: permadeath takes each Pokémon as it faints, so a loss is a
wipe and the run is over. Disconnecting cost **one** Pokémon *and skipped the wave*, so a player
facing an unwinnable boss was strictly better off pulling the plug than fighting — the opposite
of what this decision set out to achieve.

**Fix: a player-side disconnect no longer advances the wave.** The cost becomes one Pokémon *and*
you still owe the fight you fled, which is no longer better than fighting it. Considered and
rejected: killing the whole field-eligible set (harsher and hard to explain), and treating a drop
as a loss (the run-ending harshness this decision exists to avoid).

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

**Chosen, revised 2026-07-28: a 10-point budget, mirroring PokéRogue.** Every startable species
has a point cost; the player spends up to **10 points** on a starting team. The earlier decision —
one starter from a small randomised offer — is superseded.

**Why the budget rather than the offer:** the cost *is* the balance statement. PokéRogue prices a
species by how strong it actually is in play, not by rarity or stage — Torchic costs 4 because
Speed Boost is genuinely that good. A budget therefore encodes power in a way a random offer
cannot, and it turns team-building into the first real decision of a run.

**A team, but rarely six.** With costs in the 3–6 range, 10 points buys two or three Pokémon, not
a full party. So catching is *still* how a party gets to six — the earlier decision's substance
survives, it just no longer starts at exactly one.

**Amended 2026-07-29: the budget alone does not hold that line, so the draft is capped at three.**
The paragraph above did its arithmetic against an assumed 3–6 price band. PokéRogue's real table,
now extracted (`ops/gen_pokerogue_starters.py`, 542 species), runs **1 to 10** — with **25 species
at cost 1 and 124 at cost 2**. Six cost-1 species is **6 of 10 points**, so as specified the budget
bought a *full party at wave 1* and "catching is how a party gets to six" was simply false.

Three ways out were considered. **Cutting the budget does not work**: six cost-1 picks cost 6, so
the budget would have to drop below 6 to stop them, which also makes any *pair* of 3-cost species
unaffordable and collapses the draft to a single cheap Pokémon. **Accepting full starting parties**
is the faithful mirror, but it makes catching optional and changes what the mode is. **Chosen: cap
the draft at three starters** (`StarterSelection.MAX_STARTERS`), independent of the six-member run
party. That keeps their prices as the balance statement this section already argues they are, keeps
catching as the party system, and costs one refusal a player can understand.

Note the two limits are now deliberately different constants. They shared a number until the real
prices arrived, and making them equal again reinstates the wave-1 full party — there is a test that
fails if they do.

**Amended 2026-07-29: their passive table is *not* imported.** §2.27 makes candy unlock a species'
**hidden ability**, replacing its normal one. PokéRogue's passive is an **additional** second
ability, and their assignments are balanced as a bonus: Bulbasaur's passive is Grassy Surge,
Pikachu's is Transistor. Under our replace-semantics, importing that list hands Bulbasaur Grassy
Surge *instead of* Overgrow — abilities chosen as a bonus, applied as a substitution, which is a
balance re-reading rather than a port. §2.27 already falls back to each species' own Cobblemon
hidden ability when no override exists, which is the slot Game Freak balanced, so **no override
table ships**. The extractor can still emit one behind `--hidden-abilities` if that is revisited.

**Costs mirror PokéRogue's**, per species, because they carry that balance judgement. See the
licensing note in §2.7: their cost table is **their data**, so it is transcribed into server-side
datapack content and never shipped in a published build, which needs derived defaults instead
(base stat total or evolution stage would do).

**Legendaries are excluded outright** — too strong, at any price.

**Consequence — this moves catching into phase 1.** With a party that starts at one Pokémon,
catch-into-run-party is not an enhancement, it is the party system; the vertical slice cannot
ship without it. §3 is updated accordingly.

**A full party: swap or release, decided 2026-07-28.** The run party holds six and there is no
run PC, so a seventh catch prompts the player to swap it for a party member or let it go.

**Considered:** refuse the catch outright (simplest, and makes slots a hard resource) · a
swap-or-release prompt · run-scoped box storage (most generous, most new state). Chosen the
middle: it is what mainline Pokémon does, so it needs no explanation, and it keeps the party
decision *live* — a good catch late in a run should be able to displace a spent one, which
refusing would forbid and boxes would make consequence-free.

**Note what the swap discards is unrecoverable.** A released or swapped-out run Pokémon is gone,
exactly like a permadeath (§2.2 — nothing leaves a run), so the prompt has to read as a decision
rather than as inventory management.

**Two isolation leaks, both closed 2026-07-28.** Cobblemon's capture flow wrote the caught
Pokémon to the player's **real** party and PC, and marked the species `CAUGHT` in their **real
Pokédex** — which §2.15 forbids outright, since server catches are what unlock starters and an
in-run catch must not.

They needed different fixes, which is worth recording because they look like one problem.
Cobblemon exposes no cancellable hook on the way in — the capture *completes* into real storage
and only then emits its event — so the party leak is corrected by reclaiming the Pokémon back out
and routing it to the run party. The Pokédex write happens even earlier, inside `party.add`, and
nothing hands back the previous value, so it cannot be undone at all and has to be **vetoed** at
`POKEDEX_DATA_CHANGED_PRE`.

The veto is gated on *fighting* or *being in an arena*, deliberately **not** on "has a run": a
player who paused a run (§2.22) and went catching in the world must still earn real dex entries,
and eating those would be a worse failure than the one being prevented.

**Balls are a reward, not a given** (decided 2026-07-28). Poké Balls are earned as a
between-wave reward option rather than supplied without limit. That is what makes a catch a
decision: with unlimited balls every wild wave is a free roll, and the swap-or-release choice
above never has to be faced. It also gives the reward table a lever that is useful without being
raw power, which matters most across the flat-level last third (§2.19) where EXP and level
rewards are dead weight.

**Catch rate: no change needed** (checked 2026-07-28). PokéRogue's catch rate is Gen VI
mechanics — `(1 − ⅔ × %HP) × speciesCatchRate × ballBonus × statusBonus`. Cobblemon's own
calculator computes `(3·maxHP − 2·currentHP) × catchRate × … / (3·maxHP)` times a status bonus,
whose leading term is algebraically the same thing. Both are Gen 6. Cobblemon adds a low-level
bonus (under L13) and a dark-grass modifier that PokéRogue lacks; neither is significant, and the
low-level bonus helps early in a run when the party is most fragile.

Our server already runs `captureCalculator: "cobblemon"`, so the answer is to leave it alone —
which is fortunate, because that setting is **global**, like `maxPokemonLevel` and the gimmick
booleans, so a run-only catch rate would have needed interception rather than configuration.

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

**Note the separation, revised 2026-07-28:** server catching decides **which** species you may
start with; in-run catching decides **how good they are** — candy toward passives and cost
reductions, and the IV floor (§2.13, §2.17). Access is a server achievement, quality is a run
achievement.

The original clause here said an in-run catch unlocks nothing at all. That is no longer true, and
§1.1 is restated accordingly: value is sealed, progression is not.

**Candy mirrors PokéRogue's sources:** one per catch of that species, more for shinies (theirs is
5/10/20 by variant tier), plus friendship thresholds earned in battle. Spent on passive unlocks,
cost reductions, and eggs.

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

**~~Accepted loss:~~ this was wrong — corrected 2026-07-29, see §2.31.** The original text said
passive auto-triggering modifiers (auto-berry at 50% HP, Multi Lens extra hits) "are not
reachable". They are. Cobblemon's datapack registries — `abilities/*.js`, `held_items/*.js`,
`moves/*.js`, `bag_items/*.js` — ship **real Showdown handler functions** (`onDamage`,
`onDamagingHit`, `onResidual`, `onEat` …), not name mappings onto existing behaviour. Cobblemon's
own `held_items/eggantberry.js` is a working example.

The decision itself stands — the bundle is still not to be patched, and for a stronger reason
than the one given here (§2.31). What changes is the scope of what "supported mechanisms" covers,
which is considerably larger than this section assumed.

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

**What A buys:** trainer skins, names, dialogue, and per-trainer AI configuration — none of which
we would have to write.

**Correction, 2026-07-28: it does *not* avoid a bespoke battle driver.** An earlier draft claimed
that; implementing the provider proved otherwise. `RCTMod.makeBattle` builds the **player's** side
from `TrainerPlayer.getTeam()` — the player's real Cobblemon party — which would drag overworld
Pokémon into the arena, damage them, and aim permadeath at Pokémon the run has never heard of.
Its `canBattleAgainst` gate also refuses any trainer the player has already beaten, which every
roster repeat is by design.

So the provider takes RCT apart rather than calling into it: authored team, RCT's own
`TrainerEntityBattleActor` with the trainer's authored AI, and the player's side from the run
party, driven by an ordinary `BattleRegistry.startBattle`. Everything §2.6 actually wanted from
RCT survives; the driver is about forty lines. The decision stands — what was wrong was the cost
estimate, not the choice.

**Honest limitation:** scaling level does not scale movesets, EVs, or held items. A team
authored at L15 and stretched to L60 still runs L15 moves. So the ladder wants a handful of
authored bands (early/mid/late) with level scaling smoothing difficulty *within* each band,
rather than one team stretched across the whole run.

**Verified on dev 2026-07-28.** A probe against a live RCT trainer (Bird Keeper Roger, L63/L64)
forced every opponent Pokémon to L50 at `BattleStartedEvent.Pre`; the battle displayed L50, and a
recheck three seconds in still read L50, so **RCT does not re-derive its team after the event**.
Stats rescaled correctly with the level.

Two findings that make this cheaper than designed. The NPC team is a `safeCopyOf` **battle
clone** — `clonedFromOriginal=true`, `originalLevel=63->63` — so the authored trainer is never
touched and the NPC path needs **none** of `GymBattleAdjustHook`'s NBT restore machinery, which
exists only because the player's side aliases the real Pokémon. And the timing is structural, not
lucky: `startBattle` posts `BATTLE_STARTED_PRE` and only then calls `startShowdown`, which packs
the team by reading `effectedPokemon` fresh, so every Pre subscriber has run before the engine
sees a team.

*Still to check:* only downward scaling (63 → 50) was exercised, and a ladder needs upward. Same
mechanism, but the setter clamps at `maxPokemonLevel`.

**Unverified: RCT's license**, which must be checked before it becomes anything more than a soft
dependency (§1.2).

### 2.7 Boss trainers are dedicated authored trainers

**Chosen:** a fixed trainer with an authored team at set wave intervals, authored **specifically
for this mode** as RCT trainers.

**Rejected — reusing our own `gym_01`–`gym_24`.** Considered and turned down: the server's gym
leaders and E4 are content players already fight, and recycling them makes the mode a rerun
rather than its own thing.

**Revised 2026-07-28 — use PokéRogue's teams, but do not ship them.** The earlier rejection of
transcribing their rosters rested on two arguments, and §2.19 dissolved one of them: at 200 waves
their schedule (E4 at waves 182–188, champion at 190) now lands *exactly*, because we adopted
their run length. What remains is the licensing argument, and it only bites on distribution —
their code is **AGPL-3.0-only**, their docs and assets **CC-BY-NC-SA-4.0**.

Because the roster is **data**, that splits cleanly:

- **Our server** uses PokéRogue-derived rosters, transcribed into our own trainer format and kept
  in a server-side datapack. Nothing is vendored into this repo's mod source.
- **A published build** ships the schema and a neutral, authored default roster — never the
  transcribed one. The mod stays distributable; the transcription stays private server content.

**Still true, and it limits what "the same teams" can mean:** their parties are not fixed lists.
Generation is seeded and tiered — signature species plus filtered pools plus wave-dependent
templates — so a leader's team varies by wave and by run. Transcribing produces *a* plausible
team for a leader, not *the* team. What ports faithfully is the pattern; what ports literally is
a snapshot of it.

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

**Chosen, revised 2026-07-28: the mark is earned INSIDE runs, not on the server.** Every species
starts at a flat **base 10 IVs**, and the best IVs of that species *caught during a run* become
its floor for future runs.

This supersedes sourcing the floor from server catches. The reason is the same one behind §2.13's
budget: the roguelite's own progression should be earned by playing the roguelite. The server
Pokédex still decides **which** species you may start with (§2.15) — access is a server
achievement, quality is a run achievement, and the two no longer do the same job.

**Two earlier conclusions fall away with it.** The trade-in question — whether a traded Pokémon
raises the mark — is moot, since the server is no longer the source. And the one-time backfill
scan of a player's party and PC is unnecessary: everyone starts at base 10 regardless of history,
which is simpler and needs no new tracking of real storage at all.

**Why not scanning current possession** (considered when the mark was server-sourced): it
silently makes hoarding optimal, since a player who traded away a 6IV specimen would *lose* run
power for having done so, and the server wants a trade economy rather than boxes of insurance.

**Why not Unchained catch streaks** (considered and rejected): streaks are per-species and do not
carry across species, so they measure recent grinding rather than a collection, and they would
reward repetition of one species over breadth.

**Mechanism:** per-player, per-species record, in its own save file separate from runs. This *is*
new persistent state — Cobblemon's Pokédex stores forms, genders and shiny states, not IVs — but
it is module-internal and small.

**Floors rise per stat, not as a spread.** A "best spread" has no total order, and per-stat max
is monotone, so a catch can never cost a player a floor they already had.

**No backfill.** An earlier draft called for scanning each player's party and PC once to seed
their marks, so launch day would not reset every veteran to zero. Moving the source inside runs
removed the need: everyone starts at base 10 regardless of history, which is simpler and touches
real storage not at all.

**Credit goes to the evolution line's root** (decided 2026-07-28) — a caught Charizard candies
Charmander, which is PokéRogue's rule.

The deciding argument is accumulation. Crediting the species actually caught scatters a line's
earnings across as many ledgers as the line has stages, so a player who catches Charmander,
Charmeleon and Charizard over a run banks three separate piles and rarely reaches a passive on
any of them. Crediting the root means every catch in a line pays into the one thing the candy is
spent on. Note the choice is **not retroactive** — candy banked under one rule does not move.

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

### 2.19 A run is 200 waves, on PokéRogue's own level curve

**Chosen:** 200 waves, matching PokéRogue's Classic length, explicitly *because* it is long
enough to be an achievement rather than an afternoon. The level curve is therefore theirs
literally — `1 + wave/2 + (wave/25)²`, boss waves ×1.2 — with no scaling, since the curve and the
run length now come from the same place. Trainer waves every 5th, bosses every 10th (§2.14).

Three consequences, none of them blocking, all of them real:

**Levels flatten for the last third, and that is accepted.** Cobblemon's `maxPokemonLevel` is 100
and it is a **global** config value — the same class of lever as the gimmick booleans, so it
cannot be raised for runs alone. Their curve passes 100 at about wave 138 (bosses around wave
120) and would reach 165 by wave 200.

**Decided 2026-07-28:** use PokéRogue's scaling verbatim up to level 100, then continue the run
with levels pinned there. No retuning of the constants to fit 100 into 200 waves. So waves
~138–200 are all level 100 — roughly 30% of the run with no level progression, and difficulty
across it has to come from team quality, EVs, held items, gimmicks and boss design instead.
Defensible: a level-100 endgame where teams decide is what real Pokémon looks like. But it has to
be *designed* rather than discovered, and §5 keeps that open.

**A run is a multi-session commitment.** Cobblemon battles are not browser battles: at a couple
of minutes each, 200 waves is the better part of a day of play. Checkpointing (§2.3) already
makes that survivable, but it changes what "a run" is socially — most runs will span days.
Run expiry (§5) gets more delicate as a result: expiring someone's eight-hour run for a fortnight
of absence is a different act from expiring an abandoned ten-wave one.

**It sets the content scale — 40 non-wild encounters, not 60.** An earlier draft of this section
said "40 trainer battles and 20 boss battles", double-counting: 200/5 = 40 already *contains* the
twenty multiples of ten. With bosses winning the collision, a 200-wave run is **20 boss + 20
trainer + 160 wild**. Authored bands (§2.6) keep that from being 40 bespoke teams, but the roster
still has to be big enough that a player meeting their twentieth trainer has not seen it four
times already.

If 60 non-wild waves was actually wanted, the boss interval has to sit *between* trainer slots
(trainer every 5, boss every 7, say) rather than dividing them. That is a content call and it
doubles the roster requirement.

### 2.20 The payout is not currency

**Chosen:** runs pay out in **non-currency rewards**, configurable as data. Contents are decided
later; the shape is decided now.

**Why:** §2.18 charges currency to enter, and the mode is supposed to be a money *sink*. If the
payout were also currency, the sink only works when the average run loses money — which is a
hard thing to make feel good, and the moment it tips the other way the roguelite becomes the
best money loop on the server and starves the activities meant to feed it. Paying out in
something that is not money keeps currency flow reliably negative while the reward still reads
as generous, because nothing players are chasing is denominated in what they paid.

**Two consequences for code already written:**

1. **The payout stops needing an economy at all.** Granting configured rewards is something the
   module can do by itself, which is a better standalone story than depending on a host to bank
   an amount — a published build pays out properly with nothing registered.
2. **The seam we need is the *inverse* of the one we built.** `RunPayoutProvider` exists and
   takes an abstract amount; what is actually missing is a **charge** seam, because §2.18's
   entry fee *is* currency and the module has no economy and must never grow one (§2.2). The
   payout provider's shape should be revisited — resolving a configured payout table into
   concrete grants, with the host hook demoted to an optional extra rather than the only route
   out.

**Format:** reuse §2.12's datapack convention rather than inventing a second one.

### 2.21 The party starts at level 1 and levels on PokéRogue's curve

**Chosen:** run starters begin at **level 1**, and the party's progression follows PokéRogue's
scaling — the same curve the opponents are on (§2.19). A level-1 starter against a wave-1
opponent is consistent, since the curve puts wave 1 at roughly level 1.

**Why it matters that this was decided:** the opponent curve alone does not describe difficulty.
If the party lagged the curve, every wave would get harder than intended; if it outran the curve,
the run would trivialise. Tying both to the same scaling is what makes the difficulty a designed
quantity rather than an emergent one.

**Decided: battle EXP**, PokéRogue's own mechanism, rather than setting levels to the curve.
Levels are earned, not assigned, so the party can run ahead of or behind the curve depending on
how the run has gone. **A Pokémon caught mid-run joins at its own encounter level**, not at the
party's.

**Consequence — this only creates a catch-up cost if the party outpaces the curve, so EXP must
be tuned so that it does.** A wild encounter at wave N is generated *at* the curve level for wave
N (§2.14), so a fresh catch joins at parity with the curve, not behind it. It is behind the
*party* only to the extent the party has pulled ahead by fighting. If EXP were tuned so the party
tracks the curve exactly, a mid-run catch would arrive at parity and cost nothing — which is
precisely the outcome this decision was meant to avoid. Tuning the party modestly ahead of the
curve is what makes catching a trade-off, and it is also what makes a 200-wave run winnable.

**Consequence — EXP stops mattering at the cap.** The party reaches level 100 at roughly the same
point the opponents do (§2.19, around wave 138). From there EXP rewards are dead weight and the
reward table has to shift entirely onto EVs, held items, ability patches and gimmicks. That is
the same wave band whose escalation §5 still lists as unresolved; they are one problem.

**Watch:** Cobblemon species have different EXP growth rates, so a slow-growth species will lag a
fast-growth one on identical EXP. That is true in PokéRogue too and is not inherently a bug, but
it means the tuning target is a band, not a line.

### 2.22 Pausing is a stated price, not a hidden one

**Chosen:** a `/roguelite pause` command that always works, and whose job is to make sure a player
is **never surprised by the disconnect penalty**.

- **Between waves:** free. There is no battle in progress, the run is already checkpointed, and
  leaving costs nothing. This is where most quits actually happen.
- **Mid-battle:** the command states the price — *leaving now costs the Pokémon on the field* —
  and asks for confirmation. Confirming, or simply dropping, costs the same. The command does not
  avoid the penalty; it discloses it.

**Why not a real pause mid-battle.** It would need the battle state, and there is no way to
capture it. `ShowdownService` — Cobblemon's entire interface to the engine — is `openConnection`,
`closeConnection`, `startBattle`, `endBattle`, `send` and registry data. No serialise, no
restore, no `inputLog`, no readable or settable seed. Pokémon Showdown can itself replay a battle
deterministically from an input log, so this is not impossible in principle, but reaching it
means driving the bundle directly — which §2.4 already refused on its own merits.

The other route, a lossy snapshot of party HP/status/PP with the wave restarted, was rejected on
feel rather than cost: it silently drops stat boosts, hazards, weather and terrain counters,
choice lock and Substitute, so it would sometimes favour the player and sometimes rob them,
unpredictably. Not offering a restore is better than offering one that lies.

**Decided 2026-07-28 — the cases the decision above did not name.**

- **No run at all:** it answers, it does not refuse. "Unknown command" or a silent no-op leaves a
  player unable to tell whether they have no run or whether pause does not work here, and those
  imply opposite things about logging off. A paid start with no starter picked is likewise free —
  there is no party to lose.
- **Confirming mid-battle acknowledges and nothing more.** It does not charge the penalty early
  (that punishes a player who read the warning and stayed) and it does not clear the marker (which
  would make the drop that follows *free* — §2.10's hole, opened by the command that explains it).
  Nor does it end the battle: a clean forfeit is a separate decision that would need §2.10's price
  argued again, and there is no live battle to end until `RunWaves` is implemented.
- **The warning names no Pokémon.** Until the wave handler reports switches through
  `battleFieldChanged`, the marker still holds the party *lead*, so a named Pokémon would be
  confidently wrong for anyone who has switched — and it is the one detail a player would check the
  warning against. "What you have out" is true either way.

**Implemented 2026-07-28** (`run/RunPause`, `/roguelite pause [confirm]`).

**Why this matters beyond convenience:** it converts §2.10's penalty from something players
discover by losing a Pokémon into a price they choose. The rule is unchanged; only its visibility
is.

### 2.23 Arena slots are held only while a player is in one; expiry is nearly irrelevant

**The premise this corrects.** Earlier sections treated an arena slot as held for a whole run,
which made expiry a *capacity* problem: a bounded grid, runs spanning days, and a queue of
absent players starving new starts. That framing was wrong. **A run only occupies an arena while
its player is online and in it.** On logout the run state is already saved, so the slot can be
released and reacquired on return — re-stamping is idempotent and cheap, which is what makes this
free.

**So expiry is storage hygiene, not capacity management**, and it can be generous. A run is a
handful of Pokémon and some counters; a player holds one at a time. There is no volume here worth
policing.

**Chosen:**

- **Activity means playing the run.** Progressing a wave is activity; merely logging in is not.
  A player who logs in daily and never touches their run is not using it.
- **The period scales with depth.** The deepest runs (wave 100+) last **six months**; shallower
  runs expire sooner. A wave-3 run nobody returned to is worth nothing; a wave-150 run is many
  hours of play and should outlive any reasonable absence.
- **Expiry pays nothing.** Someone who has not touched a run in six months is not owed a payout,
  and paying one would land on an absent player anyway.

**Implemented 2026-07-29.** A slot is leased for a *session*: logout releases it, the next resume
reacquires. Expiry is depth-scaled from 7 days below wave 10 to 180 at wave 100+, activity means
a wave started or cleared, and the sweep runs at server start rather than lazily on login —
a lazy check would keep exactly the runs the feature exists to remove.

**One consequence worth knowing:** the lease fields (slot, stamped template, painted biome) are
deliberately **not persisted**. That is what makes a crash safe — the process reloads with an
empty grid rather than slots leased to disconnected players. Persisting them had the worse
failure of the two, since a restored slot that had since been reassigned puts two players in one
arena.

### 2.24 Biomes: the arena becomes the place you are

**Chosen:** a run moves through **biomes**, one per wave band (PokéRogue changes region every ten
waves), and the arena *becomes* that biome rather than merely being decorated for it.

**Two mechanisms, and the second is the one that sells it:**

1. **The arena template changes.** Already decided in §2.19 — the slot stays allocated and only
   the stamped structure changes at a band boundary. Biomes are simply what those bands *are*.
2. **The Minecraft biome of the arena box is repainted** to match, via `FillBiomeCommand.fill`,
   which is public static and does the chunk rewrite and client resend itself. That changes sky
   colour, fog, water and grass tint, ambient sound loops and biome music — the whole feel of the
   place — for a call we do not have to write. A stamped structure alone reads as scenery; a
   repainted biome reads as somewhere else.

**Consequence worth taking:** a biome is a natural key for the wild encounter pool.
`WaveSpeciesPool.eligibleAt` was deliberately built so that "everything the data side wants to do
with tiers, segments, biomes or evolution stages collapses into this call", so biome-gated
encounters need no new plumbing — only the biome in the key. Whether to use it that way is a
content decision, but the mechanism should not be foreclosed.

**Open:** whether biome transitions are **chosen** by the player (PokéRogue offers a branch) or
follow a seeded path. A branch is the more interesting mechanic and is the reason the biome
belongs in `RunState` rather than being derived from the wave number — derive it and a choice can
never be added without a schema change. Implemented seeded; the seam is one function.

**Implementation note worth keeping** (found 2026-07-28): `FillBiomeCommand.fill` refuses any
region larger than the `commandModificationBlockLimit` game rule, default **32768**, and a default
arena box is roughly 108,000 cells after quantisation. The naive single call is therefore refused
on every server, every time, and the refusal arrives as a value rather than an exception — so it
would have failed silently and completely. The repaint is sliced to fit. Anyone changing the
arena box size needs to know this limit exists.

### 2.25 The badge gate has an operator override

**Chosen:** an op-only override that bypasses §2.18's badge-gated depth cap.

**Why it is not just a testing convenience:** the gate reads *server* advancements, so on a dev
server nobody has the badges, and every run is capped at the shallowest tier. Without an override
the deep half of a 200-wave ladder — bosses, the flat-level last third, the E4 waves — is
unreachable by the people who need to test it. The override is what makes the back of the run
testable at all.

**Constraints:** op-only, per-player, obvious in the log when it is in force, and never the
default. A run started under an override should be identifiable, so an inflated leaderboard entry
can be told apart from an honest one.

### 2.26 A payout owed to an offline player is held, not dropped

**Chosen:** hold the grants and drop them at the player's feet on **next login**.

**Why this is not an edge case:** §2.10's disconnect penalty can wipe a party, which ends the run
and owes a payout to someone who is by definition not there. Dropping at the moment the run ends
would put items in the world for five minutes and then lose them — in exactly the case the
mechanism exists for, while the log said it paid.

**Delivery waits for a safe moment.** Login *arms* it; the drop happens once the player is
settled, alive, not in a run and not in arena space. Our own login hooks teleport people, a
player on the respawn screen is "online" by every test the server makes, and dropping into a
sealed arena puts permanent items in a dimension whose blocks are rewritten between waves. There
is no timeout on that wait — the debt is on disk, so waiting costs nothing, and a timeout would
only convert a safe wait into a delivery somewhere already judged wrong.

**It errs toward losing a payout rather than duplicating one.** A crash between the ledger write
and the items existing pays zero, never twice. Paying twice is unbounded and invisible — the
crash window repeats on every restart and nothing distinguishes the copy, which is precisely the
faucet §2.2 refuses. Paying zero is single and repairable, from a log line written *before* the
claim naming the player and every item.

**Held payouts never expire**, and this is deliberately *not* inherited from §2.23. Expiring a
run and expiring a debt are different acts: an untouched run is owed nothing, whereas a held
payout is already owed, earned and resolved, and is waiting only because the server chose the
moment of delivery. If the file ever grows, the honest fix is an op command that lists and clears
it — not a timer that quietly deletes debts.

### 2.27 The candy "passive" is a hidden-ability unlock

**Considered:** PokéRogue's actual passive — a *second* ability stacking on the Pokémon's own ·
unlocking the species' **hidden ability**.

**Chosen:** the hidden ability. Candy buys certainty of it on a starter.

**Why not a true passive.** Three reasons, and the first two are structural rather than about
balance:

- **Showdown applies one ability.** A second stacking ability means patching the bundle — refused
  in §2.4 as a permanent maintenance tax and incompatible with §1.2 — or hand-implementing each
  passive mod-side. That is not one feature; it is an open-ended catalogue.
- **Our opponents cannot compensate.** PokéRogue gives passives to enemies too and buffs bosses
  procedurally, so its arms race self-balances. Ours are **authored** RCT teams (§2.7), so
  player-only passives are straight power creep and the fix would be re-authoring every roster
  entry rather than turning a dial.
- **It would land where we are already flat.** §2.19 leaves the last third of a run pinned at
  level 100, with difficulty coming from teams, items and gimmicks. A second ability on every
  party member would overshoot that gap rather than fill it.

**Why the hidden ability is the better fit.** `Pokemon.ability` is settable and species data
already carries hidden abilities — Cobblemon defines the concept and never rolls it
(`HiddenAbility.isSatisfiedBy` returns false behind a `TODO`), so this is a slot the game
declares and leaves unused. §2.16 already adopted PokéRogue's 1/256 hidden-ability rate for
encounters, so candy buying *certainty* is the natural extension of a rate we took anyway: the
player is paying to stop gambling. And the cost table already prices ability access — Torchic
costs 4 largely because Speed Boost, its hidden ability, is that strong.

Power is bounded in a way a passive is not: hidden abilities are official and balanced, rather
than hand-picked to make a species S-tier.

**The weakness, and the fix.** Hidden abilities vary wildly in worth — Speed Boost is
transformative, Truant is a joke — whereas PokéRogue hand-assigns passives to be good. So the
granted ability is **datapack-defined per species**, defaulting to the species' hidden ability
and overridable. A server can hand-assign a better one where the hidden ability is worthless,
exactly as PokéRogue does, without ever needing a second ability slot.

**Name it what it is.** "Passive" describes a mechanic we are not building, and the term would
mislead anyone arriving from PokéRogue.

### 2.28 Content decisions, 2026-07-29

**Baseline starter pool: the classic starter Pokémon.** Every player can pick from them regardless
of their Pokédex, which is what §2.15 requires a baseline to do — a new player must get a real
choice, not an empty catalogue. Their server Pokédex widens it from there.

**Entry fee: a flat 5,000.** Not scaled by depth tier. Simple to explain, and §2.16 only asks that
the fee be non-trivial relative to what players hold, since it is what prices an abandon-and-restart.

**Payout: Poké eggs, tiered by depth.** Eggs are exactly the shape §2.20 asked for — valuable,
wanted, and *not currency*, so the entry fee stays a real sink. They also feed the gacha, which
is a system the mode already wanted to point players at.

**Determinism is preserved, and the difficulty does the gating.** §2.20 makes the payout a
deterministic filter rather than a draw, so "you cannot get an ultra-rare every time" is achieved
by *depth bands* rather than randomness: an ultra-tier egg sits behind a wave nobody reaches
casually. Two identical runs still pay identically, which is what keeps the payout auditable.
Note the egg tables themselves are our server's gacha content, so they are server-side datapack
data; a published build ships something else.

**Candy prices: PokéRogue's, which are per-tier and inverse.** Their `allStarterCandyCosts` is
indexed by starter cost 1–10: a **1-cost** species needs **40** candy for its unlock and reductions
at 25/60, while a **10-cost** species needs only **10** and 5/15. That is deliberate on their part —
you catch cheap species constantly and expensive ones rarely, so the price compensates for the
rate candy accrues.

**This corrects what we shipped.** Our default is a *flat* 40 with reductions at 20/50, which
overcharges strong species and undercharges weak ones — the opposite of the intent. The same file
carries friendship caps scaling 25–600 by cost, against our flat 150 placeholder; both are the
`friendshipThresholdByCost` seam that was left empty for exactly this.

### 2.29 Arenas are generated from a palette, not built by hand

**Chosen:** the arena is **generated in code** from a per-biome block palette — floor, border,
dimensions — rather than stamped from a hand-built `StructureTemplate`. A hand-built `.nbt`
remains supported as an override.

**Why, and it is not only about who can build.** A published build (§1.2) cannot depend on a
hand-made structure either: it would have to *ship* one, and that one becomes somebody's taste
imposed on every server that installs the mod. Generation from data is the shape that works for
both us and a stranger's server.

**Why a plain platform is enough.** The atmosphere is not carried by the build — it is carried by
§2.24's biome repaint. Sky colour, fog, water and grass tint, ambient loops and biome music all
change when the arena's Minecraft biome is repainted. A basalt platform under orange fog with
volcanic ambience reads as a volcano; the structure was never going to do that work.

**What this changes and what it does not.** The slot grid, chunk tickets, stamp-on-assignment,
band transitions and the repaint all stay exactly as they are — only "read an `.nbt`" becomes
"place blocks from a palette". The palette is content a non-builder can author: it is a choice of
**blocks**, not of architecture.

**Rejected for now — worldgen arenas.** Switching the arena dimension to a noise generator so each
slot sits in real terrain, with a biome transition moving the player to a matching cell, needs no
authoring at all and would look better. It also trades away the sealed-box property that makes
arenas cheap and predictable, complicates the slot grid, and stops guaranteeing flat ground.
Worth revisiting if generated platforms feel sterile *in play* — a judgement to make after seeing
one, not before.

### 2.30 Trainer teams are generated, not authored

**Chosen:** a roster entry stores a trainer's **signature species**, and the team is generated at
the encounter from `(seed, wave)`. PokéRogue's `signature-species.ts` gives ~80 gym leaders as
four slots each, where a slot is one species or a set of alternatives; that table is the input.

**Why this became possible.** §2.6 assumed RCT would supply the team, so the team had to be
authored JSON. It doesn't — RCT's own battle path builds the *player's* side from their real
party, so the bridge provider already assembles battles itself. The team was ours to decide all
along.

**It also retires the reason bands encoded teams.** Bands existed because level-scaling an
authored team does not scale its *movesets* — a team written for L15 still throws L15 moves at
L60. Generating at the encounter derives the moveset for the level being generated, so that
problem disappears. Bands now encode only **which leaders appear when**, and the evolution stage.

**Settled details:**

- **Alternatives are a seeded pick** at the encounter, so Brock differs between runs and is stable
  within one. Generating Brock-A and Brock-B as separate roster entries was rejected: the roster
  would happily draw both, and meeting Brock twice in a run reads as a bug.
- **Party size scales by band** — 4 early, 5 mid, 6 late. This matters beyond fidelity: §2.19
  leaves the last third of a run at flat level 100, so party size is one of the few difficulty
  levers still available up there.
- **Evolution stage comes from the band**, which is PokéRogue's "fully evolved from wave 80" rule
  expressed as our band structure.
- **No EVs.** PokéRogue removed EVs from stat calculation entirely, so trainers get none.
- **Held items are generated**, scaled by band and boss status, mirroring their `genModifiers`.

**The EV asymmetry is deliberate, not an oversight.** Our *players* earn EVs as a reward (§2.4's
mechanism 1), because EVs are our substitute for PokéRogue's stacking modifiers, which had no
Showdown equivalent. Their trainers have no EVs and no modifier stacks; ours have no EVs and
some held items. The two sides stay consistent with each other.

**Authored fights remain available** and should be used for the E4, the champion, and any rival
worth curating. A generated team is uniform by nature; the fights players remember are the ones
someone tuned. The roster can already name a specific trainer id, so an authored fight is one
entry that points at one rather than at a generator.

### 2.31 What we can actually do to a live battle

Investigated 2026-07-29 against the deployed Cobblemon 1.7.3 jar, its unbundled Showdown on the
dev VM, and a GraalJS probe. This corrects §2.4 and reopens two mechanics it wrote off.

**Datapack JS is real code, not name mapping.** `data/<ns>/abilities/*.js`, `held_items/*.js`,
`moves/*.js` and `bag_items/*.js` are each scanned and pushed to Showdown, and they carry genuine
handler functions with access to the battle and the Pokémon. A custom ability is then assignable
from Kotlin via `updateAbility(..., forced = true)`, which bypasses the species pool. This is the
channel to build on: stock Cobblemon, no mixin, no bundle contact, survives publication.

**Bundle patching is now disqualified for a stronger reason than §2.4 gave.** Mega Showdown
**already overwrites** Cobblemon's `sim/battle.js`, `sim/side.js`, `data/abilities.js` and others
on our server — the deployed files are byte-identical to those inside its jar. Patching the
bundle would put us in a load-order fight with a mod we depend on, on our own machine, before
publication is even considered. Its version string (`1.8.2+1.7.3`) encodes the Cobblemon release
whose bundle it replaces, which is §2.4's "maintenance tax" made observable.

**The GraalJS context is public and reachable.** `GraalShowdownService.context` is a public field
on a public singleton; live `Battle` objects are readable and writable from Kotlin, verified by
probe. Mega Showdown already does exactly this. The gotcha is that Graal is **relocated** into
`com.cobblemon.mod.relocations.graalvm.polyglot`. Treat this as the emergency lever, not the
foundation: its whole fragility surface is two symbols plus a package name, which is a far
smaller update-time contract than patching the bundle.

**`>eval` exists in stock Showdown** and `PokemonBattle.writeShowdownAction` reaches it with no
validation — a public path to arbitrary mutation. It costs red chat spam, because the eval case
emits protocol lines the interpreter broadcasts. Escape hatch, not foundation.

**Boss shields are cheap.** One custom ability of ~60–80 lines expresses both halves: floor
incoming damage at the next segment boundary, and boost a random stat when a boundary breaks. The
`-boost` line comes back through the normal interpreter, so the player sees it happen correctly.

**Stacking is partly recoverable.** Showdown's one-item limit constrains *count*, not
*magnitude*: pre-generate tiers as separate datapack items and swap between waves. The player
experiences stacking; Showdown only ever sees one item. The ability slot gives a second axis.

**Levels above 100: Showdown is not the blocker** — it clamps to 9999 and scales stats correctly.
Cobblemon's global `maxPokemonLevel` is. A sim-only bump is a **trap**: HP instructions write
*absolute* values from the sim, so a Pokémon whose sim maxhp exceeds its Cobblemon maxhp shows a
full health bar until the sim drops below the real cap. The workable route is raising the config
and re-clamping normal play with two mixins on the EXP paths.

**Hard ceiling regardless:** poke-engine stores level as `i8` and panics above **127**, and a pyo3
panic escapes the bridge's guards. Cap at 127 while the bridge exists.

### 2.32 Boss shields are an unremovable held item, and they announce themselves

**Chosen:** a boss's HP is segmented, and breaking a segment boosts a random stat — implemented as
a **custom held item** shipped as datapack JS (§2.31), not as an ability.

**Why the item slot and not the ability slot.** Forcing a custom ability would strip the
Pokémon's own — a boss Gyarados would lose Intimidate to gain its shields, quietly removing the
thing that makes it feel like that species. On the item slot the shields ride alongside whatever
ability the Pokémon actually has. The cost is the boss's held item, which is the right budget to
spend: the shields *are* its power.

**The item must refuse removal.** Otherwise Knock Off, Trick or Magic Room delete a boss's
shields mid-fight, which reads as a bug rather than counterplay. `onTakeItem` returning false is
how Mega Stones already do this, so it is a supported property rather than a workaround.

**Signalling is part of the mechanic, not polish.** An unexplained damage floor is *worse* than
no mechanic: a player who watches a lethal hit land for 80% with no explanation concludes the mod
is broken. Three message points, all from the item's own JS:

- **At battle start** — the Pokémon is shielded, and how many.
- **On each absorb** — the shield held. This is the one that prevents the bug report.
- **On each break** — a shield shattered, and which stat rose. The `-boost` itself already comes
  back through the normal interpreter; the sentence supplies the *cause*.

**The Pokémon is marked statically, by name** — "Boss Onix" — set once at send-in. Putting the
shield *count* in the nickname was rejected twice over: whether a mid-battle nickname change even
reaches the client is unverified, and a Pokémon whose name changes as you hit it breaks a rule
players know. The name says *what kind of fight this is*; the messages carry the running count.

**Item suppression is a hole, and it is closed inside run battles only.** `onTakeItem` blocks
*removal* — Knock Off, Trick — but **Magic Room, Embargo and Klutz do not remove anything**. They
make Showdown stop consulting the item, so our handlers never run and the shields simply switch
off. The ability slot would not have saved us: Gastro Acid and Neutralizing Gas do the same to
abilities. No slot in Showdown is un-suppressable.

**Chosen: detect the suppression and cancel it, scoped to run battles containing a shielded
boss**, with a message framing it as the boss resisting. One place to fix, covers all three
effects, and it reads as boss design rather than as a patch.

**Deliberately not scoped wider.** Magic Room is untouched everywhere else on the server —
ranked, gyms, wild, PvP — and untouched even inside a run battle with no shielded boss on the
field. The move is not being nerfed; a boss is being made immune, which is an ordinary thing for
a boss to be. Note that cancelling a field effect also restores the *player's* items, so the fix
favours the player rather than taxing them.

**Rejected — banning the moves at format level.** Showdown's banlist rejects *teams* containing a
banned move at validation, so a player who caught something with Magic Room mid-run would find
their whole team refused. Far worse than the exploit.

**Rejected — restoring HP after the fact** when a hit crossed a boundary while suppressed. Robust
against suppression mechanisms we have not thought of, but it means visibly healing the boss
after damage, which looks like a bug even when it is working correctly.

**Sequencing:** implement after the dev pass confirms the base mechanic fires at all. Hardening a
mechanic nobody has seen work once risks building on something that needs reshaping anyway.

**A segmented HP bar needs a client mod, and is deliberately deferred.** It is the faithful
version, and we already ship client mods — but it costs the standalone story, and nobody yet
knows whether the marker plus messages are enough. The shield state lives server-side either way,
so a client mod later *reads state that already exists*. It is additive, not a rewrite, which is
what makes waiting cheap.

### 2.33 Player-side stacking: depth, not breadth

**Chosen:** recover PokéRogue's stacking modifiers as **tiered datapack held items**, using the
pattern boss shields proved (§2.32) — one script per tier, no Minecraft item registered, the tier
carried in a component naming the Showdown id, implementation shared behind a global.

A reward pick grants a modifier or upgrades its tier, and the item on the run Pokémon *is* the
stack. Because run Pokémon are real Cobblemon Pokémon in our own store, the stack persists across
waves for free.

**The honest limitation: one line per Pokémon, not many.** Showdown allows one held item, so a
Pokémon can stack one modifier *deeply* — five tiers of extra hits — but cannot also carry berries
and a type booster at the same time. PokéRogue piles several different modifiers on one Pokémon;
we can pile one, high.

**Depth is preserved, breadth is not**, and that is the trade. It is also a more interesting
decision than PokéRogue's in one respect: with a single slot, choosing *which* line a Pokémon
commits to actually matters, where stacking everything eventually does not.

**Amended 2026-07-29 — this section overstated the loss.** In PokéRogue, vitamins, Macho Brace,
Shuckle Juice, Old Gateau and Lucky Egg all occupy the *same held-item budget* as Leftovers and
Multi Lens. For us those are mechanism 1 and cost no slot at all. So our single slot is contested
only by *battle-effect* items, where PokéRogue's is contested by battle-effect **and** stat items.
The audit in §2.34 found only **four** genuinely lost combinations — see there.

**The ability slot is a second axis** if one line proves too tight — a custom ability can carry an
effect the same way. Worth holding until play shows it is needed rather than spending it now.

**Multi Lens is reachable**, contrary to §2.4's original claim: `onModifyMove` can set
`move.multihit`, so extra hits are ordinary item behaviour rather than an engine change.

**In-battle one-shots** — X Attack, screens, hazards — belong in `bag_items/*.js` instead, which
already receives the raw battle object. Those stack as consumables rather than as tiers.

**Sequencing: after the dev pass.** This rides on exactly the channel §2.32 is waiting to have
confirmed. Building a second feature on an unverified mechanism before the first one has fired
once is how both end up needing rework.

### 2.34 The modifier audit

Full pass over PokéRogue's `modifier-type.ts` / `modifier.ts` against what our channels express,
2026-07-29. The menu, not a guess.

**A large slice is already free.** Eviolite, the species stat boosters (Light Ball, Thick Club,
Metal Powder, Quick Powder, Deep Sea Scale and Tooth), Toxic and Flame Orb, Leek, and **every
berry** exist in stock Showdown at the same multipliers. They cost one Kotlin line each to make
obtainable — no JS at all.

**`consumed:false` gives berry persistence for free.** The `held_item_effect` component's flag
decides whether the *ItemStack* dies when Showdown consumes the item, so a berry eaten in a wave
is back for the next one. That is PokéRogue's Berry Pouch behaviour with nothing to build, and
Berry Pouch becomes redundant rather than a feature.

**Multi Lens is confirmed with a mechanism.** `onModifyMove` sets `move.multihit`, which
`hitStepMoveHitLoop` then reads — Parental Bond does exactly this assignment. The per-hit damage
split must be added by us via `onModifyDamage`, because only `multihitType === "parentalbond"`
gets halved automatically.

**Stacking is identity, not quantity.** PokéRogue counts stacks on one modifier; Showdown
distinguishes items. So a reward pick is always "upgrade Multi Lens 1→2", never "add a second
one", and every tier ceiling is *our* balance choice rather than theirs.

**Only four combinations are genuinely lost:** Type Booster + Multi Lens (the canonical PokéRogue
snowball), King's Rock + Multi Lens, Leftovers + Reviver Seed, and Focus Band + Reviver Seed. The
last pair are near-duplicates anyway and should not both ship, or they read as the same reward
twice.

**An honest magnitude gap on vitamins.** PokéRogue multiplies a *base stat* by `1 + 0.1 × stack`,
up to as many stacks as the Pokémon's IV in that stat — potentially ×4. Cobblemon EVs cap at
252 per stat and 510 total, a far smaller ceiling, and Cobblemon has no base-stat override API.
Matching it would cost the held-item slot. **Accept the smaller ceiling and tune wave difficulty
to it.**

**Two blockers of our own making, both worth knowing:**

- **Bag items are impossible inside a run because §2.11 says so.** The blanket rejection of
  bag-item actions also blocks *run-issued* ones, so PokéRogue's X Attack line and Dire Hit
  cannot ship until that guard becomes "reject player-owned, allow run-issued". That is a
  decision reversal, not an implementation detail. Note those effects are **player-wide and last
  five battles** in PokéRogue, so the faithful version is run state applied at battle start, not
  a consumable.
- **~8 modifiers need an in-run currency.** `RunState.credits` exists and is documented as
  "spent in the between-wave shop", but no shop was ever designed. The audit surfaced the gap;
  nothing has closed it.

**Grip Claw and Mini Black Hole are blocked by Cobblemon, not Showdown.**
`CobblemonHeldItemManager` early-returns on every give/take item effect, commented as a temporary
anti-duping fix. Item theft would need our own stack-moving code and opens a dupe surface.

**Enemy buff tokens map to the ability axis** on generated enemy teams (§2.30) — damage up,
damage reduction, regen, status chances, endure. But an ability on every enemy strips its real
ability, which is the exact objection §2.32 raised against ability-slot shields. Folding the
first three into §2.6's runtime scaling instead is cheaper and costs nothing; decide deliberately.

**Recommended first set — eight, chosen to share one harness:** Attack Type Booster (18 types,
generated), Leftovers, Focus Band, King's Rock, Shell Bell, Multi Lens, Reviver Seed, and the
free berries. All but the last two use hooks the boss shield already proved, so the risk sits in
the *tiering harness* rather than in eight separate unknowns. Build the harness plus Leftovers,
Focus Band and King's Rock first — three near-verbatim stock copies validating the harness on the
cheapest possible payload.

### 2.35 Credits are a within-run resource and never leave

**The gap §2.34 exposed:** `RunState.credits` has existed since the first commit, documented as
"spent in the between-wave shop" — and no shop was ever designed. Around eight of PokéRogue's
modifiers (Amulet Coin, Coin Case, Nugget, Relic Gold, Golden Punch, Healing Charm, Lock Capsule)
have no meaning without one.

**Chosen:** credits are real, earned inside a run, spent inside a run, and **die with it**.
Unspent credits convert to nothing.

**This corrects a stale line.** `RunState`'s own docs say credits are "converted to server currency
at run end". That predates §2.20, which made the payout **non-currency** precisely so the entry
fee stays a real sink. Converting credits would quietly reopen the faucet §2.18 and §2.20 closed
together. The doc comment is wrong and should be fixed with the implementation.

**Two separate things, and conflating them would flatten both.** The **reward pick** after a wave
is free and is where power comes from (§2.4 mechanism 1, and §2.33's modifier tiers). The **shop**
is where credits go — consumables, balls (§2.13 made balls earned rather than unlimited), healing.
PokéRogue runs both, and they do different jobs: the pick is a decision about your build, the shop
is a decision about your resources.

**Why credits dying with the run is the right call**, beyond consistency with §2.20: a currency
that leaves makes every run a farming decision, and the mode already has exactly one metered
channel out. Credits that expire also make *spending* them correct — a hoarded balance is wasted,
which is the behaviour a between-wave shop wants.

**Open, and all of it content:** what credits are earned per wave kind, what the shop stocks, what
things cost, and whether the shop restocks per wave or per band. None of it blocks the mechanism.

### 2.36 Decision blitz, 2026-07-29

**Run-issued bag items are allowed.** §2.11's blanket rejection becomes "reject player-owned,
allow run-issued". The ban existed so a player's own potions could not trivialise permadeath;
items the *run* grants are the reward loop, not a bypass of it. This unblocks PokéRogue's X Attack
line — noting those are player-wide and last five battles there, so the faithful version is run
state applied at battle start rather than a consumable.

**A default arena palette ships**, keyed to biomes, so an unconfigured install is playable and
testable. §2.29 worried about imposing taste on every server; a plainly-labelled default that an
owner overrides is a smaller cost than a mode that cannot be entered.

**The milestone checkpoint interval is dropped.** §2.10 kept it as persistence granularity, but
checkpointing is per-wave and §2.23 rewrote how sessions work. It described nothing.

**Content mirrors PokéRogue except the payout.** The per-species cost table and the starter list
are extracted from their repo, and candy prices swap to their per-tier table (§2.28). Run
*rewards* stay ours — §2.28's depth-banded eggs — because those hook into our gacha and economy,
which theirs does not have.

**Rivals are a distinct encounter kind; the E4 and champion are not.** Checked rather than
assumed: the E4 sit at waves 182/184/186/188 and the champion at 190, always from one region —
fixed, strong, and otherwise ordinary bosses, which §2.30's fixed-encounter override already
expresses. **Rivals are different in kind.** They appear at waves 8, 25, 55, 95, 145 and 195 as
the *same character* whose team grows across the run — a regional starter and regional bird to
begin, gaining Pokémon each meeting, ending on Rayquaza. That is a run-long thread, not a boss:
it needs run state remembering which rival and what they have gained. Recorded as a real gap;
nothing implements it.

### 2.37 EVs are the flat third's difficulty lever, on both sides

**The problem (§2.19, §2.21):** from about wave 138 levels pin at 100 for everyone, and EXP
rewards go dead over the same band. For roughly 30% of a run — the part meant to be hardest —
nothing in the design escalates.

**PokéRogue's answer does not port.** Theirs is stacking enemy buff tokens, applied side-wide and
scaling with depth. Reproducing them needs a slot: the ability slot strips the Pokémon's real
ability (§2.32's objection), and the item slot on a boss is spent on shields. And the obvious
alternative — "just scale levels" — is precisely the lever that is capped.

**Chosen: EVs, which PokéRogue does not have at all.** They removed EVs from stat calculation
entirely, so §2.30 mirrored that and gave generated enemy teams none. That leaves 510 points per
Pokémon sitting unused, costing no item slot and no ability slot. **Enemy EV spreads ramp with
wave depth**, and they are the escalation the flat third was missing.

**It is symmetric, which is what makes it legible.** Players already earn EVs as rewards — §2.4
made them our substitute for PokéRogue's stacking modifiers. So in the late game both sides' power
curves run on the same currency: the player's earned through reward picks, the enemy's scaled by
depth. One shared mechanic that both sides visibly climb beats two unrelated ones.

**The full late-third ramp is therefore four levers, none of them levels:** more boss shields
deeper (§2.32), enemy EV spreads by wave, fully-evolved teams with better held items (§2.30), and
gimmick bands unlocking Tera and Dynamax (§2.5).

### 2.38 Trainer filler matches the specialty type, not the union

**Keep generated filler** — their signature table gives four species and our late bands run five
or six, so the gap is filled from the dex. It costs nothing and adds variety.

**But the matching was wrong, and this is why Brock got a Cramorant.** The extractor unions the
types of *every* signature species' final form. Brock's list contains Aerodactyl, which is
Rock/**Flying** — so Flying enters the pool and Cramorant matches on it.

**Fix: match the specialty type — the one common to the signature species — rather than the
union.** Brock's four are Rock/Ground, Rock/Ground, Rock/Water and Rock/Flying; the intersection
is **Rock**, which is exactly what a Brock filler should be. A union is a superset of every
theme a leader touches, which is the opposite of a theme.

### 2.39 Mirror their content by default; author only what they cannot have

**Standing rule:** where PokéRogue has a table, we extract it. Authoring from scratch is reserved
for things their game has no equivalent of. Earlier sections framed much of this as content to
write, which overstated the work considerably.

**Extracted from PokéRogue** (server-side datapack content only, never shipped — §2.7): per-species
starter costs · candy prices and friendship caps · **reward tables and the rarity curve**
(`init-modifier-pools.ts` carries tiered pools with weights) · **boss shield counts** (≥1, +1 at
level 100+, +1 at BST ≥670) · **shop stock, prices and money per battle** · **trainer held-item
tiers** (`genModifiers`, scaled by trainer strength) · the E4 and champion schedule and teams ·
the escalation ladder, mostly — leader Tera from wave 100, Mega Rayquaza on the final rival · the
biome list and its transition graph · the baseline starter set · trainer band edges, derivable
from their region ordering.

**Ours, because they have no equivalent:** the biome → **Minecraft biome** mapping · arena block
palettes · egg payout bands (our gacha) · the entry fee. Four things, two of them Minecraft-shaped
and two economic.

**Rivals are mirrored too**, as their own encounter kind (§2.36): waves 8/25/55/95/145/195, the
same character across a run, regional starter plus regional bird, gaining a Pokémon each meeting,
ending on Rayquaza. That needs run state remembering which rival and what they have gained — a
run-long thread rather than a boss.

**Why this is safe and why it is bounded.** Extraction reads their data at build time and emits
our format; nothing is vendored, and the output never enters the jar. The licence line from §2.7
is unchanged — transcription is private server content, and a published build ships neutral
defaults. What extraction cannot give us is anything Minecraft-shaped, which is exactly the list
above.

### 2.40 Balance rulings, 2026-07-31

Four decisions from the balance pass, recorded here; the detail lives in
`docs/roguelite-trainer-battles.md` (§2.4, §4).

- **The wild curve constants stopped being placeholders.** `WaveLevelCurve`'s defaults are now
  PokéRogue's Classic verbatim (`1 + wave/2 + (wave/25)²`, bosses ×1.2, cap 100) — §2.19 already
  decided this; the code caught up and `pokeRogueClassicCurve()` was retired as redundant. The
  jitter fields remain placeholders.
- **`getPartyLevels` is ported verbatim** (`WaveLevelCurve.partyMemberLevel`) and applied to every
  generated team: per-member levels with an ace-first strength spread, no jitter, and **no boss
  ×1.2** — their party-level formula has no boss term, so a generated boss's step-up is the
  strength spread plus §2.32 shields. The bridge's flat level-forcing is authored-path-only now;
  flattening a generated team would erase the spread.
- **Trainer bags are stripped** (Q4 resolved): `null` into the RCT actor. PokéRogue trainers never
  item mid-fight, and an authored bag would couple wave difficulty to which RCT id a roster named.
- **Victories award only ₽** — verified, not changed: `waveCleared` grants credits, the §2.15
  friendship stamp, and the between-wave pick; no per-trainer item grants, no voucher analogue.

The smoke roster (`ops/gen_roguelite_smoketest.py`) was switched to the generated-teams path for
every trainer and boss band at the same time, so Q1's end-to-end smoke run exercises generation
and the level spread rather than the authored path.

### 2.41 Reward and shop tables are PokéRogue transcriptions, 2026-07-31

The §3 blocker "reward table contents" is closed by §2.39's standing rule: extracted, not
authored. `ops/pokerogue-modifier-pools.json` holds the full extraction (their
`init-modifier-pools.ts` at commit `0d94c5bb`, every PLAYER/WILD/TRAINER-pool entry with its
weight or weight-function description), and `ops/gen_pokerogue_reward_tables.py` maps it through
§2.34's dispositions into `ops/roguelite-tables-pack` — server-side content per §2.7, replacing
the smoke-test tables. What is verbatim, reshaped, and dropped:

- **Verbatim:** tier odds (their 1024-roll windows, 75/19/4.7/1.2/0.1% as flat tier curves), the
  static entry weights, the shop's seven-row unlock schedule (waves 1/21/51/81/111/141/171) and
  cost multiples, three options per pick, boss waves shopless.
- **Reshaped, and this grew a mechanism:** their healing items weight themselves by *party
  state* (`(party) => …` functions). `RewardEntry.scaledBy` now carries that as two closed
  conditions — `injured` (× total missing-HP fraction) and `fainted` (× fainted count) — fed
  from the real party at roll time. A full-health party is never offered potions, which the
  economy reference called the one idea to take first. Consequence: healing between opening the
  screen and picking legitimately re-rolls the healing slots; `take`'s existing stale-screen
  guard covers it. Generator entries (BERRY, MINT, TM, type/temp boosters) fan out into
  per-item entries splitting the generator's weight.
- **Dropped, each with its reason in the script's `DROPPED` table:** money/meta items (no
  channel, §2.35), species/ability-conditional items (no condition to express — a future
  `scaled_by` axis), §2.33 tiered items (harness not built), item-theft items (Cobblemon
  blocks), gimmick unlocks (§2.5 wiring undecided). The generator fails if a re-extraction
  contains an id that is neither mapped nor dropped.
- **Not mirrored:** the looping luck upgrade (no luck stat — recorded divergence), their TM
  tier contents (per-species learnset partitions; `TM_MOVES` in the script is a curated
  operator knob, the one hand-authored list in the file).
- **Correction from the extraction:** the post-wave pick always draws their PLAYER pool;
  `ModifierPoolType.TRAINER`/`WILD` are enemy held-item pools. The trainer-battles doc §1.5 was
  wrong about this and is fixed.

### 2.42 First-playtest rulings, 2026-07-31 evening

Rulings from the first real dev runs, each reversing or completing an earlier section:

- **A faint is a faint, not a death — §2.13's permadeath is reversed.** The playtest caught the
  contradiction directly: the tables sell Revives (ruled in with §2.11's reversal), and a
  permadeath-deleted Pokémon is not a revive target — the player watched a fainted Dondozo vanish
  and correctly read it as a bug. PokéRogue's own rule ships instead: fainted members stay in the
  party at 0 HP, revivable between waves; the wipe is everyone down at once
  (`RunState.isWiped`). §2.10's disconnect penalty still kills — rage-quitting still costs the
  on-field Pokémon — and is now the *only* way a run Pokémon dies before the run does.
- **The X0 full heal is in** (PokéRogue's rule): clearing every tenth wave fully heals the party.
  Keyed on the wave number, not the kind, as theirs is. This retires the "no healing between
  waves ever" reading of §2.13 — attrition now runs inside ten-wave blocks, not across the run.
- **Power spots are OUT of arena palettes (stopgap).** The wave-10 boss Gigantamaxed through the
  palette's power spot — the AI side needs no band, so the spot armed the boss, not the player.
  PokéRogue bosses never Dynamax (shields are their boss mechanic). §2.5's in-run gimmick design,
  when it happens, must express "player may, boss may not"; the block alone cannot.
- **Wave starts are announced** (action bar + chat) and reward TMs are learnset-gated with real
  SimpleTMs icons — quality-of-life rulings from the same session, detail in the code.

### 2.43 Verified: bosses are WILD, gyms are the X20-every-30 — composition realignment pending

Read from their source 2026-07-31 evening (`src/game-mode.ts`, `src/battle.ts`), prompted by the
playtest reference run:

- `isBoss(waveIndex)` is `waveIndex % 10 === 0` — deterministic, every X0 wave.
- `isWaveTrainer` classic is `waveIndex % 30 === 20` — the gym-leader waves: 20/50/80/110/140/170.
- Therefore **the X0 waves that are not gym waves (10, 30, 40, 60, 70, 90, …) are wild BOSS
  Pokémon** — ×1.2 level, shield segments, catchable. Shields are a *wild* mechanic in PokéRogue;
  their trainers never have them. Generic trainers appear on ordinary waves by `arena.trainerChance`
  roll, not on an interval.

Our schedule (trainer every 5, boss *trainer* every 10, shields on generated trainer teams) was
§2.14's recorded simplification, now measurably off-model. The faithful restructure, **scoped but
not yet built**: (a) `WaveComposition` gains the gym schedule (%30==20) and marks non-gym X0 waves
as wild-boss; (b) the wild path attaches §2.32's shield item to the wild boss and keeps it
catchable; (c) generic trainer waves become a seeded per-wave chance rather than every-5th (roster
band validation must switch from gap-coverage-of-scheduled-waves to coverage-of-all-waves); (d)
boss *trainers* keep shields only where authored (E4/champion may — that is our call, theirs do
not). Rival meetings (8/25/55/95/145/195) already match — the smoke roster simply has no rival
block yet, so no meeting has been seen in testing.

**Also ruled, same session:** their EXP items (EXP Share / EXP Charm / Super EXP Charm) are
**team-wide permanent run buffs**, not held items — Share cuts participants' EXP to the whole
party, Charm scales total EXP. The first-cut held-item stand-ins are removed from the tables;
the faithful shape is a **run-passive mechanism** (run-scoped stacks read by an EXP-event
multiplier), which is also the natural home for Healing Charm if it ever ships.

---

### 2.44 The hosted pivot: self-host the real game, bridge the rewards, 2026-08-01

Ruled after the 2026-07-31 playtest marathon: instead of continuing to mirror PokéRogue inside
Cobblemon, **self-host the actual game** (pagefaultgames/pokerogue frontend +
pagefaultgames/rogueserver backend) and connect it to the server through a reward bridge.
`cobblemon-roguelite` is parked, not deleted — its isolation/arena/battle plumbing is unique, the
dev server still runs it, and every decision above stands as the record of what mirroring costs.

**Hosting (decided 2026-07-31, built in `ops/pokerogue/`):** native on the cobblemon VM, not
Docker (none installed; everything there is already systemd-native) and not k8s (the stack is
stateful and belongs next to the MC servers it rewards). nginx serves the built frontend on
`:8000` and reverse-proxies the API same-origin at `:8000/api/`; `rogueserver` (a single static
Go binary, cross-compiled from the Mac) runs as the `pokerogue` system user on localhost `:8001`;
MariaDB is localhost-only. The binary is built `-tags=devsetup` so the schema self-creates.
Same-origin is load-bearing, found the hard way: the beta frontend stamps every request with a
custom `PKR-Client-Version` header, which fails rogueserver's CORS preflight cross-origin
("Unknown login error!"). It also makes 8000 the single public port. The frontend bakes its API
URL (`<origin>/api`) at build time, so going public is exactly: rebuild with the public URL,
re-run setup, forward 8000 on the router.

**Accounts:** real rogueserver accounts (username/password, `VITE_BYPASS_LOGIN=0`), because
per-player saves and the reward bridge both need identity. OAuth stays unconfigured.

**Reward bridge (designed, not yet built):** a poller on `pokeroguedb.accountStats` — rogueserver
keeps exactly the monotonic per-account counters a milestone system wants (`sessionsWon`,
`classicSessionsPlayed`, `highestEndlessWave`, `highestLevel`, `pokemonCaught`, `pokemonHatched`,
`eggsPulled`, vouchers) — diffed against a bridge-side state file, mapped through a
user-authored milestone table to in-game grants via RCON/bridge, same service pattern as
`ops/poke-engine-bridge`. Two rules carried in from the ruling: **milestones only, never raw
web-side quantities** (the web save is client-trusting, so a cheated save can at worst skip to a
milestone cap, not print money), and the milestone table is **content — the human authors it**.

**Immersion plan (ruled 2026-08-01).** The game is a browser tab; immersion comes from making the
*run* a server event. Approved: (1) run announcements in MC chat from the bridge's DB poll,
loudness gated by milestone rarity — `sessionSaveData` is zstd-JSON, so live wave/party/death are
readable; (2) a physical shrine ("Dream Machine") at spawn — link dispensed and milestone rewards
*claimed* there via NPC, never auto-mailed; (3) a "Dream Journal" written book item the bridge
keeps rewritten (clickable link, linked account, stats, unclaimed milestones); (5) frontend
reskin — server name/splashes, and gym-leader/rival display names swapped to our gym leaders via
a locales patch in the build script — **content SKIPPED by ruling 2026-08-01**: the mechanism
(ops/pokerogue/reskin/) stays built with placeholders, but no real renames/splashes are planned;
don't re-propose; (6) a leaderboard wall at the shrine the bridge rewrites. Second round, also approved: (7) "dreaming" presence — tab-list
suffix (`💤 wave 42`) while a run is active, body-at-the-shrine framing; (8) the wake-up moment —
when a run ends and the player is online, a personal fade/title ("You wake up… you remember
reaching wave 143") with a private one-line summary, so every run ends inside Minecraft; (9) live
dream ghosts — a translucent display entity of the runner's current lead Pokémon at the shrine
with a nameplate, driven from the session JSON. Rejected or parked, don't re-propose: the
daily-seed race (their `dailyRuns` shared seed), permanent-firsts plaques, milestone chat titles,
Discord webhook echo, starter-nudge query param, and the communal champion-clear buff.
Write-direction ideas (gifting eggs/starters into PokéRogue saves) stay deferred: we own the DB
but not their save format; a bad write corrupts a run.

### 2.45 Hosted payout design, scoped 2026-08-01

Scoped live with the human; numbers marked (tune) are theirs to finalize.

- **Pay-to-dream, hard-gated.** A run cannot happen unpaid: `/pokerogue enter` (later the shrine
  NPC) charges a flat 5,000₽ (§2.28's fee, same abandon-prices-restart semantics) and writes one
  armed-run credit to `bridgeRunArming` — the one table the bridge's otherwise-SELECT-only DB user
  may write. A second rogueserver patch consumes a credit when a save starts a NEW run (no
  bridgeRunState row / seed change) and rejects the save when there is none; a frontend patch turns
  that rejection into a friendly "pay at the shrine" message. Known seams, accepted: the block
  lands at the first save (~1 wave of ghost play), and in-flight runs at ship time are
  grandfathered. Refined at build time: the gate and the payout are both **classic-only** —
  daily/challenge/endless stay free practice with no payout, rather than charging for modes
  that never pay.
- **Repeatable payout: pokemon-crate keys by deepest wave.** 50→1, 100→2, 150→3, 200→4 (tune),
  granted via the existing `/gacha grant`, claimed through the shrine flow. Deliberately never
  egg-crate keys: PokéRogue meta-progression makes veteran clears routine, so the repeatable
  column must not mint top-tier loot (the §2.20 filter logic — scarcity lives in placement, not
  in hoping wave 200 stays hard).
- **One-time wave milestones** at first-ever 50/100/150/200 (needs the bridge-side maxClassicWave
  virtual stat — accountStats has no classic-depth column): common/rare/ultra key jackpots and a
  server-wide title at 200 (tune). Cheat bound: milestones one-time + every paid run costs 5k +
  optional max-armed-runs/day cap.
- **Pokemon-crate pity (gacha-mod feature, not bridge):** the table is ~80% common/uncommon with
  Ultra Rare at 1.5%; every 10th pokemon-key roll draws only Ultra-Rare-or-jackpot (70/22.5/7.5
  tune), counter resets on any Jackpot-class drop, progress visible to the player.
- **Rejected en route:** paying out raw eggs directly (crates give the shared spectacle + pity a
  home); flat depth→egg-tier bands (veteran inflation); reskin content (§2.44 note).

### 2.46 Server-minted accounts and the tokenized entry link, ruled 2026-08-02

Replaces §2.44's open linking question and closes §2.45's ghost-wave seam. Accounts are
**server-minted**: `/pokerogue enter` for an unlinked player first creates the PokéRogue account
itself — username = MC name, password **generated by the bridge** (never typed: MC logs every
issued command to latest.log, so a password argument would land on disk in plaintext) and stored
bridge-side; `/pokerogue password` whispers it for manual logins. Squatting dies by construction —
there is no public "link someone else's username" verb anymore (legacy UUID-keyed links persist;
staff unlink remains). The paid flow then hands back a **tokenized link**: the bridge asks a new
secret-gated rogueserver endpoint to mint a one-time session token and appends it as a URL
fragment; the frontend reads it, sets the session cookie, strips the fragment — the browser opens
already logged in, so the only advertised path into a run is the paid one. The frontend also
gains the **courtesy pre-check** (an authed credits endpoint consulted at New Game) so an unarmed
player is told at the button, not a wave in; save-time 402 rejection stays the actual
enforcement. Shared secret between bridge and rogueserver is minted by setup-vm.sh.
Amended 2026-08-02 after the auth review: `/account/register` itself is **walled behind the same
secret** (fail-closed when unset) — the bridge is the only account mint, which closes the last
squatting path (pre-registering someone's MC name via the portal). Verified live: portal
register → 403 "accounts are created in-game"; the save-time §2.45 gate was also verified
empirically against a manually-logged-in creditless account (402), and OAuth callbacks confirmed
dead (redirect to home, unauthenticated; client IDs null). Known edge
delegated to the build: rogueserver's username validation vs MC names (underscores) — verified
against source, with a deterministic fallback mapping if needed.

### 2.47 One session, one verb: auto-start and auto-resume, ruled 2026-08-02

`/pokerogue enter` becomes the only session verb. Bridge-side routing before the link is minted:
an **active session** (any sessionSaveData row for the account) → free resume link; **no session
but an unspent armed credit** (paid earlier, abandoned before the first save) → free new-run
link; **neither** → charge 5,000₽, arm, new-run link. The link's fragment carries the intent —
`#pt=<token>&auto=new|resume` — and the frontend consumes it one-shot at the first TitlePhase:
`resume` mimics Continue (latest slot), `new` mimics the Classic option (into starter select,
save-slot prompt auto-resolved to the first empty slot). Quit-to-title afterwards behaves
normally — the directive never re-fires. Single-session is enforced *economically*, not by UI
lockout: a manual second New Game has no credit (enter refuses to arm while a session exists)
and dies at the §2.45 save gate. Exiting the browser just leaves the save; ending the run pays
per §2.45. Amended same day after the first playtest: a **classic** run ending (game-over
confirmed, session cleared) also **logs the browser out** — cookie cleared, token invalidated,
a "return to the world" message, then back to the login screen — so the session's life is
exactly fee-to-game-over and re-entry is always `/pokerogue enter`. Non-classic practice runs
keep the vanilla end-of-run flow (they never went through the fee, and logout would strand
manual-login practice players).

**Open questions:**
- *Account linking.* **RESOLVED by §2.46** (server-minted accounts). `/pokerogue link <username>` storing an MC↔account mapping is the shape;
  what proves ownership is not settled. On a small trusted server, first-come-first-served with
  collisions flagged to staff is probably enough; a challenge-code scheme (bridge sets a code,
  player proves login by e.g. a named new save slot) is the escalation if it isn't.
- *Client UX.* **RESOLVED 2026-08-01: MCEF fails, links win.** The spike (merged 332cbeb) was
  tested against the self-hosted instance on macOS ARM: CEF initialized, the screen stayed white —
  the pre-registered fail criterion (PokéRogue is a WebGL app; MCEF's off-screen rendering can't
  paint it). The client mod and MCEF were removed from the test instance; the module stays in the
  repo as a record. The link UX (`/pokerogue` → clickable URL) lands in the reward-bridge server
  mod, whose command tree needs to exist for `/pokerogue link` anyway.
- *Public exposure.* Router forwards and the public hostname are operator actions, Discord-only,
  never committed.

### 2.48 The command is `/dream`, ruled 2026-08-02

The bridge command tree is registered as **`/dream`** (enter, password, claim, unlink, link),
not `/pokerogue` — the verb should sell the fiction, not name the upstream project. Same day,
the presentation got its palette: the tab-list 💤+wave suffix renders **blue**, and the private
wake-up moment (title, subtitle, chat line) is **blue for an ordinary wake, gold for a victory
wake**. Earlier sections that say `/pokerogue <verb>` mean today's `/dream <verb>`; they are
left as written because this is a decision record.

Amended same day: **bare `/dream` IS the enter verb** — one word, one door. The §2.47 routing
(resume / armed credit / charge-and-arm) hangs off the bare command; `enter` stays as a legacy
alias and the old status line moved to `/dream status`. And because a bare word can now cost
money: the charge path stops at a **confirmation prompt** ("Beginning a NEW dream costs N.
Ready?" + a clickable `[ Begin the dream ]` running `/dream confirm`, 60-second one-shot
window) — the free resume/armed-credit paths never prompt, and nothing is charged until
confirm re-runs the routing and reaches the charge path again.

### 2.49 Dex-locked dreams and the glimpse system, scoped 2026-08-03

**The tether.** The hosted minigame risks out-competing the server it serves. The chosen
mechanical answer: **which species you may START a dream with comes from your server Pokédex.**
PokéRogue's own unlock engines — run catches and the egg-gacha voucher loop — keep firing, but
their unlocks arrive **dormant ("glimpsed")**: recorded, banked, and inert until the species
registers as caught in your Cobblemon dex, at which point the starter activates *with
everything the gacha banked on it* (candies, shiny variant, passives). Vouchers become
reveal-and-escrow currency: the gacha tells you what to hunt; the world is where hunting
happens. This AMENDS §2.44's save-write ban, narrowly: the save is **read-filtered, never
mutated** — the player's true blob is always preserved.

**Mechanism.** (1) The bridge feeds each linked account's server-dex caught-species ids into a
new `bridgeDexWhitelist` table (write grant added alongside bridgeRunArming), on login, on
capture events, and by periodic reconcile. (2) A rogueserver patch filters the SYSTEM save on
the way down: starter/dex entries outside `whitelist ∪ activated` are masked out of the served
copy. (3) **The merge-on-write is the hard part:** the client posts back its (filtered) state,
so the patch must union the incoming save with the preserved hidden entries or gacha progress
is silently destroyed. Getting this merge provably lossless is the risk center of the whole
feature and where the testing budget goes.

**Work breakdown (estimate: 2–3 focused sessions):**
- Bridge dex feeder (Cobblemon dex API research, capture-event hook, reconcile, table): ~½–1 session.
- rogueserver glimpse patch (GET filter + lossless POST merge + 5th-patch stack regen +
  setup-vm grant): ~1 session, dominated by merge testing.
- Integration verify on dev (hatch non-whitelisted → relog → still banked; server catch →
  activation ≤1 poll; no clobber from updateAll): ~½ session.
- Stretch, v2: frontend patch showing glimpsed species greyed in starter select ("seen only in
  dreams"); without it they simply vanish until activated, which is acceptable for v1.

**Open questions (user decides before build):**
- Baseline starter set for a fresh account (verify PokéRogue's default unlocks; pick the
  minimal server-fresh set).
- Grandfathering: existing accounts' current unlocks — snapshot as activated, or hard-reset to
  server dex on enable.
- Does server SEEN count for anything, or CAUGHT only (assumed caught)?
- Regional-form starters activate at base-species level (2xxx/4xxx/6xxx/8xxx ids) — confirm
  acceptable.
- Rollout: config-gated (`dexLockedDreams`, default off) until the answers above land.

## 3. Preliminary plan

The original three-phase plan described a ten-wave vertical slice, and §2.19's 200-wave decision
retired that framing. What follows is where things actually stand.

### Built

Run lifecycle (start ordering, progress, abandon, pause, commands), world-save-data persistence,
arenas (slot grid, chunk tickets, stamping, entry and exit, biome repaint), the datapack
convention with reward, payout, trainer-roster and biome registries, wild-wave generation, the
10-point starter budget, candy and IV-floor progression, wave composition, trainer rosters with
fixed-encounter overrides, wild and trainer battles, catch-into-run-party with swap-or-release,
permadeath, disconnect attribution, offline payout delivery, and the operator depth override.
Host seams for charging, bonus payouts and battle AI, each with a working standalone default;
the trainer-battle provider lives in `cobblemon-bridge` and reaches the module by reflection so
no build-time edge exists between them.

§2.30's generated teams: a roster's `generated` block holds signature species per trainer, and the
team is built at the encounter from `(seed, wave)` — seeded alternative, party size and evolution
stage by band, held items on their own draw stream, no EVs. `ops/gen_pokerogue_roster.py` extracts
the signature table into that block. A trainer with no entry keeps the authored path, which is how
the Elite Four and the champion stay hand-made.

### Not built, and blocking a playable run

All of it content, none of it code:

- A **default trainer roster** — nothing ships at `cobblemon_roguelite:default`, so every wave
  reports no roster. `ops/gen_pokerogue_roster.py --roster` now produces one from PokéRogue's
  signature table (74 leaders), but its band edges are a mechanical split of their ordering and it
  is server-side content by §2.7 — it must not be committed into the mod.
- **Held item choices** for generated teams. The mechanism ships with an empty table, so trainers
  currently carry nothing.
- **RCT trainers for the generated leaders** — a generated team still needs an NPC with a name, a
  skin, a bag and an AI, and the ids the script emits (`rgl_brock`, …) name nothing yet.
- An **arena template** `.nbt` — a run fails at `resume` with the expected path named. This also
  decides whether `power_spot` goes inside it, which §2.5's gimmick confinement depends on.
- The **per-species cost table** (§2.13), the **baseline starter pool** (§2.15), **reward and
  payout table contents**, the **entry fee**, and **candy prices**.
- **Biome definitions** — template, Minecraft biome and name per biome (§2.24).

Every one of these fails loudly by design rather than silently doing nothing.

### Not built, not blocking

A run-party GUI (see §5 — the party HUD question is unresolved), biome-gated encounter pools
(the seam exists and is documented; using it is a content call), and player-chosen biome
transitions (§2.24 left the seam, implemented seeded).

### Unverified

Everything that needs a booted server: the capture path end to end, the biome repaint, trainer
waves through the bridge provider, and the party HUD. The dev VM has confirmed the arena
dimension, `power_spot` placement, and NPC-side level scaling.

Generated teams are decided in unit tests and *built* only on a server, so what the dev VM still
owes is that Cobblemon accepts the properties strings — `species=cobblemon:corsola galarian
level=53 held_item=cobblemon:leftovers` resolving to the regional form, holding the item, and
`create()` deriving a level-appropriate moveset.

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

Decisions §2 has settled are **not** repeated here. This is only what is genuinely undecided.

### Design calls

- **§2.11's bag guard blocks run-issued bag items too.** It is a blanket rejection, so PokéRogue's
  X Attack line and Dire Hit cannot ship until it becomes "reject player-owned, allow run-issued".
  A decision reversal, not a detail. Note those effects are player-wide and last five battles in
  PokéRogue, so the faithful version is run state applied at battle start, not a consumable.
- **Should "boss" and "leader" be distinct encounter kinds?** Every tenth wave is currently one
  kind. Gym leaders, rivals, the E4 and the champion may want different treatment.
- **Enemy buff tokens (§2.34): an ability on every enemy, or folded into §2.6's level scaling?**
  An ability strips the Pokémon's real one — the exact objection §2.32 raised against ability-slot
  shields. Folding the first three into scaling is cheaper and costs nothing.
- **Trainer filler species.** Type-matched stride sampling gives Brock a Cramorant. Acceptable,
  authored per leader, or dropped so late bands run four?
- **Does a minimal arena palette ship by default?** `ArenaBuilds.default` names an `.nbt` nobody
  ships, so an unconfigured install fails loudly. Shipping one makes the mode playable out of the
  box, against §2.29's "not somebody's taste on every server".
- **Bless §2.34's first modifier set?** Eight items sharing one tiering harness.
- **Is the milestone checkpoint interval still a thing?** §2.10 kept milestones as persistence
  granularity, but checkpointing is per-wave and §2.23 changed how sessions work. It may be dead.
- **How the flat last third escalates** (§2.19, §2.21). Levels pin at 100 from ~wave 138 and EXP
  rewards go dead over the same band, so the reward table has to change character there. Boss
  shields (§2.32) and modifier tiers (§2.33) are levers; nothing has designed the shape.

### Content and numbers

- Per-species **cost table** (§2.13) — extractable from PokéRogue, server-side only.
- **Baseline starter pool** — decided as the classic starters (§2.28); the list itself is unwritten.
- **Candy prices** — decided as PokéRogue's per-tier table (§2.28); the code still ships a flat 40.
- **Reward table** contents and rarity curve.
- **Egg payout bands** — which egg tier at which depth (§2.28).
- **Shop stock, prices and credit earning rates** (§2.35).
- **Biome definitions and arena palettes** (§2.24, §2.29) — roughly thirty rows each.
- **Trainer band edges** — the current 21/21/20 split is mechanical, and §2.19's twenty boss waves
  against four late bosses will repeat visibly.
- **Held-item tiers for trainers** (§2.30) — ships empty, so nothing generated carries an item.
- **Boss shield counts** — which party members get them and how many by wave (§2.32).
- **The E4 and champion schedule**, and their authored teams (§2.7, §2.30).
- **Escalation ladder** — which wave bands unlock Mega, Tera, Dynamax, legendaries.

### Deferred until someone has played it

- **Difficulty calibration.** Bosses have shields again (§2.32) but players do not yet have
  stacking (§2.33), so the two halves are not yet balanced against each other.
- **A client mod for a segmented HP bar** (§2.32) — decide once the marker and messages have been
  seen in play.
- **Whether one modifier line per Pokémon is too tight** (§2.33), which is what the unspent
  ability axis is for.
- **Replayability beyond §2.15's dex unlocks** — reward paths, branching routes, run modifiers.

### Blocking publication only

- Whether to publish at all, and under what licence and name (§2.9).
- **RCT's licence**, if it is ever to be more than a soft dependency (§2.6).

### Parked — deliberately out of scope

- **Battle speed.** Findings recorded so they are not rediscovered: no config lever exists;
  Cobblemon exposes only `walkingInBattleAnimations` and `animateBattleTiles`, both already false
  here. The delays are hardcoded across ~30 sites funnelling through
  `PokemonBattle.dispatchWaiting`, which is server-side and mixin-able with no client mod. About
  twenty further waits are `UntilDispatch`, blocking on a condition rather than a timer, and set a
  floor a multiplier cannot get under. **Separate PR, probably server-wide rather than run-only.**
