plugins {
    id("java")
    id("net.neoforged.moddev") version "2.0.78"
}

version = project.property("mod_version") as String
group = project.property("maven_group") as String

repositories {
    mavenCentral()
    maven("https://artefacts.cobblemon.com/releases")
    maven("https://thedarkcolour.github.io/KotlinForForge/")
}

neoForge {
    version = project.property("neoforge_version") as String

    runs {
        register("server") { server(); programArguments.add("--nogui") }
    }

    // Required: without an explicit mods block the @Mod constructor never fires under
    // runServer — FML still lists the mod, so the failure is silent.
    mods {
        register("cobblemon_pokerogue_bridge") {
            sourceSet(sourceSets.main.get())
        }
    }
}

dependencies {
    // mariadb-java-client ships inside our jar via NeoForge jar-in-jar (it is not a mod and
    // not otherwise on a server's classpath). JDBC driver for the read-only pokeroguedb poll.
    // NOTE deliberately NO zstd/blob-decoding dependency: sessionSaveData.data is
    // zstd-wrapped Go gob, not JSON — run detail comes from the bridgeRunState side table
    // our patched rogueserver maintains (see db/PokerogueDb).
    "jarJar"("org.mariadb.jdbc:mariadb-java-client") {
        version {
            strictly("[3.0,4.0)")
            prefer(project.property("mariadb_version") as String)
        }
    }
    implementation("org.mariadb.jdbc:mariadb-java-client:${project.property("mariadb_version")}")
    // Presentation layer (dream ghosts) spawns Cobblemon PokemonEntity; Kotlin stdlib is
    // needed to compile Java against Cobblemon's Kotlin-authored API.
    implementation("thedarkcolour:kotlinforforge-neoforge:${project.property("kotlin_for_forge_version")}")
    implementation("com.cobblemon:neoforge:${project.property("cobblemon_version")}")
}

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks {
    processResources {
        inputs.property("version", project.version)
        filesMatching("META-INF/neoforge.mods.toml") { expand(project.properties) }
    }
    compileJava { options.release = 21 }
}
