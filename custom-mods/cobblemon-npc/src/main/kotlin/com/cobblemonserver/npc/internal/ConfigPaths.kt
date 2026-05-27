package com.cobblemonserver.npc.internal

import com.cobblemonserver.npc.CobblemonNpc
import net.neoforged.fml.loading.FMLPaths
import java.nio.file.Files
import java.nio.file.Path

/**
 * Resolves authored/runtime paths and migrates legacy flat-layout files to the
 * new authored/ + runtime/ split on first boot.
 *
 * See docs/design/mod-state-vs-config.md.
 *
 *   config/cobblemon-npc/authored/<name>   ← design data, ships from repo
 *   config/cobblemon-npc/runtime/<name>    ← per-instance state, never shipped
 *
 * Legacy flat layout (config/cobblemon-npc/<name>) is auto-migrated on first
 * call. Subsequent calls are no-ops.
 */
internal object ConfigPaths {
    private const val MOD_ID = "cobblemon-npc"
    private val root: Path
        get() = FMLPaths.CONFIGDIR.get().resolve(MOD_ID)

    fun authored(name: String): Path = migrateThen(root.resolve("authored").resolve(name), name)
    fun runtime(name: String): Path = migrateThen(root.resolve("runtime").resolve(name), name)

    /**
     * If [target] doesn't exist but a legacy flat-layout file with [name] does,
     * move it to [target]. Idempotent: once moved, the legacy path no longer
     * exists, so this becomes a no-op.
     */
    private fun migrateThen(target: Path, name: String): Path {
        val legacy = root.resolve(name)
        if (!Files.exists(target) && Files.exists(legacy)) {
            try {
                Files.createDirectories(target.parent)
                Files.move(legacy, target)
                CobblemonNpc.logger.info("$MOD_ID: migrated $name from legacy path to ${target.parent.fileName}/")
            } catch (e: Exception) {
                CobblemonNpc.logger.warn("$MOD_ID: failed to migrate $name: ${e.message}")
            }
        }
        return target
    }
}
