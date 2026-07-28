package com.cobblemonroguelite.wave

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The curve's *shape*, never its tuning. Every constant in [WaveLevelCurve] is a placeholder, so
 * asserting particular levels here would just cement numbers nobody has signed off on; these tests
 * pin the properties the shape is supposed to have — rising, accelerating, narrowing, bounded —
 * which are what would actually be broken by a bad edit.
 */
class WaveLevelCurveTest {

    private val curve = WaveLevelCurve()

    private fun rng(wave: Int) = WaveRandom.forDraw(99L, wave, WaveDrawStream.LEVEL)

    @Test
    fun `mean level rises with wave`() {
        val means = (1..30).map { curve.meanLevelAt(it) }
        assertTrue(means.zipWithNext().all { (a, b) -> b > a }, "mean level was not monotonic: $means")
    }

    @Test
    fun `the tail accelerates rather than staying linear`() {
        // The quadratic term's whole purpose: a late wave gains more than an early one.
        val earlyStep = curve.meanLevelAt(3) - curve.meanLevelAt(2)
        val lateStep = curve.meanLevelAt(30) - curve.meanLevelAt(29)
        assertTrue(lateStep > earlyStep, "late step $lateStep was not steeper than early step $earlyStep")
    }

    @Test
    fun `boss waves scale off the same curve`() {
        val plain = curve.meanLevelAt(10, boss = false)
        assertEquals(plain * curve.bossMultiplier, curve.meanLevelAt(10, boss = true), 1e-9)
    }

    @Test
    fun `spread narrows as waves deepen and never reaches zero`() {
        val spreads = (1..40).map { curve.spreadAt(it) }
        assertEquals(curve.spreadAtWaveOne, spreads.first(), 1e-9)
        assertTrue(spreads.zipWithNext().all { (a, b) -> b < a }, "spread did not narrow: $spreads")
        assertTrue(spreads.all { it >= curve.spreadFloor }, "spread fell below its floor")
    }

    @Test
    fun `jitter never exceeds the sigma clamp`() {
        // The reason the clamp exists: an unbounded normal would occasionally hand an early wave a
        // level far over curve, which is rare enough to survive playtesting and fatal in a run.
        for (wave in 1..20) {
            val bound = curve.clampSigma * curve.spreadAt(wave) + 0.5 // +0.5 for the rounding to int
            val mean = curve.meanLevelAt(wave)
            repeat(2_000) {
                val level = curve.levelFor(wave, boss = false, rng = rng(wave + it * 100))
                assertTrue(abs(level - mean) <= bound, "wave $wave produced level $level against mean $mean")
            }
        }
    }

    @Test
    fun `levels are clamped into the configured band`() {
        val tight = WaveLevelCurve(baseLevel = 1.0, linearPerWave = 40.0, minLevel = 5, maxLevel = 20)
        assertEquals(20, tight.levelFor(10, boss = false, rng = rng(10)))
        val sunken = WaveLevelCurve(baseLevel = -50.0, linearPerWave = 0.0, minLevel = 5, maxLevel = 20)
        assertEquals(5, sunken.levelFor(1, boss = false, rng = rng(1)))
    }

    @Test
    fun `a curve that would divide by zero is refused at construction`() {
        // These would produce NaN levels, which round to 0 and clamp silently — a config typo would
        // present as "every opponent is level 1" with nothing in the log.
        assertFailsWith<IllegalArgumentException> { WaveLevelCurve(quadraticDivisor = 0.0) }
        assertFailsWith<IllegalArgumentException> { WaveLevelCurve(spreadNarrowingWaves = 0.0) }
        assertFailsWith<IllegalArgumentException> { WaveLevelCurve(minLevel = 40, maxLevel = 10) }
    }
}
