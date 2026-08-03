package com.cobblemonroguelite.run

import com.cobblemonroguelite.data.payout.RunOutcome

/**
 * Why a run stopped — the run loop's own vocabulary, projected onto [RunOutcome] for the payout.
 *
 * ### Why this exists as well as [RunOutcome], and why it is not the same enum
 *
 * [RunOutcome] is a **schema promise to server owners**: those three names are what a payout table
 * author writes in a JSON file, and adding a case to that enum silently changes what every existing
 * table does or does not match. The run loop, meanwhile, ends runs for reasons that are genuinely
 * different from each other and that an operator reading a log needs to be able to tell apart —
 * "the player finished wave 200" and "an op shortened the run and their wave no longer exists" are
 * both a completed run to the payout and are not remotely the same event.
 *
 * So the two types are kept apart on purpose and [outcome] is the only bridge between them. That
 * direction is the load-bearing one: causes may be added here freely, and each new one has to say
 * which of the three existing outcomes it pays as. What must not happen is a new case appearing in
 * [RunOutcome] because the run loop grew a reason — that is the duplication this arrangement exists
 * to prevent, and [RunOutcome]'s own docs ask for exactly this shape.
 *
 * @property outcome what the payout table sees. Several causes map to one outcome by design.
 */
enum class RunEndCause(val outcome: RunOutcome) {

    /** The player won the final wave (§2.19's wave 200). The run cleared. */
    CLEARED_FINAL_WAVE(RunOutcome.COMPLETED),

    /**
     * The run hit the depth its badges entitle it to (§2.18) and stopped there.
     *
     * Pays as a completed run rather than as a walk-away, and that is a decision. The alternative —
     * refusing to advance and leaving the player to abandon — hands them a run they paid for, cannot
     * progress, and must throw away at whatever a table pays for abandoning. A capped run is a run
     * played to its own end; the badge gate decides where that end is.
     */
    REACHED_DEPTH_CAP(RunOutcome.COMPLETED),

    /**
     * The configured run length was lowered below the wave this run had reached.
     *
     * [com.cobblemonroguelite.composition.WaveComposition.isBeyondRun] answers overrun waves rather
     * than refusing them precisely so this can happen cleanly: an operator retuning a live server
     * ends the affected runs as completed instead of leaving them stuck at a wave that no longer
     * exists. Distinct from [CLEARED_FINAL_WAVE] because nobody should read an operator's config
     * edit as twenty players clearing wave 200 at once.
     */
    RUN_LENGTH_SHORTENED(RunOutcome.COMPLETED),

    /** Permadeath took the last party member (§2.13). */
    PARTY_WIPED(RunOutcome.WIPED),

    /** The player chose to walk away. §2.16 treats this as legitimate; the entry fee prices it. */
    PLAYER_ABANDONED(RunOutcome.ABANDONED),
    ;

    /** Whether the run ended on the player's own initiative, for phrasing the message they get. */
    val voluntary: Boolean get() = this == PLAYER_ABANDONED
}
