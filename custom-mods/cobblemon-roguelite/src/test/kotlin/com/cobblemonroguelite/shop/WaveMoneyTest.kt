package com.cobblemonroguelite.shop

import com.cobblemonroguelite.integration.RunOpponent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The economy curve.
 *
 * The values in [reproduces PokeRogue's own numbers] were computed from their `getWaveMoneyAmount`
 * verbatim (see `docs/roguelite-economy-reference.md`). They are here because the curve is the one
 * thing in the economy nobody can eyeball: a transposed term or a `%` where a `/` belongs still
 * produces a plausible-looking rising line, and the only way to know it is *their* line is to check
 * against numbers taken from their source.
 */
class WaveMoneyTest {

    private val curve = WaveMoneyCurve()

    @Test
    fun `reproduces PokeRogue's own numbers`() {
        // wave -> getWaveMoneyAmount(1), from their formula:
        //   setIndex = ceil(w/10)-1;  scale = setIndex + 1 + (0.75 + ((w-1)%10 + 1)/10)
        //   floor(((scale*100) ^ (1 + 0.005*setIndex)) / 10) * 10
        val expected = mapOf(
            1 to 180,
            5 to 220,
            10 to 270,
            11 to 290,
            20 to 380,
            50 to 760,
            100 to 1610,
            200 to 4510,
        )
        expected.forEach { (wave, amount) ->
            assertEquals(amount, curve.amountAt(wave), "wave $wave")
        }
    }

    @Test
    fun `it ramps within a block of ten, not only between them`() {
        // The term that makes wave 19 worth more than wave 11. A curve that only stepped per block
        // would make nine waves in a row identical and then jump, which is the thing this rules out.
        val withinBlock = (11..19).map { curve.amountAt(it) }
        assertEquals(withinBlock.sorted(), withinBlock)
        assertTrue(withinBlock.last() > withinBlock.first(), "the block did not ramp: $withinBlock")
    }

    @Test
    fun `it is superlinear, so late waves are not merely proportional`() {
        // wave 100 is worth much more than ten times wave 10 — this is what keeps late shop items
        // meaningful rather than pocket change.
        assertTrue(
            curve.amountAt(100) > curve.amountAt(10) * 5,
            "wave 100 (${curve.amountAt(100)}) should dwarf wave 10 (${curve.amountAt(10)})",
        )
    }

    @Test
    fun `every amount is a whole multiple of the rounding step`() {
        (1..200).forEach { wave ->
            assertEquals(0, curve.amountAt(wave) % curve.roundTo, "wave $wave is not rounded")
        }
    }

    @Test
    fun `it never goes backwards across a run`() {
        // A dip would mean a wave that pays less than the one before it while prices kept climbing.
        val amounts = (1..200).map { curve.amountAt(it) }
        assertEquals(amounts.sorted(), amounts)
    }

    @Test
    fun `wave zero and below are clamped rather than inverting the curve`() {
        // A wave index of 0 makes the set index -1 and the exponent less than 1, which quietly turns
        // the curve upside down instead of failing.
        assertEquals(curve.amountAt(1), curve.amountAt(0))
        assertEquals(curve.amountAt(1), curve.amountAt(-50))
    }

    @Test
    fun `a multiplier scales it and zero pays nothing`() {
        assertTrue(curve.amountAt(20, 2.0) > curve.amountAt(20))
        assertEquals(0, curve.amountAt(20, 0.0))
    }

    @Test
    fun `wild waves pay nothing and the hard ones pay in order`() {
        val rules = CreditRules()
        val wave = 25
        assertEquals(0, rules.creditsFor(wave, RunOpponent.WILD))

        val trainer = rules.creditsFor(wave, RunOpponent.TRAINER)
        val rival = rules.creditsFor(wave, RunOpponent.RIVAL)
        val boss = rules.creditsFor(wave, RunOpponent.BOSS)
        assertTrue(trainer > 0, "a trainer wave should pay")
        assertTrue(rival > trainer, "a rival should out-pay a trainer")
        assertTrue(boss > rival, "a boss should out-pay a rival")
    }

    @Test
    fun `pay and prices move together, so the shop cannot drift out of reach`() {
        // The property the shared curve exists for: what a wave pays and what it charges grow at the
        // same rate, so a shop that is affordable at wave 20 is still affordable at wave 120.
        val rules = CreditRules()
        val potionMultiple = 0.2
        listOf(20, 60, 120, 190).forEach { wave ->
            val paid = rules.creditsFor(wave, RunOpponent.TRAINER)
            val potion = rules.curve.amountAt(wave, potionMultiple)
            assertTrue(
                potion < paid,
                "at wave $wave a trainer paid $paid but a potion costs $potion",
            )
        }
    }

    @Test
    fun `the shop is shut on boss waves and open on the rest`() {
        val rules = ShopRules()
        assertTrue(rules.closedAt(10) && rules.closedAt(20) && rules.closedAt(200))
        assertTrue(!rules.closedAt(1) && !rules.closedAt(9) && !rules.closedAt(11))
        // Zero means "never shut", not "shut on every wave" — which is what a server disabling the
        // rule would expect, and the reading the other way round would close the shop permanently.
        assertTrue(!ShopRules(closedEvery = 0).closedAt(10))
    }
}
