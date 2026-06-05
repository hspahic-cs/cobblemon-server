package com.cobblemonbridge.tower

import com.cobblemonbridge.gymtp.WarpPos
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonSyntaxException
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

private val log = LoggerFactory.getLogger("cobblemon_bridge/tower/store")

/**
 * Battle-tower runtime state persisted to `config/cobblemon-bridge/runtime/tower.json`.
 * Same single-file-JSON pattern as [com.cobblemonbridge.spawn.SpawnStore].
 *
 * What persists (everything that must survive a restart):
 *   - [TowerFile.floors] — the three configured floor positions ("1"/"2"/"3" → [WarpPos]),
 *     set once by an op via `/tower setfloor`.
 *   - [TowerFile.lastRotatedEpochDay] — the epoch-day of the last NPC rotation, so a restart
 *     mid-day doesn't re-roll the leaders (the daily pick is deterministic anyway, but
 *     re-summoning would duplicate NPCs if the kill-selector missed an unloaded chunk).
 *   - [TowerFile.clearedEpochDay] — per-player (uuid string) epoch-day of their last tower
 *     clear; gates the rare-key grant to once per day.
 *
 * What deliberately does NOT persist: active run state (current floor, party snapshot,
 * heal-void flag). A restart mid-run just resets the run — same contract as the E4 gauntlet,
 * and retry-until-reset means the player loses nothing but a few minutes.
 *
 * Keys are strings (not Int/UUID) because Gson map keys are JSON object keys.
 */
private data class TowerFile(
    val floors: Map<String, WarpPos> = emptyMap(),
    val lastRotatedEpochDay: Long? = null,
    val clearedEpochDay: Map<String, Long> = emptyMap(),
)

class TowerStore private constructor(
    private val file: Path,
    private var data: TowerFile,
) {
    fun floor(n: Int): WarpPos? = data.floors[n.toString()]

    /** All three floors set → the tower is operational and rotation may summon. */
    fun floorsConfigured(): Boolean = (1..3).all { data.floors.containsKey(it.toString()) }

    fun setFloor(n: Int, pos: WarpPos) {
        require(n in 1..3) { "tower floor must be 1..3 (got $n)" }
        data = data.copy(floors = data.floors + (n.toString() to pos))
        save()
    }

    fun lastRotatedEpochDay(): Long? = data.lastRotatedEpochDay

    fun setLastRotatedEpochDay(day: Long) {
        data = data.copy(lastRotatedEpochDay = day)
        save()
    }

    fun clearedEpochDay(uuid: java.util.UUID): Long? = data.clearedEpochDay[uuid.toString()]

    fun markCleared(uuid: java.util.UUID, day: Long) {
        data = data.copy(clearedEpochDay = data.clearedEpochDay + (uuid.toString() to day))
        save()
    }

    private fun save() {
        file.parent.createDirectories()
        val tmp = file.resolveSibling(file.fileName.toString() + ".tmp")
        tmp.writeText(gson.toJson(data))
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    companion object {
        private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

        fun load(file: Path): TowerStore {
            if (!file.exists()) return TowerStore(file, TowerFile())
            return try {
                val parsed = gson.fromJson(file.readText(), TowerFile::class.java) ?: TowerFile()
                TowerStore(file, parsed)
            } catch (e: JsonSyntaxException) {
                log.warn("tower.json malformed; starting with empty tower state", e)
                TowerStore(file, TowerFile())
            } catch (e: Exception) {
                log.warn("tower.json load failed; starting with empty tower state", e)
                TowerStore(file, TowerFile())
            }
        }
    }
}
