package com.cobblemonroguelite.arena

import net.minecraft.world.level.levelgen.structure.BoundingBox
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The arithmetic §2.24's repaint stands on.
 *
 * `FillBiomeCommand.fill` refuses a region larger than `commandModificationBlockLimit` (32768 by
 * default) and quantises both corners **downwards** onto the 4-block biome grid before measuring. The
 * default arena box is four times the limit, so a repaint that ignores either fact does not fail
 * loudly — it either gets refused with the error dropped into an `Either` nobody read, or it paints a
 * box that is one cell short on each far edge. Both present as "the biome feature does not work", on a
 * server, with nothing in the log.
 *
 * Everything below is integer arithmetic over a box, which is exactly the half of the repaint that can
 * be pinned here rather than by flying to an arena and looking at the sky.
 */
class BiomeFillSlicesTest {

    /** The default arena box, at the origin: [com.cobblemonroguelite.arena.ArenaBox] is 64x32x64. */
    private val defaultBox = BoundingBox(0, 64, 0, 63, 95, 63)

    private val vanillaLimit = 32768

    /** What `fill` will measure for a slice: the span between the two quantised corners. */
    private fun measured(box: BoundingBox): Long {
        fun quantize(value: Int) = Math.floorDiv(value, 4) * 4
        val x = (quantize(box.maxX()) - quantize(box.minX())) / 4 + 1
        val y = (quantize(box.maxY()) - quantize(box.minY())) / 4 + 1
        val z = (quantize(box.maxZ()) - quantize(box.minZ())) / 4 + 1
        return ((x - 1) * 4L + 1) * ((y - 1) * 4L + 1) * ((z - 1) * 4L + 1)
    }

    /** Every biome cell the box covers, as the coordinate of the cell's first block. */
    private fun cells(box: BoundingBox): Set<Triple<Int, Int, Int>> {
        fun starts(min: Int, max: Int) = generateSequence(Math.floorDiv(min, 4) * 4) { it + 4 }
            .takeWhile { it <= max }
            .toList()
        val result = mutableSetOf<Triple<Int, Int, Int>>()
        for (x in starts(box.minX(), box.maxX())) {
            for (y in starts(box.minY(), box.maxY())) {
                for (z in starts(box.minZ(), box.maxZ())) result += Triple(x, y, z)
            }
        }
        return result
    }

    @Test
    fun `the default arena box does not fit in one call and is cut until it does`() {
        // The premise of the whole class: if this ever becomes 1, either the box shrank or somebody
        // raised the game rule, and the slicing is no longer load-bearing.
        assertTrue(measured(defaultBox) > vanillaLimit, "the default box would fit — this test is stale")
        val slices = BiomeFillSlices.slice(defaultBox, vanillaLimit)
        assertTrue(slices.size > 1, "expected the default box to be cut, got ${slices.size} slice(s)")
        slices.forEach {
            assertTrue(measured(it) <= vanillaLimit, "slice $it measures ${measured(it)}, over $vanillaLimit")
        }
    }

    @Test
    fun `the slices cover every cell of the box exactly once`() {
        val slices = BiomeFillSlices.slice(defaultBox, vanillaLimit)
        val painted = slices.flatMap { cells(it) }
        // Once, not at least once: a repeated cell is harmless to the result and means the slicing
        // is doing work twice, which at ~130k blocks per arena is not free.
        assertEquals(painted.size, painted.toSet().size, "some cells are painted by more than one slice")
        assertEquals(cells(defaultBox), painted.toSet(), "the slices do not cover the box")
    }

    @Test
    fun `slices start on the biome grid and never run past the box`() {
        BiomeFillSlices.slice(defaultBox, vanillaLimit).forEach { slice ->
            // Unaligned starts are the silent failure: `fill` quantises them downwards, so the slice
            // begins outside itself and repaints a strip of the arena next door.
            assertEquals(0, Math.floorMod(slice.minX(), 4), "slice $slice starts off the biome grid on x")
            assertEquals(0, Math.floorMod(slice.minY(), 4), "slice $slice starts off the biome grid on y")
            assertEquals(0, Math.floorMod(slice.minZ(), 4), "slice $slice starts off the biome grid on z")
            assertTrue(slice.maxX() <= defaultBox.maxX(), "slice $slice runs past the box on x")
            assertTrue(slice.maxY() <= defaultBox.maxY(), "slice $slice runs past the box on y")
            assertTrue(slice.maxZ() <= defaultBox.maxZ(), "slice $slice runs past the box on z")
        }
    }

    @Test
    fun `a box that already fits is one slice, unchanged`() {
        val small = BoundingBox(0, 64, 0, 15, 79, 15)
        assertEquals(listOf(small), BiomeFillSlices.slice(small, vanillaLimit))
    }

    @Test
    fun `an arena at negative coordinates aligns downwards rather than towards zero`() {
        // The grid puts slot 0 at the origin, but fixedArenas (option D) points at builds anywhere,
        // and integer division truncates towards zero — which on the negative side leaves the first
        // cell of the box unpainted, and paints one cell past its far edge.
        val box = BoundingBox(-70, 64, -70, -7, 95, -7)
        val slices = BiomeFillSlices.slice(box, vanillaLimit)
        assertEquals(cells(box), slices.flatMap { cells(it) }.toSet())
        slices.forEach { assertTrue(it.minX() <= -72 || Math.floorMod(it.minX(), 4) == 0) }
    }

    @Test
    fun `an impossible limit returns nothing rather than an unpaintable slice`() {
        // The caller logs and leaves the arena unpainted; a slice it could not honour would be
        // refused at runtime with the failure buried in an Either.
        assertTrue(BiomeFillSlices.slice(defaultBox, 0).isEmpty())
    }
}
