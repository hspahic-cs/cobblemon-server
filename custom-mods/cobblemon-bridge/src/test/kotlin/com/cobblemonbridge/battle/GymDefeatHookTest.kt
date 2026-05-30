package com.cobblemonbridge.battle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GymDefeatHookTest {

    // -----------------------------------------------------------------------------------------
    // npcBounty formula: max(1, ⌈multiplier * maxLevel * numPokemon / 6⌉)
    // multiplier ∈ {1, 2, 3} rolled uniformly per defeat (expected value = 2). Ceiling division
    // + min-of-1 floor ensure that any non-trivial defeat pays at least $1 — fixes the silent-
    // zero case (e.g. L5 / 1 mon / multiplier 1) that 0.7.24's flooring formula produced.
    // The pure-math `computeNpcBounty` takes `multiplier` as a param (default 2 for the mid-
    // roll) so the formula is testable deterministically.
    //
    // Gym bounty is NOT tested here — since 0.7.26 it's paid by `eco give @s <amount>` inside
    // each beat_gym_*.mcfunction, not by Kotlin. The hardcoded gym-bounty sanity check below
    // mirrors that mcfunction value to keep the NPC payout from drifting close to it.
    // -----------------------------------------------------------------------------------------

    @Test
    fun `full 6-mon team divides evenly - matches the integer-floor cases unchanged`() {
        // Numerator divisible by 6: ceiling and floor agree, results unchanged from 0.7.24.
        assertEquals(40, GymDefeatHook.computeNpcBounty(maxLevel = 20, numPokemon = 6))
        assertEquals(80, GymDefeatHook.computeNpcBounty(maxLevel = 40, numPokemon = 6))
        assertEquals(120, GymDefeatHook.computeNpcBounty(maxLevel = 60, numPokemon = 6))
    }

    @Test
    fun `smaller team pays less - proportional to count`() {
        assertEquals(60, GymDefeatHook.computeNpcBounty(maxLevel = 60, numPokemon = 3))
        assertEquals(20, GymDefeatHook.computeNpcBounty(maxLevel = 60, numPokemon = 1))
    }

    @Test
    fun `non-divisible numerator rounds up`() {
        // 2 * 25 * 1 = 50; 50/6 = 8.33; ceiling = 9. (Was 8 under the floor formula.)
        assertEquals(9, GymDefeatHook.computeNpcBounty(maxLevel = 25, numPokemon = 1))
        // 2 * 17 * 5 = 170; 170/6 = 28.33; ceiling = 29.
        assertEquals(29, GymDefeatHook.computeNpcBounty(maxLevel = 17, numPokemon = 5))
    }

    @Test
    fun `low-level low-team rolls never silently floor to zero`() {
        // The case that broke in 0.7.24: tiny trainers + multiplier-1 used to floor to 0 and
        // skip both chat and deposit. Now floored at 1.
        assertEquals(1, GymDefeatHook.computeNpcBounty(maxLevel = 5, numPokemon = 1, multiplier = 1))  // 5/6 → 1
        assertEquals(1, GymDefeatHook.computeNpcBounty(maxLevel = 3, numPokemon = 1, multiplier = 1))  // 3/6 → 1
        assertEquals(1, GymDefeatHook.computeNpcBounty(maxLevel = 1, numPokemon = 1, multiplier = 1))  // 1/6 → 1
        // Sanity: any positive triple stays ≥ 1.
        for (lvl in 1..10) for (n in 1..6) for (m in 1..3) {
            assertTrue(
                GymDefeatHook.computeNpcBounty(lvl, n, m) >= 1,
                "expected ≥1 for L$lvl/$n mons/×$m",
            )
        }
    }

    @Test
    fun `zero or negative inputs return zero`() {
        assertEquals(0, GymDefeatHook.computeNpcBounty(maxLevel = 0, numPokemon = 6))
        assertEquals(0, GymDefeatHook.computeNpcBounty(maxLevel = 50, numPokemon = 0))
        assertEquals(0, GymDefeatHook.computeNpcBounty(maxLevel = -1, numPokemon = 3))
        assertEquals(0, GymDefeatHook.computeNpcBounty(maxLevel = 60, numPokemon = 6, multiplier = 0))
        assertEquals(0, GymDefeatHook.computeNpcBounty(maxLevel = 60, numPokemon = 6, multiplier = -1))
    }

    @Test
    fun `multiplier param scales the payout linearly`() {
        // L60 / 6-mon team. multiplier rolls in {1, 2, 3} per defeat in npcBounty().
        assertEquals(60, GymDefeatHook.computeNpcBounty(maxLevel = 60, numPokemon = 6, multiplier = 1))
        assertEquals(120, GymDefeatHook.computeNpcBounty(maxLevel = 60, numPokemon = 6, multiplier = 2))
        assertEquals(180, GymDefeatHook.computeNpcBounty(maxLevel = 60, numPokemon = 6, multiplier = 3))
    }

    @Test
    fun `npc bounty stays an order of magnitude under hardcoded gym bounty`() {
        // 0.7.26 — `GymDefeatHook.gymBounty` was removed; gym bounty payment moved into
        // each beat_gym_*.mcfunction as `/eco give @s 150 * gymId`. The math is identical
        // to the prior Kotlin path. This sanity check hardcodes the gym 12 expected payout
        // ($1,800) and asserts the L60/6-mon NPC mid-roll ($120) stays an order of magnitude
        // smaller — so NPC bounty doesn't compete with gym bounty as an income source.
        val gym12Bounty = 150 * 12  // mirrors the eco give in beat_gym_12.mcfunction
        assertEquals(1800, gym12Bounty)
        assertEquals(120, GymDefeatHook.computeNpcBounty(maxLevel = 60, numPokemon = 6))
        assertTrue(GymDefeatHook.computeNpcBounty(maxLevel = 60, numPokemon = 6, multiplier = 3) < gym12Bounty)
    }
}
