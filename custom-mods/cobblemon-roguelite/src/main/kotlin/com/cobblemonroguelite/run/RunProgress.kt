package com.cobblemonroguelite.run

import com.cobblemonroguelite.composition.WaveComposition
import com.cobblemonroguelite.composition.WavePlan
import com.cobblemonroguelite.data.trainer.TrainerPick
import net.minecraft.resources.ResourceLocation

/**
 * What should happen next in a run.
 *
 * Fight it, or the run is over. "Wait for the player to do something" is deliberately not a case here
 * — that is the controller's business, and folding it in would let a run sit in a state that neither
 * continues nor ends.
 *
 * [NoRoster] is the one addition to that pair and it is neither of the two rather than a third mood:
 * the wave cannot be *composed at all*, so there is nothing to fight, and ending the run would destroy
 * a party over a datapack an operator can put back in thirty seconds.
 */
sealed interface WaveStep {

    /**
     * [plan] is fully composed — including any promotion this run's roster applies — so the caller
     * does not re-derive the level, the kind or whether the wave is catchable.
     *
     * @property trainer who the wave fights, or null on a wild wave and on a wave the roster serves
     *   nothing. This is the trainer *for this run at this wave*: it already accounts for fixed
     *   encounters and for the no-repeat window, so re-drawing it in the battle layer would produce a
     *   different opponent from the one this run is supposed to meet.
     */
    data class Fight(val plan: WavePlan, val trainer: TrainerPick?) : WaveStep

    data class EndRun(val cause: RunEndCause) : WaveStep

    /**
     * The run's pinned roster is not loaded, so [wave] cannot be composed. The run is untouched and
     * resumes the moment the roster is back. [rosterId] is null when the run carries no pinned id.
     */
    data class NoRoster(val wave: Int, val rosterId: ResourceLocation?) : WaveStep
}

/**
 * The decisions a run makes between waves, with nothing of the game in them.
 *
 * ### Why this is a separate object and not methods on the controller
 *
 * Everything here is a pure function of a wave number, a seed, a roster, this run's recent opponents
 * and two config-derived limits, which makes it the whole of the run loop that can be tested without a
 * booted server — and it is also where the mistakes that matter live. "Wave 200 advanced to 201", "a
 * lowered run length left a run stuck", "the depth cap ended the run one wave early", "wave 182 came
 * out catchable" are all silent in play and obvious in a unit test. The controller keeps the parts that
 * need a server: Pokémon, players, payouts.
 *
 * ### The plan comes from the roster, never from the composition
 *
 * [TrainerRoster.planFor][com.cobblemonroguelite.data.trainer.TrainerRoster.planFor] wraps
 * [WaveComposition.planFor] and is the only one of the two that knows about promotions. Calling the
 * composition directly is not a shortcut, it is a bug with three symptoms at once: a promoted wave
 * stays catchable (§2.14 — an Elite Four member ends up in somebody's party), it is fought at the wild
 * level rather than the boss one, and its kind is wrong everywhere downstream. The composition cannot
 * fix this itself and should not: which waves are bosses must not depend on which datapack a server
 * happens to have loaded, so the reconciliation belongs on the roster side and this layer's whole job
 * is to route through it.
 */
object RunProgress {

    /**
     * What to do when a run is sitting at [wave] — on start, on resume, and after every victory.
     *
     * @param roster the run's pinned roster, resolved. [RunRoster.Missing] stops the run here rather
     *   than composing the wave without it; see [RunRoster] for why "no promotions, carry on" is the
     *   wrong reading.
     * @param memory this run's recent opponents, read and not written. Recording is the controller's,
     *   at the one point a wave is actually finished — planning a wave has to stay repeatable.
     * @param depthCap the deepest wave §2.18's badge gate allows, or null for no cap. Re-read from
     *   the player rather than stored, because a gate can only open further; see [RunDepthGate].
     */
    fun nextStep(
        wave: Int,
        seed: Long,
        composition: WaveComposition,
        roster: RunRoster,
        memory: RunTrainerMemory,
        depthCap: Int?,
    ): WaveStep {
        // Both endings are checked before the roster, so a run that is already over ends cleanly even
        // on a server whose roster has gone missing. The other order would leave someone who finished
        // wave 200 unable to be paid until an operator fixed a datapack they no longer need.
        if (composition.isBeyondRun(wave)) return WaveStep.EndRun(RunEndCause.RUN_LENGTH_SHORTENED)
        if (depthCap != null && wave > depthCap) return WaveStep.EndRun(RunEndCause.REACHED_DEPTH_CAP)
        return when (roster) {
            is RunRoster.Missing -> WaveStep.NoRoster(wave, roster.id)
            is RunRoster.Loaded -> planFor(wave, seed, composition, roster, memory)
        }
    }

    /**
     * One wave, composed through the roster and paired with its opponent.
     *
     * Separate from [nextStep] because the controller needs it for the wave that was just *won*, where
     * the endings and the depth cap have nothing to say — and re-composing a wave has to give the same
     * answer as composing it did, which is only true while nothing has recorded into [memory] in
     * between.
     */
    fun planFor(
        wave: Int,
        seed: Long,
        composition: WaveComposition,
        roster: RunRoster.Loaded,
        memory: RunTrainerMemory,
    ): WaveStep.Fight {
        val plan = roster.roster.planFor(wave, seed, composition)
        // Asked with the plan's kind rather than the schedule's, so a promoted wave draws from the
        // boss bands it was promoted into instead of from the trainer ones it was scheduled as.
        val trainer = RunTrainerSelection.pick(roster.roster, wave, plan.kind, seed, memory.before(wave))
        return WaveStep.Fight(plan, trainer)
    }

    /**
     * What to do once [cleared] has been won.
     *
     * The final wave is answered here rather than by letting [nextStep] see wave 201 as an overrun:
     * they are the same number and completely different events. A player who beat wave 200 cleared
     * the run; a run whose length was lowered under it did not, and a payout table that pays those
     * two the same has been given no way to tell them apart.
     */
    fun afterVictory(
        cleared: WavePlan,
        seed: Long,
        composition: WaveComposition,
        roster: RunRoster,
        memory: RunTrainerMemory,
        depthCap: Int?,
    ): WaveStep =
        if (cleared.finalWave) {
            WaveStep.EndRun(RunEndCause.CLEARED_FINAL_WAVE)
        } else {
            nextStep(cleared.wave + 1, seed, composition, roster, memory, depthCap)
        }
}
