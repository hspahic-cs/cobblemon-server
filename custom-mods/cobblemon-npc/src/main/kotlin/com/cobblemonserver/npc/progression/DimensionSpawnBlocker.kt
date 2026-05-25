package com.cobblemonserver.npc.progression

import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemonserver.npc.CobblemonNpc
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.minecraft.resources.ResourceLocation
import net.neoforged.fml.loading.FMLPaths
import java.nio.file.Files
import java.nio.file.Path

object DimensionSpawnBlocker {

    private val blockedDimensions: MutableSet<ResourceLocation> = mutableSetOf()

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    fun load() {
        blockedDimensions.clear()
        val path = configPath()
        if (!Files.exists(path)) {
            writeDefaults(path)
        }
        try {
            val text = Files.readString(path)
            val json = gson.fromJson(text, JsonObject::class.java)
            val arr = json.getAsJsonArray("blockedDimensions") ?: JsonArray()
            arr.forEach { blockedDimensions.add(ResourceLocation.parse(it.asString)) }
            CobblemonNpc.logger.info(
                "cobblemon-npc: spawn blocker active for ${blockedDimensions.size} dimension(s): $blockedDimensions"
            )
        } catch (e: Exception) {
            CobblemonNpc.logger.warn("cobblemon-npc: failed to load spawn-blocker config, no dimensions blocked: ${e.message}")
        }
    }

    fun register() {
        CobblemonEvents.ENTITY_SPAWN.subscribe { event ->
            val dim = event.entity.level().dimension().location()
            if (dim in blockedDimensions) {
                event.cancel()
            }
        }
    }

    private fun writeDefaults(path: Path) {
        try {
            Files.createDirectories(path.parent)
            val json = JsonObject().apply {
                add("blockedDimensions", JsonArray().apply {
                    add("multiworld:arena1")
                    add("multiworld:arena2")
                    add("multiworld:elite4")
                })
            }
            Files.writeString(path, gson.toJson(json))
            CobblemonNpc.logger.info("cobblemon-npc: wrote default spawn-blocker config to $path")
        } catch (e: Exception) {
            CobblemonNpc.logger.warn("cobblemon-npc: failed to write default spawn-blocker config: ${e.message}")
        }
    }

    private fun configPath(): Path =
        FMLPaths.CONFIGDIR.get().resolve("cobblemon-npc").resolve("spawn-blocker.json")
}
