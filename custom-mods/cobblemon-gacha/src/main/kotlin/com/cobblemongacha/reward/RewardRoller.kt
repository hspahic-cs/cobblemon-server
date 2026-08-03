package com.cobblemongacha.reward

import com.cobblemongacha.data.LootEntry
import com.cobblemongacha.data.LootTable
import com.cobblemongacha.data.LootTier
import kotlin.random.Random

/**
 * Stateless weighted picker. Filters out 0-weight entries (which the loot table keeps for record
 * purposes), then picks one entry proportionally to `weightPct`. Random is injected so tests can
 * seed it for determinism.
 */
object RewardRoller {

    fun roll(table: LootTable, random: Random = Random.Default): LootEntry {
        val candidates = table.entries.filter { it.weightPct > 0.0 }
        check(candidates.isNotEmpty()) {
            "Loot table for ${table.tier.key} has no positive-weight entries — refusing to roll"
        }
        val total = candidates.sumOf { it.weightPct }
        val r = random.nextDouble() * total
        var acc = 0.0
        for (entry in candidates) {
            acc += entry.weightPct
            if (r < acc) return entry
        }
        return candidates.last()
    }

    /**
     * Guaranteed-floor pity roll (§2.45): draws ONLY from the table's Jackpot-tier entries,
     * re-weighted by [weights] (keyed by entry label — the labels in `pity.json`). Labels in
     * [weights] with no matching table entry are ignored; if NONE match (e.g. the table was
     * renamed without updating pity.json), falls back to the Jackpot entries at their natural
     * table weights. If the table has no rollable Jackpot entries at all, degrades to a normal
     * [roll] — there is nothing to guarantee.
     */
    fun pityRoll(table: LootTable, weights: Map<String, Double>, random: Random = Random.Default): LootEntry {
        val jackpots = table.entries.filter { it.lootTier == LootTier.Jackpot && it.weightPct > 0.0 }
        if (jackpots.isEmpty()) return roll(table, random)
        val configured = jackpots.map { it to (weights[it.label] ?: 0.0) }.filter { it.second > 0.0 }
        val candidates = configured.ifEmpty { jackpots.map { it to it.weightPct } }
        val total = candidates.sumOf { it.second }
        val r = random.nextDouble() * total
        var acc = 0.0
        for ((entry, weight) in candidates) {
            acc += weight
            if (r < acc) return entry
        }
        return candidates.last().first
    }
}
