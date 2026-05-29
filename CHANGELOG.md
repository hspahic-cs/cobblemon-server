# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

One version covers the entire repo: the modpack (`modpack/pack.toml`) and the
custom mod (`custom-mods/cobblemon-npc/gradle.properties`) move together. The
git tag (`vX.Y.Z`) is the source of truth — see the Releasing section in the
root README.

## [Unreleased]

## [0.7.6] - 2026-05-29

### Fixed
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
- **cobblemon-bridge / NpcFaceNearestPlayer**: market villagers were
  spawned with `NoAI:1b` so they wouldn't wander off their placed
  position — but the side effect was a frozen head-locked stare. New
  server-side tick hook walks loaded villagers every 10 ticks, picks
  those carrying `cobblemon_bridge.market_vendor[.<scope>]`, finds the
  nearest player within 12 blocks, and snaps the villager's yaw to
  face them. No-op when no player is in range, so the vendor keeps
  its last orientation rather than spinning. Cost is bounded by
  loaded villagers per dim; trivial at the entity counts the server
  operates at.
- **cobblemon-market / paged shop menu**: large vendors (`tm_normal`
  ~169 entries; `tm_psychic` ~53; `tm_fighting` 45) were truncated to
  the first 45 items by the single-page layout. Row 0 now hosts a
  `Previous Page` arrow at slot 0, the balance display at slot 4, and
  a `Next Page` arrow at slot 8. Each page shows up to 45 items in
  stable registration order. Arrows only appear when there's a page
  to go to; nav lore reads `Page X / Y`. The page state is per-menu —
  closing and re-opening returns to page 1.

### Changed
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
