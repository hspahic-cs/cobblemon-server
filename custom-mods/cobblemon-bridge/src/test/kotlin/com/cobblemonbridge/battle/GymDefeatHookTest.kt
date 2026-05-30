package com.cobblemonbridge.battle

import kotlin.test.Test
import kotlin.test.assertEquals

class GymDefeatHookTest {

    // -----------------------------------------------------------------------------------------
    // npcBounty formula: multiplier * trainerLevel * numPokemon / 6  (integer division)
    // multiplier ∈ {1, 2, 3} rolled uniformly per defeat (expected value = 2, matching the
    // pre-randomised constant). Spec'd by the server admin on 2026-05-30 — applies to non-gym
    // trainer defeats only. The pure-math `computeNpcBounty` takes `multiplier` as a param
    // (default 2 for the mid-roll) so the formula is testable deterministically.
    // -----------------------------------------------------------------------------------------

    @Test
    fun `full 6-mon team scales linearly with level`() {
        assertEquals(40, GymDefeatHook.computeNpcBounty(maxLevel = 20, numPokemon = 6))
        assertEquals(80, GymDefeatHook.computeNpcBounty(maxLevel = 40, numPokemon = 6))
        assertEquals(120, GymDefeatHook.computeNpcBounty(maxLevel = 60, numPokemon = 6))
    }

    @Test
    fun `smaller team pays less - proportional to count`() {
        // L60 with 3 pokemon = 2*60*3/6 = 60, half of the 6-mon payout.
        assertEquals(60, GymDefeatHook.computeNpcBounty(maxLevel = 60, numPokemon = 3))
        // L60 with 1 pokemon = 2*60*1/6 = 20
        assertEquals(20, GymDefeatHook.computeNpcBounty(maxLevel = 60, numPokemon = 1))
    }

    @Test
    fun `integer division floors the result`() {
        // 2 * 25 * 1 / 6 = 50 / 6 = 8.33 → 8 after integer-floor.
        assertEquals(8, GymDefeatHook.computeNpcBounty(maxLevel = 25, numPokemon = 1))
        // 2 * 17 * 5 / 6 = 170 / 6 = 28.33 → 28.
        assertEquals(28, GymDefeatHook.computeNpcBounty(maxLevel = 17, numPokemon = 5))
    }

    @Test
    fun `zero or negative inputs return zero`() {
        assertEquals(0, GymDefeatHook.computeNpcBounty(maxLevel = 0, numPokemon = 6))
        assertEquals(0, GymDefeatHook.computeNpcBounty(maxLevel = 50, numPokemon = 0))
        assertEquals(0, GymDefeatHook.computeNpcBounty(maxLevel = -1, numPokemon = 3))
    }

    @Test
    fun `formula is independent of gym bounty - sanity check the two payouts don't overlap`() {
        // Gym bounty for gym 12 is $1,800 (150 * 12). NPC bounty for the same hypothetical
        // L60/6-mon team is $120 at the mid-roll. Confirms NPC bounty is intentionally an
        // order of magnitude under a mid-tier gym payout, even at the lucky high-roll ($180),
        // not a substitute for it.
        assertEquals(150 * 12, GymDefeatHook.gymBounty(gymId = 12, isChallenge = false))
        assertEquals(120, GymDefeatHook.computeNpcBounty(maxLevel = 60, numPokemon = 6))
    }

    @Test
    fun `multiplier param scales the payout linearly`() {
        // L60 / 6-mon team. multiplier rolls in {1, 2, 3} per defeat in npcBounty().
        assertEquals(60, GymDefeatHook.computeNpcBounty(maxLevel = 60, numPokemon = 6, multiplier = 1))
        assertEquals(120, GymDefeatHook.computeNpcBounty(maxLevel = 60, numPokemon = 6, multiplier = 2))
        assertEquals(180, GymDefeatHook.computeNpcBounty(maxLevel = 60, numPokemon = 6, multiplier = 3))
    }

    @Test
    fun `non-positive multiplier returns zero`() {
        assertEquals(0, GymDefeatHook.computeNpcBounty(maxLevel = 60, numPokemon = 6, multiplier = 0))
        assertEquals(0, GymDefeatHook.computeNpcBounty(maxLevel = 60, numPokemon = 6, multiplier = -1))
    }
}
