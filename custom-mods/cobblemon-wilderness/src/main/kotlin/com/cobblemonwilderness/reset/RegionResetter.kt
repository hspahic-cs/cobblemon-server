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
    /** Kept because they lie fully inside the keep-box (geometry). */
    val keptInside: Int = 0,
    /** Kept because they straddle the box edge (overlap but not fully inside). */
    val keptStraddle: Int = 0,
    /** Kept because a present chunk references a legendary monument (frozen as-is, never regenerated). */
    val keptMonument: Int = 0,
    /** True if the circuit breaker tripped: nothing was deleted because the box looked unsafe. */
    val aborted: Boolean = false,
    /** Per-region classification for every fully-outside region (for /wildreset preview). */
    val scans: List<RegionScan> = emptyList(),
)

/** How a fully-outside region was classified. */
enum class RegionDisposition {
    /** No monument → will be reset (deleted → regenerates fresh on next visit). */
    DELETABLE,

    /** References a legendary monument → kept untouched (never regenerated; spent stays spent). */
    KEPT_MONUMENT,
}

/** One fully-outside region file and whether it is deletable or kept for a monument. */
data class RegionScan(
    val name: String,
    val rx: Int,
    val rz: Int,
    val disposition: RegionDisposition,
)

/**
 * Deletes the on-disk chunk data (region/, entities/, poi/) for every region file that lies WHOLLY
 * outside the keep-box AND holds no legendary monument, so those chunks regenerate fresh on next
 * visit. A monument region is left completely untouched — never regenerated — so its world state
 * (drained pedestals, emptied chests) is frozen exactly as players left it: spent stays spent, and
 * an unclaimed monument stays claimable. See the reset-v2 plan.
 *
 * Region-file granularity only. A region that overlaps the box at all is kept (see [BoundingBox]
 * docs for the outward-rounding bias). All deletion must happen while the target chunks are
 * guaranteed unloaded — i.e. at server boot, before levels load.
 */
object RegionResetter {

    /** The three sibling chunk-data folders that share the `r.X.Z.mca` naming scheme. */
    private val MCA_SUBFOLDERS = listOf("region", "entities", "poi")

    private const val REGION_BLOCKS = 512

    /**
     * True if region (rx, rz) — covering blocks [rx*512 .. rx*512+511] on each axis —
     * does not intersect [box] at all, and is therefore a delete candidate.
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
     * True if region (rx, rz) lies WHOLLY inside [box] (every block within the box). A region that
     * overlaps the box but is not fully inside is "straddling". Kept vs deleted is decided by
     * [isRegionFullyOutside]; this only separates the two KEEP reasons for reporting.
     *
     * Pure function: unit-tested alongside the geometry.
     */
    fun isRegionFullyInside(box: BoundingBox, rx: Int, rz: Int): Boolean {
        val b = box.normalized()
        val regionMinX = rx * REGION_BLOCKS
        val regionMaxX = regionMinX + REGION_BLOCKS - 1
        val regionMinZ = rz * REGION_BLOCKS
        val regionMaxZ = regionMinZ + REGION_BLOCKS - 1
        return regionMinX >= b.minX && regionMaxX <= b.maxX &&
            regionMinZ >= b.minZ && regionMaxZ <= b.maxZ
    }

    /**
     * Safety breaker (rescoped): true if [box] is too small to be a sane keep-zone — collapsed
     * toward a point or sliver so that "outside" would engulf spawn and the intended build area.
     * Either side (X or Z) shorter than [minSideBlocks] fails the check. This is the one
     * catastrophic misconfig a blanket outside-wipe must guard against; it replaces the old
     * delete-fraction gate, which now trips on every normal cycle (full-outside deletion is normal).
     *
     * Pure function: unit-tested alongside the geometry.
     */
    fun isBoxDegenerate(box: BoundingBox, minSideBlocks: Int): Boolean {
        val b = box.normalized()
        val sideX = b.maxX.toLong() - b.minX.toLong() + 1
        val sideZ = b.maxZ.toLong() - b.minZ.toLong() + 1
        return sideX < minSideBlocks || sideZ < minSideBlocks
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
     * Scans [dimensionFolder]'s region/ folder and deletes (or, when [dryRun], merely tallies) every
     * fully-outside region that does NOT reference a legendary monument, across all three chunk-data
     * subfolders. Regions overlapping the box are kept outright (geometry, no NBT read); a fully-
     * outside region is monument-checked ([McaTimestampReader.regionHasMonument]) and kept if it holds
     * one — never regenerated, so its world state stays frozen.
     *
     * The safety breaker is a degenerate-box guard ([isBoxDegenerate]): a collapsed/sliver keep-box
     * would make "outside" swallow spawn and builds, so the run aborts and deletes nothing. It never
     * gates on delete fraction — a full-outside wipe is normal operation now.
     *
     * When [backupTarget] is non-null and this is a real (non-[dryRun]) run, each to-be-deleted file
     * is MOVED into [backupTarget]/<sub>/<name> instead of being unlinked. The move is the deletion —
     * the chunk is gone from world/ and regenerates fresh — and it leaves a restore copy.
     *
     * [forced] bypasses ONLY the degenerate-box guard for a supervised override; it changes nothing
     * else (dryRun still only tallies).
     */
    fun run(
        dimensionId: String,
        dimensionFolder: Path,
        box: BoundingBox,
        dryRun: Boolean,
        minBoxSideBlocks: Int,
        backupTarget: Path?,
        log: Logger,
        forced: Boolean = false,
    ): ResetReport {
        val regionDir = dimensionFolder.resolve("region")
        if (!regionDir.exists()) {
            log.warn("[{}] no region/ folder at {} — nothing to do", dimensionId, regionDir)
            return ResetReport(dimensionId, 0, 0, 0, dryRun)
        }

        // Safety breaker (rescoped): fail CLOSED on a degenerate/collapsed keep-box — the one
        // catastrophic misconfig, where "outside" would engulf spawn/builds. Checked up front (box
        // only), before any scan, so nothing is deleted. forceBreakerOverride is the escape hatch.
        if (isBoxDegenerate(box, minBoxSideBlocks)) {
            if (!forced) {
                log.error(
                    "[{}] CIRCUIT BREAKER: keep-box X[{}..{}] Z[{}..{}] is degenerate (a side < {} " +
                        "blocks) — 'outside' would engulf spawn/builds. Aborting; nothing was deleted.",
                    dimensionId, box.minX, box.maxX, box.minZ, box.maxZ, minBoxSideBlocks,
                )
                return ResetReport(dimensionId, 0, 0, 0, dryRun, aborted = true)
            }
            log.warn(
                "[{}] CIRCUIT BREAKER OVERRIDDEN (forced): keep-box is degenerate (a side < {} blocks), " +
                    "but /wildreset now force was set. Proceeding.",
                dimensionId, minBoxSideBlocks,
            )
        }

        // Pass 1 — classify. Inside/straddling regions are kept by geometry (no NBT read). Each
        // fully-outside region is monument-checked: a monument reference keeps the whole region.
        var keptInside = 0
        var keptStraddle = 0
        val outside = ArrayList<RegionScan>()
        Files.list(regionDir).use { stream ->
            for (regionFile in stream) {
                val (rx, rz) = parseRegionCoords(regionFile.name) ?: continue
                if (!isRegionFullyOutside(box, rx, rz)) {
                    if (isRegionFullyInside(box, rx, rz)) keptInside++ else keptStraddle++
                    continue
                }
                val disposition = if (McaTimestampReader.regionHasMonument(regionFile)) {
                    RegionDisposition.KEPT_MONUMENT
                } else {
                    RegionDisposition.DELETABLE
                }
                outside.add(RegionScan(regionFile.name, rx, rz, disposition))
            }
        }

        val keptMonument = outside.count { it.disposition == RegionDisposition.KEPT_MONUMENT }
        val toDelete = outside.filter { it.disposition == RegionDisposition.DELETABLE }.map { it.name }
        val total = keptInside + keptStraddle + outside.size
        val kept = total - toDelete.size

        // Pass 2 — remove (or tally) the matching r.X.Z.mca in region/, entities/, poi/. With a
        // backupTarget on a real run we MOVE each file into the snapshot; otherwise we unlink it.
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
            "[{}] {} {} region(s), kept {} (inside {}, straddle {}, monument {}), {} {} MB ({})",
            dimensionId, verb, toDelete.size, kept, keptInside, keptStraddle, keptMonument,
            verb, bytesFreed / (1024 * 1024), regionDir,
        )
        if (!dryRun && backupTarget != null) {
            log.info("[{}] snapshotted {} file(s) to {} before removal", dimensionId, filesBackedUp, backupTarget)
        }
        return ResetReport(
            dimensionId, kept, toDelete.size, bytesFreed, dryRun,
            keptInside = keptInside, keptStraddle = keptStraddle, keptMonument = keptMonument,
            scans = outside,
        )
    }
}
