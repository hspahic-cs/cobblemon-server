package com.cobblemonbridge.quests

import com.cobblemonbridge.CobblemonBridge
import com.cobblemonbridge.economy.EconomyBridge
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.player.AdvancementEvent

/**
 * Pays the gym-bounty / Elite-Four-bounty cash reward when the matching `server:*`
 * advancement awards. Listens to `AdvancementEvent.AdvancementEarnEvent` — NeoForge
 * fires this whenever a player completes an advancement.
 *
 * **Why this hook exists (0.7.29):** the 0.7.26 design put bounty payment into each
 * `beat_gym_*.mcfunction` as `/eco give @s <amount>` so it'd fire on any
 * advancement-award path (RCT trigger OR our Kotlin hook). But NeoEssentials' `/eco`
 * command isn't registered at datapack function-load time, so brigadier rejected the
 * whole function file and the rewards (chat, key, bounty) all silently dropped.
 *
 * Moving payment back to Kotlin sidesteps the load-order problem entirely:
 * `EconomyBridge.deposit` uses reflection against NeoEssentials' EconomyManager
 * directly, no command parsing. By the time an AdvancementEarnEvent fires, the bridge
 * has long been resolved.
 *
 * Advancement-id pattern matching:
 *   - `server:beat_gym_N` → `$150 × N` (gyms 1..24)
 *   - `server:beat_gym_N_challenge` → same `$150 × N` (Hard Mode pays the same as base —
 *     0.7.8 design call)
 *   - `server:defeat_elite_four` → flat `$5,000`
 *
 * Anything else is ignored. Reward functions still handle the chat tellraw + non-cash
 * items (keys, master balls) via the existing `cq_reward_*` tag pipeline through
 * `_finalize.mcfunction`.
 */
object AdvancementHook {

    private val GYM_ID_REGEX = Regex("""^server:beat_gym_(\d+)(_challenge)?$""")
    private const val ELITE_FOUR_BOUNTY = 5_000

    @SubscribeEvent
    fun onAdvancementEarn(event: AdvancementEvent.AdvancementEarnEvent) {
        val player = event.entity as? ServerPlayer ?: return
        val id = event.advancement.id().toString()

        val gymMatch = GYM_ID_REGEX.matchEntire(id)
        if (gymMatch != null) {
            val gymId = gymMatch.groupValues[1].toIntOrNull() ?: return
            val isChallenge = gymMatch.groupValues[2] == "_challenge"
            payBounty(player, 150 * gymId, "defeating gym $gymId" + if (isChallenge) " (Hard Mode)" else "")
            return
        }

        if (id == "server:defeat_elite_four") {
            payBounty(player, ELITE_FOUR_BOUNTY, "defeating the Elite Four")
            return
        }
    }

    private fun payBounty(player: ServerPlayer, amount: Int, reason: String) {
        if (amount <= 0) return
        EconomyBridge.deposit(player.uuid, amount)
        player.sendSystemMessage(Component.literal(
            "§6§l+ §e§l\$$amount §6§lfor $reason"
        ))
        CobblemonBridge.logger.info(
            "advancement-bounty: paid \${} to {} for {}",
            amount, player.gameProfile.name, reason,
        )
    }
}
