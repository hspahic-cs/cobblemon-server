package com.cobblemonroguelite.arena

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
     * Whether a run could be given an arena right now.
     *
     * [reserved] counts paid starts that have no [com.cobblemonroguelite.run.RunState] yet — a player
     * looking at a starter offer owns a slot they have not been handed. Counting them is what stops a
     * burst of starts all passing the capacity gate and then finding the grid full at the moment they
     * pick, which would be a charge taken for a run that cannot be played.
     */
    fun hasFreeSlot(occupied: Set<Int>, reserved: Int, capacity: Int): Boolean =
        occupied.size + reserved < capacity
}
