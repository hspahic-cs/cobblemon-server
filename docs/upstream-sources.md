# Upstream mod sources

This repo does **not** vendor source code from upstream mods. Packwiz pulls
release jars by URL into the modpack, and gradle reads compileOnly jars from
local clones in `mods/` (gitignored).

If you want to read source while working on `custom-mods/cobblemon-npc`, clone
these into `mods/` next to your existing layout. Versions listed are the ones
the modpack currently targets — see `modpack/pack.toml` for the authoritative
Minecraft / NeoForge versions.

| Mod | Upstream | Notes |
|-----|----------|-------|
| Cobblemon | https://gitlab.com/cable-mc/cobblemon | Build with `./gradlew build` to produce the dev-shadow jar referenced by `cobblemon_jar` in `gradle.properties` |
| Minecolonies | https://github.com/ldtteam/minecolonies | Build with `./gradlew build`; `minecolonies_jar_glob` resolves the result |
| Minecraft Comes Alive | https://github.com/Luke100000/minecraft-comes-alive | Reference only |
| Stock Market | https://github.com/Cyborgmas/stock_market | Reference only |
| Cobblemon NPC (Fabric, archived) | https://github.com/Apion4/cobblemon-npc | Predecessor to the NeoForge port we maintain in `custom-mods/cobblemon-npc/` |

## Expected local layout

```
cobblemon-server/
├── custom-mods/
│   └── cobblemon-npc/        # this repo builds and ships this
└── mods/                      # gitignored — your reference clones live here
    ├── cobblemon/
    ├── minecolonies/
    ├── minecraft-comes-alive/
    ├── stock_market/
    └── cobblemon-npc-fabric-archive/
```

The `custom-mods/cobblemon-npc/gradle.properties` paths assume this layout.
If you put your clones somewhere else, override `cobblemon_jar` and
`minecolonies_jar_glob` locally rather than committing path changes.
