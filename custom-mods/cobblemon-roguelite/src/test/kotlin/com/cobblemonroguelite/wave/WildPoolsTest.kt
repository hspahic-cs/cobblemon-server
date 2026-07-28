package com.cobblemonroguelite.wave

import com.cobblemonroguelite.composition.WaveComposition
import com.cobblemonroguelite.integration.RunOpponent
import net.minecraft.resources.ResourceLocation
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The wild pool holder, and the one cross-layer invariant the battle layer leans on.
 *
 * Everything below the battle itself is testable without a server, which is the whole reason
 * [WildEncounterFactory] was split off [WildWaveGenerator] — so the *decisions* a wave makes can be
 * checked here and only the Cobblemon objects need a booted one.
 */
class WildPoolsTest {

    private fun id(path: String) = ResourceLocation.fromNamespaceAndPath("roguelite_test", path)

    @AfterTest
    fun restore() = WildPools.reset()

    @Test
    fun `the shipped default offers nothing, so a wild wave refuses rather than substitutes`() {
        assertFalse(WildPools.isRegistered())
        assertTrue(WildPools.current.eligibleAt(1).isEmpty())
        // Null out of the generator is what the battle layer turns into a refused wave. A pool that
        // quietly produced *something* would put an undesigned species in front of 160 waves of a run
        // and nothing would report it.
        assertNull(WildPools.generator(WaveLevelCurve()).generate(seed = 1L, wave = 1))
    }

    @Test
    fun `a registered pool is what the generator draws from`() {
        WildPools.register(StaticWaveSpeciesPool(listOf(WaveSpecies(id("test_alpha")))))
        assertTrue(WildPools.isRegistered())
        val encounter = WildPools.generator(WaveLevelCurve()).generate(seed = 7L, wave = 3)
        assertEquals(id("test_alpha"), assertNotNull(encounter).species.id)
    }

    @Test
    fun `a generator built per call still draws the same encounter`() {
        WildPools.register(
            StaticWaveSpeciesPool(
                listOf(WaveSpecies(id("test_alpha")), WaveSpecies(id("test_bravo")), WaveSpecies(id("test_charlie"))),
            ),
        )
        // The battle layer builds a generator per wave so that re-registering a pool takes effect.
        // That is only safe because the generator holds no state between calls — if it ever did, two
        // instances would disagree and a resumed run would meet a different opponent.
        val first = WildPools.generator(WaveLevelCurve()).generate(seed = 99L, wave = 12)
        val second = WildPools.generator(WaveLevelCurve()).generate(seed = 99L, wave = 12)
        assertEquals(first, second)
    }

    @Test
    fun `reset restores the empty default`() {
        WildPools.register(StaticWaveSpeciesPool(listOf(WaveSpecies(id("test_alpha")))))
        WildPools.reset()
        assertFalse(WildPools.isRegistered())
        assertTrue(WildPools.current.eligibleAt(1).isEmpty())
    }

    /**
     * The claim the wild battle layer is written on top of: the level a wave *plans* and the level
     * its generator independently rolls are the same number, because both go through the same curve
     * on the same `(seed, wave)` stream.
     *
     * The battle takes the plan's level and discards the generator's. That is only a no-op while this
     * holds — if the two ever drift, the wave the player fights stops matching the wave the run
     * composed, and the symptom is a difficulty curve that is subtly wrong on wild waves only.
     */
    @Test
    fun `a wild wave's planned level is the level its generator rolls`() {
        WildPools.register(StaticWaveSpeciesPool(listOf(WaveSpecies(id("test_alpha")))))
        val composition = WaveComposition()
        val generator = WildPools.generator(composition.config.curve)
        val seed = 20260728L

        (1..60).forEach { wave ->
            val plan = composition.planFor(wave, seed)
            if (plan.kind != RunOpponent.WILD) return@forEach
            val encounter = assertNotNull(generator.generate(seed, wave, boss = false))
            assertEquals(plan.level, encounter.level, "wave $wave")
        }
    }
}
