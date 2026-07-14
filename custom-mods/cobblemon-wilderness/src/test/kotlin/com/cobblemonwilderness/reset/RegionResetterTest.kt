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
    private val lmStart = "legendarymonuments:articuno_shrine"

    /** Builds a temp dimension with one inside-box region (r.0.0) and one fully-outside, non-monument
     *  region (r.100.100), each present in region/, entities/ and poi/ (header-only → no monument). */
    private fun makeDimension(): Path {
        val dim = Files.createTempDirectory("wild-dim")
        McaTestFiles.writeAllFolders(dim, 0, 0, 1L)       // inside box (kept by geometry)
        McaTestFiles.writeAllFolders(dim, 100, 100, 1L)   // outside box, no monument → deletable
        return dim
    }

    /** Writes an outside monument region at (rx, rz): region/ holds an LM start chunk; entities/poi headers. */
    private fun writeMonument(dim: Path, rx: Int, rz: Int) {
        McaTestFiles.writeRegionWithChunks(
            dim.resolve("region").resolve("r.$rx.$rz.mca"),
            listOf(McaTestFiles.ChunkSpec(slot = 0, nbt = McaTestFiles.chunkStructuresNbt(starts = mapOf(lmStart to lmStart)))),
        )
        McaTestFiles.writeRegion(dim.resolve("entities").resolve("r.$rx.$rz.mca"), listOf(1L))
        McaTestFiles.writeRegion(dim.resolve("poi").resolve("r.$rx.$rz.mca"), listOf(1L))
    }

    private fun run(dim: Path, dryRun: Boolean, maxFraction: Double = 1.0, backup: Path? = null, forced: Boolean = false) =
        RegionResetter.run("d", dim, box, dryRun, maxFraction, backup, NOPLogger.NOP_LOGGER, forced)

    @Test
    fun `real run with backupTarget moves outside non-monument files into the snapshot and keeps inside`() {
        val dim = makeDimension()
        val backup = dim.resolveSibling("snap")
        try {
            val report = run(dim, dryRun = false, backup = backup)
            assertEquals(1, report.regionsDeleted)
            assertEquals(1, report.regionsKept)
            assertEquals(1, report.keptInside)
            for (sub in subfolders) {
                assertFalse(Files.exists(dim.resolve(sub).resolve("r.100.100.mca")))
                assertTrue(Files.exists(backup.resolve(sub).resolve("r.100.100.mca")))
                assertTrue(Files.exists(dim.resolve(sub).resolve("r.0.0.mca")))
                assertFalse(Files.exists(backup.resolve(sub).resolve("r.0.0.mca")))
            }
        } finally {
            dim.toFile().deleteRecursively(); backup.toFile().deleteRecursively()
        }
    }

    @Test
    fun `real run without backupTarget deletes outside non-monument files outright`() {
        val dim = makeDimension()
        try {
            run(dim, dryRun = false)
            for (sub in subfolders) {
                assertFalse(Files.exists(dim.resolve(sub).resolve("r.100.100.mca")))
                assertTrue(Files.exists(dim.resolve(sub).resolve("r.0.0.mca")))
            }
        } finally {
            dim.toFile().deleteRecursively()
        }
    }

    @Test
    fun `a monument region is never deleted, even fully outside the box`() {
        val dim = makeDimension()
        writeMonument(dim, 200, 200) // fully outside, holds a monument
        try {
            val report = run(dim, dryRun = false)
            assertEquals(1, report.keptMonument)
            assertEquals(1, report.regionsDeleted) // only r.100.100
            assertEquals(RegionDisposition.KEPT_MONUMENT, report.scans.single { it.rx == 200 }.disposition)
            for (sub in subfolders) {
                assertTrue(Files.exists(dim.resolve(sub).resolve("r.200.200.mca")), "monument $sub must survive")
                assertFalse(Files.exists(dim.resolve(sub).resolve("r.100.100.mca")))
            }
        } finally {
            dim.toFile().deleteRecursively()
        }
    }

    @Test
    fun `a straddling region is kept and counted separately from inside`() {
        val dim = Files.createTempDirectory("wild-straddle")
        try {
            // r.39.0 covers X[19968..20479], overlapping the box edge at maxX=20000 → straddle.
            McaTestFiles.writeAllFolders(dim, 39, 0, 1L)
            McaTestFiles.writeAllFolders(dim, 0, 0, 1L)       // fully inside
            McaTestFiles.writeAllFolders(dim, 100, 100, 1L)   // fully outside → deletable
            val report = run(dim, dryRun = false)
            assertEquals(1, report.keptInside)
            assertEquals(1, report.keptStraddle)
            assertEquals(1, report.regionsDeleted)
            for (sub in subfolders) {
                assertTrue(Files.exists(dim.resolve(sub).resolve("r.39.0.mca")))
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
            val report = run(dim, dryRun = true, backup = backup)
            assertEquals(1, report.regionsDeleted) // tallied, not performed
            for (sub in subfolders) {
                assertTrue(Files.exists(dim.resolve(sub).resolve("r.100.100.mca")))
            }
            assertFalse(Files.exists(backup))
        } finally {
            dim.toFile().deleteRecursively(); backup.toFile().deleteRecursively()
        }
    }

    @Test
    fun `a normal run aborts above the fraction and deletes nothing`() {
        // 1 inside + 1 outside-deletable → deletable 1 of 2 = 50% > the 40% limit → breaker trips.
        val dim = makeDimension()
        val backup = dim.resolveSibling("snap")
        try {
            val report = run(dim, dryRun = false, maxFraction = 0.4, backup = backup)
            assertTrue(report.aborted)
            assertEquals(0, report.bytesFreed)
            for (sub in subfolders) {
                assertTrue(Files.exists(dim.resolve(sub).resolve("r.100.100.mca")))
            }
            assertFalse(Files.exists(backup))
        } finally {
            dim.toFile().deleteRecursively(); backup.toFile().deleteRecursively()
        }
    }

    @Test
    fun `a forced run proceeds past the fraction and still backs up`() {
        val dim = makeDimension()
        val backup = dim.resolveSibling("snap")
        try {
            val report = run(dim, dryRun = false, maxFraction = 0.4, backup = backup, forced = true)
            assertFalse(report.aborted)
            assertEquals(1, report.regionsDeleted)
            for (sub in subfolders) {
                assertFalse(Files.exists(dim.resolve(sub).resolve("r.100.100.mca")))
                assertTrue(Files.exists(backup.resolve(sub).resolve("r.100.100.mca")))
                assertTrue(Files.exists(dim.resolve(sub).resolve("r.0.0.mca")))
            }
        } finally {
            dim.toFile().deleteRecursively(); backup.toFile().deleteRecursively()
        }
    }

    // ---- geometry ----

    @Test
    fun `region at origin is kept`() {
        assertFalse(RegionResetter.isRegionFullyOutside(box, 0, 0))
    }

    @Test
    fun `region far outside is deletable`() {
        assertTrue(RegionResetter.isRegionFullyOutside(box, 100, 100))
    }

    @Test
    fun `region straddling the positive edge is kept`() {
        assertFalse(RegionResetter.isRegionFullyOutside(box, 39, 0))
        assertTrue(RegionResetter.isRegionFullyOutside(box, 40, 0))
    }

    @Test
    fun `region straddling the negative edge is kept`() {
        assertFalse(RegionResetter.isRegionFullyOutside(box, -40, 0))
        assertTrue(RegionResetter.isRegionFullyOutside(box, -41, 0))
    }

    @Test
    fun `outside on one axis only is still deletable`() {
        assertTrue(RegionResetter.isRegionFullyOutside(box, 0, 100))
    }

    @Test
    fun `inverted box coordinates are normalized`() {
        val inverted = BoundingBox(minX = 20000, minZ = 20000, maxX = -20000, maxZ = -20000)
        assertFalse(RegionResetter.isRegionFullyOutside(inverted, 0, 0))
        assertTrue(RegionResetter.isRegionFullyOutside(inverted, 100, 100))
    }

    @Test
    fun `isRegionFullyInside separates inside, straddle and outside`() {
        assertTrue(RegionResetter.isRegionFullyInside(box, 0, 0))    // wholly inside
        assertFalse(RegionResetter.isRegionFullyInside(box, 39, 0))  // straddles the +X edge
        assertFalse(RegionResetter.isRegionFullyInside(box, 100, 100)) // wholly outside
        // On a region-snapped box every region is strictly inside or strictly outside.
        val snapped = box.snappedToRegions()
        assertTrue(RegionResetter.isRegionFullyInside(snapped, 39, 39))
        assertFalse(RegionResetter.isRegionFullyInside(snapped, 40, 0))
    }

    @Test
    fun `snapping expands edges out to region boundaries`() {
        val snapped = box.snappedToRegions()
        assertEquals(-20480, snapped.minX)
        assertEquals(-20480, snapped.minZ)
        assertEquals(20479, snapped.maxX)
        assertEquals(20479, snapped.maxZ)
    }

    @Test
    fun `an already-aligned box is unchanged by snapping`() {
        val aligned = BoundingBox(minX = -20480, minZ = -20480, maxX = 20479, maxZ = 20479)
        assertEquals(aligned, aligned.snappedToRegions())
    }

    @Test
    fun `with a snapped box no region straddles an edge`() {
        val snapped = box.snappedToRegions()
        assertFalse(RegionResetter.isRegionFullyOutside(snapped, 39, 39))
        assertTrue(RegionResetter.isRegionFullyOutside(snapped, 40, 0))
        assertFalse(RegionResetter.isRegionFullyOutside(snapped, -40, 0))
        assertTrue(RegionResetter.isRegionFullyOutside(snapped, -41, 0))
    }

    @Test
    fun `contains is inclusive of both edges and rejects just past them`() {
        val b = box.snappedToRegions()
        assertTrue(b.contains(0, 0))
        assertTrue(b.contains(-20480, -20480))
        assertTrue(b.contains(20479, 20479))
        assertFalse(b.contains(20480, 0))
        assertFalse(b.contains(-20481, 0))
        assertFalse(b.contains(0, 20480))
    }

    @Test
    fun `circuit breaker trips only above the fraction`() {
        assertTrue(RegionResetter.exceedsLimit(95, 100, 0.9))
        assertFalse(RegionResetter.exceedsLimit(90, 100, 0.9))
        assertFalse(RegionResetter.exceedsLimit(50, 100, 0.9))
        assertFalse(RegionResetter.exceedsLimit(100, 100, 1.0))
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
