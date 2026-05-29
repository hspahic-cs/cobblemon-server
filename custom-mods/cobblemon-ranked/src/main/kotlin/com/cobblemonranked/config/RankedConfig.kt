package com.cobblemonranked.config

import com.cobblemonranked.CobblemonRanked
import com.cobblemonranked.internal.ConfigPaths
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * A single teleport point for a ranked-match arena.
 *
 * `world` is a namespaced dimension id (`minecraft:overworld`, `minecraft:the_nether`, etc.).
 * `yaw`/`pitch` set the player's facing on arrival — typically each player faces the other.
 */
data class ArenaPos(
    val x: Double = 0.0,
    val y: Double = 64.0,
    val z: Double = 0.0,
    val world: String = "minecraft:overworld",
    val yaw: Float = 0.0f,
    val pitch: Float = 0.0f
)

data class RankedConfig(
    val startingElo: Int = 1200,
    val minimumElo: Int = 1000,
    val kFactor: Int = 32,
    val levelCap: Int = 50,
    val maxLegendaries: Int = 1,
    val forcesPerDayPerPair: Int = 1,
    val decayEnabled: Boolean = true,
    val leaderboardSize: Int = 10,
    /**
     * Arena 1 — primary battlefield. `arenaPos1` is where player 1 lands, `arenaPos2` is
     * where player 2 lands. Both must be set for arena 1 to be usable.
     */
    val arenaPos1: ArenaPos? = null,
    val arenaPos2: ArenaPos? = null,
    /**
     * Arena 2 — secondary battlefield used when arena 1 is already in use by another
     * ranked match. Same shape: `arena2Pos1` for player 1, `arena2Pos2` for player 2.
     */
    val arena2Pos1: ArenaPos? = null,
    val arena2Pos2: ArenaPos? = null,
    /**
     * Overflow spawn point. When both arenas are in use the next concurrent match teleports
     * both players to this single position (multiple matches can share — no mutex). Distinct
     * from cobblemon-bridge's `/setspawn` so each subsystem can pick its own coords.
     */
    val spawnPos: ArenaPos? = null,
) {
    fun isArenaConfigured(): Boolean = arenaPos1 != null && arenaPos2 != null
    fun isArena2Configured(): Boolean = arena2Pos1 != null && arena2Pos2 != null
    fun isSpawnConfigured(): Boolean = spawnPos != null
    companion object {
        private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

        fun load(configDir: Path): RankedConfig {
            // config.json mixes design (ELO knobs) and per-world data (arena coords).
            // Treated as runtime so operator edits via /ranked admin setarena don't
            // get overwritten by deploys.
            val file = ConfigPaths.runtime(configDir, "config.json")
            if (!file.exists()) {
                val default = RankedConfig()
                save(configDir, default)
                return default
            }
            return try {
                gson.fromJson(file.readText(), RankedConfig::class.java)
            } catch (e: Exception) {
                CobblemonRanked.logger.error("Failed to load config, using defaults", e)
                RankedConfig()
            }
        }

        fun save(configDir: Path, config: RankedConfig) {
            val file = ConfigPaths.runtime(configDir, "config.json")
            file.parent.createDirectories()
            file.writeText(gson.toJson(config))
        }
    }
}
