package com.cobblemonroguelite.composition

import com.cobblemonroguelite.integration.RunOpponent
import com.cobblemonroguelite.wave.StaticWaveSpeciesPool
import com.cobblemonroguelite.wave.WaveSpecies
import com.cobblemonroguelite.wave.WildWaveGenerator
import net.minecraft.resources.ResourceLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unlike `WaveLevelCurveTest`, this file *does* assert exact numbers — because these constants are
 * not ours to tune. §2.19 adopts PokéRogue's curve literally, so a changed constant here is a changed
 * decision, and the test is the thing that says so out loud.
 */
class WaveCompositionCurveTest {

    private val curve = WaveCompositionConfig.pokeRogueClassicCurve()
    private val composition = WaveComposition()

    /** `1 + wave/2 + (wave/25)²`, written out independently of the implementation. */
    private fun pokeRogue(wave: Int): Double {
        val w = wave.toDouble()
        return 1.0 + w / 2.0 + (w / 25.0) * (w / 25.0)
    }

    @Test
    fun `the mean curve is PokeRogue's, verbatim`() {
        for (wave in listOf(1, 5, 25, 50, 100, 138, 200)) {
            assertEquals(pokeRogue(wave), curve.meanLevelAt(wave), 1e-9, "wave $wave")
        }
    }

    @Test
    fun `bosses are a 1_2x step on the same curve`() {
        assertEquals(1.2, curve.bossMultiplier, 1e-9)
        assertEquals(pokeRogue(50) * 1.2, curve.meanLevelAt(50, boss = true), 1e-9)
    }

    @Test
    fun `the curve crosses 100 around wave 138, and bosses around 120`() {
        // The waves §2.19 names. If a constant drifts, these move and the flat tail's length — which
        // is a design decision, not a side effect — changes with it.
        assertTrue(curve.meanLevelAt(137) < 100.0)
        assertTrue(curve.meanLevelAt(138) >= 100.0)
        assertTrue(curve.meanLevelAt(119, boss = true) < 100.0)
        assertTrue(curve.meanLevelAt(120, boss = true) >= 100.0)
    }

    @Test
    fun `no wave of a full run can produce a level above Cobblemon's cap`() {
        // maxPokemonLevel is global (§2.19) — a level 165 opponent is not "hard", it is a Pokémon the
        // server cannot represent.
        for (seed in listOf(0L, 1L, 4242L, -99L)) {
            for (wave in 1..200) {
                val level = composition.levelFor(wave, seed)
                assertTrue(level in 1..WaveCompositionConfig.COBBLEMON_MAX_LEVEL, "wave $wave gave level $level")
            }
        }
    }

    @Test
    fun `the last third of a run is flat at 100 by design`() {
        // Not a bug being pinned — a decision being pinned, so that anyone who "fixes" the flat tail
        // has to delete this test and notice §2.19 while doing it.
        for (seed in listOf(3L, 77L)) {
            for (wave in 145..200) {
                assertEquals(100, composition.levelFor(wave, seed), "wave $wave, seed $seed")
            }
        }
    }

    @Test
    fun `early waves start at the bottom of the level range`() {
        // Party starts at level 1 (§2.21); a wave-1 opponent in the twenties would be the placeholder
        // curve leaking back in.
        assertTrue(curve.meanLevelAt(1) < 3.0, "wave 1 mean was ${curve.meanLevelAt(1)}")
        assertTrue(composition.levelFor(1, seed = 11L) in 1..10)
    }

    @Test
    fun `a wild wave's composed level is the one the wild generator produces`() {
        // Same curve, same (seed, wave, LEVEL) stream. If these ever disagree, one of the two has
        // started deriving its own levels and the run has two difficulty curves.
        val generator = WildWaveGenerator(
            StaticWaveSpeciesPool(listOf(WaveSpecies(ResourceLocation.fromNamespaceAndPath("roguelite_test", "alpha")))),
            curve,
        )
        for (wave in listOf(1, 7, 33, 99, 151)) {
            val plan = composition.planFor(wave, seed = 8080L)
            assertEquals(RunOpponent.WILD, plan.kind, "wave $wave should be a wild wave for this test")
            val encounter = assertNotNull(generator.generate(8080L, wave, boss = false))
            assertEquals(encounter.level, plan.level, "wave $wave")
        }
    }

    @Test
    fun `boss waves take the multiplier through the shared curve`() {
        // The trainer path must not reimplement the ramp; the only difference between a boss wave and
        // its neighbours is the multiplier the curve already owns.
        val bossWave = 120
        assertEquals(RunOpponent.BOSS, composition.kindOf(bossWave))
        val level = composition.levelFor(bossWave, seed = 5L)
        assertTrue(level > composition.levelFor(bossWave - 1, seed = 5L) || level == 100)
    }
}
