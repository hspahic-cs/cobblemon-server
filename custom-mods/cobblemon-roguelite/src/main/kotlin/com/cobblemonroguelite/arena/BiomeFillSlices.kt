package com.cobblemonroguelite.arena

import net.minecraft.world.level.levelgen.structure.BoundingBox

/**
 * Cutting an arena box into pieces `FillBiomeCommand.fill` will accept.
 *
 * ### Why this exists at all
 *
 * `fill` is public static and does the whole repaint — chunk rewrite and client resend — which is
 * exactly why §2.24 chose it. What it also does is refuse any region larger than the
 * `commandModificationBlockLimit` game rule, which defaults to **32768 blocks**. The default arena
 * box is 64×32×64, i.e. 131072, so the obvious single call is refused every time, on every server,
 * with the failure arriving as an `Either.right` that is easy to drop on the floor. A repaint that
 * silently never happens leaves a stamped build under the wrong sky, which reads as the feature not
 * working rather than as a limit being hit.
 *
 * Raising the game rule was the alternative and is worse: it is a world-wide setting that exists to
 * bound what one command can do to a server, and turning it up so that our arena fits would raise it
 * for every `/fill` any player ever runs.
 *
 * ### Why the arithmetic is fiddly
 *
 * Biomes are stored per 4×4×4 cell, and `fill` quantises **both** corners *downwards* onto that grid
 * before measuring. A slice therefore has to start on a 4-aligned coordinate or it silently begins
 * outside itself — and the volume `fill` measures is the span of the quantised corners, so `n` cells
 * along an axis measure as `4n-3` blocks, not `4n`. Both of those are off-by-a-cell mistakes whose
 * only symptom is a stripe of the arena keeping its old biome.
 *
 * Everything here is integer arithmetic over a box, which makes it the whole of the repaint that can
 * be tested without a booted server.
 */
object BiomeFillSlices {

    /** Biome cell size. Vanilla's `QuartPos`, spelled out because the whole file is about it. */
    const val CELL = 4

    /**
     * [box] cut into pieces, each of which `fill` will accept under [blockLimit].
     *
     * Every cell of [box] is covered by exactly one slice, so the caller repaints the whole arena by
     * walking the list. The order is deterministic (x, then z, then y) for no reason other than that a
     * log line naming "slice 3 of 4" should mean the same thing twice.
     *
     * An empty list means the box cannot be repainted at all under this limit — only reachable with a
     * limit below one cell, i.e. an operator who has set the game rule to something absurd. It is
     * returned rather than thrown because the caller's answer is to log and carry on with an unpainted
     * arena, which is cosmetic, where an exception out of the arena path would cost the player their
     * wave.
     */
    fun slice(box: BoundingBox, blockLimit: Int): List<BoundingBox> {
        val cellsX = cellSpan(box.minX(), box.maxX())
        val cellsY = cellSpan(box.minY(), box.maxY())
        val cellsZ = cellSpan(box.minZ(), box.maxZ())

        var stepX = cellsX
        var stepY = cellsY
        var stepZ = cellsZ
        // Halve the longest axis until the slice fits. Halving rather than shrinking to fit keeps the
        // pieces roughly cubic, which matters because `fill` loads a chunk per column it touches: a
        // one-cell-thick slab of the whole arena would fetch every chunk in the box for every slice.
        while (measuredVolume(stepX, stepY, stepZ) > blockLimit) {
            val longest = maxOf(stepX, stepY, stepZ)
            if (longest <= 1) return emptyList()
            when (longest) {
                stepX -> stepX = halve(stepX)
                stepZ -> stepZ = halve(stepZ)
                else -> stepY = halve(stepY)
            }
        }

        val slices = mutableListOf<BoundingBox>()
        var y = align(box.minY())
        while (y <= box.maxY()) {
            var z = align(box.minZ())
            while (z <= box.maxZ()) {
                var x = align(box.minX())
                while (x <= box.maxX()) {
                    slices += BoundingBox(
                        x, y, z,
                        lastBlock(x, stepX, box.maxX()),
                        lastBlock(y, stepY, box.maxY()),
                        lastBlock(z, stepZ, box.maxZ()),
                    )
                    x += stepX * CELL
                }
                z += stepZ * CELL
            }
            y += stepY * CELL
        }
        return slices
    }

    /**
     * The volume `fill` will measure for a slice of this many cells.
     *
     * `4n-3` and not `4n`, because it measures the span between two *quantised* corners: a slice of
     * two cells along x runs from cell start 0 to cell start 4, which is a span of 5 blocks. Getting
     * this wrong in the safe direction only wastes slices; getting it wrong the other way produces a
     * slice that is refused at runtime, on a server, in the one code path that has no test.
     */
    private fun measuredVolume(cellsX: Int, cellsY: Int, cellsZ: Int): Long =
        span(cellsX).toLong() * span(cellsY) * span(cellsZ)

    private fun span(cells: Int): Int = (cells - 1) * CELL + 1

    private fun cellSpan(min: Int, max: Int): Int = (align(max) - align(min)) / CELL + 1

    /** Floor division, not truncation: arenas may sit at negative coordinates. */
    private fun align(value: Int): Int = Math.floorDiv(value, CELL) * CELL

    private fun halve(cells: Int): Int = (cells + 1) / 2

    /**
     * The last block of a slice, clamped to the box.
     *
     * Clamping matters at the far edge: a box that is not a whole number of cells wide would
     * otherwise hand `fill` a corner outside the arena, and everything from there to the cell
     * boundary belongs to the arena next door.
     */
    private fun lastBlock(start: Int, cells: Int, boxMax: Int): Int =
        minOf(start + cells * CELL - 1, boxMax)
}
