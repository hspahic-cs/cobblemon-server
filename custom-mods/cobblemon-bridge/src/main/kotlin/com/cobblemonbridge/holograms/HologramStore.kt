package com.cobblemonbridge.holograms

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

private val log = LoggerFactory.getLogger("cobblemon_bridge/holograms/store")

/**
 * One persistent hologram. `text` is the raw display string — may contain §-color codes or
 * a literal `\n` for newlines. `billboard` is one of `"center" | "vertical" | "horizontal" |
 * "fixed"` (vanilla Display.BillboardConstraints enum names).
 */
data class HologramEntry(
    val position: WarpPos,
    val text: String,
    val billboard: String = "center",
)

private data class HologramFile(
    val entries: MutableMap<String, HologramEntry> = linkedMapOf(),
)

/**
 * Single-file Gson store for `/hologram` metadata. Mirrors the
 * [com.cobblemonbridge.gymtp.GymTpStore] pattern — load once at server start, mutate via
 * commands, atomic write on every change.
 *
 * Holograms ARE durable independent of this file (vanilla text_display entities are persisted
 * in chunk data on save), but the file is the canonical record so admin commands can
 * enumerate and re-spawn missing entries after world swaps.
 */
class HologramStore private constructor(
    private val file: Path,
    private var data: HologramFile,
) {
    fun entries(): Map<String, HologramEntry> = data.entries.toMap()

    fun get(id: String): HologramEntry? = data.entries[id]

    fun set(id: String, entry: HologramEntry) {
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

        fun load(file: Path): HologramStore {
            if (!file.exists()) return HologramStore(file, HologramFile())
            return try {
                val parsed = gson.fromJson(file.readText(), HologramFile::class.java) ?: HologramFile()
                HologramStore(file, HologramFile(linkedMapOf<String, HologramEntry>().apply { putAll(parsed.entries) }))
            } catch (e: JsonSyntaxException) {
                log.warn("holograms.json malformed; starting with empty store", e)
                HologramStore(file, HologramFile())
            } catch (e: Exception) {
                log.warn("holograms.json load failed; starting with empty store", e)
                HologramStore(file, HologramFile())
            }
        }
    }
}
