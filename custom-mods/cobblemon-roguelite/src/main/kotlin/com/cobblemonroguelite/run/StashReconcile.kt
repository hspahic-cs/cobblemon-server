package com.cobblemonroguelite.run

/**
 * The §6 reconcile table from `docs/roguelite-run-isolation.md`, as a pure function.
 *
 * ### Why a function and not code inline in the login hook
 *
 * Every crash, kill -9, operator intervention and world rollback presents as one input tuple here,
 * which makes the whole recovery surface a table a unit test walks — the same argument
 * [com.cobblemonroguelite.arena.ArenaPlan] makes for arena generation. The hook's job shrinks to
 * gathering the four facts and executing the verdict; the *decision* never touches Minecraft.
 *
 * ### The doctrine this encodes
 *
 * Without a tag, no destructive or additive act is ever justified (row 2: a stray file is archived,
 * never restored — either the clear never landed and restoring would duplicate, or the restore
 * already happened and restoring would duplicate). With a tag and no readable matching file, nothing
 * destructive happens at all (row 3: the disk promises a stash that is not there; guessing here is
 * how items die). The only destructive verdicts are marker-keyed ones.
 */
object StashReconcile {

    /** The stash file's state *relative to the tag* — matching means the header's swapId equals it. */
    enum class FileState { ABSENT, MATCHING, MISMATCHED, UNREADABLE }

    sealed interface Verdict {

        /** Row 1: nothing to do. The row that runs a thousand times a day and keeps the rest honest. */
        data object Nothing : Verdict

        /**
         * Row 2: a file with no tag pointing at it. Archive it, log WARN with a stack-count summary,
         * restore nothing. [alsoMismatched] is the §6 footnote: a tagged player whose file mismatches
         * gets the alarm for the *promised* file (row 3) while the mismatched file itself is archived
         * as this row — both verdicts, one pass.
         */
        data class ArchiveStale(val alsoMismatched: Boolean = false) : Verdict

        /**
         * Row 3: the alarm. Tag present, promised file missing/unreadable/mismatched. Refuse
         * everything destructive: no clear, no install, no tag removal (a restored backup must stay
         * matchable by swapId). Arena entry is refused while this holds. Exits: op restores the file,
         * or `stash forfeit`.
         */
        data class Alarm(val archiveMismatched: Boolean = false) : Verdict

        /**
         * Rows 4 and 5: finish the exit swap (X-protocol). [captureRunBag] is the row-5 distinction —
         * the run still exists, so X1 must save its bag before the partition voids the live copies;
         * row 4's run is gone and X1 is skipped.
         */
        data class FinishExit(val captureRunBag: Boolean) : Verdict

        /**
         * Row 6: tagged and inside arena space at login. Eject first (the lease ended at logout,
         * §2.23), then reconcile again — the exit swap's "outside" postcondition should be true when
         * it finishes. The second pass lands on row 4 or 5.
         */
        data object EjectThenReconcile : Verdict

        /**
         * Row 7: mid-run, untagged, outside — a paused player or a fresh login. Sweep run-marked
         * Pokémon out of the party and PC; install NOTHING (§2's revised contract — install happens
         * only through `resume`). Their own party is theirs right now.
         */
        data object SweepRunPokemon : Verdict

        /**
         * Row 8: a tagless player standing in arena space — a `/tpahere` mule, or an op teleport.
         * Eject; void run-marked stacks they carry (read-through). Their own property is untouched —
         * the tag rule guarantees the guards cannot mistake their inventory for a run's.
         */
        data object EjectForeigner : Verdict
    }

    fun decide(tagged: Boolean, file: FileState, hasRun: Boolean, inArena: Boolean): Verdict = when {
        // Arena-space rows first: position decides before anything else, because every other verdict
        // assumes "outside" as its postcondition.
        !tagged && inArena -> Verdict.EjectForeigner
        tagged && inArena -> Verdict.EjectThenReconcile

        tagged -> when (file) {
            FileState.MATCHING -> Verdict.FinishExit(captureRunBag = hasRun)
            FileState.ABSENT, FileState.UNREADABLE -> Verdict.Alarm()
            FileState.MISMATCHED -> Verdict.Alarm(archiveMismatched = true)
        }

        // Untagged, outside.
        file != FileState.ABSENT -> Verdict.ArchiveStale()
        hasRun -> Verdict.SweepRunPokemon
        else -> Verdict.Nothing
    }
}
