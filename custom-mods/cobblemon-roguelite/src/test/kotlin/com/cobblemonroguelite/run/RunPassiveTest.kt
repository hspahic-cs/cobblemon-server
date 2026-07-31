package com.cobblemonroguelite.run

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The passive arithmetic, pinned against PokéRogue's published numbers.
 *
 * These are not balance opinions: 25%/60% per charm stack, 20% per share stack over the participant
 * count, and caps of 99/30/5 are read straight out of `pagefaultgames/pokerogue` (see [RunPassive]'s
 * docs for the exact code). If a test here starts failing after an edit to the enum, the edit changed
 * the semantics §2.43 chose to import — which might be right, but must be a decision, not a typo.
 */
class RunPassiveTest {

    @Test
    fun `no stacks means EXP is untouched`() {
        assertEquals(1.0, RunPassive.expMultiplier(emptyMap()))
        assertEquals(340, RunPassive.boostedExp(340, emptyMap()))
    }

    @Test
    fun `an EXP Charm adds 25 percent per stack`() {
        assertEquals(125, RunPassive.boostedExp(100, mapOf("exp_charm" to 1)))
        assertEquals(175, RunPassive.boostedExp(100, mapOf("exp_charm" to 3)))
        // Floored, PokéRogue's Math.floor: 33 * 1.25 = 41.25 -> 41.
        assertEquals(41, RunPassive.boostedExp(33, mapOf("exp_charm" to 1)))
    }

    @Test
    fun `a Super EXP Charm adds 60 percent per stack and stacks with the plain charm`() {
        assertEquals(160, RunPassive.boostedExp(100, mapOf("super_exp_charm" to 1)))
        // 1 + 0.25*2 + 0.60*1 = 2.10 — both kinds sum into one multiplier.
        assertEquals(210, RunPassive.boostedExp(100, mapOf("exp_charm" to 2, "super_exp_charm" to 1)))
    }

    @Test
    fun `stacks beyond the cap read as the cap, so a hand-edited checkpoint cannot out-earn the reward path`() {
        assertEquals(
            RunPassive.boostedExp(100, mapOf("exp_share" to 5)),
            RunPassive.boostedExp(100, mapOf("exp_share" to 500)),
        )
        assertEquals(
            RunPassive.expMultiplier(mapOf("super_exp_charm" to 30)),
            RunPassive.expMultiplier(mapOf("super_exp_charm" to 999)),
        )
        // Negative counts are damage, not a debuff.
        assertEquals(100, RunPassive.boostedExp(100, mapOf("exp_charm" to -3)))
    }

    @Test
    fun `an unknown passive id contributes nothing rather than throwing`() {
        // The map deliberately survives NBT round-trips with ids this build does not know.
        assertEquals(100, RunPassive.boostedExp(100, mapOf("golden_exp_charm" to 4)))
    }

    @Test
    fun `EXP Share grants 20 percent per stack, split by participant count`() {
        // PokéRogue: expValue * (stacks * 0.2) / participantCount.
        assertEquals(20, RunPassive.sharedExp(100, mapOf("exp_share" to 1), participantCount = 1))
        assertEquals(100, RunPassive.sharedExp(100, mapOf("exp_share" to 5), participantCount = 1))
        assertEquals(30, RunPassive.sharedExp(100, mapOf("exp_share" to 3), participantCount = 2))
        // Floored: 55 * 0.2 = 11, / 2 = 5.5 -> 5.
        assertEquals(5, RunPassive.sharedExp(55, mapOf("exp_share" to 1), participantCount = 2))
    }

    @Test
    fun `EXP Share with no stacks, no earnings, or broken participant counts shares nothing sane-ly`() {
        assertEquals(0, RunPassive.sharedExp(100, emptyMap(), participantCount = 1))
        assertEquals(0, RunPassive.sharedExp(0, mapOf("exp_share" to 5), participantCount = 1))
        // A participant count of zero is Cobblemon reporting something odd — floored to 1, not a
        // divide-by-zero.
        assertEquals(20, RunPassive.sharedExp(100, mapOf("exp_share" to 1), participantCount = 0))
    }

    @Test
    fun `stack grants count up to each kind's cap and then refuse`() {
        assertEquals(1, RunPassive.stackAfterGrant(0, RunPassive.EXP_SHARE))
        assertEquals(5, RunPassive.stackAfterGrant(4, RunPassive.EXP_SHARE))
        assertNull(RunPassive.stackAfterGrant(5, RunPassive.EXP_SHARE), "the 6th EXP Share must refuse")
        assertNull(RunPassive.stackAfterGrant(30, RunPassive.SUPER_EXP_CHARM))
        assertNull(RunPassive.stackAfterGrant(99, RunPassive.EXP_CHARM))
        // Over the cap (a damaged map) still refuses rather than counting further up.
        assertNull(RunPassive.stackAfterGrant(7, RunPassive.EXP_SHARE))
    }

    @Test
    fun `the wire ids resolve and the caps are PokéRogue's`() {
        assertEquals(RunPassive.EXP_CHARM, RunPassive.byId("exp_charm"))
        assertEquals(RunPassive.SUPER_EXP_CHARM, RunPassive.byId("super_exp_charm"))
        assertEquals(RunPassive.EXP_SHARE, RunPassive.byId("exp_share"))
        assertNull(RunPassive.byId("exp_balance"), "EXP_BALANCE is deliberately not implemented")
        assertEquals(99, RunPassive.EXP_CHARM.maxStacks)
        assertEquals(30, RunPassive.SUPER_EXP_CHARM.maxStacks)
        assertEquals(5, RunPassive.EXP_SHARE.maxStacks)
    }
}
