package com.cobblemonserver.pokeai.ai

import com.gitlab.srcmc.rctapi.api.util.JTO

/**
 * Registers the `"pe"` parser with rctapi's JTO registry so trainer JSONs can
 * opt in via `"ai": {"type": "pe"}`.
 */
@JvmRecord
data class PokeEngineAIConfig(val unused: Boolean = false) {
    companion object {
        @JvmStatic
        fun register() {
            JTO.registerParser<PokeEngineAIConfig, PokeEngineAI>(
                "pe",
                { PokeEngineAI() },
                { PokeEngineAIConfig() },
                PokeEngineAIConfig::class.java,
            )
        }
    }
}
