package com.cobblemonwilderness.config

import com.cobblemonwilderness.CobblemonWilderness
import com.cobblemonwilderness.internal.ConfigPaths
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Persistent wilderness keep-zone. Block coordinates, inclusive. Any chunk wholly
 * OUTSIDE this box is eligible to be reset (regenerated fresh) on the schedule.
 *
 * Resets operate at region-file granularity (512×512 blocks), and a region that
 * overlaps the box even partially is always kept. So the effective protected area
 * rounds OUTWARD to the next 512-block region boundary — i.e. up to ~512 blocks of
 * extra land beyond each edge is spared. That bias is intentional: we never delete
 * a chunk that touches the box.
 */
data class BoundingBox(
    val minX: Int = -20480,
    val minZ: Int = -20480,
    val maxX: Int = 20479,
    val maxZ: Int = 20479,
) {
    fun normalized(): BoundingBox = BoundingBox(
        minX = minOf(minX, maxX),
        minZ = minOf(minZ, maxZ),
        maxX = maxOf(minX, maxX),
        maxZ = maxOf(minZ, maxZ),
    )

    /**
     * Expands the box outward so each edge lands exactly on a region boundary: mins drop
     * to the start of their region, maxes rise to the end of theirs. The result contains
     * only whole regions, so no region straddles an edge and the kept area equals the box
     * with zero rounding slop.
     */
    fun snappedToRegions(): BoundingBox {
        val n = normalized()
        return BoundingBox(
            minX = Math.floorDiv(n.minX, REGION_SIZE) * REGION_SIZE,
            minZ = Math.floorDiv(n.minZ, REGION_SIZE) * REGION_SIZE,
            maxX = Math.floorDiv(n.maxX, REGION_SIZE) * REGION_SIZE + REGION_SIZE - 1,
            maxZ = Math.floorDiv(n.maxZ, REGION_SIZE) * REGION_SIZE + REGION_SIZE - 1,
        )
    }

    companion object {
        /** Side length of a region file in blocks (32 chunks). */
        const val REGION_SIZE = 512
    }
}

/**
 * Server-wide wilderness-reset knobs. Both safety gates default to the safe value:
 *  - `enabled = false`  → the mod does nothing at all until you flip this.
 *  - `dryRun = true`    → even once enabled, the first runs only LOG what they would
 *                          delete. Flip to false only after you've eyeballed the logs.
 *
 * Recommended rollout: confirm every player base sits inside `box`, enable with
 * dryRun=true, restart, read the "would delete" report, then set dryRun=false.
 */
data class WildernessConfig(
    val enabled: Boolean = false,
    val dryRun: Boolean = true,
    /** Days between automatic resets. <= 0 disables the automatic schedule (manual only). */
    val intervalDays: Int = 14,
    /** Dimensions to clean, by id. Default: overworld only. */
    val dimensions: List<String> = listOf("minecraft:overworld"),
    val box: BoundingBox = BoundingBox(),
    /**
     * When true (default), the box is expanded outward to whole-region boundaries before
     * use, so the enforced keep-zone is exactly region-aligned (no rounding slop) and is
     * reported as such by /wildreset status and preview. Set false to use the box verbatim
     * (boundary-straddling regions are still kept either way).
     */
    val snapToRegions: Boolean = true,
) {
    /** The box actually enforced — region-aligned when [snapToRegions] is on. */
    fun effectiveBox(): BoundingBox = if (snapToRegions) box.snappedToRegions() else box.normalized()
    companion object {
        private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

        fun load(configDir: Path): WildernessConfig {
            val file = ConfigPaths.authored(configDir, "config.json")
            if (!file.exists()) {
                val default = WildernessConfig()
                save(configDir, default)
                return default
            }
            return try {
                gson.fromJson(file.readText(), WildernessConfig::class.java)
            } catch (e: Exception) {
                CobblemonWilderness.logger.error("Failed to load wilderness config, using defaults", e)
                WildernessConfig()
            }
        }

        fun save(configDir: Path, config: WildernessConfig) {
            val file = ConfigPaths.authored(configDir, "config.json")
            file.parent.createDirectories()
            file.writeText(gson.toJson(config))
        }
    }
}
