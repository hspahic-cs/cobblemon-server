package com.cobblemonroguelite.arena

import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.TicketType
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.levelgen.structure.BoundingBox
import org.slf4j.LoggerFactory
import java.util.Comparator

private val log = LoggerFactory.getLogger("cobblemon_roguelite/arena")

/**
 * Keeping an arena loaded long enough to do something to it.
 *
 * ### This is not a precaution — it was observed failing
 *
 * The arena dimension has no player in it until we put one there, and an empty dimension has no
 * loaded chunks. On dev, a console `setblock` into a fresh arena was refused with *"That position is
 * not loaded"* until the chunk was force-loaded. Anything that stamps a template, sweeps entities, or
 * summons an opponent **before the player arrives** hits that, and the symptom is not an exception:
 * `Level.setBlock` on an unloaded chunk returns false, so a stamp against a cold arena silently
 * places nothing and the player is teleported into void.
 *
 * ### A ticket *and* a blocking get, because they do different jobs
 *
 * [hold] does both. `addRegionTicket` is what keeps the chunks loaded afterwards — for the entity
 * sweep, for the settle window ([ArenaConfig.settleTicks]), for whatever gets summoned next — but it
 * does not load them *now*: chunk loading is asynchronous and the ticket only raises the level that
 * the chunk system will eventually work towards. `ServerLevel.getChunk(x, z)` is the blocking load,
 * and it is what makes the very next `setBlock` legal. Doing only the first leaves the same silent
 * no-op we started with; doing only the second loads the chunks and lets them fall out again on the
 * next unload pass, mid-sweep.
 *
 * The blocking load is cheap *here specifically* and would not be elsewhere: the arena dimension is a
 * void flat generator with no features, no structures and no lakes, so there is nothing to generate.
 * That stops being true for an owner who points [ArenaConfig.fixedArenas] at a real world, where the
 * arena is presumably already built and generated — still finite work, but worth knowing about.
 *
 * ### The ticket expires on its own
 *
 * [ARENA_TICKET] is created with a lifespan, so the chunk system drops it whether or not [release] is
 * ever called. That is deliberate and it is the same argument the design makes for stamping on
 * assignment rather than on release: cleanup that only happens on the happy path is cleanup that a
 * crash turns into a leak, and a leaked chunk ticket is a slice of a dead run's arena ticking forever.
 */
object ArenaChunks {

    /**
     * Distance 2, matching `ServerLevel.setChunkForced`. That resolves to a ticket level of 31 —
     * entity-ticking — which is what an arena needs: block edits alone would be satisfied by a lower
     * level, but a summoned opponent that does not tick is worse than one that does not exist.
     */
    private const val TICKET_DISTANCE = 2

    /**
     * Refuses to force-load an absurd number of chunks. A 64x64 arena is 16 chunks plus its border;
     * this is orders of magnitude above that and exists to turn a config typo in [ArenaBox] into a
     * refusal with a number in it rather than into a server that stops responding while it generates.
     */
    private const val MAX_CHUNKS = 1024

    /**
     * Our own type rather than [TicketType.FORCED]. `FORCED` is persisted in the world's forced-chunk
     * save data and is what `/forceload` manipulates, so using it would (a) survive a restart with
     * nothing left to release it and (b) make `/forceload query` list arenas an operator never asked
     * for, mixed in with the ones they did.
     *
     * The lifespan is the crash backstop described in the class docs.
     */
    private val ARENA_TICKET: TicketType<ChunkPos> = TicketType.create(
        "cobblemon_roguelite_arena",
        Comparator.comparingLong { pos: ChunkPos -> pos.toLong() },
        DEFAULT_HOLD_TICKS,
    )

    /**
     * Thirty seconds at 20 TPS — long enough to cover a stamp, a settle delay and a teleport, short
     * enough that a leak is measured in seconds.
     *
     * Deliberately **not** an [ArenaConfig] field, even though every other number in this area is
     * one. `TicketType` fixes its lifespan at construction and there is one instance per JVM, so a
     * config value here would be read once at class-init and silently ignored ever after — which is a
     * worse thing to ship than a constant.
     */
    const val DEFAULT_HOLD_TICKS = 600

    /**
     * Force-load and block until [box]'s chunks are actually there. False means we refused, in which
     * case the caller must not touch the arena — see the class docs for why a silent no-op is the
     * failure being avoided.
     */
    fun hold(level: ServerLevel, box: BoundingBox): Boolean {
        val chunks = chunksOf(box)
        if (chunks.size > MAX_CHUNKS) {
            log.error(
                "roguelite: arena box {} spans {} chunks, over the {} cap — refusing to force-load it",
                box, chunks.size, MAX_CHUNKS,
            )
            return false
        }
        val source = level.chunkSource
        chunks.forEach { source.addRegionTicket(ARENA_TICKET, it, TICKET_DISTANCE, it) }
        // Second pass, after every ticket is in: the blocking get is what makes the chunks present
        // right now, and interleaving the two would block on the first chunk before the last chunk's
        // ticket exists.
        chunks.forEach { level.getChunk(it.x, it.z) }
        return true
    }

    /**
     * Drop our tickets on [box]. Best-effort: the lifespan on [ARENA_TICKET] releases them anyway, so
     * a caller that never reaches this — a crash, an exception, a run ended while the server was
     * shutting down — leaks nothing that outlives [DEFAULT_HOLD_TICKS].
     */
    fun release(level: ServerLevel, box: BoundingBox) {
        val source = level.chunkSource
        chunksOf(box).forEach { source.removeRegionTicket(ARENA_TICKET, it, TICKET_DISTANCE, it) }
    }

    private fun chunksOf(box: BoundingBox): List<ChunkPos> {
        val min = ChunkPos(box.minX() shr 4, box.minZ() shr 4)
        val max = ChunkPos(box.maxX() shr 4, box.maxZ() shr 4)
        val out = ArrayList<ChunkPos>()
        for (x in min.x..max.x) {
            for (z in min.z..max.z) out.add(ChunkPos(x, z))
        }
        return out
    }
}
