package com.cobblemonroguelite.arena

import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.levelgen.structure.BoundingBox

/**
 * Where slot *n* actually is.
 *
 * @property box inclusive on both corners, the volume the stamp clears and the entity sweep empties.
 */
data class ArenaPlacement(
    val slot: Int,
    val dimension: ResourceLocation,
    val origin: BlockPos,
    val box: BoundingBox,
) {
    /** Where a player stands when they arrive. */
    fun entry(offset: BlockPos): BlockPos = origin.offset(offset)
}

/**
 * Slot index in, coordinates out.
 *
 * ### Why this is a pure function and not a registry
 *
 * A run stores an integer. Everything else about its arena — which dimension, which coordinates,
 * which volume to clear — is derived from that integer on demand. That is the property that makes a
 * server restart mid-run uninteresting: there is no arena state to rebuild, no ordering hazard
 * between our startup and the first login, and nothing that can be out of sync with the run store
 * because there is only one copy of the fact.
 *
 * It is also what makes [ArenaConfig.gridWidth] and [ArenaConfig.spacing] dangerous to change on a
 * server with live runs, and that is the honest trade: the same derivation that costs nothing to
 * persist means an operator who re-indexes the grid moves every in-flight run's arena out from under
 * it. The alternative — writing the coordinates onto the run — would survive a re-index and would
 * instead leave runs pointing at arenas the allocator no longer knows are occupied.
 */
sealed interface ArenaLayout {

    /** How many runs can be in arenas at once. */
    val capacity: Int

    /** Null for a slot outside [capacity] — which is a bug in the allocator, not a runtime condition. */
    fun placementOf(slot: Int): ArenaPlacement?

    /**
     * True when this position belongs to the arena system rather than to the world at large.
     *
     * Two callers with the same question and different consequences: [ArenaSpawnSuppressor] cancels
     * Cobblemon spawns here, and the login hook ejects run-less players from here. Both have to agree,
     * which is why it is one method — a suppressor that covered more ground than the ejector would
     * make dead zones in the shared world, and the reverse would strand players.
     */
    fun isArenaSpace(dimension: ResourceLocation, pos: BlockPos): Boolean
}

/**
 * The generated grid: `x = (n % width) * spacing`, `z = (n / width) * spacing`.
 *
 * [isArenaSpace] answers true for the **whole dimension**, not just the occupied boxes. The arena
 * dimension is ours entirely — nothing else has any business generating, spawning or standing in it —
 * so covering the gaps between slots costs nothing and closes the case of a player who logs in at
 * (0, 64, 5000) because they were thrown there by something, which a box-only check would leave
 * stranded forever.
 */
class SlotGrid(
    private val dimension: ResourceLocation,
    private val spacing: Int,
    private val width: Int,
    override val capacity: Int,
    private val floorY: Int,
    private val box: ArenaBox,
) : ArenaLayout {

    override fun placementOf(slot: Int): ArenaPlacement? {
        if (slot < 0 || slot >= capacity) return null
        val origin = BlockPos((slot % width) * spacing, floorY, (slot / width) * spacing)
        return ArenaPlacement(slot, dimension, origin, boxAt(origin, box))
    }

    override fun isArenaSpace(dimension: ResourceLocation, pos: BlockPos): Boolean = dimension == this.dimension
}

/**
 * Option D: arenas an owner built, listed in config.
 *
 * [isArenaSpace] is a **box** test here and not a dimension test, which is the whole difference
 * between the two layouts. Hand-built arenas usually sit in a world that has other things in it —
 * ours would be `multiworld:` rooms shared with everything else there — so suppressing Cobblemon
 * spawns across the dimension would blank the spawner for that entire world, and ejecting every
 * run-less player in it would teleport bystanders.
 */
class FixedArenas(
    private val defaultDimension: ResourceLocation,
    private val origins: List<ArenaOrigin>,
    private val box: ArenaBox,
) : ArenaLayout {

    override val capacity: Int get() = origins.size

    override fun placementOf(slot: Int): ArenaPlacement? {
        val arena = origins.getOrNull(slot) ?: return null
        return ArenaPlacement(
            slot = slot,
            dimension = arena.dimension ?: defaultDimension,
            origin = arena.origin,
            box = boxAt(arena.origin, box),
        )
    }

    override fun isArenaSpace(dimension: ResourceLocation, pos: BlockPos): Boolean =
        origins.indices.any { slot ->
            val placement = placementOf(slot) ?: return@any false
            placement.dimension == dimension && placement.box.isInside(pos)
        }
}

private fun boxAt(origin: BlockPos, box: ArenaBox): BoundingBox = BoundingBox(
    origin.x, origin.y, origin.z,
    origin.x + box.width - 1, origin.y + box.height - 1, origin.z + box.depth - 1,
)
