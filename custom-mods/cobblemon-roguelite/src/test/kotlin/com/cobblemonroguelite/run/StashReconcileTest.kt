package com.cobblemonroguelite.run

import com.cobblemonroguelite.run.StashReconcile.FileState
import com.cobblemonroguelite.run.StashReconcile.Verdict
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The §6 reconcile table (`docs/roguelite-run-isolation.md`), input tuple by input tuple.
 *
 * Every crash scenario in the design's edge-case catalogue is one row here. The tests are grouped by
 * the failure they encode rather than by verdict, so a reader can find "what happens if the operator
 * deletes X" by name.
 */
class StashReconcileTest {

    private fun decide(
        tagged: Boolean = false,
        file: FileState = FileState.ABSENT,
        hasRun: Boolean = false,
        inArena: Boolean = false,
    ) = StashReconcile.decide(tagged, file, hasRun, inArena)

    @Test
    fun `row 1 - the thousand-a-day login does nothing`() {
        assertEquals(Verdict.Nothing, decide())
    }

    @Test
    fun `row 2 - a file without a tag is archived, never restored`() {
        // Two indistinguishable histories, one safe answer: either E3 landed but E5 never did (live
        // inventory intact — restoring would duplicate), or X4 landed but X5's archive didn't
        // (already restored — restoring would duplicate).
        assertIs<Verdict.ArchiveStale>(decide(file = FileState.MATCHING))
        assertIs<Verdict.ArchiveStale>(decide(file = FileState.MISMATCHED, hasRun = true))
        // Even unreadable: without a tag no act is justified beyond getting it out of the way.
        assertIs<Verdict.ArchiveStale>(decide(file = FileState.UNREADABLE))
    }

    @Test
    fun `row 3 - a tag whose file is missing is the alarm, and nothing destructive happens`() {
        // Op deleted the stash file, or a partial rollback restored playerdata without world data.
        assertEquals(Verdict.Alarm(), decide(tagged = true, file = FileState.ABSENT))
        // A file that exists but cannot be parsed is the same promise broken.
        assertEquals(Verdict.Alarm(), decide(tagged = true, file = FileState.UNREADABLE))
    }

    @Test
    fun `swapId mismatch collapses to the alarm for the promised file, row 2 for the found one`() {
        val verdict = assertIs<Verdict.Alarm>(decide(tagged = true, file = FileState.MISMATCHED))
        assertTrue(verdict.archiveMismatched, "the mismatched file itself is stale and must be archived")
    }

    @Test
    fun `row 4 - run deleted or expired while swapped still restores the inventory`() {
        // The row that justifies the stash store being separate from RunStore: op deletion and
        // offline expiry cost a run, never an inventory.
        val verdict = assertIs<Verdict.FinishExit>(decide(tagged = true, file = FileState.MATCHING, hasRun = false))
        assertTrue(!verdict.captureRunBag, "the run is gone; there is no bag to capture")
    }

    @Test
    fun `row 5 - crash mid-session finishes the exit and saves the run bag first`() {
        val verdict = assertIs<Verdict.FinishExit>(decide(tagged = true, file = FileState.MATCHING, hasRun = true))
        assertTrue(verdict.captureRunBag, "the run continues; its bag must not die with the session")
    }

    @Test
    fun `row 6 - tagged inside arena space ejects before swapping`() {
        // The exit swap's postcondition is "outside"; ejecting first is what makes that true. Holds
        // whatever the file state says — the second reconcile pass sorts that out.
        for (file in FileState.entries) {
            for (hasRun in listOf(true, false)) {
                assertEquals(
                    Verdict.EjectThenReconcile,
                    decide(tagged = true, file = file, hasRun = hasRun, inArena = true),
                    "file=$file hasRun=$hasRun",
                )
            }
        }
    }

    @Test
    fun `row 7 - mid-run outside the arena sweeps and never installs`() {
        // The §2 contract revision: a paused player's party is THEIRS; install happens only through
        // resume. The sweep is what un-strands run Pokémon after a crash.
        assertEquals(Verdict.SweepRunPokemon, decide(hasRun = true))
    }

    @Test
    fun `row 8 - a tagless player in arena space is ejected whatever else is true`() {
        // The /tpahere mule, and also the run owner found inside untagged after a clean exit whose
        // teleport never happened: neither holds run property the guards could confuse for their own.
        for (file in FileState.entries) {
            for (hasRun in listOf(true, false)) {
                assertEquals(
                    Verdict.EjectForeigner,
                    decide(tagged = false, file = file, hasRun = hasRun, inArena = true),
                    "file=$file hasRun=$hasRun",
                )
            }
        }
    }

    @Test
    fun `no untagged input can reach a destructive verdict`() {
        // The doctrine itself: without a tag, nothing destructive or additive is ever justified.
        // FinishExit (clears and restores) and Alarm (locks the player out of runs) are reachable
        // only through a tag.
        for (file in FileState.entries) {
            for (hasRun in listOf(true, false)) {
                for (inArena in listOf(true, false)) {
                    val verdict = decide(tagged = false, file = file, hasRun = hasRun, inArena = inArena)
                    assertTrue(
                        verdict !is Verdict.FinishExit && verdict !is Verdict.Alarm,
                        "untagged (file=$file hasRun=$hasRun inArena=$inArena) reached $verdict",
                    )
                }
            }
        }
    }
}
