# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

One version covers the entire repo: the modpack (`modpack/pack.toml`) and the
custom mod (`custom-mods/cobblemon-npc/gradle.properties`) move together. The
git tag (`vX.Y.Z`) is the source of truth — see the Releasing section in the
root README.

## [Unreleased]

## [0.6.3] - 2026-05-29

### Added
- **cobblemon-bridge** — `/hologram` admin (op 2): `set/move/delete/list/respawn-all`
  + `set <id> billboard <center|vertical|horizontal|fixed>`. Server-side
  persistent floating text via vanilla `text_display` entities. Metadata
  at `config/cobblemon-bridge/runtime/holograms.json`. No client mod needed.
- **cobblemon-bridge** — `/wild admin show|setcenter|setradius|setcooldown`.
  Center/radius/cooldown now persisted at `config/cobblemon-bridge/runtime/wild.json`
  instead of hardcoded.
- **cobblemon-ranked** — disconnect-mid-battle = forfeit. `PlayerLoggedOutEvent`
  subscriber resolves the active match with the opponent as winner (real ELO update),
  teleports both back to captured pre-arena positions, and ends the
  Cobblemon-side battle. New `/ranked admin forfeit <player>` for stuck matches.
- **SimpleTMs 2.3.3** — Modrinth `yFqR0DNc/uaKfPMYS`. TMs + TRs as physical
  items for teaching Pokémon moves. Client+server, `side = "both"`.

### Fixed
- **cobblemon-bridge** — `WorldRulesHook.onEntityJoin` belt-and-suspenders
  removed. It was canceling `/function server:gym/spawn_<N>` trainer summons
  before the function's own `tag` line could stamp them as op-spawned.
  `FinalizeSpawnEvent` already handles the natural-vs-command discrimination.
- **cobblemon-bridge** — `WorldRulesHook.onFinalizeSpawn` now globally cancels
  natural vanilla `MobCategory.MONSTER` spawns (hostile mobs) while leaving
  peaceful mobs alone. Op-initiated spawns (commands, eggs, /summon, dispenser)
  still go through. Difficulty stays Easy.
- **starterkit** — `cobblemon:pokedex` → `cobblemon:pokedex_red`. The bare
  id isn't a registered item in Cobblemon 1.7.3 (`Unknown registry key` in
  logs); only color variants exist. Red is the canonical starting Pokédex.
  Also added 24 baked potatoes for early food.
- **quest text** — `set_home`, `farm_carrots`, `use_wild` reward chat text
  corrected: apricorn-plant hint, 5 bone meal (was 3), Great Ball recipe is
  2 blue + 2 red apricorns + 1 iron nugget (not "4 blue + 1 iron ingot").
- **server-gyms datapack** — rebased from `data/server/` namespace into
  `data/rctmod/` so RCT actually finds the trainer JSONs (entities reference
  bare ids which default-resolve to `rctmod:`). Includes 10 challenge
  variants + 34 loot tables that were missing. Sourced from valley.

### Changed
- **modpack/options.txt** — `tutorialStep:none` so fresh client installs
  skip the "Move with WASD" / "Punch a tree" popups.
- **modpack/server-overrides/config/neoessentials/config.json** — full pinned
  config (858 lines). `allowUnsafeCommands: true` + four `enable*Safety:
  false` flags applied. Frozen baseline; diff future upstream defaults
  rather than letting CI silently revert in-flight edits.

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
- **server-hide-advancements** datapack — overrides the five vanilla
  advancement roots (`minecraft:story/root`, `nether/root`,
  `adventure/root`, `husbandry/root`, `end/root`) and `cobblemon:root` with
  no-display versions so the F screen only shows `server:*` progression.
  Mod-namespace tabs from new mods (minecolonies, cobbleworkers, …) will
  still appear — add overrides as needed.

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
