package com.cobblemonroguelite.data.shop

import com.cobblemonroguelite.data.reward.RunReward
import com.cobblemonroguelite.data.reward.WeightCurve
import net.minecraft.resources.ResourceLocation
import kotlin.random.Random

/**
 * One thing the between-wave shop can sell: a [RunReward], a price in credits, and a depth band.
 *
 * ### Why this is not just a [com.cobblemonroguelite.data.reward.RewardEntry] with a price
 *
 * They look like the same record and they answer different questions. A reward entry is something the
 * run *gives* you, drawn by a tier curve that makes rarities ramp with depth. A shop entry is
 * something you *choose to buy*, and the thing that gates it is what you can afford. Bolting a price
 * onto the reward tables would mean every rebalance of the free-reward rarity curve silently
 * re-prices the shop, and every price change perturbs what the free rolls hand out.
 *
 * They deliberately share [RunReward], because what a reward *is* — an EV bump, a mint, a held item —
 * is the same question in both places, and §2.12's sealed list is the boundary that keeps "rewards
 * are data" honest. Only the selection machinery differs.
 *
 * ### Price is authored, not derived
 *
 * There is no formula from reward magnitude to cost. A formula would be wrong in both directions at
 * once: 10 Attack EVs and 10 HP EVs cost the same to compute and are worth wildly different amounts,
 * and the same item is a bargain at wave 20 and a rounding error at wave 180. Pricing is a balance
 * decision per entry, and the shape of this record says so.
 */
data class ShopEntry(
    /**
     * Stable identifier, unique within a table. Authored rather than generated for the same reason
     * [com.cobblemonroguelite.data.reward.RewardEntry] authors its own: it is what an offer
     * de-duplicates on, and it is what a purchase names.
     */
    val id: String,
    /** What the buyer gets. */
    val reward: RunReward,
    /** Credits charged. Non-negative; a zero-price entry is a giveaway, which is legal if odd. */
    val price: Int,
    /** How likely this entry is to appear in an offer, relative to others eligible at that wave. */
    val weight: Double = 1.0,
    /** First wave this entry can be offered at all. */
    val minWave: Int = 1,
    /** Last wave this entry can be offered, or null for "forever". */
    val maxWave: Int? = null,
    /**
     * Optional per-wave price curve. When set, the price at a wave is [price] scaled by the curve's
     * weight at that wave, in hundredths — so a curve of `[{wave:1, weight:100},{wave:200, weight:400}]`
     * makes the entry four times as expensive by the end of a run.
     *
     * Exists because a static price cannot survive a 200-wave run: credits-per-wave grows with depth
     * (see [com.cobblemonroguelite.shop.CreditRules]), so a fixed price becomes free. Reusing
     * [WeightCurve] rather than inventing a second interpolation format means one implementation of
     * "read a value off a piecewise line", already tested.
     */
    val priceCurve: WeightCurve? = null,
) {

    /** Whether this entry exists at [wave] at all — the hard content band, not a likelihood. */
    fun appearsAt(wave: Int): Boolean = wave >= minWave && (maxWave == null || wave <= maxWave)

    /**
     * What this entry costs at [wave].
     *
     * Clamped at zero and at [MAX_PRICE]: a curve authored with a negative or absurd weight is an
     * operator error, and the safe failure is an item that is free or unaffordable rather than one
     * whose price wraps into a negative and pays the player to take it.
     */
    fun priceAt(wave: Int): Int {
        val curve = priceCurve ?: return price.coerceIn(0, MAX_PRICE)
        val scaled = price.toLong() * curve.weightAt(wave).toLong() / HUNDREDTHS
        return scaled.coerceIn(0L, MAX_PRICE.toLong()).toInt()
    }

    private companion object {
        const val HUNDREDTHS = 100L
        const val MAX_PRICE = 1_000_000
    }
}

/**
 * One loaded shop table: everything the between-wave shop can stock.
 *
 * ### The offer draw, and why it is seeded from outside
 *
 * [rollOffer] takes a [Random] rather than making one. §2.16 requires a resumed run to see the same
 * shop it saw before it was paused — a player who logs out staring at a Master Ball must not log in
 * to a different offer, and a shop that re-rolled on resume would be a free reroll for anyone willing
 * to relog. So the caller derives the stream from (run seed, wave, rerolls) and this stays pure.
 *
 * That is also why the reroll *count* is part of the caller's stream input and not state here: the
 * offer has to be a function of things that are persisted, and the count is
 * ([com.cobblemonroguelite.run.RunState]), whereas a `Random` mid-sequence is not.
 */
data class ShopTable(
    val id: ResourceLocation,
    val entries: List<ShopEntry>,
) {

    /**
     * Draw up to [slots] distinct entries eligible at [wave].
     *
     * Distinct by [ShopEntry.id], because two of the same item in a four-slot offer is a wasted slot
     * and reads as a bug. Returns fewer than [slots] when the table has fewer eligible entries, which
     * is a content thin spot rather than an error — the caller shows a smaller shop.
     */
    fun rollOffer(wave: Int, slots: Int, random: Random): List<ShopEntry> {
        if (slots <= 0) return emptyList()
        val eligible = entries.filter { it.appearsAt(wave) }
        val offer = mutableListOf<ShopEntry>()
        val taken = mutableSetOf<String>()
        while (offer.size < slots) {
            val candidates = eligible.filter { it.id !in taken }
            val picked = pick(candidates, random) ?: break
            offer += picked
            taken += picked.id
        }
        return offer
    }

    /** Find an entry by the id a purchase named, or null if the offer is stale. */
    fun entry(id: String): ShopEntry? = entries.firstOrNull { it.id == id }

    /**
     * Weighted pick, with the same guard as the reward tables: a candidate list whose weights sum to
     * zero or less has no meaningful winner, so it yields nothing rather than the first element. An
     * author who weights everything at zero has said "offer nothing", and silently offering the first
     * entry would hide that.
     */
    private fun pick(candidates: List<ShopEntry>, random: Random): ShopEntry? {
        if (candidates.isEmpty()) return null
        val total = candidates.sumOf { it.weight.coerceAtLeast(0.0) }
        if (total <= 0.0) return null
        var roll = random.nextDouble() * total
        for (candidate in candidates) {
            roll -= candidate.weight.coerceAtLeast(0.0)
            if (roll <= 0.0) return candidate
        }
        return candidates.last()
    }
}
