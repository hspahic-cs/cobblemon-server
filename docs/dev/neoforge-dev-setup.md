# NeoForge Mod — Local Development Setup

From zero to "Minecraft launches with my mod loaded" for NeoForge 1.21.1.

## Prerequisites

- **Java 21 JDK** — not 17, not 22. Check with `java -version`.
  - macOS: `brew install openjdk@21`
  - Windows/Linux: [Adoptium](https://adoptium.net/) → Temurin 21 (LTS)
- **IntelliJ IDEA Community Edition** (free). VS Code technically works but IntelliJ's mixin support and debugger integration are much better — don't fight this.
- **Git**

## Scaffold the project

1. Grab the NeoForge MDK (Mod Development Kit) for 1.21.1:
   https://github.com/NeoForgeMDKs/MDK-1.21.1-NeoGradle
   → **Code → Download ZIP**, or clone it.
2. Extract to your project folder. Delete the `.git/` directory inside so you can init your own repo.
3. Edit `gradle.properties` — set `mod_id`, `mod_name`, `mod_license`, `mod_version`, `mod_group_id`, `mod_authors`.
4. Rename the example package `com.example.examplemod` → your own.
   In IntelliJ: right-click the package → **Refactor → Rename**.

## Open in IntelliJ

1. **File → Open**, pick the project root (folder containing `build.gradle`). Select **Open as Project**, not "Open as File."
2. IntelliJ starts a Gradle sync. **First sync takes 10–20 minutes** — it downloads Minecraft, deobfuscation mappings, and NeoForge itself. Let it finish without interrupting.
3. Set the project JDK to 21: **File → Project Structure → SDKs → +** → point at your Java 21 install.

## Run in development

After Gradle sync completes, IntelliJ's run configuration dropdown (top-right) shows:

- `runClient` — launches Minecraft client with the mod loaded
- `runServer` — launches a dedicated server with the mod loaded
- `runData` — runs datagen (generates assets/loot at build time)

Click the green ▶ next to `runClient`. Minecraft launches with your mod.

To debug, use the bug icon instead — breakpoints in IntelliJ halt execution when hit.

## The edit-test loop

| Change type | What to do |
|---|---|
| Java/Kotlin code | Stop client → hit `runClient` again (~30s) |
| JSON assets (models, blockstates, recipes, loot) | Don't restart. In-game: F3+T to reload resource packs, or `/reload` for data |
| `build.gradle` / `gradle.properties` | Re-sync Gradle (IntelliJ will prompt) |

## Gotchas

- **First sync fails with "no such JDK"** (common on macOS). Fix: add `org.gradle.java.home=/path/to/jdk-21` to `~/.gradle/gradle.properties`. Find the path with `/usr/libexec/java_home -v 21`.
- **Don't use `-SNAPSHOT` NeoForge versions.** Pin to a stable release (e.g. `21.1.227`) in `gradle.properties`.
- **No need for Prism or a separate launcher during development.** `runClient` launches Minecraft for you.
- **Gradle daemon memory** — if sync is sluggish, add `org.gradle.jvmargs=-Xmx4G` to `~/.gradle/gradle.properties`.

## Next steps

Once `runClient` launches and you see your mod in the in-game mods list (Mods button on title screen):

- Write code under `src/main/java/` or `src/main/kotlin/` (add the Kotlin plugin if you want Kotlin)
- Assets/data go under `src/main/resources/assets/<modid>/` and `src/main/resources/data/<modid>/`
- NeoForge docs: https://docs.neoforged.net/

If something breaks during setup, grab the exact error text from the Gradle console or `runClient` log and ask for help — generic "it doesn't work" is hard to debug remotely.
