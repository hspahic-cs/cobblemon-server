package com.cobblemonroguelite.arena

import java.util.UUID
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

    @Test
    fun `occupancy counts the runs holding a slot and ignores the ones that are not`() {
        // Since §2.23 a null slot is the ordinary resting state of a saved run — every run whose player
        // is offline is in it — so this is the line between "runs on the server" and "arenas in use".
        assertEquals(setOf(0, 2), ArenaSlots.held(listOf(0, null, 2, null, null)))
        assertTrue(ArenaSlots.held(listOf(null, null)).isEmpty())
    }

    @Test
    fun `a logout frees the slot and the next start takes it`() {
        // §2.23's whole point, as the sequence it happens in. Before the lease change these three runs
        // held the grid whether or not anybody was playing them, so a capacity of 3 refused a fourth
        // player while nobody was in an arena at all.
        val slots: MutableMap<String, Int?> = mutableMapOf("a" to 0, "b" to 1, "c" to 2)
        assertNull(ArenaSlots.firstFree(ArenaSlots.held(slots.values), capacity = 3))

        slots["b"] = null // b logs out; the run is still on disk and still theirs.
        assertEquals(1, ArenaSlots.firstFree(ArenaSlots.held(slots.values), capacity = 3))
    }

    @Test
    fun `a returning player is not owed the slot they had`() {
        // The assumption this change makes wrong, pinned so nothing grows to depend on it. b logs out,
        // d takes the free index, and b comes back to a different arena — which is why the reacquire
        // re-stamps and repaints rather than trusting what it thinks is standing there.
        val slots: MutableMap<String, Int?> = mutableMapOf("a" to 0, "b" to 1, "c" to 2)
        slots["b"] = null
        slots["d"] = ArenaSlots.firstFree(ArenaSlots.held(slots.values), capacity = 4)
        assertEquals(1, slots["d"])

        slots["b"] = ArenaSlots.firstFree(ArenaSlots.held(slots.values), capacity = 4)
        assertEquals(3, slots["b"])
    }

    @Test
    fun `a crash leaves nothing leased`() {
        // Not a property of any cleanup path — there is none, and a crash would skip it. It holds
        // because the slot is never persisted: every run reloads holding null, so the grid comes back
        // empty however the process died. See [com.cobblemonroguelite.run.RunState.arenaSlot].
        val beforeTheCrash = listOf(0, 1, 2, 3)
        val afterTheRestart = beforeTheCrash.map { null }
        assertTrue(ArenaSlots.held(afterTheRestart).isEmpty())
        assertEquals(0, ArenaSlots.firstFree(ArenaSlots.held(afterTheRestart), capacity = 4))
    }

    @Test
    fun `only pending starts whose player is online reserve a slot`() {
        // A pending start has no expiry, so counting the offline ones lets somebody who paid and quit
        // before choosing burn a slot for the life of the world — capacity held by an absent player,
        // which is the same defect the lease change removes everywhere else.
        val here = UUID.randomUUID()
        val gone = UUID.randomUUID()
        val online = setOf(here)
        assertEquals(1, ArenaSlots.reserved(listOf(here, gone)) { it in online })
        assertEquals(0, ArenaSlots.reserved(listOf(gone)) { it in online })
        assertEquals(0, ArenaSlots.reserved(emptyList()) { it in online })
    }
}
