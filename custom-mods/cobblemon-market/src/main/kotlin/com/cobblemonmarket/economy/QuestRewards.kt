package com.cobblemonmarket.economy

import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer

/**
 * Income-threshold quest awards. For every threshold the player's current balance meets or
 * exceeds, award the corresponding `server:reach_income_<N>` advancement. The award itself is
 * idempotent (skipped if `progress.isDone`), so this is safe to call repeatedly — on every
 * sell deposit, on login, and anywhere else the balance might land at or above a milestone.
 *
 * Earlier this checked only "did this specific sell deposit *cross* the threshold" via a
 * before/after comparison. That missed the case where the player already had ≥ threshold
 * before the quest's prerequisite cleared (e.g. earned ¢260 from trainer bounties before
 * beating gym 1, then sold items afterwards — the sells stayed strictly above 250 and never
 * triggered a "crossing"). Switched to "current balance meets threshold" so the award fires
 * regardless of how/when the balance got there.
 *
 * Threshold list is centralized here so admins editing income tiers only have one place to
 * touch. Keep it in sync with the datapack's `data/server/advancement/reach_income_*.json`.
 */
object QuestRewards {

    // Aligned with the datapack's actual advancement files
    // (reach_income_250 / _1000 / _10000 / _100000). Any threshold here without a matching
    // advancement file silently no-ops in awardQuest, and any advancement without a threshold
    // here is unreachable — keep both lists in sync.
    private val INCOME_THRESHOLDS = listOf(250, 1000, 10000, 100000)

    /**
     * Re-evaluate every income-threshold advancement against the player's current balance.
     * Awards any threshold the balance has reached that isn't already marked complete.
     */
    fun checkIncomeThresholds(player: ServerPlayer) {
        val balance = EconomyBridge.getBalance(player.uuid)
        for (threshold in INCOME_THRESHOLDS) {
            if (balance >= threshold) {
                awardQuest(player, "server:reach_income_$threshold")
            }
        }
    }

    private fun awardQuest(player: ServerPlayer, advancementId: String, criterion: String = "done") {
        val rl = ResourceLocation.parse(advancementId)
        val holder = player.server.advancements.get(rl) ?: return
        val progress = player.advancements.getOrStartProgress(holder)
        if (progress.isDone) return
        player.advancements.award(holder, criterion)
    }
}
