package com.cobblemonserver.npc.data

import com.cobblemonserver.npc.CobblemonNpc
import com.google.gson.Gson
import com.google.gson.JsonObject
import net.minecraft.server.MinecraftServer
import net.neoforged.fml.loading.FMLPaths

/**
 * Loads npc-profession-pools.json and builds a map of professionId → List<species>.
 *
 * Resolution order:
 *   1. config/cobblemon-npc/profession-pools.json  (server operator override)
 *   2. /data/cobblemon-npc/profession-pools.json   (bundled in the mod jar)
 */
object ProfessionPoolLoader {

    private val gson = Gson()

    /** professionId → list of species names (lowercase, no hyphens) */
    private val pools: MutableMap<String, List<String>> = mutableMapOf()

    fun load(server: MinecraftServer) {
        val configFile = FMLPaths.CONFIGDIR.get()
            .resolve("cobblemon-npc/profession-pools.json")
            .toFile()

        val json = when {
            configFile.exists() -> {
                CobblemonNpc.logger.info("cobblemon-npc: loading profession pools from config override")
                configFile.readText()
            }
            else -> {
                val stream = ProfessionPoolLoader::class.java
                    .getResourceAsStream("/data/cobblemon-npc/profession-pools.json")
                if (stream == null) {
                    CobblemonNpc.logger.error(
                        "cobblemon-npc: profession-pools.json not found in mod resources"
                    )
                    return
                }
                CobblemonNpc.logger.info("cobblemon-npc: loading bundled profession pools")
                stream.reader().readText()
            }
        }

        parse(json)
        CobblemonNpc.logger.info("cobblemon-npc: loaded ${pools.size} profession pools (deploy-flow test marker)")
    }

    private fun parse(json: String) {
        pools.clear()
        val root = gson.fromJson(json, JsonObject::class.java)
        val professions = root.getAsJsonArray("professions") ?: return
        for (entry in professions) {
            val obj = entry.asJsonObject
            val id = obj.get("id")?.asString ?: continue
            val poolArray = obj.getAsJsonArray("pokemonPool") ?: continue
            pools[id] = poolArray.map { it.asString }
        }
    }

    /**
     * Returns the Pokemon pool for a given profession ID.
     * Falls back to the "unemployed" pool if the profession is unknown or has an empty pool.
     */
    fun getPool(professionId: String): List<String> {
        val pool = pools[professionId]
        if (!pool.isNullOrEmpty()) return pool
        return pools["unemployed"] ?: emptyList()
    }

    /**
     * Maps a profession/job registry key to our pool ID.
     * e.g. "minecolonies:builder" → "builder", "minecraft:weaponsmith" → "weaponsmith"
     */
    fun professionKeyToPoolId(registryKey: String): String {
        val id = registryKey.substringAfter(':')
        return when (id) {
            "none",
            "unemployed_villager",
            "placeholder",
            "student",
            "pupil",
            "teacher",
            "archertraining",
            "combattraining" -> "unemployed"
            else -> id
        }
    }

    fun isLoaded(): Boolean = pools.isNotEmpty()
}
