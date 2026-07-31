package com.cobblemonroguelite.shop

import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemonroguelite.data.reward.CurvePoint
import com.cobblemonroguelite.data.reward.RewardEntry
import com.cobblemonroguelite.data.reward.RewardTable
import com.cobblemonroguelite.data.reward.RewardTier
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
 * The between-wave step, which is **two independent halves** — a distinction the first version of this
 * module got wrong by modelling it as one shop.
 *
 * These tests are written to hold the two apart, because the failure mode is not a crash: a shop that
 * randomises its stock and a free offer you can take all of both *work*, they just are not the mechanic.
 */
class RewardOfferTest {

    private val id = ResourceLocation.fromNamespaceAndPath("test", "rewards")

    private fun entry(name: String, tier: String = "common", min: Int = 1, max: Int? = null) = RewardEntry(
        id = name,
        tier = tier,
        weight = 1.0,
        minWave = min,
        maxWave = max,
        reward = RunReward.Evs(Stats.ATTACK, 10),
    )

    private val table = RewardTable(
        id = id,
        tiers = listOf(RewardTier("common", WeightCurve(listOf(CurvePoint(1, 100.0))))),
        entries = (1..12).map { entry("item$it") },
    )

    @AfterTest
    fun tearDown() = ShopSettings.reset()

    @Test
    fun `three options are offered by default, matching PokeRogue`() {
        // The number is the mechanic: three doors, one key. Six options usually has an obvious best.
        assertEquals(3, RewardOffer.offerFor(table, wave = 10, seed = 1L).size)
    }

    @Test
    fun `the same seed, wave and reroll count always give the same three`() {
        // §2.16: a paused run resumes to the same offer, so relogging is not a free reroll.
        assertEquals(
            RewardOffer.offerFor(table, 40, 1234L).map { it.id },
            RewardOffer.offerFor(table, 40, 1234L).map { it.id },
        )
    }

    @Test
    fun `adjacent waves do not offer the same three`() {
        assertNotEquals(
            RewardOffer.offerFor(table, 40, 77L).map { it.id },
            RewardOffer.offerFor(table, 41, 77L).map { it.id },
        )
    }

    @Test
    fun `a reroll changes the offer and is itself reproducible`() {
        val before = RewardOffer.offerFor(table, 30, 9L, rerolls = 0).map { it.id }
        val after = RewardOffer.offerFor(table, 30, 9L, rerolls = 1).map { it.id }
        assertNotEquals(before, after)
        assertEquals(after, RewardOffer.offerFor(table, 30, 9L, rerolls = 1).map { it.id })
    }

    @Test
    fun `taking one of the three is free — there is no price and no balance to check`() {
        // The signature is the assertion: `take` has no credits parameter, so a free option cannot
        // accidentally acquire a cost. If this stops compiling, the two halves have been merged again.
        val offered = RewardOffer.offerFor(table, 20, 42L).first()
        val ok = assertIs<TakeResult.Ok>(RewardOffer.take(table, 20, 42L, rerolls = 0, entryId = offered.id))
        assertEquals(offered.id, ok.entry.id)
    }

    @Test
    fun `taking something not among the three is refused even though it is in the table`() {
        val offered = RewardOffer.offerFor(table, 20, 42L).map { it.id }.toSet()
        val elsewhere = table.entries.first { it.id !in offered }
        assertIs<TakeResult.NotOffered>(RewardOffer.take(table, 20, 42L, 0, elsewhere.id))
    }

    @Test
    fun `taking an id the table never had is a different refusal from one not offered`() {
        // "Refresh your screen" and "you invented an item" need different words.
        assertEquals(TakeResult.NoSuchEntry("nonsense"), RewardOffer.take(table, 20, 42L, 0, "nonsense"))
    }

    @Test
    fun `an option from the previous reroll cannot still be taken`() {
        val old = RewardOffer.offerFor(table, 30, 9L, rerolls = 0).map { it.id }.toSet()
        val new = RewardOffer.offerFor(table, 30, 9L, rerolls = 1).map { it.id }.toSet()
        val stale = (old - new).firstOrNull() ?: return
        assertIs<TakeResult.NotOffered>(RewardOffer.take(table, 30, 9L, rerolls = 1, entryId = stale))
    }

    // --- rerolls ---------------------------------------------------------------------------------

    @Test
    fun `rerolling is disabled until the server prices it`() {
        assertEquals(RerollResult.Disabled, RewardOffer.reroll(credits = 9999, rerollsTaken = 0, wave = 1))
    }

    @Test
    fun `each reroll this wave costs more than the last`() {
        ShopSettings.shop = ShopRules(rerollCost = 100, rerollGrowthHundredths = 150, rerollPerWaveHundredths = 0)
        val prices = (0..3).map { assertIs<RerollResult.Ok>(RewardOffer.reroll(99_999, it, 1)).price }
        assertEquals(listOf(100, 150, 225, 337), prices)
    }

    @Test
    fun `rerolling costs more deeper into the run`() {
        // Theirs is 250 early and 750 by wave 21. Without depth scaling the reroll becomes free in real
        // terms as earnings grow — the same failure the paid row's price curve prevents.
        ShopSettings.shop = ShopRules(rerollCost = 250, rerollPerWaveHundredths = 2500)
        val early = assertIs<RerollResult.Ok>(RewardOffer.reroll(99_999, 0, wave = 1)).price
        val deep = assertIs<RerollResult.Ok>(RewardOffer.reroll(99_999, 0, wave = 21)).price
        assertEquals(250, early)
        assertTrue(deep > early, "wave 21 reroll $deep should cost more than wave 1's $early")
    }

    @Test
    fun `an unaffordable reroll is refused and takes nothing`() {
        ShopSettings.shop = ShopRules(rerollCost = 500)
        assertEquals(RerollResult.NotEnoughCredits(have = 10, need = 500), RewardOffer.reroll(10, 0, 1))
    }
}

/**
 * The paid consumable row. The property under test is that it is **not random** — the same items every
 * wave, so a player can decide to save for the expensive one.
 */
class ShopStockTest {

    private val id = ResourceLocation.fromNamespaceAndPath("test", "shop")

    private fun entry(name: String, price: Int = 100, min: Int = 1, max: Int? = null) = ShopEntry(
        id = name,
        reward = RunReward.Evs(Stats.ATTACK, 10),
        price = price,
        minWave = min,
        maxWave = max,
    )

    private val table = ShopTable(
        id,
        listOf(entry("potion", 50), entry("ether", 100), entry("revive", 500),
               entry("super_potion", 180, min = 20), entry("full_heal", 400, min = 40)),
    )

    @AfterTest
    fun tearDown() = ShopSettings.reset()

    @Test
    fun `the row is the same every wave, not a fresh random draw`() {
        // THE CORRECTION. PokeRogue's row is Potion/Ether/Revive at wave 6 and at 7 and 8 and 9. A
        // shop that re-rolled its stock could not be planned against, which is the only thing this
        // half adds over the free offer.
        val waves = listOf(6, 7, 8, 9, 15, 19)
        val rows = waves.map { ShopStock.stockAt(table, it).map { e -> e.id } }
        assertTrue(rows.distinct().size == 1, "stock varied across waves: ${waves.zip(rows)}")
        assertEquals(listOf("potion", "ether", "revive"), rows.first())
    }

    @Test
    fun `stock takes no seed, so there is nothing to reroll`() {
        // Signature-level assertion again: if stockAt ever needs a seed, this half has drifted back
        // towards being a second random offer.
        assertEquals(ShopStock.stockAt(table, 11), ShopStock.stockAt(table, 11))
    }

    @Test
    fun `the row grows with depth`() {
        // Waves off the boss cadence: the shop is shut on every tenth, so 11/21/41 rather
        // than 10/20/40 — the rule under test here is how the row GROWS, not when it closes.
        assertEquals(3, ShopStock.stockAt(table, 11).size)
        assertEquals(4, ShopStock.stockAt(table, 21).size)
        assertEquals(5, ShopStock.stockAt(table, 41).size)
    }

    @Test
    fun `an item is stocked only once its own min_wave is reached, independent of slot count`() {
        // Two different tools: min_wave says an item exists, the slot count says how many fit.
        assertTrue(ShopStock.stockAt(table, 19).none { it.id == "super_potion" })
        assertTrue(ShopStock.stockAt(table, 21).any { it.id == "super_potion" })
    }

    @Test
    fun `stock keeps the authored order rather than sorting by price`() {
        // A row whose entries move between waves reads as random even when the contents are fixed.
        assertEquals(listOf("potion", "ether", "revive"), ShopStock.stockAt(table, 11).map { it.id })
    }

    @Test
    fun `slots at zero stock nothing rather than defaulting to everything`() {
        ShopSettings.shop = ShopRules(shopSlots = emptyList())
        assertEquals(emptyList(), ShopStock.stockAt(table, 11))
    }

    // --- buying ----------------------------------------------------------------------------------

    @Test
    fun `buying a stocked item charges its price and reports the remainder`() {
        val ok = assertIs<PurchaseResult.Ok>(ShopStock.buy(table, 11, credits = 500, entryId = "ether"))
        assertEquals(100, ok.price)
        assertEquals(400, ok.remaining)
    }

    @Test
    fun `the same item can be bought repeatedly, unlike a free option`() {
        // The paid row is not pick-one. Two purchases of the same consumable is the normal case.
        var credits = 500
        repeat(3) {
            val ok = assertIs<PurchaseResult.Ok>(ShopStock.buy(table, 11, credits, "potion"))
            credits = ok.remaining
        }
        assertEquals(350, credits)
    }

    @Test
    fun `buying something not yet stocked is refused as NotStocked`() {
        assertIs<PurchaseResult.NotStocked>(ShopStock.buy(table, 11, 9999, "full_heal"))
    }

    @Test
    fun `buying an unknown id is a different refusal`() {
        assertEquals(PurchaseResult.NoSuchEntry("nope"), ShopStock.buy(table, 11, 9999, "nope"))
    }

    @Test
    fun `an unaffordable purchase reports both numbers and takes nothing`() {
        assertEquals(
            PurchaseResult.NotEnoughCredits(have = 20, need = 500),
            ShopStock.buy(table, 11, credits = 20, entryId = "revive"),
        )
    }

    @Test
    fun `prices rise with depth when a curve says so`() {
        // Their Potion is 46/48/50/52 across waves 6-9 and 80 by 21.
        val curved = ShopTable(
            id,
            listOf(entry("potion", 50).copy(priceCurve = WeightCurve(listOf(CurvePoint(1, 100.0), CurvePoint(101, 200.0))))),
        )
        assertEquals(50, ShopStock.stockAt(curved, 1).single().priceAt(1))
        assertEquals(100, ShopStock.stockAt(curved, 101).single().priceAt(101))
    }

    @Test
    fun `the shop is shut on a boss wave, however much is stocked`() {
        // PokéRogue's rule, and §2.19 puts our bosses on the same cadence. A player who has just been
        // handed the wave's reward AND a shop after the hardest fight of the block is getting two
        // decisions where the boss should have been the moment.
        assertEquals(emptyList(), ShopStock.stockAt(table, 10))
        assertEquals(emptyList(), ShopStock.stockAt(table, 20))
        assertTrue(ShopStock.stockAt(table, 11).isNotEmpty(), "the wave after a boss should stock normally")
    }
}
