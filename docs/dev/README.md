# Operator & contributor docs

Anything you need to ship a change end-to-end.

## Common workflows

- **[Working with mods](working-with-mods.md)** — the full edit→ship→prod loop
- **[Snapshots](snapshots.md)** — snapshot prod, reset dev to a snapshot
- **[NeoForge dev setup](neoforge-dev-setup.md)** — local environment

## Reference

- **[Server setup](server-setup.md)** — VM layout, systemd units, ports, accounts
- **[Upstream sources](upstream-sources.md)** — reference clones in `reference/` (cobblemon, neoforge)

## Conventions

- **[Mod state vs config](conventions/mod-state-vs-config.md)** — authored vs runtime, what gets shipped where
- **[Schematic build guide](conventions/schematic-build-guide.md)** — WorldEdit schematic workflow

## Per-mod docs

Each custom mod owns its own implementation guide as a README in its directory:

- `custom-mods/cobblemon-bridge/README.md`
- `custom-mods/cobblemon-feedback/README.md`
- `custom-mods/cobblemon-feedback-client/README.md`
- `custom-mods/cobblemon-gacha/README.md`
- `custom-mods/cobblemon-market/README.md`
- `custom-mods/cobblemon-npc/README.md`
- `custom-mods/cobblemon-ranked/README.md`
- `custom-mods/cobblemon-carrots/README.md`

(Not all of these exist yet — they get filled in as each mod's behavior stabilizes.)
