import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("java")
    id("net.neoforged.moddev") version "2.0.78"
    kotlin("jvm") version "2.2.20"
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
        register("client") { client() }
    }

    // Required: without an explicit mods block the @Mod constructor never fires under
    // runServer/runClient — FML still lists the mod, so the failure is silent.
    mods {
        register("cobblemon_roguelite") {
            sourceSet(sourceSets.main.get())
        }
    }
}

dependencies {
    implementation("thedarkcolour:kotlinforforge-neoforge:${project.property("kotlin_for_forge_version")}")
    implementation("com.cobblemon:neoforge:${project.property("cobblemon_version")}")

    // Deliberately NOT depended on: our other custom mods (cobblemon-bridge, cobblemon-ranked,
    // cobblemon-poke-ai). This module must stay independently buildable and shippable — see
    // docs/pokerogue-mode-plan.md §2.9. Server-specific integrations (our economy, our arenas,
    // the poke-engine AI bridge) belong on the other side of an interface declared here and
    // implemented in cobblemon-bridge, never as a compile dependency in this direction.

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.slf4j:slf4j-api:2.0.9")
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.9")
}

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks {
    test { useJUnitPlatform() }
    processResources {
        inputs.property("version", project.version)
        filesMatching("META-INF/neoforge.mods.toml") { expand(project.properties) }
    }
    compileJava { options.release = 21 }
    compileKotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_21) } }
}
