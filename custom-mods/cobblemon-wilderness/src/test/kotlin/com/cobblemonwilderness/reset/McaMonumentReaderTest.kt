package com.cobblemonwilderness.reset

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Covers [McaTimestampReader.regionHasMonument] / [McaTimestampReader.chunkReferencesMonument]. */
class McaMonumentReaderTest {

    private val lmStart = "legendarymonuments:articuno_shrine"
    private val lmRef = "legendarymonuments:zapdos_shrine"

    private fun tempDim(): Path = Files.createTempDirectory("wild-monument")

    // ---- chunkReferencesMonument (pure NBT) ----

    @Test
    fun `a chunk with an LM start is a monument`() {
        val nbt = McaTestFiles.chunkStructuresNbt(starts = mapOf(lmStart to lmStart))
        assertTrue(McaTimestampReader.chunkReferencesMonument(nbt))
    }

    @Test
    fun `a chunk with only an LM reference (footprint straddle, no start) is a monument`() {
        val nbt = McaTestFiles.chunkStructuresNbt(references = listOf(lmRef))
        assertTrue(McaTimestampReader.chunkReferencesMonument(nbt))
    }

    @Test
    fun `a chunk with only a non-LM structure is not a monument`() {
        val nbt = McaTestFiles.chunkStructuresNbt(
            starts = mapOf("minecraft:village" to "minecraft:village"),
            references = listOf("minecraft:mineshaft"),
        )
        assertFalse(McaTimestampReader.chunkReferencesMonument(nbt))
    }

    @Test
    fun `a chunk with no structures compound is not a monument`() {
        assertFalse(McaTimestampReader.chunkReferencesMonument(McaTestFiles.chunkStructuresNbt(includeStructures = false)))
    }

    @Test
    fun `an empty structures compound is not a monument`() {
        assertFalse(McaTimestampReader.chunkReferencesMonument(McaTestFiles.chunkStructuresNbt()))
    }

    @Test
    fun `an LM start marked INVALID (biome-rejected) is not a monument`() {
        // A candidate cell where the monument was considered but failed to generate writes an
        // INVALID start — it must NOT keep the region (keep only monuments that actually generated).
        val nbt = McaTestFiles.chunkStructuresNbt(starts = mapOf(lmStart to "INVALID"))
        assertFalse(McaTimestampReader.chunkReferencesMonument(nbt))
    }

    @Test
    fun `malformed NBT bytes are not a monument`() {
        assertFalse(McaTimestampReader.chunkReferencesMonument(byteArrayOf(10, 0, 0, 4, 2, 9, 9)))
        assertFalse(McaTimestampReader.chunkReferencesMonument(ByteArray(0)))
    }

    // ---- regionHasMonument (region file, all compressions) ----

    @Test
    fun `regionHasMonument detects an LM start across all compression types`() {
        val dim = tempDim()
        try {
            for (compression in listOf(McaTestFiles.GZIP, McaTestFiles.ZLIB, McaTestFiles.UNCOMPRESSED)) {
                val f = dim.resolve("region").resolve("r.$compression.0.mca")
                McaTestFiles.writeRegionWithChunks(
                    f,
                    listOf(McaTestFiles.ChunkSpec(
                        slot = 0,
                        nbt = McaTestFiles.chunkStructuresNbt(starts = mapOf(lmStart to lmStart)),
                        compression = compression,
                    )),
                )
                assertTrue(McaTimestampReader.regionHasMonument(f), "compression $compression")
            }
        } finally {
            dim.toFile().deleteRecursively()
        }
    }

    @Test
    fun `regionHasMonument detects a footprint-straddle reference`() {
        val dim = tempDim()
        try {
            val f = dim.resolve("region").resolve("r.1.1.mca")
            McaTestFiles.writeRegionWithChunks(
                f, listOf(McaTestFiles.ChunkSpec(slot = 0, nbt = McaTestFiles.chunkStructuresNbt(references = listOf(lmRef)))),
            )
            assertTrue(McaTimestampReader.regionHasMonument(f))
        } finally {
            dim.toFile().deleteRecursively()
        }
    }

    @Test
    fun `a region with only vanilla structures has no monument`() {
        val dim = tempDim()
        try {
            val f = dim.resolve("region").resolve("r.2.2.mca")
            McaTestFiles.writeRegionWithChunks(
                f,
                listOf(
                    McaTestFiles.ChunkSpec(slot = 0, nbt = McaTestFiles.chunkStructuresNbt(starts = mapOf("minecraft:village" to "minecraft:village"))),
                    McaTestFiles.ChunkSpec(slot = 1, nbt = McaTestFiles.chunkStructuresNbt(includeStructures = false)),
                ),
            )
            assertFalse(McaTimestampReader.regionHasMonument(f))
        } finally {
            dim.toFile().deleteRecursively()
        }
    }

    @Test
    fun `regionHasMonument scans past earlier empty chunks to find a later monument`() {
        // The monument sits in a LATER present chunk; an early-exit-only-on-first-chunk reader would
        // miss it and wrongly wipe the region. Also confirms a bad/garbage chunk doesn't abort the scan.
        val dim = tempDim()
        try {
            val f = dim.resolve("region").resolve("r.3.3.mca")
            McaTestFiles.writeRegionWithChunks(
                f,
                listOf(
                    McaTestFiles.ChunkSpec(slot = 0, nbt = McaTestFiles.chunkStructuresNbt(includeStructures = false)),
                    McaTestFiles.ChunkSpec(slot = 1, nbt = byteArrayOf(1, 2, 3, 4), compression = McaTestFiles.UNCOMPRESSED), // garbage
                    McaTestFiles.ChunkSpec(slot = 2, nbt = McaTestFiles.chunkStructuresNbt(starts = mapOf(lmStart to lmStart))),
                ),
            )
            assertTrue(McaTimestampReader.regionHasMonument(f))
        } finally {
            dim.toFile().deleteRecursively()
        }
    }

    @Test
    fun `a missing or short region file has no monument`() {
        val dim = tempDim()
        try {
            assertFalse(McaTimestampReader.regionHasMonument(dim.resolve("region").resolve("r.9.9.mca")))
            val short = dim.resolve("region").resolve("r.8.8.mca")
            Files.createDirectories(short.parent)
            Files.write(short, byteArrayOf(1, 2, 3)) // shorter than one sector
            assertFalse(McaTimestampReader.regionHasMonument(short))
        } finally {
            dim.toFile().deleteRecursively()
        }
    }
}
