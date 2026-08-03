package com.cobblemonroguelite.run

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtAccounter
import net.minecraft.nbt.NbtIo
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.UUID

private val log = LoggerFactory.getLogger("cobblemon_roguelite/stash")

/**
 * The file protocol under the inventory stash — nothing but bytes, paths and fsync.
 *
 * ### Why this is its own class and paranoid beyond the codebase's norm
 *
 * `docs/roguelite-run-isolation.md` §3: the moment the live inventory is cleared, the stash file is
 * **the only copy of a player's items anywhere**. `RunStore` tolerates a truncated write because a
 * lost run is survivable; this file exists because a lost inventory is not. So every write here is
 * temp-file → fsync the stream → atomic rename → fsync the directory, which is one fsync more than
 * vanilla's own playerdata protocol (`Util.safeReplaceFile` renames but never syncs — design F9),
 * and the difference is exactly power loss: rename-without-fsync orders the write against other
 * writes, not against the machine dying.
 *
 * ### Separate from [RunStore], structurally and on purpose
 *
 * Run state is deletable by design — expiry deletes it, abandon deletes it, an operator may delete
 * it — and every one of those must cost a run, never an inventory (§3's `PendingPayoutStore`
 * precedent: a stashed inventory is the strongest form of "already owed"). Nothing in this class
 * knows a run exists.
 *
 * ### Archives are renamed, never deleted
 *
 * `stash/stale/` and `stash/quarantine/` grow until an operator prunes them (design §14 Q4 left
 * retention with the human). A consumed archive is renamed to `.consumed`, not removed — the
 * op-restore protocol (§9, F3) marks-durably-then-delivers so a crash between the two *loses* rather
 * than *duplicates*, and the loss stays recoverable from the consumed file by hand.
 *
 * All paths are derived from one injected [root] so tests run the whole protocol — including
 * injected rename and sync failures via [FsOps] — against a temp directory with no Minecraft.
 */
class StashFiles(private val root: Path, private val fs: FsOps = FsOps.REAL) {

    /** The seam tests inject failures through. Production uses [REAL]; nothing else ever should. */
    interface FsOps {
        fun fsync(path: Path, isDirectory: Boolean)
        fun move(from: Path, to: Path)

        companion object {
            val REAL: FsOps = object : FsOps {
                override fun fsync(path: Path, isDirectory: Boolean) {
                    // Directories need READ; regular files WRITE. `force(true)` covers metadata, which
                    // matters for a rename's visibility after power loss on ext4.
                    val option = if (isDirectory) StandardOpenOption.READ else StandardOpenOption.WRITE
                    FileChannel.open(path, option).use { it.force(true) }
                }

                override fun move(from: Path, to: Path) {
                    Files.move(from, to, StandardCopyOption.ATOMIC_MOVE)
                }
            }
        }
    }

    private val stashDir: Path get() = root
    private val staleDir: Path get() = root.resolve("stale")
    private val quarantineDir: Path get() = root.resolve("quarantine")

    fun stashPath(player: UUID): Path = stashDir.resolve("$player.dat")

    fun exists(player: UUID): Boolean = Files.exists(stashPath(player))

    /**
     * Write [tag] as [player]'s stash, durably. Throws on any failure — the caller refuses entry
     * (design E3: on failure nothing has changed and nothing is lost), so an exception here is the
     * *correct* outcome rather than something to swallow.
     *
     * The temp file lives beside the target (same directory, same filesystem) because
     * `ATOMIC_MOVE` across filesystems is an error, and because the directory fsync that makes the
     * rename durable has to be the same directory the file landed in.
     */
    fun writeStash(player: UUID, tag: CompoundTag) {
        writeDurably(stashPath(player), tag)
    }

    /** Read [player]'s stash, or null if absent. A file that exists but fails to parse THROWS —
     *  callers must treat that as design row 3 (refuse and escalate), never as "no stash". */
    fun readStash(player: UUID): CompoundTag? {
        val path = stashPath(player)
        if (!Files.exists(path)) return null
        return NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap())
    }

    /**
     * Move [player]'s stash into `stale/` (design X5 and row 2). Archive, not delete: it is the last
     * generation of somebody's inventory. Returns the archive path, or null when there was nothing.
     */
    fun archiveStash(player: UUID, timestamp: Long): Path? {
        val from = stashPath(player)
        if (!Files.exists(from)) return null
        Files.createDirectories(staleDir)
        val to = uncollided(staleDir.resolve("$player-$timestamp.dat"))
        fs.move(from, to)
        fs.fsync(staleDir, isDirectory = true)
        fs.fsync(stashDir, isDirectory = true)
        log.info("roguelite: archived stash {} -> {}", from.fileName, to.fileName)
        return to
    }

    /**
     * Durably write quarantined stacks (design X2). Same protocol as the stash itself —
     * durable-write-before-clear applies to quarantine too, because between the write and the removal
     * these stacks exist in two places and after it in one.
     */
    fun writeQuarantine(player: UUID, timestamp: Long, tag: CompoundTag): Path {
        Files.createDirectories(quarantineDir)
        val path = uncollided(quarantineDir.resolve("$player-$timestamp.dat"))
        writeDurably(path, tag)
        return path
    }

    /** List a player's archived and quarantined files, newest first, for the op surface (§9). */
    fun listArchives(player: UUID): List<Path> = listIn(staleDir, player)
    fun listQuarantine(player: UUID): List<Path> = listIn(quarantineDir, player)

    /**
     * Mark an archive consumed — durably, BEFORE its contents are delivered (§9, F3). Rename rather
     * than a flag inside the file, so "is this consumed" is answerable from a directory listing and
     * the un-consumed name can never be re-read by a second `stash restore`.
     */
    fun markConsumed(archive: Path): Path {
        val to = archive.resolveSibling(archive.fileName.toString() + ".consumed")
        fs.move(archive, to)
        fs.fsync(archive.parent, isDirectory = true)
        return to
    }

    /**
     * Delete temp-file orphans of writes that died mid-protocol. Boot-time only. `.tmp` files are the
     * one thing here that is safe to delete unexamined: by construction they were never renamed, so
     * they were never the authoritative copy of anything.
     */
    fun sweepOrphanTemps(): Int {
        var swept = 0
        for (dir in listOf(stashDir, staleDir, quarantineDir)) {
            if (!Files.isDirectory(dir)) continue
            Files.list(dir).use { stream ->
                stream.filter { it.fileName.toString().endsWith(".tmp") }.forEach {
                    runCatching { Files.delete(it) }
                        .onSuccess { _ -> swept++ }
                        .onFailure { e -> log.warn("roguelite: could not sweep orphan temp {}", it, e) }
                }
            }
        }
        if (swept > 0) log.info("roguelite: swept {} orphan stash temp file(s)", swept)
        return swept
    }

    private fun writeDurably(target: Path, tag: CompoundTag) {
        Files.createDirectories(target.parent)
        val temp = target.resolveSibling(target.fileName.toString() + ".tmp")
        try {
            Files.newOutputStream(temp).use { out -> NbtIo.writeCompressed(tag, out) }
            fs.fsync(temp, isDirectory = false)
            fs.move(temp, target)
            fs.fsync(target.parent, isDirectory = true)
        } catch (failure: Exception) {
            // The temp is the only thing that may exist in a broken state; the target is untouched
            // (rename is atomic) or absent. Delete the temp so a later boot sweep has less to do, and
            // rethrow — the caller's refusal is the safety mechanism, not this cleanup.
            runCatching { Files.deleteIfExists(temp) }
            throw IOException("stash write to $target failed", failure)
        }
    }

    private fun listIn(dir: Path, player: UUID): List<Path> {
        if (!Files.isDirectory(dir)) return emptyList()
        return Files.list(dir).use { stream ->
            stream.filter { it.fileName.toString().startsWith(player.toString()) }
                .sorted(compareByDescending { it.fileName.toString() })
                .toList()
        }
    }

    /** Same-millisecond collisions get a numeric suffix rather than an overwrite — these directories
     *  are courts of last resort, and nothing in one may ever be replaced by anything. */
    private fun uncollided(path: Path): Path {
        if (!Files.exists(path)) return path
        var n = 1
        while (true) {
            val candidate = path.resolveSibling(path.fileName.toString().removeSuffix(".dat") + "-$n.dat")
            if (!Files.exists(candidate)) return candidate
            n++
        }
    }
}
