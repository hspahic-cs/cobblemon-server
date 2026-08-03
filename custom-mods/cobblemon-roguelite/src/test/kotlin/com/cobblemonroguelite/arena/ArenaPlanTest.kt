package com.cobblemonroguelite.arena

import com.cobblemonroguelite.data.arena.ArenaPalette
import com.cobblemonroguelite.data.arena.ArenaPillars
import com.cobblemonroguelite.data.arena.ArenaRim
import com.cobblemonroguelite.data.arena.ArenaShape
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.levelgen.structure.BoundingBox
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The whole of §2.29's arena generation that can be pinned without a server.
 *
 * Block states, chunk sections and levels cannot be constructed in a plain JUnit run, so what is
 * under test here is the *decision* — which cell gets which block — and the properties that decision
 * has to have. Actual placement, the section-pruned clear and the power spot's behaviour in the world
 * need the dev VM; see [ArenaGenerator].
 *
 * Three of these are not "does the maths work" but "does the arena still exist":
 *
 * - **containment**, because a block outside the declared box is a block that nothing ever clears,
 *   sweeps or repaints, and it accumulates over a 200-wave run's twenty band transitions;
 * - **idempotence**, because §2.19 re-stamps at band boundaries and §2.23 re-stamps on every session
 *   resume, so a plan that drifted between two calls would rebuild an arena around a standing player;
 * - **the power spot**, because §2.5's entire Tera/Dynamax confinement is that block existing inside
 *   an arena and nowhere else, and its absence is invisible until somebody tries to Dynamax.
 */
class ArenaPlanTest {

    private val floor = ResourceLocation.withDefaultNamespace("grass_block")
    private val rimBlock = ResourceLocation.withDefaultNamespace("stone_bricks")
    private val pillarBlock = ResourceLocation.withDefaultNamespace("oak_log")

    /** The default arena box at the origin: [ArenaBox] is 64x32x64 and [ArenaConfig.floorY] is 64. */
    private val defaultBox = BoundingBox(0, 64, 0, 63, 95, 63)

    private fun palette(
        width: Int? = null,
        depth: Int? = null,
        rim: ArenaRim? = null,
        pillars: ArenaPillars? = null,
        powerSpot: Boolean = true,
        shape: ArenaShape = ArenaShape.SQUARE,
    ) = ArenaPalette(
        id = ResourceLocation.fromNamespaceAndPath("test", "example"),
        floor = floor,
        shape = shape,
        width = width,
        depth = depth,
        rim = rim,
        pillars = pillars,
        powerSpot = powerSpot,
    )

    private fun plan(palette: ArenaPalette, box: BoundingBox = defaultBox): ArenaPlan =
        assertIs<ArenaPlanResult.Planned>(ArenaPlan.of(palette, box), "expected a plan").plan

    private fun refusal(palette: ArenaPalette, box: BoundingBox = defaultBox): String =
        assertIs<ArenaPlanResult.DoesNotFit>(ArenaPlan.of(palette, box), "expected a refusal").detail

    @Test
    fun `a bare palette floors the whole box footprint, one layer deep`() {
        val plan = plan(palette())

        assertEquals(64 * 64, plan.blocks.size, "the floor is the whole box footprint")
        assertTrue(plan.blocks.values.all { it == floor })
        // One layer, at the very bottom, because [ArenaConfig.entryOffset] puts the player one block
        // above the box's minimum corner. A second layer would have to sit outside the box.
        assertEquals(setOf(64), plan.blocks.keys.map { it.y }.toSet())
    }

    @Test
    fun `an explicit platform is centred in the box`() {
        val plan = plan(palette(width = 40, depth = 20))
        val xs = plan.blocks.keys.map { it.x }
        val zs = plan.blocks.keys.map { it.z }
        assertEquals(12, xs.min(), "(64 - 40) / 2")
        assertEquals(51, xs.max())
        assertEquals(22, zs.min(), "(64 - 20) / 2")
        assertEquals(41, zs.max())
    }

    @Test
    fun `every block a plan places is inside the declared box`() {
        // The property, over a spread of shapes rather than one. Anything outside the box is outside
        // what the entity sweep empties, what the next stamp clears and what section 2.24 repaints,
        // so it survives every band transition for the rest of the run.
        val shapes = listOf(
            palette(),
            palette(width = 3, depth = 3),
            palette(width = 63, depth = 63),
            palette(rim = ArenaRim(rimBlock, height = 31)),
            palette(width = 5, depth = 5, pillars = ArenaPillars(pillarBlock, height = 31, inset = 2)),
        )
        for (shape in shapes) {
            val plan = plan(shape)
            for (pos in plan.claimed) {
                assertTrue(
                    defaultBox.isInside(pos),
                    "palette ${shape.width}x${shape.depth} put $pos outside the arena box",
                )
            }
        }
    }

    @Test
    fun `planning twice produces the same arena`() {
        // §2.19 re-stamps at every band boundary and §2.23 on every session resume, so this runs many
        // times over one run. Nothing in the plan reads a clock, a random or the world, which is what
        // makes that safe — and this is the assertion that keeps it true.
        val palette = palette(width = 21, depth = 17, rim = ArenaRim(rimBlock, 3), pillars = ArenaPillars(pillarBlock, 6, 2))
        val first = plan(palette)
        val second = plan(palette)
        assertEquals(first.blocks, second.blocks)
        assertEquals(first.powerSpot, second.powerSpot)
        assertEquals(first.blocks.keys.toList(), second.blocks.keys.toList(), "iteration order too, for stable logs")
    }

    @Test
    fun `a rim rings the platform edge and leaves the middle open`() {
        val plan = plan(palette(width = 5, depth = 5, rim = ArenaRim(rimBlock, height = 2), powerSpot = false))
        val rim = plan.blocks.filterValues { it == rimBlock }
        // Two layers of a 5x5 ring: 16 blocks each.
        assertEquals(32, rim.size)
        assertEquals(setOf(65, 66), rim.keys.map { it.y }.toSet())
        val x0 = (64 - 5) / 2
        val z0 = (64 - 5) / 2
        assertTrue(rim.containsKey(BlockPos(x0, 65, z0)), "the corner is rim")
        assertFalse(rim.containsKey(BlockPos(x0 + 2, 65, z0 + 2)), "the middle is where the fight happens")
    }

    @Test
    fun `pillars stand at the inset corners and win their cells from the rim`() {
        val plan = plan(
            palette(
                width = 9,
                depth = 9,
                rim = ArenaRim(rimBlock, height = 4),
                pillars = ArenaPillars(pillarBlock, height = 4, inset = 0),
                powerSpot = false,
            ),
        )
        val x0 = (64 - 9) / 2
        val z0 = (64 - 9) / 2
        // Inset 0 puts a pillar in the rim. Legal, and the pillar wins — stated in [ArenaPlan] rather
        // than left to whichever loop happens to run last.
        assertEquals(pillarBlock, plan.blocks[BlockPos(x0, 65, z0)])
        assertEquals(pillarBlock, plan.blocks[BlockPos(x0 + 8, 68, z0 + 8)])
        assertEquals(rimBlock, plan.blocks[BlockPos(x0 + 4, 65, z0)], "the rest of the rim is untouched")
    }

    @Test
    fun `the power spot is placed on the floor, next to where the player lands`() {
        // Section 2.5's confinement is entirely "this block exists inside an arena and nowhere else".
        // Not IN the floor (a non-full replacement would be a hole into the void under the player) and
        // not at the centre (that is where the entry offset puts them).
        val plan = plan(palette())
        val spot = assertNotNull(plan.powerSpot)
        val entry = ArenaConfig().entryOffset

        assertEquals(65, spot.y, "standing on the floor, not in it")
        assertEquals(entry.x + 1, spot.x)
        assertEquals(entry.z, spot.z)
        assertFalse(plan.blocks.containsKey(spot), "nothing else may claim the power spot's cell")
        assertTrue(plan.claims(spot))

        // Adjacent to the player, so it is inside Mega Showdown's powerSpotRange whatever the size.
        val distance = kotlin.math.sqrt(
            ((spot.x - entry.x) * (spot.x - entry.x) + (spot.z - entry.z) * (spot.z - entry.z)).toDouble(),
        )
        assertTrue(distance < ArenaConfig.POWER_SPOT_RANGE, "power spot is $distance from the entry point")
    }

    @Test
    fun `the power spot survives a platform narrow enough for the rim to reach the middle`() {
        // 3x3 with a rim is all edge. The rim would otherwise own the power spot's cell, and section
        // 2.5's confinement would be off in that biome with nothing in the log to say so.
        val plan = plan(palette(width = 3, depth = 3, rim = ArenaRim(rimBlock, height = 2)))
        val spot = assertNotNull(plan.powerSpot)
        assertTrue(defaultBox.isInside(spot))
        assertNull(plan.blocks[spot], "the power spot wins its cell outright")
    }

    @Test
    fun `opting out of the power spot places none`() {
        assertNull(plan(palette(powerSpot = false)).powerSpot)
    }

    @Test
    fun `a platform larger than the box is refused with both sizes named`() {
        val detail = refusal(palette(width = 80, depth = 10))
        assertTrue("80x10" in detail && "64x64" in detail, detail)
    }

    @Test
    fun `a box too small for the palette's own minimum is refused, naming where the size came from`() {
        // The reachable version: a palette with no explicit size was valid until somebody shrank the
        // arena box under it, so the message has to say the numbers were not theirs.
        val detail = refusal(palette(), box = BoundingBox(0, 64, 0, 1, 67, 1))
        assertTrue("arena box" in detail, detail)
    }

    @Test
    fun `a rim taller than the box is refused rather than truncated`() {
        // Truncating would build an arena that is not the one that was written, and the author would
        // have to notice the missing courses by eye.
        val detail = refusal(palette(rim = ArenaRim(rimBlock, height = 40)))
        assertTrue("41" in detail && "32" in detail, detail)
    }

    @Test
    fun `a box one block tall has no room for a power spot`() {
        val box = BoundingBox(0, 64, 0, 15, 64, 15)
        assertTrue("height" in refusal(palette(), box))
        // ...and is fine once the palette stops asking for one.
        assertNull(plan(palette(powerSpot = false), box).powerSpot)
    }

    @Test
    fun `pillars that would pass through each other are refused`() {
        val detail = refusal(palette(width = 5, depth = 5, pillars = ArenaPillars(pillarBlock, height = 4, inset = 3)))
        assertTrue("inset" in detail, detail)
    }

    // ------------------------------------------------------------------ circular islands

    @Test
    fun `a circular island fills its footprint as a disc, not a square`() {
        val square = plan(palette(width = 41, depth = 41)).blocks.keys
        val circle = plan(palette(width = 41, depth = 41, shape = ArenaShape.CIRCLE)).blocks.keys

        // A disc inscribed in a 41x41 square is about pi/4 of it, and every cell of it is also a cell
        // of the square — the circle carves the same footprint rather than moving it.
        assertTrue(circle.size < square.size, "a disc should be smaller than its bounding square")
        assertTrue(circle.size > square.size / 2, "a disc should be most of its bounding square")
        assertTrue(square.containsAll(circle), "the disc left its bounding footprint")
    }

    @Test
    fun `a circular island is symmetric about its centre block`() {
        // The property a builder actually needs: something built on one side of the island fits the
        // other. An off-by-half in the radius maths gives an island a column wider on one side, which
        // nobody notices until they build something symmetrical on it.
        val floorCells = plan(palette(width = 41, depth = 41, shape = ArenaShape.CIRCLE))
            .blocks.keys.filter { it.y == defaultBox.minY() }
        val centreX = floorCells.minOf { it.x } + (floorCells.maxOf { it.x } - floorCells.minOf { it.x }) / 2
        val centreZ = floorCells.minOf { it.z } + (floorCells.maxOf { it.z } - floorCells.minOf { it.z }) / 2
        val cells = floorCells.map { it.x to it.z }.toSet()

        cells.forEach { (x, z) ->
            val mirroredX = 2 * centreX - x
            val mirroredZ = 2 * centreZ - z
            assertTrue(mirroredX to z in cells, "($x,$z) has no mirror across x")
            assertTrue(x to mirroredZ in cells, "($x,$z) has no mirror across z")
        }
    }

    @Test
    fun `a circular rim follows the edge of the disc and stands on its own floor`() {
        val plan = plan(
            palette(width = 41, depth = 41, shape = ArenaShape.CIRCLE, rim = ArenaRim(rimBlock, height = 2)),
        )
        val floorY = defaultBox.minY()
        val floorCells = plan.blocks.filter { it.key.y == floorY }.keys.map { it.x to it.z }.toSet()
        val rimCells = plan.blocks.filter { it.key.y == floorY + 1 }.keys

        assertTrue(rimCells.isNotEmpty(), "a circular rim placed nothing")
        // The bug this exists to catch: a rim derived from the bounding box rather than the disc would
        // ring the square, leaving rim blocks hovering over the void outside the island.
        rimCells.forEach { pos ->
            assertTrue(pos.x to pos.z in floorCells, "rim at (${pos.x},${pos.z}) has no floor under it")
        }
    }

    @Test
    fun `a square island keeps exactly the plan it had before shapes existed`() {
        // SQUARE is the default and has to stay bit-identical, since every palette written before the
        // field existed is one.
        val before = plan(palette(width = 21, depth = 21, rim = ArenaRim(rimBlock, height = 2)))
        val after = plan(
            palette(width = 21, depth = 21, rim = ArenaRim(rimBlock, height = 2), shape = ArenaShape.SQUARE),
        )
        assertEquals(before.blocks, after.blocks)
        assertEquals(before.powerSpot, after.powerSpot)
    }

    @Test
    fun `pillars on a circular island stand on the island`() {
        // On a square they go in the corners; a disc has none, so they go on the diagonals. Either way
        // the thing that must hold is that they are standing on floor rather than in the void.
        val plan = plan(
            palette(
                width = 41,
                depth = 41,
                shape = ArenaShape.CIRCLE,
                pillars = ArenaPillars(pillarBlock, height = 5, inset = 4),
            ),
        )
        val floorY = defaultBox.minY()
        val floorCells = plan.blocks.filter { it.key.y == floorY }.keys.map { it.x to it.z }.toSet()
        val pillarCells = plan.blocks.filter { it.value == pillarBlock }.keys

        assertEquals(4, pillarCells.map { it.x to it.z }.distinct().size, "expected four pillars")
        pillarCells.forEach { pos ->
            assertTrue(pos.x to pos.z in floorCells, "pillar at (${pos.x},${pos.z}) is standing in the void")
        }
    }
}
