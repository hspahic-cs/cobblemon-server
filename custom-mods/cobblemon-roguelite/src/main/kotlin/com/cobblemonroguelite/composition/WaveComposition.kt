package com.cobblemonroguelite.composition

import com.cobblemonroguelite.integration.RunOpponent
import com.cobblemonroguelite.wave.WaveDrawStream
import com.cobblemonroguelite.wave.WaveLevelCurve
import com.cobblemonroguelite.wave.WaveRandom
import net.minecraft.resources.ResourceLocation

/**
 * Everything decided about a wave before anything is built for it.
 *
 * Deliberately holds no species, no trainer id and no reward contents: this layer says *what kind of
 * thing* wave N is and how hard it is, and hands off. [com.cobblemonroguelite.wave.WildWaveGenerator]
 * turns a WILD plan into a Pokémon; the trainer path turns a TRAINER or BOSS plan into an authored
 * RCT trainer scaled to [level] (§2.6); the reward layer rolls [rewardTable]. None of those three
 * needs to know the schedule, and this needs to know none of their contents.
 *
 * @property level the opponent's level, from the shared [WaveLevelCurve] with the boss multiplier
 *   already applied where it applies. Trainer and boss waves take this rather than deriving their own
 *   so that a run's difficulty is one function of wave index instead of three that drift apart.
 * @property catchable §2.14's rule, carried as a fact rather than left for each caller to re-derive
 *   from [kind]: wild Pokémon are catchable, trainer-owned ones never are. Getting this wrong on a
 *   boss wave hands a player a run party member they were not supposed to have.
 * @property rewardTable id to roll from, or null when this wave rewards nothing. See [RewardRouting].
 * @property finalWave true on the last wave of the run — the caller's cue to end the run on victory
 *   rather than advance.
 */
data class WavePlan(
    val wave: Int,
    val kind: RunOpponent,
    val level: Int,
    val catchable: Boolean,
    val rewardTable: ResourceLocation?,
    val finalWave: Boolean,
)

/**
 * Decides what each wave of a run *is*.
 *
 * ### The schedule is a function of the wave number alone, never of the seed
 *
 * Which waves are bosses is fixed for every run on the server: wave 10 is a boss in your run and in
 * mine. That is not an implementation convenience, it is the property that makes the mode legible —
 * players compare progress by wave number, a boss roster is authored against known wave indices
 * (§2.7 puts PokéRogue's E4 at 182–188 and their champion at 190, which only means anything if those
 * waves are the same for everyone), and a run resumed from a checkpoint must not re-roll its own
 * structure. So [kindOf] and [rewardTableFor] take no seed at all; the seed enters only at [levelFor],
 * where the jitter lives, and even there it is the same `(seed, wave)` draw the wild generator makes.
 *
 * Nothing here is stateful, so one instance serves every concurrent run — same as
 * [com.cobblemonroguelite.wave.WildWaveGenerator], and for the same reason.
 */
class WaveComposition(val config: WaveCompositionConfig = WaveCompositionConfig()) {

    /**
     * The kind of wave [wave] is.
     *
     * **Boss beats trainer** where the intervals collide, which under PokéRogue's 5/10 is every boss
     * wave. The other order would make wave 10 an ordinary trainer battle and bosses would never
     * appear at all; that is the sort of bug that reads as "the boss roster is unused" three weeks
     * later rather than as a precedence mistake here.
     *
     * **This never answers [RunOpponent.RIVAL]**, and that is the same principle the promotion rule
     * follows rather than a missing branch. §2.36's rival waves are declared by a roster
     * ([com.cobblemonroguelite.data.trainer.RivalLadder]) and this class must not read datapack content
     * — which waves are what has to be the same for every player on the server whatever roster happens
     * to be loaded. A caller that needs the rival waves asks
     * [com.cobblemonroguelite.data.trainer.TrainerRoster.effectiveKind], which is where both facts are
     * available; the note on [waveCount] says what that costs here.
     *
     * Waves past [WaveCompositionConfig.runLength] are still answered rather than refused — see
     * [isBeyondRun].
     */
    fun kindOf(wave: Int): RunOpponent {
        require(wave >= 1) { "wave is 1-based, got $wave" }
        return when {
            wave % config.bossInterval == 0 -> RunOpponent.BOSS
            wave % config.trainerInterval == 0 -> RunOpponent.TRAINER
            else -> RunOpponent.WILD
        }
    }

    /** Whether the player may throw a ball on [wave]. §2.14: wild only. */
    fun isCatchable(wave: Int): Boolean = kindOf(wave) == RunOpponent.WILD

    fun rewardTableFor(wave: Int): ResourceLocation? = config.rewards.tableFor(wave, kindOf(wave))

    /**
     * Opponent level for [wave] of the run seeded with [seed].
     *
     * Calls straight through to [WaveLevelCurve.levelFor] on the same `(seed, wave, LEVEL)` stream the
     * wild generator uses, so a WILD plan's level is *the same number* the generator independently
     * produces for that wave rather than a second opinion about it. Re-deriving the curve here would
     * make that agreement a coincidence that survives until someone edits one of the two copies.
     *
     * The boss multiplier is applied by the curve, not here, which is what keeps a boss a step up on
     * the same shape instead of a separate ramp.
     */
    fun levelFor(wave: Int, seed: Long): Int {
        val kind = kindOf(wave)
        return config.curve.levelFor(
            wave = wave,
            boss = kind == RunOpponent.BOSS,
            rng = WaveRandom.forDraw(seed, wave, WaveDrawStream.LEVEL),
        )
    }

    /** The full plan for [wave]. */
    fun planFor(wave: Int, seed: Long): WavePlan {
        val kind = kindOf(wave)
        return WavePlan(
            wave = wave,
            kind = kind,
            level = levelFor(wave, seed),
            catchable = kind == RunOpponent.WILD,
            rewardTable = config.rewards.tableFor(wave, kind),
            finalWave = wave == config.runLength,
        )
    }

    fun isFinalWave(wave: Int): Boolean = wave == config.runLength

    /**
     * True for a wave the configured run does not contain.
     *
     * Answered instead of thrown because [WaveCompositionConfig.runLength] can be lowered on a live
     * server while somebody is mid-run at a wave that no longer exists. Refusing to compose it would
     * turn an operator's tuning change into a stuck run with a crash in the log; letting the caller
     * see the overrun lets it end the run cleanly. The curve clamps regardless (§2.19's flat tail), so
     * an overrun wave is well-defined, just past the finish line.
     */
    fun isBeyondRun(wave: Int): Boolean = wave > config.runLength

    /**
     * How many waves of [kind] a full run contains.
     *
     * Here because §2.19 makes it a content-scale question, not a curiosity: the authored roster
     * (§2.6's bands) has to be big enough that a player's last trainer battle is not the fourth rerun
     * of the same team, and anyone retuning the intervals should be able to read the new roster
     * requirement off the config instead of working it out.
     *
     * Note that at 200/5/10 the answer is **20 trainer waves and 20 boss waves**, not the "40 trainer
     * battles and 20 boss battles" §2.19 states: 40 is `200/5`, and boss waves are taken out of that
     * count rather than added to it. Whether the plan wanted 60 non-wild waves — which needs the
     * bosses to sit *between* the trainer slots, e.g. a trainer interval that does not divide the
     * boss one — is a design question this class cannot answer for itself.
     *
     * **Always 0 for [RunOpponent.RIVAL]**, because [kindOf] cannot see a roster. That is not a bug to
     * work around here: a rival count is `roster.rival?.meetings?.size`, and answering it from this side
     * would mean the composition reading datapack content. Note the knock-on for the numbers above — a
     * six-meeting rival ladder takes five waves *out* of the trainer count and one out of the wild one,
     * so a 200/5/10 run with §2.36's ladder is 15 trainer + 20 boss + 6 rival + 159 wild rather than the
     * 20/20/160 §2.19 states. Anyone sizing a band pool off this method is sizing it slightly large,
     * which is the harmless direction.
     */
    fun waveCount(kind: RunOpponent): Int = (1..config.runLength).count { kindOf(it) == kind }
}
