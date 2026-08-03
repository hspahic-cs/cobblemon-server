package com.cobblemonroguelite.data.trainer

import com.cobblemonroguelite.composition.WaveCompositionConfig
import com.cobblemonroguelite.integration.RunOpponent
import com.cobblemonroguelite.wave.WaveDrawStream
import com.cobblemonroguelite.wave.WaveRandom
import net.minecraft.resources.ResourceLocation

/**
 * One of the six times a run meets its rival.
 *
 * @property wave §2.36 fixes these at 8/25/55/95/145/195 for PokéRogue's schedule, but the numbers are
 *   content and live in the roster file. Only two things about them are mechanism: they are ordered,
 *   and a wave is a meeting or it is not.
 * @property trainerId the RCT trainer for *this* meeting, which is a different id from the last one and
 *   deliberately so. The character is one person; the NPC is one of six numbered stages, because that
 *   is how the only rival progression RCT ships is modelled (`rgl_rival_1`..`rgl_rival_6` in
 *   `ops/gen_trainer_texture_pack.py`, all six from the same variant line so the face does not change
 *   between meetings). Reusing one id for all six would cost the visible progression for nothing —
 *   the growing team is carried by [RivalLadder.teams] and does not need the id to be stable.
 *
 *   **The ids are not interchangeable with a band pool.** A band draws; this does not. If these ids
 *   ever appear in a [TrainerBand.trainers] as well, a run can meet stage 4 of the rival at wave 35 as
 *   an ordinary trainer, out of order, with a two-Pokémon team — which is why
 *   [TrainerRoster.validate] reports that overlap rather than allowing it.
 */
data class RivalMeeting(val wave: Int, val trainerId: ResourceLocation) {
    init {
        require(wave >= 1) { "wave is 1-based, got $wave" }
    }
}

/**
 * One rival *identity*: the whole team the character will ever own, in the order they acquire it.
 *
 * ### Why the full team is stored once and not per meeting
 *
 * Because the thing §2.36 describes is a team that **grows** — "gaining Pokémon each meeting" — and a
 * team that grows is a prefix. Meeting one takes the first two slots, meeting two the first three, and
 * so on ([RivalLadder.partySizeAt]). Storing six teams instead would let them disagree: an author who
 * edited slot one of meeting four and not of meeting three would produce a rival whose starter changed
 * species halfway through a run, and nothing in the format could notice.
 *
 * Slot order is therefore **acquisition order**, not the ace-first order [TrainerEntry.signature] uses.
 * That difference is worth knowing before writing one: a signature entry is truncated from the end when
 * a band wants a smaller party, so its first slot is its most important; a rival ladder is truncated
 * from the end because the later slots *have not been caught yet*, so its first slots are its oldest.
 * The starter goes first. Whatever the roster wants to be the final surprise goes last, where only the
 * last meeting or two will reach it.
 *
 * @property id an author-facing name — `kanto`, `hoenn` — used only in validation messages and logs.
 *   It is what tells someone reading `rival team 'hoenn' has 4 slots` which block to lengthen.
 * @property slots at least one. Which alternative fills a slot is a seeded pick per *run*, not per
 *   meeting; see [RivalLadder.RUN_SCOPED].
 */
data class RivalTeam(val id: String, val slots: List<SignatureSlot>) {
    init {
        require(id.isNotBlank()) { "a rival team needs a name — it is how validation messages identify it" }
        require(slots.isNotEmpty()) { "rival team '$id' has no slots, so the rival would arrive empty" }
    }
}

/**
 * §2.36's rival: one character, met on a fixed schedule, with a team that grows across the run.
 *
 * ### Why this is a mechanism of its own and the Elite Four is not
 *
 * §2.36 checked both and split them, and the split is the reason this class exists. The E4 and the
 * champion are *fixed, strong, otherwise ordinary bosses* — one authored team, one known wave — which
 * [FixedEncounter] already expresses; adding a kind for them would have bought nothing. A rival is a
 * different shape of thing entirely:
 *
 * - **It is one character, not a draw.** [TrainerBand] exists to answer "which of these trainers turns
 *   up here", and a rival has no "which". Expressing a rival as a one-trainer band per meeting would
 *   type-check and would then be six unrelated trainers who happen to share a face.
 * - **Its content at wave 145 is constrained by what it brought at wave 25.** This is the only opponent
 *   in the mode with that property, and it is the one that breaks the existing generator outright:
 *   [TrainerTeamGenerator] draws on `(seed, wave)` *by design* (§2.3 — a resumed run must meet the same
 *   team), and any function of the wave gives a different answer at every meeting. A rival built that
 *   way would swap starters six times and read as six impostors.
 *
 * ### How growth is implemented, and why it needs no run state
 *
 * §2.36 records this as needing "run state remembering which rival and what they have gained". It does
 * not, and the reason is worth stating because it looks like it should:
 *
 * - **Which rival** is a draw over [teams] on [WaveDrawStream.RIVAL] keyed on the run seed alone. A
 *   pure function of the seed is reproducible across a restart, a mod update and a different JVM for
 *   the same reason [WaveRandom] exists at all, so persisting it would only duplicate it.
 * - **What they have gained** is a function of the meeting index, which is a function of the wave. Take
 *   the first `n` slots; nothing has to be remembered because nothing was chosen.
 *
 * Both draws are keyed on [RUN_SCOPED] rather than on the wave, which is the whole trick and the only
 * fragile part of it — see that constant.
 *
 * **The one thing this does cost.** Editing [teams] mid-run re-points a run that has already met its
 * rival, exactly as editing [TrainerBand.trainers] re-points a later trainer wave — but the symptom is
 * worse in kind, because it is *within* one run: appending a second team changes the draw's divisor, so
 * a player who met the Kanto rival at wave 25 can meet the Hoenn one at wave 55, and the character has
 * visibly changed person. A pinned copy in the checkpoint would close that, and it is the one argument
 * for the run state §2.36 asked for; it is deliberately not taken here because the pin would need this
 * draw as its own fallback anyway (a pinned team id can be deleted from the roster too), so the pure
 * version is the part that has to exist either way. Until it is added, the operational rule is: **do
 * not edit `teams` while runs are in flight.**
 *
 * ### What a rival wave is *not* given, both of them decisions
 *
 * - **No boss level multiplier.** [RunOpponent.RIVAL] is not [RunOpponent.BOSS], so
 *   [TrainerRoster.planFor] derives the ordinary trainer level. That matches PokéRogue, where the ×1.2
 *   is attached to their boss waves (multiples of ten) and no rival wave is one — and it costs almost
 *   nothing either way, because §2.19 pins levels at 100 from about wave 138, which is where three of
 *   the six meetings sit. A rival is meant to be hard because of its team, which is the one lever it
 *   has that an ordinary trainer does not.
 * - **No boss shields (§2.32).** Falls out of the same fact, and is the more important half. Shields
 *   cost the held-item slot and are what makes a boss wave read as a wall; a rival read as a wall six
 *   times over would flatten the difference between the two kinds, and the rival would lose its items
 *   to buy a mechanic it does not need. An operator who wants a shielded final rival has a
 *   deliberately awkward route — write that wave as a `fixed` boss instead — and the awkwardness is
 *   the point: it stops being a rival at that moment.
 *
 * **Never catchable**, which needs no decision: §2.14 makes every trainer-owned Pokémon uncatchable and
 * [TrainerRoster.planFor] derives the flag from the kind, so promoting wave 8 out of a wild wave takes
 * catchability away in the same step.
 *
 * **Rewards are the scheduled wave's, not the rival's**, and that is the one answer here that is somebody
 * else's rather than ours. Every meeting is a promoted wave, and [TrainerRoster.planFor] leaves a promoted
 * wave's reward table alone on purpose — re-pointing it from a roster file would be a balance change made
 * by data, and §2.12 puts routing in the operator's hands. The consequence is that wave 8 rolls the *wild*
 * table, which is a wart shared with a promoted Elite Four member and is fixable with a one-wave
 * `RewardBand`. Whether promotions should route by effective kind is a §2.12 decision, not this class's.
 *
 * @property meetings ordered by wave, each wave distinct. Six under §2.36, but the count is content:
 *   the ladder works for one meeting or ten, and [partySizeAt] extends past the end of [partySizes].
 * @property teams the identities a run may draw. One is the ordinary case and means every run meets the
 *   same rival; several is PokéRogue's model, where the rival's team follows a region. A repeated entry
 *   doubles its share, which is the only weighting mechanism here for [TrainerBand.trainers]' reason.
 * @property partySizes how many Pokémon the rival brings at each meeting, indexed by meeting. Empty is
 *   the ordinary case and means §2.36's own ramp — see [partySizeAt].
 */
data class RivalLadder(
    val meetings: List<RivalMeeting>,
    val teams: List<RivalTeam>,
    val partySizes: List<Int> = emptyList(),
) {
    init {
        require(meetings.isNotEmpty()) { "a rival ladder with no meetings describes nothing; omit the block" }
        require(teams.isNotEmpty()) { "a rival ladder needs at least one team, or the rival arrives empty" }
        require(meetings.map { it.wave } == meetings.map { it.wave }.sorted()) {
            "rival meetings must ascend — the meeting index is what decides how much of the team has " +
                "been gained, so an out-of-order file would grow the team out of order too"
        }
        require(meetings.map { it.wave }.distinct().size == meetings.size) {
            "two rival meetings on one wave: the meeting index would be ambiguous"
        }
        require(partySizes.all { it in 1..MAX_PARTY }) {
            "rival party sizes must be 1..$MAX_PARTY, got $partySizes"
        }
    }

    /** Waves in ascending order, for validation messages and for a `/roguelite status` line. */
    fun waves(): List<Int> = meetings.map { it.wave }

    /** Which meeting [wave] is — 0-based — or null when it is not a meeting at all. */
    fun meetingIndexOf(wave: Int): Int? = meetings.indexOfFirst { it.wave == wave }.takeIf { it >= 0 }

    fun meetingAt(wave: Int): RivalMeeting? = meetings.firstOrNull { it.wave == wave }

    fun isMeeting(wave: Int): Boolean = meetings.any { it.wave == wave }

    /** Every stage NPC this ladder can summon, for the overlap checks in [TrainerRoster.validate]. */
    fun trainerIds(): Set<ResourceLocation> = meetings.mapTo(mutableSetOf()) { it.trainerId }

    /**
     * How many Pokémon the rival brings to meeting [index].
     *
     * Two behaviours in one function, and the empty case is the interesting one:
     *
     * - **[partySizes] empty** — §2.36's ramp, derived: [FIRST_MEETING_PARTY] at the first meeting and
     *   one more at each of the rest, capped at Cobblemon's [MAX_PARTY]. For six meetings that is
     *   2·3·4·5·6·6, which is the plan's sentence ("a regional starter and regional bird to begin,
     *   gaining Pokémon each meeting") turned into numbers. Derived rather than shipped as a literal
     *   list so a roster with four meetings or eight gets a coherent ramp instead of a clamped copy of
     *   somebody else's.
     * - **[partySizes] given** — read positionally, clamped to the last entry past the end. A ladder
     *   with more meetings than sizes therefore plateaus rather than throwing, which is the same clamp
     *   [SpeciesLine.stageAt] makes and for the same reason: the two lists are authored separately and
     *   the shorter one must not be able to break a run.
     *
     * A size larger than the team's slot count is not an error here — the party is simply shorter, the
     * way [TrainerEntry.filler] is documented to run short. [TrainerRoster.validate] reports it at load
     * instead, because a rival that arrives with four Pokémon when the ramp asked for six is a content
     * mistake nobody would attribute to the team block being one slot too short.
     */
    fun partySizeAt(index: Int): Int {
        require(index >= 0) { "meeting index is 0-based, got $index" }
        if (partySizes.isEmpty()) return (FIRST_MEETING_PARTY + index).coerceAtMost(MAX_PARTY)
        return partySizes[index.coerceAtMost(partySizes.lastIndex)]
    }

    /**
     * Which rival this run gets.
     *
     * A function of the seed and nothing else — not the wave, not the meeting, not the player. That is
     * what makes the character the *same* character at every meeting, and it is the property that would
     * be silently lost by anyone "fixing" this to take a wave for symmetry with the rest of the module.
     *
     * The index arithmetic is [TrainerRoster.pickFor]'s, kept identical rather than shared for the
     * reason given there: a modulo of a 64-bit draw is biased toward low indices for any pool size that
     * does not divide 2^64, and the two call sites must not drift into disagreeing about it.
     */
    fun teamFor(seed: Long): RivalTeam {
        // Short-circuited for the one-team case, which is the ordinary one. Safe to skip the draw only
        // because this stream is [WaveDrawStream.RIVAL] and nothing else consumes it — the slot picks are
        // on RIVAL_TEAM, so not spending a draw here cannot shift them.
        if (teams.size == 1) return teams.first()
        val rng = WaveRandom.forDraw(seed, RUN_SCOPED, WaveDrawStream.RIVAL)
        val index = (rng.nextDouble() * teams.size).toInt().coerceAtMost(teams.lastIndex)
        return teams[index]
    }

    companion object {

        /**
         * Fed to [WaveRandom.forDraw] where a wave number goes, for the two rival streams.
         *
         * **This is the mechanic, not a placeholder.** Both rival draws have to answer the same at
         * every meeting of one run — the identity so the character does not change person, and the slot
         * picks so the growing team stays a prefix of one team rather than six re-rolls. Feeding a wave
         * would make both vary per meeting, which is the exact failure §2.36 says a rival is the
         * opposite of. The precedent is [WaveDrawStream.BIOME], which passes a band index for the same
         * reason: the wave slot of that function is "what is this draw scoped to", and here the answer
         * is the run.
         *
         * Zero specifically because waves are 1-based everywhere in this module, so it cannot collide
         * with a real wave's stream and cannot be produced by an off-by-one somewhere else.
         */
        const val RUN_SCOPED = 0

        /**
         * §2.36: "a regional starter and regional bird to begin" — two Pokémon at the first meeting.
         *
         * The number is the plan's, and it is also the only starting size that makes the *growth* the
         * thing a player notices. Starting at four would leave two meetings' worth of ramp before the
         * cap and make the first rival indistinguishable from an early trainer wave.
         */
        const val FIRST_MEETING_PARTY = 2

        /** Cobblemon's party limit, the same ceiling [PartySizeTier] is bounded by. */
        const val MAX_PARTY = 6

        /**
         * PokéRogue's rival waves under their Classic schedule, for a validator message and for tests.
         *
         * **Reference, not content, and nothing reads it as a default.** A roster that wants this
         * ladder writes it out; §2.7 keeps the transcribed schedule in a server-side datapack and the
         * mod ships no loaded roster at all. It is here so that a message can say "this looks like
         * PokéRogue's ladder with wave 55 typed as 45" instead of leaving an author to count, and
         * because [WaveCompositionConfig]'s 5/10 schedule makes exactly one of the six a wild wave —
         * a fact worth being able to assert rather than remember.
         */
        val POKEROGUE_MEETING_WAVES = listOf(8, 25, 55, 95, 145, 195)
    }
}
