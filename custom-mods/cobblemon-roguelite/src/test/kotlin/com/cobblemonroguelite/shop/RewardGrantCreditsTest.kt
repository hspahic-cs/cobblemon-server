package com.cobblemonroguelite.shop

import com.cobblemonroguelite.data.reward.RunReward
import com.cobblemonroguelite.run.RunState
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The one grant in [RewardGrant] that is testable without a booted game, because it touches no
 * Cobblemon type: a credits reward writes to [RunState.credits] and nothing else.
 *
 * What is pinned here is the economy invariant, not arithmetic: the amount must come from the SAME
 * [WaveMoneyCurve] the shop prices from ([ShopSettings.credits.curve]), at the run's CURRENT wave.
 * A Nugget that resolved through anything else would drift against the shop the way the old flat
 * credit ramp did — the failure [WaveMoneyCurve]'s docs exist to prevent.
 */
class RewardGrantCreditsTest {

    @AfterTest
    fun reset() = ShopSettings.reset()

    @Test
    fun `a credits grant pays the shared wave curve times the multiplier, at the current wave`() {
        val run = RunState(wave = 11, seed = 1L, credits = 100)
        val expected = ShopSettings.credits.curve.amountAt(11, 2.5)
        assertTrue(expected > 0, "wave 11 must be worth something or this test checks nothing")

        val result = RewardGrant.grantCredits(RunReward.Credits(2.5), run)

        val ok = assertIs<GrantResult.Ok>(result)
        assertEquals(100 + expected, run.credits)
        // The message names the amount — the task of GrantResult.Ok is to be shown to the player.
        assertTrue(RunCurrency.format(expected) in ok.message, ok.message)
    }

    @Test
    fun `deeper waves pay more for the same multiplier, because the curve is the amount`() {
        val early = RunState(wave = 5, seed = 1L)
        val late = RunState(wave = 105, seed = 1L)
        RewardGrant.grantCredits(RunReward.Credits(1.0), early)
        RewardGrant.grantCredits(RunReward.Credits(1.0), late)
        assertTrue(
            late.credits > early.credits,
            "wave 105 paid ${late.credits}, wave 5 paid ${early.credits} — the curve is not being read",
        )
    }

    @Test
    fun `a curve that pays nothing is NoEffect, not a failure`() {
        // Only reachable with a misconfigured curve; nothing broke and nothing was spent.
        ShopSettings.credits = CreditRules(curve = WaveMoneyCurve(base = 1, roundTo = 1_000_000))
        val run = RunState(wave = 1, seed = 1L, credits = 40)
        val result = RewardGrant.grantCredits(RunReward.Credits(1.0), run)
        assertIs<GrantResult.NoEffect>(result)
        assertEquals(40, run.credits, "a grant that paid nothing must not touch the balance")
    }

    @Test
    fun `the balance saturates instead of overflowing`() {
        val run = RunState(wave = 199, seed = 1L, credits = Int.MAX_VALUE - 1)
        assertIs<GrantResult.Ok>(RewardGrant.grantCredits(RunReward.Credits(10.0), run))
        assertEquals(Int.MAX_VALUE, run.credits)
    }
}
