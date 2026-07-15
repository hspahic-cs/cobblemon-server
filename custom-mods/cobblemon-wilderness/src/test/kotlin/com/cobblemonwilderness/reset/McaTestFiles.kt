package com.cobblemonwilderness.reset

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.DeflaterOutputStream
import java.util.zip.GZIPOutputStream

/** Test helpers for fabricating Anvil `.mca` files with controlled headers (header-only, 8 KiB). */
object McaTestFiles {

    /** Anvil chunk-compression ids. */
    const val GZIP = 1
    const val ZLIB = 2
    const val UNCOMPRESSED = 3

    /** Anvil sector size in bytes. */
    private const val SECTOR = 4096

    /** One fabricated chunk: [nbt] payload written into location slot [slot] with Anvil [compression]. */
    data class ChunkSpec(
        val slot: Int,
        val nbt: ByteArray,
        val compression: Int = ZLIB,
    )

    /**
     * Serializes a minimal chunk root compound with a `structures` compound. [starts] maps a
     * structure id to the start's `id` value (`"INVALID"` for a biome-rejected start); [references]
     * lists structure ids that appear in `References` (each with one packed position). A decoy
     * `Status` string precedes `structures`, and each start carries a decoy sibling, so the reader
     * must skip past siblings to reach the tags it cares about. [includeStructures]=false omits the
     * `structures` compound entirely (the "no structures" case).
     */
    fun chunkStructuresNbt(
        starts: Map<String, String> = emptyMap(),
        references: List<String> = emptyList(),
        includeStructures: Boolean = true,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use { d ->
            d.writeByte(10); d.writeShort(0)                     // root TAG_Compound, empty name
            d.writeByte(8); d.writeUTF("Status")                 // decoy sibling before `structures`
            d.writeUTF("minecraft:full")
            if (includeStructures) {
                d.writeByte(10); d.writeUTF("structures")        // structures TAG_Compound
                d.writeByte(10); d.writeUTF("starts")            // starts TAG_Compound
                for ((sid, startId) in starts) {
                    d.writeByte(10); d.writeUTF(sid)             // start compound, keyed by structure id
                    d.writeByte(8); d.writeUTF("id"); d.writeUTF(startId)
                    d.writeByte(3); d.writeUTF("ChunkX"); d.writeInt(0) // decoy sibling in the start
                    d.writeByte(0)                              // end this start
                }
                d.writeByte(0)                                  // end starts
                d.writeByte(10); d.writeUTF("References")        // References TAG_Compound
                for (sid in references) {
                    d.writeByte(12); d.writeUTF(sid)            // TAG_Long_Array of packed positions
                    d.writeInt(1); d.writeLong(0L)
                }
                d.writeByte(0)                                  // end References
                d.writeByte(0)                                  // end structures
            }
            d.writeByte(0)                                      // end root
        }
        return out.toByteArray()
    }

    private fun compress(nbt: ByteArray, compression: Int): ByteArray = when (compression) {
        GZIP -> ByteArrayOutputStream().also { bo -> GZIPOutputStream(bo).use { it.write(nbt) } }.toByteArray()
        ZLIB -> ByteArrayOutputStream().also { bo -> DeflaterOutputStream(bo).use { it.write(nbt) } }.toByteArray()
        else -> nbt // UNCOMPRESSED
    }

    /**
     * Writes a full region file (header + chunk-data sectors) at [file]. Each [ChunkSpec] gets its
     * own 4 KiB data sector (chunks are tiny), a location-table entry pointing at it, and a
     * timestamp-table entry set to [timestamp].
     */
    fun writeRegionWithChunks(file: Path, chunks: List<ChunkSpec>, timestamp: Long = 1L) {
        val total = (2 + chunks.size) * SECTOR
        val buf = ByteArray(total)
        chunks.forEachIndexed { idx, spec ->
            val sectorOffset = 2 + idx
            // Location entry: 3-byte big-endian sector offset + 1-byte sector count.
            val loc = spec.slot * 4
            buf[loc] = ((sectorOffset ushr 16) and 0xFF).toByte()
            buf[loc + 1] = ((sectorOffset ushr 8) and 0xFF).toByte()
            buf[loc + 2] = (sectorOffset and 0xFF).toByte()
            buf[loc + 3] = 1
            // Timestamp entry (sector 1).
            val t = SECTOR + spec.slot * 4
            buf[t] = ((timestamp ushr 24) and 0xFF).toByte()
            buf[t + 1] = ((timestamp ushr 16) and 0xFF).toByte()
            buf[t + 2] = ((timestamp ushr 8) and 0xFF).toByte()
            buf[t + 3] = (timestamp and 0xFF).toByte()
            // Chunk data: 4-byte length (compression byte + payload) + 1-byte compression + payload.
            val payload = compress(spec.nbt, spec.compression)
            val length = payload.size + 1
            val dataBase = sectorOffset * SECTOR
            buf[dataBase] = ((length ushr 24) and 0xFF).toByte()
            buf[dataBase + 1] = ((length ushr 16) and 0xFF).toByte()
            buf[dataBase + 2] = ((length ushr 8) and 0xFF).toByte()
            buf[dataBase + 3] = (length and 0xFF).toByte()
            buf[dataBase + 4] = spec.compression.toByte()
            payload.copyInto(buf, dataBase + 5)
        }
        Files.createDirectories(file.parent)
        Files.write(file, buf)
    }

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
