package com.cobblemonroguelite.arena

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The clamp the section-pruned scan stands on.
 *
 * The scan itself needs a level and cannot be reached here. This is the arithmetic it gets wrong if
 * anybody touches it: the part of the box that lies inside one 16-block chunk section. Too narrow and
 * a stripe of the previous build survives along a chunk edge, which is invisible until a band
 * transition puts a smaller arena in and the old wall is still standing; too wide and the scan reads
 * blocks outside the box, which belong to the slot next door.
 */
class ArenaBoxScanTest {

    @Test
    fun `a box larger than one section is clipped to that section`() {
        assertEquals(0..15, ArenaBoxScan.overlap(0, 63, 0))
        assertEquals(16..31, ArenaBoxScan.overlap(0, 63, 1))
        assertEquals(48..63, ArenaBoxScan.overlap(0, 63, 3))
    }

    @Test
    fun `a box that starts and ends mid-section is clipped to the box`() {
        assertEquals(5..15, ArenaBoxScan.overlap(5, 40, 0))
        assertEquals(32..40, ArenaBoxScan.overlap(5, 40, 2))
    }

    @Test
    fun `negative coordinates clamp the same way`() {
        // Arenas sit at negative coordinates as soon as an owner points fixedArenas at a real world,
        // and section coordinates there are negative too — the place a `/ 16` instead of a floor
        // division would land one section off and silently skip a chunk.
        assertEquals(-16..-1, ArenaBoxScan.overlap(-32, -1, -1))
        assertEquals(-32..-17, ArenaBoxScan.overlap(-32, -1, -2))
    }
}
