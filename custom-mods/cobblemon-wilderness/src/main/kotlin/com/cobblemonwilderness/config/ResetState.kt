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
 * Mod-managed runtime state. Do not hand-edit.
 *
 * `lastResetEpochMillis` is keyed by dimension id; `forceNextBoot` is armed by
 * `/wildreset now` to make the next server start perform a reset regardless of the
 * interval (destructive deletes only ever happen at boot, never on a live world).
 */
data class ResetState(
    val lastResetEpochMillis: MutableMap<String, Long> = mutableMapOf(),
    var forceNextBoot: Boolean = false,
    /**
     * Per-cycle salt mixed into structure placement for chunks outside the keep-box, so monuments
     * and structures relocate each reset. Bumped on every real prune; 0 = never pruned yet (the
     * structure mixin treats 0 as inactive). Read on the worldgen hot path via [WildernessGenState].
     */
    var structureSalt: Int = 0,
) {
    @Transient
    private var configDir: Path? = null

    fun save() {
        val dir = configDir ?: return
        val file = ConfigPaths.runtime(dir, "state.json")
        file.parent.createDirectories()
        file.writeText(gson.toJson(this))
    }

    companion object {
        private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

        fun load(configDir: Path): ResetState {
            val file = ConfigPaths.runtime(configDir, "state.json")
            val state = if (!file.exists()) {
                ResetState()
            } else {
                try {
                    gson.fromJson(file.readText(), ResetState::class.java) ?: ResetState()
                } catch (e: Exception) {
                    CobblemonWilderness.logger.error("Failed to load reset state, starting fresh", e)
                    ResetState()
                }
            }
            state.configDir = configDir
            return state
        }
    }
}
