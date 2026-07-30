package com.cobblemonroguelite.shop

import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemonroguelite.data.reward.CurvePoint
import com.cobblemonroguelite.data.reward.RunReward
import com.cobblemonroguelite.data.reward.WeightCurve
import com.cobblemonroguelite.data.shop.ShopEntry
import com.cobblemonroguelite.data.shop.ShopTable
import net.minecraft.resources.ResourceLocation
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The between-wave shop's decision layer.
 *
 * The offer is **derived** from (run seed, wave, rerolls) rather than stored, so the tests that matter
 * are the determinism ones: §2.16 promises a paused run resumes to the same shop, and a shop that
 * re-rolled on resume would be a free reroll for anyone willing to relog.
 */
class ShopOfferTest {

    private val id = ResourceLocation.fromNamespaceAndPath("test", "shop")

    private fun entry(name: String, price: Int = 100, weight: Double = 1.0, min: Int = 1, max: Int? = null) =
        ShopEntry(
            id = name,
            reward = RunReward.Evs(Stats.ATTACK, 10),
            price = price,
            weight = weight,
            minWave = min,
            maxWave = max,
        )

    private val table = ShopTable(id, (1..12).map { entry("item$it") })

    @AfterTest
    fun tearDown() = ShopSettings.reset()

    @Test
    fun `the same seed, wave and reroll count always give the same offer`() {
        val first = ShopOffer.offerFor(table, wave = 40, seed = 1234L)
        val again = ShopOffer.offerFor(table, wave = 40, seed = 1234L)
        assertEquals(first.map { it.id }, again.map { it.id })
    }

    @Test
    fun `adjacent waves do not offer the same items`() {
        // The reason the stream is mixed rather than `Random(seed + wave)`: an additive seed gives
        // adjacent waves adjacent first draws, which for a small table is frequently the same draw.
        val at40 = ShopOffer.offerFor(table, wave = 40, seed = 77L).map { it.id }
        val at41 = ShopOffer.offerFor(table, wave = 41, seed = 77L).map { it.id }
        assertNotEquals(at40, at41)
    }

    @Test
    fun `different runs at the same wave see different shops`() {
        val runA = ShopOffer.offerFor(table, wave = 40, seed = 1L).map { it.id }
        val runB = ShopOffer.offerFor(table, wave = 40, seed = 2L).map { it.id }
        assertNotEquals(runA, runB)
    }

    @Test
    fun `a reroll changes the offer and is itself reproducible`() {
        val before = ShopOffer.offerFor(table, wave = 30, seed = 9L, rerolls = 0).map { it.id }
        val after = ShopOffer.offerFor(table, wave = 30, seed = 9L, rerolls = 1).map { it.id }
        assertNotEquals(before, after)
        // Reroll three, log out, log in: still offer three.
        assertEquals(
            ShopOffer.offerFor(table, wave = 30, seed = 9L, rerolls = 3).map { it.id },
            ShopOffer.offerFor(table, wave = 30, seed = 9L, rerolls = 3).map { it.id },
        )
    }

    @Test
    fun `an offer never repeats an item`() {
        val offer = ShopOffer.offerFor(table, wave = 10, seed = 5L, slots = 6)
        assertEquals(offer.size, offer.map { it.id }.distinct().size)
    }

    @Test
    fun `an offer is capped by the number of eligible entries, not padded`() {
        val thin = ShopTable(id, listOf(entry("only"), entry("late", min = 100)))
        val offer = ShopOffer.offerFor(thin, wave = 1, seed = 3L, slots = 4)
        assertEquals(listOf("only"), offer.map { it.id })
    }

    @Test
    fun `wave bands gate what can be offered at all`() {
        val banded = ShopTable(id, listOf(entry("early", max = 20), entry("late", min = 21)))
        assertEquals(listOf("early"), ShopOffer.offerFor(banded, 10, 1L, slots = 4).map { it.id })
        assertEquals(listOf("late"), ShopOffer.offerFor(banded, 30, 1L, slots = 4).map { it.id })
    }

    @Test
    fun `a table weighted entirely at zero offers nothing rather than the first entry`() {
        val zeroed = ShopTable(id, listOf(entry("a", weight = 0.0), entry("b", weight = 0.0)))
        assertEquals(emptyList(), ShopOffer.offerFor(zeroed, 10, 1L, slots = 4))
    }

    // ------------------------------------------------------------------ purchases

    @Test
    fun `buying something on offer charges its price and reports the remainder`() {
        val offered = ShopOffer.offerFor(table, wave = 20, seed = 42L).first()
        val result = ShopOffer.purchase(table, 20, 42L, rerolls = 0, credits = 500, entryId = offered.id)
        val ok = assertIs<PurchaseResult.Ok>(result)
        assertEquals(offered.id, ok.entry.id)
        assertEquals(100, ok.price)
        assertEquals(400, ok.remaining)
    }

    @Test
    fun `buying something in the table but not on sale is refused as NotOffered`() {
        // What a stale shop screen produces, and it must be distinguishable from a typo: the fix is
        // "refresh", not "you invented an item".
        val offered = ShopOffer.offerFor(table, wave = 20, seed = 42L).map { it.id }.toSet()
        val notOffered = table.entries.first { it.id !in offered }
        val result = ShopOffer.purchase(table, 20, 42L, 0, credits = 9999, entryId = notOffered.id)
        assertIs<PurchaseResult.NotOffered>(result)
    }

    @Test
    fun `buying an id the table has never heard of is refused as NoSuchEntry`() {
        val result = ShopOffer.purchase(table, 20, 42L, 0, credits = 9999, entryId = "nonsense")
        assertEquals(PurchaseResult.NoSuchEntry("nonsense"), result)
    }

    @Test
    fun `an unaffordable purchase reports both numbers and takes nothing`() {
        val offered = ShopOffer.offerFor(table, wave = 20, seed = 42L).first()
        val result = ShopOffer.purchase(table, 20, 42L, 0, credits = 30, entryId = offered.id)
        assertEquals(PurchaseResult.NotEnoughCredits(have = 30, need = 100), result)
    }

    @Test
    fun `a purchase resolved against the wrong reroll count cannot buy the old offer`() {
        // The guard that makes recomputing the offer worth it: after a reroll, yesterday's screen must
        // not still be spendable.
        val old = ShopOffer.offerFor(table, wave = 30, seed = 9L, rerolls = 0).map { it.id }.toSet()
        val new = ShopOffer.offerFor(table, wave = 30, seed = 9L, rerolls = 1).map { it.id }.toSet()
        val goneStale = (old - new).firstOrNull() ?: return
        assertIs<PurchaseResult.NotOffered>(
            ShopOffer.purchase(table, 30, 9L, rerolls = 1, credits = 9999, entryId = goneStale),
        )
    }

    // ------------------------------------------------------------------ prices

    @Test
    fun `a price curve scales the authored price as a percentage`() {
        val curved = entry("patch", price = 200).copy(
            priceCurve = WeightCurve(listOf(CurvePoint(1, 100.0), CurvePoint(101, 300.0))),
        )
        assertEquals(200, curved.priceAt(1))
        assertEquals(600, curved.priceAt(101))
        // Halfway along the line, so halfway between the two multipliers.
        assertEquals(400, curved.priceAt(51))
    }

    @Test
    fun `a price without a curve is flat`() {
        assertEquals(100, entry("flat").priceAt(1))
        assertEquals(100, entry("flat").priceAt(200))
    }

    @Test
    fun `an absurd curve is clamped rather than wrapping negative`() {
        val silly = entry("x", price = 1_000_000).copy(
            priceCurve = WeightCurve(listOf(CurvePoint(1, 1_000_000.0))),
        )
        assertTrue(silly.priceAt(1) in 0..1_000_000)
    }

    // ------------------------------------------------------------------ rerolls

    @Test
    fun `rerolling is disabled unless the server prices it`() {
        assertEquals(RerollResult.Disabled, ShopOffer.reroll(credits = 9999, rerollsTaken = 0))
    }

    @Test
    fun `a priced reroll charges and reports the remainder`() {
        ShopSettings.shop = ShopRules(rerollCost = 50, rerollGrowthHundredths = 200)
        assertEquals(RerollResult.Ok(price = 50, remaining = 150), ShopOffer.reroll(credits = 200, rerollsTaken = 0))
        assertEquals(RerollResult.Ok(price = 100, remaining = 100), ShopOffer.reroll(credits = 200, rerollsTaken = 1))
    }

    @Test
    fun `an unaffordable reroll is refused`() {
        ShopSettings.shop = ShopRules(rerollCost = 500)
        assertEquals(RerollResult.NotEnoughCredits(have = 10, need = 500), ShopOffer.reroll(10, 0))
    }

    @Test
    fun `the offer slot count comes from settings when the caller does not say`() {
        ShopSettings.shop = ShopRules(offerSlots = 2)
        assertEquals(2, ShopOffer.offerFor(table, wave = 10, seed = 1L).size)
    }
}
