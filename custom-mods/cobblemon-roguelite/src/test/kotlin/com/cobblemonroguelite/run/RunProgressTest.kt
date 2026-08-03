package com.cobblemonroguelite.run

import com.cobblemonroguelite.composition.WaveComposition
import com.cobblemonroguelite.composition.WaveCompositionConfig
import com.cobblemonroguelite.data.payout.RunOutcome
import com.cobblemonroguelite.data.trainer.FixedEncounter
import com.cobblemonroguelite.data.trainer.TrainerBand
import com.cobblemonroguelite.data.trainer.TrainerRoster
import com.cobblemonroguelite.integration.RunOpponent
import net.minecraft.resources.ResourceLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The between-wave decisions, and the mapping from why a run stopped to what a payout table sees.
 *
 * All of it is a pure function of a wave number, a roster and two limits, which is the whole reason it
 * was pulled out of the controller: these are the mistakes that a play-through would find slowly or
 * not at all — a run that advanced past its final wave, one left stuck at a wave the config no longer
 * contains, a depth cap that ended a run a wave early or a wave late, a promoted Elite Four wave that
 * came out catchable.
 */
class RunProgressTest {

    // Short run, so the boundary cases are reachable without composing two hundred waves. The
    // intervals are PokéRogue's; only the length is shortened.
    private val schedule = WaveCompositionConfig(runLength = 20)
    private val composition = WaveComposition(schedule)
    private val seed = 1234L

    private fun id(path: String) = ResourceLocation.fromNamespaceAndPath("test", path)

    private fun roster(fixed: List<FixedEncounter> = emptyList()) = RunRoster.Loaded(
        id("roster"),
        TrainerRoster(
            id = id("roster"),
            authoredFor = schedule,
            bands = listOf(
                TrainerBand("t", RunOpponent.TRAINER, 1, null, listOf(id("t_a"), id("t_b"), id("t_c"))),
                TrainerBand("b", RunOpponent.BOSS, 1, null, listOf(id("b_a"), id("b_b"))),
            ),
            fixed = fixed.associateBy { it.wave },
        ),
    )

    private fun step(wave: Int, roster: RunRoster = roster(), depthCap: Int? = null) =
        RunProgress.nextStep(wave, seed, composition, roster, RunTrainerMemory(), depthCap)

    @Test
    fun `an ordinary wave is fought`() {
        val step = step(3)
        assertIs<WaveStep.Fight>(step)
        assertEquals(3, step.plan.wave)
    }

    @Test
    fun `a wild wave has no trainer and a trainer wave does`() {
        assertNull(assertIs<WaveStep.Fight>(step(3)).trainer)
        assertEquals("t", assertIs<WaveStep.Fight>(step(5)).trainer?.bandId)
        assertEquals("b", assertIs<WaveStep.Fight>(step(10)).trainer?.bandId)
    }

    @Test
    fun `the plan is the roster's, not the composition's`() {
        // The bug this exists for: routing through WaveComposition leaves a promoted wave catchable
        // and at the wild level, and nothing downstream can tell. Wave 13 is wild under 5/10.
        val promoted = roster(listOf(FixedEncounter(13, id("e4"), RunOpponent.BOSS)))
        assertEquals(RunOpponent.WILD, composition.kindOf(13))
        assertTrue(composition.planFor(13, seed).catchable)

        val step = assertIs<WaveStep.Fight>(step(13, promoted))
        assertEquals(RunOpponent.BOSS, step.plan.kind)
        assertFalse(step.plan.catchable)
        assertTrue(step.plan.level > composition.planFor(13, seed).level, "a promotion must take the boss multiplier")
        assertEquals(id("e4"), step.trainer?.trainerId)
    }

    @Test
    fun `a promoted wave still rewards as the wave it was scheduled as`() {
        // Left this way on purpose (§2.12 owns reward routing): re-pointing a reward table from a
        // roster file would be a balance change made by data. Pinned so it is a decision and not a
        // detail somebody tidies up.
        val promoted = roster(listOf(FixedEncounter(13, id("e4"), RunOpponent.BOSS)))
        assertEquals(
            composition.planFor(13, seed).rewardTable,
            assertIs<WaveStep.Fight>(step(13, promoted)).plan.rewardTable,
        )
    }

    @Test
    fun `a missing roster stops the run without ending it`() {
        // The alternative — compose from the schedule and carry on — is the failure worth refusing:
        // the run would keep going and quietly serve wild waves where the ladder should have been.
        val step = step(5, RunRoster.Missing(id("gone")))
        assertEquals(WaveStep.NoRoster(5, id("gone")), step)
    }

    @Test
    fun `an ending beats a missing roster`() {
        // A run that is already over must be payable on a server whose roster went missing, or the
        // player waits on a datapack they no longer need.
        val missing = RunRoster.Missing(id("gone"))
        assertEquals(WaveStep.EndRun(RunEndCause.RUN_LENGTH_SHORTENED), step(21, missing))
        assertEquals(WaveStep.EndRun(RunEndCause.REACHED_DEPTH_CAP), step(6, missing, depthCap = 5))
    }

    @Test
    fun `clearing the final wave completes the run`() {
        val final = RunProgress.planFor(20, seed, composition, roster(), RunTrainerMemory()).plan
        val step = RunProgress.afterVictory(final, seed, composition, roster(), RunTrainerMemory(), depthCap = null)
        assertEquals(WaveStep.EndRun(RunEndCause.CLEARED_FINAL_WAVE), step)
    }

    @Test
    fun `clearing an ordinary wave advances by one`() {
        val step = RunProgress.afterVictory(
            composition.planFor(7, seed), seed, composition, roster(), RunTrainerMemory(), depthCap = null,
        )
        assertIs<WaveStep.Fight>(step)
        assertEquals(8, step.plan.wave)
    }

    @Test
    fun `the depth cap ends the run after its last allowed wave, not before it`() {
        val capped = RunProgress.afterVictory(
            composition.planFor(5, seed), seed, composition, roster(), RunTrainerMemory(), depthCap = 5,
        )
        assertEquals(WaveStep.EndRun(RunEndCause.REACHED_DEPTH_CAP), capped)
        // Wave 5 itself is allowed — a cap of 5 means five waves, not four.
        assertIs<WaveStep.Fight>(step(5, depthCap = 5))
    }

    @Test
    fun `a run past a shortened run length ends rather than sticking`() {
        // The operator case: runLength lowered under a live run. WaveComposition answers overrun
        // waves instead of refusing them precisely so this can be handled rather than crash.
        assertEquals(WaveStep.EndRun(RunEndCause.RUN_LENGTH_SHORTENED), step(21))
    }

    @Test
    fun `clearing the final wave beats the depth cap`() {
        // A player whose cap happens to equal the run length cleared the run; they did not run out
        // of badges on the last wave, and the two pay the same but read differently in a log.
        val step = RunProgress.afterVictory(
            composition.planFor(20, seed), seed, composition, roster(), RunTrainerMemory(), depthCap = 20,
        )
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
