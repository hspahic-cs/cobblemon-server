package com.cobblemonroguelite.arena

import com.cobblemonroguelite.data.arena.ArenaPalette
import com.cobblemonroguelite.data.arena.ArenaPalettes
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.levelgen.structure.BoundingBox

/** A palette that will not fit the arena box in force, or the plan that will. */
sealed interface ArenaPlanResult {

    data class Planned(val plan: ArenaPlan) : ArenaPlanResult

    /**
     * [detail] names both numbers. The owner reading it has a palette file and an arena config open
     * in different directories, and "does not fit" without them tells them which pair to compare and
     * nothing else.
     */
    data class DoesNotFit(val palette: ResourceLocation, val detail: String) : ArenaPlanResult
}

/**
 * Exactly which block goes in exactly which cell — decided with no world, no registries and no
 * randomness.
 *
 * ### Why the plan is a value and not a series of `setBlock` calls
 *
 * Two reasons, and the second is the one that matters.
 *
 * A plan is a **pure function of (palette, box)**, so it is the whole of arena generation that can be
 * pinned in a plain JUnit run. Block states, chunk sections and levels cannot be constructed without
 * a booted server; a map from [BlockPos] to a block id can.
 *
 * And it makes idempotence a property of the *data* rather than of the code that walks it. §2.19
 * re-stamps at every band boundary and §2.23 re-stamps on every session resume, so generation runs
 * many times over one run's life and has to produce the same arena each time. Because this contains
 * no `random`, no clock and no read of the world, two calls with equal inputs produce equal maps —
 * which is a thing a test can assert. [ArenaGenerator] then only has to make the world match the
 * plan, and "make the world match a value" is idempotent for free.
 *
 * ### The floor is one layer, and that is forced rather than chosen
 *
 * [ArenaConfig.entryOffset] defaults to one block above the slot origin, which is the box's minimum
 * corner. So the surface the player lands on is `box.minY`, the floor block occupies exactly that
 * layer, and anything under it would be at `box.minY - 1` — outside the declared box, which is the
 * volume the sweep empties, the debris pass clears and the repaint covers. A two-layer floor would
 * therefore be a layer nothing ever cleans up, accumulating across band transitions. Nobody can see
 * the underside of a platform in a void dimension anyway.
 *
 * ### Where the power spot goes, and why not the obvious place
 *
 * §2.5's gimmick confinement is entirely "a `power_spot` exists inside the arena and nowhere else",
 * so this places one. Not *in* the floor layer: if a future version of that block stops being a full
 * cube, a power spot substituted into the floor is a hole into the void directly under the player.
 * Not at the platform centre either — that is where [ArenaConfig.entryOffset] puts them, and arriving
 * inside a block is a shove at best. So: standing on the floor, one block along +x from the centre.
 * Adjacent to the player, which is comfortably inside Mega Showdown's `powerSpotRange` of 20 whatever
 * the platform size.
 */
class ArenaPlan internal constructor(
    val palette: ResourceLocation,

    /**
     * The structural blocks. Missing any of these is fatal to the stamp — see [ArenaGenerator].
     *
     * A [LinkedHashMap] rather than a `Map`, so iteration order is the order the plan was built in
     * and a log line about the *n*th placement means the same thing twice. Positions are unique, so
     * the order changes nothing about the result; it changes what a debug session sees.
     */
    val blocks: Map<BlockPos, ResourceLocation>,

    /** Where §2.5's `power_spot` goes, or null when the palette opted out. */
    val powerSpot: BlockPos?,
) {

    /**
     * Every cell this plan owns.
     *
     * The debris pass consults it for the one question it has: is this existing block something the
     * new arena wants, or is it left over from the last one. Built once because that pass asks it per
     * non-air block in the box.
     */
    val claimed: Set<BlockPos> = if (powerSpot == null) blocks.keys else blocks.keys + powerSpot

    /**
     * Whether this plan wants the cell at [pos].
     *
     * Takes a [BlockPos] and is called with the debris pass's shared mutable cursor, which works
     * because `BlockPos` hashes and compares on its three coordinates and `MutableBlockPos` is one.
     */
    fun claims(pos: BlockPos): Boolean = claimed.contains(pos)

    /** The distinct block ids to resolve, which is a handful even for a large platform. */
    val blockIds: Set<ResourceLocation> get() = blocks.values.toSet()

    companion object {

        /**
         * Mega Showdown's power spot, as a string.
         *
         * Not a compile dependency and it cannot be one: Mega Showdown is a **soft** dependency
         * (§2.5), so the mode has to build and run without it. A missing block here degrades the
         * gimmick ladder to off and is *not* fatal — see [ArenaGenerator] for why that is the one
         * block whose absence does not refuse the arena.
         */
        const val POWER_SPOT_BLOCK = "mega_showdown:power_spot"

        /** One block along +x from the platform centre. See the class docs for why it is not zero. */
        private const val POWER_SPOT_OFFSET_X = 1

        /**
         * Plan [palette] into [box], or say why it will not fit.
         *
         * Every position produced is inside [box]. That is not politeness either: the box is what the
         * entity sweep empties, what the debris pass clears and what §2.24 repaints, so a block
         * outside it is a block nothing will ever tidy up or recolour — the same overspill
         * [ArenaStamper] can only *warn* about for a hand-built template, and which generation is in
         * a position to simply refuse.
         */
        fun of(palette: ArenaPalette, box: BoundingBox): ArenaPlanResult {
            val width = palette.width ?: box.xSpan
            val depth = palette.depth ?: box.zSpan

            // A box smaller than the minimum footprint is the reachable version of this: a palette
            // with no explicit size is legal everywhere until somebody shrinks the arena box.
            if (width < ArenaPalettes.MIN_FOOTPRINT || depth < ArenaPalettes.MIN_FOOTPRINT) {
                return doesNotFit(
                    palette,
                    "platform is ${width}x$depth, below the ${ArenaPalettes.MIN_FOOTPRINT}x${ArenaPalettes.MIN_FOOTPRINT} minimum" +
                        if (palette.width == null || palette.depth == null) " (taken from the arena box, which is ${box.xSpan}x${box.zSpan})" else "",
                )
            }
            if (width > box.xSpan || depth > box.zSpan) {
                return doesNotFit(palette, "platform is ${width}x$depth but the arena box is only ${box.xSpan}x${box.zSpan}")
            }

            // Height above the floor layer that the tallest thing in this palette needs. The power
            // spot counts for one: a box exactly one block tall has a floor and nowhere to put it.
            val needed = maxOf(
                palette.rim?.height ?: 0,
                palette.pillars?.height ?: 0,
                if (palette.powerSpot) 1 else 0,
            )
            if (1 + needed > box.ySpan) {
                return doesNotFit(palette, "needs ${1 + needed} blocks of height but the arena box is only ${box.ySpan} tall")
            }

            val pillars = palette.pillars
            if (pillars != null && pillars.inset * 2 + 1 > minOf(width, depth)) {
                return doesNotFit(
                    palette,
                    "pillars are inset ${pillars.inset} on a ${width}x$depth platform, which puts them past each other",
                )
            }

            // Centred, by integer division. Deterministic — which is the whole requirement — and it
            // biases to the low corner by half a block on an odd remainder, which nobody can see.
            val x0 = box.minX() + (box.xSpan - width) / 2
            val z0 = box.minZ() + (box.zSpan - depth) / 2
            val floorY = box.minY()

            val blocks = LinkedHashMap<BlockPos, ResourceLocation>()
            for (x in x0 until x0 + width) {
                for (z in z0 until z0 + depth) {
                    blocks[BlockPos(x, floorY, z)] = palette.floor
                }
            }

            palette.rim?.let { rim ->
                for (level in 1..rim.height) {
                    val y = floorY + level
                    for (x in x0 until x0 + width) {
                        for (z in z0 until z0 + depth) {
                            val onEdge = x == x0 || x == x0 + width - 1 || z == z0 || z == z0 + depth - 1
                            if (onEdge) blocks[BlockPos(x, y, z)] = rim.block
                        }
                    }
                }
            }

            // After the rim, so a pillar at inset 0 wins its cell rather than depending on which loop
            // ran last. Order between two writers of one cell is exactly the kind of thing that is
            // stable until somebody reorders the code, so it is stated here rather than left to luck.
            pillars?.let { spec ->
                val xs = listOf(x0 + spec.inset, x0 + width - 1 - spec.inset)
                val zs = listOf(z0 + spec.inset, z0 + depth - 1 - spec.inset)
                for (x in xs.distinct()) {
                    for (z in zs.distinct()) {
                        for (level in 1..spec.height) blocks[BlockPos(x, floorY + level, z)] = spec.block
                    }
                }
            }

            val powerSpot = if (!palette.powerSpot) {
                null
            } else {
                BlockPos(x0 + width / 2 + POWER_SPOT_OFFSET_X, floorY + 1, z0 + depth / 2)
            }
            // The power spot wins its cell outright. On a platform narrow enough for the rim to reach
            // the centre they collide, and a rim block there would mean no power spot at all — i.e.
            // §2.5's confinement silently not working, which is the failure this whole placement is
            // for.
            powerSpot?.let { blocks.remove(it) }

            return ArenaPlanResult.Planned(ArenaPlan(palette.id, blocks, powerSpot))
        }

        private fun doesNotFit(palette: ArenaPalette, detail: String) =
            ArenaPlanResult.DoesNotFit(palette.id, detail)
    }
}
