package com.cobblemonbridge.quests

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import net.neoforged.fml.loading.FMLPaths
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

private val log = LoggerFactory.getLogger("cobblemon_bridge/gym_caps")

/**
 * Authored source of truth for gym / Elite-Four level caps, loaded from
 * `config/cobblemon-bridge/runtime/gym_caps.json`.
 *
 * Replaces the old `cap = 20 + 5*(gymId-1)` formula: the in-battle downlevel cap for each gym is
 * now looked up by gym id from the config, NOT derived from the number embedded in the trainer
 * filename. `cap: null` (or a gym absent from the file) means no in-battle cap. The overworld
 * (wild-catch) progression — base + perMainlineGym per mainline gym beaten, uncapped after
 * `uncapAfterGym` — is also authored here.
 *
 * Battle-tower caps are NOT here: those ride a flat level_cap tag applied by
 * [com.cobblemonbridge.tower.TowerManager] (challenge = 50, normal = uncapped).
 */
object GymCaps {

    private data class Entry(
        val slug: String? = null,
        val leader: String? = null,
        val mainline: Boolean = false,
        val cap: Int? = null,
    )
    private data class Overworld(
        val base: Int = 20,
        val perMainlineGym: Int = 5,
        val uncapAfterGym: Int = 23,
    )
    private data class CapsFile(
        val gyms: Map<String, Entry> = emptyMap(),
        val overworld: Overworld = Overworld(),
    )

    private val gson: Gson = GsonBuilder().create()

    /** Built-in fallback if the config is missing/malformed — mirrors the shipped JSON so a load
     *  failure never silently strips every cap. */
    private val DEFAULT: CapsFile = CapsFile(
        gyms = buildMap {
            (1..10).forEach { put(it.toString(), Entry(mainline = true, cap = 20 + 5 * (it - 1))) }
            (20..24).forEach { put(it.toString(), Entry(cap = 70)) }
            // gyms 11–19 deliberately absent → uncapped
        },
        overworld = Overworld(),
    )

    @Volatile private var data: CapsFile = DEFAULT

    fun init() = load(
        FMLPaths.CONFIGDIR.get()
            .resolve("cobblemon-bridge")
            .resolve("runtime")
            .resolve("gym_caps.json")
    )

    fun load(file: Path) {
        data = try {
            if (file.exists()) {
                gson.fromJson(file.readText(), CapsFile::class.java) ?: DEFAULT
            } else {
                log.warn("gym_caps.json missing at {} — using built-in cap defaults", file)
                DEFAULT
            }
        } catch (e: Exception) {
            log.warn("gym_caps.json load failed — using built-in cap defaults", e)
            DEFAULT
        }
        log.info(
            "gym caps loaded: {} gyms; overworld base={} per-gym={} uncap-after=gym{}",
            data.gyms.size, data.overworld.base, data.overworld.perMainlineGym, data.overworld.uncapAfterGym,
        )
    }

    /** In-battle downlevel cap when fighting gym [gymId], or null = no cap. */
    fun battleCap(gymId: Int): Int? = data.gyms[gymId.toString()]?.cap

    fun overworldBase(): Int = data.overworld.base
    fun perMainlineGym(): Int = data.overworld.perMainlineGym
    fun uncapAfterGym(): Int = data.overworld.uncapAfterGym

    /** Gym ids flagged `mainline` — the ones that raise the overworld cap as they're beaten. */
    fun mainlineGymIds(): List<Int> =
        data.gyms.filterValues { it.mainline }.keys.mapNotNull { it.toIntOrNull() }.sorted()
}
