package com.cobblemonbridge.quests

import com.cobblemonbridge.CobblemonBridge
import com.cobblemonbridge.economy.EconomyBridge
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.player.AdvancementEvent

/**
 * Pays the gym-bounty / Elite-Four-bounty cash reward AND grants the gym/E4 gacha key
 * when the matching `server:*` advancement awards. Listens to
 * `AdvancementEvent.AdvancementEarnEvent` — NeoForge fires this whenever a player
 * completes an advancement.
 *
 * **Why cash lives here (0.7.29):** the 0.7.26 design put bounty payment into each
 * `beat_gym_*.mcfunction` as `/eco give @s <amount>` so it'd fire on any
 * advancement-award path (RCT trigger OR our Kotlin hook). But NeoEssentials' `/eco`
 * command isn't registered at datapack function-load time, so brigadier rejected the
 * whole function file and the rewards (chat, key, bounty) all silently dropped.
 *
 * **Why keys moved here (0.7.62):** PR #129 added `give @s cobblenav:pokenav_item 1`
 * to `_finalize.mcfunction`. The id was wrong (the real registered item is
 * `cobblenav:pokenav_item_red` — `pokenav_item` isn't a concrete item), so brigadier
 * rejected the line at function-load and unloaded `_finalize` entirely. Every
 * `schedule function server:quests/rewards/_finalize` became a silent no-op, breaking
 * EVERY `cq_reward_*` delivery — gym keys, eggs, master balls, leaf stones. Cash kept
 * working because it was already Kotlin-side. Moving keys here too gives the same
 * resilience: gym keys never depend on `_finalize` parsing again.
 *
 * Moving payment to Kotlin sidesteps the load-order problem entirely:
 * `EconomyBridge.deposit` uses reflection against NeoEssentials' EconomyManager and
 * key grants use `/gacha grant <player> <tier> 1` via `performPrefixedCommand` against
 * the always-registered gacha command. By the time an AdvancementEarnEvent fires,
 * everything is up.
 *
 * Advancement-id pattern matching:
 *   - `server:beat_gym_N` → `$150 × N` cash + key (gyms 1..23)
 *   - `server:beat_gym_24` (Champion) → flat `$5,000` + ultra key (was via the now-deleted
 *     `server:defeat_elite_four` advancement; rolled into the Champion fight in 0.7.64)
 *   - `server:beat_gym_N_challenge` → same `$150 × N` cash + key (Hard Mode pays the
 *     same as base — 0.7.8 design call)
 *
 * Gym → key tier mapping (mirrors `beat_gym_N.mcfunction` → `cq_reward_key_*` tags):
 *   - Gym 1-9, 11-18, 20-22: `rare` key
 *   - Gym 10, 19, 23, 24:    `ultra` key
 *
 * Anything else is ignored. Non-key item rewards (master balls, leaf stones, eggs)
 * still route through `_finalize.mcfunction` — they'll resume working once
 * `_finalize` parses cleanly again after the cobblenav line is stripped.
 */
object AdvancementHook {

    private val GYM_ID_REGEX = Regex("""^server:beat_gym_(\d+)(_challenge)?$""")
    private const val CHAMPION_BOUNTY = 5_000
    /** Normal gyms past gym 10 are capped here (not scaled by gym number). 1500 = 150 × 10. */
    private const val NORMAL_CAP = 1_500
    /** Every challenge gym (1-18) pays a flat NORMAL_CAP × 1.5, regardless of gym number. */
    private const val CHALLENGE_BOUNTY = 2_250

    /** Normal gyms here grant an ultra key; everything else (incl. ALL challenge gyms) is rare. */
    private val ULTRA_KEY_GYMS: Set<Int> = setOf(10, 19, 23, 24)

    @SubscribeEvent
    fun onAdvancementEarn(event: AdvancementEvent.AdvancementEarnEvent) {
        val player = event.entity as? ServerPlayer ?: return
        val id = event.advancement.id().toString()

        val gymMatch = GYM_ID_REGEX.matchEntire(id)
        if (gymMatch != null) {
            val gymId = gymMatch.groupValues[1].toIntOrNull() ?: return
            val isChallenge = gymMatch.groupValues[2] == "_challenge"
            // Cash (one-time, first beat):
            //   - Champion (24): flat $5,000.
            //   - Challenge gym (1-18): flat $2,250 (NORMAL_CAP × 1.5).
            //   - Normal gym 11-18: capped $1,500 (not scaled).
            //   - Normal gym 1-10 (and E4 19-23): $150 × gym number.
            val cash = when {
                gymId == 24 -> CHAMPION_BOUNTY
                isChallenge -> CHALLENGE_BOUNTY
                gymId in 11..18 -> NORMAL_CAP
                else -> 150 * gymId
            }
            val reason = if (gymId == 24) {
                "defeating the Champion"
            } else {
                "defeating gym $gymId" + if (isChallenge) " (Hard Mode)" else ""
            }
            payBounty(player, cash, reason)
            grantGymKey(player, gymId, isChallenge)
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

    /** Run `/gacha grant <player> <tier> 1` with elevated source so the gacha mod's
     *  op-2 perm gate is satisfied and output is suppressed (the player already got a
     *  tellraw from `beat_gym_N.mcfunction` — no need for a second chat line). */
    private fun grantGymKey(player: ServerPlayer, gymId: Int, isChallenge: Boolean) {
        // Challenge gyms always grant a rare key (incl. Grimm/10 — no ultra on the challenge track).
        val tier = if (!isChallenge && gymId in ULTRA_KEY_GYMS) "ultra" else "rare"
        val src = player.createCommandSourceStack().withPermission(4).withSuppressedOutput()
        val cmd = "gacha grant ${player.gameProfile.name} $tier 1"
        player.server.commands.performPrefixedCommand(src, cmd)
        CobblemonBridge.logger.info(
            "advancement-key: granted {} key to {} for gym {}",
            tier, player.gameProfile.name, gymId,
        )
    }
}
