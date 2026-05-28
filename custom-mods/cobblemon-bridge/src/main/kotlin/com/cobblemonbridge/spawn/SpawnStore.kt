package com.cobblemonbridge.spawn

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

private val log = LoggerFactory.getLogger("cobblemon_bridge/spawn/store")

/**
 * Single global spawn point — one WarpPos persisted to `config/cobblemon-bridge/runtime/spawn.json`.
 *
 * Purpose: replace neoessentials' per-world `/spawn` (every dimension gets its own spawn point)
 * with a single global teleport target that always lands the player at the configured location
 * regardless of which world they ran the command from.
 *
 * Reuses [WarpPos] from `gymtp/` — same coordinate shape; no point introducing a parallel type.
 */
private data class SpawnFile(val position: WarpPos? = null)

class SpawnStore private constructor(
    private val file: Path,
    private var data: SpawnFile,
) {
    fun get(): WarpPos? = data.position

    fun set(position: WarpPos) {
        data = SpawnFile(position)
        save()
    }

    fun clear() {
        data = SpawnFile(null)
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

        fun load(file: Path): SpawnStore {
            if (!file.exists()) return SpawnStore(file, SpawnFile())
            return try {
                val parsed = gson.fromJson(file.readText(), SpawnFile::class.java) ?: SpawnFile()
                SpawnStore(file, parsed)
            } catch (e: JsonSyntaxException) {
                log.warn("spawn.json malformed; starting with no spawn point", e)
                SpawnStore(file, SpawnFile())
            } catch (e: Exception) {
                log.warn("spawn.json load failed; starting with no spawn point", e)
                SpawnStore(file, SpawnFile())
            }
        }
    }
}
