package com.cobblemonbridge.wild

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

private val log = LoggerFactory.getLogger("cobblemon_bridge/wild/store")

/**
 * On-disk shape for `/wild` runtime config. Mirrors the in-code defaults on [WildCommand]
 * so an unset field keeps the prior behavior.
 */
data class WildConfig(
    val centerX: Int = 350,
    val centerZ: Int = -700,
    val radius: Int = 250,
    val cooldownSeconds: Int = 60,
)

/**
 * Persistent store for `/wild` settings. Single JSON file at
 * `config/cobblemon-bridge/runtime/wild.json`. Loaded once at server start, mutated via
 * admin commands, atomic-written on every change.
 */
class WildStore private constructor(
    private val file: Path,
    private var data: WildConfig,
) {
    fun get(): WildConfig = data

    fun update(transform: (WildConfig) -> WildConfig) {
        data = transform(data)
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

        fun load(file: Path): WildStore {
            if (!file.exists()) return WildStore(file, WildConfig())
            return try {
                val parsed = gson.fromJson(file.readText(), WildConfig::class.java) ?: WildConfig()
                WildStore(file, parsed)
            } catch (e: JsonSyntaxException) {
                log.warn("wild.json malformed; using defaults", e)
                WildStore(file, WildConfig())
            } catch (e: Exception) {
                log.warn("wild.json load failed; using defaults", e)
                WildStore(file, WildConfig())
            }
        }
    }
}
