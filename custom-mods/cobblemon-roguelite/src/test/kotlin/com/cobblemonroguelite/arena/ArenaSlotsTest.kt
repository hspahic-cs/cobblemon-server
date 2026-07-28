package com.cobblemonroguelite.arena

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Allocation, release and — the one that matters — reuse.
 *
 * Reuse is what bounds the arena dimension's region files to the configured concurrency instead of to
 * runs ever played, which is the entire practical advantage the design claims for a slot grid over a
 * dimension per run. A policy that quietly stopped reusing would not fail anything; it would just
 * grow the world folder forever, and nobody would notice for months.
 *
 * There is no allocator object to test because there is no allocator state — see [ArenaSlots]. What
 * is being pinned here is the policy, driven by the occupied set the caller derives from the run
 * store, which is exactly how the real path calls it.
 */
class ArenaSlotsTest {

    @Test
    fun `the first allocation takes slot zero`() {
        assertEquals(0, ArenaSlots.firstFree(emptySet(), capacity = 4))
    }

    @Test
    fun `allocation takes the lowest free index`() {
        assertEquals(1, ArenaSlots.firstFree(setOf(0), capacity = 4))
        assertEquals(2, ArenaSlots.firstFree(setOf(0, 1), capacity = 4))
    }

    @Test
    fun `a released slot is handed straight back out`() {
        // The reuse guarantee, written as the sequence it actually happens in: three runs, the middle
        // one ends, the next start lands in the hole rather than at the end.
        val occupied = mutableSetOf(0, 1, 2)
        occupied.remove(1)
        assertEquals(1, ArenaSlots.firstFree(occupied, capacity = 32))
    }

    @Test
    fun `a full grid allocates nothing`() {
        assertNull(ArenaSlots.firstFree(setOf(0, 1, 2, 3), capacity = 4))
    }

    @Test
    fun `allocation never exceeds capacity`() {
        val occupied = mutableSetOf<Int>()
        repeat(4) { occupied.add(ArenaSlots.firstFree(occupied, capacity = 4)!!) }
        assertEquals(setOf(0, 1, 2, 3), occupied)
        assertNull(ArenaSlots.firstFree(occupied, capacity = 4))
    }

    @Test
    fun `slots held above capacity do not free up phantom room`() {
        // What a lowered maxConcurrentRuns leaves behind: runs still holding indices the grid no
        // longer has. They must still count against it, or the grid hands out a slot it cannot place.
        assertNull(ArenaSlots.firstFree(setOf(0, 1, 5, 9), capacity = 2))
    }

    @Test
    fun `pending starts count against capacity`() {
        // A paid start with no RunState yet still owns a slot in every sense the player cares about.
        // Ignoring them lets a burst of starts all pass the gate and one of them find the grid full
        // after being charged — which is unrefundable by design (§2.16).
        assertTrue(ArenaSlots.hasFreeSlot(occupied = setOf(0), reserved = 0, capacity = 2))
        assertFalse(ArenaSlots.hasFreeSlot(occupied = setOf(0), reserved = 1, capacity = 2))
        assertFalse(ArenaSlots.hasFreeSlot(occupied = emptySet(), reserved = 4, capacity = 4))
    }
}
