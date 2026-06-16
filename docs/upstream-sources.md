# Upstream mod sources

This repo does **not** vendor source code from upstream mods. Packwiz pulls
release jars by URL into the modpack, and the cobblemon-npc gradle build pulls
its compileOnly dependencies from public Maven repositories, no local clones
required to build.

| Mod | Upstream | How we depend on it |
|-----|----------|---------------------|
| Cobblemon | https://gitlab.com/cable-mc/cobblemon | Maven: `com.cobblemon:neoforge` from `maven.impactdev.net/repository/development/` |
| Minecolonies | https://github.com/ldtteam/minecolonies | Maven: `com.ldtteam:minecolonies` from `ldtteam.jfrog.io/artifactory/modding/` |
| Structurize, BlockUI, MultiPiston, Domum Ornamentum | https://github.com/ldtteam/* | Maven: `com.ldtteam:*` from `ldtteam.jfrog.io/artifactory/modding/` |
| Minecraft Comes Alive | https://github.com/Luke100000/minecraft-comes-alive | Reference only |
| Stock Market | https://github.com/Cyborgmas/stock_market | Reference only |
| Cobblemon NPC (Fabric, archived) | https://github.com/Apion4/cobblemon-npc | Predecessor to the NeoForge port in `custom-mods/cobblemon-npc/` |

## Reading source

If you want to read upstream source while working on cobblemon-npc, clone the
repos into `mods/` (gitignored). None of them need to be cloned for the build
to succeed, the build resolves everything from public Maven.

```
cobblemon-server/
├── custom-mods/
│   └── cobblemon-npc/        # this repo builds and ships this
└── mods/                      # gitignored, optional reference clones
    ├── cobblemon/
    ├── minecolonies/
    ├── minecraft-comes-alive/
    ├── stock_market/
    └── cobblemon-npc-fabric-archive/
```

## Note on Minecolonies groupId

Minecolonies migrated from `com.minecolonies:minecolonies` to
`com.ldtteam:minecolonies` around the 1.18 era. The legacy coordinate on
`mods-maven` was abandoned at `1.0.180-ALPHA` (2021). Always use
`com.ldtteam:minecolonies` for any modern build.
