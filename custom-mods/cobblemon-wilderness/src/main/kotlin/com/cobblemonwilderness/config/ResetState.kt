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
 *
 * `resetGeneration` is the per-region reset-generation counter for the overworld (T3): each
 * region that is pruned has its generation bumped, which relocates that region's structures on
 * regen. It is persisted as an array of `[regionKey, generation]` pairs — Gson stringifies long
 * map keys, so a raw `Map<Long, Int>` would round-trip ambiguously. The authoritative in-memory
 * view is a `Map<Long, Int>` hydrated lazily from that list (see [generationOf]/[bumpGeneration]).
 */
data class ResetState(
    val lastResetEpochMillis: MutableMap<String, Long> = mutableMapOf(),
    var forceNextBoot: Boolean = false,
    /**
     * Persisted form of the overworld reset-generation map: an array of `[regionKey, generation]`
     * pairs. Do NOT read this for logic — use the generation* helpers, which operate on the hydrated
     * [genMap]. It is rebuilt from that map on [save].
     */
    var resetGeneration: MutableList<LongArray> = mutableListOf(),
) {
    @Transient
    private var configDir: Path? = null

    /** In-memory O(1) region-key → generation, hydrated from [resetGeneration] on first access. */
    @Transient
    private var genMap: MutableMap<Long, Int>? = null

    private fun map(): MutableMap<Long, Int> = genMap ?: hydrate().also { genMap = it }

    private fun hydrate(): MutableMap<Long, Int> {
        val m = HashMap<Long, Int>()
        // Gson (Unsafe) can leave the list null on a legacy/hand-edited file — tolerate it.
        val list: List<LongArray>? = resetGeneration
        if (list != null) {
            for (pair in list) if (pair.size >= 2) m[pair[0]] = pair[1].toInt()
        }
        return m
    }

    /** Reset generation for a region key (0 = never reset). */
    fun generationOf(key: Long): Int = map()[key] ?: 0

    /** Bump a region's reset generation and return the new value. Overworld regions only. */
    fun bumpGeneration(key: Long): Int {
        val m = map()
        val next = (m[key] ?: 0) + 1
        m[key] = next
        return next
    }

    /** True if any region has ever been reset (drives the worldgen hook's inert fast path). */
    fun hasAnyGeneration(): Boolean = map().isNotEmpty()

    /** Immutable copy of the region-generation map for publishing to the worldgen snapshot. */
    fun generationSnapshot(): Map<Long, Int> = HashMap(map())

    fun save() {
        val dir = configDir ?: return
        // Rebuild the persisted pair-list from the authoritative map before serializing.
        resetGeneration = map().entries
            .map { longArrayOf(it.key, it.value.toLong()) }
            .toMutableList()
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
                    // Legacy state carrying `structureSalt` and no `resetGeneration` deserializes
                    // cleanly: the unknown field is ignored and generations hydrate to empty (start at 0).
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
