package com.cobblemonroguelite.run

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * §2.13's branches, which are the only ones in the catch path a test can reach.
 *
 * A `Pokemon` cannot be built outside a booted game — Cobblemon's entity classes fail to link in a
 * plain JUnit run — so nothing here touches a party member, and the routing that moves them is
 * verified on a dev VM instead. What *is* here is the part where being wrong destroys something: a
 * slot number that names the wrong Pokémon, a full party that reads as having room, a second catch
 * that overwrites the one a player is being asked about.
 */
class RunCatchRulesTest {

    @Test
    fun `a party with room takes the catch without asking`() {
        for (size in 0 until RunState.MAX_PARTY) {
            assertEquals(RunCatchRules.Route.JOIN, RunCatchRules.route(size, holdingCatch = false), "size=$size")
        }
    }

    @Test
    fun `a full party holds the catch for a decision`() {
        assertEquals(RunCatchRules.Route.HOLD, RunCatchRules.route(RunState.MAX_PARTY, holdingCatch = false))
    }

    @Test
    fun `a second catch never displaces the one being decided about`() {
        // The prompt names the held Pokémon. If a later catch could take its place, the player would
        // be confirming the destruction of something they were never shown — and both are gone
        // either way, so the only difference the alternative buys is the lie.
        for (size in 0..RunState.MAX_PARTY) {
            assertEquals(RunCatchRules.Route.REFUSE, RunCatchRules.route(size, holdingCatch = true), "size=$size")
        }
    }

    @Test
    fun `a held catch is claimed only once a slot has actually opened`() {
        assertTrue(RunCatchRules.claims(RunState.MAX_PARTY - 1, holdingCatch = true))
        assertFalse(RunCatchRules.claims(RunState.MAX_PARTY, holdingCatch = true))
    }

    @Test
    fun `nothing is claimed when nothing is held`() {
        assertFalse(RunCatchRules.claims(0, holdingCatch = false))
    }

    @Test
    fun `slot numbers are the ones the prompt printed`() {
        // The prompt numbers from 1; the party is indexed from 0. Getting this off by one destroys a
        // neighbour of the Pokémon the player named.
        assertEquals(0, RunCatchRules.swapIndex(slot = 1, partySize = 6))
        assertEquals(5, RunCatchRules.swapIndex(slot = 6, partySize = 6))
    }

    @Test
    fun `a slot past the end of a thinned party is refused, not rounded`() {
        // Permadeath makes short parties ordinary. Clamping "swap 6" to the last member of a party of
        // four would destroy a Pokémon the player did not name, in the one command where that is
        // unrecoverable.
        assertNull(RunCatchRules.swapIndex(slot = 6, partySize = 4))
        assertNull(RunCatchRules.swapIndex(slot = 5, partySize = 4))
        assertEquals(3, RunCatchRules.swapIndex(slot = 4, partySize = 4))
    }

    @Test
    fun `a slot below the first is refused`() {
        assertNull(RunCatchRules.swapIndex(slot = 0, partySize = 6))
        assertNull(RunCatchRules.swapIndex(slot = -1, partySize = 6))
    }

    @Test
    fun `no slot at all is offered on an empty party`() {
        assertNull(RunCatchRules.swapIndex(slot = 1, partySize = 0))
    }
}
