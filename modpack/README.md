# Modpack (packwiz project)

This folder is the packwiz source for the client-side modpack. The exported `.mrpack` is what friends import into Prism Launcher; this folder is the manifest that produces it.

For end-user install instructions see `../docs/install-client.md`.

## Structure

- `pack.toml` — pack metadata (MC version, NeoForge version, pack version)
- `index.toml` — index of all files with hashes (auto-managed)
- `mods/*.pw.toml` — one file per Modrinth/CurseForge mod (pointer only, no jar)
- `mods/*.jar` — mods bundled directly in the pack (`cobblemon-npc` lives here since it's not on Modrinth); packwiz auto-prefixes these under `overrides/` at export
- `Cobblemon Server-*.mrpack` — the export artifact, gitignore candidate

## Re-exporting after changes

### When a Modrinth/CurseForge mod updates
```
~/go/bin/packwiz update --all
~/go/bin/packwiz refresh
~/go/bin/packwiz mr export
```

### When cobblemon-npc changes
```
cd ../custom-mods/cobblemon-npc
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew build
cp build/libs/cobblemon-npc-*.jar ../../modpack/mods/
cd ../../modpack
~/go/bin/packwiz refresh
~/go/bin/packwiz mr export
```

For an actual release, don't bump `pack.toml` by hand — see the Releasing
section in the root README. The CI workflow rebuilds and exports on tag push.
This local export is for testing changes before tagging.

### When bumping MC / NeoForge versions
```
~/go/bin/packwiz migrate minecraft
~/go/bin/packwiz migrate loader
```

## Adding a mod

```
~/go/bin/packwiz mr add <modrinth-slug>     # Modrinth
~/go/bin/packwiz cf add <curseforge-slug>   # CurseForge (rarely needed)
```

If it's not on either platform, drop the jar in `overrides/mods/`.

## What's intentionally excluded

- **Multiworld** — server-only; clients don't need it
- **Sinytra Connector / LegendaryMonuments** — deferred; see the project plan for status
- Any pure-server tool (future `cobblemon-pvp`, Impactor, etc.)
