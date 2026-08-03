package com.cobblemonroguelite.run

import com.cobblemonroguelite.arena.RunArenas
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("cobblemon_roguelite/stash")

/**
 * Applies [StashReconcile]'s verdicts — the glue between the pure table and the two swap engines.
 *
 * ### Why the applier is separate from both
 *
 * [StashReconcile] must stay a pure function (it is the design's highest-value headless test
 * surface), and [RunInventoryStash] must stay ignorant of parties, arenas and messages (it is the
 * durability engine, and everything it does not know about is something that cannot corrupt its
 * protocol). What is left — gather four facts, decide, execute, say so — is this file, and it is
 * deliberately boring: every interesting decision already happened in one of the other two.
 */
object RunIsolation {

    /**
     * Run the §6 table for [player] and execute the verdict. Called on login (after
     * `reconcileOnLogin`'s eject), on respawn, and by the displacement poll.
     */
    fun reconcile(player: ServerPlayer, run: RunState?) {
        val files = RunInventoryStash.files(player.server)
        val tagged = RunInventoryStash.isTagged(player)
        val fileState = fileStateFor(player, files, tagged)
        val verdict = StashReconcile.decide(
            tagged = tagged,
            file = fileState,
            hasRun = run != null,
            inArena = RunArenas.isInArena(player),
        )

        when (verdict) {
            StashReconcile.Verdict.Nothing, StashReconcile.Verdict.SweepRunPokemon -> {
                // Row 1 and row 7 share an action on the party side: the sweep is idempotent and is
                // what un-strands run Pokémon a crash left in the real party or PC. On a clean login
                // it does nothing, which is the property that keeps it safe to run every time.
                runCatching { RunPartySwap.restore(player) }
                    .onFailure { log.error("roguelite: login party sweep failed for {}", player.gameProfile.name, it) }
            }

            is StashReconcile.Verdict.ArchiveStale -> {
                files.archiveStash(player.uuid, System.currentTimeMillis())
                log.warn("roguelite: archived a stale stash for {} (row 2)", player.gameProfile.name)
                runCatching { RunPartySwap.restore(player) }
                    .onFailure { log.error("roguelite: login party sweep failed for {}", player.gameProfile.name, it) }
            }

            is StashReconcile.Verdict.Alarm -> {
                if (verdict.archiveMismatched) files.archiveStash(player.uuid, System.currentTimeMillis())
                val swapId = RunInventoryStash.tagOf(player)?.toString() ?: "<unreadable>"
                log.error(
                    "roguelite: {} carries stash tag {} with no matching file — row 3, nothing touched",
                    player.gameProfile.name, swapId,
                )
                player.sendSystemMessage(RunMessages.stashAlarm(swapId))
                // The party sweep is still safe (it never touches the stash), and a player locked out
                // of runs by row 3 should at least be holding their own Pokémon.
                runCatching { RunPartySwap.restore(player) }
                    .onFailure { log.error("roguelite: login party sweep failed for {}", player.gameProfile.name, it) }
            }

            is StashReconcile.Verdict.FinishExit -> {
                val result = runCatching {
                    RunInventoryStash.exitSwap(player, if (verdict.captureRunBag) run else null)
                }.getOrElse {
                    log.error("roguelite: reconcile exit swap failed for {}", player.gameProfile.name, it)
                    RunInventoryStash.ExitResult.RolledBack
                }
                announceExit(player, result)
                runCatching { RunPartySwap.restore(player) }
                    .onFailure { log.error("roguelite: reconcile party restore failed for {}", player.gameProfile.name, it) }
            }

            StashReconcile.Verdict.EjectThenReconcile -> {
                // Login has already ejected before this runs, so reaching here means the respawn or
                // displacement path found a tagged player inside. Eject, then decide again from the
                // new position — the recursion terminates because inArena is now false.
                RunArenas.exit(player.server, player, run)
                reconcile(player, run)
            }

            StashReconcile.Verdict.EjectForeigner -> {
                // Row 8: a tagless player in arena space — a /tpahere mule or an op teleport. Their
                // own property is untouched; run-marked stacks they carry are voided on sight
                // (marker-keyed, the one permitted deletion).
                var voided = 0
                for (slot in 0 until player.inventory.containerSize) {
                    if (RunItems.isRunItem(player.inventory.getItem(slot))) {
                        player.inventory.setItem(slot, ItemStack.EMPTY)
                        voided++
                    }
                }
                if (voided > 0) {
                    log.warn(
                        "roguelite: voided {} run stack(s) from {}, who was in arena space with no tag (row 8)",
                        voided, player.gameProfile.name,
                    )
                }
                RunArenas.exit(player.server, player, null)
            }
        }
    }

    /** The player-facing accounting for any exit swap, shared by every door so the words match. */
    fun announceExit(player: ServerPlayer, result: RunInventoryStash.ExitResult) {
        when (result) {
            is RunInventoryStash.ExitResult.Ok -> {
                player.sendSystemMessage(RunMessages.stashReturned(result.returned))
                if (result.residue > 0) player.sendSystemMessage(RunMessages.stashResidue(result.residue))
                if (result.quarantined > 0) player.sendSystemMessage(RunMessages.stashQuarantined(result.quarantined))
            }

            RunInventoryStash.ExitResult.RolledBack ->
                player.sendSystemMessage(RunMessages.stashRolledBack())

            RunInventoryStash.ExitResult.Alarm ->
                player.sendSystemMessage(
                    RunMessages.stashAlarm(RunInventoryStash.tagOf(player)?.toString() ?: "<unreadable>"),
                )

            RunInventoryStash.ExitResult.NothingToDo -> Unit
        }
    }

    private fun fileStateFor(
        player: ServerPlayer,
        files: StashFiles,
        tagged: Boolean,
    ): StashReconcile.FileState {
        if (!files.exists(player.uuid)) return StashReconcile.FileState.ABSENT
        val snapshot = runCatching { files.readStash(player.uuid) }.getOrElse {
            return StashReconcile.FileState.UNREADABLE
        } ?: return StashReconcile.FileState.ABSENT
        if (!tagged) return StashReconcile.FileState.MATCHING // untagged: any file is row-2 stale anyway
        val promised = RunInventoryStash.tagOf(player)?.toString()
        val actual = snapshot.getCompound("header").getString("swapId")
        return if (promised == actual) StashReconcile.FileState.MATCHING else StashReconcile.FileState.MISMATCHED
    }
}
