package com.cobblemonwilderness.reset

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.file.Path
import java.util.zip.GZIPInputStream
import java.util.zip.InflaterInputStream
import kotlin.io.path.exists

/**
 * Reads legendary-monument presence for a region tile straight out of the Anvil `.mca` chunk data.
 *
 * An Anvil region file begins with a 4 KiB location table (1024 entries, 4 bytes each): the first
 * 3 bytes are the chunk's data offset in 4 KiB sectors, and a **nonzero** offset means the chunk is
 * present. Each present chunk's data is a `<4-byte length><1-byte compression><compressed NBT>`
 * record; the NBT carries the chunk's `structures` compound, which we walk to detect a legendary
 * monument (see [regionHasMonument]).
 *
 * (Historically this object also decoded the timestamp table for an mtime/idle freshness signal; the
 * v2 blanket-wipe replaced that with monument detection, so only the chunk-NBT walk remains. The name
 * is kept to preserve references.)
 */
object McaTimestampReader {

    /** Anvil header/sector size in bytes. */
    private const val SECTOR = 4096
    /** Chunks indexed per region file (32 × 32). */
    private const val ENTRIES = 1024

    // ---- Monument detection (chunk `structures` compound) ----
    //
    // Content-based carve-out for the blanket outside-wipe: an outside region is KEPT (frozen as-is,
    // never regenerated) iff any of its present chunks references a `legendarymonuments:*` structure.
    // This reuses the Anvil chunk-NBT decompression + tag-walk from the (removed) occupancy signal,
    // repointed from `InhabitedTime` to the `structures` compound: a chunk's `structures.starts`
    // carries the structure that STARTS in that chunk (its origin), and `structures.References`
    // carries the packed positions of chunks whose footprint STRADDLES into a start elsewhere — so
    // reading both covers a monument whose footprint spans a region boundary.
    //
    // Fail direction matches the occupancy reader: a missing/short file, an unhandled compression, or
    // malformed NBT counts as "no monument" (false → wiped). Vanilla writes zlib, so a corrupt
    // monument region being wiped is acceptable; a per-chunk exception is scoped so one bad chunk
    // never abandons the region scan, and a "no monument" verdict only returns after every present
    // chunk has been scanned (no sampling — never wipe a monument).

    /** Namespace of the legendary-monuments structure set; a match on any structure id keeps the region. */
    private const val LM_NAMESPACE = "legendarymonuments"

    /** NBT tag type ids we need to walk the chunk compound (see the NBT spec). */
    private const val TAG_END = 0
    private const val TAG_BYTE = 1
    private const val TAG_SHORT = 2
    private const val TAG_INT = 3
    private const val TAG_LONG = 4
    private const val TAG_FLOAT = 5
    private const val TAG_DOUBLE = 6
    private const val TAG_BYTE_ARRAY = 7
    private const val TAG_STRING = 8
    private const val TAG_LIST = 9
    private const val TAG_COMPOUND = 10
    private const val TAG_INT_ARRAY = 11
    private const val TAG_LONG_ARRAY = 12

    /**
     * True if the `region/` file [regionFile] has ≥1 present chunk that references a
     * `legendarymonuments:*` structure (via its `structures.starts` or `structures.References`).
     *
     * Scans the 4 KiB location table; for each present chunk it seeks to that chunk's sector, reads
     * the `<length><compression>` prefix, decompresses the NBT (gzip / zlib / uncompressed per the
     * 1-byte compression id), and checks the `structures` compound. **Early-exits** on the first
     * monument reference (keep). A chunk we can't decompress/parse contributes "no monument" and the
     * scan continues; a region with none is scanned in full before returning false. A missing/short
     * file returns false (nothing to keep → wiped).
     */
    fun regionHasMonument(regionFile: Path): Boolean {
        if (!regionFile.exists()) return false
        return try {
            RandomAccessFile(regionFile.toFile(), "r").use { raf ->
                val fileLen = raf.length()
                if (fileLen < SECTOR) return false
                val loc = ByteArray(SECTOR)
                raf.seek(0)
                raf.readFully(loc)
                for (i in 0 until ENTRIES) {
                    val base = i * 4
                    val sectorOffset = ((loc[base].toInt() and 0xFF) shl 16) or
                        ((loc[base + 1].toInt() and 0xFF) shl 8) or
                        (loc[base + 2].toInt() and 0xFF)
                    if (sectorOffset == 0) continue // chunk absent
                    // Per-chunk guard: a chunk we can't read/parse contributes "no monument" and we
                    // keep scanning the rest — one bad chunk must not abandon the region scan (that
                    // would risk a "wipe" verdict on a region whose monument sits in a later chunk).
                    val hit = try {
                        val nbt = readChunkNbt(raf, sectorOffset.toLong(), fileLen)
                        nbt != null && chunkReferencesMonument(nbt)
                    } catch (e: Exception) {
                        false
                    }
                    if (hit) return true // monument → keep, no need to scan on
                }
                false
            }
        } catch (e: IOException) {
            false
        }
    }

    /**
     * Reads and decompresses one chunk's NBT from [raf], where the chunk data begins at
     * [sectorOffset] × 4 KiB. Returns null if the record is truncated, has a bad length, or uses a
     * compression we don't handle — callers treat null as "no monument".
     */
    private fun readChunkNbt(raf: RandomAccessFile, sectorOffset: Long, fileLen: Long): ByteArray? {
        val start = sectorOffset * SECTOR
        if (start + 5 > fileLen) return null
        raf.seek(start)
        val length = raf.readInt() // big-endian: bytes of (compression byte + compressed NBT)
        if (length <= 1) return null
        val compression = raf.readByte().toInt() and 0xFF
        val dataLen = length - 1
        if (dataLen <= 0 || start + 5L + dataLen > fileLen) return null
        val compressed = ByteArray(dataLen)
        raf.readFully(compressed)
        return decompress(compressed, compression)
    }

    /** Decompresses chunk NBT per the Anvil 1-byte compression id (1=gzip, 2=zlib, 3=none); null otherwise. */
    private fun decompress(data: ByteArray, compression: Int): ByteArray? {
        return try {
            when (compression) {
                1 -> GZIPInputStream(ByteArrayInputStream(data)).use { it.readBytes() }
                2 -> InflaterInputStream(ByteArrayInputStream(data)).use { it.readBytes() }
                3 -> data
                else -> null // 4 = LZ4 and any future id: unhandled → treated as "no monument"
            }
        } catch (e: IOException) {
            null
        }
    }

    /**
     * True if a decompressed chunk NBT references a `legendarymonuments:*` structure. Walks the root
     * compound to its `structures` child and scans the `starts` + `References` sub-compounds. Returns
     * false on any malformed structure rather than throwing.
     */
    fun chunkReferencesMonument(nbt: ByteArray): Boolean {
        return try {
            val buf = ByteBuffer.wrap(nbt) // NBT is big-endian, ByteBuffer's default
            if ((buf.get().toInt() and 0xFF) != TAG_COMPOUND) return false
            skipString(buf) // root compound's (usually empty) name
            while (true) {
                val type = buf.get().toInt() and 0xFF
                if (type == TAG_END) break
                val name = readString(buf)
                if (type == TAG_COMPOUND && (name == "structures" || name == "Structures")) {
                    // Only one `structures` compound per chunk — its verdict is final.
                    return scanStructuresCompound(buf)
                }
                skipPayload(buf, type)
            }
            false
        } catch (e: RuntimeException) {
            // BufferUnderflow / bad index on malformed NBT → unreadable, treat as "no monument".
            false
        }
    }

    /** Scans the `structures` compound's `starts` and `References` children for an LM reference. */
    private fun scanStructuresCompound(buf: ByteBuffer): Boolean {
        while (true) {
            val type = buf.get().toInt() and 0xFF
            if (type == TAG_END) break
            val name = readString(buf)
            when {
                type == TAG_COMPOUND && (name == "starts" || name == "Starts") ->
                    if (scanStartsCompound(buf)) return true
                type == TAG_COMPOUND && (name == "References" || name == "references") ->
                    if (scanReferencesCompound(buf)) return true
                else -> skipPayload(buf, type)
            }
        }
        return false
    }

    /**
     * Scans `structures.starts` — entries keyed by structure id, each a compound describing the start.
     * A monument-namespaced key counts only when its `id` is not `INVALID` (a failed/biome-rejected
     * placement writes an `INVALID` start), so we keep only monuments that actually generated.
     */
    private fun scanStartsCompound(buf: ByteBuffer): Boolean {
        while (true) {
            val type = buf.get().toInt() and 0xFF
            if (type == TAG_END) break
            val name = readString(buf)
            if (type == TAG_COMPOUND && isMonumentId(name)) {
                val id = readStartId(buf) // consumes the whole start compound
                if (id != null && !id.equals("INVALID", ignoreCase = true)) return true
            } else {
                skipPayload(buf, type)
            }
        }
        return false
    }

    /**
     * Scans `structures.References` — entries keyed by structure id, each a long-array of packed
     * chunk positions that reference that structure's start. A non-empty monument-namespaced entry
     * means this chunk sits in a monument's footprint (start is in this or a neighbouring region).
     */
    private fun scanReferencesCompound(buf: ByteBuffer): Boolean {
        while (true) {
            val type = buf.get().toInt() and 0xFF
            if (type == TAG_END) break
            val name = readString(buf)
            if (type == TAG_LONG_ARRAY && isMonumentId(name)) {
                val n = buf.int
                skip(buf, if (n > 0) n * 8 else 0) // consume the array either way
                if (n > 0) return true
            } else {
                skipPayload(buf, type)
            }
        }
        return false
    }

    /** Reads the `id` string of one start compound, fully consuming it. Null if the tag is absent. */
    private fun readStartId(buf: ByteBuffer): String? {
        var id: String? = null
        while (true) {
            val type = buf.get().toInt() and 0xFF
            if (type == TAG_END) break
            val name = readString(buf)
            if (type == TAG_STRING && name == "id") {
                id = readString(buf)
            } else {
                skipPayload(buf, type)
            }
        }
        return id
    }

    /** True if a structure id string is in the `legendarymonuments` namespace. */
    private fun isMonumentId(name: String): Boolean = name.substringBefore(':', "") == LM_NAMESPACE

    /** Advances past one tag's payload of [type], recursing through lists and compounds. */
    private fun skipPayload(buf: ByteBuffer, type: Int) {
        when (type) {
            TAG_BYTE -> skip(buf, 1)
            TAG_SHORT -> skip(buf, 2)
            TAG_INT, TAG_FLOAT -> skip(buf, 4)
            TAG_LONG, TAG_DOUBLE -> skip(buf, 8)
            TAG_BYTE_ARRAY -> skip(buf, buf.int)
            TAG_STRING -> skipString(buf)
            TAG_LIST -> {
                val elemType = buf.get().toInt() and 0xFF
                val n = buf.int
                repeat(if (n > 0) n else 0) { skipPayload(buf, elemType) }
            }
            TAG_COMPOUND -> {
                while (true) {
                    val t = buf.get().toInt() and 0xFF
                    if (t == TAG_END) break
                    skipString(buf) // entry name
                    skipPayload(buf, t)
                }
            }
            TAG_INT_ARRAY -> skip(buf, buf.int * 4)
            TAG_LONG_ARRAY -> skip(buf, buf.int * 8)
            else -> throw IllegalStateException("unknown NBT tag id $type")
        }
    }

    private fun readString(buf: ByteBuffer): String {
        val n = buf.short.toInt() and 0xFFFF
        val bytes = ByteArray(n)
        buf.get(bytes)
        return String(bytes, Charsets.UTF_8) // tag names are ASCII, so modified-UTF-8 is moot here
    }

    private fun skipString(buf: ByteBuffer) = skip(buf, buf.short.toInt() and 0xFFFF)

    private fun skip(buf: ByteBuffer, n: Int) {
        buf.position(buf.position() + n)
    }
}
