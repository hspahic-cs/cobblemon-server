# Cobblemon Server Docs

Three audiences, three sections.

## I'm a player

Start here:

- **[Installing the modpack](player/installing.md)** — set up PrismLauncher, import the .mrpack
- **[Connecting to the server](player/connecting.md)** — get the IP and join

Then:

- **[Economy](player/economy.md)** — coins, BP, what to spend on what
- **[Gym progression](player/gym-progression.md)** — gym towers, tiers, challenge mode
- **[Ranked PvP](player/ranked-pvp.md)** — ladder, betting, Elo
- **[Teams & factions](player/teams-and-factions.md)** — Valor / Mystic / Instinct, sub-factions, territory
- **[/feedback](player/feedback.md)** — file bugs and suggestions in-game

## I'm an operator or contributor

How to ship a change end-to-end:

- **[Working with mods](dev/working-with-mods.md)** — edit, build, ship to dev, promote to prod
- **[Snapshots](dev/snapshots.md)** — snapshot prod, reset dev to a snapshot
- **[NeoForge dev setup](dev/neoforge-dev-setup.md)** — local environment for editing mods
- **[Server setup](dev/server-setup.md)** — VM layout, systemd units, ports
- **[Upstream sources](dev/upstream-sources.md)** — reference clones in `reference/`

Conventions:

- **[Mod state vs config](dev/conventions/mod-state-vs-config.md)** — authored vs runtime
- **[Schematic build guide](dev/conventions/schematic-build-guide.md)** — WorldEdit workflow

Per-mod implementation guides live next to the code: each `custom-mods/<mod>/README.md`.

## I want to know *why* something is designed this way

Design rationale and decision logs live in [`design/`](design/README.md). These are not
how-to guides; they explain the trade-offs behind the systems players interact with.
