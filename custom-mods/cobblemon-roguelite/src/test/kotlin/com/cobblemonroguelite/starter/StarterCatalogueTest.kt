package com.cobblemonroguelite.starter

import net.minecraft.resources.ResourceLocation
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What a player is offered to spend their budget on, and — the load-bearing half — what may not
 * influence the price.
 *
 * The properties here are the ones the superseded random offer also had to hold, restated against a
 * budget. **Eligibility and price stay separate concerns**, because §2.15 lets the server Pokédex
 * decide *which* species a player may start with and forbids it from deciding how strong the start
 * is; under a budget that is a sharper rule than it was under weighting, since the price *is* the
 * balance statement. **The catalogue is stable**, because a screen a player can change by
 * disconnecting is a screen they will learn to disconnect at.
 *
 * Nothing here needs a booted server: the catalogue works in species ids, and [ResourceLocation]
 * parses without a registry. The Cobblemon-backed unlock, label and stat sources are exactly the
 * parts these tests substitute.
 */
class StarterCatalogueTest {

    private fun id(name: String) = ResourceLocation.fromNamespaceAndPath("cobblemon", name)

    private val baseline = listOf("alpha", "bravo", "charlie", "delta").map(::id)
    private val caught = listOf("echo", "foxtrot", "golf").map(::id)

    private val player: UUID = UUID.fromString("00000000-0000-0000-0000-0000000000aa")
    private val other: UUID = UUID.fromString("00000000-0000-0000-0000-0000000000bb")

    /** Everything priced the same, so a cost difference in a result is never an accident of setup. */
    private fun flatCosts(cost: Int = 3) = StarterCostSource { cost }

    private fun factory(
        baseline: Collection<ResourceLocation> = this.baseline,
        caught: Set<ResourceLocation> = emptySet(),
        costs: StarterCostSource = flatCosts(),
        budget: Int = 10,
        exclusion: StarterExclusion = StarterExclusion.None,
    ) = StarterCatalogueFactory(
        pool = { baseline },
        unlocks = FixedCaughtSpecies(caught),
        costs = costs,
        budget = budget,
        exclusion = exclusion,
    )

    @AfterTest
    fun resetProgression() = StarterProgression.reset()

    // --- eligibility ------------------------------------------------------------------------------

    @Test
    fun `a player who has caught nothing still gets the baseline`() {
        val catalogue = factory(caught = emptySet()).catalogueFor(player)
        assertEquals(baseline.size, catalogue.options.size)
        assertTrue(catalogue.options.all { it.species in baseline })
    }

    @Test
    fun `caught species widen the pool rather than replacing it`() {
        val catalogue = factory(caught = caught.toSet()).catalogueFor(player)
        assertEquals(baseline.size + caught.size, catalogue.options.size)
        (baseline + caught).forEach { assertTrue(catalogue.contains(it), "$it missing from the catalogue") }
    }

    @Test
    fun `an empty baseline with no unlocks yields an empty catalogue rather than a broken run`() {
        // Documented failure mode, not a supported configuration: the factory logs this at ERROR and
        // RunStart refuses the run — before charging, which is the whole reason the catalogue moved
        // ahead of the fee.
        assertTrue(factory(baseline = emptyList(), caught = emptySet()).catalogueFor(player).isEmpty)
    }

    @Test
    fun `an excluded species is not merely priced out, it is absent`() {
        // §2.13 bans legendaries outright. A ban implemented as a high price would still be reachable
        // by a bigger budget or a candy discount, so the excluded species must never reach pricing.
        val banned = id("bravo")
        val seen = mutableListOf<ResourceLocation>()
        val catalogue = factory(
            costs = { seen += it; 3 },
            exclusion = { it == banned },
        ).catalogueFor(player)

        assertFalse(catalogue.contains(banned))
        assertFalse(banned in seen, "an excluded species was still handed to the cost source")
    }

    @Test
    fun `exclusion applies to unlocked species too`() {
        // The path that matters: a legendary reaches the pool by being *caught on the server*, which
        // is precisely the route §2.15 warns about.
        val legendary = id("echo")
        val catalogue = factory(caught = caught.toSet(), exclusion = { it == legendary }).catalogueFor(player)
        assertFalse(catalogue.contains(legendary))
        assertTrue(catalogue.contains(id("foxtrot")), "the exclusion took out more than it was asked to")
    }

    // --- unpriced species fail loudly, they are not free -------------------------------------------

    @Test
    fun `a species nothing prices is excluded and reported, not given away`() {
        val unpriced = id("charlie")
        val catalogue = factory(costs = { if (it == unpriced) null else 3 }).catalogueFor(player)

        assertFalse(catalogue.contains(unpriced))
        assertContains(catalogue.unpriced, unpriced)
        assertTrue(catalogue.options.none { it.cost == 0 }, "an unpriced species leaked in at cost 0")
    }

    @Test
    fun `a nonsense price is treated as unpriced rather than honoured`() {
        // A cost source is code, not just the datapack — the loader rejects zero, and this is what
        // catches a code-supplied source that returns one anyway.
        val free = id("delta")
        val catalogue = factory(costs = { if (it == free) 0 else 3 }).catalogueFor(player)
        assertFalse(catalogue.contains(free))
        assertContains(catalogue.unpriced, free)
    }

    // --- price is not influenced by who is asking --------------------------------------------------

    @Test
    fun `the cost source is never told which player it is pricing for`() {
        // Two players with identical eligible sets. There is no player-shaped argument to pass, so
        // this pins the shape rather than the behaviour: a future signature change that adds one
        // breaks here first.
        val a = factory(caught = caught.toSet()).catalogueFor(player)
        val b = factory(caught = caught.toSet()).catalogueFor(other)
        assertEquals(a.options, b.options)
    }

    @Test
    fun `the cost source cannot tell an unlocked species from a baseline one`() {
        val seen = mutableListOf<ResourceLocation>()
        factory(caught = caught.toSet(), costs = { seen += it; 3 }).catalogueFor(player)
        assertTrue(seen.isNotEmpty())
        assertTrue(seen.all { it in baseline || it in caught })
    }

    // --- progression discounts ---------------------------------------------------------------------

    @Test
    fun `progression may discount a species for one player and not another`() {
        StarterProgression.set(object : StarterProgression {
            override fun effectiveCost(player: UUID, species: ResourceLocation, baseCost: Int) =
                if (player == this@StarterCatalogueTest.player && species == id("alpha")) 1 else baseCost
            override fun ivFloor(player: UUID, species: ResourceLocation) = StarterIvFloor.Base
            override fun hiddenAbilityUnlocked(player: UUID, species: ResourceLocation) = false
        })
        assertEquals(1, factory().catalogueFor(player).costOf(id("alpha")))
        assertEquals(3, factory().catalogueFor(other).costOf(id("alpha")))
    }

    @Test
    fun `progression cannot raise a price and cannot make one free`() {
        // Candy discounts. A store that raised a cost would price a player out of something they used
        // to afford; one that reached zero would delete the budget for that species. Both are clamped
        // rather than trusted, because the store is written by someone else.
        StarterProgression.set(object : StarterProgression {
            override fun effectiveCost(player: UUID, species: ResourceLocation, baseCost: Int) =
                if (species == id("alpha")) 0 else baseCost + 5
            override fun ivFloor(player: UUID, species: ResourceLocation) = StarterIvFloor.Base
            override fun hiddenAbilityUnlocked(player: UUID, species: ResourceLocation) = false
        })
        val catalogue = factory().catalogueFor(player)
        assertEquals(1, catalogue.costOf(id("alpha")))
        assertEquals(3, catalogue.costOf(id("bravo")))
    }

    @Test
    fun `a progression store that throws falls back to the base price`() {
        // Base price, not free and not a refused run: the player loses a discount they earned, which
        // is visible and fixable.
        StarterProgression.set(object : StarterProgression {
            override fun effectiveCost(player: UUID, species: ResourceLocation, baseCost: Int): Int = error("store is down")
            override fun ivFloor(player: UUID, species: ResourceLocation) = StarterIvFloor.Base
            override fun hiddenAbilityUnlocked(player: UUID, species: ResourceLocation) = false
        })
        assertTrue(factory().catalogueFor(player).options.all { it.cost == 3 })
    }

    // --- stability ---------------------------------------------------------------------------------

    @Test
    fun `the catalogue does not depend on the order the eligible set was built in`() {
        // The real eligible set arrives from Cobblemon's Pokédex map, whose iteration order tracks
        // insertion — so a resumed session can hand us the same species in a different order.
        val f = factory()
        val forward = linkedSetOf(*(baseline + caught).toTypedArray())
        val reversed = linkedSetOf(*(baseline + caught).reversed().toTypedArray())
        assertEquals(f.priceFrom(player, forward).options, f.priceFrom(player, reversed).options)
    }

    @Test
    fun `two builds of the same catalogue are identical`() {
        // The property the seed used to buy. Nothing is rolled any more, so this must hold for free —
        // and if someone reintroduces a draw, this is where it shows.
        val f = factory(caught = caught.toSet())
        repeat(5) { assertEquals(f.catalogueFor(player), f.catalogueFor(player)) }
    }

    @Test
    fun `options are ordered cheapest first, then by id`() {
        val catalogue = factory(costs = { if (it == id("delta")) 1 else 3 }).catalogueFor(player)
        assertEquals(id("delta"), catalogue.options.first().species)
        val rest = catalogue.options.drop(1).map { it.species.toString() }
        assertEquals(rest.sorted(), rest, "ties must fall back to the id, or the order is not total")
    }

    // --- the budget travels with the catalogue -----------------------------------------------------

    @Test
    fun `the catalogue carries the budget it was built with`() {
        assertEquals(7, factory(budget = 7).catalogueFor(player).budget)
    }

    @Test
    fun `a budget below every price leaves the catalogue populated but unaffordable`() {
        // Deliberately not empty: the distinction is what lets RunStart tell an operator "your budget
        // is below your cheapest species" instead of "there are no starters", which is a different bug.
        val catalogue = factory(budget = 2, costs = flatCosts(3)).catalogueFor(player)
        assertFalse(catalogue.isEmpty)
        assertTrue(catalogue.affordable().isEmpty())
        assertEquals(3, catalogue.cheapest)
    }
}
