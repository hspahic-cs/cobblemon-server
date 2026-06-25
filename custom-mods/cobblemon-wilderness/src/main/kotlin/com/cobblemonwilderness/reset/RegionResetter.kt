package com.cobblemonwilderness.reset

import com.cobblemonwilderness.config.BoundingBox
import org.slf4j.Logger
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.fileSize
import kotlin.io.path.name

/** Outcome of a scan/reset pass over one dimension's region files. */
data class ResetReport(
    val dimension: String,
    val regionsKept: Int,
    val regionsDeleted: Int,
    val bytesFreed: Long,
    val dryRun: Boolean,
    /** True if the circuit breaker tripped: nothing was deleted because the run was too broad. */
    val aborted: Boolean = false,
)

/**
 * Deletes the on-disk chunk data (region/, entities/, poi/) for every region file that
 * lies WHOLLY outside the keep-box, so those chunks regenerate fresh on next visit.
 *
 * Region-file granularity only. A region that overlaps the box at all is kept (see
 * [BoundingBox] docs for the outward-rounding bias). All deletion must happen while the
 * target chunks are guaranteed unloaded — i.e. at server boot, before levels load.
 */
object RegionResetter {

    /** The three sibling chunk-data folders that share the `r.X.Z.mca` naming scheme. */
    private val MCA_SUBFOLDERS = listOf("region", "entities", "poi")

    private const val REGION_BLOCKS = 512

    /**
     * True if region (rx, rz) — covering blocks [rx*512 .. rx*512+511] on each axis —
     * does not intersect [box] at all, and is therefore safe to delete.
     *
     * Pure function: the unit tests pin the boundary behaviour here.
     */
    fun isRegionFullyOutside(box: BoundingBox, rx: Int, rz: Int): Boolean {
        val b = box.normalized()
        val regionMinX = rx * REGION_BLOCKS
        val regionMaxX = regionMinX + REGION_BLOCKS - 1
        val regionMinZ = rz * REGION_BLOCKS
        val regionMaxZ = regionMinZ + REGION_BLOCKS - 1
        val overlaps = regionMaxX >= b.minX && regionMinX <= b.maxX &&
            regionMaxZ >= b.minZ && regionMinZ <= b.maxZ
        return !overlaps
    }

    /**
     * Circuit breaker: true if deleting [deletable] of [total] regions exceeds [maxFraction].
     * Guards against a fat-fingered box (e.g. collapsed to a point → ~100% deletable) wiping
     * the world. A maxFraction >= 1.0 disables the check.
     *
     * Pure function: unit-tested alongside the geometry.
     */
    fun exceedsLimit(deletable: Int, total: Int, maxFraction: Double): Boolean {
        if (maxFraction >= 1.0 || total <= 0) return false
        return deletable.toDouble() / total.toDouble() > maxFraction
    }

    /** Parses region coords from an `r.X.Z.mca` filename, or null if it doesn't match. */
    fun parseRegionCoords(fileName: String): Pair<Int, Int>? {
        val parts = fileName.split('.')
        if (parts.size != 4 || parts[0] != "r" || parts[3] != "mca") return null
        val x = parts[1].toIntOrNull() ?: return null
        val z = parts[2].toIntOrNull() ?: return null
        return x to z
    }

    /**
     * Scans [dimensionFolder]'s region/ folder and deletes (or, when [dryRun], merely
     * tallies) every fully-outside region across all three chunk-data subfolders.
     *
     * Two passes: first classify every region (so we know the delete fraction), then — only
     * if the [maxDeleteFraction] circuit breaker is satisfied — perform the deletions.
     *
     * When [backupTarget] is non-null and this is a real (non-[dryRun]) run, each to-be-deleted
     * file is MOVED into [backupTarget]/<sub>/<name> instead of being unlinked. The move is the
     * deletion — the chunk is gone from world/ and regenerates fresh — and it leaves a restore
     * copy. On the same filesystem the move is an instant rename (no extra disk); across
     * filesystems it falls back to copy+delete.
     */
    fun run(
        dimensionId: String,
        dimensionFolder: Path,
        box: BoundingBox,
        dryRun: Boolean,
        maxDeleteFraction: Double,
        backupTarget: Path?,
        log: Logger,
    ): ResetReport {
        val regionDir = dimensionFolder.resolve("region")
        if (!regionDir.exists()) {
            log.warn("[{}] no region/ folder at {} — nothing to do", dimensionId, regionDir)
            return ResetReport(dimensionId, 0, 0, 0, dryRun)
        }

        // Pass 1 — classify. Collect the names of fully-outside regions; count the rest.
        val toDelete = ArrayList<String>()
        var kept = 0
        Files.list(regionDir).use { stream ->
            for (regionFile in stream) {
                val (rx, rz) = parseRegionCoords(regionFile.name) ?: continue
                if (isRegionFullyOutside(box, rx, rz)) toDelete.add(regionFile.name) else kept++
            }
        }

        val total = kept + toDelete.size
        if (exceedsLimit(toDelete.size, total, maxDeleteFraction)) {
            val pct = if (total > 0) toDelete.size * 100 / total else 0
            log.error(
                "[{}] CIRCUIT BREAKER: would delete {} of {} region(s) ({}%) > limit {}%. " +
                    "Aborting — check the box config. Nothing was deleted.",
                dimensionId, toDelete.size, total, pct, (maxDeleteFraction * 100).toInt(),
            )
            return ResetReport(dimensionId, kept, toDelete.size, 0, dryRun, aborted = true)
        }

        // Pass 2 — remove (or tally) the matching r.X.Z.mca in region/, entities/, poi/. With a
        // backupTarget on a real run we MOVE each file into the snapshot, which both preserves a
        // restore copy and clears it from world/; otherwise we unlink it.
        var bytesFreed = 0L
        var filesBackedUp = 0
        for (name in toDelete) {
            for (sub in MCA_SUBFOLDERS) {
                val target = dimensionFolder.resolve(sub).resolve(name)
                if (!target.exists()) continue
                val size = runCatching { target.fileSize() }.getOrDefault(0L)
                if (dryRun) {
                    bytesFreed += size
                    continue
                }
                try {
                    if (backupTarget != null) {
                        val dest = backupTarget.resolve(sub).resolve(name)
                        Files.createDirectories(dest.parent)
                        Files.move(target, dest, StandardCopyOption.REPLACE_EXISTING)
                        filesBackedUp++
                    } else {
                        target.deleteIfExists()
                    }
                    bytesFreed += size
                } catch (e: Exception) {
                    // Fail-safe: a failed move/delete leaves the file in place rather than losing it.
                    log.warn("[{}] failed to remove {}: {}", dimensionId, target, e.message)
                }
            }
        }

        val verb = if (dryRun) "would delete" else "deleted"
        log.info(
            "[{}] {} {} region(s), kept {}, {} {} MB ({})",
            dimensionId, verb, toDelete.size, kept, verb, bytesFreed / (1024 * 1024), regionDir,
        )
        if (!dryRun && backupTarget != null) {
            log.info("[{}] snapshotted {} file(s) to {} before removal", dimensionId, filesBackedUp, backupTarget)
        }
        return ResetReport(dimensionId, kept, toDelete.size, bytesFreed, dryRun)
    }
}
