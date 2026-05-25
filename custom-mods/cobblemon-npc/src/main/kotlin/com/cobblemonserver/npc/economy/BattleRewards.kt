package com.cobblemonserver.npc.economy

import com.cobblemonserver.npc.data.NpcTeamData

object BattleRewards {

    /**
     * Computes the payout for defeating a villager whose team is `data`.
     * Returns 0 when rewards are disabled.
     */
    fun computePayout(data: NpcTeamData): Int {
        if (!RewardsConfig.enabled) return 0
        val base = RewardsConfig.rewardForTier(data.currentTier)
        return if (data.gymLeaderTheme != null) {
            (base * RewardsConfig.gymLeaderMultiplier).toInt().coerceAtLeast(base)
        } else {
            base
        }
    }
}
