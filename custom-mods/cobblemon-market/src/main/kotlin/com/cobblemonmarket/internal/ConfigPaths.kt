package com.cobblemonmarket.internal

import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path

/**
 * Resolves authored/runtime paths under `config/cobblemon-market/` and
 * migrates legacy flat-layout files on first boot.
 *
 * See docs/design/mod-state-vs-config.md.
 */
internal object ConfigPaths {
    private const val MOD_ID = "cobblemon-market"
    private val log = LoggerFactory.getLogger("$MOD_ID/config-paths")

    fun authored(configDir: Path, name: String): Path =
        migrateThen(configDir.resolve(MOD_ID).resolve("authored").resolve(name), configDir, name)

    fun runtime(configDir: Path, name: String): Path =
        migrateThen(configDir.resolve(MOD_ID).resolve("runtime").resolve(name), configDir, name)

    private fun migrateThen(target: Path, configDir: Path, name: String): Path {
        val legacy = configDir.resolve(MOD_ID).resolve(name)
        if (!Files.exists(target) && Files.exists(legacy)) {
            try {
                Files.createDirectories(target.parent)
                Files.move(legacy, target)
                log.info("Migrated $name from legacy path to ${target.parent.fileName}/")
            } catch (e: Exception) {
                log.warn("Failed to migrate $name: ${e.message}")
            }
        }
        return target
    }
}
