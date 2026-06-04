package com.cobblemonserver.pokeai

import com.cobblemonserver.pokeai.ai.PokeEngineAIConfig
import net.minecraft.resources.ResourceLocation
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.common.Mod
import org.slf4j.LoggerFactory

@Mod(CobblemonPokeAI.MOD_ID)
class CobblemonPokeAI(@Suppress("UNUSED_PARAMETER") modBus: IEventBus) {

    init {
        logger.info("cobblemon-poke-ai initializing")
        BridgeConfig.load()
        PokeEngineAIConfig.register()
        logger.info("cobblemon-poke-ai initialized — registered AI type 'pe' (bridge=${BridgeConfig.url})")
    }

    companion object {
        const val MOD_ID = "cobblemon_poke_ai"
        val logger = LoggerFactory.getLogger(MOD_ID)

        fun id(path: String): ResourceLocation =
            ResourceLocation.fromNamespaceAndPath(MOD_ID, path)
    }
}
