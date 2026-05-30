package com.cobblemonbridge.battle

import kotlin.test.Test
import kotlin.test.assertEquals

class GymDefeatHookTest {

    // -----------------------------------------------------------------------------------------
    // npcBounty formula: 2 * trainerLevel * numPokemon / 6  (integer division)
    // Spec'd by the server admin on 2026-05-30 — applies to non-gym trainer defeats only.
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
        // L60/6-mon team is $120. Confirms NPC bounty is intentionally ~10% of a mid-tier gym
        // payout, not a substitute for it.
        assertEquals(150 * 12, GymDefeatHook.gymBounty(gymId = 12, isChallenge = false))
        assertEquals(120, GymDefeatHook.computeNpcBounty(maxLevel = 60, numPokemon = 6))
    }
}
