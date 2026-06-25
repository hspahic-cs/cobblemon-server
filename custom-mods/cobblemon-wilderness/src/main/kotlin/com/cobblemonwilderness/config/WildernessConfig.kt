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

    /** True if block (x, z) lies inside the box (inclusive of both edges). */
    fun contains(x: Int, z: Int): Boolean {
        val n = normalized()
        return x >= n.minX && x <= n.maxX && z >= n.minZ && z <= n.maxZ
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
    /**
     * When true (default), warn players who are outside the keep-box — on boundary crossing
     * and again on login — that anything they build/store out there will be reset. Only takes
     * effect while [enabled] is true, so players aren't alarmed during the confirm-bases phase.
     */
    val warnPlayersOutsideBox: Boolean = true,
    /** IANA timezone used to render the reset date in player warnings. */
    val displayTimeZone: String = "America/New_York",
    /**
     * Circuit breaker. If a run would delete more than this fraction of a dimension's region
     * files, it aborts and deletes nothing — a safety net against a mis-typed box (e.g. one
     * collapsed to a point). Set to 1.0 to disable.
     */
    val maxDeleteFraction: Double = 0.9,
    /**
     * When true (default), a real (non-dryRun) reset MOVES every to-be-deleted region file into
     * a timestamped snapshot under [backupDir] instead of unlinking it. The move IS the deletion
     * — the chunk still regenerates fresh because the file is gone from world/ — so it adds a
     * restore path at ~no extra disk on the same filesystem. This is a per-prune safety net taken
     * right before the prune; it is SEPARATE from, and not a replacement for, the server's
     * scheduled world snapshot.
     */
    val backupBeforeReset: Boolean = true,
    /**
     * Where prune snapshots go. A relative path resolves against the server (game) dir; an
     * absolute path is used as-is. Default keeps snapshots OUTSIDE the scheduled world-snapshot's
     * scope (which copies world/ and config/cobblemon-*), so the two don't overlap.
     */
    val backupDir: String = "wilderness-snapshots",
    /** Keep this many of the most recent prune snapshots; older ones are deleted after a run. 0 = keep all. */
    val backupRetention: Int = 5,
    /**
     * When true, structures and monuments in chunks OUTSIDE the keep-box are relocated each reset
     * cycle (a per-cycle salt is mixed into structure placement), so a pruned-then-revisited
     * frontier has its landmarks in new spots — terrain itself is unchanged. Off by default: this
     * is a deliberate gameplay change, and it applies to all dimensions' structures beyond the
     * X/Z box (the placement hook has no dimension context), though only the pruned overworld
     * actually regenerates them. Inside the box, placement is always untouched.
     */
    val reseedStructuresOutsideBox: Boolean = false,
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
                val parsed = fromJsonWithDefaults(file.readText())
                // If the file predated newer fields, backfill them on disk so it's complete.
                if (parsed != gson.fromJson(file.readText(), WildernessConfig::class.java)) {
                    save(configDir, parsed)
                }
                parsed
            } catch (e: Exception) {
                CobblemonWilderness.logger.error("Failed to load wilderness config, using defaults", e)
                WildernessConfig()
            }
        }

        /**
         * Parse a config JSON, backfilling fields absent from a pre-snapshot file. Gson
         * instantiates via Unsafe and so bypasses Kotlin default values — a missing field comes
         * back as false/0/null, not its declared default. An older config has no `backupDir`
         * (→ null), which we treat as the sentinel for "this file predates the snapshot fields"
         * and restore all three to their defaults (snapshots ON), rather than silently OFF.
         */
        fun fromJsonWithDefaults(json: String): WildernessConfig {
            val parsed = gson.fromJson(json, WildernessConfig::class.java)
            return if (parsed.backupDir.isNullOrBlank()) {
                val d = WildernessConfig()
                parsed.copy(
                    backupBeforeReset = d.backupBeforeReset,
                    backupDir = d.backupDir,
                    backupRetention = d.backupRetention,
                )
            } else {
                parsed
            }
        }

        fun save(configDir: Path, config: WildernessConfig) {
            val file = ConfigPaths.authored(configDir, "config.json")
            file.parent.createDirectories()
            file.writeText(gson.toJson(config))
        }
    }
}
