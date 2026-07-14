package com.cobblemonwilderness.reset

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists

/**
 * Reads the "last-visited" freshness signal for a region tile straight out of the `.mca`
 * headers — no chunk decompression, ~8 KiB per file.
 *
 * An Anvil region file begins with two 4 KiB header sectors:
 *  - sector 0: 1024 location entries (4 bytes each). The first 3 bytes are the chunk's
 *    offset in sectors; a **nonzero** offset means the chunk is present.
 *  - sector 1: 1024 timestamps (4 bytes each, big-endian **unix epoch seconds**), bumped
 *    whenever that chunk is written back to disk.
 *
 * The freshness of a tile is the newest write across all present chunks. We read all three
 * sibling folders — `region/`, `entities/`, `poi/` — for the same `r.X.Z.mca` and take the
 * max, because entity- or POI-only activity lands in the separate files: a region can be
 * block-stale yet actively used. Taking the max biases toward KEEPING, which guards the
 * dangerous direction (wiping an area someone is still visiting).
 *
 * This is the Kotlin port of the verified decode in `ops/wild-mtime-probe.sh`.
 */
object McaTimestampReader {

    /** Anvil header/sector size in bytes. */
    private const val SECTOR = 4096
    /** Chunks indexed per region file (32 × 32). */
    private const val ENTRIES = 1024
    /** Bytes we need off the front of each file: the two header sectors. */
    private const val HEADER_BYTES = 2 * SECTOR

    /** The three sibling folders that share the `r.X.Z.mca` naming scheme. */
    private val SUBFOLDERS = listOf("region", "entities", "poi")

    /**
     * Max present-chunk timestamp (epoch seconds) in a single `.mca` file, or `null` when the
     * file is absent, unreadable, shorter than the two header sectors, or has zero present
     * chunks. A missing/short file is skipped gracefully — it simply doesn't contribute.
     */
    fun maxTimestampInFile(file: Path): Long? {
        if (!file.exists()) return null
        val head = ByteArray(HEADER_BYTES)
        val read = try {
            Files.newInputStream(file).use { ins ->
                var off = 0
                while (off < HEADER_BYTES) {
                    val n = ins.read(head, off, HEADER_BYTES - off)
                    if (n < 0) break
                    off += n
                }
                off
            }
        } catch (e: IOException) {
            return null
        }
        if (read < HEADER_BYTES) return null // truncated header → not a usable region file

        var max: Long? = null
        for (i in 0 until ENTRIES) {
            val locBase = i * 4
            // Present iff the 3-byte sector offset is nonzero.
            val present = (head[locBase].toInt() and 0xFF) or
                (head[locBase + 1].toInt() and 0xFF) or
                (head[locBase + 2].toInt() and 0xFF)
            if (present == 0) continue

            val tsBase = SECTOR + i * 4
            val ts = ((head[tsBase].toLong() and 0xFF) shl 24) or
                ((head[tsBase + 1].toLong() and 0xFF) shl 16) or
                ((head[tsBase + 2].toLong() and 0xFF) shl 8) or
                (head[tsBase + 3].toLong() and 0xFF)
            if (max == null || ts > max) max = ts
        }
        return max
    }

    /**
     * Max present-chunk timestamp (epoch seconds) for region `(rx, rz)` across `region/`,
     * `entities/` and `poi/` under [dimensionFolder], or `null` when no chunk is present in
     * any of the three (a tile with zero present chunks — nothing to age out).
     */
    fun maxTimestamp(dimensionFolder: Path, rx: Int, rz: Int): Long? {
        val name = "r.$rx.$rz.mca"
        var max: Long? = null
        for (sub in SUBFOLDERS) {
            val t = maxTimestampInFile(dimensionFolder.resolve(sub).resolve(name)) ?: continue
            if (max == null || t > max) max = t
        }
        return max
    }
}
