package com.cobblemonroguelite.data.trainer

import com.cobblemonroguelite.wave.WaveDrawStream
import com.cobblemonroguelite.wave.WaveLevelCurve
import com.cobblemonroguelite.wave.WaveRandom

/**
 * Builds the rival's team for one meeting — the same character's team, one meeting further on.
 *
 * ### Why this is not [TrainerTeamGenerator] with a flag
 *
 * They differ in the one thing that matters: **what the draw is keyed on.** [TrainerTeamGenerator]
 * keys every draw on `(seed, wave)` deliberately, because a trainer wave is an independent event and
 * §2.3 only requires that replaying it gives the same answer. A rival is not independent of its own
 * past — §2.36 makes it one character across six meetings — so a per-wave key is not a detail to
 * override, it is the wrong function. Two objects, so nobody has to hold both meanings of "the seeded
 * draw" in their head at once, and so a future change to trainer generation cannot silently re-roll a
 * rival mid-run.
 *
 * ### The two axes a rival strengthens on
 *
 * 1. **Breadth: it gains a Pokémon per meeting.** Implemented as a *prefix* of [RivalTeam.slots] —
 *    meeting three brings slots 1..4, meeting two brought 1..3. The slot draws are made in order from
 *    one run-scoped stream, so meeting two's three draws are literally the first three of meeting
 *    three's four and the shared slots resolve to the same species. That equality is the whole
 *    mechanism and it is the thing to check first if a rival is ever reported as swapping starters:
 *    it holds only while the stream key excludes the wave (see [RivalLadder.RUN_SCOPED]) and while
 *    [TrainerTeamGenerator.pickAlternative] consumes exactly one draw per slot regardless of how many
 *    alternatives that slot has — which is why it does so even for a slot with one.
 * 2. **Depth: the same Pokémon evolve.** The stage index is a function of the *wave*
 *    ([EvolutionSchedule.stageIndexFor]), shared with generated trainers so a rival at wave 95 is as
 *    far evolved as a leader at wave 95. This axis is deliberately wave-keyed while the species are
 *    run-keyed: "your starter, but it is a Charizard now" is the point, and it needs both.
 *
 * ### What it does not get
 *
 * No shields, ever — passed as `boss = false` below, and argued in [RivalLadder]. Held items *are*
 * drawn, on the ordinary non-boss tier and on the ordinary per-wave stream, because items are not part
 * of the continuity: a rival turning up with a different berry at wave 145 is variety, whereas a rival
 * turning up with a different starter is a bug.
 *
 * Stateless, like every other generator here: one instance serves every concurrent run.
 */
object RivalTeamGenerator {

    /**
     * The rival's team at [wave], or null when [wave] is not one of [ladder]'s meetings.
     *
     * Null is a "this is not a rival wave" answer and not a failure — the same shape
     * [TrainerRoster.teamFor] already returns for an authored fight, so a caller that routes on
     * nullability keeps working.
     *
     * @param curve the run's level curve. Levels are per member ([WaveLevelCurve.partyMemberLevel]),
     *   spread by [TrainerTeamGenerator.strengthsFor] exactly as a generated trainer's are — the
     *   rival's ace is the starter-line slot, which the spread rewards for the same reason §2.32
     *   shields go on slot one. No boss ×1.2 is applied to rival waves and [RivalLadder] says why;
     *   under PokéRogue's `getPartyLevels` port there is no flat multiplier to apply anyway.
     * @param rules shared with generated trainers, and shared on purpose: the evolution schedule and
     *   the held-item tiers are the roster's answer to "how strong is content at wave N", and a rival
     *   with its own copy would drift out of step with the leaders around it. Only
     *   [TeamGenerationRules.partySizes] is *not* consulted — a rival's party size comes from its
     *   meeting index ([RivalLadder.partySizeAt]), because growth is the mechanic and a band-wide size
     *   would flatten it.
     */
    fun generate(
        ladder: RivalLadder,
        wave: Int,
        curve: WaveLevelCurve,
        seed: Long,
        rules: TeamGenerationRules,
    ): GeneratedTeam? {
        require(wave >= 1) { "wave is 1-based, got $wave" }
        val meeting = ladder.meetingIndexOf(wave) ?: return null

        val team = ladder.teamFor(seed)
        val wanted = ladder.partySizeAt(meeting)

        // Keyed on the run, not the wave, and consumed in slot order. See the class docs: this is what
        // makes the team a growing prefix rather than six independent rolls.
        val rng = WaveRandom.forDraw(seed, RivalLadder.RUN_SCOPED, WaveDrawStream.RIVAL_TEAM)
        // take() rather than an index check: a team shorter than the ramp asks for arrives short, which
        // TrainerRoster.validate reports at load. Failing here instead would cost a player their run
        // over a roster that is one slot too short.
        val lines = team.slots.take(wanted).map { TrainerTeamGenerator.pickAlternative(it, rng) }

        val stageIndex = rules.evolution.stageIndexFor(wave)
        val species = lines.map { it.stageAt(stageIndex) }
        val items = TrainerTeamGenerator.heldItems(species.size, wave, boss = false, seed = seed, rules = rules)
        val strengths = TrainerTeamGenerator.strengthsFor(species.size)

        return GeneratedTeam(
            species.mapIndexed { index, member ->
                // shields defaulted to 0 rather than passed: §2.32's mechanic belongs to boss waves,
                // and a rival is not one.
                GeneratedMember(member, curve.partyMemberLevel(wave, strengths[index]), items[index])
            },
        )
    }
}
