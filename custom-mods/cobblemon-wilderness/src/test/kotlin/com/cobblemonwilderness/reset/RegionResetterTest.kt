package com.cobblemonwilderness.reset

import com.cobblemonwilderness.config.BoundingBox
import org.slf4j.helpers.NOPLogger
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RegionResetterTest {

    private val box = BoundingBox(minX = -20000, minZ = -20000, maxX = 20000, maxZ = 20000)
    private val subfolders = listOf("region", "entities", "poi")

    /** Builds a temp dimension folder with one inside-box region (r.0.0) and one outside
     *  (r.100.100), each present in region/, entities/ and poi/. Returns the folder. */
    private fun makeDimension(): Path {
        val dim = Files.createTempDirectory("wild-dim")
        for (sub in subfolders) {
            val d = Files.createDirectories(dim.resolve(sub))
            Files.write(d.resolve("r.0.0.mca"), byteArrayOf(1, 2, 3))       // inside box
            Files.write(d.resolve("r.100.100.mca"), byteArrayOf(4, 5, 6))   // outside box
        }
        return dim
    }

    @Test
    fun `real run with backupTarget moves outside-box files into the snapshot and keeps inside`() {
        val dim = makeDimension()
        val backup = dim.resolveSibling("snap")
        try {
            val report = RegionResetter.run(
                "d", dim, box, dryRun = false, maxDeleteFraction = 1.0,
                backupTarget = backup, log = NOPLogger.NOP_LOGGER,
            )
            assertEquals(1, report.regionsDeleted)
            assertEquals(1, report.regionsKept)
            for (sub in subfolders) {
                // outside-box file left world/ and now lives in the snapshot
                assertFalse(Files.exists(dim.resolve(sub).resolve("r.100.100.mca")))
                assertTrue(Files.exists(backup.resolve(sub).resolve("r.100.100.mca")))
                // inside-box file untouched, and never copied into the snapshot
                assertTrue(Files.exists(dim.resolve(sub).resolve("r.0.0.mca")))
                assertFalse(Files.exists(backup.resolve(sub).resolve("r.0.0.mca")))
            }
        } finally {
            dim.toFile().deleteRecursively(); backup.toFile().deleteRecursively()
        }
    }

    @Test
    fun `real run without backupTarget deletes outside-box files outright`() {
        val dim = makeDimension()
        try {
            RegionResetter.run(
                "d", dim, box, dryRun = false, maxDeleteFraction = 1.0,
                backupTarget = null, log = NOPLogger.NOP_LOGGER,
            )
            for (sub in subfolders) {
                assertFalse(Files.exists(dim.resolve(sub).resolve("r.100.100.mca")))
                assertTrue(Files.exists(dim.resolve(sub).resolve("r.0.0.mca")))
            }
        } finally {
            dim.toFile().deleteRecursively()
        }
    }

    @Test
    fun `dry run neither deletes nor snapshots`() {
        val dim = makeDimension()
        val backup = dim.resolveSibling("snap")
        try {
            val report = RegionResetter.run(
                "d", dim, box, dryRun = true, maxDeleteFraction = 1.0,
                backupTarget = backup, log = NOPLogger.NOP_LOGGER,
            )
            assertEquals(1, report.regionsDeleted) // tallied, not performed
            for (sub in subfolders) {
                assertTrue(Files.exists(dim.resolve(sub).resolve("r.100.100.mca")))
            }
            assertFalse(Files.exists(backup)) // nothing written
        } finally {
            dim.toFile().deleteRecursively(); backup.toFile().deleteRecursively()
        }
    }

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
    fun `contains is inclusive of both edges and rejects just past them`() {
        val b = box.snappedToRegions() // X/Z [-20480..20479]
        assertTrue(b.contains(0, 0))
        assertTrue(b.contains(-20480, -20480)) // min corner, inclusive
        assertTrue(b.contains(20479, 20479))   // max corner, inclusive
        assertFalse(b.contains(20480, 0))      // one block past +X
        assertFalse(b.contains(-20481, 0))     // one block past -X
        assertFalse(b.contains(0, 20480))      // one block past +Z
    }

    @Test
    fun `circuit breaker trips only above the fraction`() {
        // 95 of 100 deletable, limit 0.9 → trips.
        assertTrue(RegionResetter.exceedsLimit(95, 100, 0.9))
        // exactly at the limit → does not trip (strictly greater).
        assertFalse(RegionResetter.exceedsLimit(90, 100, 0.9))
        // well under → fine.
        assertFalse(RegionResetter.exceedsLimit(50, 100, 0.9))
        // disabled (>= 1.0) → never trips even at 100%.
        assertFalse(RegionResetter.exceedsLimit(100, 100, 1.0))
        // empty world → nothing to trip on.
        assertFalse(RegionResetter.exceedsLimit(0, 0, 0.9))
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
