# server-baby-legends

Server balance overrides for the
[Baby Legends (Cobblemon)](https://modrinth.com/datapack/baby-legends-cobblemon)
mod (`modpack/mods/baby-legends-cobblemon.pw.toml`). The mod adds 23 baby
pre-evolutions of legendaries (e.g. Raygul, Giragrub, Beta). On this server we
keep them as **rare collectible novelties**, not a path to legendaries.

This datapack makes three changes. Datapack data outranks mod-embedded data, so
all of these win at runtime without editing the mod jar (which would break
packwiz's modrinth auto-update) — same pattern as `server-end-spawn-nerfs`.

## 1. No wild spawns — `data/cobblemon/spawn_pool_world/*.json`

The mod ships each baby legend with a `rare` / `ultra-rare` wild spawn. We never
want legendaries (or their pre-evos) in the wild. Every spawn file is overridden
with `enabled: false` and an empty `spawns` array (23 files, matching the mod's
filenames exactly, including `royal_carbink.json`, the `carbink royal` aspect).

The **only** way to obtain a baby legend is the `Legendary Pokémon Egg` in the
Pokémon (poke-egg) gacha crate at spawn (~0.5% per pull). See
`config/cobblemon-gacha/authored/` (`egg_pools.json` `baby_legend` tier +
`tables/pokemon.json`).

## 2. No evolution into the real legendary — `data/cobblemon/species/custom/*.json`

By default each baby evolves into its legendary at level 50 (e.g. Raygul →
Rayquaza via Dragon Fang). We strip that: every overridden species has
`evolutions: []`. Baby legends are a terminal, standalone form.

## 3. Nerfed stats (not competitively viable) — same species files

Each baby legend's `baseStats` are flattened to a baby-tier statline
(**HP 45 / Atk 35 / Def 35 / SpA 35 / SpD 35 / Spe 35 = BST 220**, ~Cleffa
tier). Upstream several were 450–480 BST; all are now uninteresting in battle.

The species overrides are **full** copies of the mod's species files with only
`baseStats` and `evolutions` changed — everything else (types, abilities,
movepool, egg groups, model hitbox) is preserved verbatim. Regenerate with the
inline transform in the PR / `ops` if the mod version-bumps its species defs.

## Scope notes

- `royal_carbink` (the `carbink royal` aspect → Diancie pre-evo) is **not** a
  standalone species id and is **not** in the egg pool, so it has no crate path
  and — with its wild spawn disabled here — is unobtainable on this server. Its
  stats/evolution are therefore left untouched.

## On a mod version bump

If `baby-legends-cobblemon` adds/renames species, mirror the new
`spawn_pool_world` filenames (one `enabled:false` file each) **and** re-extract
+ re-nerf the new `species/custom` files. Otherwise new baby legends would spawn
wild, evolve into legendaries, and ship full-strength stats.
