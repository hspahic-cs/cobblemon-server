# Cobblemon Server

Modpack + custom mods for our private Pokémon-themed Minecraft server.
Two NeoForge servers (`cobblemon-prod`, `cobblemon-dev`) run on a single
home VM at `192.168.1.20`. Friends connect from the internet via
`108.21.168.120:25565` (prod) / `:25566` (dev).

## Platform

| Component  | Version  |
|------------|----------|
| Minecraft  | 1.21.1   |
| Mod Loader | NeoForge |
| NeoForge   | 21.1.227 |
| Java       | 21       |
| Cobblemon  | 1.7.3    |

## Repo layout

```
cobblemon-server/
├── custom-mods/             6 in-house mods we build (CI ships them)
│   ├── cobblemon-npc/         Minecolonies citizens as gym trainers
│   ├── cobblemon-bridge/      Cobblemon event hooks (gym, level cap, E4)
│   ├── cobblemon-carrots/     Carrot-based healing
│   ├── cobblemon-gacha/       Lootbox crates
│   ├── cobblemon-market/      Dynamic-pricing /market
│   └── cobblemon-ranked/      ELO PvP ladder
├── modpack/                 packwiz manifests + datapacks shipped to server
│   ├── pack.toml              top-level packwiz pack
│   ├── mods/*.pw.toml         third-party mod manifests (Modrinth-fetched)
│   ├── overrides/             client-side files in the .mrpack
│   └── server-overrides/      datapacks rsynced to /opt/cobblemon-*/world/datapacks/
├── docs/                    player + dev + design docs (start at docs/README.md)
├── ops/                     VM-side scripts (snapshot, reset)
├── reference/               local clones of upstream mod source (gitignored)
├── scripts/                 local helper scripts
├── CHANGELOG.md             THE deploy signal — bump this to ship
└── .github/workflows/       CI (PR build, dev/prod deploy, release)
```

## Documentation

Start at **[docs/README.md](docs/README.md)** for the full doc tree. The docs split
into three audiences:

- **[docs/player/](docs/player/README.md)** — playing on the server (install, connect, economy, gyms, PvP)
- **[docs/dev/](docs/dev/README.md)** — operating + contributing (build, ship, snapshots, conventions)
- **[docs/design/](docs/design/README.md)** — design rationale and decision logs

Quick links:
- **[Working with mods (E2E guide)](docs/dev/working-with-mods.md)** — edit a mod, ship to dev, promote to prod
- **[Snapshots and dev resets](docs/dev/snapshots.md)**
- **[Server VM setup](docs/dev/server-setup.md)**
- **[Gym system design](docs/design/gym-system.md)**
- **[PvP Elo design](docs/design/pvp-elo.md)**
- **[Economy design](docs/design/economy-design.md)**

## Deploying

TL;DR for shipping a change:

1. PR your edit. CI builds all 6 mods on every PR.
2. Bump `CHANGELOG.md` with a new `## [X.Y.Z]` heading on a release commit.
3. Merge to main → **dev auto-deploys**.
4. Tag `vX.Y.Z` → GitHub Release with `.mrpack` is drafted.
5. Manually run "Deploy prod" workflow against the tag.

Full guide: [docs/dev/working-with-mods.md](docs/dev/working-with-mods.md).
