# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

One version covers the entire repo: the modpack (`modpack/pack.toml`) and the
custom mod (`custom-mods/cobblemon-npc/gradle.properties`) move together. The
git tag (`vX.Y.Z`) is the source of truth — see the Releasing section in the
root README.

## [Unreleased]

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
