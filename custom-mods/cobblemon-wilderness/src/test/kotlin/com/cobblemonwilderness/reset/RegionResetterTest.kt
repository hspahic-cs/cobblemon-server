package com.cobblemonwilderness.reset

import com.cobblemonwilderness.config.BoundingBox
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RegionResetterTest {

    private val box = BoundingBox(minX = -20000, minZ = -20000, maxX = 20000, maxZ = 20000)

    @Test
    fun `region at origin is kept`() {
        // r.0.0 covers blocks [0..511] — squarely inside the box.
        assertFalse(RegionResetter.isRegionFullyOutside(box, 0, 0))
    }

    @Test
    fun `region far outside is deletable`() {
        // r.100.100 covers [51200..51711] — nowhere near the box.
        assertTrue(RegionResetter.isRegionFullyOutside(box, 100, 100))
    }

    @Test
    fun `region straddling the positive edge is kept`() {
        // maxX=20000 falls inside r.39 (covers [19968..20479]) → must be kept.
        assertFalse(RegionResetter.isRegionFullyOutside(box, 39, 0))
        // r.40 covers [20480..20991] — wholly past the edge → deletable.
        assertTrue(RegionResetter.isRegionFullyOutside(box, 40, 0))
    }

    @Test
    fun `region straddling the negative edge is kept`() {
        // minX=-20000 falls inside r.-40 (covers [-20480..-19969]) → must be kept.
        assertFalse(RegionResetter.isRegionFullyOutside(box, -40, 0))
        // r.-41 covers [-20992..-20481] — wholly past the edge → deletable.
        assertTrue(RegionResetter.isRegionFullyOutside(box, -41, 0))
    }

    @Test
    fun `outside on one axis only is still deletable`() {
        // Inside on X, far outside on Z → does not intersect → deletable.
        assertTrue(RegionResetter.isRegionFullyOutside(box, 0, 100))
    }

    @Test
    fun `inverted box coordinates are normalized`() {
        val inverted = BoundingBox(minX = 20000, minZ = 20000, maxX = -20000, maxZ = -20000)
        assertFalse(RegionResetter.isRegionFullyOutside(inverted, 0, 0))
        assertTrue(RegionResetter.isRegionFullyOutside(inverted, 100, 100))
    }

    @Test
    fun `snapping expands edges out to region boundaries`() {
        // +/-20000 falls mid-region; snapping rounds out to whole regions -40..39.
        val snapped = box.snappedToRegions()
        assertEquals(-20480, snapped.minX) // start of region -40
        assertEquals(-20480, snapped.minZ)
        assertEquals(20479, snapped.maxX)  // end of region 39
        assertEquals(20479, snapped.maxZ)
    }

    @Test
    fun `an already-aligned box is unchanged by snapping`() {
        val aligned = BoundingBox(minX = -20480, minZ = -20480, maxX = 20479, maxZ = 20479)
        assertEquals(aligned, aligned.snappedToRegions())
    }

    @Test
    fun `with a snapped box no region straddles an edge`() {
        // Every region is now strictly inside or strictly outside — the edge regions are clean.
        val snapped = box.snappedToRegions()
        assertFalse(RegionResetter.isRegionFullyOutside(snapped, 39, 39)) // last kept
        assertTrue(RegionResetter.isRegionFullyOutside(snapped, 40, 0))   // first dropped
        assertFalse(RegionResetter.isRegionFullyOutside(snapped, -40, 0)) // last kept (neg)
        assertTrue(RegionResetter.isRegionFullyOutside(snapped, -41, 0))  // first dropped (neg)
    }

    @Test
    fun `parseRegionCoords reads valid names and rejects junk`() {
        assertEquals(39 to -40, RegionResetter.parseRegionCoords("r.39.-40.mca"))
        assertEquals(0 to 0, RegionResetter.parseRegionCoords("r.0.0.mca"))
        assertNull(RegionResetter.parseRegionCoords("r.0.0.mcc"))
        assertNull(RegionResetter.parseRegionCoords("level.dat"))
        assertNull(RegionResetter.parseRegionCoords("r.x.0.mca"))
    }
}
