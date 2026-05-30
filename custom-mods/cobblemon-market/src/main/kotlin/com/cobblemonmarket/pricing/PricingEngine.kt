package com.cobblemonmarket.pricing

import kotlin.math.pow
import kotlin.math.roundToInt

data class BatchResult(
    val perUnitPrices: List<Int>,
    val totalPrice: Int,
    val finalStock: Double,
)

/**
 * Stock-based pricing.
 *
 *   ratio = (stock + 1) / (baseStock + 1)
 *   scale = ratio^(-elasticity)         (then clamped to [1/SCALE_CLAMP, SCALE_CLAMP])
 *   buyPrice  = baseBuyPrice  × scale
 *   sellPrice = max(baseSellPrice × scale, MIN_SELL_PRICE)
 *
 * The +1 keeps the ratio finite when stock hits 0 — at stock=0 the raw scale would be
 * (baseStock+1)^elasticity, but the [SCALE_CLAMP] cap pins it to a sane multiplier.
 *
 * Per-item `elasticity` controls volatility within the clamp band: <1 for stable
 * consumables (Poké Balls), 1.0 for "ordinary" items, >1 for rare items where the price
 * should reach the clamped ceiling faster. Outside the clamp the multiplier is flat —
 * elasticity > 1 makes the band saturate sooner but doesn't push prices past 3×.
 *
 * **Per-item overrides** (all optional, default to the global values shown):
 *   - `clamp`        — default [SCALE_CLAMP] = 3.0. Independently overridable for buy
 *                      vs sell on `ItemEntry`; useful for tightening the band on stable
 *                      items (e.g., Poké Balls at 1.5) or loosening it for rares.
 *   - `stockImpact`  — default 1.0 each side. Setting `buyStockImpact = 3` makes a single
 *                      buy drain 3 stock units; the [SCALE_CLAMP] guarantees this can't be
 *                      arbitraged into a buy-then-sell profit (see Anti-arbitrage below).
 *   - `minBuyPrice`  — default 0. Hard floor on the buy price after the clamp; useful when
 *                      the natural floor (baseBuy / clamp) is too cheap for a rare item.
 *
 * **Anti-arbitrage invariant:** with the spread `baseBuyPrice ≥ 3 × baseSellPrice` and
 * SCALE_CLAMP = 3, `max sellPrice = 3 × baseSellPrice ≤ baseBuyPrice = min buyPrice
 * (at stock = baseStock)`. A buy-low-then-sell-high round trip is structurally
 * loss-making regardless of how hard the player crashes stock or what elasticity is.
 */
object PricingEngine {

    /** Max upward price multiplier from the base price; reciprocal is the floor. */
    const val SCALE_CLAMP = 3.0

    /** Global floor on sell payouts. Always pays at least this many cobbledollars per unit. */
    const val MIN_SELL_PRICE = 1

    // @JvmOverloads keeps the 4-arg Java signatures (Int, Double, Int, Double) reachable so
    // existing reflection bridges (cobblemon-carrots' MarketBridge) keep resolving the method
    // without knowing about the new clamp / minBuyPrice params.
    @JvmOverloads
    fun buyPrice(
        baseBuyPrice: Int, stock: Double, baseStock: Int, elasticity: Double,
        clamp: Double = SCALE_CLAMP,
        minBuyPrice: Int = 0,
    ): Int = (baseBuyPrice * scaleFor(stock, baseStock, elasticity, clamp)).roundToInt()
        .coerceAtLeast(minBuyPrice)

    @JvmOverloads
    fun sellPrice(
        baseSellPrice: Int, stock: Double, baseStock: Int, elasticity: Double,
        clamp: Double = SCALE_CLAMP,
    ): Int = (baseSellPrice * scaleFor(stock, baseStock, elasticity, clamp)).roundToInt()
        .coerceAtLeast(MIN_SELL_PRICE)

    /**
     * Pulls stock toward `baseStock` by [restockRatePerHour] of the gap, applied once per
     * real-time hour. Symmetric: stock above target also bleeds back down (oversupply
     * doesn't linger forever after a sell-off).
     */
    fun applyRestock(stock: Double, baseStock: Int, restockRatePerHour: Double): Double =
        stock + restockRatePerHour * (baseStock - stock)

    /**
     * Simulates buying [quantity] units in sequence. For each unit: record the buy price
     * at the current stock, then decrement stock by 1. Caller must have already verified
     * stock ≥ quantity (see TradeOps.buy → OutOfStock).
     */
    fun simulateBatchBuy(
        baseBuyPrice: Int, baseStock: Int, elasticity: Double,
        startStock: Double, quantity: Int,
        clamp: Double = SCALE_CLAMP,
        stockImpact: Double = 1.0,
        minBuyPrice: Int = 0,
    ): BatchResult {
        val prices = ArrayList<Int>(quantity)
        var stock = startStock
        repeat(quantity) {
            prices.add(buyPrice(baseBuyPrice, stock, baseStock, elasticity, clamp, minBuyPrice))
            stock -= stockImpact
        }
        return BatchResult(prices, prices.sum(), stock)
    }

    /**
     * Simulates selling [quantity] units in sequence. For each unit: record the sell price
     * at the current stock, then increment stock by 1. Caller must have already verified
     * stock + quantity ≤ maxStock (see TradeOps.sell → MarketSaturated).
     */
    fun simulateBatchSell(
        baseSellPrice: Int, baseStock: Int, elasticity: Double,
        startStock: Double, quantity: Int,
        clamp: Double = SCALE_CLAMP,
        stockImpact: Double = 1.0,
    ): BatchResult {
        val prices = ArrayList<Int>(quantity)
        var stock = startStock
        repeat(quantity) {
            prices.add(sellPrice(baseSellPrice, stock, baseStock, elasticity, clamp))
            stock += stockImpact
        }
        return BatchResult(prices, prices.sum(), stock)
    }

    private fun scaleFor(stock: Double, baseStock: Int, elasticity: Double, clamp: Double): Double {
        val s = stock.coerceAtLeast(0.0)
        val ratio = (s + 1.0) / (baseStock + 1.0)
        return ratio.pow(-elasticity).coerceIn(1.0 / clamp, clamp)
    }
}
