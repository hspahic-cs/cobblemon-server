package com.cobblemonserver.npc.economy

import com.cobblemonserver.npc.CobblemonNpc
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import net.neoforged.fml.loading.FMLPaths
import java.nio.file.Files
import java.nio.file.Path

object RewardsConfig {

    var enabled: Boolean = true
    var gymLeaderMultiplier: Double = 2.5
    var tier1Reward: Int = 25
    var tier2Reward: Int = 60
    var tier3Reward: Int = 120
    var tier4Reward: Int = 220
    var tier5Reward: Int = 360
    var tier6Reward: Int = 550

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    fun rewardForTier(tier: Int): Int = when (tier.coerceIn(1, 6)) {
        1 -> tier1Reward
        2 -> tier2Reward
        3 -> tier3Reward
        4 -> tier4Reward
        5 -> tier5Reward
        else -> tier6Reward
    }

    fun load() {
        val path = configPath()
        if (!Files.exists(path)) {
            save()
            CobblemonNpc.logger.info("cobblemon-npc: wrote default rewards config to $path")
            return
        }
        try {
            val text = Files.readString(path)
            val json = gson.fromJson(text, JsonObject::class.java)
            enabled = json.get("enabled")?.asBoolean ?: enabled
            gymLeaderMultiplier = json.get("gymLeaderMultiplier")?.asDouble ?: gymLeaderMultiplier
            tier1Reward = json.get("tier1Reward")?.asInt ?: tier1Reward
            tier2Reward = json.get("tier2Reward")?.asInt ?: tier2Reward
            tier3Reward = json.get("tier3Reward")?.asInt ?: tier3Reward
            tier4Reward = json.get("tier4Reward")?.asInt ?: tier4Reward
            tier5Reward = json.get("tier5Reward")?.asInt ?: tier5Reward
            tier6Reward = json.get("tier6Reward")?.asInt ?: tier6Reward
            CobblemonNpc.logger.info(
                "cobblemon-npc: rewards config loaded (enabled=$enabled gymMult=$gymLeaderMultiplier " +
                    "tiers=[$tier1Reward,$tier2Reward,$tier3Reward,$tier4Reward,$tier5Reward,$tier6Reward])"
            )
        } catch (e: Exception) {
            CobblemonNpc.logger.warn("cobblemon-npc: failed to load rewards.json, using defaults: ${e.message}")
        }
    }

    fun save() {
        val path = configPath()
        try {
            Files.createDirectories(path.parent)
            val json = JsonObject().apply {
                addProperty("enabled", enabled)
                addProperty("gymLeaderMultiplier", gymLeaderMultiplier)
                addProperty("tier1Reward", tier1Reward)
                addProperty("tier2Reward", tier2Reward)
                addProperty("tier3Reward", tier3Reward)
                addProperty("tier4Reward", tier4Reward)
                addProperty("tier5Reward", tier5Reward)
                addProperty("tier6Reward", tier6Reward)
            }
            Files.writeString(path, gson.toJson(json))
        } catch (e: Exception) {
            CobblemonNpc.logger.warn("cobblemon-npc: failed to save rewards.json: ${e.message}")
        }
    }

    private fun configPath(): Path =
        FMLPaths.CONFIGDIR.get().resolve("cobblemon-npc").resolve("rewards.json")
}
