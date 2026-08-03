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
    fun `every opponent kind has a rate, and wild's is deliberately zero`() {
        // Was "a new kind cannot default to zero". Wild now pays nothing ON PURPOSE — money comes from
        // trainers so that meeting one is a thing that happens to the run — so the property worth
        // pinning is that every OTHER kind pays, and that wild's zero is stated rather than forgotten.
        assertEquals(0, rules.creditsFor(50, RunOpponent.WILD))
        RunOpponent.entries.filter { it != RunOpponent.WILD }.forEach { kind ->
            assertTrue(rules.creditsFor(50, kind) > 0, "$kind pays nothing")
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
            val paid = rules.creditsFor(wave, RunOpponent.TRAINER)
            assertTrue(paid >= previous, "wave $wave paid $paid, less than the shallower $previous")
            previous = paid
        }
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

    @Test
    fun `negative multipliers pay nothing instead of draining the balance`() {
        // The old model floored a base-plus-slope; the curve is multiplied instead, so the misconfigured
        // shape is a negative multiplier. Same requirement: a wave pays nothing rather than going
        // backwards and underflowing the shop's affordability checks.
        val broken = CreditRules(
            wildMultiplier = -1.0,
            trainerMultiplier = -1.0,
            rivalMultiplier = -1.0,
            bossMultiplier = -1.0,
        )
        for (kind in RunOpponent.entries) {
            assertEquals(0, broken.creditsFor(100, kind), "$kind should floor at 0")
        }
    }

    @Test
    fun `a wave below one is treated as wave one rather than inverting the curve`() {
        assertEquals(
            rules.creditsFor(1, RunOpponent.TRAINER),
            rules.creditsFor(0, RunOpponent.TRAINER),
        )
        assertEquals(
            rules.creditsFor(1, RunOpponent.TRAINER),
            rules.creditsFor(-5, RunOpponent.TRAINER),
        )
    }
}
