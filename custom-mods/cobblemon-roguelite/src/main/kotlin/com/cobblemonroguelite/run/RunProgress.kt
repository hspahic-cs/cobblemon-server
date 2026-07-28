package com.cobblemonroguelite.run

import com.cobblemonroguelite.composition.WaveComposition
import com.cobblemonroguelite.composition.WavePlan

/**
 * What should happen next in a run.
 *
 * Two answers and no third: either there is a wave to fight or the run is over. "Wait for the player
 * to do something" is deliberately not a case here — that is the controller's business, and folding
 * it in would let a run sit in a state that neither continues nor ends.
 */
sealed interface WaveStep {

    /** [plan] is fully composed, so the caller does not re-derive the level or the kind. */
    data class Fight(val plan: WavePlan) : WaveStep

    data class EndRun(val cause: RunEndCause) : WaveStep
}

/**
 * The decisions a run makes between waves, with nothing of the game in them.
 *
 * ### Why this is a separate object and not methods on the controller
 *
 * Everything here is a pure function of a wave number, a seed and two config-derived limits, which
 * makes it the whole of the run loop that can be tested without a booted server — and it is also
 * where the mistakes that matter live. "Wave 200 advanced to 201", "a lowered run length left a run
 * stuck", "the depth cap ended the run one wave early" are all silent in play and obvious in a unit
 * test. The controller keeps the parts that need a server: Pokémon, players, payouts.
 *
 * [WaveComposition] is asked for the plan rather than consulted for the schedule directly, so that
 * the level a wave is fought at and the level this layer reasons about cannot drift apart.
 */
object RunProgress {

    /**
     * What to do when a run is sitting at [wave] — on start, on resume, and after every victory.
     *
     * @param depthCap the deepest wave §2.18's badge gate allows, or null for no cap. Re-read from
     *   the player rather than stored, because a gate can only open further; see [RunDepthGate].
     */
    fun nextStep(wave: Int, seed: Long, composition: WaveComposition, depthCap: Int?): WaveStep = when {
        // Checked before the cap because it is the operator-caused case, and a run that is past the
        // end of the configured run entirely is over for a reason that has nothing to do with badges.
        composition.isBeyondRun(wave) -> WaveStep.EndRun(RunEndCause.RUN_LENGTH_SHORTENED)
        depthCap != null && wave > depthCap -> WaveStep.EndRun(RunEndCause.REACHED_DEPTH_CAP)
        else -> WaveStep.Fight(composition.planFor(wave, seed))
    }

    /**
     * What to do once [cleared] has been won.
     *
     * The final wave is answered here rather than by letting [nextStep] see wave 201 as an overrun:
     * they are the same number and completely different events. A player who beat wave 200 cleared
     * the run; a run whose length was lowered under it did not, and a payout table that pays those
     * two the same has been given no way to tell them apart.
     */
    fun afterVictory(cleared: WavePlan, seed: Long, composition: WaveComposition, depthCap: Int?): WaveStep =
        if (cleared.finalWave) {
            WaveStep.EndRun(RunEndCause.CLEARED_FINAL_WAVE)
        } else {
            nextStep(cleared.wave + 1, seed, composition, depthCap)
        }
}
