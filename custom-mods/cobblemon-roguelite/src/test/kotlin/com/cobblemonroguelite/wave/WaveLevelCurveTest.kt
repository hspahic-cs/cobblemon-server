package com.cobblemonroguelite.wave

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Two kinds of assertion, and the split matters. The mean-curve constants are **PokéRogue's Classic
 * verbatim** (§2.19), so those get pinned to exact values — a drifted constant is a port error, not
 * a tuning choice. The jitter fields are still placeholders, so everything about spread pins only
 * the *shape* — narrowing, bounded, floored — and cementing particular spreads here would just
 * freeze numbers nobody has signed off on.
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

    /** The defaults ARE their curve: `1 + wave/2 + (wave/25)²`, bosses ×1.2. Checked at a round wave. */
    @Test
    fun `the default constants are PokéRogue's Classic verbatim`() {
        assertEquals(30.0, curve.meanLevelAt(50), 1e-9) // 1 + 25 + 4
        assertEquals(36.0, curve.meanLevelAt(50, boss = true), 1e-9)
        assertEquals(100, curve.maxLevel, "the level-100 flat tail is §2.19's decision")
    }

    /**
     * `getPartyLevels`, transcribed values — wave 100, base 67, every strength tier by hand:
     * WEAKER  min(0.95+0.025·4, 1.2)=1.05, offset −⌊2·3⌋=−6 → ceil(70.35)−6 = 65
     * WEAK    1.1, −4 → 70;  AVERAGE 1.2 (capped), −2 → 79;  STRONG ceil(80.4) = 81;
     * STRONGER ceil(83.75) = 84. Strictly rising, which is the property the tiers exist for.
     */
    @Test
    fun `partyMemberLevel matches getPartyLevels by hand at wave 100`() {
        val levels = PartyMemberStrength.entries.map { curve.partyMemberLevel(100, it) }
        assertEquals(listOf(65, 70, 79, 81, 84), levels)
    }

    @Test
    fun `partyMemberLevel takes no boss multiplier and clamps to the band`() {
        // Their getPartyLevels has no boss term — a boss trainer's step up is strength spread plus
        // shields. And a deep wave clamps: base at 200 is 165, far past the cap.
        assertEquals(
            curve.partyMemberLevel(40, PartyMemberStrength.STRONGER),
            WaveLevelCurve(bossMultiplier = 99.0).partyMemberLevel(40, PartyMemberStrength.STRONGER),
        )
        assertEquals(100, curve.partyMemberLevel(200, PartyMemberStrength.WEAKER))
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
