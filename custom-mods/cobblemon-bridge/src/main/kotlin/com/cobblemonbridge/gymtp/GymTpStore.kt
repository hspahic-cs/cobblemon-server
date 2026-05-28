package com.cobblemonbridge.gymtp

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

private val log = LoggerFactory.getLogger("cobblemon_bridge/gymtp/store")

/**
 * One teleport target for the gym-warp villager menu. Coordinate shape mirrors
 * `cobblemon-ranked`'s `ArenaPos` (kept independent — the two mods don't depend on each other).
 */
data class WarpPos(
    val x: Double = 0.0,
    val y: Double = 64.0,
    val z: Double = 0.0,
    val world: String = "minecraft:overworld",
    val yaw: Float = 0.0f,
    val pitch: Float = 0.0f,
)

/**
 * Per-entry config. [unlockAdvancement] is optional: numeric ids fall back to the standard
 * `server:beat_gym_<N-1>` (or always-visible for `id=1`) when null; non-numeric ids must
 * supply one or they stay hidden. [label] overrides the auto-formatted display name.
 */
data class GymEntry(
    val position: WarpPos,
    val unlockAdvancement: String? = null,
    val label: String? = null,
)

/**
 * Disk shape: `{ "entries": { "<id>": GymEntry, ... } }`. Keeping the wrapper object lets us
 * add sibling fields later (defaults, version) without breaking the on-disk file.
 */
private data class GymTpFile(
    val entries: MutableMap<String, GymEntry> = linkedMapOf(),
)

/**
 * Single-file Gson store. Atomic write via temp + rename so a crashed JVM doesn't truncate
 * the live file. Single-threaded server-tick discipline keeps mutations race-free; no locks.
 */
class GymTpStore private constructor(
    private val file: Path,
    private var data: GymTpFile,
) {
    /** Read-only snapshot of entries in insertion order. */
    fun entries(): Map<String, GymEntry> = data.entries.toMap()

    fun get(id: String): GymEntry? = data.entries[id]

    fun set(id: String, entry: GymEntry) {
        data.entries[id] = entry
        save()
    }

    fun remove(id: String): Boolean {
        val removed = data.entries.remove(id) != null
        if (removed) save()
        return removed
    }

    private fun save() {
        file.parent.createDirectories()
        val tmp = file.resolveSibling(file.fileName.toString() + ".tmp")
        tmp.writeText(gson.toJson(data))
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    companion object {
        private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

        fun load(file: Path): GymTpStore {
            if (!file.exists()) return GymTpStore(file, GymTpFile())
            return try {
                val parsed = gson.fromJson(file.readText(), GymTpFile::class.java) ?: GymTpFile()
                // Gson's default Map deserialization uses LinkedHashMap, so insertion order is
                // preserved across reload. Defensive: rebuild as linkedMap to be sure.
                GymTpStore(file, GymTpFile(linkedMapOf<String, GymEntry>().apply { putAll(parsed.entries) }))
            } catch (e: JsonSyntaxException) {
                log.warn("gym_tps.json malformed; starting with empty store", e)
                GymTpStore(file, GymTpFile())
            } catch (e: Exception) {
                log.warn("gym_tps.json load failed; starting with empty store", e)
                GymTpStore(file, GymTpFile())
            }
        }
    }
}
