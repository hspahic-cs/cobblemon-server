package com.cobblemonroguelite.data.trainer

import com.cobblemonroguelite.composition.WaveComposition
import com.cobblemonroguelite.composition.WaveCompositionConfig
import com.cobblemonroguelite.composition.WavePlan
import com.cobblemonroguelite.integration.RunOpponent
import com.cobblemonroguelite.wave.WaveDrawStream
import com.cobblemonroguelite.wave.WaveRandom
import net.minecraft.resources.ResourceLocation

/**
 * One wave range and the trainers that may be met inside it.
 *
 * ### Why bands exist at all, now that teams are generated
 *
 * Not for levels, and — since §2.30 — not for movesets either.
 *
 * They originally existed for movesets. Forcing an RCT trainer's team to the wave level at
 * `BattleStartedEvent.Pre` is verified on dev (§2.6), so one team could be stretched from wave 1 to
 * wave 200 and always arrive at the right level; what that setter does *not* scale is the **moveset**,
 * so a team authored for wave 10 turned up at wave 150 at level 100 still running wave-10 moves.
 * Bands were the unit at which that content got re-authored.
 *
 * Generating the team at the encounter ([TrainerTeamGenerator]) derives the moveset for the level
 * being generated, which retires that reason entirely. What is left is the one §2.30 keeps: a band
 * says **which leaders appear when**, and — through [TeamGenerationRules] — how big their parties are
 * and how far evolved. An authored fight ([TrainerEntry]) still has the old problem, which is an
 * argument for putting authored fights at a fixed wave rather than in a wide band.
 *
 * A band consequently carries **no level of its own**. One added here would silently compete with
 * [com.cobblemonroguelite.wave.WaveLevelCurve], and a run's difficulty would stop being the single
 * function of wave index that [WaveComposition] exists to keep it.
 *
 * @property id an author-facing name, used only in validation messages and logs. It is what tells
 *   someone reading `no boss band covers wave 150` which of their bands to widen.
 * @property kind which wave kind this band serves. Required, and deliberately not nullable the way
 *   [com.cobblemonroguelite.composition.RewardBand.kind] is: reward routing is first-match-wins with
 *   no obligation to cover anything, whereas these bands must tile the trainer waves and the boss
 *   waves *separately*, and a band matching "either kind" makes both the overlap check and the gap
 *   check impossible to state, let alone report usefully.
 * @property maxWave inclusive, null for open-ended — the same convention as `RewardBand` and
 *   `RewardEntry`, because an author meets all three.
 * @property trainers ids of authored trainers, in the order written.
 *
 *   **Editing a pool re-points runs already in progress.** Selection scales a uniform draw by the
 *   pool size and indexes into this list, so *any* edit — appending included, since it changes the
 *   size — moves which trainer some future wave of an existing run will meet. Appending is not the
 *   safe operation it looks like, and this is stated because it looks like one.
 *
 *   That is the same class of thing as editing a reward table mid-run and is accepted for the same
 *   reason: the alternative is snapshotting every pool into every run's save data, which is state we
 *   would then have to version. What it does *not* do is break a run — the draw stays deterministic
 *   from the checkpoint onward, it just answers from the new pool.
 *
 *   A repeated id is legal and is the pool's only weighting mechanism — listing a trainer twice
 *   doubles its share. That is deliberate rather than a `weight` field: a weight invites per-trainer
 *   tuning inside a structure whose entire job is "which one", and pools this small say the same
 *   thing by repetition without a second number to keep consistent.
 */
data class TrainerBand(
    val id: String,
    val kind: RunOpponent,
    val minWave: Int,
    val maxWave: Int? = null,
    val trainers: List<ResourceLocation>,
) {
    init {
        require(minWave >= 1) { "minWave must be at least 1, was $minWave" }
        require(maxWave == null || maxWave >= minWave) {
            "maxWave ($maxWave) is before minWave ($minWave), so this band could never match"
        }
        require(kind != RunOpponent.WILD) { "a wild wave has no authored trainer; bands are trainer or boss only" }
        // A rival band is a contradiction, not an unsupported case: a band's entire job is "which of
        // these turns up here", and §2.36's rival has no which — it is one character on a fixed
        // schedule. Allowed, it would let a run meet stage 4 of the rival at wave 35 with a
        // two-Pokémon team, out of order, as an ordinary trainer. See [RivalLadder].
        require(kind != RunOpponent.RIVAL) {
            "a rival is met on a schedule, not drawn from a pool — put it in the roster's 'rival' " +
                "block rather than in a band"
        }
    }

    fun covers(wave: Int): Boolean = wave >= minWave && (maxWave == null || wave <= maxWave)

    /** For validation messages — `waves 60-119` or `waves 120+`. */
    internal fun rangeText(): String = if (maxWave == null) "waves $minWave+" else "waves $minWave-$maxWave"
}

/**
 * One wave pinned to one trainer, beating whatever the bands and the interval schedule would say.
 *
 * ### Why this is not just "a band of width one"
 *
 * Because the waves it has to reach are not trainer waves. PokéRogue's Elite Four sit at waves
 * 182/184/186/188 with the champion at 190 (§2.7), and under a 5/10 schedule *four of those five are
 * ordinary wild waves* — not boss waves, not even trainer waves. A band cannot serve a wave the
 * schedule never routes to it, so expressing that ladder needs a mechanism that overrides the
 * schedule itself rather than one that decorates it.
 *
 * ### Why [kind] is optional, and what the two cases mean
 *
 * They are different intentions and they must not share a spelling:
 *
 * - **Omitted** — replace *this* wave's trainer, leaving the schedule alone. Only meaningful on a
 *   wave the schedule already makes TRAINER or BOSS; on a wild wave it would silently never fire,
 *   so [TrainerRoster.validate] reports it. This is the common case (pin a specific rival to wave
 *   50) and the one where a typo'd wave number is otherwise invisible.
 * - **Declared** — *promote* this wave to that kind, and fight this trainer. This is the E4 case,
 *   and it is opt-in precisely so that writing 183 instead of 182 is still an error rather than a
 *   feature. Declaring the kind is the author stating they know the wave is not scheduled as one.
 *
 * Promotion is not free and is not applied here: a promoted wave is no longer catchable, and a wave
 * promoted to BOSS wants the ×1.2 multiplier. [TrainerRoster.planFor] applies both. A caller that
 * plans waves through [WaveComposition] alone and only asks this class "who do I fight" will hand a
 * player a catchable Elite Four member at a non-boss level.
 */
data class FixedEncounter(
    val wave: Int,
    val trainerId: ResourceLocation,
    val kind: RunOpponent? = null,
) {
    init {
        require(wave >= 1) { "wave is 1-based, got $wave" }
        require(kind != RunOpponent.WILD) { "promoting a wave to WILD would mean 'fight nothing'; omit kind instead" }
        // Promoting a wave to RIVAL would make it a rival wave with no ladder behind it: no meeting
        // index, so no party size and no team. The rival block is the only thing that can say which
        // meeting a wave is, so it is the only thing allowed to declare one.
        require(kind != RunOpponent.RIVAL) {
            "a rival wave is declared by the roster's 'rival' block, which is what knows WHICH meeting " +
                "it is — a fixed encounter cannot promote a wave to a rival"
        }
    }
}

/**
 * Where a [TrainerPick] came from. Carried so a log line can say *why* wave 182 was that trainer.
 *
 * [RIVAL] is the answer for a §2.36 meeting, and it is worth telling apart from [FIXED] even though
 * both are "the author pinned this wave": a rival's *team* comes from the ladder rather than from
 * [TrainerRoster.generated], so a report of the wrong team at wave 95 is checked against a different
 * block of the file depending on which of the two this says.
 */
enum class TrainerPickSource { BAND, FIXED, RIVAL }

/**
 * The answer to "who does wave N fight".
 *
 * A trainer **id**, never a trainer. Resolving it — and summoning the NPC — happens behind
 * [com.cobblemonroguelite.run.RunWaveHandler], on the far side of RCTmod's soft dependency
 * (§1.2/§2.6: their licence is unverified, so nothing in this module may compile against `rctapi`).
 * Keeping the roster at the id level is what lets this whole layer be unit-tested with no server, no
 * RCT and no datapack — and what keeps a licence question from reaching into the run loop.
 *
 * Nothing here can tell you whether the id names a trainer that exists. It cannot: the only registry
 * that knows is RCT's, which this module is not allowed to see. An id naming nothing is therefore a
 * *summon-time* failure, reported by the layer that tried to summon it.
 */
data class TrainerPick(
    val trainerId: ResourceLocation,
    val source: TrainerPickSource,
    /** Band this came from, or null for a fixed encounter. */
    val bandId: String?,
)

/**
 * Which authored trainer a trainer or boss wave fights.
 *
 * ### Three mechanisms, and the precedence between them is fixed
 *
 * [bands] are the standing rule: a wave range holds a pool, and a wave draws from it. [fixed] is the
 * exception, checked first — see [FixedEncounter] for why a schedule this mode is aimed at cannot be
 * written without it. [rival] is §2.36's, checked between them, and it is a third mechanism rather than
 * a use of the second because a rival is not a wave-shaped fact: it is one character across six waves
 * whose team at the last is constrained by the first (see [RivalLadder]).
 *
 * **Order is fixed → rival → band, and only the first pair is a real choice.** A `fixed` entry on a
 * rival meeting wave is rejected by [validate], so in a loaded roster the two cannot both claim a wave;
 * fixed is still checked first so that its documented "beats everything" contract needs no exception,
 * and so a roster built in code rather than parsed behaves predictably instead of by field order.
 *
 * **The schedule itself is not authored here.** This ships the mechanism and one obvious example;
 * which trainer sits at which wave is content, and §2.7 keeps the transcribed roster out of the mod
 * entirely — our server's lives in a server-side datapack, a published build ships a neutral one.
 *
 * ### Selection is a function of (seed, wave), like everything else a run rolls
 *
 * Same guarantee as wild species and the starter offer (§2.3): pulling the plug must not reroll the
 * opponent. A run checkpointed at wave 44 and resumed a week later meets the same trainer at wave
 * 45 — on a different JVM, after a mod update — because the draw is [WaveRandom] over a salted
 * stream and not a `Random()` at summon time. The failure that prevents is not theoretical: an
 * opponent rolled at summon time is rerollable by disconnecting on the loading screen, which is the
 * exploit the run seed exists to close.
 *
 * The draw takes the seed and the wave and *nothing else*: not the player, not the party, not the
 * previous trainer. That last omission is the notable one — nothing stops waves 45 and 50 drawing
 * the same trainer, because avoiding it needs per-run memory of who has been met, which is run state
 * and belongs in `run/`. §2.19 makes it a real content concern (20 trainer waves against a small
 * pool means visible repeats), so it is named here rather than quietly left.
 *
 * ### Two kinds of fight, told apart by [generated] and nothing else
 *
 * A band and a fixed encounter name a trainer **id**, and always have. Whether that fight is
 * generated or authored is decided by whether [generated] holds an entry for the id — not by where
 * the id appears, and not by a flag on the band. That is what lets one leader sit in a trainer band
 * and a boss band without two copies of its signature species to keep in step, and it is why adding
 * §2.30's generation to this format broke nothing that was already written: a roster with no
 * [generated] block is exactly the roster it was before, every fight authored.
 *
 * @property authoredFor the schedule this roster was written against, used by [validate] to work out
 *   which waves it is obliged to cover. It is **not** the schedule a run uses — that is the run's
 *   own [WaveCompositionConfig] — which is why [validate] takes a composition and this is only its
 *   default. When the two disagree the run's is the truth, and re-running [validate] against the
 *   live config is how that gets said out loud.
 * @property generated trainer id → the signature species its team is built from. An id absent from
 *   here is an authored fight: the trainer's own RCT team is fought as written, which is how the
 *   Elite Four, the champion and any curated rival stay hand-made (§2.30).
 * @property generation the dials generation reads — party size by band, evolution stage by wave, held
 *   items. Shipped defaults are §2.30's numbers with no items; see [TeamGenerationRules].
 * @property rival §2.36's rival ladder, or null for a roster with no rival. Null is the ordinary state
 *   of every roster written before this existed and is not a hole: a run with no rival is a run of
 *   trainers and bosses, which is what §2.14 describes on its own. [validate] therefore says nothing
 *   about a missing ladder — it is the one absent block that is not evidence of a mistake.
 */
data class TrainerRoster(
    val id: ResourceLocation,
    val authoredFor: WaveCompositionConfig,
    val bands: List<TrainerBand>,
    val fixed: Map<Int, FixedEncounter>,
    val generated: Map<ResourceLocation, TrainerEntry> = emptyMap(),
    val generation: TeamGenerationRules = TeamGenerationRules(),
    val rival: RivalLadder? = null,
) {

    /**
     * The team [trainerId] brings to [wave], or null when this is an authored fight.
     *
     * Null is the answer for every trainer this roster does not generate, and it means "fight the RCT
     * trainer's own team" — the behaviour of every roster written before §2.30. Callers must not treat
     * it as a failure and must not substitute anything: an authored Elite Four member replaced by a
     * generated team would be the one fight in the run that somebody tuned, silently regenerated.
     *
     * [level] and [boss] come from the wave's [WavePlan] — the plan produced by [planFor], so
     * promotions are already applied. Passing the *scheduled* kind instead would give a promoted Elite
     * Four wave the ordinary trainer item tier.
     *
     * ### The rival is answered first, and matched on the id as well as the wave
     *
     * A §2.36 meeting is built by [RivalTeamGenerator] from [rival] rather than from [generated], so the
     * ladder is consulted before the signature entries. The id has to match too, which is not
     * belt-and-braces: [fixed] beats the ladder by design, so a (validation-rejected, but constructible
     * in code) roster with a fixed entry on a meeting wave would hand this the *fixed* trainer, and
     * handing back the rival's team for it would give some Elite Four member the rival's growing party.
     */
    fun teamFor(trainerId: ResourceLocation, wave: Int, level: Int, boss: Boolean, seed: Long): GeneratedTeam? {
        val ladder = rival
        if (ladder != null && ladder.meetingAt(wave)?.trainerId == trainerId) {
            return RivalTeamGenerator.generate(ladder, wave, level, seed, generation)
        }
        return generated[trainerId]?.let {
            TrainerTeamGenerator.generate(it, wave, level, boss, seed, generation)
        }
    }

    /**
     * Who [wave] fights, or null when this roster serves it nothing.
     *
     * Null covers three cases the caller cannot distinguish: the wave is wild and wants no trainer,
     * no band covers it (a roster hole — [validate] said so at load), or a covering band's pool is
     * empty (rejected at parse, so only reachable from a roster built in code). All three mean "do
     * not summon a trainer", which is the only decision the caller has to make.
     *
     * [kind] is the wave's kind *as the run sees it*. Pass [effectiveKind]'s answer, not
     * [WaveComposition.kindOf]'s, if promotions are in play — though a promoted wave resolves
     * through [fixed] before [kind] is looked at, so the common path is forgiving.
     */
    fun pickFor(wave: Int, kind: RunOpponent, seed: Long): TrainerPick? {
        require(wave >= 1) { "wave is 1-based, got $wave" }

        val override = fixed[wave]
        // An override with no declared kind only replaces the trainer on a wave that was already
        // going to be one; on a wild wave it does nothing, matching exactly what validate() warns
        // about. Firing it anyway would make the validator a liar and would promote waves the author
        // never asked to promote.
        if (override != null && (override.kind != null || kind != RunOpponent.WILD)) {
            return TrainerPick(override.trainerId, TrainerPickSource.FIXED, bandId = null)
        }

        // Unconditional on [kind], unlike the undeclared-kind branch above. A rival meeting always
        // promotes — there is no "replace the trainer on this wave but leave it a trainer wave" reading
        // of a ladder — so a caller that passed the scheduled kind for wave 8 still gets the rival
        // rather than nothing, which matches how a declared promotion resolves before [kind] is read.
        rival?.meetingAt(wave)?.let {
            return TrainerPick(it.trainerId, TrainerPickSource.RIVAL, bandId = null)
        }

        if (kind == RunOpponent.WILD) return null
        val band = bandFor(wave, kind) ?: return null
        if (band.trainers.isEmpty()) return null

        val rng = WaveRandom.forDraw(seed, wave, WaveDrawStream.TRAINER)
        // Truncating a uniform double rather than taking a modulo of nextLong: modulo of a 64-bit
        // draw is biased toward low indices for any pool size that does not divide 2^64 — invisible
        // in play, and wrong in the one test that counts. coerceAtMost only guards the 0.9999… case.
        val index = (rng.nextDouble() * band.trainers.size).toInt().coerceAtMost(band.trainers.lastIndex)
        return TrainerPick(band.trainers[index], TrainerPickSource.BAND, band.id)
    }

    /** Just the id, for callers that do not care where it came from. */
    fun trainerFor(wave: Int, kind: RunOpponent, seed: Long): ResourceLocation? =
        pickFor(wave, kind, seed)?.trainerId

    /**
     * The band serving [wave], or null for a hole.
     *
     * First match wins, so an author reads precedence top-down the way they do in
     * [com.cobblemonroguelite.composition.RewardRouting] — but unlike reward routing, overlapping
     * bands are a *validation error* rather than a resolution rule. Two bands covering one wave means
     * the second one's pool silently never appears there, which is indistinguishable from having
     * authored it correctly until somebody counts.
     */
    fun bandFor(wave: Int, kind: RunOpponent): TrainerBand? =
        bands.firstOrNull { it.kind == kind && it.covers(wave) }

    fun isFixed(wave: Int): Boolean = fixed.containsKey(wave)

    /** True on one of §2.36's rival waves. False for every roster with no [rival] block. */
    fun isRivalMeeting(wave: Int): Boolean = rival?.isMeeting(wave) == true

    /**
     * The kind [wave] actually is once promotions are applied, given what the schedule said.
     *
     * Exists because [WaveComposition] cannot answer it: the composition is a pure function of wave
     * number and config and knows nothing about datapack content, which is right — which waves are
     * bosses must not depend on which roster a server happens to have loaded. So the promotion is
     * resolved here, at the point where both facts are available. §2.36's rival waves are the second
     * reason and the stronger one: five of the six are *scheduled* trainer waves and one is a wild wave,
     * so nothing derivable from the interval can tell them apart from their neighbours.
     *
     * A [fixed] entry wins over the ladder, including the undeclared-kind form that only replaces the
     * trainer. That is the "fixed beats everything" contract kept without an exception; the combination
     * is a [validate] error, so a loaded roster never exercises it.
     */
    fun effectiveKind(wave: Int, scheduled: RunOpponent): RunOpponent {
        fixed[wave]?.let { return it.kind ?: scheduled }
        if (isRivalMeeting(wave)) return RunOpponent.RIVAL
        return scheduled
    }

    /**
     * [WaveComposition.planFor] with this roster's promotions applied.
     *
     * Provided because promotion has three consequences and missing any one of them is a silent bug:
     * the kind changes, the wave stops being catchable (§2.14 — a catchable Elite Four member ends
     * up in someone's party), and a wave promoted to BOSS wants the ×1.2 level multiplier that
     * [WaveComposition] only applies to waves it *knows* are bosses. Leaving the caller to reassemble
     * that from [effectiveKind] would work exactly until one of the three was forgotten.
     *
     * The level is re-derived from the same curve on the same `(seed, wave, LEVEL)` stream, so a
     * promoted wave's level is what that wave would have been had the schedule made it a boss —
     * not a second opinion about it.
     *
     * **A rival promotion is handled by the same three lines and takes no level change**, which is the
     * decision rather than a gap in the code: `boss` below is `kind == BOSS`, [RunOpponent.RIVAL] is not
     * BOSS, so a rival gets the ordinary trainer level and — through
     * [TeamGenerationRules.bossShieldsFor] — no §2.32 shields. [RivalLadder] argues both. What the
     * promotion *does* change for a rival is the two things that matter: wave 8 stops being catchable,
     * and the kind is right everywhere downstream.
     *
     * The reward table is left as the composition routed it. Routing rewards by *scheduled* kind
     * when a wave has been promoted is arguably wrong, but reward routing is §2.12's owner's call and
     * silently re-pointing their table from here would be a balance change made by a roster file.
     */
    fun planFor(wave: Int, seed: Long, composition: WaveComposition): WavePlan {
        val base = composition.planFor(wave, seed)
        val kind = effectiveKind(wave, base.kind)
        if (kind == base.kind) return base
        return base.copy(
            kind = kind,
            level = composition.config.curve.levelFor(
                wave = wave,
                boss = kind == RunOpponent.BOSS,
                rng = WaveRandom.forDraw(seed, wave, WaveDrawStream.LEVEL),
            ),
            catchable = kind == RunOpponent.WILD,
        )
    }

    /**
     * Everything wrong with this roster *as a schedule*, one message per problem, empty when sound.
     *
     * ### Why this runs at load and not at the wave
     *
     * A roster hole is invisible until a run reaches it. Discovering at wave 147 — hours into
     * somebody's multi-session run (§2.19) — that no band covers wave 150 costs a player their run
     * and costs the operator a bug report with no reproduction. All of it is decidable from the file
     * plus the schedule, so it gets decided the moment the file is read.
     *
     * ### Why it takes a composition instead of just reading [authoredFor]
     *
     * Because the two can differ, and the difference is the interesting case. The intervals are a
     * live tuning dial ([WaveCompositionConfig] is explicit that they will be retuned against real
     * play), so an operator moving the boss interval from 10 to 7 changes which waves need a boss
     * roster and can open a hole in a file nobody edited. Re-running this against the live config
     * turns that into a startup message; validating only against [authoredFor] would check the
     * roster against assumptions nobody is using any more and report clean.
     *
     * Messages name the band or the wave and say what breaks, not what the rule is: the reader has
     * the file open and needs to know which line to change.
     */
    fun validate(composition: WaveComposition = WaveComposition(authoredFor)): List<String> {
        val problems = mutableListOf<String>()

        bands.filter { it.trainers.isEmpty() }.forEach {
            problems += "band '${it.id}' (${it.kind.name.lowercase()}, ${it.rangeText()}) has no trainers, " +
                "so a wave landing in it would have nothing to fight"
        }

        for (kind in listOf(RunOpponent.TRAINER, RunOpponent.BOSS)) {
            reportOverlaps(kind, bands.filter { it.kind == kind }, problems)
            reportGaps(kind, composition, problems)
        }
        reportFixedProblems(composition, problems)
        reportRivalProblems(composition, problems)
        reportUnusedGenerated(problems)
        return problems
    }

    /**
     * Everything wrong with the §2.36 ladder, and nothing at all when there is no ladder.
     *
     * A roster with no rival is complete, not incomplete — §2.14's mode is trainers, bosses and wild
     * waves, and the rival is an addition. So the absent block is silent, unlike an absent band.
     *
     * The four checks are the four ways a ladder can be written and not work, and each of them is
     * otherwise invisible in play:
     *
     * - **A meeting past the end of the run** never fires, the same as a fixed encounter past the end.
     *   Under the shipping 200-wave schedule the last meeting sits at wave 195, five waves from the end,
     *   so an operator who shortens `run_length` to 150 silently deletes two meetings — which is exactly
     *   the case the message is written for.
     * - **A meeting sharing a wave with a [fixed] entry** is an author asking for two opponents at once.
     *   [pickFor] resolves it in fixed's favour, so the *symptom* is a missing rival meeting and a rival
     *   whose team jumps two sizes at the next one. Rejected rather than resolved: neither reading is
     *   obviously the intent, and last-wins would depend on which block was written first.
     * - **A meeting on a scheduled boss wave** removes a boss from the run, because RIVAL is not BOSS.
     *   Reported for the same reason [reportFixedProblems] reports the trainer-over-boss case: §2.19
     *   sizes the whole roster against the count of boss battles.
     * - **A stage id that is also in a band or in [generated]** is the one that costs real time to
     *   diagnose. A rival stage listed in a band can be *drawn* at an ordinary trainer wave, out of
     *   order, with the two-Pokémon team of meeting one; a rival stage with a signature entry has a team
     *   that is dead on its own meeting waves ([teamFor] answers from the ladder) and live anywhere
     *   else. Both look like the roster working until somebody notices the rival turned up twice.
     */
    private fun reportRivalProblems(composition: WaveComposition, problems: MutableList<String>) {
        val ladder = rival ?: return
        val runLength = composition.config.runLength
        val pooled = bands.flatMapTo(mutableSetOf()) { it.trainers }

        ladder.meetings.forEachIndexed { index, meeting ->
            val wave = meeting.wave
            if (wave > runLength) {
                problems += "rival meeting ${index + 1} is at wave $wave, past the end of the run " +
                    "(run_length=$runLength), so that meeting never happens and the rival's team stops " +
                    "growing at meeting ${index}"
                return@forEachIndexed
            }
            if (isFixed(wave)) {
                problems += "wave $wave is both rival meeting ${index + 1} and a fixed encounter " +
                    "(${fixed[wave]?.trainerId}) — the fixed encounter wins, so the meeting would " +
                    "silently not happen. Move one of them"
            }
            if (composition.kindOf(wave) == RunOpponent.BOSS) {
                problems += "rival meeting ${index + 1} is at wave $wave, which this schedule makes a " +
                    "boss wave (boss every ${composition.config.bossInterval}) — a rival is not a boss, " +
                    "so this removes a boss from the run and the meeting takes no boss multiplier and " +
                    "no shields. Move the meeting, or write that wave as a fixed boss instead"
            }
            if (meeting.trainerId in pooled) {
                problems += "rival stage '${meeting.trainerId}' (meeting ${index + 1}) is also in a band " +
                    "pool, so an ordinary trainer wave can draw it — the same character out of order, " +
                    "with the wrong meeting's team. A rival belongs only in the rival block"
            }
            if (meeting.trainerId in generated) {
                problems += "rival stage '${meeting.trainerId}' (meeting ${index + 1}) also has a " +
                    "generated entry, which its own meeting will never use — a rival's team comes from " +
                    "the rival block's 'teams'. Delete the generated entry"
            }
        }

        reportShortRivalTeams(ladder, runLength, problems)
    }

    /**
     * Rival teams that cannot fill the party their last reachable meeting asks for.
     *
     * §2.36's rival is defined by *gaining* a Pokémon each meeting, so a team one slot short does not
     * fail — it plateaus, and the last meeting is the same size as the one before it. That is the whole
     * mechanic quietly not happening at the point in a run where it was supposed to pay off, and no
     * other check can see it: the team parses, the ladder fires, the battle starts.
     *
     * Measured against the deepest **reachable** meeting rather than the last one written, so an
     * operator who shortens the run is not also told to lengthen teams for meetings they just deleted.
     * The out-of-run meetings are already reported on their own footing above.
     */
    private fun reportShortRivalTeams(ladder: RivalLadder, runLength: Int, problems: MutableList<String>) {
        val reachable = ladder.meetings.indices.filter { ladder.meetings[it].wave <= runLength }
        val deepest = reachable.maxOrNull() ?: return
        val wanted = ladder.partySizeAt(deepest)
        ladder.teams.filter { it.slots.size < wanted }.forEach { team ->
            problems += "rival team '${team.id}' has ${team.slots.size} slots but meeting " +
                "${deepest + 1} (wave ${ladder.meetings[deepest].wave}) asks for $wanted — the rival " +
                "would arrive with ${team.slots.size} Pokémon and stop growing, which is the one thing " +
                "a rival is supposed to do"
        }
    }

    /**
     * Signature entries no band and no fixed encounter names.
     *
     * Dead data, and the specific way it dies is worth catching: a generated entry is joined to a
     * fight by **id**, so `rgl_brock` in the entries and `rgl_borck` in a band is a roster where Brock
     * loads clean, appears on schedule, and fights his authored RCT placeholder team instead of the
     * generated one. Nothing else in the pipeline can notice — the id resolves, the trainer exists,
     * the battle starts. Only this comparison can.
     *
     * Reported rather than fatal on its own footing: the parser rejects a file with any problem in it
     * ([TrainerRosters]), which is the intended severity. A roster sharing one entry block across two
     * files is not a use case this format has.
     */
    private fun reportUnusedGenerated(problems: MutableList<String>) {
        if (generated.isEmpty()) return
        // Rival stage ids count as named, so a generated entry for one is not reported here as well.
        // It is a mistake — [reportRivalProblems] says so, more precisely — and one mistake earning two
        // messages is how the sharper of the two gets skimmed past.
        val named = bands.flatMapTo(mutableSetOf()) { it.trainers } +
            fixed.values.map { it.trainerId } +
            (rival?.trainerIds() ?: emptySet())
        generated.keys.filterNot { it in named }.sortedBy { it.toString() }.forEach { id ->
            problems += "generated entry '$id' is never fought: no band lists it and no fixed " +
                "encounter names it, so its signature species do nothing. Check the spelling against " +
                "the band pools — a near-miss id fights the trainer's authored team instead"
        }
    }

    /**
     * Two bands of one kind covering a common wave.
     *
     * Reported per pair rather than per wave: an author who wrote `1-60` and `50-120` has made one
     * mistake, and sixty lines about it would bury every other problem in the file.
     */
    private fun reportOverlaps(kind: RunOpponent, ofKind: List<TrainerBand>, problems: MutableList<String>) {
        for (i in ofKind.indices) {
            for (j in i + 1 until ofKind.size) {
                val a = ofKind[i]
                val b = ofKind[j]
                val from = maxOf(a.minWave, b.minWave)
                val to = when {
                    a.maxWave == null && b.maxWave == null -> null
                    a.maxWave == null -> b.maxWave
                    b.maxWave == null -> a.maxWave
                    else -> minOf(a.maxWave, b.maxWave)
                }
                if (to != null && to < from) continue
                problems += "${kind.name.lowercase()} bands '${a.id}' (${a.rangeText()}) and '${b.id}' " +
                    "(${b.rangeText()}) both cover ${rangeText(from, to)}, where '${b.id}' can never be " +
                    "drawn — the first matching band wins"
            }
        }
    }

    /**
     * Waves of [kind] this roster cannot answer for.
     *
     * Only waves the schedule actually produces are required, which is why this needs the composition
     * and not just a run length: demanding a boss band over waves 1-9 under a boss interval of 10
     * would force an author to write a pool that can never be drawn, and content written to satisfy
     * a validator is content nobody checks.
     *
     * A fixed encounter counts as coverage. Wave 190 needing no band is the whole point of the
     * override, and calling it a gap would make the E4 schedule unauthorable without writing filler
     * bands around it.
     *
     * **So does a rival meeting**, and that case is easy to miss because it is not symmetrical with the
     * fixed one. Five of §2.36's six meetings land on *scheduled trainer waves* (25/55/95/145/195 are
     * all multiples of five), so they qualify for the TRAINER pass below and would each be reported as
     * a hole in a roster that is complete — and worse, an author whose trainer bands stop at 190 would
     * be told to extend them to cover a wave the rival already owns.
     */
    private fun reportGaps(kind: RunOpponent, composition: WaveComposition, problems: MutableList<String>) {
        // Adjacency is measured in this list, not in wave numbers. The spacing between waves of one
        // kind is not the interval that produced them — under 5/10 the trainer waves are 5, 15, 25,
        // ten apart, because every other multiple of five is taken by a boss. Grouping by a fixed
        // step therefore reports one missing band as fifteen separate gaps, which is how a real
        // problem ends up scrolled off the top of a log.
        val qualifying = (1..composition.config.runLength).filter { composition.kindOf(it) == kind }
        val uncovered = qualifying.indices.filter { i ->
            val wave = qualifying[i]
            !isFixed(wave) && !isRivalMeeting(wave) && bandFor(wave, kind) == null
        }
        if (uncovered.isEmpty()) return

        var start = 0
        while (start < uncovered.size) {
            var end = start
            while (end + 1 < uncovered.size && uncovered[end + 1] == uncovered[end] + 1) end++
            val from = qualifying[uncovered[start]]
            val to = qualifying[uncovered[end]]
            problems += "no ${kind.name.lowercase()} band covers ${rangeText(from, to)} — " +
                "a run reaching wave $from would have no opponent"
            start = end + 1
        }
    }

    /**
     * Fixed encounters that can never fire.
     *
     * The undeclared-kind-on-a-wild-wave case is the one that earns this method. An author
     * transcribing a ladder writes 182, 184, 186, 188, 190; a typo'd 183 looks exactly like the four
     * correct entries around it and would simply never happen, in a part of the run almost nobody
     * reaches to notice. Naming the nearest waves that *would* fire turns a silent no-op into a
     * one-character fix.
     *
     * A declared kind that contradicts the schedule is reported too, at the one place it is
     * destructive: overriding a boss wave down to a plain trainer deletes a boss from the run, and
     * the count of boss battles is the thing §2.19 sizes the whole roster against.
     */
    private fun reportFixedProblems(composition: WaveComposition, problems: MutableList<String>) {
        val config = composition.config
        fixed.values.sortedBy { it.wave }.forEach { entry ->
            val wave = entry.wave
            if (wave > config.runLength) {
                problems += "fixed encounter at wave $wave is past the end of the run " +
                    "(run_length=${config.runLength}) and can never fire"
                return@forEach
            }
            val scheduled = composition.kindOf(wave)
            when {
                entry.kind == null && scheduled == RunOpponent.WILD ->
                    problems += "fixed encounter at wave $wave can never fire: wave $wave is a wild wave under " +
                        "this schedule (trainer every ${config.trainerInterval}, boss every ${config.bossInterval}). " +
                        "Give it \"kind\" to promote the wave, or move it to ${nearestServedWaves(composition, wave)}"

                entry.kind == RunOpponent.TRAINER && scheduled == RunOpponent.BOSS ->
                    problems += "fixed encounter at wave $wave overrides a boss wave down to a trainer wave, " +
                        "which removes a boss from the run — omit \"kind\" to keep it a boss battle"
            }
        }
    }

    /** The trainer/boss waves either side of [wave], for the "did you mean" half of a gap message. */
    private fun nearestServedWaves(composition: WaveComposition, wave: Int): String {
        val runLength = composition.config.runLength
        val below = (wave - 1 downTo 1).firstOrNull { composition.kindOf(it) != RunOpponent.WILD }
        val above = (wave + 1..runLength).firstOrNull { composition.kindOf(it) != RunOpponent.WILD }
        return listOfNotNull(below, above).joinToString(" or ") { "wave $it" }.ifEmpty { "a trainer or boss wave" }
    }

    companion object {
        internal fun rangeText(from: Int, to: Int?): String = when {
            to == null -> "waves $from+"
            to == from -> "wave $from"
            else -> "waves $from-$to"
        }
    }
}
