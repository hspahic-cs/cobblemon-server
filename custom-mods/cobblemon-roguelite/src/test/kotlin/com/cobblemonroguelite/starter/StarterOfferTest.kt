package com.cobblemonroguelite.starter

import net.minecraft.resources.ResourceLocation
import java.util.Random
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Covers the two properties the offer is not allowed to lose.
 *
 * **Determinism**, because §2.3 makes runs checkpointable and an offer rolled at call time is an
 * offer a player can reroll by disconnecting. **The unlock/weighting separation**, because §2.15
 * only holds as long as nothing downstream of the union can tell an earned species from a baseline
 * one — and that is a property of the code's shape, which a test can pin and a comment cannot.
 *
 * Nothing here needs a booted server: the offer works in species ids, and [ResourceLocation] parses
 * without a registry. The Pokédex-backed [CaughtSpeciesSource] is the one part that does, and it is
 * exactly the part these tests substitute.
 */
class StarterOfferTest {

    private fun id(name: String) = ResourceLocation.fromNamespaceAndPath("cobblemon", name)

    private class TestPool(
        private val baseline: Collection<ResourceLocation>,
        private val size: Int,
    ) : StarterPoolSource {
        override fun baselinePool() = baseline
        override fun offerSize() = size
    }

    private val baseline = listOf("alpha", "bravo", "charlie", "delta").map(::id)
    private val caught = listOf("echo", "foxtrot", "golf").map(::id)

    private fun factory(
        baseline: Collection<ResourceLocation> = this.baseline,
        caught: Set<ResourceLocation> = emptySet(),
        size: Int = 3,
        weighting: StarterWeighting = StarterWeighting.Uniform,
    ) = StarterOfferFactory(TestPool(baseline, size), FixedCaughtSpecies(caught), weighting)

    private val player: UUID = UUID.fromString("00000000-0000-0000-0000-0000000000aa")

    // --- determinism ---------------------------------------------------------------------------

    @Test
    fun `same seed and same eligible set give the same offer`() {
        val f = factory(caught = caught.toSet())
        repeat(20) { i ->
            val seed = 1_000L + i
            assertEquals(f.offerFor(player, seed).species, f.offerFor(player, seed).species)
        }
    }

    @Test
    fun `offer does not depend on the order the eligible set was built in`() {
        // The real eligible set arrives from Cobblemon's Pokédex map, whose iteration order tracks
        // insertion — so a resumed session can hand us the same species in a different order. If
        // that changed the offer, the seed would be buying nothing.
        val f = factory()
        val forward = linkedSetOf(*(baseline + caught).toTypedArray())
        val reversed = linkedSetOf(*(baseline + caught).reversed().toTypedArray())
        assertEquals(f.offerFrom(forward, 4242L).species, f.offerFrom(reversed, 4242L).species)
    }

    @Test
    fun `different seeds give different offers`() {
        val f = factory(caught = caught.toSet())
        val offers = (0 until 50).map { f.offerFor(player, it.toLong()).species }.toSet()
        assertTrue(offers.size > 1, "every seed produced the same offer — the seed is not reaching the draw")
    }

    @Test
    fun `consecutive run seeds do not produce the same first pick`() {
        // Run seeds may well come from a counter or a clock. `Random(n)` and `Random(n+1)` open with
        // near-identical draws, which would show up as every run in a minute offering the same lead
        // species; the splitmix finaliser in starterSeed is what prevents that.
        val f = factory(caught = caught.toSet(), size = 1)
        val firsts = (0 until 16).map { f.offerFor(player, it.toLong()).species.first() }.toSet()
        assertTrue(firsts.size > 1, "consecutive seeds collapsed to one lead species")
    }

    @Test
    fun `starter draw does not share a stream with the raw run seed`() {
        // wave/ derives its own streams from the same run seed. Were the starter draw unsalted, the
        // offer would be a readable function of the wave-1 roll.
        val seed = 12345L
        assertNotEquals(Random(seed).nextLong(), Random(StarterOfferFactory.starterSeed(seed)).nextLong())
    }

    @Test
    fun `an offer never repeats a species`() {
        val f = factory(caught = caught.toSet(), size = 5)
        repeat(30) { i ->
            val species = f.offerFor(player, i.toLong()).species
            assertEquals(species.size, species.toSet().size, "offer contained a duplicate: $species")
        }
    }

    // --- the baseline pool is mandatory --------------------------------------------------------

    @Test
    fun `a player who has caught nothing still gets a full offer`() {
        val offer = factory(caught = emptySet(), size = 3).offerFor(player, 7L)
        assertEquals(3, offer.species.size)
        assertTrue(offer.species.all { it in baseline })
    }

    @Test
    fun `offer size is clamped to what is actually available`() {
        val offer = factory(baseline = baseline.take(2), size = 6).offerFor(player, 7L)
        assertEquals(2, offer.species.size)
    }

    @Test
    fun `caught species widen the pool rather than replacing it`() {
        val f = factory(caught = caught.toSet(), size = 7)
        val offer = f.offerFor(player, 7L).species
        assertEquals((baseline + caught).size, offer.size)
        baseline.forEach { assertContains(offer, it) }
        caught.forEach { assertContains(offer, it) }
    }

    @Test
    fun `an empty baseline with no unlocks yields an empty offer rather than a broken run`() {
        // Documented failure mode, not a supported configuration: the factory logs this at ERROR
        // and the caller is expected to refuse the run instead of starting one with no starter.
        assertTrue(factory(baseline = emptyList(), caught = emptySet()).offerFor(player, 7L).isEmpty)
    }

    // --- unlocking is not power ----------------------------------------------------------------

    @Test
    fun `weighting is never told which player it is weighting for`() {
        // Two players with identical eligible sets and one seed. If the draw could see the player at
        // all, this is where it would show.
        val other = UUID.fromString("00000000-0000-0000-0000-0000000000bb")
        val f = factory(caught = caught.toSet())
        assertEquals(f.offerFor(player, 909L).species, f.offerFor(other, 909L).species)
    }

    @Test
    fun `weighting cannot tell an unlocked species from a baseline one`() {
        // A weighting that tried to favour unlocks has nothing to key off: it is handed the same
        // species ids either way. Pinning the recorded calls means a future signature change that
        // adds provenance breaks here first.
        val seen = mutableListOf<ResourceLocation>()
        val f = factory(caught = caught.toSet(), weighting = { seen.add(it); 1 })
        f.offerFor(player, 11L)
        assertTrue(seen.isNotEmpty())
        assertTrue(seen.all { it in baseline || it in caught })
    }

    @Test
    fun `weights change the draw but not eligibility`() {
        // The dial weighting is allowed to turn. A species weighted 1 against one weighted 10_000
        // still appears in a wide-enough offer, because weighting is not a gate.
        val heavy = id("bravo")
        val f = factory(size = 4, weighting = { if (it == heavy) 10_000 else 1 })
        assertEquals(baseline.size, f.offerFor(player, 3L).species.size)
        assertContains(f.offerFor(player, 3L).species, heavy)
    }

    @Test
    fun `every weight at zero still produces an offer`() {
        // A weighting table typo must not be able to stop a player starting a run.
        val f = factory(size = 3, weighting = { 0 })
        assertEquals(3, f.offerFor(player, 5L).species.size)
    }

    @Test
    fun `zero weight excludes a species without excluding it from eligibility`() {
        val banned = id("alpha")
        val f = factory(size = 3, weighting = { if (it == banned) 0 else 1 })
        assertTrue(banned !in f.offerFor(player, 5L).species)
        assertContains(f.eligibleSpecies(player), banned)
    }
}
