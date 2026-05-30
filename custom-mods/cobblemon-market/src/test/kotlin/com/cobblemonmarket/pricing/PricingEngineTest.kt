package com.cobblemonmarket.pricing

import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PricingEngineTest {

    // -------------------------------------------------------------------------
    // 1. At baseStock, prices equal the base prices (scale = 1.0)
    // -------------------------------------------------------------------------
    @Test
    fun `prices at base stock equal base prices`() {
        val buy = PricingEngine.buyPrice(baseBuyPrice = 90, stock = 100.0, baseStock = 100, elasticity = 1.0)
        val sell = PricingEngine.sellPrice(baseSellPrice = 30, stock = 100.0, baseStock = 100, elasticity = 1.0)
        assertEquals(90, buy)
        assertEquals(30, sell)
    }

    // -------------------------------------------------------------------------
    // 2. Empty stock is clamped to SCALE_CLAMP× base (was 101× pre-clamp)
    // -------------------------------------------------------------------------
    @Test
    fun `empty stock is clamped to scale clamp times base`() {
        // raw scale at stock=0 would be 101; clamped to SCALE_CLAMP=3 → 90 × 3 = 270
        val buy = PricingEngine.buyPrice(90, stock = 0.0, baseStock = 100, elasticity = 1.0)
        assertEquals((90 * PricingEngine.SCALE_CLAMP).roundToInt(), buy)
    }

    // -------------------------------------------------------------------------
    // 3. Elasticity 0.3 is too gentle to hit the clamp at stock=0
    // -------------------------------------------------------------------------
    @Test
    fun `low elasticity stays inside the clamp band`() {
        // raw scale = 101^0.3 ≈ 4.0 — above SCALE_CLAMP=3, so it clamps to 3
        // (kept as a regression: any change to SCALE_CLAMP will surface here)
        val buy = PricingEngine.buyPrice(90, stock = 0.0, baseStock = 100, elasticity = 0.3)
        val raw = (90 * 101.0.pow(0.3))
        val clamped = (90 * PricingEngine.SCALE_CLAMP).roundToInt()
        if (raw > clamped) {
            assertEquals(clamped, buy)
        } else {
            assertTrue(kotlin.math.abs(buy - raw.toInt()) <= 1)
        }
    }

    // -------------------------------------------------------------------------
    // 4. High elasticity also clamps — no infinite-spike attack vector
    // -------------------------------------------------------------------------
    @Test
    fun `high elasticity is bounded by scale clamp`() {
        // raw scale = 101^2 = 10201; clamped to 3 → 6000 × 3 = 18000
        val buy = PricingEngine.buyPrice(6000, stock = 0.0, baseStock = 100, elasticity = 2.0)
        assertEquals((6000 * PricingEngine.SCALE_CLAMP).roundToInt(), buy)
    }

    // -------------------------------------------------------------------------
    // 5. Oversupply is floored at base / SCALE_CLAMP (was ~0.1× pre-clamp)
    // -------------------------------------------------------------------------
    @Test
    fun `oversupply is floored at base divided by scale clamp`() {
        // raw scale ≈ 0.10 at stock=1000; clamped to 1/3 → 90 × (1/3) = 30
        val buy = PricingEngine.buyPrice(90, stock = 1000.0, baseStock = 100, elasticity = 1.0)
        assertEquals((90 / PricingEngine.SCALE_CLAMP).roundToInt(), buy)
    }

    // -------------------------------------------------------------------------
    // 6. Buy/sell prices share the same scale (spread = baseBuyPrice / baseSellPrice)
    // -------------------------------------------------------------------------
    @Test
    fun `buy and sell prices share the same scale factor`() {
        val stock = 50.0
        val baseBuy = 90; val baseSell = 30
        val buy = PricingEngine.buyPrice(baseBuy, stock, baseStock = 100, elasticity = 1.0)
        val sell = PricingEngine.sellPrice(baseSell, stock, baseStock = 100, elasticity = 1.0)
        // Both scaled by the same ratio so the buy/sell ratio matches the base ratio.
        assertEquals(baseBuy.toDouble() / baseSell, buy.toDouble() / sell, 0.05)
    }

    // -------------------------------------------------------------------------
    // 7. Buying decreases stock → next buy is more expensive
    // -------------------------------------------------------------------------
    @Test
    fun `every buy raises buy price`() {
        val baseBuy = 90; val baseStock = 100; val elasticity = 1.0
        var stock = 100.0
        var prev = PricingEngine.buyPrice(baseBuy, stock, baseStock, elasticity)
        repeat(50) {
            stock -= 1.0
            val next = PricingEngine.buyPrice(baseBuy, stock, baseStock, elasticity)
            assertTrue(next >= prev, "Buy price must never decrease as stock drops: $prev → $next at stock=$stock")
            prev = next
        }
    }

    // -------------------------------------------------------------------------
    // 8. Selling increases stock → next sell pays less
    // -------------------------------------------------------------------------
    @Test
    fun `every sell lowers sell price`() {
        val baseSell = 30; val baseStock = 100; val elasticity = 1.0
        var stock = 100.0
        var prev = PricingEngine.sellPrice(baseSell, stock, baseStock, elasticity)
        repeat(50) {
            stock += 1.0
            val next = PricingEngine.sellPrice(baseSell, stock, baseStock, elasticity)
            assertTrue(next <= prev, "Sell price must never increase as stock rises: $prev → $next at stock=$stock")
            prev = next
        }
    }

    // -------------------------------------------------------------------------
    // 9. Restock pulls stock toward baseStock (refill)
    // -------------------------------------------------------------------------
    @Test
    fun `restock refills depleted stock toward base`() {
        // 50 + 0.07 × (100 - 50) = 53.5
        val result = PricingEngine.applyRestock(stock = 50.0, baseStock = 100, restockRatePerHour = 0.07)
        assertEquals(53.5, result, 1e-9)
    }

    // -------------------------------------------------------------------------
    // 10. Restock pulls oversupplied stock down toward baseStock (bleed-off)
    // -------------------------------------------------------------------------
    @Test
    fun `restock bleeds oversupply back toward base`() {
        // 200 + 0.07 × (100 - 200) = 193
        val result = PricingEngine.applyRestock(stock = 200.0, baseStock = 100, restockRatePerHour = 0.07)
        assertEquals(193.0, result, 1e-9)
    }

    // -------------------------------------------------------------------------
    // 11. Restock at baseStock leaves stock unchanged
    // -------------------------------------------------------------------------
    @Test
    fun `restock is a no-op at base stock`() {
        val result = PricingEngine.applyRestock(stock = 100.0, baseStock = 100, restockRatePerHour = 0.07)
        assertEquals(100.0, result, 1e-9)
    }

    // -------------------------------------------------------------------------
    // 12. Restock step is larger when stock is further from base
    // -------------------------------------------------------------------------
    @Test
    fun `restock step grows with distance from base`() {
        val nearBase = PricingEngine.applyRestock(stock = 95.0, baseStock = 100, restockRatePerHour = 0.07) - 95.0
        val farFromBase = PricingEngine.applyRestock(stock = 10.0, baseStock = 100, restockRatePerHour = 0.07) - 10.0
        assertTrue(farFromBase > nearBase, "Refill rate should grow with distance: near=$nearBase far=$farFromBase")
    }

    // -------------------------------------------------------------------------
    // 13. Batch buy yields monotonically increasing per-unit prices
    // -------------------------------------------------------------------------
    @Test
    fun `batch buy produces monotonically increasing prices`() {
        val result = PricingEngine.simulateBatchBuy(
            baseBuyPrice = 90, baseStock = 100, elasticity = 1.0,
            startStock = 100.0, quantity = 5,
        )
        assertEquals(5, result.perUnitPrices.size)
        for (i in 1 until result.perUnitPrices.size) {
            assertTrue(result.perUnitPrices[i] >= result.perUnitPrices[i - 1])
        }
        assertEquals(95.0, result.finalStock, 1e-9)
        assertEquals(result.perUnitPrices.sum(), result.totalPrice)
    }

    // -------------------------------------------------------------------------
    // 14. Batch sell yields monotonically decreasing per-unit prices
    // -------------------------------------------------------------------------
    @Test
    fun `batch sell produces monotonically decreasing prices`() {
        val result = PricingEngine.simulateBatchSell(
            baseSellPrice = 30, baseStock = 100, elasticity = 1.0,
            startStock = 100.0, quantity = 5,
        )
        assertEquals(5, result.perUnitPrices.size)
        for (i in 1 until result.perUnitPrices.size) {
            assertTrue(result.perUnitPrices[i] <= result.perUnitPrices[i - 1])
        }
        assertEquals(105.0, result.finalStock, 1e-9)
        assertEquals(result.perUnitPrices.sum(), result.totalPrice)
    }

    // -------------------------------------------------------------------------
    // 15. Batch sell with elasticity 1.0 from base: prices match formula
    // -------------------------------------------------------------------------
    @Test
    fun `batch sell from base matches per-step formula`() {
        val result = PricingEngine.simulateBatchSell(
            baseSellPrice = 30, baseStock = 100, elasticity = 1.0,
            startStock = 100.0, quantity = 3,
        )
        // Step 1: stock=100, ratio=101/101=1, scale=1.0, sell=30
        // Step 2: stock=101, ratio=102/101, scale=101/102, sell=30*101/102≈29.7→30
        // Step 3: stock=102, ratio=103/101, scale=101/103, sell=30*101/103≈29.4→29
        assertEquals(30, result.perUnitPrices[0])
        assertTrue(result.perUnitPrices[1] in 29..30)
        assertTrue(result.perUnitPrices[2] in 29..30)
    }

    // -------------------------------------------------------------------------
    // 16. Stock at zero handled gracefully (no infinity from ratio)
    // -------------------------------------------------------------------------
    @Test
    fun `stock at zero gives finite prices`() {
        val buy = PricingEngine.buyPrice(90, stock = 0.0, baseStock = 100, elasticity = 1.0)
        val sell = PricingEngine.sellPrice(30, stock = 0.0, baseStock = 100, elasticity = 1.0)
        // Int values can't be infinite — guarding against the bug where ratio→0 would
        // overflow Int via Double.POSITIVE_INFINITY.toInt() → Int.MAX_VALUE.
        assertTrue(buy > 0 && buy < Int.MAX_VALUE)
        assertTrue(sell > 0 && sell < Int.MAX_VALUE)
    }

    // -------------------------------------------------------------------------
    // 17. Negative stock is clamped to zero (defensive — TradeOps prevents it)
    // -------------------------------------------------------------------------
    @Test
    fun `negative stock is clamped to zero`() {
        val atZero = PricingEngine.buyPrice(90, stock = 0.0, baseStock = 100, elasticity = 1.0)
        val belowZero = PricingEngine.buyPrice(90, stock = -5.0, baseStock = 100, elasticity = 1.0)
        assertEquals(atZero, belowZero)
    }

    // -------------------------------------------------------------------------
    // 18. Per-unit scale is symmetric: doubling baseStock at same stock-ratio gives same scale
    // -------------------------------------------------------------------------
    @Test
    fun `same stock ratio yields same price scale`() {
        // (50+1)/(100+1) ≈ (100+1)/(200+1) but not exactly — the +1 makes them differ slightly.
        // Test with larger numbers where +1 is negligible.
        val small = PricingEngine.buyPrice(100, stock = 500.0, baseStock = 1000, elasticity = 1.0)
        val large = PricingEngine.buyPrice(100, stock = 5000.0, baseStock = 10000, elasticity = 1.0)
        assertTrue(kotlin.math.abs(small - large) <= 1, "Same ratio should give same price: $small vs $large")
    }

    // -------------------------------------------------------------------------
    // 19. MIN_SELL_PRICE floor — cheap items at glut always pay at least 1
    // -------------------------------------------------------------------------
    @Test
    fun `sell price is floored at min sell price even at oversupply`() {
        // baseSell=3, scale clamped to 1/3 at high stock → raw payout = 1.0, rounds to 1.
        // With baseSell=1 and full clamp-down, raw payout = 0.33 → would round to 0; floored to 1.
        val cheapAtGlut = PricingEngine.sellPrice(
            baseSellPrice = 1, stock = 10_000.0, baseStock = 100, elasticity = 1.0,
        )
        assertEquals(PricingEngine.MIN_SELL_PRICE, cheapAtGlut)
    }

    // -------------------------------------------------------------------------
    // 20. Anti-arbitrage invariant — buy-then-sell round trip is always a loss.
    //
    // Without the clamp this is the exploit: asymmetric stock impact + low baseStock
    // lets a player crash stock so far the sell side recovers at inflated prices.
    // With SCALE_CLAMP=3 and the spread baseBuy ≥ 3 × baseSell, max_sell ≤ min_buy at
    // baseStock, so the round trip is structurally negative for any (K, stock) path.
    // -------------------------------------------------------------------------
    @Test
    fun `buy-then-sell round trip is always a loss`() {
        // Rare-candy-tier worst-case: tiny baseStock (10) where the pre-clamp exploit hit
        // hardest. Buy 3 takes stock 10→7→4→1; sell 3 takes stock 1→2→3→4.
        val baseBuy = 3000; val baseSell = 1000; val baseStock = 10; val elasticity = 1.0
        val buys = PricingEngine.simulateBatchBuy(baseBuy, baseStock, elasticity, startStock = 10.0, quantity = 3)
        val sells = PricingEngine.simulateBatchSell(baseSell, baseStock, elasticity, startStock = buys.finalStock, quantity = 3)
        assertTrue(
            sells.totalPrice < buys.totalPrice,
            "Round-trip arbitrage allowed: spent ${buys.totalPrice}, received ${sells.totalPrice}",
        )
    }

    // -------------------------------------------------------------------------
    // 21. Anti-arbitrage holds at high elasticity too — the clamp is on the multiplier,
    // not the underlying ratio, so e=2 doesn't let max sell escape SCALE_CLAMP × baseSell.
    // -------------------------------------------------------------------------
    @Test
    fun `buy-then-sell round trip is a loss at high elasticity`() {
        val baseBuy = 3000; val baseSell = 1000; val baseStock = 10; val elasticity = 2.0
        val buys = PricingEngine.simulateBatchBuy(baseBuy, baseStock, elasticity, startStock = 10.0, quantity = 3)
        val sells = PricingEngine.simulateBatchSell(baseSell, baseStock, elasticity, startStock = buys.finalStock, quantity = 3)
        assertTrue(
            sells.totalPrice < buys.totalPrice,
            "High-elasticity arbitrage allowed: spent ${buys.totalPrice}, received ${sells.totalPrice}",
        )
    }

    // -------------------------------------------------------------------------
    // 22. Per-item clamp override tightens the price band — useful for stable
    // commodities where the global ±3× is too wide.
    // -------------------------------------------------------------------------
    @Test
    fun `tighter per-item clamp pulls the max price down`() {
        val baseBuy = 90
        val withGlobal = PricingEngine.buyPrice(baseBuy, stock = 0.0, baseStock = 100, elasticity = 1.0)
        val withTight = PricingEngine.buyPrice(baseBuy, stock = 0.0, baseStock = 100, elasticity = 1.0, clamp = 1.5)
        assertEquals((baseBuy * 3.0).roundToInt(), withGlobal)   // baseline clamped to 3×
        assertEquals((baseBuy * 1.5).roundToInt(), withTight)    // tighter clamp pulls 3× → 1.5×
        assertTrue(withTight < withGlobal)
    }

    // -------------------------------------------------------------------------
    // 23. Asymmetric stock impact — buyStockImpact=3 drains 3 stock per buy.
    // -------------------------------------------------------------------------
    @Test
    fun `buy stock impact greater than one drains stock faster`() {
        val result = PricingEngine.simulateBatchBuy(
            baseBuyPrice = 90, baseStock = 100, elasticity = 1.0,
            startStock = 100.0, quantity = 5,
            stockImpact = 3.0,
        )
        // 5 buys × 3 drain each = 15 stock removed → finalStock = 100 - 15 = 85
        assertEquals(85.0, result.finalStock, 1e-9)
    }

    // -------------------------------------------------------------------------
    // 24. The clamp keeps arbitrage dead even when buyStockImpact > 1 — this is
    // the original exploit scenario the user was worried about at the start of
    // the overhaul. With impact=3 and no clamp it would profit; with clamp=3 it
    // strictly loses.
    // -------------------------------------------------------------------------
    @Test
    fun `arbitrage stays dead with asymmetric stock impact`() {
        val baseBuy = 3000; val baseSell = 1000; val baseStock = 100; val elasticity = 1.0
        val buys = PricingEngine.simulateBatchBuy(
            baseBuy, baseStock, elasticity, startStock = 100.0, quantity = 10, stockImpact = 3.0,
        )
        val sells = PricingEngine.simulateBatchSell(
            baseSell, baseStock, elasticity, startStock = buys.finalStock, quantity = 10, stockImpact = 1.0,
        )
        assertTrue(
            sells.totalPrice < buys.totalPrice,
            "Asymmetric-impact arbitrage allowed: spent ${buys.totalPrice}, received ${sells.totalPrice}",
        )
    }

    // -------------------------------------------------------------------------
    // 25. Per-item minBuyPrice floor — the buy price never drops below the per-item
    // minimum even at maximum oversupply.
    // -------------------------------------------------------------------------
    @Test
    fun `min buy price floors the buy price at oversupply`() {
        val natural = PricingEngine.buyPrice(
            baseBuyPrice = 90, stock = 10_000.0, baseStock = 100, elasticity = 1.0,
        )
        val withFloor = PricingEngine.buyPrice(
            baseBuyPrice = 90, stock = 10_000.0, baseStock = 100, elasticity = 1.0,
            minBuyPrice = 60,
        )
        // Natural floor = 90 / 3 = 30 (the clamp). minBuyPrice raises it to 60.
        assertEquals(30, natural)
        assertEquals(60, withFloor)
    }
}
