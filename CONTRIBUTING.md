# Contributing

## Before You Start

- Check [Issues](../../issues) to see what's already being worked on
- Comment on an issue before starting work — if there isn't one, create it
- One person per issue at a time to avoid conflicts

## Branch Strategy

Branch off `main` for every change. Never push directly to `main`.

**Naming convention:**
- `feature/gym-leader-teams` — new features
- `fix/pokemart-prices` — bug fixes or corrections
- `config/worldborder-arena` — server config changes
- `docs/readme-update` — documentation only

## Workflow

1. Pull latest `main` before starting
2. Create a branch for your work
3. Make your changes
4. Open a pull request → assign a reviewer
5. Get approval before merging

## What Lives in This Repo

| Tracked | Not Tracked |
|---------|-------------|
| `config/` — server config | `world/` — world files (too large, binary) |
| `datapacks/` — gym system, NPCs | `mods/*.jar` — download from Modrinth |
| `mods/mods.md` — mod manifest | `logs/`, `crash-reports/` |
| `docs/` — design documents | Player data, bans, ops |

## Mod Changes

Do not add or remove mods without discussing first — mod changes affect every contributor and all players. Update `mods/mods.md` with the mod name, version, and Modrinth link when adding.

## Sensitive Files

`server.properties` is tracked but **do not commit RCON passwords or other secrets**. Use environment variables or keep secrets out of the repo entirely.

## Ownership Areas

To avoid stepping on each other, loosely own your area:

| Area | Description |
|------|-------------|
| Gym system | Datapack NPC dialogue, team compositions, rewards |
| Economy | Pokemart stock, prices, Impactor config |
| World / builds | Arena, Elite Four, Pixeltown layout |
| Mods | Adding, removing, or updating mods |
| PvP / Elo | Ranked system implementation |
