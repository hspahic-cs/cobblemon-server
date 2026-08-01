plugins {
    id("java")
    id("net.neoforged.moddev") version "2.0.78"
}

version = project.property("mod_version") as String
group = project.property("maven_group") as String

repositories {
    mavenCentral()
    // MCEF (CinemaMod) — browser library. Serves both the mod artifacts and, at
    // runtime on clients, the java-cef/CEF native binaries the mcef mod downloads.
    maven("https://mcef-download.cinemamod.com/repositories/releases")
}

neoForge {
    version = project.property("neoforge_version") as String

    runs {
        register("client") { client() }
    }

    // Required: without an explicit mods block the @Mod constructor never fires under
    // runClient — FML still lists the mod, so the failure is silent.
    mods {
        register("cobblemon_pokerogue_client") {
            sourceSet(sourceSets.main.get())
        }
    }
}

dependencies {
    // The "common" MCEF artifact bundles the MCEF API *and* the modified java-cef
    // (org.cef.*) classes, so this one compileOnly covers the whole compile classpath.
    // compileOnly on purpose: at runtime the classes come from the mcef-neoforge mod jar
    // the player installs (Modrinth: mcef-neoforge-<version>.jar) — we must not ship
    // MCEF classes in our jar (it's also the clean LGPL posture: link, don't embed).
    compileOnly("com.cinemamod:mcef:${project.property("mcef_version")}")
}

java {
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
