package com.cobblemonserver.npc.gym

import com.cobblemonserver.npc.CobblemonNpc
import com.google.gson.Gson
import com.google.gson.JsonObject
import net.minecraft.server.MinecraftServer
import net.neoforged.fml.loading.FMLPaths

/**
 * Loads gym-leader-pool.json and exposes themed team data.
 *
 * Resolution order mirrors ProfessionPoolLoader:
 *   1. config/cobblemon-npc/gym-leader-pool.json  (server operator override)
 *   2. /data/cobblemon-npc/gym-leader-pool.json   (bundled in the mod jar)
 */
object GymLeaderPoolLoader {

    private val gson = Gson()
    private val themes: MutableMap<String, GymLeaderTheme> = linkedMapOf()

    private const val POOL_RESET_THRESHOLD = 3

    data class MaxTeamSlot(val slot: Int, val species: String, val item: String?)

    data class GymLeaderTheme(
        val id: String,
        val name: String,
        val description: String,
        val startingThree: List<String>,
        val maxTeam: List<MaxTeamSlot>,
        val megaSlot: Int?,
        val legendarySpecies: String?,
        val legendaryReplacesSlot: Int?
    )

    fun load(server: MinecraftServer) {
        val configFile = FMLPaths.CONFIGDIR.get()
            .resolve("cobblemon-npc/gym-leader-pool.json")
            .toFile()

        val json = when {
            configFile.exists() -> {
                CobblemonNpc.logger.info("cobblemon-npc: loading gym leader pool from config override")
                configFile.readText()
            }
            else -> {
                val stream = GymLeaderPoolLoader::class.java
                    .getResourceAsStream("/data/cobblemon-npc/gym-leader-pool.json")
                if (stream == null) {
                    CobblemonNpc.logger.error(
                        "cobblemon-npc: gym-leader-pool.json not found in mod resources"
                    )
                    return
                }
                CobblemonNpc.logger.info("cobblemon-npc: loading bundled gym leader pool")
                stream.reader().readText()
            }
        }

        parse(json)
        CobblemonNpc.logger.info("cobblemon-npc: loaded ${themes.size} gym leader themes")
    }

    private fun parse(json: String) {
        themes.clear()
        val root = gson.fromJson(json, JsonObject::class.java)
        val array = root.getAsJsonArray("gymLeaders") ?: return
        for (entry in array) {
            val obj = entry.asJsonObject
            val id = obj.get("id")?.asString ?: continue
            val name = obj.get("name")?.asString ?: id
            val description = obj.get("description")?.asString ?: ""
            val startingThree = obj.getAsJsonArray("startingThree")
                ?.map { it.asString } ?: emptyList()

            val maxTeam = obj.getAsJsonArray("maxTeam")?.map { slotEl ->
                val s = slotEl.asJsonObject
                MaxTeamSlot(
                    slot = s.get("slot").asInt,
                    species = s.get("species").asString,
                    item = s.get("item")?.asString
                )
            } ?: emptyList()

            val megaSlot = obj.getAsJsonArray("maxTeam")?.map { it.asJsonObject }
                ?.firstOrNull { it.has("megaNote") }
                ?.get("slot")?.asInt

            val legendary = obj.getAsJsonObject("legendary")
            val legendarySpecies = legendary?.get("species")?.asString
            val legendaryReplacesSlot = legendary?.get("replacesSlot")?.asInt

            themes[id] = GymLeaderTheme(
                id, name, description, startingThree, maxTeam,
                megaSlot, legendarySpecies, legendaryReplacesSlot
            )
        }
    }

    fun getTheme(id: String): GymLeaderTheme? = themes[id]

    fun allThemeIds(): Set<String> = themes.keys.toSet()

    /**
     * Returns themes currently available for picking, honoring the pool-reset rule:
     * when the available set would drop to [POOL_RESET_THRESHOLD] or fewer, the whole
     * pool is considered reset (all themes available again).
     */
    fun availableThemes(used: Set<String>): List<GymLeaderTheme> {
        val unused = themes.values.filter { it.id !in used }
        if (unused.size <= POOL_RESET_THRESHOLD) {
            return themes.values.toList()
        }
        return unused
    }

    fun isLoaded(): Boolean = themes.isNotEmpty()
}
