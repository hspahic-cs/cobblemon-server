# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

One version covers the entire repo: the modpack (`modpack/pack.toml`) and the
custom mod (`custom-mods/cobblemon-npc/gradle.properties`) move together. The
git tag (`vX.Y.Z`) is the source of truth — see the Releasing section in the
root README.

## [Unreleased]

## [0.7.11] - 2026-05-30

### Added
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

### Changed (cont'd)
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
