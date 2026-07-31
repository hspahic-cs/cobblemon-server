# Roguelite trainer battles: PokéRogue's rules, our provider

How the trainer/boss/rival waves of a roguelite run are fought: what PokéRogue does, what our
codebase already does, and what is left. Written 2026-07-31 against branch `feat/pokerogue-mode`.

**Status up front, because it changes how to read this document:** the provider this document was
commissioned to plan **already exists and is committed** —
`custom-mods/cobblemon-bridge/src/main/kotlin/com/cobblemonbridge/roguelite/` (three files:
`RogueliteSeam.kt`, `RctTrainerParts.kt`, `RogueliteTrainerBattles.kt`, committed in
`026e9f5 feat(roguelite): trainer-battle provider in bridge`), wired from
`CobblemonBridge.kt:141` (`RogueliteTrainerBattles.install()`). So this is a **design record and
gap analysis**, not a greenfield plan: §1 records PokéRogue's rules, §2 records how our
implementation works and why, §3 verifies the implementation against both PokéRogue's rules and
the roguelite module's seam contract, and §4 lists what is genuinely still open.

Authority note: `docs/pokerogue-mode-plan.md` is the decision record (§ references below are its
sections). `docs/roguelite-economy-reference.md` carries PokéRogue's money formulas verbatim and
is not repeated here beyond what trainer battles specifically need.

---

## 1. PokéRogue's trainer battles (their source, `pagefaultgames/pokerogue`, `main`)

Read out of their real source on 2026-07-31 (via the GitHub API) so nobody has to go find it
again. File paths are theirs; line numbers approximate `main` at that date.

### 1.1 What makes a battle a trainer battle

- **Scheduling** — `src/game-mode.ts` `isWaveTrainer()` (~L206): a wave is a *forced* trainer wave
  when `waveIndex % 30 === (offsetGym ? 0 : 20)` — i.e. every X20 wave is the gym-leader wave.
  Other waves roll a random trainer via `arena.trainerChance`, with X1 waves excluded and generic
  trainers suppressed within 2 waves of a fixed trainer battle
  (`src/battle-scene.ts` `handleNonFixedBattle()`). Our §2.14 simplifies this to fixed intervals
  (trainer every Nth, boss every wider Nth) — a recorded, deliberate simplification.
- **Catching forbidden** — enforced at the *command*, not the ball: `src/phases/command-phase.ts`
  (~L389) `} else if (battleType === BattleType.TRAINER) { this.queueShowText("battle:noPokeballTrainer");`
  and returns false, so the ball is never thrown.
- **Fleeing forbidden** — same file, `handleRunCommand()` (L578–591): `battleType === BattleType.TRAINER`
  (or a mystery-encounter trainer battle) → `"battle:noEscapeTrainer"`, return false.

### 1.2 Money: trainers are the payout event

**Only trainer victories pay money directly.** `MoneyRewardPhase` is instantiated solely from
`src/phases/trainer-victory-phase.ts` (L22):

```ts
globalScene.phaseManager.unshiftNew("MoneyRewardPhase", globalScene.currentBattle.trainer?.config.moneyMultiplier!);
```

The base amount is `getWaveMoneyAmount()` (`src/battle-scene.ts` L2399–2406 — ported verbatim into
our `WaveMoneyCurve`; full text in `docs/roguelite-economy-reference.md`), times the **per-trainer
`moneyMultiplier`** from `src/data/trainers/trainer-config.ts` (default `1`, L149):

| Trainer kind | moneyMultiplier |
| --- | --- |
| Generic classes | ~0.2 (Preschooler) … 3.25, e.g. Ace Trainer 2.25, Maid 1.6, Janitor 1.1 |
| Evil-team admin | 1.5 |
| Stat trainer | 2 |
| Evil-team leader | 2.5 |
| **Gym leader** | **2.5** (init at L712) |
| **Elite Four** | **3.25** (L768) |
| **Champion** | **10** (L800) |
| Rival | 1 → 1.25 → 1.5 → 1.75 → 2.5 → 3 across RIVAL..RIVAL_6 |

(`money-reward-phase.ts` then applies Amulet Coin and Happy Hour doubling — item systems we do not
have.) Compare ours (§2.4): trainer ×1.0, rival ×1.5, boss ×2.0 over the same curve — same shape,
flatter spread, and wild pays 0 where PokéRogue's wild victories also pay no *money* (their wild
waves pay in modifier picks, ours in the free reward pick — the divergence is smaller than the
economy reference's shorthand suggests, but real: they still item-reward every wave from a
`TRAINER` modifier pool after trainer fights, `battle-scene.ts` L2738).

### 1.3 Party composition per wave

**Levels** — `src/field/trainer.ts` `getPartyLevels(waveIndex)` (L262–306), core verbatim:

```ts
const difficultyWaveIndex = globalScene.gameMode.getWaveForDifficulty(waveIndex);
const baseLevel = 1 + difficultyWaveIndex / 2 + Math.pow(difficultyWaveIndex / 25, 2);
switch (strength) {
  case PartyMemberStrength.WEAKER:   multiplier = 0.95; break;
  case PartyMemberStrength.WEAK:     multiplier = 1.0;  break;
  case PartyMemberStrength.AVERAGE:  multiplier = 1.1;  break;
  case PartyMemberStrength.STRONG:   multiplier = 1.2;  break;
  case PartyMemberStrength.STRONGER: multiplier = 1.25; break;
}
let levelOffset = 0;
if (strength < PartyMemberStrength.STRONG) {
  multiplier = Math.min(multiplier + 0.025 * Math.floor(difficultyWaveIndex / 25), 1.2);
  levelOffset = -Math.floor((difficultyWaveIndex / 50) * (PartyMemberStrength.STRONG - strength));
}
const level = Math.ceil(baseLevel * multiplier) + levelOffset;
```

Two things worth keeping: **trainer and wild levels share one base curve** (`src/battle.ts`
`getLevelForWave()` uses the same `baseLevel`, with a `bossMultiplier = 1.2` for boss waves — the
same ×1.2 our wave curve already applies, per the `RunTrainerBattleRequest` KDoc), and party
members are **not all one level** — strength tiers spread them, with weak members slowly catching
up in multiplier but drifting down in offset. Ours flattens the party to `plan.level`; the
per-member spread is the roster generator's business if we ever want it (see Q5).

**Party size / templates** — `src/data/trainers/trainer-party-template.ts`:
`TrainerPartyTemplate(size, strength, sameSpecies?, balanced?)`, named `ONE_AVG` …
`SIX_WEAK_BALANCED`. Generic classes step up a template ladder every 30 waves starting at 20:
`templateIndex = Math.ceil((wave - 20) / 30)`, clamped to the list (e.g. Ace Trainer:
3-weak-balanced → 4 → 5 → 6). Gym leaders use `getGymLeaderPartyTemplate()`: ≤20 → 2 mons,
≤30 → 3, ≤60 → 4, ≤90 → 5, 110+ → 6 (3 avg, 2 strong, 1 stronger). Elite Four = 6 (1 avg,
3 strong, 1 stronger + 1 random avg); Champion = 6 (4 strong + 2 stronger balanced).

**Species (brief)** — with `speciesPools`: `randSeedInt(512)` → COMMON ≥156, UNCOMMON ≥32,
RARE ≥6, SUPER_RARE ≥1, else ULTRA_RARE (≈69.5 / 24.2 / 5.1 / ~1 / ~0.2 %), tier downgraded when
empty, duplicates rerolled, all draws wave-seeded — the same determinism bargain our
`TrainerTeamGenerator` makes for the same reason (no reroll-by-relog).

### 1.4 Doubles, bosses, rival (noted, not deep-dived)

- **Doubles**: `doubleOnly` configs always; otherwise a `1/8` roll (`1/32` on X0 waves),
  `getDoubleBattleChance()` `battle-scene.ts` L1261; evil grunts 1/3. We force
  `GEN_9_SINGLES` for every wave — recorded divergence (Q5).
- **Boss trainers**: `.setBoss()` + static (fixed-seed) party + egg-voucher reward + boss BGM;
  gym leaders/E4 get Tera slots. Our bosses are ×1.2 level + boss shields (§2.32) instead.
- **Rival**: fixed-battle configs RIVAL..RIVAL_6, party grows 2 → 7 mons with a persistent
  starter-line, boss from RIVAL_4, milestone item rewards (EXP Share etc.). Ours: §2.36 rival
  ladder (`rgl_rival_1..6`), team continuity via `RivalLadder`, paid ×1.5 — same skeleton, no
  item-reward extras.

### 1.5 Victory flow

`src/phases/trainer-victory-phase.ts`: victory BGM → `MoneyRewardPhase(config.moneyMultiplier)` →
one `ModifierRewardPhase` per authored reward func → (boss trainers) an egg-voucher phase →
`battle:trainerDefeated` text → one seeded-random line from `trainer.getVictoryMessages()`. The
post-wave item select then draws from `ModifierPoolType.TRAINER` instead of `WILD`. Our
equivalents: credits in `RunController.waveCleared` (§2.4), the between-wave shop/reward GUI, and
RCT's own trainer chat lines stand in for victory dialogue (Q5).

---

## 2. How our implementation works

### 2.1 The seam, restated in one paragraph

`cobblemon-roguelite` may not compile against RCT (§1.2/§2.6: licence unverified), so it declares
`integration/RunTrainerBattleProvider.kt` and defaults it to a provider that **refuses** every
trainer/boss/rival wave (`RunTrainerBattles.UNIMPLEMENTED`,
`custom-mods/cobblemon-roguelite/.../integration/RunTrainerBattleProvider.kt:127`). Refusal is
fail-closed by design: the only no-op alternative is "count the wave as won", and a run that
free-wins its 40 trainer waves is a run walked to wave 200 and paid for it. `cobblemon-bridge` —
which is allowed to name RCT — registers the real provider at mod setup.

### 2.2 The bridge side, file by file

**`RogueliteSeam.kt`** — the only file in bridge that knows roguelite's class names. Resolves the
whole reflective chain **eagerly** at `install()` (RogueliteSeam.kt:142) so a renamed member is an
ERROR in the boot log rather than a mystery at wave 5, and registers a `Proxy` implementing
roguelite's `fun interface`. Why reflection and not a compile dependency: each `custom-mods/<mod>/`
is an independent Gradle build cached against its **own** `src/` hash, so a `compileOnly(files(...))`
on roguelite's jar would be silently stale in both directions (RogueliteSeam.kt:52–77 spells this
out). Degrades to roguelite's own refusing default when anything is missing.

**`RctTrainerParts.kt`** — RCT taken apart, reflectively (RCT is a soft dependency, same as
everywhere else in bridge). The critical decision (RctTrainerParts.kt:18–41): we do **not** call
`TrainerMob.startBattleWith` / `RCTMod.makeBattle`, because RCT builds the player's side from
`TrainerPlayer.getTeam()` — the player's **real** Cobblemon party — and a run's party deliberately
is not there (§1.1). RCT's `canBattleAgainst` gate would also refuse repeats and apply overworld
level caps. What we take instead, all via public API:

- `TrainerRegistry.getById(id, TrainerNPC.class)` → the authored **team** and authored **AI**.
- `BattleManager$TrainerEntityBattleActor(name, entity, uuid, team, bag, ai)` → the opponent-side
  actor, so the battle behaves like an RCT trainer battle in every way except who built it. The
  `TrainerBag` is **cloned** (stateful across a battle; sharing the registry's instance would let
  one wave spend another's items).
- `TrainerMob` entity, spawned **synchronously** via `EntityType.create` + `addFreshEntity`
  (RctTrainerParts.kt:215) — deliberately *not* the tower's `rctmod trainer summon_persistent`,
  which materialises over following ticks, needs a box search to find the entity again, and needs
  `ArenaConfig.settleTicks = 40` (the tower's observed figure, ArenaConfig.kt:49–53). A wave
  cannot afford asynchrony: the provider must answer "did this wave start" before returning, or a
  failed summon leaves the run holding a §2.10 battle marker for a battle that never happens and
  the player's next logout is billed as a rage-quit. Two explicit flags on the spawned mob:
  `setPersistent(true, /*suppress spawner*/ true)` (persistence without RCT's spawner adopting it
  and dragging its own chunk ticket around), and `setNoAi(true)` — a live `TrainerMob` walks up to
  players and starts battles on sight, which in a one-player arena is a second RCT-driven battle
  against the run's own wave.

**`RogueliteTrainerBattles.kt`** — the provider itself. `begin()` (line 108) in order:

1. **Resolve the trainer id** against RCT's registry (`resolveTrainerId`), trying both the bare
   and namespaced spellings (RCT keys by bare string, roguelite's roster carries a
   `ResourceLocation`). Unknown id → loud ERROR naming `data/rctmod/trainers/`, return false.
2. **Re-take the arena chunk ticket** via `RogueliteSeam.holdArena` → roguelite's
   `RunArenas.prepare` (idempotent; a summon into a cold arena fails silently — the exact contract
   `RunTrainerBattleProvider.begin`'s KDoc imposes), and refuse if the player is not standing in
   their arena's dimension (never summon an RCT trainer into the overworld).
3. **Refuse if already in a battle** (`BattleRegistry.getBattleByParticipatingPlayer`).
4. **Build the player's side** from the *run* party via `RunBattleParty.teamFor` — uncloned and
   unhealed, exactly what the wild path fights with, so damage sticks and permadeath aims at the
   right Pokémon.
5. **Build the opponent's team** (`teamFor`, line 245): if roguelite sent generated
   `PokemonProperties` strings (§2.30 roster generation), `parse().create()` each — a member that
   fails to build is **fatal to the wave**, not skipped (a trainer arriving one Pokémon short looks
   like a balance bug forever) — plus an `initializeMoveset()` guard for the empty-moveset path.
   If it sent none, that means "**fight the authored RCT team as written**" (the Elite Four and
   champion are hand-made by design; refusing on empty would delete exactly the fights somebody
   tuned). Either way every member is `BattlePokemon.safeCopyOf(...)`: the opponent fights a
   battle **clone**, so level mutation never reaches the authored trainer on disk, and — unlike
   the player-side downlevel in `battle/GymBattleAdjustHook.kt` — no crash-safe NBT restore
   machinery is needed, because there is nothing to restore.
6. **Spawn the NPC** 6.0 blocks in front of the player (`OPPONENT_DISTANCE`, same figure as
   `RunWildBattle`), facing back at them.
7. **Stash the wave level, then `BattleRegistry.startBattle`** with
   `BattleFormat.GEN_9_SINGLES + BAG_CLAUSE` (copied, never mutated — the shared instance serves
   ranked too) and RCT's actor on side 2. The stash (player UUID → level, 10 s TTL) is consumed by
   a `BATTLE_STARTED_PRE` subscriber that forces every **non-player** actor's
   `effectedPokemon.level` to the wave's level. The timing is structural: `startBattle` posts Pre
   and only then calls `startShowdown`, which packs teams by reading `effectedPokemon` fresh —
   verified on dev 2026-07-28 (plan §2.6 revision: the write lands, a recheck three seconds in
   still read the forced level, stats rescaled). On the generated path the level is already in the
   properties string so the forcing is a no-op — and the *moveset* is the one Cobblemon derives
   for that level, which is the whole reason §2.30 generates instead of stretching authored teams.
8. On any startBattle refusal: unstash, `entity.discard()` (an NPC left standing outlives the
   wave, survives into the next stamp, and is an RCT trainer a player can walk into), return
   false. On success: remember `battleId → entity` and return true. **Nothing else is reported** —
   see 2.3.

### 2.3 Battle end: adoption, not callbacks

The provider deliberately reports nothing after `true`. Roguelite's `battle/RunBattles.kt`
**adopts** any battle that starts while its player's run carries a battle marker
(`BATTLE_STARTED_POST` at `Priority.LOWEST`, `adopt()` at RunBattles.kt:188): the marker is only
set between the wave transition and the wave resolving, so the window is the wave itself. From
adoption onward, faints (`RunController.pokemonFainted` → permadeath §2.13), field tracking
(§2.10 disconnect penalty targeting), and the result (`BATTLE_VICTORY` →
`RunController.waveCleared` / `waveLost`, RunBattles.kt:232–247) are entirely roguelite's. A
provider that also reported them would double every faint — the seam KDoc says so in as many
words. Roguelite's per-tick `reconcile()` also catches battles that end with *no* event
(disconnect, `/cobblemon battle close`, Showdown error).

The one thing adoption cannot do is clean up the NPC: an adopted `LiveBattle` holds
`opponent = null` because "discarding somebody else's entity is not ours to do"
(RunBattles.kt:94–100). So the bridge runs its own 20-tick sweep
(`RogueliteTrainerBattles.sweep()`, line 344): any remembered battle that
`BattleRegistry` no longer has, or has `ended`, gets its NPC discarded. Polled rather than hooked
on `BATTLE_VICTORY` because victory is not the only way a wave ends. Belt-and-braces behind that:
the arena **stamp sweep** discards every non-player entity in the box
(`ArenaStamper.sweep()`, ArenaStamper.kt:197–205 — "opponents, dropped items, projectiles"), so
even a leaked NPC dies at the next band re-stamp; and run end/teardown re-stamps too. The
per-battle sweep is still necessary — the next *wave* in the same band does not re-stamp, and a
live leftover `TrainerMob` (even NoAi) is right-clickable.

### 2.4 Money, catching, fleeing — where each rule lives

- **Money:** `RunController.waveCleared` (RunController.kt:560) pays
  `run.credits += ShopSettings.credits.creditsFor(cleared.plan.wave, cleared.plan.kind)`
  (RunController.kt:596) — off the *re-composed* plan, so a promoted Elite-Four wave pays boss
  rates, and off the wave *number*, so §2.10's send-back is not farmable. `CreditRules.creditsFor`
  (shop/CreditRules.kt:88): wild ×0.0, trainer ×1.0, rival ×1.5, boss ×2.0 over the shared
  `WaveMoneyCurve` (PokéRogue's `getWaveMoneyAmount`, ported — see
  `docs/roguelite-economy-reference.md`). Wild-pays-nothing is a **deliberate divergence** from
  PokéRogue, recorded in both CreditRules' KDoc and the economy reference: it makes trainer waves
  the income so meeting one matters beyond difficulty. Nothing for the provider to do — the credit
  fires because adoption routes the victory to `waveCleared`.
- **Catching:** forbidden on trainer waves twice over, both structural. (a) The opponent's Pokémon
  exist only as `BattlePokemon` clones inside the battle — no `PokemonEntity` ever stands in the
  world for a thrown ball to key off (Cobblemon's capture flow requires an entity carrying the
  battle id; the wild path has to set `entity.battleId` *explicitly* to make capture work,
  RunWildBattle.kt:169). (b) `RunBattles.isCatchableWave` is false for adopted battles
  (RunBattles.kt:122 — "false for an adopted battle, which is the correct default twice over"), so
  `RunCapture` would refuse to route the catch into the run even if a ball somehow connected.
  Matches plan §2.14's table: "trainer-owned Pokémon never are [catchable]".
- **Fleeing:** the *player* cannot flee a trainer wave for Cobblemon's own reason — the run/flee
  option exists against a `PokemonBattleActor` (wild), not against a `TrainerEntityBattleActor`.
  The *opponent* cannot flee because it is a trainer, not a wild entity with a flee distance (the
  wild path had to learn `-1f` is the no-flee sentinel the hard way, RunWildBattle.kt:77–95).
  `RunBattles` still subscribes `BATTLE_FLED` defensively and treats it as "wave not fought,
  re-fight" (RunBattles.kt:162–169). Matches PokéRogue: no running from trainer battles.
- **Items:** `BAG_CLAUSE` in the format plus `RunBagGuard` (§2.11), same as wild waves. The
  *trainer's* bag is RCT's authored one, cloned per battle — a divergence-in-detail from PokéRogue
  (their trainers don't use items mid-battle; RCT's may, if the authored trainer carries a bag).
- **AI:** the opponent runs RCT's **authored per-trainer AI** (`npc.getBattleAI()`, wired through
  `TrainerEntityBattleActor`), i.e. whatever the trainer JSON declares (registry: `rb`/`cbl`/
  `rct`/`sd5`; the gyms run the poke-engine bridge via `pe`). This is *different* from the wild
  path, which goes through roguelite's `RunBattleAi` seam (default `StrongBattleAI(skill=5)`,
  integration/RunBattleAiProvider.kt:107–160). Deliberate: §2.6 wanted "an authored team, an
  authored AI"; the difficulty dial for trainer waves is the trainer JSON, not the seam. See open
  question Q3.

### 2.5 Skins

RCT renders a trainer's skin by **trainer id**: the texture must be named `<trainerId>.png` inside
the `cobblemon-npc` client jar, and the JSON's `textureResource` field is renderer-ignored (hard-won;
see memory `reference_rctmod_trainer_skin`). Because the provider spawns a real `TrainerMob` with
`setTrainerId(rctId)`, skins need **zero code**: a roster that names an existing RCT trainer
(`rctmod:leader_brock_0038`, as the smoke roster does — `ops/gen_roguelite_smoketest.py:52`) gets
that trainer's shipped skin; a roster that names our PokéRogue-cast ids (`rgl_brock`, `rgl_rival_1`…)
gets whatever `ops/gen_trainer_texture_pack.py` packed under that name. `ROGUELITE_SKINS`
(gen_trainer_texture_pack.py:88) maps each `rgl_*` id to the RCT art to copy — Kanto/Johto/Sinnoh
leaders, E4 + champion — with the stated rule "NO LOOKALIKES: absent means RCT's default skin,
which reads as 'not done yet' rather than a wrong casting choice". What the `rgl_*` ids still lack
is the **RCT trainer definition itself** (`data/rctmod/trainers/rgl_*.json`) — the smoke generator
says so: "NOT the rgl_* ids: those name trainers nothing defines yet" — which is content work
(§2.7 keeps it in a server-side datapack), not provider work.

---

## 3. Verification checklist — the seam contract, point by point

| Contract clause (from `RunTrainerBattleProvider` KDoc) | Met by | Verified how |
| --- | --- | --- |
| Use the trainer the run handed us, never draw our own | `begin()` resolves `wave.trainerId` only | code read |
| Run party on the player's side, uncloned | `RogueliteSeam.runTeam` → `RunBattleParty.teamFor` | code read |
| Empty `teamProperties` = authored team, not refusal | `teamFor()` authored branch | code read + KDoc "the empty case is a fight, not a failure" |
| Scale opponent to `plan.level`, never touch the authored trainer | `BATTLE_STARTED_PRE` write onto `safeCopyOf` clones | **dev-verified 2026-07-28** (plan §2.6) |
| Re-take the chunk ticket before summoning | `holdArena` → `RunArenas.prepare` per call | code read |
| Synchronous summon or true-then-adopt | fully synchronous (`addFreshEntity`) | code read |
| Report nothing after `true` (adoption owns the end) | provider ends at `return true`; only the NPC sweep remains | code read |
| Fail closed on every refusal, before a battle exists | all refusal paths return false pre-`startBattle`; post-refusal discards the entity | code read |
| NPC cleanup (roguelite refuses to discard others' entities) | 20-tick `sweep()` + arena stamp sweep as backstop | code read; **not yet runtime-tested** (Q1) |
| Credits fire on trainer/boss/rival victory | via adoption → `waveCleared` → `creditsFor` | code read (unit tests cover `CreditRules`) |

---

## 4. Open questions for the human

- **Q1 — Runtime smoke test of the full loop is still owed.** The level-mutation half was probed
  on dev (2026-07-28), but summon → fight → victory → credits → NPC sweep → next wave has not been
  run end-to-end. The smoke datapack (`ops/gen_roguelite_smoketest.py`, boss bands naming
  `rctmod:leader_brock_0038`/`leader_misty_0020`) exists for exactly this. Remember the deploy
  gotcha: never name the hand-installed datapack `server-*`.
- **Q2 — RCT trainer definitions for the `rgl_*` cast.** The provider is done; the *content* that
  makes rosters name PokéRogue's cast is not: `data/rctmod/trainers/rgl_*.json` (teams are
  irrelevant for generated-team trainers — the roster overrides them — but the definition must
  exist for `isValidId`, and its `ai` field chooses the opponent brain). Generator work
  (`gen_pokerogue_roster.py` / a sibling), user-supplied content per the authorship preference.
- **Q3 — Should trainer waves route through the `RunBattleAi` seam instead of RCT's authored AI?**
  Today: wild waves = seam (default StrongBattleAI-5, bridge could register poke-engine), trainer
  waves = whatever the trainer JSON says. If `rgl_*` definitions all declare `pe`, boss difficulty
  couples to the gym-AI bridge being up; `TrainerEntityBattleActor` takes the AI as a constructor
  argument, so swapping in `RunBattleAi.create(...)` is a one-line change *if* wanted. Decide when
  authoring the `rgl_*` JSONs, not before.
- **Q4 — The trainer's bag.** RCT trainers may use authored bag items mid-battle; PokéRogue's
  trainers never heal/item mid-fight (their difficulty is party composition). Keep the bag
  (RCT-authentic) or pass `null` into the actor (PokéRogue-faithful)? Currently kept, cloned.
- **Q5 — PokéRogue rules we have consciously not mirrored** (confirm they stay unmirrored):
  per-wave money on wild waves (diverged: wild pays 0, see CreditRules); trainer dialogue/victory
  messages (RCT trainers have their own chat lines; roguelite adds none); double battles
  (`GEN_9_SINGLES` forced for every wave — an authored trainer declaring doubles is overridden,
  RogueliteTrainerBattles.kt:281–291); PokéRogue party-size/level templates (ours come from the
  roster's `TeamGenerationRules` + wave curve instead — see §1 for theirs, kept as a balancing
  reference only).
