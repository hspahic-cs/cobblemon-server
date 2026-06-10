package com.cobblemonbridge.voice

import com.cobblemonbridge.CobblemonBridge
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import net.neoforged.fml.loading.FMLPaths
import kotlin.io.path.exists
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.readText

/**
 * One trainer's voice lines, keyed by trigger. Any list may be empty (that trigger stays silent
 * for that trainer). `name`/`color` control the chat prefix — `color` is a vanilla
 * [net.minecraft.ChatFormatting] name (e.g. `light_purple`, `gold`, `aqua`).
 */
data class TrainerVoice(
    val name: String? = null,
    val color: String? = null,
    val intro: List<String> = emptyList(),
    val taunt: List<String> = emptyList(),
    val victory: List<String> = emptyList(),
    val defeat: List<String> = emptyList(),
)

/**
 * Loads per-trainer voice lines from `config/cobblemon-bridge/runtime/voicelines.json`
 * (map of `<trainer id>` → [TrainerVoice]). Hot-reloaded: the file's mtime is checked on each
 * lookup, so editing the JSON takes effect on the next battle with no restart. Missing/!malformed
 * file → no lines (every trainer silent). Read by [TrainerVoiceHook].
 */
object VoiceLines {

    private val file = FMLPaths.CONFIGDIR.get()
        .resolve("cobblemon-bridge").resolve("runtime").resolve("voicelines.json")
    private val gson = Gson()
    private val mapType = object : TypeToken<Map<String, TrainerVoice>>() {}.type

    @Volatile private var cache: Map<String, TrainerVoice> = emptyMap()
    @Volatile private var stamp: Long = Long.MIN_VALUE

    fun get(trainerId: String): TrainerVoice? {
        reloadIfChanged()
        return cache[trainerId]
    }

    private fun reloadIfChanged() {
        try {
            if (!file.exists()) {
                if (cache.isNotEmpty()) cache = emptyMap()
                stamp = Long.MIN_VALUE
                return
            }
            val mtime = file.getLastModifiedTime().toMillis()
            if (mtime == stamp) return
            cache = gson.fromJson<Map<String, TrainerVoice>>(file.readText(), mapType) ?: emptyMap()
            stamp = mtime
            CobblemonBridge.logger.info("voicelines: loaded {} trainer entries", cache.size)
        } catch (e: Exception) {
            CobblemonBridge.logger.warn("voicelines: load failed ({}); keeping previous", e.message)
        }
    }
}
