package com.cobblemonroguelite.arena

import java.util.UUID

/**
 * Which slot a new run gets.
 *
 * ### There is no allocator state
 *
 * The occupied set is computed from [com.cobblemonroguelite.run.RunStore] every time it is needed,
 * because the store already holds every active run and every active run already carries its slot.
 * A free-list beside it would be a second copy of the same fact, and the failure mode of a second
 * copy is not "slower" — it is a slot handed to two runs after a crash restores one of them and not
 * the other, which presents as two players standing in each other's arena.
 *
 * That makes allocation O(active runs), which for a `maxConcurrentRuns` an operator is willing to
 * host is a walk over a handful of entries at run start.
 *
 * ### §2.23: a slot is leased for a session, not for a run
 *
 * A run occupies an arena only while its player is online and in it. On logout the lease is dropped
 * ([RunArenas.release]) and re-taken on the way back in, which is what makes `maxConcurrentRuns`
 * bound *concurrent play* rather than concurrent saved runs — the far tighter bound it was throttling
 * before, and one that would refuse new starts on a busy server for no reason.
 *
 * The consequence for everything here is that [firstFree] hands out a slot that has no relationship
 * to the one the same run had last session. Nothing may assume otherwise: the reacquire re-stamps and
 * repaints from scratch, and [com.cobblemonroguelite.run.RunState] deliberately does not write the
 * slot to disk so that a crash cannot restore a lease nobody is holding.
 *
 * ### Lowest free index, not round-robin
 *
 * Reuse is the point (it is what bounds region-file growth to the configured concurrency rather than
 * to runs ever played), so a policy that spreads runs across the grid would defeat it. Lowest-free
 * keeps a server that peaks at three concurrent runs using three arenas forever, which also makes
 * the dimension's region directory something an operator can look at and recognise.
 *
 * ### Threading
 *
 * Called on the server thread only, like the rest of the run lifecycle. Nothing here is atomic and
 * nothing needs to be: two concurrent allocations would both read the same occupied set and both
 * take the same lowest index. If a caller ever needs to allocate off-thread, it hops through
 * `server.execute` first — it does not add a lock here.
 */
object ArenaSlots {

    /** The lowest index in `0 until capacity` that nothing holds, or null when the grid is full. */
    fun firstFree(occupied: Set<Int>, capacity: Int): Int? =
        (0 until capacity).firstOrNull { it !in occupied }

    /**
     * The slots currently leased, from the slot field of every active run.
     *
     * A null in [slots] is a run that holds no arena, which since §2.23 is the *ordinary* state of a
     * saved run: every run whose player is offline is in it, and so is every run that has been created
     * but not yet entered. Named rather than inlined at the one call site because this is the whole of
     * the occupancy model, and the property worth stating in one place is that a run drops out of it by
     * losing a field — no de-registration to forget, and nothing to reconcile after a crash.
     */
    fun held(slots: Iterable<Int?>): Set<Int> = slots.filterNotNullTo(mutableSetOf())

    /**
     * How many slots are spoken for by paid starts that have no [com.cobblemonroguelite.run.RunState]
     * yet — a player looking at a starter offer owns a slot they have not been handed. Reserving them
     * is what stops a burst of starts all passing the capacity gate and then finding the grid full at
     * the moment they pick, which would be a charge taken for a run that cannot be played.
     *
     * **Only the ones whose player is online**, on §2.23's rule. A pending start has no expiry
     * ([com.cobblemonroguelite.run.PendingStart] says why), so counting the offline ones would let a
     * player who paid and logged out before choosing burn a slot for the lifetime of the world — the
     * same "capacity held by somebody who is not here" defect the lease change exists to remove, in the
     * one place that has no run to release.
     *
     * The cost of that is real and is accepted: a player who returns to an offer they paid for can find
     * the grid full at the moment they pick. That path already fails softly — the run is created
     * without an arena, the failure is logged, and the next resume tries again
     * ([com.cobblemonroguelite.run.RunController.chooseStarters]) — whereas a permanently leaked slot
     * has no path back at all.
     */
    fun reserved(pending: Collection<UUID>, isOnline: (UUID) -> Boolean): Int = pending.count(isOnline)

    /** Whether a run could be given an arena right now. */
    fun hasFreeSlot(occupied: Set<Int>, reserved: Int, capacity: Int): Boolean =
        occupied.size + reserved < capacity
}
