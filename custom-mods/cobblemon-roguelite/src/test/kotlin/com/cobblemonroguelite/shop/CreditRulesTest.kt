package com.cobblemonroguelite.shop

import com.cobblemonroguelite.integration.RunOpponent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What a cleared wave pays.
 *
 * The property worth pinning is not any particular number — those are balance settings — but the two
 * invariants a change must not break: **a wave pays the same every time it is fought**, which is what
 * stops §2.10's disconnect penalty from being farmable, and **payment never goes negative**, which is
 * what stops a misconfigured server from making the shop's affordability checks meaningless.
 */
class CreditRulesTest {

    private val rules = CreditRules()

    @Test
    fun `payment is ordered wild then trainer then rival then boss at the same wave`() {
        // The ordering IS the design statement — a rival sits between a trainer and a boss because it
        // is hard through its team while taking neither the boss level multiplier nor shields (§2.36).
        val paid = listOf(RunOpponent.WILD, RunOpponent.TRAINER, RunOpponent.RIVAL, RunOpponent.BOSS)
            .map { it to rules.creditsFor(50, it) }
        paid.zipWithNext().forEach { (lower, higher) ->
            assertTrue(
                lower.second < higher.second,
                "${lower.first} paid ${lower.second}, should be less than ${higher.first}'s ${higher.second}",
            )
        }
    }

    @Test
    fun `every opponent kind has a rate, so a new kind cannot default to zero`() {
        // The exhaustive `when` in creditsFor makes adding a kind a compile error rather than a silent
        // unpaid wave — which is how RIVAL was caught when it was added. This pins the outcome.
        RunOpponent.entries.forEach { kind ->
            assertTrue(rules.creditsFor(10, kind) > 0, "$kind pays nothing")
        }
    }

    @Test
    fun `payment is a function of the wave number, so refighting a wave pays the same`() {
        // The anti-farming invariant. If this ever becomes "waves cleared", a player who drops
        // connection at wave 40 repeatedly earns wave-40 money over and over on rising totals.
        repeat(3) {
            assertEquals(rules.creditsFor(40, RunOpponent.TRAINER), rules.creditsFor(40, RunOpponent.TRAINER))
        }
    }

    @Test
    fun `depth increases payment monotonically`() {
        var previous = 0
        for (wave in listOf(1, 25, 50, 100, 150, 200)) {
            val paid = rules.creditsFor(wave, RunOpponent.WILD)
            assertTrue(paid >= previous, "wave $wave paid $paid, less than the shallower $previous")
            previous = paid
        }
    }

    @Test
    fun `wave 1 pays exactly the base, with no depth bonus`() {
        // Pins the off-by-one: the slope is applied to (wave - 1), so the first wave is the base.
        assertEquals(rules.wildBase, rules.creditsFor(1, RunOpponent.WILD))
    }

    @Test
    fun `a wave below one is treated as wave one rather than paying negatively`() {
        assertEquals(rules.wildBase, rules.creditsFor(0, RunOpponent.WILD))
        assertEquals(rules.wildBase, rules.creditsFor(-5, RunOpponent.WILD))
    }

    @Test
    fun `negative settings floor at zero instead of draining the balance`() {
        val broken = CreditRules(
            wildBase = -100, trainerBase = -100, rivalBase = -100, bossBase = -100, perWaveHundredths = -100,
        )
        for (kind in RunOpponent.entries) {
            assertEquals(0, broken.creditsFor(100, kind), "$kind should floor at 0")
        }
    }

    @Test
    fun `the per-wave slope is expressed in hundredths so sub-credit ramps are possible`() {
        // 35 hundredths per wave => 100 waves of depth is 34 extra credits, not 3500 and not 0.
        val at101 = CreditRules(wildBase = 0, perWaveHundredths = 35).creditsFor(101, RunOpponent.WILD)
        assertEquals(35, at101)
    }
}

/**
 * Reroll pricing, which exists to stop a large balance simply buying the whole table.
 */
class ShopRulesTest {

    @Test
    fun `rerolling is disabled by default`() {
        assertEquals(null, ShopRules().rerollPrice(0))
    }

    @Test
    fun `each reroll costs more than the last`() {
        val rules = ShopRules(rerollCost = 100, rerollGrowthHundredths = 150)
        val prices = (0..4).map { rules.rerollPrice(it)!! }
        assertEquals(listOf(100, 150, 225, 337, 505), prices)
        assertTrue(prices.zipWithNext().all { (a, b) -> b > a })
    }

    @Test
    fun `a growth multiplier at or below one hundred never cheapens below the base`() {
        // A multiplier of 100 is "no growth" and 50 would be "each reroll is half price", which would
        // make rerolling free in practice. The floor keeps a misconfiguration merely pointless.
        val flat = ShopRules(rerollCost = 100, rerollGrowthHundredths = 50)
        assertEquals(100, flat.rerollPrice(5))
    }

    @Test
    fun `price is clamped rather than overflowing on an absurd multiplier`() {
        val runaway = ShopRules(rerollCost = 1000, rerollGrowthHundredths = 10_000)
        val price = runaway.rerollPrice(50)!!
        assertTrue(price in 1..1_000_000, "expected a clamped price, got $price")
    }

    @Test
    fun `a negative reroll count is treated as none taken`() {
        val rules = ShopRules(rerollCost = 80)
        assertEquals(80, rules.rerollPrice(-3))
    }
}
