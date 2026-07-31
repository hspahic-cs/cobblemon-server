package com.cobblemonroguelite.shop

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow

/**
 * What a wave is worth, and therefore what things cost at it.
 *
 * ### One curve behind both halves of the economy
 *
 * PokéRogue derives the entire economy from a single per-wave number: it is what a trainer pays, and
 * it is simultaneously the base every shop price is a multiple of. That is the property worth copying
 * — not the constants. A shop priced off the same curve that pays you cannot drift out of reach as a
 * run deepens, because the thing that grew is the same thing on both sides. Two independent tables
 * (ours, until now: flat prices and a linear credit ramp) drift by construction, and the drift shows
 * up as a shop that is trivial at wave 20 and unaffordable at wave 120.
 *
 * See `docs/roguelite-economy-reference.md` for the original, read out of their source.
 *
 * ### Two things about the shape that are easy to miss
 *
 * **It ramps within each block of ten, not just between them.** The `(((wave - 1) % 10) + 1) / 10`
 * term means wave 19 is worth noticeably more than wave 11, so the run gets richer continuously
 * rather than in steps at each boss. A curve that only stepped per block would make waves 11–19 feel
 * identical and then jump.
 *
 * **It is superlinear.** The exponent itself grows with depth (`1 + 0.005 × setIndex`), so wave 100 is
 * worth much more than ten times wave 10. That is what keeps late shop items meaningful instead of
 * pocket change, and it is why [roundTo] exists — the numbers get big enough that the last digit is
 * noise.
 *
 * ### Why the constants are configurable and default to theirs
 *
 * §2.7 keeps PokéRogue's *data* out of this mod. A formula's shape is mechanism and belongs here; its
 * tuning is theirs, so every number below is a field a server can change rather than a literal buried
 * in the arithmetic. The defaults reproduce their curve because they have the one thing we do not —
 * a lot of people having played it.
 */
data class WaveMoneyCurve(

    /** Scales the whole curve. Their 100. */
    val base: Int = 100,

    /** How much the exponent grows per block of [setLength] waves. Their 0.005. */
    val exponentPerSet: Double = 0.005,

    /** Results are floored to a multiple of this, so prices read as prices. Their 10. */
    val roundTo: Int = 10,

    /** Waves per "set" — the block the ramp resets over, and their boss cadence. */
    val setLength: Int = 10,
) {
    init {
        require(base > 0) { "base must be positive, was $base" }
        require(roundTo >= 1) { "roundTo must be at least 1, was $roundTo" }
        require(setLength >= 1) { "setLength must be at least 1, was $setLength" }
    }

    /**
     * The wave's value, scaled by [multiplier].
     *
     * `multiplier` is how one curve serves several purposes: 1.0 is the shop's base cost, and a
     * trainer pays its own multiple of the same number.
     *
     * Clamped at wave 1 rather than trusted, because a wave index of 0 makes the set index -1 and the
     * exponent less than 1, which quietly inverts the curve instead of failing.
     */
    fun amountAt(wave: Int, multiplier: Double = 1.0): Int {
        val safeWave = wave.coerceAtLeast(1)
        val setIndex = ceil(safeWave.toDouble() / setLength).toInt() - 1
        val withinSet = ((safeWave - 1) % setLength) + 1
        val scale = setIndex + 1 + (0.75 + withinSet.toDouble() / setLength)
        val value = (scale * base).pow(1 + exponentPerSet * setIndex) * multiplier
        if (!value.isFinite() || value <= 0.0) return 0
        // Floored to a multiple, not rounded: a price that rounds up past what the same wave just paid
        // is a shop the player cannot afford on the wave it was priced for.
        return (floor(value / roundTo) * roundTo).coerceAtMost(Int.MAX_VALUE.toDouble()).toInt()
    }
}
