package com.cobblemonroguelite.run

import com.cobblemonroguelite.composition.WaveComposition
import com.cobblemonroguelite.composition.WaveCompositionConfig
import com.cobblemonroguelite.data.payout.RunOutcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * The between-wave decisions, and the mapping from why a run stopped to what a payout table sees.
 *
 * All of it is a pure function of a wave number and two limits, which is the whole reason it was
 * pulled out of the controller: these are the mistakes that a play-through would find slowly or not
 * at all — a run that advanced past its final wave, one left stuck at a wave the config no longer
 * contains, a depth cap that ended a run a wave early or a wave late.
 */
class RunProgressTest {

    // Short run, so the boundary cases are reachable without composing two hundred waves. The
    // intervals are PokéRogue's; only the length is shortened.
    private val composition = WaveComposition(WaveCompositionConfig(runLength = 20))
    private val seed = 1234L

    @Test
    fun `an ordinary wave is fought`() {
        val step = RunProgress.nextStep(3, seed, composition, depthCap = null)
        assertIs<WaveStep.Fight>(step)
        assertEquals(3, step.plan.wave)
    }

    @Test
    fun `clearing the final wave completes the run`() {
        val final = composition.planFor(20, seed)
        val step = RunProgress.afterVictory(final, seed, composition, depthCap = null)
        assertEquals(WaveStep.EndRun(RunEndCause.CLEARED_FINAL_WAVE), step)
    }

    @Test
    fun `clearing an ordinary wave advances by one`() {
        val step = RunProgress.afterVictory(composition.planFor(7, seed), seed, composition, depthCap = null)
        assertIs<WaveStep.Fight>(step)
        assertEquals(8, step.plan.wave)
    }

    @Test
    fun `the depth cap ends the run after its last allowed wave, not before it`() {
        val capped = RunProgress.afterVictory(composition.planFor(5, seed), seed, composition, depthCap = 5)
        assertEquals(WaveStep.EndRun(RunEndCause.REACHED_DEPTH_CAP), capped)
        // Wave 5 itself is allowed — a cap of 5 means five waves, not four.
        assertIs<WaveStep.Fight>(RunProgress.nextStep(5, seed, composition, depthCap = 5))
    }

    @Test
    fun `a run past a shortened run length ends rather than sticking`() {
        // The operator case: runLength lowered under a live run. WaveComposition answers overrun
        // waves instead of refusing them precisely so this can be handled rather than crash.
        val step = RunProgress.nextStep(21, seed, composition, depthCap = null)
        assertEquals(WaveStep.EndRun(RunEndCause.RUN_LENGTH_SHORTENED), step)
    }

    @Test
    fun `clearing the final wave beats the depth cap`() {
        // A player whose cap happens to equal the run length cleared the run; they did not run out
        // of badges on the last wave, and the two pay the same but read differently in a log.
        val step = RunProgress.afterVictory(composition.planFor(20, seed), seed, composition, depthCap = 20)
        assertEquals(WaveStep.EndRun(RunEndCause.CLEARED_FINAL_WAVE), step)
    }

    @Test
    fun `every end cause maps onto a payout outcome`() {
        // The reconciliation §2.20's RunOutcome asks for: causes may be added freely, and each has
        // to say which of the three schema outcomes it pays as. This fails the moment one does not.
        assertEquals(RunOutcome.COMPLETED, RunEndCause.CLEARED_FINAL_WAVE.outcome)
        assertEquals(RunOutcome.COMPLETED, RunEndCause.REACHED_DEPTH_CAP.outcome)
        assertEquals(RunOutcome.COMPLETED, RunEndCause.RUN_LENGTH_SHORTENED.outcome)
        assertEquals(RunOutcome.WIPED, RunEndCause.PARTY_WIPED.outcome)
        assertEquals(RunOutcome.ABANDONED, RunEndCause.PLAYER_ABANDONED.outcome)
    }

    @Test
    fun `every payout outcome is reachable from some end cause`() {
        // The other direction, and the one that catches an outcome nobody can ever be paid for: a
        // table author writing `"outcomes": ["wiped"]` is entitled to assume a run can wipe.
        assertEquals(RunOutcome.entries.toSet(), RunEndCause.entries.map { it.outcome }.toSet())
    }
}
