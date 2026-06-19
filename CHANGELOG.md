# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

One version covers the entire repo: the modpack (`modpack/pack.toml`) and the
custom mod (`custom-mods/cobblemon-npc/gradle.properties`) move together. The
git tag (`vX.Y.Z`) is the source of truth — see the Releasing section in the
root README.

## [Unreleased]

## [0.22.5] - 2026-06-18

Chat de-bloat pass: streak/counter spam trimmed, breeding-streak wording removed.

### Changed
- **IV streak chat throttled to one message per 5 catches.** Cobblemon Unchained fires its
  "…with perfect IVs!" line on *every* boosted catch once a per-species IV streak is up, which
  spams chat for any dedicated hunter. The mod has no cadence setting (`notifyPlayer` is on/off),
  so `cobblemon-bridge` now gates Unchained's `notifyPlayer()` (`UnchainedIvNotifyThrottleMixin` +
  `IvNotifyThrottle`): only every 5th boosted IV message per (player, species) is shown — the 5th,
  10th, 15th, … The IV boost itself is unchanged (applied earlier in the booster run), and **shiny
  and hidden-ability notifications are untouched** (only `iv*` booster keys are throttled). Breeding/
  egg streaks remain fully disabled (0.22.2).
- **Cobbled Counter broadcasts moved out of chat.** The separate Cobbled Counter mod broadcast
  `Captured/Knocked out/Fished/Snacked/Resurrected a … (Count N/Streak M)` to chat on every action.
  New `config/cobbled_counter.json` override sets `broadcastLocation: ACTION_BAR` (KO/snack counts
  now show above the actor's hotbar, off global chat) and `noBroadcastFor: ["HATCH", "CAPTURE",
  "FISH", "RESURRECTION"]` — those four are replaced by our own rarity-coloured lines (below).
- **Hatch line cleaned up + rarity-coloured.** Replaced Cobbled Counter's
  `Hatched a … (Count/Streak)` — whose "Streak" suffix implied breeding streaks still exist — with a
  plain `Hatched a <Pokémon>` line sent only to the hatching player, the species name coloured by the
  egg's gacha tier (common=gray, uncommon=green, rare=blue, ultra=purple, ultra-rare/shiny=gold;
  bred/daycare eggs = white). `HatchAnnounceHook` + `PokemonEggMixin` (captures the egg tier at hatch).
- **Catch/fish/resurrect lines rarity-coloured on the action bar.** `CaptureAnnounceHook` replaces
  Cobbled Counter's suppressed capture/fish/resurrection broadcasts with a `Captured a <Pokémon>` /
  `Resurrected a <Pokémon>` line on the actor's action bar (off chat — you catch far more than you
  hatch). Name colour reflects the species tier: shiny=gold (✨ prefix), legendary=gold,
  mythical=light-purple, paradox=red, ultra-beast=aqua, everything else=white. Covers wild and fished
  catches (both fire `POKEMON_CAPTURED`) plus fossil revives (`FOSSIL_REVIVED`). KO counts stay on
  Cobbled Counter's action bar — they're useful for shiny/HA chaining (KO streaks feed the spawn
  shiny/hidden boosters).

### Removed
- **"Your <tier> egg is ready to hatch!" chat ping.** Dropped from `EggDefeatHook` — the egg's
  tooltip/timer already surfaces readiness, so the per-egg line was pure noise (server log kept).

### Fixed
- **Spawn bucket weights now actually apply.** Cobblemon only reads the external
  `config/cobblemon/spawning/best-spawner-config.json` when `exportSpawnConfig: true`; it had been
  `false`, so `BestSpawnerConfig.load()` used the jar-bundled default (ultra-rare `0.2`) and every
  bucket-weight nerf we'd shipped was inert. Set `exportSpawnConfig: true` (`config/cobblemon/main.json`)
  so the external file is read.

### Changed
- **Rare/ultra-rare spawn rates retuned now that they take effect.** Bucket weights set to
  common `94.88` / uncommon `5` / rare `0.1` / ultra-rare `0.02` (sum 100, so each weight is its
  per-roll %). Ultra-rare drops from the de-facto `0.2` (jar default) to `0.02` (10× rarer), rare
  from `0.5` to `0.1`.

## [0.22.3] - 2026-06-18

### Changed
- **Replaced `/pay` with our own implementation.** NeoEssentials' `/pay` was unreliable on this
  server, so it's disabled (`config/neoessentials/commands.json` → `"pay": false`) and reimplemented
  in the bridge (`PayCommand`). `/pay <player> <amount>` transfers coins between online players using
  the same NeoEssentials economy balances (`/balance`, `/baltop`, income all stay consistent). The
  transfer is debit-then-credit with a refund if the recipient can't be credited, so coins can't
  vanish. NeoEssentials' `/balance`, `/baltop`, `/eco` etc. are untouched.

## [0.22.2] - 2026-06-17

### Changed
- **Pasture size reverted 2 → 5.** The 0.22.0 nerf to `pastureInventorySize` also shrank the egg
  storage (Cobreeding ties Pokémon slots and egg slots to the same value), which was too tight.
  Back to 5 (`server-overrides/config/cobbreeding/main.json`). Max active pastures per player stays 4.
- **Cobblemon Unchained breeding streaks disabled.** The shiny/IV/hidden-ability "egg" streak boosters
  (which ramp up odds as you hatch more bred eggs) are now `enabled: false`
  (`server-overrides/config/unchained/egg/{shiny,iv,hidden}EggBooster.json`). Spawn/capture/fish
  streak boosters are untouched. Combined with shiny breeding already being off, breeding gives no
  streak-based shiny/IV/HA advantage.

## [0.22.1] - 2026-06-16

### Fixed
- **Breeding parents are now actually untradeable.** The parent-side trade lock (0.20.0) never fired:
  it listened for Cobblemon's `COLLECT_EGG` event, which only fires for Cobblemon's *native* daycare —
  this server breeds via Cobreeding, which has its own egg system and never emits it. So parents could
  still be traded through the normal Cobblemon trade menu. Replaced the dead event hook with a
  server-tick monitor (`BreedingParentTagHook`) that watches Cobreeding's per-pasture egg state
  (`PastureBreedingData.registry`): when a pasture lays an egg, every Pokémon tethered to it is tagged
  `bred_parent` (and the owning store is flagged dirty so the tag survives a restart). The bred-*child*
  lock was unaffected — Cobreeding does fire `HATCH_EGG_POST`, so `BredTagHook` already tagged offspring.

## [0.22.0] - 2026-06-16

### Changed
- **Breeding nerfs.**
  - **Shiny breeding disabled** — a bred (non-gacha) Pokémon never hatches shiny (`BredTagHook`).
  - **Hidden-ability breeding halved** — a bred Pokémon that inherited its hidden ability keeps it
    only ~50% of the time; otherwise it's rerolled to a normal ability.
  - **Bred-egg hatch time 30m → 1h** (`BRED_DEFAULT_SECONDS`).
  - **Pasture capacity 5 → 2** mons and **max active pastures per player ∞ → 4**
    (`server-overrides/config/cobbreeding/main.json`).
- **Gacha crate-egg hatch timers shortened.** common 10m, uncommon 20m, rare 30m, ultra/ultra-rare 1h,
  and **shiny eggs 1h** (any pool). Shiny gacha eggs are now tagged with a dedicated `shiny` tier
  (`RewardGranter`) so they use the 1h timer regardless of which pool they rolled from.

## [0.21.0] - 2026-06-16

### Fixed
- **High-level wild trainers are reasonable again.** Above party level ~60 the RCTmod wild pool used
  to collapse to over-leveled competitive bosses (gym leaders, Elite Four, champions, rivals). New
  `server-trainer-spawns` datapack (gen: `ops/gen_highlevel_trainers.py`): (a) removes gym leaders +
  Elite Four from wild spawning (`spawnWeightFactor: 0`), (b) level-gates the remaining bosses
  (champion/rival/battleground/team-admin → retyped `normal` so they respect level-scaling, weight
  cut to 0.08 so they're drowned out), and (c) adds ~80 themed mid-tier trainers at L55–100 (Bird
  Keeper, Bug Catcher, Hiker, Fisherman, Black Belt, Psychic, Hex Maniac, Cooltrainer) with 2–4
  fully-evolved Pokémon, natural auto-assigned movesets, empty EVs — non-competitive, level-matched.
- **Loss-EXP now honors Lucky Egg and EXP Share.** The "keep per-defeat EXP even on a loss to a
  trainer" hook (`PveLossExpHook`) was paying flat base EXP to participants only; it now mirrors
  Cobblemon's normal distribution — Lucky Egg ×1.5 on the holder, and benched EXP-Share holders get
  their `experienceShareMultiplier` (0.5×) share.

### Changed
- **No more 2× EXP for re-clearing a gym you've already beaten.** `TrainerExpBoostHook` still doubles
  trainer-battle EXP, but the bonus is now suppressed for gym/E4 trainers the player already holds the
  `beat_gym_N` advancement for — repeat clears pay the regular 1×. First-ever clears still get 2×.

## [0.20.0] - 2026-06-16

### Changed
- **Regi summon keys demoted from Ultra → Rare crate.** All five (Rock/Steel/Ice/Dragon Golem Keys
  + Titan Key/Regigigas) now drop from the Rare crate at 2.0% each instead of the Ultra crate. Both
  tables renormalized to 100%.
- **Breeding restriction reworked: parents *and* children can't be traded** (replaces the old
  "must be the Original Trainer to breed" rule). Breeding no longer cancels the egg; instead both
  parents are tagged non-tradeable at egg collection (children were already non-tradeable via the
  bred tag). Closes the breed-rare-mons-and-sell loop. (`BreedingTradeLockHook` replaces
  `BreedingOTHook`; the trade gates now use `BredTagHook.isTradeLocked`.) Note: a Ditto used as a
  parent is tagged too.
- **Legendary Monuments ~2× more common.** New `server-monument-frequency` datapack scales the 15
  rare monuments' worldgen `spacing`/`separation` by 0.7 (density ≈ ×2) — compensating for the
  banned Arc Phone locator. Already-common monuments and villages are unchanged.
- **Blue Moon shiny boost cut.** New `server-lunar-shiny-nerf` datapack lowers the Cobblemon
  Integrations Blue Moon shiny multiplier 64× → 8× (Super Blue Moon 128× → 16×).
- **Cobbleworkers buffed.** `areaScanRadius` 2→3, `areaScanCooldown` 45→15, `scannedBlocksPerTick`
  50→128, `navigationTimeout` 30→60 — workers find work far more often, path more patiently, and
  cover a wider area.

## [0.19.3] - 2026-06-15

### Changed
- **Ultra-rare spawn bucket dropped 10× (`0.001 → 0.0001`).** Starters share the `ultra-rare` bucket
  with legendaries/UB/paradox and dominate it (weight 6.0 vs 0.05–1.0), so they read as too common in
  their biomes; this cuts every ultra-rare *wild* spawn 10×. Wild legendary/UB encounters get rarer too,
  but those are primarily obtained via monuments/shrines + gacha, so wild spawns are a minor source.
  `common` absorbs the 0.0009 so the bucket table still sums to 100.

## [0.19.2] - 2026-06-14

### Changed
- **Legendary Monuments Entrepreneur no longer sells Light/Dark Stone Shards.** The Entrepreneur
  villager's code-registered Tier-4 trades (`legendarymonuments:lightstone_shard` /
  `darkstone_shard` → Reshiram / Zekrom) are stripped via a NeoForge `VillagerTradesEvent` hook
  (`EntrepreneurTradeFilter`), since they're baked into the mod and can't be removed by a datapack.
  Reshiram / Zekrom remain obtainable through the Ultra gacha crate (which sells the full Light/Dark
  Stone). All other Entrepreneur wares — the Treasure-of-Ruin Seals, Silver Wing (Lugia), and
  Celestica Flute (Arceus) — are intentionally left in place.

## [0.19.1] - 2026-06-14

### Added
- **Breeding Rule 1 — you can only breed Pokémon you're the Original Trainer of (Dittos exempt).**
  `BreedingOTHook` cancels the egg at collection (Cobblemon's cancelable `CollectEggEvent`) when a
  non-Ditto parent's Original Trainer isn't the breeding player, with a chat warning + audit log.
  (First of a larger breeding-restrictions set; pasture-access, no-bred-Cobbleworkers, and
  bred-egg audit logging are still in progress.)

### Changed
- **Rare crate: Eye of Ender payout cut 20 → 16.** Quantity-only nerf (the 2.0% drop odds from
  0.19.0 are unchanged); the rare table still sums to exactly 100%.

## [0.19.0] - 2026-06-14

### Changed
- **Gacha crate rebalance.** Common crate: Ultra Balls 10 → 20 and Quick Balls 32 → 20 per pull
  (round, consistent ball counts). Rare crate jackpot odds cut — Master Ball 2.5% → 0.5%, Ultra Key
  1.0% → 0.5%, "2 Rare keys" 2.0% → 1.0%, "20 Eye of Ender" 5.0% → 2.0%; the freed weight is
  redistributed proportionally across the other entries so the table still sums to exactly 100% (the
  odds menu shows true chances).
- **Beldum line is now ultra-rare in the End.** Beldum / Metang / Metagross spawned in the End
  (`#cobblemon:is_end`) at the `rare` bucket (~100× more common than the legendaries and Ultra Beasts
  that the ATM spawn-nerf pipeline pins to `ultra-rare`). New `server-end-spawn-nerfs` datapack moves
  only the End-biome entry to the `ultra-rare` bucket; every other biome (Aether, dripstone, peak,
  howling constructs) is unchanged, so the line is still obtainable normally elsewhere.
- **Doubles PvP battles now stay unranked.** The ranked system intercepted *every* player-vs-player
  battle and forced it into ranked 1v1-singles team-select. The `BATTLE_STARTED_PRE` veto now only
  claims 1v1 **singles** matches; a Doubles (or any non-singles / multi) battle started from the
  Cobblemon battle menu runs as a normal, casual battle with no ELO and no arena teleport.
- **The 2-minute gym cooldown now covers the Elite Four (gyms 20–24).** Previously the E4 gauntlet was
  exempt. The cooldown is per-(player, gym), so it never blocks normal gauntlet progression
  (each member is fought once per run) — only re-fighting the *same* member within 2 minutes, closing
  the E4 EXP-farm loop. Trade-off: retrying a member you just lost to waits out the cooldown.
- **Zygarde is no longer buyable from the Legendary Monuments Entrepreneur.** The Entrepreneur villager's
  `20 emerald + 40 relic_coin → zygarde_cell` trade is removed via the new
  `server-monument-villager-trades` datapack (an edited `outskirt_stand.nbt` structure template). Applies
  to monuments generated after deploy; the emerald → relic-coin trade is unchanged.

### Fixed
- **Legendary Monuments chests can no longer drop Large or XL EXP candy.** Eight monument chest loot
  tables (bell tower, dragoeleki, liberty island, regice/rock/steel/gigas, turnback cave) handed out
  `exp_candy_l` / `exp_candy_xl` as free max-level fuel. `ops/strip_exp_candy_from_chest_loot.py` now
  strips just those two sizes from the monument chests (small/medium stay) via the
  `server-no-exp-candy-chests` datapack.

## [0.18.6] - 2026-06-13

### Fixed
- **Ultra-rare spawns (legendaries, Ultra Beasts, Paradox, mythicals) were spawning ~90–100× too
  often.** The `server-spawn-nerfs` datapack re-declared AllTheMons' spawn files at the same resource
  paths, relying on out-prioritising AllTheMons in the enabled-pack order — but AllTheMons ships as a
  version-named zip that Minecraft auto-enables *last* (highest priority) on every bump, silently
  clobbering all our nerfs. End Ultra Beasts sat in the `rare` bucket at weight 5.0 (~1 in 1,200)
  instead of ultra-rare at tier weights (~1 in 100k). Tier weights are now baked **directly into the
  AllTheMons zip** by `ops/gen_spawn_nerfs.py`, so load order can no longer override them, and the
  `server-spawn-nerfs` datapack is retired. Also corrects two leaks the old generator silently skipped:
  Virizion (spawned wild at weight 4 despite being LegendaryMonuments questline-only → suppressed to 0,
  matching Cobalion/Terrakion) and Meloetta (weight 2.0 → floored to 0.1 like other mythicals).
- **Retired datapacks lingered on the live world.** The non-destructive deploy rsync never removed a
  pack deleted from the repo, so retired `server-*` packs stayed active forever — including the
  0.7.38-era `server_spawn_filler` namespace (retired at 0.7.43) that kept diluting the ultra-rare
  bucket. A new prune step (`ops/prune-removed-server-datapacks.sh`, wired into dev + prod deploys)
  removes any `server-*` datapack on the server we no longer ship.

## [0.18.5] - 2026-06-12

### Changed
- **Gym-AI fallback now plays smarter, and the poke-engine bridge self-heals and logs breaking battles.**
  When the bridge can't produce a move and Cobblemon's `StrongBattleAI` fallback is also unusable, the
  `cobblemon-poke-ai` last-resort floor now picks the most type-effective damaging move (reusing
  Cobblemon's own type chart) and switches only when forced — instead of grabbing the first usable move —
  so a degraded turn still hits hard and never voluntarily switches. On the bridge side, `/healthz` is now
  health-aware (only a genuinely poisoned worker is recycled, never on deterministic data bugs that would
  otherwise take every replica down), and pick failures are recorded with a grouping fingerprint and
  rotated so the batch-fix backlog survives a full disk. Server-side only — no client reinstall.

## [0.18.4] - 2026-06-12

### Changed
- **All gym leaders and the Elite Four now run the poke-engine AI at temperature 0.5.** Previously all
  24 gym/E4 trainers (`gym_01`–`gym_24`, including the Oak/Alder/Cynthia/Ash/Lance/N champion cast) ran
  `pe` at the default temperature 0.0 (perfect maximin). They now carry `ai.data.temperature = 0.5` —
  the same opponent-fallibility setting the HARD Battle Tower challengers already use — applied in both
  the `server-gyms` datapack and the `cobblemon-npc` skin jar. Takes effect at runtime only on the
  patched poke-engine bridge (which honors the temperature arg; otherwise it degrades to 0.0 MCTS).

## [0.18.3] - 2026-06-11

### Changed
- **Battle Tower HARD titles are now bold red with a skull.** Following 0.18.2's mode markers, the
  HARD track's `name` is stamped `§c§l☠ <leader> [HARD]` (bold red + skull) to visually emphasize
  the harder track; NORMAL stays plain `<leader> [Normal]`. Restyled by `gen_battle_tower_teams.py`
  across all 20 HARD trainers in both the `server-gyms` datapack and the `cobblemon-npc` skin jar;
  the idempotency strip now also peels the leading §-codes + skull so re-runs don't stack.

## [0.18.2] - 2026-06-11

### Fixed
- **Battle Tower trainer titles now show the difficulty.** A floor's HARD and NORMAL tracks shared
  one leader name (e.g. "Penny"), and since that `name` is what the battle UI displays, players
  couldn't tell mid-battle whether they were on the hard or normal track — only the over-head
  nameplate distinguished them. Both tracks' `name` now carries a mode marker (`Penny [HARD]` /
  `Penny [Normal]`), matching the nameplate convention in `TowerManager`. Stamped by
  `ops/gen_battle_tower_teams.py` (idempotent strip-then-reapply) across all 20 leader pairs in both
  the `server-gyms` datapack and the `cobblemon-npc` skin jar.

## [0.18.1] - 2026-06-11

### Added
- **Multiworld showcase worlds are now always sunny.** `WorldRulesHook` keeps weather permanently
  clear in the `multiworld:*` dimensions (spawn, Elite Four, arenas) via a slow server-tick poll.
  Multiworld gives each world independent weather, so this only affects the showcase worlds — the
  survival overworld (the wilderness) keeps its normal weather cycle.

### Changed
- **No random trainers in the Elite Four or arenas.** Added `multiworld:elite4` / `multiworld:arena1`
  / `multiworld:arena2` to rctmod's `dimensionBlacklist` so wandering trainers no longer spawn there.
  (rctmod spawns trainers through its own system, bypassing the vanilla spawn event `WorldRulesHook`
  already guards.) Placed gym / Elite Four trainers are command-summoned and unaffected.

### Fixed
- **Gym and Battle Tower battles now play the gym battle music.** In 0.18.0 the theme was stamped on
  `EntityInteract`, which misses the force-battle/on-sight start path (how gym fights usually begin),
  and Battle Tower trainers carry no `gym_id` tag at all — so neither was themed. Moved theme
  stamping into `GymBattleGate`, the universal gate at the head of `TrainerMob.startBattleWith` that
  covers both right-click and force-battle. Regular gyms, Battle Tower fights (shared gym pool), and
  the Elite Four (per member) are now all themed reliably.
- **Arena worlds were silent.** The arena exploration pool (`music.arena`) had no tracks — the arena
  audio is the battle pool. `music.arena` now aliases the `battle.arena` pool (no duplicate audio),
  so the arena world plays the arena music as ambient and PvP fights use the same pool.

## [0.18.0] - 2026-06-11

### Added
- **Per-world soundtracks.** A new client-only mod (`cobblemon-soundtracks`) plus a server-side hook
  give the custom Multiworld dimensions their own music, while the survival overworld (the wilderness)
  is left untouched on vanilla music.
  - **Exploration music** is chosen by dimension via NeoForge's `SelectMusicEvent`: the spawn hub
    (`multiworld:spawn`) and the Elite Four world (`multiworld:elite4`) each play their own pool of
    tracks; every other dimension keeps vanilla music.
  - **Battle music** is driven by Cobblemon's per-actor `battleTheme` (`cobblemon-bridge`'s new
    `BattleThemeHook`): the Elite Four play a theme **per member** (Alder / Cynthia / Ash / Lance / N,
    keyed on gym id 20–24), all regular gym fights (1–19) share one rotating pool, and the PvP arenas
    share a pool. Cobblemon auto-pauses the exploration track for the battle's duration and resumes it
    after. Music is per-player (only the battlers — and any spectators — hear a battle theme).
  - Track selection is the engine's random pick (per music cycle / per battle) for now; a strict
    no-repeat shuffle was deferred.
  - 77 tracks ship inside the mod jar as 128 kbps OGG Vorbis (~176 MB jar); the `.mrpack` delivers it
    to clients and the server skips it (`dist=[Dist.CLIENT]`). The source audio and conversion
    pipeline live under `ops/soundtracks/` (`build-soundtracks.py`, ffmpeg + oggenc).

## [0.17.2] - 2026-06-11

### Added
- **`/e4 skip` — op-only Elite Four test bypass.** Admins (op level 2) can toggle a per-player
  bypass that lets them right-click and challenge any Elite Four member (gyms 20-24) out of order,
  instead of grinding the full gauntlet in sequence — handy for testing a single E4 fight. With the
  bypass on, `E4GauntletHook.canChallenge` accepts any E4 gym and the party-lock + dimension leashes
  are disengaged for that player. In-memory per-UUID only (clears on restart) and affects no other
  player, so live gauntlet runs are untouched. `/e4 skip` shows current state; `/e4 skip on|off`
  toggles it.

## [0.17.1] - 2026-06-11

### Fixed
- **Gym AI 500-crashed when N's Mega Rayquaza entered the field (Champion fight).** 0.16.1 set Mega
  Rayquaza's ability to its canonical `deltastream` (Delta Stream), but poke-engine/foul-play's
  weather enum doesn't know the `deltastream` (strong winds) weather it sets — so the bridge raised
  `ValueError: Unknown weather deltastream` and returned **500 on every `/pick`**, dropping the gym
  AI into its StrongBattleAI fallback (which mishandles switch choices — the perma-switch "bug-out"
  symptom). Reverted Rayquaza to the engine-safe `airlock` (its base ability). The mega still fields
  correctly (stats/render are datapack-side); only the ability changed. Fielding true Delta Stream
  would require teaching poke-engine the `deltastream` weather first. Diagnosed from the dev bridge
  journal (`journalctl -u poke-engine-bridge`); the 500 path doesn't write `pick_failures.jsonl`,
  which is why that log looked empty.

## [0.17.0] - 2026-06-11

### Added
- **Disconnect-tolerant Elite Four & Battle Tower runs.** Previously a disconnect (or server
  restart) mid-run wiped all progress, forcing a restart from E4 1 / tower floor 1 — punishing for
  anyone who simply dropped connection mid-gauntlet. Both gauntlets now write a resumable snapshot
  (next gym/floor + the locked party + dimension/epoch-day) to the player's NBT at each progression
  checkpoint and restore it on reconnect, using the same crash-safe `persistentData` mechanism the
  gym down-level recovery already relies on. A run now survives disconnect, a clean restart, and a
  crash. (Cobblemon ends a battle on disconnect via `battle.stop()`, which fires no victory/flee
  event — so the only thing that used to fail these runs on disconnect was the logout handler, which
  now suspends rather than clears.)
  - **Elite Four:** resumes wherever you left off, with no expiry (the gauntlet has no daily
    rotation). Discarded only if you're no longer in the Elite Four dimension when you return.
  - **Battle Tower:** resumes the same day, but if the **daily leader rotation** elapsed while you
    were away (the run's stored epoch-day no longer matches today's), the run is voided and you're
    told to restart from floor 1 — the tower's lineup changed underneath you. Clearing the tower
    already-today still blocks a second key.
  - The party lock, the E4 dimension leash, and every existing fail condition (loss / flee / healing
    machine / party swap) are unchanged — only a disconnect no longer counts as a failure.

## [0.16.1] - 2026-06-11

### Fixed
- **Elite Four megas spawned as base forms.** Cynthia's Mega Lucario (gym 21) and N's Mega Rayquaza
  (gym 24) were authored with `"form":"mega"`, but rctmod's team parser silently ignores `form` — its
  `PokemonModel` only reads `aspects` (`PokemonModelConverter` calls `setForcedAspects`), so both
  fielded base-form stat blocks. Switched to `"aspects":["mega"]` (no held stone — the in-battle mega
  transform is gen6/7-only and never fires in our `GEN_9_SINGLES` gyms; the aspect spawns the mega
  pre-formed). Also corrected N's Rayquaza ability to its mega ability `deltastream` (was the base
  `airlock`). The same ignored-`form` bug left Alder's "Galarian" Slowking (gym 20) as a base
  Kantonian Slowking — fixed to `"aspects":["galarian"]`.
- **Alder's Slowking summoned snow.** Its Chilly Reception set snow weather, which clashed with
  Alder's fire core (Ho-Oh, Cinderace, Volcarona). Swapped Chilly Reception → Sunny Day so the
  Slowking now sets sun to back the team. All four team edits were applied to both synced copies (the
  `server-gyms` datapack and the `cobblemon-npc` skin jar).

### Changed
- **Elite Four progression messages now name the trainer and their statue.** Beating an E4 member
  previously read only "Next: E4 2 (Gym 21)". It now reads e.g. "You beat Cynthia! Go challenge Ash
  (E4 3) next — you'll find them under the Greninja statue." Clearing E4 #4 gives a Champion hand-off
  ("…It's time to face the Champion. N awaits beneath the Rayquaza statue."), and beating N gives a
  full Champion victory message. Statues map to each team's perfect-IV ace (Ho-Oh, Lucario, Greninja,
  Eternatus, Rayquaza), defined in `E4GauntletHook.kt`.

## [0.16.0] - 2026-06-11

### Added
- **Wilderness reset mod** (`custom-mods/cobblemon-wilderness`, server-side, ships **disabled**).
  Caps unbounded overworld growth by regenerating chunks that lie wholly outside a persistent,
  region-aligned keep-box. A region file is deleted only if it does not touch the box; `snapToRegions`
  (default on) expands the box out to whole-region boundaries so the enforced zone equals the box with
  no rounding slop. All deletion runs once per boot (`ServerAboutToStartEvent`, before levels load —
  chunks guaranteed unloaded), and clears `region/`, `entities/`, and `poi/` together.
  - Two default-safe gates: `enabled=false` (master) and `dryRun=true` (log-only). First boot with
    `enabled=true` records a baseline and skips, to avoid a surprise wipe.
  - **Circuit breaker** (`maxDeleteFraction`, default 0.9) aborts a run that would delete too large a
    fraction — a net against a mis-typed box.
  - **Player warnings** (server→client chat): on crossing the boundary outward and again on login if
    already outside, naming the safe build zone and the reset date. Gated on `enabled`.
  - Commands `/wildreset status|preview|now|cancel` (op level 4); `preview` is read-only and safe on a
    live world, `now` only arms the next boot's pass. See the mod README for the rollout procedure.
  - Default keep-box `-20480..20479` (regions −40..39). Offline preview of the live overworld: keep
    ~1,550 regions / reclaim ~615 (~2.3 GB) now, growing weekly.

## [0.15.5] - 2026-06-11

### Fixed
- **Ho-Oh rendered garbled in battle** ("overlapping skins"). AllTheMons' Ho-Oh remodel declares the
  generic geometry id `geometry.hooh` (the same id base Cobblemon uses); on its heavy 140-bone winged
  model that id clash mispairs the animation onto the wrong model. Disabled just that remodel (via
  `ops/disable-atm-remodels.sh`, which strips the resolver/model/poser/animation/texture from the
  resourcepack copy) so Ho-Oh falls back to base Cobblemon's model. Species data, spawns, and the
  server datapack copy are untouched. Other heavy-legendary remodels (Reshiram, Zekrom, Kyurem,
  Giratina, Palkia, Lugia, Zacian, …) share the same id pattern and can be added to the same script
  if they turn out to garble too.

## [0.15.4] - 2026-06-11

### Changed
- **Updated AllTheMons R3.5 → 3.5.1.** 3.5.1 ships the official fix for the Mewtwo blink crash
  (`Unknown animation group: _atm`), so it supersedes the one-character hand-patch from 0.15.3 (now
  running the upstream pack unmodified again), plus the latest models/animations/hitboxes. The
  resourcepack + datapack copies and `options.txt` were bumped to `[R3.5.1]`; the old `[R3.5]`
  datapack is auto-removed on the server by the 0.15.1 stale-AllTheMons prune step (filename change).
  Note: 3.5.1 still ships `geometry.hooh` (shared with base Cobblemon), so it does not specifically
  address the Ho-Oh remodel render quirk.

## [0.15.3] - 2026-06-11

### Fixed
- **Client crash rendering Mewtwo** (`Unknown animation group: _atm`). AllTheMons R3.5's Mewtwo
  poser has a typo — one blink quirk referenced the animation group `_atm` instead of `mewtwo_atm`,
  and `_atm` is defined nowhere in the pack, so *any* Mewtwo (wild, gym, `/testteam`) crashed the
  client on render. Patched the poser inside the bundled `AllTheMons [R3.5].zip` (both the
  resourcepack and datapack copies). A full scan confirmed it was the only broken poser in the pack.

## [0.15.2] - 2026-06-11

### Added
- **`/testteam` dev command** (op level 2+) — `/testteam <player> <tier> <level> [index]` stashes the
  target's party into their PC, then gives a preset competitive team at the requested level. Tiers:
  `uber` / `ou` (perfect IVs + competitive EVs/moves/items, legendaries in `uber`) and `normal`
  (~22 IVs + partial EVs — a decent player-grade team). Teams are authored in
  `config/cobblemon-bridge/runtime/test_teams.json` (same format as gym teams, editable without
  recompiling) and picked at random per tier unless an index is given.

## [0.15.1] - 2026-06-11

### Changed
- **Trainer admin commands reorganized** into `gym/`, `e4/`, `bt/` namespaces with short names —
  `/function server:gym/spawn_fire`, `server:e4/spawn_alder`, `server:bt/spawn_faker` (the folder
  already names the category, so the redundant prefix is dropped). The dev/prod deploy now runs a
  scoped `--delete` prune over just those three function folders, so renamed-away commands — like
  the old numbered `spawn_N` — are removed instead of lingering on the server.

## [0.15.0] - 2026-06-11

### Added
- **Faker & Professor Oak — secret battle-tower bosses.** Two master-tier challengers join the daily
  tower rotation, each with a normal (`rb`) and hard (`pe`) track. Faker ("Unkillable Demon King")
  fields a League-of-Legends-themed team of custom-nicknamed Pokémon; Professor Oak ("Kanto Master")
  brings his legendary squad. Test-spawn via `/function server:tower/spawn_faker | spawn_oak`.
- **Hidden boss achievements.** Four `hidden` advancements for beating Faker/Oak (normal & hard) —
  invisible in the advancement tree until earned, each awarding a Rare Key (with a larger cash
  bounty on Hard Mode).
- **Authored gym level caps.** `config/cobblemon-bridge/runtime/gym_caps.json` is now the single
  source of truth for per-gym in-battle caps and the overworld progression, replacing the old
  number-derived formula. The Elite Four now enforce an L70 cap (previously unbounded).

### Changed
- **Elite Four rebalance.** Non-ace Pokémon softened (lower IVs/EVs) while each trainer's ace keeps
  perfect stats. Re-themes: Alder's Garchomp → **Excadrill**, Lance's Garchomp → **Salamence**,
  Cynthia now runs her full **Sinnoh (Platinum)** team with a Mega Lucario ace. Champion N
  de-legendaried (Heatran/Landorus/Tapu Koko → Ferrothorn/Gliscor/Rotom-Wash), keeping Zekrom,
  Zoroark, and a now-**shiny** Mega Rayquaza so Zoroark's Illusion shows a shiny disguise.
- **Gym admin functions renamed** by type/name — e.g. `/function server:gym/spawn_gym_fire`,
  `spawn_e4_alder` (was `spawn_5`, `spawn_20`).
- **Achievements no longer broadcast to chat** — unlocks show a personal toast only.

### Removed
- 48 orphaned `aitest_*` trainer files (leftovers from the removed AI-test experiment).

## [0.13.2] - 2026-06-11

### Added
- **Ultra Key for clearing all challenge gyms** — defeating the challenge variant of all ten
  gym-type leaders (gyms 1-10, in any order) now awards an Ultra Key via the new "Hard Mode
  Master" advancement. Retroactive: players who already cleared them get it on their next login.

### Removed
- **Disabled the custom `/trade` command** — the Pokémon+money trade GUI was too buggy. Players
  use the native Cobblemon trade (look at a player, press **R** → Trade) and `/pay` for money.

## [0.13.1] - 2026-06-11

### Added
- **"Master Collector" side quest** — a follow-up to Centurion that grants a **Master Ball**
  once you've caught **300 different Pokémon species**. Branches off Centurion (100 species →
  PokéNav); the bridge's `PokedexProgressHook` awards it at the 300 threshold, and the reward
  function gives the Master Ball.

## [0.14.2] - 2026-06-11

### Fixed
- **Gym leader skins (the actual fix).** rctmod's `TrainerRenderer` resolves a
  trainer's skin purely by **trainer id** (`findResource(trainerId, "textures")`)
  and ignores the `textureResource` data field entirely — so 0.14.1's
  `textureResource` change had no effect and gyms 1–18 still rendered the Steve
  fallback. Added id-named textures (`gym_01_ground.png`, `bt_01_ground.png`, …)
  for all 54 type-skinned gym/battle-tower trainers, copied from the `type_*`
  textures. E4 (19–24) already worked because their textures were id-named.

## [0.14.1] - 2026-06-11

### Fixed
- **Gym leader skins now render.** `textureResource` was set on the `trainers/`
  team file (rctmod `TrainerData`, which ignores it) instead of the `mobs/` file
  (`TrainerMobData`, the only reader), so every gym/E4/tower leader fell back to
  the default skin. Added it to all 60 custom-skinned `mobs/` files.
- **Gym AI reliability (poke-engine bridge).** Serialized foul-play's
  thread-unsafe dataset cache (concurrent `/pick` calls raced into
  `IndexError`/`KeyError` 500s → StrongBattleAI fallback → perma-switch); built
  the patched poke-engine fork on the bare-metal install path so the per-gym
  temperature dial stops 500ing challenge/E4 battles; and added a replayable
  `pick_failures.jsonl` for triage.

## [0.14.0] - 2026-06-10

### Added
- **Battle Tower system.** New `bt_01`–`bt_18` trainer scheme (plus `_challenge`
  variants) in cobblemon-bridge, with per-floor progression, force-battle-proof
  floor ordering, and BT-win achievements. Replaces the standalone challenge-gym
  trainers, which are dropped in favour of the tower roster.
- **Custom Elite Four / Champion cast.** Gyms 19–24 get a rethemed, hand-built
  lineup — Oak, Alder, Cynthia, Ash, Lance, and N — each with a bespoke L65 team,
  a dedicated NPC battle skin, and per-player Elite-Four-style voice lines.

### Changed
- **E4 trainer ids renamed to character names.** `gym_20_lorelei` → `gym_20_alder`,
  `gym_22_agatha` → `gym_22_ash`, `gym_24_champion` → `gym_24_n` across the server
  datapack and the cobblemon-npc skin jar, with every reference updated (spawn /
  delete functions, advancements, defeat loot tables). Gyms 19/21/23 keep their
  names.
- **Reward tuning.** Normal gym cash is capped past gym 10; challenge wins pay a
  flat reward and always grant a rare key; foul-play (pe) AI NPC fights pay 1.5×
  per-fight money.

### Fixed
- **Gym-AI temperature fallback.** The poke-engine bridge now falls back to plain
  MCTS when the configured temperature is unsupported, instead of erroring.

## [0.13.0] - 2026-06-10

### Changed
- **Elite Four gauntlet now includes the Champion (gym 24)**, mainline-style: the gauntlet
  is gyms 20-24 (E4 #1-4 + Champion), beaten consecutively in one run. Beating E4 #4 no
  longer ends it — the dimension + party leashes stay engaged through the Champion, and
  losing/fleeing/leaving (including against the Champion) restarts from E4 #1. E4 #4 and the
  Champion still each award an Ultra Key. Quest descriptions updated to match.

### Added
- **Gym battle cooldown** — a 2-minute per-gym cooldown after starting a battle (announced in
  chat when blocked), to stop gym battles being spam-farmed for EXP. Mainline/challenge/rotating
  gyms only; the Elite Four gauntlet (gyms 20-24) is exempt.

## [0.12.3] - 2026-06-10

### Changed
- **Gacha crate tuning, round 2.**
  - **Ultra:** removed the legendary **pedestals** entirely and hand-reweighted to a
    true 100%; **2 Ultra Keys → 0.5%**; **Master Ball → 2**; **20 Eye of Ender** moved
    to the rare crate.
  - **Rare:** **IV Candy 5 → 3**.
  - **Loot tier "Floor" renamed "Standard"** (`LootTier` enum + announce flair; the
    CSV parser still accepts the old "floor").
  - **Jackpot tier** now contains only items at ≤1% weight.
  - **Player-facing crate notes** cleaned up (removed version numbers / dev commentary).

### Added
- **Shiny eggs now draw from `rare` *and* `uncommon`** pools — `EggPools.pickSpecies`
  accepts a union of tiers (`rare|uncommon`).


## [0.12.2] - 2026-06-10

### Changed
- **Gacha crate tuning pass.**
  - **Ultra:** replaced the Truth/Ideals Bottles with **Lugia (Vortex Stone)**; the
    five legendary **pedestals** (Mew/Lati/Raikou/Suicune/Entei) are **disabled
    (weight 0) for now**.
  - **Rare:** added the **Articuno/Zapdos/Moltres urns**, a Mega Showdown
    **Keystone** + raw **Mega Stone**, and **5× random IV candy**; curated held
    items into **Competitive / Breeding / EV-Training** random pools; moved junk
    (Quick/Ultra Balls, PP Up) out to common; removed Exp Share/Leftovers from rare;
    Rare Candy buffed to 2.
  - **Common:** egg weight cut 38% → 25%; removed the useless Exp Candy S; added a
    **Random Held Item** split into a **Type-Boost pool** (type-boost items + Muscle
    Band/Wise Glasses/Leftovers/Black Sludge) and an **Evolution-Item pool**;
    Quick Balls moved here.

### Fixed
- **"Random" Evolution Stone always gave a Fire Stone**, and **"Random" EV Vitamin
  always gave HP Up** — both were stored as single items. They now roll genuinely at
  random (10 stones / 6 vitamins).

## [0.12.1] - 2026-06-10

### Added
- **One-time welcome key grant** — every player receives 1 Rare Key + 1 Pokémon Key
  on their next login, exactly once ever. Idempotent by design: the grant flag is
  persisted before any item is handed out, so a full inventory, crash, or duplicate
  login can never cause a second grant.

### Changed
- **Paradox Pokémon now count as legendaries in ranked.** They count toward the
  `maxLegendaries` team cap (Cobblemon's `isLegendary()` excludes them despite many
  being Ubers-tier), and the team-select GUI tags them `[PARADOX]`.

## [0.12.0] - 2026-06-10

### Added
- **Pokémon gacha crate** — a new egg-only crate tier: 35% common / 45% uncommon /
  15% rare / 3.5% shiny / 1.5% ultra-rare egg. New Pokémon Key (Turtle Egg).
  Place the crate with `/gacha admin setcrate pokemon`; the key is admin-granted.
- **Paradox Pokémon** (all 20) added to the `ultra_rare` gacha egg pool, and
  **charcadet** added to the `rare` pool.

### Changed
- **Gacha eggs grant the Hidden Ability automatically** when the rolled species is
  flagged as an HA mon — no separate "HA-only" egg type.
- **Ultra crate reworked** — LegendaryMonuments summon items (Azure Flute/Arceus
  jackpot, golem keys, stones/bottles, whistles, Red Chain), legendary pedestals
  (Mew/Raikou/Suicune/Entei/Lati), Griseous Orb, Proof of Conquest U/A/M.
- **Common & rare crates rebalanced** — trimmed filler (balls/potions/revives),
  boosted Pokémon eggs and useful items. Premium starters (Snivy, Litten,
  Sprigatito, Grookey, Oshawott) moved from the uncommon to the rare egg pool.

### Fixed
- **EXP on lost PvE battles** — you now keep the EXP for the enemy Pokémon you
  defeated even when you lose the battle (trainers, gyms, E4, and wild), matching
  modern-gen behavior. Previously a loss granted nothing, even on KO'd mons.

## [0.11.0] - 2026-06-09

### Added
- **Challenge-gym spawn commands for all 18 gyms.** `spawn_<N>_challenge` /
  `delete_<N>_challenge` now exist for gyms 11–18 (previously only 1–10), plus
  `spawn_all_challenge` / `delete_all_challenge`. `list` shows real leader names
  and challenge rows for 11–18; `help` lists the new commands. Challenge mob +
  loot tables added for 11–18 so they behave exactly like 1–10.
- **Poison gym restored — Koga (gym 13).** Gym 13 was a duplicate Electric gym
  (Lt. Surge); it is now Koga / Poison with ShepskyDad's Poison team (Scolipede,
  Crobat, Salazzle, Galarian Slowking, Revavroom, Overqwil), wearing RCT's
  `leader_koga_01a2` ninja sprite. Restores full 18-type coverage — Poison was
  lost when gym 6 was swapped Roxie→Volkner (Electric). All other 14 non-excluded
  challenge teams were verified an exact match to ShepskyDad's lists.

### Changed
- **Gym files renamed by type, not leader.** `gym_NN_<leader>` → `gym_NN_<type>`
  (e.g. `gym_01_ground`, `gym_06_electric`) for gyms 1–18, across the server
  datapack and the cobblemon-npc client-skin jar, with every reference updated
  (spawn/delete functions, advancements, battle-tower roster, `ops/` generators).
  Leader display names are unchanged; gyms 19–24 keep their leader names.
- **Smaller biomes.** Terralith `xz_scale` 0.1 → 0.18 (affects newly-generated
  chunks only).
- **Battle tower: healing resets the run.** Using a healing machine mid-run now
  sends the player back to floor 1 (previously the run continued and only the key
  was voided).

### Fixed
- **Challenge gyms now cap the player at L50.** Challenge spawns carry a flat
  `level_cap.50` tag, and `GymBattleGate` applies it without bypassing the
  "beat the mainline gym first" prereq. Previously challenge gyms used the gym
  progression formula (e.g. gym 1 capped the player at L20 against an L50 team).
- **Battle tower floor order is force-battle-proof.** Floor order is now enforced
  at the battle-start choke point (not just on right-click), so teleporting to a
  higher floor or an on-sight force-battle can't skip floor 1.

## [0.10.6] - 2026-06-08

### Added
- **`server-no-arc-phone` datapack — Arc Phone is no longer craftable.** Overrides
  `legendarymonuments:arc_phone_recipe` with a `neoforge:false` condition so the
  recipe never registers (gone from the crafting table, recipe book, and JEI).
  Server-side only via the world datapack overlay — the Legendary Monuments jar is
  untouched. Existing Arc Phones and `/give` are unaffected; this blocks crafting
  only, not other potential sources (loot/structure rewards).

## [0.10.5] - 2026-06-08

### Changed
- **Challenge gyms now play at softmax temperature 0.5** (`ai.data.temperature`
  on all 18 `gym_*_challenge` trainers, previously unset → 0.0). T=0 was sharp
  maximin; a mild 0.5 keeps most of that strength while shaving off the rare
  cautious-stall draws against casual lines — a "challenging but fair" feel.
  Informed by offline self-play tuning (gym MCTS vs greedy/optimal players).

### Removed
- **AI-test gyms + temperature A/B-test datapack** (`server-gym-ai-test`) and its
  generators (`ops/gen_aitest_gyms.py`, `ops/gen_gym_ai_test_datapack.py`). The
  temperature sweep is done; the test gyms and `aitest`/temp-test spawn functions
  are no longer needed on the server. NOTE: the deploy rsyncs datapacks
  non-destructively, so this datapack must also be removed from each live world's
  `datapacks/` directory (and any spawned aitest trainers killed) — it won't be
  deleted by the deploy alone.

## [0.10.4] - 2026-06-08

### Fixed
- **AI-test gym level cap now applies to every leader, not just the first two.**
  `rctmod trainer summon_persistent` ignores the command's `positioned` context
  and spawns the trainer at the player's feet, but the spawn functions tagged via
  a selector with `distance=..5` measured from each leader's intended `^offset`
  slot. Only the leaders at `^0`/`^3` fell inside the radius — every leader past
  `^3` (Blaine T2, all Byrons, challenge Cheren, etc.) was never tagged
  `cobblemon_bridge.level_cap.50`, so `GymBattleAdjustHook` left the challenger's
  party at full level. Each trainer is now identified by its unique `TrainerId`
  at `@s` (no distance clamp), tagged there, then `tp`'d out to its row slot.

### Changed
- `ops/gen_aitest_gyms.py` now emits the `level_cap` tag itself (was hand-patched
  into the generated functions before) and preserves hand-tuned `ai.data` (the
  per-leader softmax `temperature` sweep) across regens, so re-running the
  generator no longer wipes the tuning or drops the cap.

## [0.10.3] - 2026-06-07

### Fixed
- **Gym losses no longer fully heal the player's team.** The 0.10.0 level cap
  used Cobblemon's `adjustLevel`, which clones the player team — so battle damage
  hit the throwaway clone and your real Pokémon came out untouched (full HP win
  or lose). Replaced with a flat down-level via cobblemon-bridge's crash-safe
  `GymBattleAdjustHook` (mutates the real Pokémon's level + NBT-restores it; no
  clone → damage persists).

### Changed
- **Gym player level cap now a flat `level_cap` tag.** New
  `cobblemon_bridge.level_cap.<N>` entity tag applies a flat cap via the same
  crash-safe down-level the formula gyms use (vs the `gym_id` progression
  formula). The pe AI-test gyms are tagged `level_cap.50`. Removed the old
  clone-based cap (`PokeEngineAIConfig.levelCap` + `BattleManagerMixin`).

## [0.10.2] - 2026-06-07

### Fixed
- **Gym AI no longer freezes the server thread.** The bridge call (poke-engine
  MCTS, ~1-2s) ran synchronously inside `AIBattleActor.onChoiceRequested()` on
  the server thread, so every AI turn stalled the whole server (~2s "Can't keep
  up", dropped battle packets → the player's move menu sometimes failed to render
  mid-battle). PokeEngineAI choices now compute on a worker thread and submit via
  `setActionResponses` back on the server thread — the battle waits for the
  response like it does for a human player. Multiple battles also search in
  parallel now instead of serializing on the main thread.

### Changed
- **Gym AI search time 2s → 3s.** Now that the search runs off-thread it no
  longer costs server TPS, so the bridge gets a longer think budget for stronger
  play. Default in `BridgeConfig` is 3000ms; the per-server runtime config
  (`config/cobblemon-poke-ai.json`) carries the live value.

## [0.10.1] - 2026-06-07

### Fixed
- **Gym AI softlock when switching to formed Pokémon.** The 0.10.0 form fixes
  made gym mons spawn correctly formed (Arcanine-Hisui, Rotom-Heat, …), but the
  mod matched the bridge's form-qualified switch target (`arcaninehisui`,
  `rotomheat`) against bare `species.name` (`arcanine`, `rotom`) — so every
  switch to a formed mon failed to resolve, fell back to StrongBattleAI (which
  then NPE'd), and on a forced switch could hang the battle ("battle" button
  vanishes). `parseChoice` now matches the Pokémon's form-aware `showdownId()`.

### Added
- **Temperature A/B test trainers.** `server:aitest/spawn_temp_test` spawns
  Blaine and Byron at `temperature` 0 / 1 / 2 (level cap 50) side by side for
  tuning. Cleanup via `server:aitest/cleanup`.

## [0.10.0] - 2026-06-07

### Added
- **Gym AI punishes greedy play (softmax-opponent search).** poke-engine's MCTS
  now samples the player's move from a softmax over its value estimates at a
  per-gym `temperature` (trainer `ai.data.temperature`), so the AI plans a best
  response to a fallible opponent — setting up and pressing advantages instead
  of switch-cycling when it reads a position as lost. `temperature: 0` keeps the
  original perfect-opponent behaviour (Elite 4). The bridge ships a patched
  poke-engine (hspahic-cs/poke-engine fork, gen9, tera-free) plus per-pick
  option logging. aitest gyms 1-18 enabled with a temperature tier
  (2.0 / 1.5 / 1.0 by gym range); E4 / champion left perfect.
- **Per-gym player level cap.** Trainer `ai.data.levelCap` (e.g. 50) clamps the
  player's team to that level for the battle via a mixin into RCT's battle build
  — over-leveled teams are pulled down, under-leveled up. Enabled on aitest gyms
  1-18 (challenge gyms); omitted for the Elite 4.

### Fixed
- **Gym battle crash ("A battle error has occurred").** The pe-AI's
  StrongBattleAI fallback NPE'd during forced-switch upkeep and killed the whole
  battle; the fallback is now crash-safe (returns a guaranteed-legal action and
  logs why it fell back).
- **Wrong Pokemon forms on gym teams.** 17 trainers were missing form aspects
  (Rotom `*-appliance`, Alolan Ninetales/Muk, Galarian Weezing, Hisuian
  Electrode/Zoroark/Braviary), so they spawned base forms with mismatched
  movesets/typing.

## [0.9.2] - 2026-06-07

### Changed
- **Xaero's Minimap `25.3.10` → `25.3.13`** and **Xaero's World Map
  `1.40.11` → `1.40.16`** — routine upstream updates.

## [0.9.1] - 2026-06-06

### Fixed
- **Prod crash loop: MineColonies stuck-citizen pathing crash.** A stuck
  colonist's `PathingStuckHandler.tryUnstuck` read a path node past the end
  of a 1-node path (`AIOOBE: Index 1 out of bounds for length 1`) on the
  server thread — "Ticking entity" crash, hung save, watchdog kill, repeat
  (prod, 2026-06-07 01:54 + 02:13 UTC). Two-layer fix:
  - MineColonies `1.1.1301` → `1.1.1327` (includes upstream's partial
    bounds-check fe40f38, which only guards the negative side).
  - `PathingStuckHandlerMixin` in cobblemon-bridge wraps `checkStuck` and
    suppresses out-of-bounds path reads (logs + skips the tick) — covers
    the past-the-end variant upstream still misses. Soft mixin (`require=0`):
    no-ops if MineColonies is absent or renames the class.

## [0.9.0] - 2026-06-05

### Added
- **Daily Battle Tower** (cobblemon-bridge). The three-floor building at spawn
  now hosts a rotating daily gauntlet: every server-local midnight, three of
  the 18 challenge-mode gym leaders are picked (deterministic per day) and
  summoned at op-configured floor positions. Beat all three in order
  (1→2→3) without using a healing machine for a **Rare Key** — once per
  player per day, unlimited retries until midnight. Party is locked for the
  run (PC swaps fail it); items are allowed. Battles are flat L50 via
  `adjust_level.50`. Tower wins still count toward the one-time
  `beat_gym_N_challenge` advancements (shared trainer ids — by design).
  Setup: `/tower setfloor 1..3` standing at each leader spot, then
  `/tower rotate`; `/tower status` shows the day's lineup. Teleports move
  players through the tower: winning a floor warps you up to the next
  floor's spot, losing warps you down to floor 1, and clearing floor 3
  warps you to `/tower setreturn` (default: floor 1).
- **Gym-win return teleport.** Beating gym leaders 1–10 (mainline or
  challenge) teleports the player to the `/gymreturn set` point ~1s after
  the battle ends — every win, not just the first. Opt-in: unset = no
  teleport. Tower fights against the same leaders are excluded (the tower
  handles its own run-end teleport), as are gyms 11+ / E4 / Champion.

## [0.8.2] - 2026-06-05

### Added
- **Challenge-mode gym rewards for gyms 11–18.** Each challenge gym now has
  its own advancement (`server:beat_gym_N_challenge`, triggered by RCT's
  `defeat_count` on the exact `gym_NN_*_challenge` trainer id) and reward
  function. First-time challenge win per leader grants a Rare Key +
  `$150 × N` via `AdvancementHook` — same plumbing as gyms 1–10, no mod
  changes needed. Completable in any order. E4/Champion (19–24)
  intentionally have no challenge variants.

## [0.8.1] - 2026-06-05

### Fixed
- **Trainer skins actually reach clients.** 0.8.0 embedded them in
  `cobblemon-poke-ai`, which is server-only and never ships in the client
  mrpack. They now live in `cobblemon-npc` (both sides), together with
  client-side copies of the trainer data RCT needs to bind id→texture.
- **Ice battles crashed the bridge**: Cobblemon emits the pre-gen9
  `|-weather|Snow`; foul-play only knows `Snowscape`. Translated in the
  bridge's protocol normalization.

### Changed
- **AI-test spawns come in chunks of 3** (`spawn_1`…`spawn_8`, each with a
  next-chunk chat hint) instead of one 24-leader row; `cleanup` unchanged.

## [0.8.0] - 2026-06-05

### Added
- **Perfect-information gym AI.** `cobblemon-poke-ai` packs the opposing
  side's team (Cobblemon's `BattleRegistry.packTeam()`) with every bridge
  request; the bridge overlays ground truth onto the log-replayed battle and
  runs a single deep MCTS — no more Smogon set-guessing, which collapsed on
  non-meta teams. Anti-defeatism move selection, root-parallel search batches,
  expectiminimax alternative, per-turn JSONL battle logs + offline `replay.py`.
- **All gym leaders run the `pe` AI.** Base rosters unchanged; hard-mode
  `_challenge` teams for all 18 type gyms now mirror ShepskyDad's Perfect Gym
  Leaders at L50 (generated via `ops/gen_challenge_teams.py`, validated
  against engine data). E4/Champion teams untouched.
- **Trainer skins ship inside the mod jar.** RCT resolves skins client-side
  by trainer-ID filename; all 66 custom trainers (gyms + AI-test row) now
  carry textures automatically — no resource pack to install.
- **AI-test datapack: `spawn_all_pe`.** One command spawns all 24 leaders
  (pe variants, generated by `ops/gen_aitest_gyms.py`) for beta sweeps.
- **Bridge as a cluster service.** Dockerfile builds the bridge with
  poke-engine compiled *without* the terastallization feature; deployed as a
  2-replica StatefulSet on the gemini cluster (manifests in gemini-server).
  Authored config points the mod at the cluster LB.

### Fixed
- **Gym AI could only ever switch**: `MoveActionResponse` needs a `targetPnx`
  even in singles — every attack was rejected and the turn passed.
- **Phantom mons + L100 levels**: Cobblemon's Showdown fork injects mon UUIDs
  into protocol details/nicknames, breaking foul-play's level parsing and
  request↔protocol matching; plus the stateless rebuild orphaned the
  currently-active mon during log replay (the lead fought as a moveless ghost).
- **Pivot-KO softlock**: `forceSwitch` was ignored, so Volt Switch/Teleport
  KOs made the AI return "pass" and the battle hung with no player UI.
- **Tera disabled end-to-end**: server runs tera-free; `-tera` search options
  filtered (and absent entirely in the cluster engine build).

## [0.7.67] - 2026-06-05

### Changed
- **TM vendor now sells TRs.** All 631 `simpletms:tm_<move>` entries in
  `cobblemon-market`'s `items.json` swapped to `simpletms:tr_<move>` (every TM
  has a corresponding TR in SimpleTMs 2.3.3 — verified 1:1 coverage). Pricing,
  stock, elasticity, and `vendorTag` (`tm_grass`, `tm_fire`, etc.) preserved
  so the existing per-type vendor NPCs still scope to the same item sets. The
  vendor itself is still tagged "tm_*" in NPC metadata — only the items it
  hands out are now reusable TRs instead of single-use TMs.

## [0.7.66] - 2026-06-04

### Fixed
- **`cobblemon-poke-ai`: force HTTP/1.1 on bridge requests.** Java HttpClient
  defaults to HTTP/2 (h2c upgrade), which uvicorn rejects, sending the body
  empty and the bridge returning 422. Manifested in-game as the gym AI
  infinitely switching out (every turn falling back to StrongBattleAI).

## [0.7.65] - 2026-06-04

### Added
- **Tier 1 gym AI: poke-engine bridge.** New `cobblemon-poke-ai` mod registers
  AI type `pe` via rctapi's JTO. Trainer JSONs that opt in (`"ai": {"type": "pe"}`)
  route move selection to a local FastAPI service (`ops/poke-engine-bridge/`)
  that wraps foul-play's parser + poke-engine MCTS. Stateless: mod sends the
  full Showdown event log each turn; bridge rebuilds `Battle` from scratch
  per call so workers run independently. Bridge listens on 127.0.0.1:8642
  with 4 uvicorn workers (configurable in `/etc/default/poke-engine-bridge`).
  Both cobblemon-prod and cobblemon-dev share one bridge instance.
  - On any bridge error (timeout, connection refused, unparseable response),
    the mod falls back to `StrongBattleAI(skill=5)` so battles keep moving.
  - First-time VM install: `sudo bash ops/poke-engine-bridge/install.sh` (rustup
    + venv + poke-engine 0.0.46 build, ~5 min). After that, deploys via the
    new `deploy-bridge.yml` workflow.

### Changed
- **`server-gym-ai-test` datapack: rb-vs-pe head-to-head.** Refactored from
  three separate A/B/C variants (rb / cbl / sd5) to a paired rb-vs-pe shape.
  Each per-leader spawn (`/function server:aitest/spawn_sabrina`,
  `/spawn_surge`, `/spawn_blaine`) drops both variants side-by-side so the
  play-tester can fight both in one session and compare directly. Trainers
  are labeled `AI Test [rb]: Sabrina` and `AI Test [pe]: Sabrina`.

## [0.7.64] - 2026-06-04

### Changed
- **Comfortable income quest reward → Rare Key** (was 1 Master Ball). Granted directly
  from `reach_income_10000.mcfunction` via `gacha grant @s rare 1`; the shared
  `cq_reward_key_rare_1` tag doesn't work for non-gym quests since 0.7.63 moved gym
  keys to `AdvancementHook` and stripped the `_finalize` grant lines.
- **HUD: drop gyms 11-19.** The rotating gym slots are branch content (extra keys),
  not mainline progression. After beating gym 10 the HUD jumps straight to E4 1.
  Rotating gyms still appear in `/quests list` under "Rotating Gyms".
- **HUD: E4 quest text now mentions the consecutive requirement.**
  - E4 1: "Defeat Elite Four 1: Lorelei"
  - E4 2: "Beat E4 2: Cynthia — consecutively after E4 1"
  - E4 3: "Beat E4 3: Agatha — fighting E4 1-3 consecutively"
  - E4 4: "Beat E4 4: Lance — fighting all four trainers consecutively"
- **`/quests list` reward labels resynced.** `reach_income_10000` now says "Rare Key"
  and `reach_pokedex_100` now says "PokéNav" (was "1 Master Ball + Ultra Key").
- **Income quest title drift fixed.** `reach_income_10000` tellraw said "Tycoon"
  (the title of the $100k quest, not the $10k one); now correctly says "Comfortable".
  `reach_income_100000` said "Mogul" — now correctly "Tycoon" to match the
  advancement title.

### Removed
- **Duplicate `defeat_elite_four` advancement + reward function.** Beating Lance
  (`beat_gym_23`) already requires all four E4 trainers thanks to 0.7.63's
  `E4GauntletHook` (dimension leash + party stability), so the AND-of-four
  advancement was just firing in parallel with `beat_gym_23`, double-toasting and
  double-paying. `beat_gym_24` re-parented from `defeat_elite_four` back to
  `beat_gym_23`. The flat $5,000 bounty that used to live on `defeat_elite_four`
  is now paid on Champion completion (`beat_gym_24`) instead — Champion bounty
  goes from $150 × 24 = $3,600 to a flat $5,000. The Master Ball that used to
  come with `defeat_elite_four` is dropped (Champion still gives Ultra Key).
- **`_finalize.mcfunction` `cq_reward_item_elite_four` block** — tag-removal kept
  as a safety net for any in-flight tags, but no item dispatch.

## [0.7.63] - 2026-06-03

### Fixed
- **Root cause of broken gym keys / Centurion PokéNav: wrong cobblenav item id.**
  PR #129 added `give @s cobblenav:pokenav_item 1` to `_finalize.mcfunction`,
  but `pokenav_item` isn't a concrete item — Cobblenav only registers colored
  variants (`pokenav_item_red`, `pokenav_item_yellow`, etc.). Brigadier rejected
  the line at function-load time and unloaded `_finalize.mcfunction` entirely,
  silently breaking every `schedule function .../_finalize` dispatch. Symptom:
  chat fires + cash arrives (Kotlin-side) but gym keys, quest eggs, master
  balls, leaf stones never show up. Fixed by switching to `pokenav_item_red`.
- **Gym keys via Kotlin (defense in depth).** `AdvancementHook` now grants the
  gym key on `server:beat_gym_N` / `_challenge`: ultra for gym 10/19/23/24,
  rare for the rest. Runs `/gacha grant <player> <tier> 1` via
  `performPrefixedCommand` with an op-4 source. Gym keys no longer depend on
  `_finalize` parsing cleanly. `_finalize.mcfunction`'s `cq_reward_key_*`
  execute lines stripped to prevent double-grant; tag-removal kept.
- **Centurion PokéNav grant via Kotlin.** `PokedexProgressHook` resolves
  `cobblenav:pokenav_item_red` via `BuiltInRegistries`, adds to inventory, drops
  at feet on overflow. Idempotent per-player via
  `cobblemon_bridge:centurion_pokenav_awarded`. Backfills players who hit 100
  caught before the reward changed (any pokédex update triggers the check).
- **Crate / gacha eggs no longer tagged as bred.** `PokemonEggMixin` injects at
  Cobreeding's `inventoryTick → hatchEgg` call site, reads
  `custom_data.cobblemongacha_tier`, and tells `BredTagHook` to skip the bred
  tag for that hatch. Restores tradeability of `/gacha giveegg` outputs (gacha
  pulls + quest-reward eggs). The mixin commit was lost in PR #129's
  squash-merge.

### Changed
- **Elite Four gauntlet: dimension leash + party stability.** Snapshot dimension
  and Pokémon UUIDs at E4 1 interact. Any `PlayerChangedDimensionEvent` out of
  that dimension (portal, `/home`, `/tpa`, death-respawn) fails the gauntlet.
  Each subsequent E4 battle start re-checks the party against the snapshot;
  PC swaps, deposits, or releases cancel the battle and fail the gauntlet.
  Items and healing are untouched — only the party roster is locked.

## [0.7.62] - 2026-06-03

### Added
- **Gym AI A/B/C test datapack (`server-gym-ai-test`).** Dev-only datapack with
  9 trainer JSONs (Sabrina / Surge / Blaine × 3 AI variants), all sharing each
  leader's hardmode team capped at L50 so the AI is the only variable. Variants:
  A=`rb` (RunBunAI, current prod), B=`cbl` (StrongBattleAI), C=`sd5`
  (SelfdotGen5AI). Spawn with `/function server:aitest/spawn_a` (or `_b`, `_c`),
  remove with `/function server:aitest/cleanup`. Trainers are labeled
  `AI Test [A]: Sabrina` etc. so a play-tester sees only the letter, not the
  AI mapping. Source-of-truth generator: `ops/gen_gym_ai_test_datapack.py`.

## [0.7.61] - 2026-06-03

### Fixed
- **Monument lock: handler intercepts player-owned legendaries.** Sending out a
  legendary from your party while standing in an LM structure made the handler
  treat it as a fresh wild spawn — it canceled the entity and broke the battle
  UI ("not your turn to act"). Now bails on `!pokemon.isWild()`.
- **Monument lock: chunk-reload re-spawn loop.** A re-spawned legendary saves
  to disk; when its chunk reloads, `EntityJoinLevel` fires again, finds the
  pedestal already drained (no match in the scan), and the old code re-ran the
  spawn path with `pedestal=null`, drifting the entity each cycle. Now bails
  when `findPedestal` returns null — the only valid case for our handler is a
  fresh activation with a real pedestal.
- **Monument lock: battle UI renders without moves.** `PokemonProperties.createEntity`
  doesn't initialize the moveset (same gap as LM's spawn path). Re-introduced
  the 0.7.55 `initializeMoveset()` call, now on our re-spawned entity.

## [0.7.60] - 2026-06-03

### Changed
- **No-op deploy verification.** Comment-only nudge in `LegendaryMonumentLock.kt`
  to confirm the CHANGELOG-triggered dev deploy fires end-to-end.

## [0.7.59] - 2026-06-02

### Changed
- **Monument lock: world is the source of truth — drop in-memory spent-altars set
  and persistence.** The persisted `spent_altars.json` was causing false positives:
  stale entries from earlier versions blocked fresh activations even after
  `/monument admin reset`, and `findPedestal`'s ±16-block scan picked up *any*
  drained altar in the radius and matched it as "spent". Now the only check is
  "is a legendary already active?" — a pedestal block exists or it doesn't, and
  drained pedestals are crying obsidian (different namespace) so the scan skips
  them automatically. `/monument admin reset` now clears just the active-legendary
  slot; to restore a drained altar, use `/setblock` to put the original pedestal
  back.

## [0.7.58] - 2026-06-02

### Changed
- **Monument lock: drain on activation, re-spawn via Cobblemon.** When LM spawns
  a legendary inside an LM structure, we now cancel its entity, drain the
  activation block to crying obsidian (permanently spending the altar), and
  re-spawn the same species/level via `PokemonProperties.createEntity`. This
  sidesteps LM's incomplete spawn pipeline (no moveset init, no client sync) —
  the re-spawned entity is a normal wild Pokemon with proper battle UI, moves,
  and despawn behavior. Outcome (catch / flee / loss / disconnect) no longer
  matters: the altar is already spent on activation, so there's no post-battle
  bookkeeping. Removes the `BATTLE_FLED` / `BATTLE_VICTORY` / `initializeMoveset`
  band-aids from 0.7.54–0.7.57.

## [0.7.57] - 2026-06-03

### Fixed
- **Monument lock: disconnect mid-battle leaves active slot permanently blocked.**
  `BATTLE_FLED` doesn't fire on player disconnect — the active slot stayed set, blocking
  all future legendary spawns and causing a crash when the player reconnected and tried
  to fight again. Now also subscribes to `BATTLE_VICTORY` (covers disconnect/player loss)
  so the slot is cleared on any battle end.

## [0.7.56] - 2026-06-03

### Fixed
- **Monument lock: Kyurem (and other large-cave) pedestal returns null.** The ±4 block
  scan radius was too small — Kyurem Cave spawns the legendary far from the activation
  pedestal. Widened to ±16 XZ / -24..+4 Y.
- **Server crash (ConcurrentModificationException) when battling LM legendary.** The
  previous bounding-box drain replaced thousands of blocks in one tick, causing
  `DistanceManager.runAllUpdates` to fail with CME. Fixed in 0.7.52 (single pedestal
  drain); this version just documents it.

### Removed
- **ImmediatelyFast** client mod dropped — confirmed to break Cobblemon battle UI
  (moves/flee/switch buttons not rendering).

## [0.7.55] - 2026-06-03

### Fixed
- **LM legendary battle UI missing moves.** LM spawns legendaries via `PokemonProperties`
  without going through Cobblemon's full spawn pipeline, so `initializeMoveset()` is never
  called. The battle screen renders but shows no moves/flee/switch. Now calls
  `pokemon.initializeMoveset()` at join time if the moveset is empty.

## [0.7.54] - 2026-06-03

### Fixed
- **Monument lock: "another legendary already active" after fleeing battle.** When a player
  flees a battle with an LM legendary, the entity stays alive in the world so
  `onEntityLeaveLevel` never fires and `activeLmPokemon` was never cleared. Every subsequent
  altar activation was blocked. Now subscribes to `BATTLE_FLED` to clear the blocking slot
  while keeping the tracked pokemon reference so the altar still drains when the entity
  eventually leaves the world.

## [0.7.53] - 2026-06-03

### Fixed
- **Monument drain: lock blocks not recognized as activation blocks.** LM uses `*_lock`
  blocks (Regice, Regirock, Registeel, Regigigas, Victini, temple) and other spawner blocks
  (`pokemon_trial_spawner`, `sanctuary_block`, `hoopa_boss_summon`) in addition to
  `*_pedestal`. `findPedestal` now matches all of them via `isActivationBlock()`.

## [0.7.52] - 2026-06-03

### Fixed
- **Monument drain: adjacent pedestals (Dialga/Palkia, Zekrom/Reshiram) both draining.**
  Previous bounding-box scan covered the entire structure — Spear Pillar is one structure
  whose box contains both pedestals. Switched to scanning ±4 XZ / -8..+1 Y around the
  legendary's spawn position for the nearest `legendarymonuments:*_pedestal` block, then
  replacing only that single block. Pedestals 10 blocks apart are never confused.

## [0.7.51] - 2026-06-03

### Fixed
- **Monument lock: global lock was wrong design.** The single `locked` flag shut down every
  altar in the world after one catch. Replaced with a per-altar `spentAltars` set keyed by
  structure bounding-box anchor — each altar is independently one-shot; all others stay open.
- **Monument lock: adjacent altars both draining.** Fixed-radius scan (±48 blocks) overlapped
  adjacent structures (e.g. Dialga/Palkia altars). Drain now iterates the specific structure's
  own bounding box — adjacent structures are untouched.
- **Monument lock: "message fires but nothing spawns" on spent altars.** When a spent altar
  was triggered, the entity was cancelled silently (debug log only) while LM had already
  broadcast its own message. Now sends an explicit player-facing "this altar is spent" message.
- **Monument lock: persist spent altars as JSON.** Replaces the single `monument_lock.flag`
  sentinel with `spent_altars.json` containing each spent anchor position.

## [0.7.50] - 2026-06-03

### Fixed
- **Monument lock: capture mis-classified as flee.** The previous `activeLmPokemon !== active`
  guard in `onEntityLeaveLevel` assumed `POKEMON_CAPTURED` would null it out first, but
  Cobblemon's event sequences after the NeoForge scheduled task. Switched to `active.isWild()`:
  false means already in a player's party (caught).

### Changed
- **Monument lock: altar is now one-shot regardless of outcome.** Previously the monument
  reset if the legendary fled, allowing repeated altar activations by luring the legendary
  away and fleeing. Both catch and flee now permanently lock and drain the altar.
- **Monument lock: removed dead `POKEMON_CAPTURED` subscription.** Both code paths now go
  through `onEntityLeaveLevel`; the separate Cobblemon event handler is gone.

## [0.7.49] - 2026-06-03

### Fixed
- **Monument lock: spawn detection completely broken.** LM spawns legendaries by
  directly constructing a `PokemonEntity` via `PokemonProperties` (not through
  Cobblemon's spawn pipeline), so `CobblemonEvents.POKEMON_ENTITY_SPAWN` never
  fires for them. Switched to NeoForge's `EntityJoinLevelEvent` which fires for
  all entity adds regardless of origin.

## [0.7.48] - 2026-06-02

### Fixed
- **Monument lock: spawn detection broken for tall structures.** `getAllStructuresAt`
  is a 3D bounding-box check — legendaries spawning at the apex of Bell Tower
  (y≈242) were above all structure piece boxes and slipped past undetected.
  Switched to `startsForStructure(ChunkPos, Predicate)` which checks only the
  2D chunk footprint and is Y-agnostic.
- **Monument lock: altar drain never reached ground level.** `drainAltar` scanned
  ±8 blocks around the entity's spawn position (y≈242), missing altar blocks at
  y≈125. Added `findStructureAnchor` to resolve the structure's bounding-box
  ground-level centre, then scans the full world Y range (−64..320) within
  ±48 XZ of that anchor.
- **Monument lock: duplicate region registration on world reload.** Added a
  one-shot `registered` guard on `LegendaryMonumentsTerraBlenderShim` so
  repeated `ServerAboutToStartEvent` fires can't stack duplicate TB region
  registrations.

### Changed
- **Cherry Plains biome size retuned.** Previous over-tightening (0.7.47) made
  biomes postage-stamp sized. Climate spans widened and region weight set to 2
  for a reasonable frequency without dominating the overworld.
- **Terralith biome scale increased.** New `server-terralith-biome-scale`
  datapack overrides `temperature`, `vegetation`, and `base_erosion` density
  functions with `xz_scale` 0.25 → 0.1, making Terralith biomes ~2.5× wider
  in newly generated chunks.

## [0.7.47] - 2026-06-02

### Added
- **Legendary Monument one-shot global lock.** Only one LM legendary may be
  alive in the world at a time. Catching it permanently locks the monument —
  no further legendaries will spawn from any `legendarymonuments:*` structure.
  Fleeing or despawning resets the monument so players can try again.
  On capture, all `legendarymonuments:*` blocks within 8 blocks of the spawn
  point are replaced with crying obsidian so players can see the monument has
  been spent. Lock persists across restarts (`monument_lock.flag`).
  Ops can reset via `/monument admin reset` or check state with
  `/monument admin status`.

## [0.7.46] - 2026-06-02

### Fixed
- **0.7.45 follow-up: don't load LM's class at all.** 0.7.45 stub-shipped
  `terrablender.api.TerraBlenderApi` to satisfy LM's missing supertype.
  That bricked the JVM with a JPMS split-package error
  (`Modules cobblemon_bridge and terrablender export package terrablender.api
  to module fabric_api`) — both modules can't export the same package
  under Java's module system. Dev wouldn't start.

  0.7.46 throws away the "load LM's class" approach and replicates
  what LM 7.8's `LegendaryMonumentsTerraBlender$1.addBiomes` would have
  done, in our own `LMCherryPlainsRegion` Kotlin class that extends
  `terrablender.api.Region` directly. Three climate parameter points
  (extracted from LM's bytecode), region weight 3, type OVERWORLD.

  We never reference LM's class symbolically, so no missing-supertype
  load failure and no stub interface to collide with.

  To compile a `Region` subclass without a binary dep on TerraBlender
  (which isn't on a Maven we can reach), this PR vendors a 6.8KB
  compile-only stubs jar at
  `custom-mods/cobblemon-bridge/libs/terrablender-api-stubs.jar`,
  containing just `Region`, `Regions`, `RegionType` extracted from the
  real TB jar. Stubs jar is `compileOnly` and never lands in the bridge
  runtime jar; at runtime the JVM resolves `terrablender.api.*` from
  the real TB jar in the modpack.

  Refresh that stubs jar on TerraBlender bumps:
  `jar xf TerraBlender-neoforge-X.Y.Z.jar terrablender/api && jar cf libs/terrablender-api-stubs.jar terrablender/api`.

  If LM 7.9+ retunes its climate spans, re-extract the floats from the
  new bytecode — this shim still registers the 7.8 values.

## [0.7.45] - 2026-06-02

### Fixed
- **0.7.44 follow-up: stub `terrablender.api.TerraBlenderApi`.** 0.7.44's
  shim threw `NoClassDefFoundError: terrablender/api/TerraBlenderApi` on
  every dev boot, before the reflective `Class.forName(...)` of LM's
  entrypoint could complete. Cause: NeoForge TerraBlender 4.1 doesn't
  ship that interface (TB on NeoForge uses a `@Mod`-class entrypoint,
  not a Fabric-Loader-iterated one). LM 7.8 was compiled against the
  Fabric-side TerraBlender that DID export it, so JVM resolution of
  LM's class fails when its supertype can't be found.

  Added a 3-line stub interface at the same FQN
  (`custom-mods/cobblemon-bridge/src/main/java/terrablender/api/TerraBlenderApi.java`).
  JVM now finds the supertype, LM's class loads, the rest of the shim
  reaches `onTerraBlenderInitialized()` which calls `Regions.register(...)`
  on NeoForge TB's real `Regions` class.

  Drop the stub the day NeoForge TerraBlender ships the interface again.

## [0.7.44] - 2026-06-02

### Fixed
- **LegendaryMonuments biomes finally generate.** LM 7.8 is a Fabric mod
  loaded via Sinytra Connector. Its TerraBlender region (the
  `legendarymonuments:cherry_plains` biome and its climate-parameter
  ranges) is registered through the Fabric `terrablender` entrypoint —
  which Connector does not pass through to NeoForge TerraBlender 4.1.
  Confirmed on dev: only the two vanilla TB regions
  (`minecraft:overworld`, `minecraft:nether`) appeared in
  `[terrablender/]: Registered region ...` startup lines; nothing from
  LM. Visible symptom: `/locate biome legendarymonuments:cherry_plains`
  reported "could not find ... within a reasonable distance" because the
  biome had no parameter footprint and never spawned.

  Fix is a tiny shim in `cobblemon-bridge`
  (`adapters/LegendaryMonumentsTerraBlenderShim.kt`) that subscribes to
  `ServerAboutToStartEvent` at `EventPriority.HIGHEST` (TB's own
  registration runs at `LOWEST`, so we land first) and reflectively
  invokes `github.jorgaomc.world.biome.LegendaryMonumentsTerraBlender#onTerraBlenderInitialized`
  — the exact entrypoint Connector skipped. LM's method already
  encodes its authored climate parameters, so we don't reimplement
  any worldgen logic; we just fire the call. Reflection keeps LM off
  the compile classpath, gated by `ModList.isLoaded("legendarymonuments")`,
  so cobblemon-bridge stays usable without LM.

  Note: existing chunks were generated without this region and will not
  retroactively grow cherry_plains. The biome will only appear in
  newly explored wilderness.

  Terralith is also broken in a similar way (its biomes don't appear
  either) but that's a different mechanism — Terralith ships zero
  entrypoints and relies on TerraBlender allowlisting it via
  `terrablender.toml`. Out of scope for this release.

## [0.7.43] - 2026-06-01

### Changed (gameplay)
- **Ultra-rare bucket roll cut from 0.2 → 0.01** (~20× nerf vs vanilla
  Cobblemon, ~6.7× rarer than 0.7.38's hotfix value of 0.0667). The
  override now lives at `config/cobblemon/spawning/best-spawner-config.json`
  instead of the datapack-side `data/cobblemon/spawn_data/buckets.json`
  the hotfix used. Cobblemon's `BestSpawnerConfig.Companion.loadExternal()`
  reads from the *config* path (verified by disassembling
  `Cobblemon-neoforge-1.7.3+1.21.1.jar`), so the datapack-side override
  in 0.7.38 was a no-op — only the in-jar default of 0.2 was actually
  taking effect. `replaceWithNewVersion` is set to `false` so future
  Cobblemon updates won't silently restore the upstream value.

  The 0.7.38 datapack `data/cobblemon/spawn_data/buckets.json` file is
  removed (it never did anything).

- **LegendaryMonuments-covered species suppressed from wild spawning
  (weight=0).** LM v7.8 ships dedicated questlines (pedestals, shrines,
  treats, monument completion vouchers from cobblemon-gacha) for 46
  species across all 9 generations. Allowing them to also spawn
  randomly in the wild undermines the intended acquisition path. The
  44 species that have ATM spawn pools are now overridden to
  `weight: 0` (the other 2 — Ting-Lu and Virizion — have no ATM spawn
  entries, so no override is written). LM coverage list verified
  against in-game LM UI:

  | Gen | LM-covered |
  |---|---|
  | 1 | articuno, zapdos, moltres, mew |
  | 2 | raikou, entei, suicune, lugia, ho-oh, celebi |
  | 3 | regirock, regice, registeel, latias, latios |
  | 4 | regigigas, cresselia, darkrai, heatran, mesprit, azelf, uxie, dialga, palkia, giratina, arceus |
  | 5 | cobalion, terrakion, virizion, keldeo, reshiram, zekrom, kyurem, victini |
  | 6 | hoopa |
  | 7 | cosmog, meltan |
  | 8 | zacian, zamazenta, eternatus, regieleki, regidrago |
  | 9 | chien-pao, chi-yu, ting-lu, wo-chien |

- **Per-tier weights for the remaining 64 species** (legendaries +
  mythicals not in LM, all paradoxes, all UBs). Each species
  classified by peak Gen 6+ competitive tier (final-evolved-form
  basis — Cosmog mapped pre-LM-override; Kubfu → Urshifu; Poipole →
  Naganadel). Steep curve, 20× spread top to bottom:

  | Tier | Weight | Examples (post-LM filter) |
  |------|-------:|----------|
  | AG | 0.05 | Miraidon, Koraidon, Deoxys |
  | Ubers | 0.10 | Kyogre, Groudon, Rayquaza, Xerneas, Yveltal, Calyrex, Necrozma, Terapagos, Flutter Mane, Iron Bundle, Roaring Moon, Iron Valiant, Pheromosa |
  | OU | 0.30 | Tapus, Ogerpon, Therian Forces of Nature, Great Tusk, Iron Treads, Slither Wing, Iron Moth, most UBs |
  | UU+ | 1.00 | Glastrier, Phione, Zarude, Zeraora, Brute Bonnet, Iron Leaves, Scream Tail, Guzzlord |

  Replaces upstream AllTheMons weights (which range 0.05–11.5
  per-species and don't track competitive viability). Suicune
  (upstream 0.5) and Articuno (upstream 2.0) — both UU+ — now both
  spawn at the same 1.0; Heatran (upstream 11.5 across 3 entries)
  drops to 0.3.

  **Mythical floor:** mythicals are additionally capped at the Ubers
  weight regardless of competitive tier. Mew (OU), Jirachi (OU),
  Phione (UU+), etc. all spawn at 0.10 — game-defining mythicals
  shouldn't outpace Ubers-tier legendaries.

- **Paradox bucket promotion** stays from 0.7.38 (rare → ultra-rare).
  Without this, paradoxes would escape the bucket-roll nerf since
  upstream puts them in the `rare` bucket.

- **Biome filler removed.** 0.7.38 added 27 thematic species (Duskull
  in soul_sand, Cubone in nether_desert, Unown in end, etc.) into
  the ultra-rare bucket of 7 biomes to dilute legendary share to
  ~20% in legend-only biomes. With LM suppression in this release,
  most biomes' ultra-rare candidates are non-LM legendaries +
  paradoxes anyway, so the filler is no longer load-bearing. A few
  biomes (soul_sand, deep_dark) end up with very thin ultra-rare
  pools — players just need to roam more for legendaries from those
  biomes, which is consistent with the "ultra-rare = rare" intent.

- 108 ATM species covered: 44 LM-suppressed (weight=0), 64 at tier
  weights. `ops/gen_spawn_nerfs.py` is the source of truth — re-run
  after bumping AllTheMons or LegendaryMonuments to pick up new
  species (warns on unmapped species and on LM species without ATM
  spawn pools).

  Replaces `ops/gen_spawn_hotfix.py` from 0.7.38.

## [0.7.42] - 2026-06-01

### Fixed
- **`reach_income_250` (Pocket Change) and other income-threshold
  quests now award when the player's balance meets the threshold,
  regardless of how it got there.** Previously the check fired only
  when a specific sell deposit *crossed* the threshold
  (`balanceBefore < N && balanceAfter >= N`). Players who built up
  ¢250+ from trainer bounties before beating Gym 1 — the quest's
  prerequisite — never saw the award because subsequent sells stayed
  strictly above 250 (no crossing).

  Behavior change:
  - `QuestRewards.checkIncomeThresholds(player)` now re-evaluates
    every threshold against current balance and awards any reached.
    `awardQuest` is already idempotent (`progress.isDone` short-
    circuit), so the re-check is safe to call from anywhere.
  - Called on every sell deposit (existing path, simplified) AND
    on every login via `PlayerEvent.PlayerLoggedInEvent` (new). Login
    handles the "already had enough money when the quest unlocked"
    case retroactively.

  Same logic covers the `reach_income_1000 / _10000 / _100000` tiers.

### Fixed
- **Poké Healer quote now shows the real carrot price.** The
  cobblemon-carrots → cobblemon-market reflection bridge invoked
  `PricingEngine.buyPrice` as if it were a static method
  (`Method.invoke(null, …)`), but `PricingEngine` is a Kotlin
  `object` — `buyPrice` is bound to its `INSTANCE` singleton, not
  static. Every call NPE'd and the bridge fell back to the flat
  `carrotPrice` config (`= 5`) for the prompt's per-carrot price
  + total cost. Meanwhile the confirm-time charge went through the
  (working) TradeOps path at the live market price, so a player
  quoted "$5 × 10 = $50" actually got charged $80 if elasticity
  had pushed carrots to $8/each. Bridge now resolves
  `PricingEngine.INSTANCE` at startup and passes it as the receiver
  — prompt + charge use the same live price.

## [0.7.40] - 2026-06-01

### Fixed
- **Carrots now grow at normal speed everywhere, every season.** New
  datapack `server-crop-fertility` adds `minecraft:carrots` to Serene
  Seasons' `year_round_crops` tag. Carrots are seasonal infrastructure
  on this server — the starter quest chain (`evolve_exeggutor` →
  `ranch_carrot_farm`) hands the player a Pasture Block + bonemeal +
  Exeggutor and expects the carrots to actually grow, and the Poké
  Healer block consumes carrots per heal. Under SS defaults carrots
  were only fertile in spring + autumn (~48 of 96 in-game days);
  tropical biomes (savanna/desert/jungle/etc.) were even worse
  because SS treats those as permanent summer, leaving carrots
  out-of-season every day there.

  Other crops (wheat, potatoes, beets, pumpkins, melons) still follow
  the seasonal calendar — only carrots are pulled into the
  always-fertile pool.

## [0.7.39] - 2026-05-31

### Changed
- **Public chat is now global, not proximity-based.** NeoEssentials
  shipped with a `local` chat channel as the default (100-block
  radius); players typing in chat could only reach others within that
  range. The `chat.json` override pins
  `chat.channels.local.enabled: false` so the `global` channel
  becomes the effective default — all chat reaches all players.
  Staff channel still works via `/staff`.

  Shipping `modpack/server-overrides/config/neoessentials/chat.json`
  also pins the rest of the chat settings (formatting templates,
  mentions, badges, anti-spam) so future NeoEssentials updates can't
  silently revert them. Prod was migrated from the legacy monolithic
  `config.json` to NeoEssentials' split-file layout (via the in-game
  `/neoessentials config split` admin command) so both servers
  consume the same file shape.

## [0.7.38] - 2026-05-31

### Fixed
- **`reach_income_1000` (Founding Fortune) no longer promises a Master
  Ball for placing the supply camp.** The `► Next:` preview advertised
  "Reward: Poké Healer; 1 Master Ball" but the `join_colony` reward
  function only sets `cq_reward_item_pokehealer` — players placed the
  camp expecting a master ball, got just the healer. Preview now
  matches reality (Poké Healer only).
- Fixed stale `► Next:` preview on `ranch_carrot_farm` — was
  pointing at "Gym 2", actual next step is `first_pvp_win`.

### Changed (gameplay)
- **Ultra-rare spawn rate × 1/3.** New `server-spawn-nerfs` datapack
  overrides `data/cobblemon/spawn_data/buckets.json` to set the
  ultra-rare bucket weight from Cobblemon's default `0.2` to `0.0667`.
  Every ultra-rare encounter — legendary, mythical, paradox,
  ultra-beast, pseudo-legend, starter — fires ~3× less often. Other
  buckets renormalize at roll time so their effective % grows
  slightly to absorb the gap.
- **Paradoxes moved into the ultra-rare bucket.** AllTheMons ships
  paradoxes in the `rare` bucket; the datapack rewrites every paradox
  entry's `bucket` field to `ultra-rare` (17 species, weights
  unchanged from AllTheMons). Without this, paradoxes would have
  escaped the bucket slash above.
- **Filler entries in legendary-dominated biomes.** Seven biomes had
  their ultra-rare bucket composed entirely (or almost entirely) of
  legendaries with no non-competitive species to dilute. Added
  thematic filler so the competitive share lands at roughly 20% of
  the bucket in each:

  | Biome | Filler species |
  |---|---|
  | `nether/is_soul_sand` | Duskull, Dusclops, Dusknoir |
  | `nether/is_desert` | Cubone, Bramblin, Salandit, Ekans |
  | `is_deep_dark` | Golett, Spiritomb, Golurk, Mawile |
  | `is_end` | Unown, Gothita, Elgyem, Sigilyph |
  | `is_island` | Wattrel, Kilowattrel, Cramorant, Poltchageist |
  | `is_sky` | Chatot, Squawkabilly, Murkrow, Pidgey |
  | `is_peak` | Growlithe, Meditite, Medicham, Delibird |

  All picks come from species that already spawn in the same biome
  at common/uncommon/rare buckets — thematic by construction. New
  entries live in `data/server_spawn_filler/spawn_pool_world/`.

  Generator: `ops/gen_spawn_hotfix.py`. Re-run after bumping
  AllTheMons or Cobblemon.

### Changed
- **Exeggcute / Cobbleworkers chain promoted to mainline styling.**
  The four quests on the mainline arc between Gym 1 and Gym 2
  (`reach_income_250 → evolve_exeggutor → ranch_carrot_farm →
  first_pvp_win`) now use the same gold `[Mainline Quest Complete]`
  chat header + `frame: "challenge"` advancement tile +
  `announce_to_chat: true` as `defeat_elite_four`. Previously they
  were styled as side/task quests (`[Quest Complete]` green, `frame:
  "task"`, no chat broadcast), which made the whole
  Cobbleworkers-introduction arc visually invisible relative to the
  gym/E4 milestones it sits between.

## [0.7.37] - 2026-05-31

### Changed
- **Ranked PvP post-match output is two lines per player + no
  leaderboard.** Replaces the previous 4–14 line broadcast (winner +
  combined ELO line + auto-leaderboard top-N) with three lines: a
  header, the winner's ELO change in green, and the loser's in red.
  - Old: `[Ranked] Titan defeated SixthSense! / [Ranked] Titan: 1240 -> 1256 (+16) | SixthSense: 1190 -> 1174 (-16) / [Ranked] Leaderboard: / 1. ... / 2. ... / ...`
  - New:
    ```
    [Ranked] Titan defeated SixthSense!
    §aTitan: 1240 → 1256 (+16)§r
    §cSixthSense: 1190 → 1174 (−16)§r
    ```
  - Players can still pull the full leaderboard with
    `/ranked leaderboard` (already wired). Removed the auto-broadcast
    because end-of-match chat spam was eating screen space.
  - Added `signed()` helper for ELO deltas so a `−16` reads as a real
    minus (U+2212) and an ELO-floor `±0` is distinguishable from a
    positive change.

## [0.7.36] - 2026-05-31

### Fixed
- **Wild Pokémon defeats no longer say "for defeating trainer".** A
  prod log audit caught wild battles routing through `GymDefeatHook`'s
  trainer branch and emitting the trainer-bounty message. Root cause:
  `PokemonBattleActor` and `MultiPokemonBattleActor` both extend
  Cobblemon's `AIBattleActor` (`PokemonBattleActor` carries a
  `RandomBattleAI`), so the post-0.7.31 widened
  `is AIBattleActor` discriminator classified wild battles as trainer
  fights. Tightened to "AIBattleActor that is NOT a (Multi)PokemonBattleActor"
  and added a separate `payWildBounty` path that pays the same formula
  but emits "§7you found on the Pokémon" instead.
- Wild bounty stays on (the existing payout was unintended but the team
  decided to keep it). The change is message-only from a player POV
  unless they were watching closely.

## [0.7.35] - 2026-05-31

### Fixed
- **Ship `config/cobblemon/main.json` and `config/rctmod-server.toml`
  from the repo.** A prod audit surfaced four settings drifted back to
  Cobblemon / rctmod vanilla defaults — none of which the repo was
  managing, so a mod-config rewrite (or any path that recreated the
  file) silently reset them.

  Pinned values:

  | File | Key | Value | Why |
  |------|-----|-------|-----|
  | `cobblemon/main.json` | `healPercent` | `0.0` | Disable out-of-battle passive HP regen — players are meant to use the carrot/healer flow |
  | `cobblemon/main.json` | `defaultFaintTimer` | `2147483647` | Disable fainted-Pokémon auto-revive (Integer.MAX_VALUE ≈ never), forcing healer-block use |
  | `rctmod-server.toml` | `initialLevelCap` | `200` | Match the intended progression range; prod was at the rctmod default `15` |
  | `rctmod-server.toml` | `allowOverLeveling` | `true` | Permit player Pokémon to exceed the cap; prod default was `false` |

  Caveat: NeoForge/Cobblemon configs are whole-file writes. Pinning the
  full file means on Cobblemon updates that add new keys we have to
  re-baseline manually — otherwise the deploy keeps shipping the older
  default for any newly-added field. Tracking this in CHANGELOG so the
  next Cobblemon bump triggers a config rebase.

## [0.7.34] - 2026-05-30

### Fixed
- **Players returning from spawn (or any locked dim) to overworld /
  nether / end are no longer stuck in Adventure mode.** Three related
  holes in `WorldRulesHook`:

  1. `onPlayerLoggedOut` cleared the saved gamemode without restoring
     it first, so a player who logged out at spawn was persisted to
     NBT in ADVENTURE. On next login `applyLock` captured ADVENTURE
     as their "prior" gamemode; later `restoreLock` set them back to
     ADVENTURE on exit. Fix: logout now restores the saved survival
     mode before clearing the entry, so the player's saved NBT
     reflects survival.
  2. `restoreLock` early-returned when no saved entry existed,
     leaving the player in whatever locked-dim gamemode they had
     (covers paths where the player landed in ADVENTURE via a
     teleport that didn't fire `PlayerChangedDimensionEvent`, or
     first-ever joins straight into a locked dim). Fix: default to
     `GameType.SURVIVAL` when no saved entry — matches
     server.properties' `gamemode=survival` default.
  3. The `restoreLock` path only fires on the `locked → allowed`
     transition, so players already stuck in ADVENTURE in the
     overworld (legacy state from #1/#2) had no in-game recovery
     — non-ops can't `/gamemode` themselves. Added a
     `healStuckAdventure` pass on login and on every same-allowed-dim
     transition (overworld ↔ nether ↔ end portal hops) that snaps
     any non-op caught in ADVENTURE back to SURVIVAL.

## [0.7.33] - 2026-05-30

### Fixed
- **Exeggcute starter egg now actually lands in the player's inventory
  after `beat_wild_trainer`.** Both prior attempts called Cobbreeding
  directly: `givepokemonegg @s exeggcute …` (0.7.25–0.7.30) and then
  `cobbreeding egg give @s exeggcute` (0.7.31). Disassembly of
  `Cobbreeding-neoforge-2.2.1.jar` confirmed both are aliases for the
  same `EggGiveCommand` handler, gated identically at
  `PermissionLevel.CHEAT_COMMANDS_AND_COMMAND_BLOCKS`. Brigadier's
  `requires` clause silently drops the call when the source op-level
  isn't high enough — no chat error, no log line — and scheduled
  datapack functions run at op-2 (function-permission-level default).

  Fix routes the grant through `/gacha giveegg @s beginner`. Our
  gacha command is gated at op-2 (datapack-callable) and internally
  builds a fresh CommandSourceStack with `.withPermission(4)` before
  dispatching `/givepokemonegg` — the same source-escalation trick
  the regular gacha pull path uses. The `beginner` pool is a new
  single-species pool (`{exeggcute}`) injected by
  `EggPoolLoader.QUEST_CHAIN_POOLS` on every boot so existing
  deployments pick it up without admin action. `EggDefeatHook` gives
  it a 10-minute hatch timer so the player can keep moving through
  the onboarding chain.

## [0.7.32] - 2026-05-30

### Fixed
- **NPC trainer defeats now actually pay the bounty.** Root cause:
  `GymDefeatHook` filtered losers via `is TrainerBattleActor`, but
  rctapi's `BattleManager$TrainerEntityBattleActor` is a *sibling*
  of Cobblemon's `TrainerBattleActor` (both extend the abstract
  `AIBattleActor` base), not a subclass. Every RCT-mob trainer
  defeat — i.e. virtually every trainer fight on this server —
  silently dropped through the early return. The 0.7.30 diagnostic
  caught it: `loser-kinds=[TrainerEntityBattleActor]` after a
  Titan1190X win against Tamer Evan, with no `npc-defeat:` line.

  Fix: switched `applyToVictory` and `npcBounty` to filter on
  `AIBattleActor` instead of `TrainerBattleActor`. Both Cobblemon's
  and rctapi's trainer actors extend it, and `pokemonList` is on
  the base `BattleActor` so the bounty math works on either. Wild
  battles use `PokemonBattleActor` (no AI), so AIBattleActor
  cleanly discriminates trainer-vs-wild.

  This also explains *every* tonight-of symptom: the 0.7.27
  silent-zero fix was correct, the 0.7.29 trainer-AI datapack was
  correct, BATTLE_VICTORY was always firing — but the actor-class
  filter ate the result before any of it mattered.

### Kept
- 0.7.30 `battle-victory-event:` diagnostic line stays in. Cheap
  to log, useful any time we have to debug battle-side effects
  again — a future mod adding a new BattleActor subtype or a
  Cobblemon API rename would surface immediately in prod logs
  rather than going silent for hours of guessing.

## [0.7.31] - 2026-05-30

Bundle of playtest-surfaced fixes around the 0.7.26 Exeggcute / gym-trigger changes.

### Fixed
- **All 28 gym reward functions silently failed to load** since 0.7.26.
  The migration added `eco give @s <amount>` to each `beat_gym_*.mcfunction`,
  but NeoEssentials' `/eco` command isn't registered at datapack
  function-load time, so brigadier rejected the whole function file —
  every gym defeat awarded the advancement (via the RCT trigger) but
  granted no reward at all (no chat, no key, no bounty). Same issue
  bricked `defeat_elite_four.mcfunction`. Fixed by:
  - Stripping every `eco give` line via the updated
    `ops/migrate_gym_quests_to_rct_trigger.py` (now does removal-only,
    no insertion).
  - Adding a new `AdvancementHook` (cobblemon-bridge) that subscribes
    to NeoForge's `AdvancementEvent.AdvancementEarnEvent` and pays the
    bounty via `EconomyBridge.deposit` when `server:beat_gym_N`,
    `server:beat_gym_N_challenge`, or `server:defeat_elite_four`
    awards. Reflection-based deposit doesn't depend on command
    registration timing, so the load-order trap is sidestepped
    entirely.
- **Exeggcute egg from `beat_wild_trainer` was never granted at
  runtime.** The 0.7.25 reward used
  `givepokemonegg @s exeggcute min_perfect_ivs=2` — the file parsed OK
  but no egg landed. Switched the `cq_reward_egg_exeggcute` handler in
  `_finalize.mcfunction` to the namespaced
  `cobbreeding egg give @s exeggcute` form which the admin verified
  works in-game. Dropped the `min_perfect_ivs=2` arg too —
  keep it minimal.

### Changed
- **Exeggcute onboarding chain restructured** per the design call.
  Deleted the `receive_leaf_stone` quest (which had a clunky gym1 +
  income AND-gate via `requirements: [["gym1_done"], ["income_done"]]`
  and required wiring the criterion grants from each parent's reward
  function). The Leaf Stone reward moves to `reach_income_250` (Pocket
  Change) directly — by the time the player has ¢250 they've already
  beaten gym 1 and hatched their Exeggcute, so the AND-gate was
  redundant. Mainline chain after gym 1 is now:
  ```
  beat_gym_1
    → reach_income_250 (Pocket Change — Leaf Stone)
      → evolve_exeggutor (Pasture Block)
        → ranch_carrot_farm (16 Bone Meal)
          → first_pvp_win (existing PvP starter kit)
            → beat_gym_2
  ```
  Re-parented: `evolve_exeggutor` (was: `receive_leaf_stone`),
  `first_pvp_win` (was: `reach_income_250`), `beat_gym_2` (was:
  `ranch_carrot_farm`). Stripped the `advancement grant ... gym1_done`
  line from `beat_gym_1.mcfunction` and the `... income_done` line
  from `reach_income_250.mcfunction` — both criterion grants targeted
  the deleted `receive_leaf_stone` and were dead writes.

## [0.7.30] - 2026-05-30

### Diagnostic
- **Unconditional `INFO` log at the entry of
  `GymDefeatHook.applyToVictory`** so we can prove whether
  Cobblemon's `BATTLE_VICTORY` event is firing at all on this
  stack. The 0.7.29 datapack got vanilla trainer fights routing
  through `rbrctai` (visible in `RunBunAI: new battle detected`
  log lines), but a Titan1190X playtest still produced no
  `npc-defeat:` line and no deposit after a one-shot KO win
  against a non-gym trainer. New `battle-victory-event:` line
  fires for every event arrival regardless of trainer/wild/gym
  classification — its absence after a confirmed win means
  Cobblemon isn't delivering the event and the next layer is a
  mixin on the battle-end path. Remove once root-caused.

## [0.7.29] - 2026-05-30

### Fixed
- **NPC trainer defeats produce no `BATTLE_VICTORY` event, blocking
  the bounty payout.** Diagnosed during a live dev playtest: the
  0.7.27 silent-zero fix is correctly deployed and `GymDefeatHook`
  subscribes to `BATTLE_VICTORY` at boot (verified via `javap`), but
  Cobblemon never delivers the event for non-gym RCT trainer fights
  (e.g. Cue Ball Corey, Lass Briana). Gym fights work fine because
  their JSONs ship with `"ai": {"type": "rb"}` and route through the
  rbrctai mod. Vanilla rctmod's ~1,222 non-gym trainers omit the
  `ai` field entirely, fall back to Cobblemon's default trainer AI,
  and on this stack (Cobblemon 1.7.3 + Sinytra Connector + NeoForge
  21.1.227) that path doesn't fire `CobblemonEvents.BATTLE_VICTORY`
  to subscribers.

  Fix: new `server-rct-ai-fix` datapack overrides every vanilla
  trainer JSON missing an `ai` field with
  `"ai": {"type": "rb", "data": {}}` so they all route through
  `rbrctai`. `data: {}` resolves to RunBunAIConfig defaults
  (`canTera=false, teraTarget=""`) — same shape our gym JSONs use.
  Generator: `ops/gen_rct_ai_fix_datapack.py <rctmod-jar>`. 337
  trainers that already had AI configured were left untouched.

  Confirmed via Titan1190X's Corey battle on dev: the screen showed
  Turn 2 with both sides battling normally, but logs had ZERO
  `STARTED SHOWDOWN`, ZERO `npc-defeat:`, and Titan's transaction
  history showed only admin grants. After this datapack ships, those
  battles will route through rbrctai (visible as
  `RunBunAI: new battle detected` log lines) and `BATTLE_VICTORY`
  will fire as expected, triggering `payNpcBounty` and depositing
  the per-defeat NPC bounty.

## [0.7.28] - 2026-05-30

### Fixed
- **Server crashed on startup with `cobblemon_bridge` mod-load
  error**: 0.7.26 simplified `GymDefeatHook` (the gym path moved to
  RCT's `rctmod:defeat_count` trigger) and removed the only
  `@SubscribeEvent` method on the object — but `CobblemonBridge.init`
  still called `NeoForge.EVENT_BUS.register(GymDefeatHook)`. NeoForge
  throws `IllegalArgumentException: class … has no @SubscribeEvent
  methods, but register was called anyway.` on `register()` when the
  target has none, which aborted mod loading entirely (mods/ list
  loaded but the bridge mod failed to construct, taking the server
  down). Removed the now-unnecessary `EVENT_BUS.register` call —
  `GymDefeatHook.registerEvents()` (subscribing to Cobblemon's
  `BATTLE_VICTORY` via `CobblemonEvents.subscribe`) is still in place
  and is the only subscription the hook needs.

## [0.7.27] - 2026-05-30

### Fixed
- **NPC trainer defeats paid $0 silently.** The 0.7.24 `npcBounty`
  formula `(multiplier × maxLevel × numPokemon) / 6` is integer
  division, and `payNpcBounty` returned silently on `amount <= 0`.
  Low-level / small-team trainers (e.g. L5 / 1 mon / multiplier 1 →
  `5/6 = 0`) produced no chat message and no deposit. Reproduced from
  prod-dev logs against `SixthSense` after the 0.7.26 gym-isolation
  refactor exposed it (gyms now bypass this code path entirely, so
  the silent NPC zero became the visible bug). Fixed by switching to
  ceiling division and flooring the result at $1 — every defeat now
  pays at least $1, and rounded-up math no longer swallows partial
  payouts. New `low-level low-team rolls never silently floor to
  zero` test sweeps every `(L1..10, 1..6 mons, ×1..3)` combination
  to lock the invariant in.

### Changed
- **`payNpcBounty` now `INFO`-logs every defeat** (`npc-defeat:
  trainer={id} player={name} bounty=${amount}`). The 0.7.26 design
  short-circuits gym battles before this hook so we don't see them
  here, but the prior absence of any per-defeat log line meant we
  couldn't tell from prod logs whether a non-gym defeat had even
  reached the bounty path. Now we can.
- **First-join Server Wiki book is now a single clickable link** to
  the MkDocs site (`https://hspahic-cs.github.io/cobblemon-server/`)
  instead of 10 hardcoded pages. The site is published from `docs/`
  and stays in lockstep with releases; the old book got baked into
  player inventories at first-join and was a hassle to update. The
  link is rendered via a Minecraft `clickEvent: open_url` text
  component on a single page, with a plain-text fallback line for
  players who can't click it (e.g. servers with chat URLs disabled).
  Existing players who already received the old wiki book are
  unaffected — the change only applies to players who first-join
  after deploy.

## [0.7.26] - 2026-05-30

Two slices: a quest-chain addition for Cobbleworkers onboarding, and a
structural fix to gym achievement reliability.

### Fixed
- **Gym achievements + gym bounty payment now driven by RCT's own
  `rctmod:defeat_count` trigger** instead of our cobblemon-bridge
  `BattleVictoryEvent → RctBridge reflection → stash → proximity` chain.
  Every gym advancement (`beat_gym_1`..`beat_gym_24` + 10 challenge
  variants) had its criterion replaced with the rctmod trigger keyed on
  the specific trainer id(s). RCT fires this trigger itself when the
  player defeats the matching trainer — it's the authoritative event
  and doesn't depend on our reflection working (which has been the
  recurring failure mode: 0.7.14 fixed `TrainerBattle.getTrainerId()`
  moving to `TrainerMob`, but the chain was still fragile to other
  upstream renames or to engagement edge cases).
  - Gym bounty payment (`$150 × gymId`) moved out of
    `GymDefeatHook.payGymBounty` (Kotlin) into each
    `beat_gym_*.mcfunction` as `eco give @s <amount>` via
    NeoEssentials. Whenever the advancement awards — by any path —
    the bounty fires alongside, eliminating the "advancement awarded
    but bounty missed" race that was possible when both Kotlin and
    advancement paths could awards simultaneously.
  - `GymDefeatHook` simplified dramatically: dropped Branch 0/1 (RCT
    direct + stash gym-award paths) and all the supporting
    `BATTLE_STARTED_PRE` proximity scanning, `EntityInteract` stash,
    `Pending` data class, `pendingByPlayer` map. The hook is now
    ~25 lines and handles only the per-defeat NPC bounty for non-gym
    trainers, with `RctBridge.trainerIdForBattle` used solely to
    suppress NPC bounty for gym battles (so they don't get bonus
    NPC bounty on top of the flat gym payout).
  - One migration script: `ops/migrate_gym_quests_to_rct_trigger.py`
    rewrites all 34 advancement JSONs + mcfunctions idempotently.

### Added
- **Exeggcute → Cobbleworkers onboarding quest chain.** Three new
  quests that chain off `beat_wild_trainer` to introduce players to
  the (newly nerfed in 0.7.24) Cobbleworkers system via an Exeggutor
  carrot farm. Reward tags wired through `_finalize.mcfunction`.
  - `beat_wild_trainer` reward changed from `gacha giveegg @s common`
    (random species from the common pool) to
    `givepokemonegg @s exeggcute min_perfect_ivs=2` — a guaranteed
    Exeggcute with 2 perfect IVs to kick off the chain.
  - **`receive_leaf_stone`** — gated by BOTH `beat_gym_1` AND
    `reach_income_250`. Vanilla MC advancements only support one
    parent, so the AND is expressed via two `minecraft:impossible`
    criteria with `requirements: [["gym1_done"], ["income_done"]]`;
    each parent quest's reward function fires the matching criterion
    via `advancement grant @s only server:receive_leaf_stone <crit>`.
    Reward: 1 Leaf Stone + hint to evolve the Exeggcute.
  - **`evolve_exeggutor`** — fires via new
    `cobblemon-bridge:EvolutionHook` subscribed to Cobblemon's
    `EvolutionCompleteEvent`. When the post-evolution species name
    matches "Exeggutor", we award the `done` criterion via
    `QuestAdvancements.award`. Reward: 1 Pasture Block.
  - **`ranch_carrot_farm`** — triggers on
    `minecraft:placed_block` with `block: cobblemon:pasture`. Reward:
    16 Bone Meal so the player can fast-grow a starter carrot patch.
  - `beat_gym_2`'s parent re-routed from `first_pvp_win` to
    `ranch_carrot_farm`, so the new chain visually sits before gym 2
    in the advancement tree. (RCT doesn't actually gate gym entry on
    achievements — purely UI placement.)
  - `reach_income_250`'s Pasture Block reward moved out and replaced
    with a small grinding kit (5 Great Balls + 3 EXP Candy S), since
    the pasture now belongs to the Cobbleworkers-introduction chain.

## [0.7.25] - 2026-05-30

### Changed
- **Wardens spawn again.** Carved an exception for `EntityType.WARDEN`
  out of the global hostile-natural-spawn block in `WorldRulesHook`
  Rule 3. Wardens only emerge from sculk shriekers
  (`MobSpawnType.TRIGGERED`) after a player deliberately disturbs the
  deep dark, so they're a hazard players opt into rather than the
  ambient-hostile noise Rule 3 exists to suppress. NO_MOB dims
  (`multiworld:*`) still block every `Mob` via Rule 2, so wardens
  remain absent from spawn and arena worlds.

## [0.7.24] - 2026-05-30

Batch of playtest-surfaced fixes + balance tweaks.

### Fixed
- **Gym wins didn't always grant credit, especially for non-ops.** Root
  cause: `RctBridge`'s reflection chain still called the old
  `TrainerBattle.getTrainerId()` accessor, which RCT moved to
  `TrainerMob.getTrainerId()` in a recent rctmod-neoforge update. Every
  gym defeat was throwing `NoSuchMethodException` and falling through to
  the stash/proximity fallback. The stash worked for ops who right-clicked
  the trainer first (EntityInteract path), but failed for non-ops who
  walked into LOS engagement — the proximity scan misses when the trainer
  is > 8 blocks away at `BATTLE_STARTED_PRE`. New chain:
  `TrainerBattle.getTrainerSideMobs()` → `firstOrNull()` →
  `TrainerMob.getTrainerId()`. Branch 0 now resolves reliably for all
  players.
- **`/trade` money silently failed.** Diagnosed via prod log
  (`trade complete: SixthSense <-> peachyorbit ($ 0/0)` — both sides
  zero). `TradeManager.setMoney` was clamping silently when balance was 0
  (which is the default since 0.7.10's `startingBalance=0`). Now sends a
  chat warning when the offer is clamped below the requested amount + INFO
  log on each setMoney call. The `+$` menu tile now shows your current
  balance and a red "Your balance is $0 — offers will clamp to 0." line
  when applicable. `execute`'s money transfer also now checks
  `EconomyBridge.withdraw`'s return value and skips the deposit if the
  withdraw failed (prevents the silent money-duplication bug in the
  opposite direction).

### Changed
- **Trade menu redesigned** — replaced the opaque "+ Stage Pokémon" button
  with each player's 6 party Pokémon visible in rows 0-1 flanking their
  head. Click your party tile to stage; click again (or click the staged
  tile in the trade area) to unstage. Staged tiles get a strikethrough
  name + "Already in trade" lore so no click is ambiguous. Confirm tiles
  moved from row 5 (bottom corners) to row 1 (next to each head) for
  easier "my side" identification. The `+$` buttons moved to row 5
  alongside the money displays.
- **Trade evolutions now fire** for traded Pokémon. After the atomic
  Pokémon transfer in `TradeManager.execute`, each received mon's
  evolution chain is walked and any `TradeEvolution` entries get
  `attemptEvolution(thisMon, partnerMon)` called — partner being the
  first mon the receiver sent out as their exchange. Inherits Cobblemon's
  species JSON: covers Kadabra/Machoke/Graveler/Haunter,
  Boldore/Gurdurr/Phantump/Pumpkaboo, Karrablast↔Shelmet link-trade,
  and held-item variants (Onix+Metal Coat→Steelix, Scyther+Metal
  Coat→Scizor, Seadra+Dragon Scale→Kingdra, etc.). One-sided trades
  (the receiver gave only money/items, no partner mon) skip evolution
  since there's no context Pokémon.
- **Trainer-battle EXP doubled.** New `TrainerExpBoostHook` subscribes to
  Cobblemon's `ExperienceGainedEvent.Pre`, detects trainer-battle sources
  via `BattleExperienceSource.facedPokemon[].actor is TrainerBattleActor`,
  and multiplies `event.experience` by 2.0. Stacks multiplicatively with
  the existing global `experienceMultiplier = 2.0`, so trainer-battle EXP
  is `base × 2.0 × 2.0 = 4× vanilla`. Wild Pokémon battles unaffected.
  Lucky Egg's 1.5× stacks on top if held (→ 6× vanilla in that case).
- **Cobbleworkers heavily nerfed**:
  - **20 jobs locked to species allowlists** instead of accepting any
    Pokémon of a matching type. E.g., `crops_harvester` now only accepts
    Leafeon/Bellossom/Sunflora/Lilligant/Vileplume/Victreebel/Exeggutor/
    Roserade/Simisage/Whimsicott (was "any grass-type"). New
    `server-cobbleworkers-allowlists` datapack overrides each upstream job
    JSON with a `species` requirement while preserving any
    `conditions` (e.g. IN_WATER for the now-restricted irrigator /
    extinguisher / water_generator entries). Regional-form Pokémon in the
    source spec collapsed to their base species name since Cobbleworkers'
    Requirements ANDs species+aspects (e.g., `alolan_ninetales` →
    `ninetales` — both regular and Alolan qualify; flag for follow-up if
    you want stricter form-checking).
    - Tumblestone harvester restriction applies to all 3 tumblestone
      variants (`tumblestone_harvester`, `black_tumblestone_harvester`,
      `sky_tumblestone_harvester`).
    - 4 treasure-generator jobs disabled entirely (impossible species +
      empty components): `archaeologist`, `dive_looter`, `fishing_looter`,
      `pickup_looter`. These pull from random loot tables with no real
      block to harvest. `honey_collection` is intentionally kept (it's
      loot-table-driven but tied to real beehives in the world).
    - Untouched (still type-based "any matching type" requirement):
      `bush_harvester`, `hearty_grains_harvester`, `dive_looter` (NOTE:
      already disabled above), `healer`, `honey_collection`,
      `honey_generation`, `pickup_looter` (disabled), `rain_dancer`.
    - Generator script at `ops/apply_cobbleworkers_allowlists.py` for
      idempotent regeneration when the species list changes.
  - **Job scan radius reduced from 8 → 2** (5×5 horizontal footprint
    from the ranch block, was 17×17). Vertical `areaScanHeight` left at
    5. Pinned in `modpack/server-overrides/config/cobbleworkers/cobbleworkers.json`
    so it deploys with each release instead of drifting in runtime.

## [0.7.23] - 2026-05-30

### Added
- **AllTheMons R3.5** — meta-pack that fills 92 of the ~101 species
  Cobblemon 1.7.3 ships as data-only (no model). Covers Raikou /
  Entei / Suicune, Pawniard / Bisharp, the Treasures of Ruin (3 of
  4 — Ting-Lu still pending), most Paradox Pokémon, and a long
  tail of gen-3/5/9 stragglers. Maintainers explicitly resolve
  overlap conflicts between the bundled sub-packs (MissingMons,
  HiddenMons, BoniMons, Kale's, Pigeon's, OdysseyMons, +17 others)
  so we don't have to manage them ourselves.
  - **9 species still missing** after AllTheMons: Tympole / Palpitoad
    / Seismitoad, Virizion, Celesteela, Iron Hands, Iron Jugulis,
    Iron Boulder, Ting-Lu.
  - **85 already-implemented species** get their models replaced by
    AllTheMons. That's the trade-off and the reason we chose a
    curated meta-pack over hand-mixing addons.

### Changed
- **`modpack/resourcepacks/`** — new directory; packwiz bundles its
  contents into the `.mrpack` overrides path so PrismLauncher
  extracts them to `<minecraft>/resourcepacks/` on import.
- **`modpack/options.txt`** — added `resourcePacks:["file/AllTheMons
  [R3.5].zip"]` so PrismLauncher auto-enables the pack on first
  import.

### Notes
- Distribution: AllTheMons is a single zip that's both a datapack
  (`data/`) and a resource pack (`assets/`). The zip ships in two
  places:
  - `modpack/resourcepacks/` → client side via packwiz/.mrpack.
  - `modpack/server-overrides/datapacks/` → server side via the
    existing datapacks rsync in deploy-{dev,prod}.yml.
- Existing imports won't auto-enable the resource pack —
  PrismLauncher only writes `options.txt` on first import.
  Players either re-import or enable AllTheMons manually from
  Options → Resource Packs.

## [0.7.22] - 2026-05-30

### Fixed
- **Gym leaders still wandered after 0.7.21.** The 0.7.21 anchor used
  `EntityJoinLevelEvent` to register tagged mobs, but RCT's
  `summon_persistent` fires the join event synchronously *before* the
  next-line `tag … add cobblemon_bridge.anchor` command runs in the
  spawn mcfunction. The trainer was observed without the tag, the
  registry skipped it, and no later event re-checked. Switched
  `EntityAnchor` to `EntityTickEvent.Post` with a fast-path tag
  predicate — every loaded mob is checked once per tick, and tagged
  ones get snap-back regardless of when the tag was added. Per-tick
  cost is dominated by the entities Minecraft was already ticking; the
  added work is a `Mob` cast and tag scan that returns in nanoseconds
  for non-anchored mobs.
  - Market vendor anchoring is unaffected (the legacy
    `cobblemon_bridge.market_vendor` tag was always present at summon
    time, so 0.7.21 worked for vendors — but the new mechanism makes
    the spawn-flow assumption irrelevant going forward).

## [0.7.21] - 2026-05-30

### Changed
- **`MarketVendorAnchor` → `EntityAnchor`.** Generalized the per-tick
  snap-back from `Villager` to `Mob` so any tagged entity — vendors,
  RCT trainers, future NPCs — can be pinned to its spawn position
  without freezing the AI driving natural head/body movement. Legacy
  `cobblemon_bridge.market_vendor[.<scope>]` tag still recognized,
  so existing market shopkeepers anchor unchanged.

### Fixed
- **Gym leaders no longer wander out of their arenas.** RCT trainers
  have minimal pathfinding but still drift over time; the anchor
  pattern that already kept the market shopkeeper rooted now applies
  to gym leaders too. Tagged in all 24 mainline + 10 challenge gym
  spawn mcfunctions as `cobblemon_bridge.anchor`.

### Activation on existing dev/prod world
After deploy, re-spawn each leader at its arena to capture the desired
anchor position:
```
/function server:gym/delete_<N>
/tp @s <arena coords>
/function server:gym/spawn_<N>
```
Or quickly backfill anchors at current positions:
```
/tag @e[type=rctmod:trainer] add cobblemon_bridge.anchor
```

## [0.7.20] - 2026-05-30

### Removed
- **Cobblemon Move Inspector.** Extended Battle UI (kept) already
  surfaces the relevant info via its move tooltips, and Move
  Inspector's separate overlay added clutter without adding value.

### Changed
- **Default `guiScale` 5 → 3** in `modpack/options.txt`. Scale 5 made
  Cobblemon's battle HUD (Fight/Switch/Forfeit, party tiles, HP bars)
  feel oversized in 1v1 battles and clash with Extended Battle UI's
  panels. Scale 3 matches the readable, compact look from the mod's
  reference screenshots.
  - **Note:** PrismLauncher only writes `options.txt` on **first**
    import. Existing imports keep their current `guiScale` — change it
    in-game (Options → Video Settings → GUI Scale) or re-import the
    `.mrpack`.

## [0.7.19] - 2026-05-30

Hotfix for the 0.7.18 battle-UI mods landing — the dev server crashed
on boot with `Mod loading has failed`.

### Fixed
- **Battle UI mod dep failures crashed the server.** Two unmet
  Connector deps in 0.7.18 caused FML to abort mod loading (which
  also took down LegendaryMonuments as collateral):
  - **Cobblemon Battle Conditions 0.2.0-1.7.0-BETA** pinned to
    Cobblemon `1.7.0`, but the pack ships `1.7.3`. No newer release
    exists, so the mod is removed for now. We can revisit when an
    update targeting 1.7.3 is published.
  - **Cobblemon: Extended Battle UI 0.9.0** required
    `fabric-language-kotlin >= 1.12.0`, which we hadn't shipped (we
    had `kotlin-for-forge` for the NeoForge side, but Fabric mods
    via Sinytra Connector need the Fabric Kotlin runtime
    separately). Added `fabric-language-kotlin 1.13.11+kotlin.2.3.21`.
- **Cobblemon Move Inspector** is unaffected (NeoForge-native) and
  continues to ship.

### Notes
- Lesson: when adding Fabric-via-Connector mods, check both their
  Cobblemon version pin *and* their Fabric runtime deps. CI's
  `is-active` check only catches whether systemd started the service
  — Minecraft can still die mid-mod-load before binding the listen
  port.

## [0.7.18] - 2026-05-30

### Added
- Three Cobblemon battle UI mods to bring the in-battle experience
  closer to the mainline games. They cover non-overlapping slices of
  the battle screen:
  - **Cobblemon Move Inspector** (NeoForge, native) — in-battle move
    details: PP, type, power, accuracy, and effectiveness vs the
    active opponent.
  - **Cobblemon: Extended Battle UI** (Fabric via Connector) —
    Showdown-style HUD with held items, ability, status, and stat
    changes for the active Pokemon.
  - **Cobblemon Battle Conditions** (Fabric via Connector) — movable
    field-state window tracking weather, terrain, hazards, and
    screens.

## [0.7.17] - 2026-05-30

Server-side LM (and any Fabric mod) finally works on dev/prod.

### Fixed
- **`mods/` symlink broke Sinytra Connector silently** — the deploy
  workflows did `ln -sfn mods.vX.Y.Z mods` for cheap atomic-swap. Turns
  out NeoForge's `ServiceLoader` for `IModFileCandidateLocator` doesn't
  pick up services from jars in a symlinked directory, so Connector's 4
  service registrations (TransformationService, CoreMod, DependencyLocator,
  ModFileCandidateLocator) silently no-op'd. Result: every Fabric mod —
  LegendaryMonuments, Trinkets, etc — got "Skipping jar. File ... is a
  Fabric mod and cannot be loaded" and only the NeoForge-native subset
  loaded. This had been broken on cobblemon-{dev,prod} for ~6 weeks; the
  symptom was that LM appeared in the modpack but `/locate
  legendarymonuments:southern_island` returned "no structures found".

  Root cause confirmed by isolated repro: a fresh NeoForge install with
  the same mod set in a real `mods/` directory loaded LM correctly;
  swapping to a symlink broke it; restoring the real directory fixed it.

  Fix: `mods/` is now a hardlink copy (`cp -al`) of the staged version
  archive. Same atomicity, same disk usage (hardlinks share inodes), but
  it's a real directory that NeoForge's service loader will scan. The
  `mods.vX.Y.Z/` archives stay at install root for rollback.

  Touches `deploy-dev.yml`, `deploy-prod.yml`, plus doc updates in
  `docs/server-setup.md` and `docs/working-with-mods.md`.

## [0.7.16] - 2026-05-30

Reverted LM to 7.8 + removed Trinkets.

LM 7.1-NEOFORGE-CONNECTOR was the wrong direction — its fabric.mod.json
declares `depends trinkets`, which crashed the *client* (cobblenav model
load). LM 7.8 declares `accessories` instead, which is already in the pack.
Reverting to 7.8.

Trinkets removed since 7.8 doesn't need it.

## [0.7.15] - 2026-05-30 [reverted]

Added Trinkets — wrong fix, reverted in 0.7.16.

## [0.7.14] - 2026-05-30 [reverted]

Switched LM to 7.1-NEOFORGE-CONNECTOR — wrong fix, reverted in 0.7.16.

## [0.7.13] - 2026-05-30

Two playtest-surfaced bug fixes from 0.7.12 + a small market addition.

### Fixed
- **Gacha "Nature Mint" entries gave nothing** — common.json's `1 Nature
  Mint (random)` and rare.json's `3 Nature mints` both referenced
  `cobblemon:nature_mint`, which isn't a real Cobblemon item id (the actual
  items are per-nature: `cobblemon:adamant_mint`, `cobblemon:bold_mint`,
  etc.). `RewardGranter.materialize` resolved them to `Items.AIR` and
  logged a warning, so the gacha pull was silent no-op. Switched both
  entries to `random_item` across all 21 typed mints (the 4 neutral
  natures — bashful/docile/hardy/quirky — don't have mints, intentionally
  excluded). For the rare entry's `count: 3`, the random_item rolls once
  per entry and grants 3 of the picked nature; this is the existing
  RandomItem behavior, not a regression. **This was also the "missing
  item after Rare Candy in the common preview"** — same cause.

### Added
- **5 EXP Candy entries on the default-vendor market**
  (xs/s/m/l/xl), so they appear in `/market prices` and are purchasable.
  Prices follow the 5× sell→buy spread from the original design table:
  `xs 5/25`, `s 30/150`, `m 90/450`, `l 270/1350`, `xl 810/4050`. All at
  `baseStock=200` and elasticity 1.0 — they pick up the server-wide
  `buyStockImpact=3` default, so a single buy drains 3 stock and the
  price clamp at `[1/3, 3]` keeps arbitrage impossible. The original
  table gated these on a future daily-cap mod; that gate is now released
  on user direction. If grinding turns out to be exploitable in playtest,
  the per-item override knobs (lower `buyPriceClamp`, higher
  `buyStockImpact`) can tighten without a code change.

## [0.7.12] - 2026-05-30

Gacha reward fixes + market table cleanup + advancement menu cleanup. Mostly
config/bug-fix work — only one schema-level change (new `enchanted_book`
ItemSpec) and one display-name bug fix in the market.

### Fixed (gacha — items giving the wrong thing)
- **Silk Touch Book** now grants an actually-enchanted book (was giving a
  plain `minecraft:enchanted_book` with no stored enchantment). Added
  `ItemSpec.EnchantedBook(enchantment, level, count)` sealed subtype and
  wired it through serializer + `RewardGranter.materialize` using
  `EnchantmentHelper.setEnchantments`. Loot-table schema gains a new
  `enchanted_book` JSON `type`.

### Changed (gacha — removed broken / unwanted entries)
- **Bee Egg** entry removed from `rare.json` — was giving a vanilla
  `minecraft:egg` (chicken throwing egg). No replacement.
- **IV Candy** entry removed from `rare.json` — was giving `cobblemon:rare_candy`
  with no IV effect. No replacement.
- **Bottle Cap / Gold Bottle Cap** entries removed from `rare.json` and
  `ultra.json` — item doesn't exist in the live Cobblemon build, so the
  vanilla-id lookup was failing silently.
- **Focus held item** renamed to **Choice Held Item** and converted from a
  fixed `cobblemon:focus_sash` drop to a `random_item` pick across
  `cobblemon:muscle_band`, `cobblemon:choice_band`, `cobblemon:focus_band`
  with equal probability.

### Changed (gacha — tuning)
- **EXP Candy counts halved across all 3 crate tiers** (round down, min 1).
  E.g., 5 Exp Candy S → 2; 5 Exp Candy M → 2; 3 Exp Candy L → 1; 2 Exp Candy
  XL → 1. Weights untouched.
- **Pokémon eggs bumped to ~30% total weight per crate** (was 0% in common,
  ~0.5% in rare, ~23% in ultra). All crates now total exactly 30% egg.
  - Common crate: 30% common-pool egg (no shiny).
  - Rare crate: 18% uncommon + 11.5% rare + 0.5% shiny rare (shiny preserved).
  - Ultra crate: 14% uncommon + 8% ultra_rare + 3% ultra_rare HA + 5% shiny
    (shiny weights unchanged per design).
- All 3 loot tables re-normalised to `totalWeightPct = 100`.

### Changed (market)
- **`/market prices` now shows only Pokéballs + Carrots + Candies**
  (whitelist: `*_ball`, `minecraft:carrot`, `rare_candy`, `exp_candy_*`).
  Potions, status heals, PP restore, EV vitamins, revives, and all TM /
  held-item vendor scopes are hidden from the prices overview.
- **Carrot baseSellPrice raised 0 → 2 + `sellable=true`** so excess carrots
  from the carrot farm have a sink and the carrot economy isn't one-way.
- **Fixed Held Items vendor display name** — used to render as
  "Held_items TM Vendor" because the spawn-vendor command's name generator
  assumed every non-default vendor was a TM vendor and naïvely
  upper-cased the slug. Now renders as "Held Items Vendor". Unified the
  vendor-display-name logic between `MarketCommands.spawnVendor` and
  `MarketMenu.titleForVendor` so spawn and GUI titles can't drift again
  (single helper: `MarketCommands.vendorDisplayName`).

### Changed (advancements)
- **RCT (radical-trainers) advancement tab hidden** from the in-game
  advancements menu. Added a server-side override at
  `data/rctmod/advancement/trainers/defeat_any.json` (in the existing
  `server-hide-advancements` datapack) that keeps the original
  `rctmod:defeat_count` criterion intact (so trainer defeats still register
  and downstream child advancements still unlock for any code listening),
  but strips the `display` block so the tab disappears from the menu.
- Server Progression (already configured) becomes the sole visible
  advancement tab — cobblemon + minecraft + rctmod tabs are now all hidden
  by the same datapack.

### Added (economy)
- **Per-defeat NPC trainer bounty** for non-gym RCT trainers. Formula:
  `bounty = multiplier × maxTrainerLevel × numPokemon / 6` (integer
  division), where `multiplier ∈ {1, 2, 3}` is rolled uniformly per defeat,
  `maxTrainerLevel` is the max level among the loser's team and `numPokemon`
  is the team size. Wired into `GymDefeatHook` Branch 2 (the non-gym trainer
  defeat path); gym trainers continue to use the existing `$150 × gymId` flat
  reward and are unaffected. Examples for a full 6-mon team (low / mid / high
  roll): L20 → $20 / $40 / $60; L60 → $60 / $120 / $180. Smaller teams scale
  linearly with `numPokemon`. Expected value matches the original constant-2
  multiplier; the per-defeat randomness keeps trainer grinds from feeling
  monotone. Fires on **every** defeat (RCT trainers reset, so this is a
  renewable income source separate from the one-time `server:beat_wild_trainer`
  advancement award). New `GymDefeatHookTest` covers the formula edge cases
  (full/partial teams, integer-flooring, zero-input guards, all 3 multiplier
  rolls, and a sanity check that NPC bounty stays an order of magnitude under
  gym bounty even at the high roll).

## [0.7.11] - 2026-05-30

Full market overhaul. Price-multiplier clamp, global min sell price, per-item
overrides for both clamp sides + stock-impact (asymmetric scarcity) + per-item
buy-price floor, plus a 22-entry authored values pass for the default vendor
that fills in HP potions, status heals, PP restore, and revives — categories
previously missing from the market.

### Added (new pricing-curve knobs on `ItemEntry`)
All optional, default to the global behavior so existing entries are unchanged.
- **`buyPriceClamp` / `sellPriceClamp`** (`Double`): per-item override for the
  price multiplier clamp. Defaults to `PricingEngine.SCALE_CLAMP = 3.0`. Tighter
  values (e.g., `1.5`) shrink the price band for stable commodities; looser
  values widen it. Buy and sell are independently configurable.
- **`buyStockImpact` / `sellStockImpact`** (`Double`): stock units moved per
  trade unit.
  - `buyStockImpact` defaults to **`3.0` server-wide** — every buy drains 3
    stock units regardless of how many items the player asked for. Per-item
    override is supported (set to `1.0` on items where bulk-buy needs to
    clear without hitting the stock floor — none currently overridden).
  - `sellStockImpact` defaults to `1.0` (sell-side stays symmetric with raw
    items moved).
  - The anti-arbitrage invariant holds at any impact ratio thanks to the
    clamp, so the 3:1 asymmetry doesn't re-open the buy-then-sell exploit
    (regression-tested in `arbitrage stays dead with asymmetric stock impact`).
- **`minBuyPrice`** (`Int`): hard floor on the buy price applied after rounding.
  Defaults to `0`. Useful when the natural clamp floor (`baseBuy / clamp`) is
  too cheap for an item that should never be a bargain.

### Changed
- **cobblemon-market / pricing multiplier clamped to `[1/3, 3]`**: the
  per-item `scale = ((stock+1)/(baseStock+1))^(-elasticity)` factor is now
  clamped post-elasticity to `[1 / clamp, clamp]` with `SCALE_CLAMP = 3.0`
  as the default. Effects:
  - `buyPrice ≤ clamp × baseBuyPrice` regardless of how low stock is driven.
  - `buyPrice ≥ baseBuyPrice / clamp` (unless raised by `minBuyPrice`).
  - `sellPrice` bounded symmetrically.
  - **Anti-arbitrage invariant** — with the spread `baseBuyPrice ≥ clamp ×
    baseSellPrice`, the clamp guarantees `max sellPrice ≤ baseBuyPrice`. A
    buy-then-sell round trip is structurally loss-making at any
    `(quantity, stock, stockImpact)` path and at any `elasticity`. Pre-clamp,
    low-baseStock items admitted a profitable arbitrage; now strictly negative
    in unit tests, including under asymmetric stock impact.
- **cobblemon-market / global minimum sell price of 1 cobbledollar**:
  `MIN_SELL_PRICE = 1` floor on `sellPrice`. Prevents cheap items from
  rounding to 0 payout at oversupply.
- **cobblemon-market / authored default-vendor values overhaul**: 22 items
  (vs the previous 6). Categories: Pokéballs (3), Carrot (1, buy-only), HP
  potions (5), Revives (2), Status heals (6), PP restore (4), Rare Candy (1).
  All `baseStock ≥ 200` (bumped from the source table's `100` floor so
  shift-buy-16 at `buyStockImpact = 3` clears comfortably: `16 × 3 = 48 ≪ 200`);
  super-common items (Carrot + all 3 Pokéball tiers) at `baseStock = 1000`. Carrot elasticity lowered `1.0 → 0.7`. Every
  default-vendor item inherits the server-wide `buyStockImpact = 3.0` default
  (no explicit override needed). Three table-level inconsistencies from the
  source draft fixed: Hyper Potion slotted between Super and Max (`180/36`,
  was tied with Super at `135/27`); Elixir and Max Elixir bumped above their
  Ether counterparts (`225/45` and `315/63`) since Elixir restores all moves'
  PP vs Ether's one.
- **cobblemon-market / TradeOps stock-availability check**: now requires
  `floor(stock) ≥ ceil(qty × buyStockImpact)` (was `≥ qty`). Stops items with
  `buyStockImpact > 1` from being purchased past the stock-floor.
- **Exp Candies (5) and Hyper Training Candies (10+) intentionally NOT
  shipped this release** — per the source table's "ships after daily-cap
  mod" gate.

### Tests
- Existing tests 2-5 in `PricingEngineTest` rewritten to assert clamped
  values (previously asserted unbounded `~101×` / `~0.1×` multipliers).
- New tests (5):
  - `sell price is floored at min sell price even at oversupply`.
  - `buy-then-sell round trip is always a loss` (elasticity 1).
  - `buy-then-sell round trip is a loss at high elasticity` (elasticity 2).
  - `tighter per-item clamp pulls the max price down` — covers the
    `buyPriceClamp` override.
  - `buy stock impact greater than one drains stock faster` — covers the
    `buyStockImpact` plumbing.
  - `arbitrage stays dead with asymmetric stock impact` — regression against
    the original exploit you raised at the overhaul kickoff: with `impact=3`
    and the clamp in place, the round trip is still strictly negative.
  - `min buy price floors the buy price at oversupply` — covers `minBuyPrice`.
- `@JvmOverloads` added to `PricingEngine.buyPrice` and `sellPrice` so the
  carrots reflection bridge (`MarketBridge.kt`) continues to resolve the
  4-arg `(Int, Double, Int, Double): Int` signature.

## [0.7.10] - 2026-05-30

Combined feature + content bundle (squash of two in-flight PRs:
`/trade` command and the Pokédex side quest / /profile additions /
starting balance / gym species swaps / held-item vendor).

### Added
- **cobblemon-bridge / `/trade` command + shared GUI**: player-to-player
  trades for Pokémon + items + cobbledollars in a single transaction.
  - `/trade <player>` sends a request (60s expiry, single pending request
    per target). `/trade accept`, `/trade decline`, `/trade cancel`,
    `/trade money <amount>` round out the command surface.
  - Shared 6×9 chest GUI: both players open a `ChestMenu` backed by the
    same `SimpleContainer`, so updates push to both clients live via
    vanilla container-sync. Left half = P1 offer, right half = P2, gray
    divider column down the middle.
  - Pokémon: staged from current party via the `+ Stage Pokémon` button
    (left-click = next un-staged party slot ascending; right-click =
    descending). Display tile is the Pokémon's `PokemonItem`. Click a
    staged tile to un-stage.
  - Items: dragged from inventory into the player's own item slots
    (4–6 per side). Ownership enforced — players can't drop items into
    the other side's slots or remove the other side's items.
  - Money: `+ Add Money` button (left = +\$100, shift-left = +\$1,000,
    right = -\$100, shift-right = clear) or `/trade money <amount>`.
    Money isn't escrowed — sender validates at execute time.
  - Confirm: each side clicks their Confirm tile. Any offer change
    un-confirms BOTH sides (matches canon Pokémon trading). When both
    are confirmed, execute fires.
  - Execute (atomic): validates level caps both ways (blocks trade if
    any incoming Pokémon exceeds receiver's cap), validates money still
    in sender's wallet, validates pokemon still in sender's party (by
    UUID), then transfers — pokemon to receiver's party with overflow
    to PC, items into receiver's inventory with overflow dropped at
    their feet, money via `EconomyBridge` (NeoEssentials).
  - Cancel paths (`/trade cancel`, closing the chest window, logout,
    one side disconnecting) all funnel through a single refund: items
    return to the original owner's inventory (drops at their feet if
    full), money offers reset, session torn down.

  Files: new `com.cobblemonbridge.trade` package
  (`TradeOffer`, `TradeSession`, `TradeManager`, `TradeMenu`,
  `TradeLifecycle`) + `commands/TradeCommand`. ~800 LOC total.
  Level-cap blocking matches the existing `TradeCapHook` policy
  (which gates Cobblemon's built-in trade event); our custom trade
  applies the same rule inline since it doesn't go through the
  Cobblemon trade API.
- **server-quests / "Centurion" side quest (`server:reach_pokedex_100`)**:
  catch 100 distinct Pokémon species. Branches off `server:catch_pokemon`
  ("Gotta Catch One") so it appears in the player's advancement tree, but
  it's a side quest — not in the HUD ticker, not blocking, never gates
  anything else. New `cobblemon-bridge / PokedexProgressHook` subscribes
  to `CobblemonEvents.POKEDEX_DATA_CHANGED_POST`, recounts caught
  species via reflection into `Cobblemon.playerDataManager.getPokedexData`
  on each fire, and awards at 100. Completion `tellraw` is prefixed with
  `[Side Quest Complete]` in purple/light-purple so it visually
  distinguishes from main-line completions. Reward: 1 Master Ball +
  Ultra Key.
- **cobblemon-bridge / /profile additions**:
  - **Pokédex tile** (slot 24, row 2) shows the player's caught-species
    count. Reads via `PokedexProgressHook.caughtCount(player)`. Offline
    targets show 0 until next login (Cobblemon's pokedex API needs a
    live `ServerPlayer`).
  - **Ranked-ELO tile** now shows a Pokémon-themed rank alongside the
    raw number: `1450 (Veteran)`. Bands: <1100 Rookie / 1100-1199
    Trainer / 1200-1299 Ace Trainer / 1300-1399 Veteran / 1400-1499
    Elite / 1500+ Champion.

### Changed
- **NeoEssentials / `startingBalance` 100 → 0**: new players now start
  at \$0 instead of \$100. Live `runtime/economy.json` on dev patched;
  also pinned as `modpack/server-overrides/config/neoessentials/economy.json`
  so deploys persist the value (rsync from server-overrides/config on
  every deploy). Pinning the whole file means a NeoEssentials version
  update that adds new fields would need a re-pin from the live copy.
- **server-gyms / 5 assetless species swapped to typed equivalents**:
  pancham, pawniard, vikavolt, lokix, bisharp lack rendering assets
  in Cobblemon 1.7.3 (Substitute fallback in battle). Replaced inline
  with same-type, same-level alternates that DO have models. Movesets
  + abilities re-picked per the new species' learnable pool.
  - Gym 3 Korrina (Fighting): `pancham` → `timburr` (Iron Fist;
    Drain Punch / Mach Punch / Rock Slide / Bulk Up). Applies to
    `gym_03_korrina` + `_challenge`.
  - Gym 4 Byron (Steel): `pawniard` → `lairon` (Rock Head;
    Iron Head / Rock Slide / Earthquake / Stealth Rock). Applies
    to `gym_04_byron` + `_challenge`.
  - Gym 11 Viola (Bug): `vikavolt` → `galvantula` (Compound Eyes;
    Thunder / Bug Buzz / Sticky Web / Thunder Wave).
  - Gym 18 Marnie (Dark): `lokix` → `absol` (Super Luck;
    Night Slash / Psycho Cut / Sucker Punch / Swords Dance).
  - Gym 21 Cynthia (E4): `bisharp` → `tyranitar` (Sand Stream;
    Stone Edge / Crunch / Earthquake / Dragon Dance).
  - **Not swapped**: lycanroc, oricorio, eternatus — also assetless
    but more complex (forms/legendary); user opted to leave for now.
  Generation in `ops/swap_gym_species.py`; rerun against a future
  Cobblemon update if any of these regain assets.
- **server-market / new "held_items" vendor** (101 entries): every
  item in Cobblemon's `is_held_item` tag (Choice Band/Specs/Scarf,
  Leftovers, Life Orb, Focus Sash, Eviolite, Assault Vest, Rocky
  Helmet, Air Balloon, status orbs, type-boosting items, weather
  rocks, plates, etc.) sold at flat $5,000 each, buy-only. Same
  vendor framework as the TM shops (0.7.4). Spawn with
  `/market admin spawn held_items`. Excludes: tag-refs (
  `#cobblemon:held/terrain_seeds`, `#cobblemon:type_gems`),
  `cobblemon:medicinal_leek` (it's a crop, in `cobbleworkers:crops`
  tag), and vanilla `minecraft:bone`/`minecraft:snowball` (plentiful
  via gameplay). Generation in `ops/gen_held_item_vendor.py`.

### Notes
- `QuestCommand` gains a `SIDE_QUESTS` list (so `/quests list` shows it
  under "Side Quests") and a REWARDS entry, but it stays out of
  `LINEAR_CHAIN`, `INITIAL_TRACK`, and `tick_player.mcfunction` — the
  three places that drive the HUD + main-quest UI.

## [0.7.9] - 2026-05-29

Combined hotfix + small-features bundle (squash of three in-flight
PRs: ELO floor revert, EXP-candy chest blocker, /profile header +
favorite-tracker fix). Plus a fourth fix landed on top: the
recurring "had to beat the gym twice to get credit" bug, finally
killed by talking to RCT directly instead of guessing from proximity.

### Fixed
- **cobblemon-bridge / "had to beat gym N twice to progress"**
  finally fixed deterministically. The 0.7.6 proximity-scan fallback
  missed in edge cases (player teleported into arena before
  `BATTLE_STARTED_PRE` could scan; trainer despawned post-battle; the
  `EntityInteract` stash was empty because the player engaged via LOS
  with no right-click). New primary path: a reflection bridge into
  RCT's own `RCTMod.getInstance().getTrainerManager().getBattle(uuid)`,
  which returns the `TrainerBattle` for that battle id directly.
  `TrainerBattle.getTrainerId()` then hands us the trainer JSON stem
  (`"gym_06_volkner"`, `"gym_03_korrina_challenge"`); a regex parses
  out `gymId` + `_challenge` flag. Zero dependence on entity proximity
  or event timing. The stash + proximity machinery is kept as
  defensive fallback in case RCT changes its API or isn't loaded.
- **cobblemon-ranked / "I lost a ranked battle and my ELO went UP"**:
  0.7.8 raised `minimumElo` 1000 → 1200 thinking that's what "decay
  target = 1200" meant. But `minimumElo` is the floor for *all* ELO
  drops — including normal battle losses. So any player whose ELO
  was below 1200 (e.g. 1100 from earlier losses) would, on their
  next loss, see the calculated new ELO clamped UP to 1200 by
  `maxOf(newLoser, minimumElo)`. The loss read as a gain.

  Reverted `minimumElo` to 1000 (the historical battle-loss floor).
  Decay's target/opponent is still `startingElo = 1200` via
  `EloCalculator.decayElo`, which is what actually implements
  "decay drags inactive players toward 1200" — independent of the
  battle-loss floor. `decayEnabled = false` from 0.7.8 is unchanged.

  Live runtime config on dev patched (`minimumElo: 1200 → 1000`)
  so the floor is correct on the next restart.
- **cobblemon-bridge / "favorite pokemon" tally was inconsistent for
  carrot heals**: 0.7.5 wired `FavoriteTracker` to Cobblemon's
  `POKEMON_HEALED` event. That event reliably fires for the canonical
  `Pokemon.heal()` path (Poké Healer block) but not always when HP
  is set via a direct `currentHealth = X` field write, which is what
  `CarrotHealHandler` does. Result: feeding 10 carrots to a Pokémon
  often credited 0 or only some of them to the favorite tally, so the
  /profile "favorite" jumped around between mons the player hadn't
  actively bonded with.

  Moved the credit call into the actual heal flow: cobblemon-carrots
  gains a small `FavoriteBridge.kt` that reflection-calls into
  cobblemon-bridge's `FavoriteTracker.record(...)` on each successful
  feed (both plain right-click heal and shift-right-click revive).
  `FavoriteTracker` no longer subscribes to `POKEMON_HEALED` — the
  carrot-side path is now the single deterministic source of credit,
  which also makes the stat more semantically correct ("favorite =
  Pokémon you've actively fed by hand", not "Pokémon that happened
  to be in the box during a mass heal").

  Reflection is the same soft-coupling pattern we use for
  `EconomyBridge` — cobblemon-carrots stays compile-time independent
  of cobblemon-bridge; if bridge isn't loaded the credit call is a
  silent no-op.

### Changed
- **server-no-exp-candy-chests** (new datapack): EXP candies no longer
  spawn in worldgen chest loot. First piece of the broader economy
  rework — wild XP shortcuts go away so the level loop stays driven
  by trainer/wild battles and quest rewards instead of chest
  jackpots. Two-strategy override:
  - **Cobblemon `sets/any_exp_candy.json`** is overridden with an
    empty pool. Every Cobblemon chest table that references this
    sub-loot-table (ruins gilded chests, shipwreck cove spawners,
    village pokecenters, etc.) now rolls nothing for the EXP candy
    slot. Other items in those chests are untouched.
  - **3 mega_showdown tables** (`observatory_chest`,
    `observatory_barrel_2`, `archaeology/ruins`) inline
    `cobblemon:exp_candy_*` entries directly — those entries are
    surgically removed pool-by-pool, preserving the rest of each
    table's loot.
  Out of scope: rctmod trainer-defeat medicine pools (those are
  loot drops from beating a trainer, not chest spawns).
  `LegendaryMonuments` chests aren't included because the mod isn't
  loading right now (broken Connector beta.14); easy to add via the
  same `ops/strip_exp_candy_from_chest_loot.py` if LM gets fixed.
- **cobblemon-bridge / /profile header uses the player's real skin**:
  the header slot in the profile chest GUI was a generic
  `Items.PLAYER_HEAD` with no profile attached, so it rendered as the
  default Steve/Alex face for everyone. Now sets
  `DataComponents.PROFILE` to a `ResolvableProfile(name, uuid,
  PropertyMap())` built from the target player's UUID; the client
  fetches the texture via session servers (cache hit on second
  open). Required adding `playerUuid: UUID` to `ProfileSnapshot` so
  the menu has the right id for both online and offline lookups.

## [0.7.8] - 2026-05-29

### Fixed
- **EconomyBridge → NeoEssentials Economy (Cobblemon Economy was
  silently no-op since the Connector beta.14 deploy)**: market
  sell-to-vendor wasn't updating the player's balance because every
  `EconomyBridge.deposit` / `withdraw` / `getBalance` call was
  hitting Cobblemon Economy via reflection — but `cobblemon-economy-
  0.0.17.jar` is a Fabric mod, and Sinytra Connector beta.14
  (`connector-2.0.0-beta.14+1.21.1-full.jar`) had been getting
  rejected by NeoForge at scan time with `File ... is not a valid
  mod file`. With Connector dead, the Fabric mod never loaded, our
  reflection bridge fell into the `ClassNotFoundException → manager()
  returns null → silently no-op` branch, and every economy operation
  did nothing. The symptom was only visible at the market because the
  UI shows balance; quieter callsites (wild bounty, gym income payouts,
  ranked wagers, Poké Healer charges) had been silently failing too —
  for as long as that Connector build was on the server.

  Switched all five `EconomyBridge.kt` files (cobblemon-bridge,
  cobblemon-market, cobblemon-ranked, cobblemon-carrots, cobblemon-npc)
  to talk to **NeoEssentials's `EconomyManager`** — the active Vault
  economy provider that backs `/balance` and `/pay`. API shape is
  identical: `getInstance()` instead of `getEconomyManager()`,
  `getBalance(UUID) → BigDecimal`, `addBalance(UUID, BigDecimal)`,
  `subtractBalance(UUID, BigDecimal)`. NeoEssentials's
  `balances.json` becomes the single source of truth (already was,
  since CE wasn't writing anywhere).
- **cobblemon-market / leaderboard**: reimplemented
  `EconomyBridge.getTopBalance(N)` against NeoEssentials's
  `getAllBalances() → Map<UUID, BigDecimal>` (NeoEssentials doesn't
  expose a top-N helper). Sort + truncate client-side; cost is fine
  at player-count scale.

### Changed
- **cobblemon-ranked / decay disabled + floor raised to 1200**:
  `decayEnabled` default flipped `true → false` (decay paused while
  we tune). `minimumElo` default raised `1000 → 1200` so when decay
  is re-enabled it floors at the starting ELO — "decay target =
  1200" per the user spec. Live runtime configs need to be edited
  on each server (or deleted to regenerate from the new defaults);
  the dev `runtime/config.json` will be patched as part of this
  deploy.
- **cobblemon-bridge / gym income payouts → flat $150 × N**: was a
  tiered table (50+25(N-1) mainline / 200 rotating / 300 E4 / 500
  champion) in 0.7.6 — replaced with a single linear `$150 × gymId`
  per user spec. Gym 1 = $150, Gym 12 = $1,800, Gym 24 = $3,600.
  Challenge variants still match base reward. First-beat-only gate
  via `QuestAdvancements.award()` is unchanged — RCT trainers are
  re-fightable but the income only pays once per player per gym.
- **server-quests / Pocket Change threshold $100 → $250 (revert)**:
  the 0.7.6 lowering to $100 is reverted back to $250 per user
  preference. Files renamed back to `reach_income_250.json` /
  `reach_income_250.mcfunction`; every parent reference, HUD text,
  `INCOME_THRESHOLDS` first entry, `QuestCommand` REWARDS map, and
  inline-reward script mapping flipped back. Reward bundle (Pasture
  Block) and 0.7.6's improved chat / HUD styling are unchanged.

### Removed
- **modpack/mods/cobblemon-economy.pw.toml** — dead weight (15 MB
  Fabric jar that never loaded). Removed from packwiz pinning so
  the .mrpack stops shipping it.

### Notes
- Connector still ships in the pack and is still being rejected as
  "not a valid mod file" — out of scope for this hotfix, but
  `LegendaryMonuments-7.8.jar` (the only other Fabric-only mod
  on disk) is also dead until Connector loads. Worth investigating
  separately.
- Player balances on the server haven't moved through CE since
  whenever Connector beta.14 was first deployed, so there's nothing
  to migrate. All money already lives in NeoEssentials's
  `balances.json`.

## [0.7.7] - 2026-05-29

### Fixed
- **cobblemon-bridge / MarketVendorAnchor crash-loop on 0.7.6**: the
  0.7.6 anchor hook called `level.getEntitiesOfClass(Villager.class, AABB)`
  with a world-spanning ±30,000,000 box every server tick. The
  resulting `LongAVLTreeSet.subSet` over the loaded entity-section
  index pegged the tick thread; the server watchdog killed it (a
  "single server tick took 60000004.00 seconds" stack trace pointing
  at `MarketVendorAnchor.anchorVendorsIn:69`), systemd restarted, the
  next tick re-fired the scan, crash loop. Rewrote the hook to use
  an event-based registry: `EntityJoinLevelEvent` adds tagged vendors,
  `EntityLeaveLevelEvent` removes them, and the per-tick path iterates
  only the small registry (~10 entries) with no level-wide scan. Same
  user-facing behaviour (AI on for natural head movement, position
  snapped to anchor each tick) — just doesn't hang the server.

## [0.7.6] - 2026-05-29

### Fixed
- **cobblemon-bridge / GymDefeatHook**: gym-leader defeat awards were
  inconsistent across players. The host (who right-clicked Clay)
  got the `server:beat_gym_1` advancement + quest completion + reward;
  a playtester who got engaged via RCT's line-of-sight auto-challenge
  (no right-click) silently missed all three. Root cause: the hook
  stashed `gym_id` only on `PlayerInteractEvent.EntityInteract`, so
  the LOS path bypassed the stash entirely and only the generic
  `server:beat_wild_trainer` advancement fired. New
  `BATTLE_STARTED_PRE` subscriber fills in the gap: when an actor's
  side has a `TrainerBattleActor` and the player has no existing
  stash, scan entities within 8 blocks of the player for the nearest
  one carrying a `cobblemon_bridge.gym_id.*` tag and stash that
  match. EntityInteract still takes priority when both signals
  fire, so the precise click-based path is unchanged.
- **cobblemon-market / default vendor shows empty**: the unscoped
  market shopkeeper opened with an empty grid. 0.7.4 added `vendorTag`
  and `sellable` fields to `ItemEntry` but the six pre-0.7.4 entries
  in `items.json` (`cobblemon:rare_candy`, `…:ultra_ball`,
  `…:great_ball`, `…:poke_ball`, `…:revive`, `minecraft:carrot`)
  never had those fields filled in. Gson constructs Kotlin data
  classes via Unsafe (skipping the constructor), so Kotlin
  default-parameter values like `vendorTag: String = ""` are NOT
  applied to a missing JSON field — the field deserializes to `null`,
  the menu filter `vendorTag == ""` skipped every legacy entry, and
  the default shop rendered empty. Fixed in two layers: (1) backfill
  explicit `vendorTag: ""` + `sellable: true` on the six legacy
  entries in `items.json`, and (2) make both fields nullable on
  `ItemEntry` with `vendorScope` / `isSellable` extensions that treat
  null as the documented default. Hand-edited entries that omit
  either field now do the right thing instead of silently dropping
  out of the menu.

### Added
- **cobblemon-bridge / MarketVendorAnchor**: market villagers were
  spawned with `NoAI:1b` so they wouldn't wander, but that froze
  every animation — vendors read as broken statues. New approach
  flips AI back ON (so vanilla `LookAtPlayer` + `LookAround`
  behaviours drive natural head + body movement, including trade-
  look toward nearby players) and anchors the vendor to its spawn
  position via a per-tick position-snap. Anchor is captured on first
  sighting and stashed in entity NBT (`persistentData`), so it
  survives chunk unloads + restarts. Tolerance is ~0.05 blocks, so
  the snap fires on the first sub-tick the AI tries to step — the
  body never visibly leaves the anchor. Replaces the original
  snap-rotation `NpcFaceNearestPlayer` from earlier in 0.7.6 dev
  (that one snapped the full body toward the nearest player every
  10 ticks, which read as twitchy).
- **market spawn functions**: both spawn paths (mcfunction default
  vendor + Kotlin `/market admin spawn <tag>` TM vendors) stop
  setting `NoAI:1b` so newly summoned vendors come up with AI
  enabled. Existing pre-0.7.7 vendors are auto-upgraded the first
  time `MarketVendorAnchor` sees them (it flips `noAi` off in
  place).
- **cobblemon-market / paged shop menu**: large vendors (`tm_normal`
  ~169 entries; `tm_psychic` ~53; `tm_fighting` 45) were truncated to
  the first 45 items by the single-page layout. Row 0 now hosts a
  `Previous Page` arrow at slot 0, the balance display at slot 4, and
  a `Next Page` arrow at slot 8. Each page shows up to 45 items in
  stable registration order. Arrows only appear when there's a page
  to go to; nav lore reads `Page X / Y`. The page state is per-menu —
  closing and re-opening returns to page 1.

### Changed
- **SimpleTMs / TM acquisition locked to market vendors**: 0.7.4
  disabled trainer-defeat drops but other paths (chest loot in
  structures + blank-TM crafting) were still open. Closed three more:
  - `simpletms/main.json`: `blankTMsUsable` + `blankTRsUsable` flipped
    `true → false`. Even if players craft a blank TM/TR via the mod's
    recipes, the item can't be used to receive a move — so the
    "blank → typed via Pokémon snapshot" path is dead.
  - `simpletms/main.json`: `dropRateTMFractionInBattle` +
    `dropRateTMFractionOutsideOfBattle` zeroed for cleanliness
    (already gated by `dropInBattle`/`dropOutsideOfBattle = false`).
  - **server-no-tm-loot** (new datapack): 45 empty-pool overrides for
    every SimpleTMs structure-injection loot table (vanilla chests,
    cobblemon ruins/shipwrecks/villages, pokeloot blocks, BCA
    structures). Match each `data/simpletms/loot_table/injection/<…>.json`
    with our own `{ "type": "minecraft:chest", "pools": [] }` so the
    mod's appended loot is a no-op. Generation lives in
    `ops/seed_simpletms_loot_blockers.py`; rerun to refresh against a
    new SimpleTMs version that adds injection paths.
  Net: the type-vendor market shop (`/market admin spawn tm_<type>`)
  is the only player path; admin `/give` still works.
- **server-gyms / Gym 6 swapped Roxie → Volkner (Poison → Electric)**:
  gym 6 is now Volkner with an electric-typed roster. Volkner has a
  bundled RCT skin (`gym_leader_volkner_03db`) so the swap fills in a
  proper trainer model at the same time. Team (level 40): Galvantula
  / Magnezone / Lanturn / Jolteon / Electivire / Luxray, with a
  Hard Mode variant at level 45 + max IVs. All gym 6 references
  (advancements, HUD, chat, spawn/delete/list mcfunctions, README,
  trainer mob registration) renamed `gym_06_roxie*` →
  `gym_06_volkner*`.
- **server-gyms / RCT trainer skins**: 12 named gym leaders + Volkner
  (gym 6) now declare `textureResource` pointing at a bundled RCT
  texture, so the entity renders with a real skin instead of the
  default trainer model. Coverage:
  - Exact gym-leader skins (8): Gardenia (2), Byron (4), Crasher Wake
    (7), Volkner (6 — new), Oak (19), Lorelei (20), Cynthia (21),
    Agatha (22), Lance (23).
  - Close `leader_*` matches (4): Blaine (5), Sabrina (8), Morty (10),
    Lt. Surge (13).
  - The remaining 11 gym leaders (Clay, Korrina, Roxie-was-here,
    Drayden, Viola, Cheren, Grant, Skyla, Brycen, Valerie, Marnie,
    Champion) have no bundled match — they keep the default skin
    until/unless a resource pack ships their textures. Wiring lives
    in `ops/wire_trainer_skins.py` for repeatable runs.
- **cobblemon-bridge / GymDefeatHook**: first-time gym defeats now
  deposit money on top of the advancement reward. Table:
  - Gyms 1-10 (mainline ladder): `$50 + $25×(N-1)` → 50, 75, 100,
    125, 150, 175, 200, 225, 250, 275
  - Gyms 11-19 (rotating roster): flat $200 per defeat
  - Gyms 20-23 (Elite Four): flat $300 per trainer
  - Gym 24 (Champion): $500
  Gated by the advancement, so each tier pays exactly once per
  player. Challenge ("Hard Mode") variants currently match the base
  reward — bump `gymBounty` if you want them to differ.
- **server-quests / reach_income_250 → reach_income_100**: pocket-
  change milestone threshold lowered $250 → $100 so first-server-day
  players hit it during the carrots/wild loop instead of grinding to
  the second hour. Renamed files, every datapack + Kotlin reference
  (`first_pvp_win.json`, `reach_elo_1100.json`, `reach_income_1000.json`,
  `_finalize.mcfunction`, `tick_player.mcfunction`,
  `QuestCommand.kt`, `QuestRewards.INCOME_THRESHOLDS`) updated to
  point to the new advancement id. Reward bundle (Pasture Block) is
  unchanged.
- **server-quests / inline upcoming-reward hint**: every quest-complete
  `tellraw` now appends `§8(Reward: <X>§8)` to its `Next:` line, so
  the player sees the upcoming reward without running `/quests`. e.g.
  `Next: Set a Home (/sethome) §8(Reward: §f3 Red Apricorn Sprouts§8)`.
  Multi-target hints (Gym 1's "Reach $100 or Gym 2") list both
  rewards; duplicate labels deduplicate. Source of truth for the
  mapping lives in `ops/inline_quest_rewards.py` (mirrors the
  `QuestCommand.REWARDS` map + chain shape).
- **server-quests / HUD + reward chat**: the income-quest HUD
  actionbar now reads `Reach $100 — sell items at /warp market
  (tip: /market prices)`. Every quest-grant `tellraw` upgraded the
  `Reward:` line from `§7Reward: §f...` (gray on white) to
  `§6§l✦ Reward: §e§l...` (bold gold label, bold yellow item) so
  what was just-granted reads as the dominant line in the chat
  block. Applied across all 54 reward mcfunctions in a single sweep.
- **server-gyms / battleRules**: trainer JSONs flipped
  `"maxItemUses": 0` → `"maxItemUses": 999` across all 34 gym +
  challenge fights, so players can use bag items (potions, revives,
  status cures) during NPC battles. Was hard-disabled before; high
  cap rather than `null` so RCT still tracks usage if we want to
  re-cap a particular fight later.
- **cobblemon-bridge / WildBattleRewardHook**: wild-battle bounty
  flipped off (`BOUNTY = 0`). Was `\$2` per KO or capture; the
  cobblemon-economy auto-payouts for `battleVictoryReward` and
  `capture_event_base_reward` were already zeroed in config, so this
  removes the last source of wild-encounter income. Wilds are now
  purely XP / catch-progression — the economy loop runs through
  trainer battles + quests + market trades only. Set
  `BOUNTY` back to a positive int to re-enable.
- **cobblemon-bridge / WorldRulesHook.onIncomingDamage**: tagged-
  entity invulnerability now applies in every dimension, not just
  `multiworld:*`. 0.7.3 gated this to the showcase worlds; trainers
  and market vendors that live in the overworld (or anywhere else)
  were still killable by non-ops. Anything stamped with a
  `cobblemon_bridge.*` tag — gym leaders, gym-TP villagers, market
  shopkeepers, future overworld NPCs — is now zero-damage from
  regular players in any world. Ops can still damage them for
  cleanup. Environmental damage (lava / fall / void / suffocation)
  still applies. Pokemon battles are unaffected: Showdown runs
  outside the vanilla damage path.

## [0.7.5] - 2026-05-29

### Fixed
- **build / .mrpack routing**: every 0.7.4 client got rejected at
  connect-time with `Connection Lost — cobblemonfeedback:chunk/ready/
  request channel missing on client side, but required on the server`.
  Root cause: 0.7.4 moved `cobblemon-feedback-client` from
  `modpack/mods/` to `modpack/client-overrides/mods/`, but `packwiz mr
  export` nests that path under the mrpack's `overrides/`, so on
  install the jar landed at `<mc>/client-overrides/mods/...` rather
  than `<mc>/mods/...`. NeoForge never loaded the mod → the required
  custom payload channels were never registered → connection rejected.
  `cobblemon-feedback-client` is back in `modpack/mods/` (its
  `@Mod(dist = [Dist.CLIENT])` annotation already prevents the server
  JVM from loading it, so cross-side delivery is harmless). The
  server-only routing
  (`modpack/server-overrides/mods/{bridge,carrots,feedback,gacha,market,ranked}-*.jar`)
  is unchanged from 0.7.4 — those jars work correctly because their
  delivery path on the server is via the deploy workflows' second
  rsync, not via the mrpack install.

### Notes
- The mrpack still ships the server-only jars (nested under
  `overrides/server-overrides/mods/`), so clients still download
  bytes they don't load. Reclaiming that bandwidth requires either
  contributing the `client-overrides/`/`server-overrides/` split to
  packwiz upstream or post-processing the mrpack zip after export —
  both out of scope for this hotfix.

## [0.7.4] - 2026-05-29

### Added
- **cobblemon-ranked / queue**: new `/queue`, `/queue auto`, `/queue cancel`,
  `/queue list` commands. Plain `/queue` adds you to the rotation queue;
  `auto` mode auto-pairs the next compatible player. Pairing broadcasts a
  clickable `[/queue]` / `[/challenge]` chat prompt to all online players so
  third parties can challenge into the lobby. Queue state is per-session and
  re-queues on match end (when a `notifyMatchEnded()` fires from
  `RankedBattle`).
- **cobblemon-ranked / wager**: `/ranked challenge <player> <wager>` plus
  top-level `/challenge`, `/accept`, `/decline` aliases. Wagers are escrowed
  from both sides at battle start (uses the existing Cobblemon Economy via
  reflection bridge) and paid out to the winner on `BATTLE_VICTORY`, with
  refund on `BATTLE_FLED` or any allocation error. Wager is capped at 50%
  of the challenger's balance and 25% of the target's so a rich player
  can't bait a poor one into an unwinnable stake.
- **cobblemon-ranked / arenas**: second PvP arena (`arena2`) and an
  overflow `spawn` slot, with mutex on each. Admin commands:
  `/ranked admin setarena2 <pos1|pos2>`, `setoverflow`,
  `clearpos2`, `clearoverflow`. When all arenas are full the overflow
  pads the queue instead of failing. PvP match start now broadcasts a
  global announcement so spectators can warp in.
- **cobblemon-ranked / team-select GUI**: replaces the stained-glass-pane
  team picker with `PokemonItem.from(pokemon)` so each slot renders the
  actual Pokemon model (selected state via `§a✓ ` name prefix). Drops the
  LIME/WHITE/GRAY palette — selection is now visually unambiguous.
- **cobblemon-ranked / leaderboard**: `/ranked leaderboard` rewrite with
  medal glyphs (🥇🥈🥉), padded columns, and win-rate column. New
  `/ranked stats <player>` command prints a single-player card.
- **cobblemon-ranked / Cobblemon Battle integration**: right-click on a
  player → Battle now routes through the ranked flow (subscribes
  `BATTLE_STARTED_PRE`, vetoes the raw Cobblemon battle, opens the
  team-select GUI). Eliminates the duplicate "raw" battle path that
  bypassed ELO updates.
- **cobblemon-bridge / /profile**: 6×9 chest GUI showcasing the player's
  badges (gym advancement count), ELO, level cap, income, MineColonies
  colony (reflection), last team (with `PokemonItem` rendering), and
  "favorite" Pokemon (most HP healed at a healing machine — tracked by
  the new `FavoriteTracker`, persisted at
  `config/cobblemon-bridge/runtime/favorites.json`). `/profile` opens
  self; `/profile <name>` works for online players and offline ones via
  the profile cache.
- **cobblemon-bridge / admin commands**: `/wild` runtime config
  (per-dimension wild encounter toggle) and `/hologram` admin commands
  (place, list, remove vanilla `text_display` holograms with color +
  bold markdown).
- **server-market**: 18 type-keyed TM vendor NPCs (poison, ghost, fire,
  etc.) plus the existing scoped per-vendor shop infrastructure. Each
  vendor sells every SimpleTMs TM of its type for 5000 cobble dollars
  (buy-only). Pricing and stock are easy to revisit later.
- **server-gyms**: gym challenge variants relabelled "Hard Mode" so it's
  clear which RCT NPC is the optional harder fight. Galarian Weezing
  added to one team via the `aspects:["galarian"]` form.
- **cobblemon-bridge / pokedex_red**: a custom Pokédex item ID swap so
  the in-game Pokédex matches the new red model used in starter kits.
- **content tweaks**: baked potatoes added to the carrot-farm reward
  chain; bonemeal grant +5; Great Ball recipe lore line updated for
  clarity; neoessentials config pinned so per-version regenerations
  don't reset our overrides.

### Changed
- **build / .mrpack routing**: server-only custom mods (cobblemon-bridge,
  cobblemon-carrots, cobblemon-feedback, cobblemon-gacha,
  cobblemon-market, cobblemon-ranked) now stage into
  `modpack/server-overrides/mods/` so packwiz exports them in the
  server-only section of the .mrpack. Clients no longer download them
  at all — server-side iteration (ELO tweaks, /queue, gym configs,
  market prices, etc.) stops forcing every player to re-pull the pack.
  Defense-in-depth: each of those mods is now annotated
  `@Mod(dist = [Dist.DEDICATED_SERVER])` so even if a jar lands on the
  wrong side, the JVM skips loading it. cobblemon-feedback-client moves
  symmetrically into `modpack/client-overrides/mods/`. cobblemon-npc
  stays in `modpack/mods/` (it registers blocks/items/buildings the
  client needs to render). Deploy workflows
  (deploy-dev / deploy-prod) gain a second rsync pass over
  `server-overrides/mods/` so server installs continue to receive every
  server-side jar.
- **cobblemon-bridge / WorldRulesHook.onFinalizeSpawn**: structure
  consolidated into three explicit rules — (1) Pokemon/trainer veto in
  locked dims, (2) all `Mob` veto in `multiworld:*` dims, (3)
  global hostile-monster veto (server runs Easy difficulty with
  no hostile spawns). Op-initiated spawn types
  (`COMMAND`/`SPAWN_EGG`/`MOB_SUMMONED`/`DISPENSER`) pass through at
  the top so all rules honour the op-bypass uniformly.

### Fixed
- **cobblemon-bridge / WorldRulesHook**: trainers spawned via
  `/function server:gym/spawn_<N>` no longer immediately die. The
  `EntityJoinLevelEvent` veto added in 0.7.3 over-blocked: the
  mcfunctions stamp the `cobblemon_bridge.gym_id.<N>` tag AFTER
  `rctmod trainer summon_persistent`, so at `EntityJoinLevel` time the
  tag isn't on yet and the spawn was being cancelled before its own
  tag command could run. The `FinalizeSpawnEvent` gate above already
  distinguishes op-initiated spawn types from natural spawns — the
  belt-and-suspenders `onEntityJoin` was redundant. The note in the
  0.7.3 release about gym-summon being vetoed by the entity-join check
  no longer applies.
- **server-quests / hud**: hotbar quest HUD `/warp gym<N>` →
  `/warp gyms` (and `/warp elite4` for entries 20-24) for all 24 gym
  entries. Previous fix lived only on `feat/holograms-0.6.3` and never
  reached `main`; this rebase brings it forward.
- **cobblemon-ranked / forfeit**: leaving a ranked battle now correctly
  forfeits with ELO update + teleports both players back to their
  pre-battle locations. Adds an admin force-cancel command for stuck
  matches. SimpleTMs mod added to the modpack alongside this fix.
- **server-trainers / RCT**: clears all "trainer validation failed"
  log spam by completing missing aspect arrays in the RCT trainer
  JSONs. Disables SimpleTMs random-drop loot from trainer defeats —
  TM acquisition is now strictly via the type vendors.

### Notes
- Rebased onto 0.7.3 (which introduced the upstream WorldRulesHook
  overhaul). The 0.7.3 `onEntityJoin` belt-and-suspenders check is
  removed in this release (see Fixed above). The
  `LivingIncomingDamageEvent` invulnerability hook from 0.7.3 is
  preserved unchanged.

## [0.7.3] - 2026-05-29

### Added
- **cobblemon-bridge / WorldRulesHook**: stricter rules for `multiworld:*`
  dimensions (spawn, elite4, arena1, arena2, and any future `multiworld:`
  worlds). Two new behaviors on top of the existing locked-dim treatment
  (Adventure mode + no block edits + no Pokemon/trainer natural spawns):
  - **All natural mob spawns blocked.** Hostile, passive, ambient — any
    `Mob` entity. Removes any incentive for players to explore the
    showcase worlds for resources or fights. Op-summoned entities
    (`COMMAND` / `SPAWN_EGG` / `MOB_SUMMONED` / `DISPENSER`) and any
    entity tagged with `cobblemon_bridge.*` still go through.
  - **Tagged entities are invulnerable to non-op players.** Anything
    carrying a `cobblemon_bridge.*` tag (gym leaders, gym-TP villagers,
    etc.) takes zero damage from regular players in `multiworld:*`
    worlds. Ops can still damage them for cleanup. Pokemon battles are
    unaffected — those use Showdown, not the vanilla damage path.

### Notes
- Other locked dims (anything outside vanilla overworld/nether/end that
  isn't `multiworld:*`) are unchanged: same Pokemon/trainer veto +
  Adventure-mode treatment as before.
- Existing mobs already in `multiworld:*` worlds aren't auto-cleared by
  this change. Use `/kill @e[type=!minecraft:player,...]` per world to
  flush them once.
- Gym leaders summoned via the rctmod trainer spawner block still need
  the existing `cobblemon_bridge.gym_id.<N>` and
  `cobblemon_bridge.adjust_level.<N>` tags applied manually after spawn
  (see `server-gyms` datapack README). Without a tag they're vetoed by
  the entity-join check. A `/gymsummon <N>` helper is planned for a
  future release.

## [0.7.2] - 2026-05-29

### Changed
- **cobblemon-feedback**: explicit consent before uploading a screenshot.
  When `/feedback bug ...` runs and the player has a fresh F2 capture,
  the chat now shows two clickable buttons:
  ```
  You have a screenshot from Ns ago. Upload it publicly with this issue?
   [ Attach screenshot ]   [ Submit without ]
  (auto-cancels in 30s — defaults to text-only)
  ```
  No upload happens until the player clicks `[ Attach screenshot ]`. If
  the player walks away or clicks `[ Submit without ]`, the issue is
  filed text-only. The consent prompt is per-submission — not persisted
  across sessions, so the player is asked every time. Without an existing
  capture, `/feedback` behaves exactly as before (no prompt).
- **cobblemon-feedback-client**: F2 ack message updated to spell out the
  consequence: "If you /feedback in the next 120s, you'll be asked
  whether to upload this screenshot to a public URL on the bug report."

## [0.7.1] - 2026-05-29

### Fixed
- **cobblemon-feedback / cobblemon-feedback-client**: client crash on
  startup with `Cannot register payload cobblemonfeedback:ready as it is
  already registered`. The .mrpack ships both jars to player clients, so
  both mods loaded on the client JVM and raced to register the same
  custom payload IDs, which NeoForge rejects. Annotated each mod's `@Mod`
  class with the dist it actually needs (`DEDICATED_SERVER` for the
  server mod, `CLIENT` for the client mod) so each JVM only loads one of
  the two and the registration only happens once. Single-player
  (integrated server) consequence: `/feedback` doesn't exist there —
  acceptable, since there's no GitHub repo for an SP world's reports.

## [0.7.0] - 2026-05-29

### Added
- **cobblemon-feedback-client** (new mod, client-side only): hooks vanilla
  F2 to additionally hold the rendered frame in client-side memory for 120
  seconds. When the player runs `/feedback bug ...` within that window, the
  server fetches the screenshot via a chunked custom-payload protocol,
  uploads to Cloudflare R2, and embeds the image URL in the GitHub issue
  body. F2's vanilla disk-write behavior is unchanged — players still get
  their on-disk screenshot file as usual.
- **cobblemon-feedback**: server-side R2 client (`R2Client.kt`) doing
  AWS SigV4 PutObject against the R2 S3-compatible API. Configured via
  five new fields in `runtime/config.json`: `r2Endpoint`, `r2Bucket`,
  `r2AccessKeyId`, `r2SecretAccessKey`, `r2PublicUrlBase`. Blank
  `r2Endpoint` disables uploads (issues are still filed text-only).
- **cobblemon-feedback**: chunk-reassembly inbox with concurrent-write
  safety, byte-cap enforcement (8 MB total), and per-request timeout
  (30s). Players who don't have the client mod installed get text-only
  /feedback as before — the server only attempts to fetch a screenshot
  if it has previously seen a `feedback_ready` packet from the player.

### Notes
- See `docs/design/player-feedback-phase2.md` for the full design.
  Phase 2a (PII anonymization) shipped in 0.6.1; this release covers
  phases 2b (client mod + chunked-payload protocol) and 2c (R2 upload).
- The client mod ships in the .mrpack so PrismLauncher users get it
  automatically. Vanilla / non-mrpack clients still get text-only
  /feedback.
- R2 credentials must be added to the per-instance runtime config on
  the VM (never committed). See [README → R2 setup] for the procedure.

## [0.6.2] - 2026-05-28

### Fixed
- **cobblemon-bridge** — carrot-heal + Poké Healer quest now actually awards
  `server:heal_pokemon`. `HealQuestHook`'s `EntityInteract` /
  `RightClickBlock` subscribers ran at NORMAL priority, but Cobblemon's own
  carrot-feed + healing-machine handlers also run at NORMAL and SUCCEED the
  `InteractionResult`, which cancels the event for later subscribers. Bumped
  both to `EventPriority.HIGHEST` so we see the click before Cobblemon
  cancels it.
- **cobblemon-gacha** — `OddsMenu` now shows 0%-weight loot entries
  (Pokémon egg placeholders) with a "Coming soon — not rolling yet" lore
  line, sorted after rollable entries. The old filter hid them entirely so
  the preview looked empty in slots that hadn't shipped a weight yet.

### Added
- **cobblemon-bridge** — global `/spawn` (any player) + `/setspawn` (op
  level 2) + `/clearspawn`. Overrides neoessentials' per-world spawn
  behavior; a single point persisted at
  `config/cobblemon-bridge/runtime/spawn.json` works from every dimension.
- **cobblemon-bridge** — world-rules hook. Pokémon and `rctmod:trainer`
  natural spawns are blocked outside `minecraft:overworld /
  minecraft:the_nether / minecraft:the_end`. Non-op players entering a
  non-progression dimension are forced into Adventure mode + block
  break/place cancelled. Their prior gamemode is restored on exit back to
  a progression dimension. Ops bypass all restrictions; entities tagged
  `cobblemon_bridge.*` (op-summoned trainers, gym leaders, gym-TP NPCs)
  still spawn through.

## [0.6.1] - 2026-05-28

### Changed
- **cobblemon-feedback**: replace raw player username + UUID in public
  GitHub issue bodies with HMAC-derived `anon-XXXXXXXX` reporter IDs.
  Now that the repo is public, raw player identifiers in issue bodies
  expose more than necessary. Maintainers reverse the lookup with the
  new op-only `/feedback whois <anon-id>` (in-memory, since last
  server start) or by grepping `config/cobblemon-feedback/runtime/audit.log`
  on the VM.
- HMAC secret (`anonHmacSecret`) is auto-generated on first boot and
  persisted to the per-instance runtime config. Different secrets on
  dev vs prod produce different anon-IDs for the same player — intentional.

### Notes
- Existing dev/prod configs auto-backfill `anonHmacSecret` on next
  restart. No manual config edit required.
- See `docs/design/player-feedback-phase2.md` for the full design and
  the upcoming Phase 2b/2c work (client mod with screenshot capture,
  Cloudflare R2 upload).

## [0.6.0] - 2026-05-28

### Fixed
- **cobblemon-gacha**: quest reward grants now actually fire. The reward chain
  (advancement → reward function → `_finalize.mcfunction` → `gacha admin grant`)
  was silently failing because datapack functions run at
  `function-permission-level=2` while `gacha admin grant` required permission 4.
  Moved `grant` and `giveegg` out of the `admin` subtree (gated at permission 2
  instead) and updated `_finalize.mcfunction` to call `gacha grant` /
  `gacha giveegg`. The `admin` subtree keeps `setcrate`, `clearcrate`, `force`,
  `reload`.
- **starterkit/Default.txt**: replaced the bundled default kit (leather boots,
  shield, wooden sword, 9 bread) with stone pickaxe + axe + shovel + hoe +
  Cobblemon Pokédex. Now lives in `modpack/server-overrides/config/starterkit/`
  so deploys keep it in sync. (Note: uses `cobblemon:pokedex`. Swap to
  `cobblenav:pokenav_item` if you wanted the PokéNav instead.)

### Added
- **cobblemon-bridge**: gym-warp villager NPC. Tag a vanilla villager
  `cobblemon_bridge.gym_tp_npc` (or use `/gymtp spawn` to create one) and
  right-clicking it opens a chest GUI listing each warp target the player
  has unlocked. Visibility is computed from `server:beat_gym_<N>` advancements:
  gym 1 always shows; gym `N` shows once the player holds `beat_gym_<N-1>`
  (next-up) or `beat_gym_<N>` (revisit). Non-numeric entry ids (e.g. `rotating`,
  `elite4`) require an explicit `unlockAdvancement` set with
  `/gymtp set <id> unlock <advancement>`. Coordinates persisted to
  `config/cobblemon-bridge/runtime/gym_tps.json` (atomic write).
- **cobblemon-bridge**: `/gymtp` admin commands — `set`, `clear`, `list`,
  `spawn`, `delete`. Op level 2.
- **cobblemon-ranked**: `/ranked admin setarena 1|2 [pos rot dim]`,
  `clearpos 1|2`, `showarena` — replace manual edits to `runtime/config.json`.
- **ops/dev-wipe-players.sh**: idempotent wipe of per-player state on
  `cobblemon-dev` (vanilla playerdata/advancements/stats, Cobblemon party/PC/
  pokedex, counter, gacha login history, starter-kit tracking, rctmod player
  stat files). Stops + restarts the dev service.

## [0.5.6] - 2026-05-28

### Changed
- `Deploy dev` split into two jobs: `deploy` (self-hosted, unchanged
  behavior except no longer publishes) and `publish-dev-latest`
  (ubuntu-latest, downloads the mrpack as a workflow artifact and pushes
  it to the rolling `dev-latest` pre-release). Reasons:
  - The self-hosted runner sits behind a residential ~4 MB/s upstream.
    Pushing 120 MB to `uploads.github.com` from there reliably trips
    GitHub Actions' job watchdog and the publish step gets cancelled
    mid-upload (regardless of upload tool — observed with both
    `actions/upload-artifact@v4` and `softprops/action-gh-release@v2`).
  - GitHub Actions internal blob storage *is* fast from this cluster
    (~50 MB/s observed), so handing the mrpack between jobs via a
    workflow artifact is cheap. The publish job runs in GitHub's
    datacenter and pushes to `uploads.github.com` over the backbone.
- Companion change in `gemini-server`: bumped runner pod
  `terminationGracePeriodSeconds` to 600 (was default 30s) to defuse a
  separate ARC scale-down race uncovered during the investigation.

## [0.5.5] - 2026-05-28

### Changed
- `Deploy dev` no longer uses `actions/upload-artifact` for the .mrpack.
  The artifact upload kept racing with the ARC ephemeral-runner lifecycle —
  the controller terminated the pod mid-stream during the ~120 MB upload,
  failing the run with `runner has received a shutdown signal` even though
  the actual deploy succeeded. Workflow artifacts also require sign-in,
  which is incompatible with making the repo public-facing.
- `Deploy dev` now publishes/updates a rolling `dev-latest` GitHub
  pre-release with the .mrpack via `softprops/action-gh-release`, on the
  same self-hosted runner. Stable URL:
  `https://github.com/hspahic-cs/cobblemon-server/releases/tag/dev-latest`.
  Adds a "Clear prior dev-latest assets" step (versioned filenames would
  otherwise accumulate).
- Re-introduced the gh CLI install in the Install build/deploy deps step,
  matching the pattern from `promote-dev-to-prod.yml`.

## [0.5.4] - 2026-05-28

### Fixed
- Flip the remaining `side = "server"` manifests to `side = "both"`:
  cobbleworkers, cobblemon-linkie, cobblemon-unchained, flan, in-control,
  neoessentials, worldedit. NeoForge networking refused client connection
  to 0.5.3 dev/prod with `Channel of mod "Cobbleworkers" failed to connect:
  This channel is missing on the client side, but required on the server`
  (cobbleworkers + 1 other). These mods register network channels at
  startup, so the client must have the jar to negotiate the channel —
  Modrinth's `client_side: unsupported` flag is misleading. Same root cause
  as the 0.5.3 tim-core fix; flipping the rest in one batch closes the
  category.

## [0.5.3] - 2026-05-28

### Fixed
- `cobblemon-tim-core` packwiz manifest flipped from `side = "server"` to
  `side = "both"`. Modrinth tags tim-core as `client: unsupported` so packwiz
  auto-set it server-side, but `cobblemon-counter` (`side = "both"`) declares
  tim-core as a hard dep at NeoForge load time. PrismLauncher import of 0.5.2
  failed at client startup with `Mod cobbled_counter requires tim_core ...
  Currently, tim_core is not installed`. Other server-tagged mods
  (cobblemon-linkie, cobblemon-unchained, cobbleworkers, flan, in-control,
  neoessentials, worldedit) audited — none are required deps of any
  `side = "both"` mod, so they stay server-only.

## [0.5.2] - 2026-05-28

### Fixed
- Pin `cobblemon-tim-core` to `1.7.3-neoforge-1.32.0`. The 0.5.1 deploy
  pulled the latest version (`1.8.0-neoforge-1.32.0-r1`) which requires
  Cobblemon 1.8.0; we're on Cobblemon 1.7.3. Hard mod-load failure on
  the dev server. Now matches what cobblemonvalley actually shipped.

## [0.5.1] - 2026-05-28

### Synced from upstream (almutwakel/cobblemon-mods, 5 commits since 2026-05-26)
- **server-quests datapack**: full sync to friend's production-aligned version.
  91 → 193 files. New reward fns (`reach_income_250`, `reach_party_level_20`,
  `root`, `select_pokemon`, `use_wild`); `reach_party_level_15` removed.
- **cobblemon-ranked**: TeamSelectionMenu reworked to use vanilla
  GENERIC_9x6 MenuType (no client-side menu registration needed).
  `MenuRegistry.kt` deleted.
- **cobblemon-bridge**:
  - New `/wild` command — random surface teleport centered on X=350 Z=−700
    with chunk preloading.
  - New `HealQuestHook` — grants `heal_pokemon` advancement on carrot heal.
- **client keybinds**: appended PokeNav (`O`), location overlay (`'`),
  Xaero waypoint (`;`) to options.txt.

### Added
- **15 third-party mods** that were on cobblemonvalley but missing from the
  packwiz manifests. Adding closes the gap between dev's modpack and the
  upstream server friend has been running. Mods added (with auto-deps in
  parens):
  - **Core gameplay**: neoessentials (homes/warps/tpa — bridge expects this),
    cobbleworkers, cobblemon-unchained (+ counter), starter-kit
  - **Cobblemon QoL**: cobblemon-linkie (+ tim-core), cobblemon-counter,
    cobblemon-pokenav, cobblemon-recobbled (rbrctai trainer AI)
  - **General QoL**: chatbubbles, sophisticated-backpacks (+ sophisticated-core),
    collective, coroutil, what-are-they-up-to (watut)
- Mirrored cobblemonvalley's tuned authored configs into dev for the 6
  in-house cobblemon mods (carrots/config, gacha/{egg_pools,tables},
  market/{config,items}, npc/{rewards,spawn-blocker}, ranked arena coords).

### Fixed
- `options.txt` is now correctly placed in the mrpack. Was at
  `overrides/overrides/options.txt` (double-nested) inside the .mrpack zip,
  which made PrismLauncher lay it down at `<instance>/.minecraft/overrides/options.txt`
  instead of the instance root. MC never saw the keybind/FOV/gamma overrides.
  Moved from `modpack/overrides/options.txt` → `modpack/options.txt` (packwiz
  already wraps everything in `overrides/` during mrpack export — the extra
  folder was decorative and broke the path).

### Skipped (intentionally)
- `cobblemonalphas` and `fightorflight` — friend's notes flag both as
  currently disabled.

## [0.5.0] - 2026-05-27

### Added
- **`cobblemon-feedback` mod** — in-game `/feedback bug <text>` and
  `/feedback suggest <text>` commands. Server captures rich metadata
  (player username + UUID, dimension + biome + coords, TPS, party,
  recent chat buffer, server log tail) and POSTs a labeled GitHub Issue
  to `hspahic-cs/cobblemon-server`.
- Per-player cooldown (60s default, configurable).
- Async HTTP — the player isn't blocked while the issue creates.
- Token + repo configured in `config/cobblemon-feedback/runtime/config.json`
  (runtime path: never shipped via deploy, never in repo).

### Notes
- Phase 1 = server-side only. Phase 2 (later) will add a client mod for
  screenshot capture and Cloudflare R2 upload.

## [0.4.4] - 2026-05-27

### Fixed
- CI: dev-latest pre-release publish step in `Deploy dev` now installs the
  gh CLI on the self-hosted runner before invoking it. Bare runner image
  doesn't ship gh.

## [0.4.3] - 2026-05-27

### Added
- **Authored vs runtime config convention** — every in-house mod now writes
  authored data (design choices like market prices, gym rewards) to
  `config/cobblemon-<mod>/authored/` and runtime data (per-instance state
  like ELO, market stock) to `runtime/`. Deploys ship `authored/`; `runtime/`
  is never touched. See `docs/design/mod-state-vs-config.md`.
- **`Deploy dev` / `Deploy prod`** now rsync `modpack/server-overrides/config/`
  onto the live `config/` directory. Authored config changes ship through CI.
- **`Promote` workflow** (manual `workflow_dispatch`): rsyncs dev's
  `authored/` → prod's, restarts prod, opens an automatic backup PR
  recording the change in the repo.

### Changed
- All 6 in-house mods (cobblemon-{npc,bridge,carrots,gacha,market,ranked})
  refactored to read/write through a per-mod `internal.ConfigPaths` helper.
  Legacy flat-layout configs auto-migrate to the new subdirectories on
  first boot.
- `cobblemon-npc`: removed the deploy-flow test marker from the
  `loaded N profession pools` log line.

## [0.4.2] - 2026-05-27

### Added
- `docs/working-with-mods.md` — end-to-end developer guide covering editing
  mods, deploys, troubleshooting, and rollback.
- README links to the new guide above the Releasing section.

### Changed
- `cobblemon-npc`: profession-pool load log line now appends a deploy-flow
  test marker. Added to verify the CHANGELOG-bump → dev-deploy → prod-deploy
  pipeline works end-to-end. Will be removed in the next release.

## [0.4.1] - 2026-05-27

### Fixed
- Add `cloth-config` (15.0.140-neoforge) packwiz manifest. Cobbreeding declares
  `cloth_config` as a CLIENT-side required dep; server didn't need it (dev VM
  loaded fine without it) but PrismLauncher imports of the mrpack failed at
  client-side mod loading with "Mod cobbreeding requires cloth_config 15 or above".

## [0.4.0] - 2026-05-26

### Added
- **5 NeoForge mods from almutwakel/cobblemon-mods** (merged into `custom-mods/`):
  - `cobblemon-bridge` — tag-driven hooks for level cap, gym progression, E4
    gauntlet, command aliases, egg-by-defeats, RCTmod/Cobreeding/economy
    reflection bridges.
  - `cobblemon-carrots` — carrot-based healing (right-click Pokémon, Poké Healer
    block charges $5/missing carrot).
  - `cobblemon-gacha` — Common/Rare/Ultra lootbox crates with daily/ranked/gym keys.
  - `cobblemon-market` — dynamic-pricing stock market on 6 items with hourly restock.
  - `cobblemon-ranked` — ELO PvP ladder with `/challenge`, `/accept`, decay,
    leaderboard. Replaces the previously-shipped Modrinth `cobblemon-ranked`
    (modid clash).
- **Datapacks** under `modpack/server-overlays/world/datapacks/`:
  `server-quests` (40 mainline quests + reward dispatchers), `server-gyms`
  (24 trainer JSONs), `server-lootballs` (carrot drop fallback). Deploy
  overlays them onto the live world non-destructively.
- **Third-party mod manifests** for bridge runtime deps: `rctmod`, `rctapi`
  (auto), `cobbreeding`, `cobblemon-economy` (Fabric, via Sinytra Connector),
  `flan`.

### Changed
- **CI deploy model**: `deploy-dev` now triggers on CHANGELOG.md changes to
  main (not on tags). Idempotent via `/opt/cobblemon-dev/.deployed_version`.
  `deploy-prod` is workflow_dispatch-only with a "must-be-on-dev-first" guard.
  Tagged releases still draft GitHub Releases via `release.yml`.
- **`pr-validation.yml`** now builds all `custom-mods/*` modules and runs
  `packwiz refresh` on every PR touching modpack or custom-mods.
- All build steps loop over `custom-mods/*/` so adding a new in-house mod
  requires no workflow edits.
- Renamed `mods/` to `reference/` to clarify those folders hold vendored
  upstream sources never built or shipped.

### Removed
- Modrinth `cobblemon-ranked.pw.toml` (modid `cobblemon_ranked` collides with
  the in-house `custom-mods/cobblemon-ranked/`).

### Notes
- `cobblemon-showcase` (Fabric, WIP per friend) deferred for a follow-up.

## [0.3.2] - 2026-05-25

### Added
- CI: `cache-warm.yml` workflow runs on push to main when build inputs change,
  saving a gradle cache on the default branch that tag-triggered releases can
  read. Tag-scoped caches are invisible to other tag runs, so releases were
  always cold without this.

## [0.3.1] - 2026-05-25

### Changed
- CI: gradle cache key no longer hashes `gradle.properties`, so `mod_version`
  bumps don't invalidate the cache on every release.
- CI: packwiz binary cached by commit SHA; `go install` only runs on cache miss.

## [0.3.0] - 2026-05-25

### Changed
- Repo restructure: custom mod moved to `custom-mods/cobblemon-npc/`; reference
  clones of upstream mod sources are no longer tracked (gitignored under
  `mods/`). See `docs/upstream-sources.md` for upstream links and expected
  local layout.
- Tag-driven release CI: `vX.Y.Z` tag triggers a draft GitHub Release with the
  custom mod jar and exported `.mrpack` attached.

## [0.2.2] - 2026-04-25

Initial public-ish state. Modpack at 0.2.2, custom mod at 0.1.0 — versions
unified going forward.
