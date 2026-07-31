package com.cobblemonroguelite.data.shop

import com.cobblemonroguelite.data.reward.RunReward
import com.cobblemonroguelite.shop.ShopSettings
import com.cobblemonroguelite.data.reward.WeightCurve
import net.minecraft.resources.ResourceLocation

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
     * [com.cobblemonroguelite.data.reward.RewardEntry] authors its own: it is what a purchase names,
     * so an auto-generated index would change which item a command buys when the file is reordered.
     */
    val id: String,
    /** What the buyer gets. */
    val reward: RunReward,
    /** Credits charged. Non-negative; a zero-price entry is a giveaway, which is legal if odd. */
    val price: Int,
    /**
     * Price as a multiple of the wave's own value, instead of a flat number.
     *
     * This is how PokéRogue prices its entire shop — a Potion is 0.2× what the wave is worth, a Revive
     * 2×, a Sacred Ash 10× — and it is the field that keeps a shop in reach at wave 150 without anybody
     * authoring a second table for late waves. A flat [price] cannot do that: it is either trivial late
     * or unaffordable early, and a per-wave [priceCurve] only papers over it by making the author
     * describe the same growth twice.
     *
     * When set it wins outright, and [price] and [priceCurve] are ignored. See
     * `docs/roguelite-economy-reference.md` for their multiplier table.
     */
    val costMultiplier: Double? = null,
    /** First wave this entry is stocked at all. */
    val minWave: Int = 1,
    /** Last wave this entry is stocked, or null for "forever". */
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
        // The multiplier is checked first and returns outright, so the two pricing models can never
        // compose into a number neither of them describes.
        costMultiplier?.let { multiple ->
            val scaled = ShopSettings.credits.curve.amountAt(wave, multiple)
            return scaled.coerceIn(0, MAX_PRICE)
        }
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
 * One loaded shop table: every consumable the paid between-wave row can stock.
 *
 * ### There is no draw here, and that is deliberate
 *
 * An earlier version of this class had a `rollOffer` that drew entries by weight from a seeded
 * `Random`. That was the wrong model — the paid row is *always the same items*, so a player can plan
 * to save for the expensive one. Selection is now a filter in authored order; see
 * [com.cobblemonroguelite.shop.ShopStock]. The seeded, rerollable draw belongs to the FREE half of the
 * step, over the reward tables ([com.cobblemonroguelite.shop.RewardOffer]).
 */
data class ShopTable(
    val id: ResourceLocation,
    val entries: List<ShopEntry>,
) {

    /** Find an entry by the id a purchase named, or null if the table has no such entry. */
    fun entry(id: String): ShopEntry? = entries.firstOrNull { it.id == id }

}
