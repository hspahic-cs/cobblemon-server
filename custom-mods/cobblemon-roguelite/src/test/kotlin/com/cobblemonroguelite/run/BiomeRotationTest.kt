package com.cobblemonroguelite.run

import com.cobblemonroguelite.data.biome.RunBiome
import com.cobblemonroguelite.data.biome.RunBiomes
import net.minecraft.resources.ResourceLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * §2.24's rotation: which biome a run is in, and when that changes.
 *
 * Every failure here is invisible in play and expensive. A boundary that fires a wave late looks like
 * nothing at all; a draw that is not stable across a resume rebuilds somebody's arena around them for
 * no reason they can see; a band with nothing eligible dropping the run back to the default build is a
 * datapack typo demolishing a player's surroundings mid-run. None of it throws, and none of it is
 * reproducible without playing forty waves.
 */
class BiomeRotationTest {

    private fun biome(name: String, min: Int = 1, max: Int? = null, weight: Double = 1.0) = RunBiome(
        id = ResourceLocation.fromNamespaceAndPath("test", name),
        displayName = name,
        arenaTemplate = ResourceLocation.fromNamespaceAndPath("test", "arena/$name"),
        minecraftBiome = ResourceLocation.fromNamespaceAndPath("minecraft", name),
        minWave = min,
        maxWave = max,
        weight = weight,
    )

    private val pool = listOf(biome("meadow"), biome("desert"), biome("swamp"), biome("taiga"))

    private fun next(current: BiomeVisit?, wave: Int, seed: Long = 42L, eligible: List<RunBiome> = pool) =
        BiomeRotation.next(current, wave, bandLength = 10, seed = seed, eligible = eligible)

    @Test
    fun `bands are ten waves and start at wave one`() {
        assertEquals(0, BiomeRotation.bandOf(1, 10))
        assertEquals(0, BiomeRotation.bandOf(10, 10))
        // The boundary that matters: wave 11 is a new band, not wave 10. Off by one here and every
        // transition in the run happens a wave late, including the one at the boss.
        assertEquals(1, BiomeRotation.bandOf(11, 10))
        assertEquals(19, BiomeRotation.bandOf(200, 10))
    }

    @Test
    fun `a run with no biome gets one on its first wave`() {
        val visit = assertNotNull(next(current = null, wave = 1))
        assertEquals(0, visit.band)
        assertTrue(pool.any { it.id == visit.biome })
    }

    @Test
    fun `the biome does not move inside a band`() {
        val first = assertNotNull(next(current = null, wave = 1))
        for (wave in 2..10) {
            assertEquals(first, next(first, wave), "wave $wave re-rolled inside band 0")
        }
    }

    @Test
    fun `crossing a band boundary picks for the new band`() {
        val first = assertNotNull(next(current = null, wave = 1))
        val second = assertNotNull(next(first, wave = 11))
        assertEquals(1, second.band)
    }

    @Test
    fun `the same seed and band pick the same biome, with or without a stored visit`() {
        // The resume guarantee. A run whose checkpoint lost its biome — a damaged tag, a slot
        // reassignment — must come back to the same place rather than have its arena rebuilt.
        val stored = assertNotNull(next(current = null, wave = 34))
        val recomputed = assertNotNull(next(current = null, wave = 37))
        assertEquals(stored, recomputed, "the same band picked differently on a different wave")
    }

    @Test
    fun `two runs with different seeds do not walk the same path`() {
        // Not a distribution test — just that the seed reaches the draw at all. A rotation that
        // ignored it would give every player on the server an identical run, and nothing about that
        // is visible until two of them compare notes.
        val a = (0..19).map { assertNotNull(next(null, it * 10 + 1, seed = 1L)).biome }
        val b = (0..19).map { assertNotNull(next(null, it * 10 + 1, seed = 2L)).biome }
        assertTrue(a != b, "two seeds produced the same twenty-band path")
    }

    @Test
    fun `wave bounds and zero weights are settled before the draw, not inside it`() {
        // The eligibility filter lives on the registry and the draw takes what it is given — see
        // [RunBiomes.eligibleIn]. Asserted together because the split is easy to get wrong in the
        // direction that does not fail: a rotation that re-filtered would work, and one that assumed
        // the caller filtered when it did not would put the endgame's biome at wave 1.
        val catalogue = listOf(
            biome("volcano", min = 100),
            biome("meadow", max = 40),
            biome("disabled", weight = 0.0),
        )
        assertEquals(listOf("meadow"), RunBiomes.eligibleIn(catalogue, 1).map { it.displayName })
        assertEquals(listOf("volcano"), RunBiomes.eligibleIn(catalogue, 101).map { it.displayName })
        // Nothing eligible and nothing held: no biome, which the arena layer reads as "leave the
        // configured template and the dimension's own biome alone".
        assertNull(next(current = null, wave = 60, eligible = RunBiomes.eligibleIn(catalogue, 60)))
    }

    @Test
    fun `the eligible order does not depend on how the biomes were loaded`() {
        // A weighted walk is order-sensitive, and datapack iteration order is not something we
        // control. Unsorted, a run resumed after a restart would find itself somewhere else.
        val catalogue = listOf(biome("taiga"), biome("desert"), biome("meadow"))
        assertEquals(
            RunBiomes.eligibleIn(catalogue, 1),
            RunBiomes.eligibleIn(catalogue.reversed(), 1),
        )
    }

    @Test
    fun `a band with nothing eligible keeps the biome the run is already in`() {
        // The datapack-edited-mid-run case. Dropping to no biome would demolish and re-stamp the
        // player's arena because of a typo in somebody else's file, and the run recovers by itself at
        // the next band either way.
        val held = assertNotNull(next(current = null, wave = 1))
        assertEquals(held, next(held, wave = 11, eligible = emptyList()))
    }
}
