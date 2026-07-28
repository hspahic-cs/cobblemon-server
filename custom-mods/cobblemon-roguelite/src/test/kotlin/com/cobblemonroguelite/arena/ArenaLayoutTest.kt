package com.cobblemonroguelite.arena

import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The slot grid, which is the whole of instancing.
 *
 * Every failure here is silent in play and expensive: two runs sharing coordinates means one player's
 * re-stamp deletes the other's arena mid-battle, and an arena within Mega Showdown's power-spot range
 * of its neighbour means a Dynamax that nobody placed a spot for. Neither shows up as an exception,
 * and both need two concurrent runs and a specific wave to reproduce by hand. Arithmetic is exactly
 * the kind of thing a test pins in milliseconds and a play-through misses.
 */
class ArenaLayoutTest {

    private val arena = ArenaConfig.DEFAULT_DIMENSION
    private val elsewhere = ResourceLocation.fromNamespaceAndPath("multiworld", "arena1")

    private fun grid(spacing: Int = 1024, width: Int = 8, capacity: Int = 32, floorY: Int = 64) =
        SlotGrid(arena, spacing, width, capacity, floorY, ArenaBox())

    @Test
    fun `slot maps to x by column and z by row`() {
        val layout = grid(spacing = 1024, width = 8)
        assertEquals(BlockPos(0, 64, 0), layout.placementOf(0)?.origin)
        assertEquals(BlockPos(1024, 64, 0), layout.placementOf(1)?.origin)
        assertEquals(BlockPos(7 * 1024, 64, 0), layout.placementOf(7)?.origin)
        // Row wrap: slot 8 is the start of the second row, not the ninth column.
        assertEquals(BlockPos(0, 64, 1024), layout.placementOf(8)?.origin)
        assertEquals(BlockPos(3 * 1024, 64, 2 * 1024), layout.placementOf(19)?.origin)
    }

    @Test
    fun `every slot in the grid is at least spacing from every other`() {
        val spacing = 1024
        val layout = grid(spacing = spacing, capacity = 32)
        val origins = (0 until 32).map { assertNotNull(layout.placementOf(it)).origin }
        assertEquals(32, origins.toSet().size, "two slots resolved to the same origin")
        for (a in origins.indices) {
            for (b in a + 1 until origins.size) {
                val dx = Math.abs(origins[a].x - origins[b].x)
                val dz = Math.abs(origins[a].z - origins[b].z)
                assertTrue(
                    maxOf(dx, dz) >= spacing,
                    "slots $a and $b are ${maxOf(dx, dz)} apart, inside the ${spacing} spacing",
                )
            }
        }
    }

    @Test
    fun `the default spacing puts every arena far outside a neighbour's power spot range`() {
        // The gimmick confinement argument, as arithmetic: the closest two arena *boxes* can come is
        // spacing minus the footprint, and that gap has to clear the power-spot radius twice over.
        val config = ArenaConfig()
        val gap = config.spacing - maxOf(config.box.width, config.box.depth)
        assertTrue(gap > ArenaConfig.POWER_SPOT_RANGE * 2, "closest arena boxes are only $gap apart")
    }

    @Test
    fun `a slot outside capacity does not resolve`() {
        val layout = grid(capacity = 4)
        assertNull(layout.placementOf(4))
        assertNull(layout.placementOf(-1))
        assertNotNull(layout.placementOf(3))
    }

    @Test
    fun `the box runs from the origin and is inclusive on both corners`() {
        val layout = SlotGrid(arena, 1024, 8, 4, 64, ArenaBox(width = 16, height = 8, depth = 32))
        val box = assertNotNull(layout.placementOf(1)).box
        assertEquals(1024, box.minX())
        assertEquals(1024 + 15, box.maxX())
        assertEquals(64, box.minY())
        assertEquals(64 + 7, box.maxY())
        assertEquals(0, box.minZ())
        assertEquals(31, box.maxZ())
    }

    @Test
    fun `entry is the origin plus the offset`() {
        val placement = assertNotNull(grid().placementOf(2))
        assertEquals(BlockPos(2 * 1024 + 32, 65, 32), placement.entry(BlockPos(32, 1, 32)))
    }

    @Test
    fun `the generated grid owns its whole dimension`() {
        val layout = grid()
        // Not just the boxes. A player thrown into the gap between slots is as stuck as one inside a
        // slot, and nothing else has any business spawning in a dimension only this mod declares.
        assertTrue(layout.isArenaSpace(arena, BlockPos(500, 64, 500)))
        assertTrue(layout.isArenaSpace(arena, BlockPos(-99999, -40, 77777)))
        assertFalse(layout.isArenaSpace(elsewhere, BlockPos(0, 64, 0)))
    }

    @Test
    fun `hand-built arenas own only their boxes`() {
        // The difference that matters for option D: these live in a world with other things in it, so
        // claiming the dimension would blank Cobblemon's spawner for that entire world.
        val layout = FixedArenas(
            defaultDimension = elsewhere,
            origins = listOf(ArenaOrigin(BlockPos(100, 70, 100))),
            box = ArenaBox(width = 16, height = 8, depth = 16),
        )
        assertTrue(layout.isArenaSpace(elsewhere, BlockPos(100, 70, 100)))
        assertTrue(layout.isArenaSpace(elsewhere, BlockPos(115, 77, 115)))
        assertFalse(layout.isArenaSpace(elsewhere, BlockPos(116, 70, 100)))
        assertFalse(layout.isArenaSpace(elsewhere, BlockPos(0, 70, 0)))
    }

    @Test
    fun `a fixed arena may name its own dimension, and otherwise inherits`() {
        val other = ResourceLocation.fromNamespaceAndPath("multiworld", "arena2")
        val layout = FixedArenas(
            defaultDimension = elsewhere,
            origins = listOf(ArenaOrigin(BlockPos(0, 64, 0)), ArenaOrigin(BlockPos(0, 64, 0), other)),
            box = ArenaBox(),
        )
        assertEquals(elsewhere, layout.placementOf(0)?.dimension)
        assertEquals(other, layout.placementOf(1)?.dimension)
        // Same coordinates, different dimensions: they do not collide, and the containment test has
        // to agree or the suppressor and the ejector would disagree about which arena a spot is in.
        assertTrue(layout.isArenaSpace(other, BlockPos(0, 64, 0)))
    }

    @Test
    fun `capacity comes from the list when arenas are hand-built`() {
        val layout = FixedArenas(elsewhere, listOf(ArenaOrigin(BlockPos.ZERO), ArenaOrigin(BlockPos(0, 0, 512))), ArenaBox())
        assertEquals(2, layout.capacity)
        assertNull(layout.placementOf(2))
    }

    @Test
    fun `setting fixed arenas replaces the grid rather than adding to it`() {
        val config = ArenaConfig(fixedArenas = listOf(ArenaOrigin(BlockPos(0, 64, 0))))
        assertTrue(config.layout() is FixedArenas)
        assertEquals(1, config.layout().capacity)
        assertTrue(ArenaConfig().layout() is SlotGrid)
    }
}
