package com.cobblemonroguelite.wave

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * How opponent level is derived from wave index.
 *
 * The *shape* is PokéRogue's (plan §2.14): a linear ramp with a quadratic tail so the back half of
 * a run outpaces the front, a multiplier on boss waves, and a jitter that narrows as waves deepen —
 * early waves feel varied, late waves stop being a coin flip about whether the opponent outspeeds.
 * Their constants are `1 + wave/2 + (wave/25)^2` and are **not** ported: that curve is tuned for a
 * 200-wave run, so on our ~10-wave slice it would put wave 1 at level 6 and wave 10 at level 6+.
 *
 * Not wild-only. Trainer and boss waves scale off the same curve so that the difficulty of a run
 * is one function of wave index rather than three that drift apart; the difference between wave
 * kinds is [bossMultiplier] and how the level is applied, not the curve.
 *
 * ### Every default below is a placeholder
 *
 * **UNBALANCED PLACEHOLDER VALUES — do not ship a tuning pass on top of these, replace them.**
 * They are round numbers picked to make the shape observable in a short run (wave 1 ≈ 13, wave 10 ≈
 * 42), not to make anything fair. Real numbers depend on what the starter offer hands out and how
 * strong the reward table is, neither of which exists yet. These live here so the generator can be
 * built and tested; the balance owner supplies the values, and this class exists so that supplying
 * them is a config change and not a code change.
 */
data class WaveLevelCurve(
    /** PLACEHOLDER. Level intercept — the linear term is added on top of this from wave 1. */
    val baseLevel: Double = 10.0,

    /** PLACEHOLDER. Levels gained per wave from the linear term. */
    val linearPerWave: Double = 3.0,

    /**
     * PLACEHOLDER. Wave count at which the quadratic tail contributes one level; smaller values
     * make the tail bite sooner and harder. This is the knob that decides whether a long run
     * accelerates or stays flat.
     */
    val quadraticDivisor: Double = 8.0,

    /** PLACEHOLDER. Applied to the mean before jitter, so a boss is a step up rather than a reroll. */
    val bossMultiplier: Double = 1.25,

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

    /** Hard ceiling on the produced level. Cobblemon's own cap is 100. */
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
}
