# Mod state vs config

## Structure

```
config/cobblemon-<modname>/
├── authored/         design data — ships from repo, deploys overwrite
│   └── *.json
└── runtime/          per-instance state — never in repo, deploys never touch
    └── *.json
```

Mods read from `authored/`, write player state to `runtime/`.

## Summary

Mods write two kinds of data to disk: **design choices we author** (market
prices, drop tables, gym rewards) and **player state we accumulate** (ELO
records, market stock, last-login dates). Mods don't separate them by
default — everything lands in the same `config/<mod>/` directory. That
makes deploys risky: shipping a config change to prod could trample player
state unless we're careful.

We split the two by convention. Each in-house mod's config dir has an
`authored/` subdir (ships from the repo, deploys overwrite) and a
`runtime/` subdir (never in the repo, never touched by deploy). Every file
lives in exactly one of them. Deploy rsyncs only `authored/`. Player data
in `runtime/` survives every deploy untouched.

This works because we own the source of all 6 in-house mods. Third-party
mods are out of scope — we treat them as configure-once-per-environment.

## Dev flow

### To tweak a config live and ship it

1. Edit the config on **dev** in-game (RCON or admin command — e.g. `/market admin setbase`).
2. Mod writes the change to `/opt/cobblemon-dev/config/cobblemon-<mod>/authored/<file>`.
3. Run the **Promote** workflow when you're happy with the change.
4. Promote rsyncs dev's `authored/` → prod's `authored/`, restarts prod.
5. Promote also opens an automatic PR adding the change to the repo as a
   backup. Optional to merge — the live edit is already on prod.

### To make a permanent change via the repo

1. Edit `modpack/server-overrides/config/cobblemon-<mod>/authored/<file>`.
2. Bump `CHANGELOG.md` to a new version.
3. Push to main. Deploy dev fires automatically.
4. Manually run Deploy prod (or Promote) when ready.

### What's touched on deploy

| | Replaced | Untouched |
|---|---|---|
| Mod jars                  | ✅ | |
| Datapacks                 | ✅ | |
| `authored/` (design)      | ✅ | |
| `runtime/` (player state) |    | ✅ |
| `world/`                  |    | ✅ |
| Third-party mod configs   |    | ✅ (opt-in per file) |

### Gotchas

- Tweaking a file on dev AND committing a different change to it = next deploy clobbers the dev edit. Promote first.
- Mod-version upgrades may need one-time data fixes. Check the mod's MIGRATION notes.
- Third-party mods don't follow this convention. Stay hands-off unless opting in.

---

## Reference

### Per-mod inventory

| Mod | Authored | Runtime |
|---|---|---|
| **bridge**  | (none — datapack-only)                                  | (none)                                |
| **carrots** | `config.json`                                           | (none)                                |
| **gacha**   | `egg_pools.json`, `tables/{common,rare,ultra}.json`     | `config.json` (crate coords + animation tuning), `players.json` |
| **market**  | `config.json`, `items.json`                             | `state.json`, `player_spend.json`     |
| **npc**     | `spawn-blocker.json`, `rewards.json`, `gym-leader-pool.json`, `profession-pools.json` | (none) |
| **ranked**  | (none — `config.json` mixes ELO knobs + arena coords, treated as runtime) | `config.json`, `elo.json`, `teams/<uuid>.json` |

### Migration on first boot

- `authored/` + `runtime/` both exist → no-op
- Neither exists, legacy files in parent dir → mod moves them to the right subdir
- Fresh install → mod extracts bundled defaults to `authored/`

### Deploy rsync

```
rsync -rltzO modpack/server-overrides/config/ \
  deployer@192.168.1.20:/opt/cobblemon-<env>/config/
```

Repo only contains `authored/` paths, so the rsync touches design data only.

### Promote workflow

`promote-dev-to-prod.yml` (workflow_dispatch):

1. Rsync `dev:/opt/.../authored/` → `prod:/opt/.../authored/`
2. Open auto-PR adding the dev-side authored configs to the repo (backup)

### Opting in a third-party file

Drop it at `modpack/server-overrides/config/<mod-name>/<file>`. Deploy
picks it up.
