# Upstream mod sources

This repo does **not** vendor source code from upstream mods. Packwiz pulls
release jars by URL into the modpack, and the cobblemon-npc gradle build pulls
its compileOnly dependencies from public Maven repositories — no local clones
required to build.

| Mod | Upstream | How we depend on it |
|-----|----------|---------------------|
| Cobblemon | https://gitlab.com/cable-mc/cobblemon | Maven: `com.cobblemon:neoforge` from `maven.impactdev.net/repository/development/` |
| Minecolonies | https://github.com/ldtteam/minecolonies | Built from source (see below — no public 1.21.1 Maven yet) |
| Structurize, BlockUI, MultiPiston, Domum Ornamentum | https://github.com/ldtteam/* | Maven: `com.ldtteam:*` from `ldtteam.jfrog.io/artifactory/mods-maven/` |
| Minecraft Comes Alive | https://github.com/Luke100000/minecraft-comes-alive | Reference only |
| Stock Market | https://github.com/Cyborgmas/stock_market | Reference only |
| Cobblemon NPC (Fabric, archived) | https://github.com/Apion4/cobblemon-npc | Predecessor to the NeoForge port in `custom-mods/cobblemon-npc/` |

## Building Minecolonies locally

Minecolonies has not yet published a 1.21.1 artifact to a public Maven repo,
so cobblemon-npc resolves it from `mavenLocal()`. To build cobblemon-npc on a
fresh checkout:

```
git clone --branch version/1.21 https://github.com/ldtteam/minecolonies.git mods/minecolonies
cd mods/minecolonies
./gradlew build
```

Then install the resulting jar to mavenLocal so gradle can resolve it:

```
VER=0.0.11-1.21.1
DIR=~/.m2/repository/com/minecolonies/minecolonies/$VER
mkdir -p "$DIR"
cp build/libs/minecolonies-$VER.jar "$DIR/"
cat > "$DIR/minecolonies-$VER.pom" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.minecolonies</groupId>
  <artifactId>minecolonies</artifactId>
  <version>$VER</version>
  <packaging>jar</packaging>
</project>
EOF
```

CI does the equivalent on every release — see `.github/workflows/release.yml`,
pinned to a specific upstream commit via `MINECOLONIES_REF`.

When ldtteam starts publishing 1.21.1 to their Maven, drop this section,
add a `maven` block for it in `build.gradle`, and remove `mavenLocal()` from
the repositories list.

## Reading source

If you want to read upstream source while working on cobblemon-npc, clone the
repos into `mods/` (gitignored). Cobblemon and the ldtteam libs no longer need
to be cloned for the build to succeed — only Minecolonies does, until it
publishes properly.

```
cobblemon-server/
├── custom-mods/
│   └── cobblemon-npc/        # this repo builds and ships this
└── mods/                      # gitignored
    ├── cobblemon/             # optional — for reading source
    ├── minecolonies/          # required for builds (see above)
    ├── minecraft-comes-alive/ # optional
    ├── stock_market/          # optional
    └── cobblemon-npc-fabric-archive/  # optional
```
