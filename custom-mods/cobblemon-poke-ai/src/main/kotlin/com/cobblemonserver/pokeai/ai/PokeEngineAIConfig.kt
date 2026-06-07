package com.cobblemonserver.pokeai.ai

import com.gitlab.srcmc.rctapi.api.util.JTO

/**
 * Registers the `"pe"` parser with rctapi's JTO registry so trainer JSONs can
 * opt in via `"ai": {"type": "pe"}`.
 *
 * Optional per-gym tuning via `ai.data`, e.g.:
 * `"ai": {"type": "pe", "data": {"temperature": 1.5}}`
 *
 * - [temperature]: opponent-fallibility for the bridge MCTS. 0 = perfect
 *   opponent (hardest, e.g. Elite 4); higher values make the AI assume the
 *   player misplays and punish greedy lines (free setup, bad switch-ins).
 * - [levelCap]: if > 0, the player's Pokemon are set to this level for the
 *   battle (an over-leveled team is clamped down; an under-leveled team is
 *   raised). 0 disables. Applied by BattleManagerMixin. Use it for the
 *   challenge gyms; leave it off (0) for the Elite 4.
 */
@JvmRecord
data class PokeEngineAIConfig(val temperature: Double = 0.0, val levelCap: Int = 0) {
    companion object {
        @JvmStatic
        fun register() {
            JTO.registerParser<PokeEngineAIConfig, PokeEngineAI>(
                "pe",
                { cfg -> PokeEngineAI(temperature = cfg.temperature, levelCap = cfg.levelCap) },
                { PokeEngineAIConfig() },
                PokeEngineAIConfig::class.java,
            )
        }
    }
}
