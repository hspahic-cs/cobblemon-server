package com.cobblemonroguelite.wave

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The stream underneath wave generation. Its job is to be boringly reproducible, so most of what is
 * asserted here is sameness rather than quality.
 */
class WaveRandomTest {

    private fun draw(seed: Long, wave: Int, stream: WaveDrawStream, n: Int = 8): List<Long> {
        val rng = WaveRandom.forDraw(seed, wave, stream)
        return List(n) { rng.nextLong() }
    }

    @Test
    fun `the same seed, wave and stream replay the same sequence`() {
        assertEquals(draw(1234L, 5, WaveDrawStream.SPECIES), draw(1234L, 5, WaveDrawStream.SPECIES))
    }

    @Test
    fun `different streams on the same wave do not move together`() {
        // If they did, a high species roll would always come with a high level roll.
        assertNotEquals(draw(1234L, 5, WaveDrawStream.SPECIES), draw(1234L, 5, WaveDrawStream.LEVEL))
        assertNotEquals(draw(1234L, 5, WaveDrawStream.LEVEL), draw(1234L, 5, WaveDrawStream.VARIANT))
    }

    @Test
    fun `adjacent waves are unrelated`() {
        assertNotEquals(draw(1234L, 5, WaveDrawStream.SPECIES), draw(1234L, 6, WaveDrawStream.SPECIES))
    }

    @Test
    fun `adjacent seeds are unrelated`() {
        // Two players starting at the same moment get near-identical seeds from any clock-derived
        // source; they must not then get near-identical runs.
        val a = draw(1_000_000L, 1, WaveDrawStream.SPECIES)
        val b = draw(1_000_001L, 1, WaveDrawStream.SPECIES)
        assertNotEquals(a, b)
        assertTrue(a.zip(b).none { (x, y) -> abs(x - y) < 1_000_000L }, "seeds one apart produced adjacent output")
    }

    @Test
    fun `nextDouble stays in range`() {
        val rng = WaveRandom.forDraw(42L, 1, WaveDrawStream.LEVEL)
        repeat(10_000) {
            val d = rng.nextDouble()
            assertTrue(d >= 0.0 && d < 1.0, "nextDouble produced $d")
        }
    }

    @Test
    fun `nextGaussian is centred and roughly unit variance`() {
        val rng = WaveRandom.forDraw(7L, 1, WaveDrawStream.LEVEL)
        val samples = List(50_000) { rng.nextGaussian() }
        val mean = samples.average()
        val variance = samples.sumOf { (it - mean) * (it - mean) } / samples.size
        assertTrue(abs(mean) < 0.05, "gaussian mean was $mean")
        assertTrue(abs(variance - 1.0) < 0.05, "gaussian variance was $variance")
        assertTrue(samples.none { it.isNaN() }, "gaussian produced NaN")
    }
}
