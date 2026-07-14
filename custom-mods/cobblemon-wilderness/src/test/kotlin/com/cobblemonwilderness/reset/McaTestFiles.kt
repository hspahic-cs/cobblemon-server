package com.cobblemonwilderness.reset

import java.nio.file.Files
import java.nio.file.Path

/** Test helpers for fabricating Anvil `.mca` files with controlled headers (header-only, 8 KiB). */
object McaTestFiles {

    /**
     * Writes an 8 KiB region-file header at [file]: one present chunk per entry in
     * [chunkTimestamps] (slot i, epoch-second timestamp `chunkTimestamps[i]`), the rest absent.
     * An empty list yields an all-zero header — a region with zero present chunks.
     */
    fun writeRegion(file: Path, chunkTimestamps: List<Long>) {
        val head = ByteArray(8192)
        chunkTimestamps.forEachIndexed { i, ts ->
            val loc = i * 4
            head[loc + 2] = 2.toByte() // nonzero 3-byte sector offset → chunk present
            head[loc + 3] = 1.toByte() // sector count (unread by our decoder)
            val t = 4096 + i * 4
            head[t] = ((ts ushr 24) and 0xFF).toByte()
            head[t + 1] = ((ts ushr 16) and 0xFF).toByte()
            head[t + 2] = ((ts ushr 8) and 0xFF).toByte()
            head[t + 3] = (ts and 0xFF).toByte()
        }
        Files.createDirectories(file.parent)
        Files.write(file, head)
    }

    /** Writes the same single-chunk timestamp into region/, entities/ and poi/ for `r.rx.rz.mca`. */
    fun writeAllFolders(dimensionFolder: Path, rx: Int, rz: Int, timestamp: Long) {
        for (sub in listOf("region", "entities", "poi")) {
            writeRegion(dimensionFolder.resolve(sub).resolve("r.$rx.$rz.mca"), listOf(timestamp))
        }
    }
}
