package com.cobblemonroguelite.run

import net.minecraft.nbt.CompoundTag
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import java.util.UUID

/**
 * The stash file protocol, including the failures the design says must be injected rather than hoped
 * about (`docs/roguelite-run-isolation.md` §13: "the file protocol against a temp dir with injected
 * fsync/rename failures").
 *
 * What cannot be tested here and is deliberately not faked: real power loss. fsync-then-rename is
 * the correct *sequence*; whether the deployment filesystem honours fsync is §12.6's live check.
 */
class StashFilesTest {

    private val root: Path = Files.createTempDirectory("stash-test")
    private val player: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")

    @AfterTest
    fun cleanup() {
        root.toFile().deleteRecursively()
    }

    private fun tag(marker: String = "x"): CompoundTag = CompoundTag().apply { putString("marker", marker) }

    /** An FsOps that fails on demand, for the crash-window cases. */
    private class Failing(var failFsync: Boolean = false, var failMove: Boolean = false) : StashFiles.FsOps {
        override fun fsync(path: Path, isDirectory: Boolean) {
            if (failFsync) throw IOException("injected fsync failure")
        }

        override fun move(from: Path, to: Path) {
            if (failMove) throw IOException("injected move failure")
            StashFiles.FsOps.REAL.move(from, to)
        }
    }

    @Test
    fun `a stash round-trips`() {
        val files = StashFiles(root)
        files.writeStash(player, tag("inventory"))
        assertEquals("inventory", assertNotNull(files.readStash(player)).getString("marker"))
    }

    @Test
    fun `no stash reads as null, not as an empty inventory`() {
        assertNull(StashFiles(root).readStash(player))
    }

    @Test
    fun `a failed write leaves no target and no temp`() {
        // The E3 contract: on any failure nothing has changed and nothing is lost — and nothing is
        // left lying around either, because a stray .tmp is what the boot sweep exists to kill.
        val files = StashFiles(root, Failing(failFsync = true))
        assertFailsWith<IOException> { files.writeStash(player, tag()) }
        assertTrue(!files.exists(player), "a failed write must not produce a stash")
        Files.list(root).use { stream ->
            assertEquals(0, stream.count(), "a failed write left something behind")
        }
    }

    @Test
    fun `a failed rename does not clobber an existing stash`() {
        // Overwrite is only legal through the atomic rename; if the rename itself fails, whatever was
        // there before is still whole.
        val files = StashFiles(root)
        files.writeStash(player, tag("original"))

        val broken = StashFiles(root, Failing(failMove = true))
        assertFailsWith<IOException> { broken.writeStash(player, tag("replacement")) }
        assertEquals("original", assertNotNull(files.readStash(player)).getString("marker"))
    }

    @Test
    fun `archiving moves the file into stale and empties the stash slot`() {
        val files = StashFiles(root)
        files.writeStash(player, tag("kept"))
        val archived = assertNotNull(files.archiveStash(player, timestamp = 1234))

        assertTrue(!files.exists(player))
        assertTrue(archived.exists())
        assertTrue(archived.name.startsWith("$player-1234"), archived.name)
        assertEquals(listOf(archived), files.listArchives(player))
    }

    @Test
    fun `archiving nothing returns null rather than inventing a file`() {
        assertNull(StashFiles(root).archiveStash(player, timestamp = 1))
    }

    @Test
    fun `archives never overwrite each other, even in the same millisecond`() {
        // These directories are courts of last resort; a same-timestamp collision must suffix, not
        // replace — a replaced archive is somebody's inventory gone.
        val files = StashFiles(root)
        files.writeStash(player, tag("first"))
        files.archiveStash(player, timestamp = 99)
        files.writeStash(player, tag("second"))
        files.archiveStash(player, timestamp = 99)

        assertEquals(2, files.listArchives(player).size)
    }

    @Test
    fun `quarantine writes durably and lists newest first`() {
        val files = StashFiles(root)
        files.writeQuarantine(player, timestamp = 1, tag = tag("older"))
        files.writeQuarantine(player, timestamp = 2, tag = tag("newer"))

        val listed = files.listQuarantine(player)
        assertEquals(2, listed.size)
        assertTrue(listed[0].name > listed[1].name, "expected newest first: $listed")
    }

    @Test
    fun `a consumed archive changes name so it cannot be restored twice`() {
        // The F3 dupe guard: consumption is a durable rename BEFORE delivery, so a second
        // `stash restore` cannot find the un-consumed name.
        val files = StashFiles(root)
        files.writeStash(player, tag())
        val archive = assertNotNull(files.archiveStash(player, timestamp = 5))

        val consumed = files.markConsumed(archive)
        assertTrue(!archive.exists(), "the original archive name must be gone")
        assertTrue(consumed.name.endsWith(".consumed"))
        // Still on disk: consumption loses availability, never bytes — an op can recover it by hand.
        assertTrue(consumed.exists())
    }

    @Test
    fun `the boot sweep deletes only temp orphans`() {
        val files = StashFiles(root)
        files.writeStash(player, tag("real"))
        Files.createDirectories(root.resolve("stale"))
        Files.writeString(root.resolve("$player.dat.tmp"), "orphan")
        Files.writeString(root.resolve("stale").resolve("other.dat.tmp"), "orphan")

        assertEquals(2, files.sweepOrphanTemps())
        assertNotNull(files.readStash(player), "the sweep must never touch a real stash")
    }
}
