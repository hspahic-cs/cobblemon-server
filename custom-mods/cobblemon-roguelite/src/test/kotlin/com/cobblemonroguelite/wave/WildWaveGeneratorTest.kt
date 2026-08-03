package com.cobblemonroguelite.wave

import net.minecraft.resources.ResourceLocation
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Test-only species ids, namespaced so nothing here can be mistaken for real pool content. */
private fun id(path: String) = ResourceLocation.fromNamespaceAndPath("roguelite_test", path)

/**
 * Determinism is a correctness requirement here, not a nicety: runs are checkpointable (plan §2.3),
 * so if `(seed, wave)` did not pin the encounter, quitting before a bad wave resolved and
 * reconnecting would reroll it. Most of this file is that one property attacked from different
 * angles.
 *
 */
class WildWaveGeneratorTest {

    private val pool = listOf(
        WaveSpecies(id("test_alpha")),
        WaveSpecies(id("test_bravo")),
        WaveSpecies(id("test_charlie")),
        WaveSpecies(id("test_delta")),
    )

    private fun generator(entries: List<WaveSpecies> = pool, curve: WaveLevelCurve = WaveLevelCurve()) =
        WildWaveGenerator(StaticWaveSpeciesPool(entries), curve)

    @Test
    fun `the same seed and wave always produce the same encounter`() {
        val a = generator().generate(4242L, 7)
        val b = generator().generate(4242L, 7)
        assertEquals(a, b)
    }

    @Test
    fun `a resumed run replays the whole remaining sequence`() {
        // What a disconnect actually looks like: the run is rebuilt from its checkpoint into a fresh
        // generator and must walk into the identical waves it was going to walk into.
        val before = (1..20).map { generator().generate(-987654321L, it) }
        val after = (1..20).map { WildWaveGenerator(StaticWaveSpeciesPool(pool.reversed())).generate(-987654321L, it) }
        assertContentEquals(before, after)
    }

    @Test
    fun `pool ordering does not change what a seed rolls`() {
        // The data layer's iteration order is not something we control or can rely on being stable
        // across loads, so the generator imposes its own. If it stopped doing that, this is the test
        // that would catch it — and the bug it prevents is invisible in play.
        val ordered = generator(pool).generate(31337L, 4)
        val shuffled = generator(pool.shuffled()).generate(31337L, 4)
        val reversed = generator(pool.reversed()).generate(31337L, 4)
        assertEquals(ordered, shuffled)
        assertEquals(ordered, reversed)
    }

    @Test
    fun `encounters vary across waves of one run`() {
        val species = (1..40).map { generator().generate(555L, it)?.species?.id?.path }.toSet()
        assertTrue(species.size > 1, "every wave rolled the same species: $species")
        val levels = (1..40).map { generator().generate(555L, it)?.level }
        assertTrue(levels.toSet().size > 1, "every wave rolled the same level")
    }

    @Test
    fun `two runs with different seeds diverge`() {
        val a = (1..20).map { generator().generate(1L, it) }
        val b = (1..20).map { generator().generate(2L, it) }
        assertTrue(a != b, "two seeds produced identical runs")
    }

    @Test
    fun `weights are honoured`() {
        val weighted = listOf(
            WaveSpecies(id("test_common"), weight = 95.0),
            WaveSpecies(id("test_rare"), weight = 5.0),
        )
        val counts = (1..4000)
            .mapNotNull { generator(weighted).generate(777L, it)?.species?.id?.path }
            .groupingBy { it }.eachCount()
        val common = counts["test_common"] ?: 0
        val rare = counts["test_rare"] ?: 0
        assertTrue(common > rare * 5, "95:5 weighting produced $common common vs $rare rare")
        assertTrue(rare > 0, "the rare entry never appeared at all in 4000 draws")
    }

    @Test
    fun `non-positive weights are dropped, not treated as ties`() {
        // The data layer's way of disabling a line without deleting it.
        val entries = listOf(WaveSpecies(id("test_enabled"), 1.0), WaveSpecies(id("test_disabled"), 0.0))
        val rolled = (1..500).mapNotNull { generator(entries).generate(11L, it)?.species?.id?.path }.toSet()
        assertEquals(setOf("test_enabled"), rolled)
    }

    @Test
    fun `an empty pool reports rather than substituting`() {
        assertNull(generator(emptyList()).generate(1L, 1))
        assertNull(generator(listOf(WaveSpecies(id("test_off"), 0.0))).generate(1L, 1))
    }

    @Test
    fun `boss waves come in above the same wave as a wild encounter`() {
        val curve = WaveLevelCurve(spreadAtWaveOne = 0.0, spreadFloor = 0.0)
        val plain = generator(curve = curve).generate(8L, 10)
        val boss = generator(curve = curve).generate(8L, 10, boss = true)
        assertNotNull(plain); assertNotNull(boss)
        assertTrue(boss.level > plain.level, "boss level ${boss.level} was not above ${plain.level}")
        assertTrue(boss.boss)
    }

    @Test
    fun `waves are 1-based`() {
        assertFailsWith<IllegalArgumentException> { generator().generate(1L, 0) }
        assertFailsWith<IllegalArgumentException> { generator().generate(1L, -3) }
    }

    @Test
    fun `properties string carries the level we chose, not the species default`() {
        val encounter = WildEncounter(3, 27, false, WaveSpecies(id("test_alpha")), 0L)
        assertEquals("species=roguelite_test:test_alpha level=27", encounter.propertiesString())
        val withExtras = WildEncounter(3, 27, false, WaveSpecies(id("test_alpha"), properties = " shiny=true "), 0L)
        assertEquals("species=roguelite_test:test_alpha level=27 shiny=true", withExtras.propertiesString())
        val blankExtras = WildEncounter(3, 27, false, WaveSpecies(id("test_alpha"), properties = "   "), 0L)
        assertEquals("species=roguelite_test:test_alpha level=27", blankExtras.propertiesString())
    }

    @Test
    fun `golden values pin the sequence across builds`() {
        // The tests above prove the generator agrees with itself inside one JVM. This one is the
        // guard against the failure that actually loses a run: a rewrite of the mixer, a swap to a
        // platform RNG, or a JVM whose libm differs, all of which keep every other test green while
        // changing what an existing checkpoint resumes into. If this fails, either the change was
        // wrong or every in-flight run has just been re-rolled — decide which before touching it.
        val gen = generator(curve = FROZEN_CURVE)
        val rolled = (1..6).map {
            val e = gen.generate(20260727L, it)!!
            "${e.species.id}@${e.level}"
        }
        assertContentEquals(GOLDEN, rolled)
    }

    private companion object {
        /**
         * Spelled out rather than defaulted so that retuning [WaveLevelCurve]'s placeholders — which
         * is expected and is somebody else's decision — does not fail a test about reproducibility.
         * These happen to be today's defaults; that is a coincidence this test does not depend on.
         */
        val FROZEN_CURVE = WaveLevelCurve(
            baseLevel = 10.0,
            linearPerWave = 3.0,
            quadraticDivisor = 8.0,
            bossMultiplier = 1.25,
            spreadAtWaveOne = 3.0,
            spreadFloor = 0.5,
            spreadNarrowingWaves = 6.0,
            clampSigma = 2.0,
        )

        val GOLDEN = listOf(
            "roguelite_test:test_delta@12",
            "roguelite_test:test_charlie@16",
            "roguelite_test:test_delta@20",
            "roguelite_test:test_delta@24",
            "roguelite_test:test_alpha@24",
            "roguelite_test:test_bravo@30",
        )
    }
}
