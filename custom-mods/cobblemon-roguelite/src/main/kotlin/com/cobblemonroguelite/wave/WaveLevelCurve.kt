package com.cobblemonroguelite.wave

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The relative strength of one party member, PokéRogue's `PartyMemberStrength` with the same order
 * and therefore the same ordinal arithmetic — [WaveLevelCurve.partyMemberLevel] computes
 * `STRONG - strength`, so reordering these changes levels, not just names.
 *
 * The tiers are how a trainer's party stops being six copies of one level: the ace runs ahead of the
 * curve, the filler drifts below it, and the drift *widens* with depth (the offset term is a function
 * of the wave). That is PokéRogue's difficulty texture for trainer battles and the reason the wave's
 * flat level is not applied to generated teams — see docs/roguelite-trainer-battles.md §1.3.
 */
enum class PartyMemberStrength { WEAKER, WEAK, AVERAGE, STRONG, STRONGER }

/**
 * How opponent level is derived from wave index.
 *
 * The defaults are **PokéRogue's Classic curve, verbatim**: `1 + wave/2 + (wave/25)²`, bosses ×1.2.
 * They were placeholders until §2.19 adopted PokéRogue's 200-wave run length — the original
 * objection to porting the constants was that a curve tuned for 200 waves puts a ~10-wave slice at
 * level 6, and adopting their run length removed it. The curve and the run length now come from the
 * same place, so retuning either alone reintroduces the mismatch from the other end.
 *
 * Not wild-only. Trainer and boss waves scale off the same curve so that the difficulty of a run
 * is one function of wave index rather than three that drift apart. How the curve is *applied*
 * differs by path: wild waves take [levelFor] (jittered, ×[bossMultiplier] on boss waves — their
 * `getLevelForWave`), generated trainer teams take [partyMemberLevel] per member (their
 * `getPartyLevels`: no jitter, no boss multiplier, spread by [PartyMemberStrength]), and authored
 * trainer teams are forced flat to the wave's [levelFor] on the far side of the seam.
 *
 * **The level-100 ceiling is load-bearing, not a safety rail.** Cobblemon's `maxPokemonLevel` is a
 * *global* config value, so it cannot be raised for runs alone. The curve passes 100 at about wave
 * 138 (bosses around 120) and would reach 165 by wave 200, so the last ~30% of a run is flat at 100
 * by decision (§2.19): difficulty there has to come from team quality, held items and boss design.
 * A flat tail in the level column is the intended behaviour, not a clamped bug.
 *
 * **Still placeholders: the jitter fields.** §2.19 fixes the mean curve and says nothing about
 * spread. Note what the defaults do over 200 waves — `spreadNarrowingWaves = 6` was picked for a
 * ~10-wave slice, so jitter sits on its floor from roughly wave 30 onward and the back three
 * quarters of a run has almost no level variance. That is a tuning gap for the balance owner.
 */
data class WaveLevelCurve(
    /** PokéRogue's intercept: their curve is `1 + …`, so wave 0 would sit exactly at 1. */
    val baseLevel: Double = 1.0,

    /** PokéRogue's linear term, `wave / 2`. */
    val linearPerWave: Double = 0.5,

    /**
     * PokéRogue's quadratic tail, `(wave / 25)²` — the term that makes the back half of a run
     * outpace the front. Smaller values make the tail bite sooner and harder.
     */
    val quadraticDivisor: Double = 25.0,

    /**
     * PokéRogue's `bossMultiplier` from `getLevelForWave`, applied to the mean before jitter so a
     * boss is a step up rather than a reroll. Wild-path and authored-path only: their
     * `getPartyLevels` takes no boss multiplier, and neither does [partyMemberLevel] — a generated
     * boss's step up is [PartyMemberStrength] spread plus §2.32 shields, which is their design too.
     */
    val bossMultiplier: Double = 1.2,

    /** PLACEHOLDER. Standard deviation of the level jitter at wave 1, in levels. */
    val spreadAtWaveOne: Double = 3.0,

    /** PLACEHOLDER. Standard deviation the jitter decays toward. Never reaches zero variance. */
    val spreadFloor: Double = 0.5,

    /**
     * PLACEHOLDER. Waves over which the jitter decays from [spreadAtWaveOne] toward [spreadFloor]
     * (one e-folding). Larger keeps the run loose for longer.
     */
    val spreadNarrowingWaves: Double = 6.0,

    /**
     * PLACEHOLDER. Jitter is clamped to this many standard deviations.
     *
     * Not cosmetic. A Gaussian has no bound, so roughly one wave in a few thousand would otherwise
     * hand a wave-2 player something ten levels over curve — rare enough to survive playtesting and
     * common enough to end runs on a live server. Clamping trades an unnoticeable distortion of the
     * tails for a hard worst case.
     */
    val clampSigma: Double = 2.0,

    /** Hard floor on the produced level. */
    val minLevel: Int = 1,

    /**
     * Hard ceiling on the produced level. Cobblemon's own cap is 100, and a server that lowers its
     * `maxPokemonLevel` wants the curve clamped to the new value — which is a config edit here
     * rather than a surprise at spawn time.
     */
    val maxLevel: Int = 100,
) {
    init {
        require(quadraticDivisor > 0.0) { "quadraticDivisor must be > 0 (it is a divisor)" }
        require(spreadNarrowingWaves > 0.0) { "spreadNarrowingWaves must be > 0 (it is a divisor)" }
        require(spreadFloor >= 0.0) { "spreadFloor must be >= 0" }
        require(clampSigma >= 0.0) { "clampSigma must be >= 0" }
        require(minLevel >= 1) { "minLevel must be >= 1" }
        require(maxLevel >= minLevel) { "maxLevel ($maxLevel) must be >= minLevel ($minLevel)" }
    }

    /** The un-jittered level for [wave], before clamping. Exposed so tuning can be inspected in isolation. */
    fun meanLevelAt(wave: Int, boss: Boolean = false): Double {
        val w = wave.toDouble()
        val mean = baseLevel + linearPerWave * w + (w / quadraticDivisor) * (w / quadraticDivisor)
        return if (boss) mean * bossMultiplier else mean
    }

    /**
     * Standard deviation of the jitter at [wave], decaying from [spreadAtWaveOne] toward
     * [spreadFloor]. Wave 1 sits exactly at [spreadAtWaveOne] so the parameter means what it says.
     */
    fun spreadAt(wave: Int): Double {
        val decayed = (spreadAtWaveOne - spreadFloor) *
            StrictMath.exp(-(wave - 1).toDouble() / spreadNarrowingWaves)
        return max(spreadFloor, spreadFloor + decayed)
    }

    /**
     * The level to use for [wave], drawing the jitter from [rng].
     *
     * Takes the stream rather than a seed so the caller controls which draw this is — see
     * [WaveDrawStream]. Consumes exactly two values from it.
     */
    fun levelFor(wave: Int, boss: Boolean, rng: WaveRandom): Int {
        val spread = spreadAt(wave)
        val bound = clampSigma * spread
        val jitter = (rng.nextGaussian() * spread).coerceIn(-bound, bound)
        return (meanLevelAt(wave, boss) + jitter).roundToInt().coerceIn(minLevel, maxLevel)
    }

    /**
     * One party member's level at [wave] — PokéRogue's `getPartyLevels` (their
     * `src/field/trainer.ts` L262–306), ported verbatim; the transcription of record is
     * docs/roguelite-trainer-battles.md §1.3.
     *
     * Deterministic on purpose: no jitter and no draw, because their trainer levels have none —
     * which also means calling this consumes nothing from any [WaveRandom] stream, so wiring it in
     * moved no existing roll.
     *
     * ### What is verbatim and what is not
     *
     * The base is [meanLevelAt] with `boss = false`, which under the default constants **is** their
     * `baseLevel` expression exactly; a server that retunes the curve retunes this with it, which is
     * the point of sharing one function. The `/ 25` in the weak-member catch-up term and the `/ 50`
     * in the offset are **their literals, kept as literals** — the 25 coincides with
     * [quadraticDivisor]'s default but is not the same knob, and tying them together would make a
     * curve retune silently change how fast weak members catch up.
     *
     * ### No boss multiplier, and that is their design
     *
     * Their `getPartyLevels` never applies `bossMultiplier` — that term lives in `getLevelForWave`,
     * the wild path. A boss *trainer*'s step up is party composition ([PartyMemberStrength] spread)
     * plus shields, not a flat ×1.2. A generated boss team built from this is therefore lower-mean
     * than the authored path's forced level on the same wave, and that divergence is recorded rather
     * than accidental.
     */
    fun partyMemberLevel(wave: Int, strength: PartyMemberStrength): Int {
        val w = wave.toDouble()
        val base = meanLevelAt(wave, boss = false)
        var multiplier = when (strength) {
            PartyMemberStrength.WEAKER -> 0.95
            PartyMemberStrength.WEAK -> 1.0
            PartyMemberStrength.AVERAGE -> 1.1
            PartyMemberStrength.STRONG -> 1.2
            PartyMemberStrength.STRONGER -> 1.25
        }
        var levelOffset = 0
        if (strength < PartyMemberStrength.STRONG) {
            // Weak members slowly catch up in multiplier (capped at STRONG's 1.2) while drifting
            // further below the curve in absolute offset — both terms theirs, see the KDoc.
            multiplier = min(multiplier + 0.025 * floor(w / 25.0), 1.2)
            levelOffset = -floor(
                (w / 50.0) * (PartyMemberStrength.STRONG.ordinal - strength.ordinal),
            ).toInt()
        }
        return (ceil(base * multiplier).toInt() + levelOffset).coerceIn(minLevel, maxLevel)
    }
}
