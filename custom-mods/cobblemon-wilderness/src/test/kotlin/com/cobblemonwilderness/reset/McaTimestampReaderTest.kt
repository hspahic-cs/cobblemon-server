package com.cobblemonwilderness.reset

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class McaTimestampReaderTest {

    private fun tempDim(): Path = Files.createTempDirectory("wild-mca")

    @Test
    fun `decodes the epoch-second timestamp of a present chunk`() {
        val dim = tempDim()
        try {
            val f = dim.resolve("region").resolve("r.5.5.mca")
            McaTestFiles.writeRegion(f, listOf(1_700_000_000L))
            assertEquals(1_700_000_000L, McaTimestampReader.maxTimestampInFile(f))
        } finally {
            dim.toFile().deleteRecursively()
        }
    }

    @Test
    fun `takes the max across present chunks within one file`() {
        val dim = tempDim()
        try {
            val f = dim.resolve("region").resolve("r.5.5.mca")
            McaTestFiles.writeRegion(f, listOf(100L, 900L, 400L))
            assertEquals(900L, McaTimestampReader.maxTimestampInFile(f))
        } finally {
            dim.toFile().deleteRecursively()
        }
    }

    @Test
    fun `a header with no present chunk yields null`() {
        val dim = tempDim()
        try {
            val f = dim.resolve("region").resolve("r.5.5.mca")
            McaTestFiles.writeRegion(f, emptyList()) // all-zero header
            assertNull(McaTimestampReader.maxTimestampInFile(f))
        } finally {
            dim.toFile().deleteRecursively()
        }
    }

    @Test
    fun `a missing or truncated file is skipped as null`() {
        val dim = tempDim()
        try {
            val missing = dim.resolve("region").resolve("r.9.9.mca")
            assertNull(McaTimestampReader.maxTimestampInFile(missing))

            val short = dim.resolve("region").resolve("r.8.8.mca")
            Files.createDirectories(short.parent)
            Files.write(short, byteArrayOf(1, 2, 3)) // shorter than the two header sectors
            assertNull(McaTimestampReader.maxTimestampInFile(short))
        } finally {
            dim.toFile().deleteRecursively()
        }
    }

    @Test
    fun `maxTimestamp takes the newest across region entities and poi`() {
        val dim = tempDim()
        try {
            McaTestFiles.writeRegion(dim.resolve("region").resolve("r.2.3.mca"), listOf(100L))
            McaTestFiles.writeRegion(dim.resolve("entities").resolve("r.2.3.mca"), listOf(500L))
            McaTestFiles.writeRegion(dim.resolve("poi").resolve("r.2.3.mca"), listOf(300L))
            // Newest write is the entities file — that's the tile's freshness.
            assertEquals(500L, McaTimestampReader.maxTimestamp(dim, 2, 3))
        } finally {
            dim.toFile().deleteRecursively()
        }
    }

    @Test
    fun `maxTimestamp is null when the region is absent from all three folders`() {
        val dim = tempDim()
        try {
            assertNull(McaTimestampReader.maxTimestamp(dim, 7, 7))
        } finally {
            dim.toFile().deleteRecursively()
        }
    }
}
