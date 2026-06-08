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
 *
 * Player level capping is handled separately by cobblemon-bridge's
 * GymBattleAdjustHook via a `cobblemon_bridge.level_cap.<N>` entity tag (flat,
 * crash-safe down-level — no clone, so battle damage persists).
 */
@JvmRecord
data class PokeEngineAIConfig(val temperature: Double = 0.0) {
    companion object {
        @JvmStatic
        fun register() {
            JTO.registerParser<PokeEngineAIConfig, PokeEngineAI>(
                "pe",
                { cfg -> PokeEngineAI(temperature = cfg.temperature) },
                { PokeEngineAIConfig() },
                PokeEngineAIConfig::class.java,
            )
        }
    }
}
