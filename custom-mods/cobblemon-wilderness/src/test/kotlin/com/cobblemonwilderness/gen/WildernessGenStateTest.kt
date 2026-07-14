package com.cobblemonwilderness.gen

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class WildernessGenStateTest {

    private val spacing = 10 // chunks → 160-block cells

    // Default keep-zone: blocks [-20480 .. 20479] on each axis.
    private fun configure(generations: Map<Long, Int>) =
        WildernessGenState.configure(true, generations, -20480, -20480, 20479, 20479)

    // Anchor region of a cell = (cellX*spacing) >> 5. For cell 200 @ spacing 10 → chunk 2000 → region 62.
    private val outsideCell = 200
    private val outsideRegion = (outsideCell * spacing) shr 5 // 62

    @Test
    fun `an outside cell whose anchor region was reset gets that region's derived salt`() {
        val key = WildernessGenState.regionKey(outsideRegion, outsideRegion)
        configure(mapOf(key to 3))
        val expected = WildernessGenState.deriveSalt(outsideRegion, outsideRegion, 3)
        assertNotEquals(0, expected)
        assertEquals(expected, WildernessGenState.cellSalt(outsideCell, outsideCell, spacing, true))
    }

    @Test
    fun `an outside cell whose anchor region was never reset stays vanilla`() {
        configure(mapOf(WildernessGenState.regionKey(outsideRegion, outsideRegion) to 3))
        // A different far-outside cell whose anchor region is absent from the map → gen 0 → salt 0.
        assertEquals(0, WildernessGenState.cellSalt(500, 500, spacing, true))
    }

    @Test
    fun `a cell inside the box is left vanilla even if its region key were present`() {
        // cell (0,0) = blocks [0..159], inside. Region 0 present with a gen, but the box gate wins.
        configure(mapOf(WildernessGenState.regionKey(0, 0) to 5))
        assertEquals(0, WildernessGenState.cellSalt(0, 0, spacing, true))
    }

    @Test
    fun `the overworld gate suppresses salt for non-overworld worldgen`() {
        val key = WildernessGenState.regionKey(outsideRegion, outsideRegion)
        configure(mapOf(key to 3))
        // Same cell that relocates in the overworld must be byte-identical vanilla elsewhere.
        assertNotEquals(0, WildernessGenState.cellSalt(outsideCell, outsideCell, spacing, true))
        assertEquals(0, WildernessGenState.cellSalt(outsideCell, outsideCell, spacing, false))
    }

    @Test
    fun `disabled or empty snapshot never relocates (inert fast path)`() {
        WildernessGenState.disable()
        assertEquals(0, WildernessGenState.cellSalt(outsideCell, outsideCell, spacing, true))
        // Enabled but empty snapshot (no prune yet) → inactive.
        configure(emptyMap())
        assertEquals(0, WildernessGenState.cellSalt(outsideCell, outsideCell, spacing, true))
    }

    @Test
    fun `non-positive spacing is ignored`() {
        configure(mapOf(WildernessGenState.regionKey(outsideRegion, outsideRegion) to 3))
        assertEquals(0, WildernessGenState.cellSalt(outsideCell, outsideCell, 0, true))
    }

    @Test
    fun `deriveSalt is 0 only for generation 0 and never 0 for gen 1 plus`() {
        for (rx in -40..40 step 7) {
            for (rz in -40..40 step 11) {
                assertEquals(0, WildernessGenState.deriveSalt(rx, rz, 0))
                for (gen in 1..64) {
                    assertNotEquals(0, WildernessGenState.deriveSalt(rx, rz, gen), "rx=$rx rz=$rz gen=$gen")
                }
            }
        }
    }

    @Test
    fun `regionKey packs and is distinct per region including negatives`() {
        assertEquals(WildernessGenState.regionKey(62, 62), WildernessGenState.regionKey(62, 62))
        assertNotEquals(WildernessGenState.regionKey(62, 62), WildernessGenState.regionKey(62, -62))
        assertNotEquals(WildernessGenState.regionKey(-1, 0), WildernessGenState.regionKey(0, -1))
    }

    @Test
    fun `the 3-arg cellSalt honors the per-thread overworld flag`() {
        val key = WildernessGenState.regionKey(outsideRegion, outsideRegion)
        configure(mapOf(key to 3))
        try {
            // Flag defaults false → inert.
            WildernessGenState.endOverworld()
            assertEquals(0, WildernessGenState.cellSalt(outsideCell, outsideCell, spacing))
            // Marked overworld → salt applies, matching the explicit-arg path.
            WildernessGenState.beginOverworld()
            assertTrue(WildernessGenState.isOverworld())
            assertEquals(
                WildernessGenState.cellSalt(outsideCell, outsideCell, spacing, true),
                WildernessGenState.cellSalt(outsideCell, outsideCell, spacing),
            )
        } finally {
            WildernessGenState.endOverworld()
        }
    }
}
