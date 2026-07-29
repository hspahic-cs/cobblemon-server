package com.cobblemonroguelite.starter

import com.cobblemonroguelite.run.RunState
import net.minecraft.resources.ResourceLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * §2.13's budget arithmetic.
 *
 * The sums are trivial and that is the point: this is the one place where a player's points turn
 * into a party, it runs once per run and never again, and every way of getting it wrong is
 * unrecoverable — there is no refund seam (§2.16), so a team that cost the wrong number of points
 * cannot be undone by anything short of an operator.
 */
class StarterSelectionTest {

    private fun id(name: String) = ResourceLocation.fromNamespaceAndPath("cobblemon", name)

    private val cheap = id("cheap")
    private val alsoCheap = id("also-cheap")
    private val mid = id("mid")
    private val dear = id("dear")
    private val unaffordable = id("unaffordable")

    private fun catalogue(budget: Int = 10, unpriced: List<ResourceLocation> = emptyList()) = StarterCatalogue(
        budget = budget,
        options = listOf(
            StarterOption(cheap, 3),
            StarterOption(alsoCheap, 3),
            StarterOption(mid, 4),
            StarterOption(dear, 6),
            StarterOption(unaffordable, 12),
        ),
        unpriced = unpriced,
    )

    private fun accept(vararg picks: ResourceLocation, budget: Int = 10) =
        assertIs<StarterSelectionResult.Accepted>(StarterSelection.validate(catalogue(budget), picks.toList()))

    // --- the arithmetic ---------------------------------------------------------------------------

    @Test
    fun `a team that spends the budget exactly is accepted`() {
        val accepted = accept(mid, dear)
        assertEquals(10, accepted.spent)
        assertEquals(0, accepted.remaining)
        assertEquals(listOf(mid, dear), accepted.team.map { it.species })
    }

    @Test
    fun `a team that spends under the budget is accepted, and the rest is not carried`() {
        // Under-spending is legal (§2.13). There is deliberately no rebate or carry — the leftover is
        // simply not spent, and a second currency is the thing that decision avoids.
        val accepted = accept(cheap)
        assertEquals(3, accepted.spent)
        assertEquals(7, accepted.remaining)
    }

    @Test
    fun `a team one point over the budget is refused`() {
        // One point, not obviously over: an off-by-one in the comparison is the failure that would
        // otherwise ship, since every wildly-over team is refused either way.
        val over = assertIs<StarterSelectionResult.OverBudget>(
            StarterSelection.validate(catalogue(budget = 12), listOf(cheap, mid, dear)),
        )
        assertEquals(13, over.spent)
        assertEquals(12, over.budget)
    }

    @Test
    fun `three cheap picks and one expensive pick are both legal openings`() {
        // The trade the budget exists to pose (§2.13). If either end stopped working the mechanic
        // would still look fine and would quietly have become "pick one".
        val wide = accept(cheap, alsoCheap, mid)
        assertEquals(3, wide.team.size)
        assertEquals(10, wide.spent)

        val tall = accept(dear)
        assertEquals(1, tall.team.size)
        assertEquals(4, tall.remaining)
    }

    @Test
    fun `a single pick dearer than the whole budget is refused`() {
        val over = assertIs<StarterSelectionResult.OverBudget>(
            StarterSelection.validate(catalogue(), listOf(unaffordable)),
        )
        assertEquals(12, over.spent)
    }

    @Test
    fun `the budget is read from the catalogue the player was shown`() {
        // Not from configuration. A `/reload` between the screen and the confirm must not change what
        // a team costs under a player who has already decided.
        assertIs<StarterSelectionResult.OverBudget>(StarterSelection.validate(catalogue(budget = 5), listOf(dear)))
        assertIs<StarterSelectionResult.Accepted>(StarterSelection.validate(catalogue(budget = 6), listOf(dear)))
    }

    // --- what is refused, and in what order --------------------------------------------------------

    @Test
    fun `an empty selection is refused rather than starting an empty run`() {
        assertEquals(StarterSelectionResult.Empty, StarterSelection.validate(catalogue(), emptyList()))
    }

    @Test
    fun `the same species twice is refused`() {
        val duplicate = assertIs<StarterSelectionResult.Duplicate>(
            StarterSelection.validate(catalogue(), listOf(cheap, cheap)),
        )
        assertEquals(cheap, duplicate.species)
    }

    @Test
    fun `a duplicate is reported before the budget, not counted twice into it`() {
        // Two of a 6-cost species is 12 points, so a budget check that ran first would report "over
        // budget" and send the player off to find a cheaper team they did not need.
        assertIs<StarterSelectionResult.Duplicate>(StarterSelection.validate(catalogue(), listOf(dear, dear)))
    }

    @Test
    fun `a species not in the catalogue is refused, listing every one that was wrong`() {
        val missing = assertIs<StarterSelectionResult.NotEligible>(
            StarterSelection.validate(catalogue(), listOf(cheap, id("nope"), id("also-nope"))),
        )
        assertEquals(listOf(id("nope"), id("also-nope")), missing.species)
    }

    @Test
    fun `an unpriced species is reported as unpriced, not as the player's mistake`() {
        // Both are "absent from the catalogue" and they have different culprits: this one is an
        // operator's missing table entry, and telling the player to pick something else would send
        // them looking for a fault that is not theirs.
        val gap = id("gap")
        val result = StarterSelection.validate(catalogue(unpriced = listOf(gap)), listOf(cheap, gap))
        assertEquals(listOf(gap), assertIs<StarterSelectionResult.Unpriced>(result).species)
    }

    @Test
    fun `more picks than a party holds is refused before the budget`() {
        // Seven 1-point species would fit a 10-point budget and would not fit a party. Reported as
        // "too many" rather than as an over-budget team, which it is not.
        val roomy = StarterCatalogue(budget = 10, options = (1..8).map { StarterOption(id("mon$it"), 1) })
        val many = assertIs<StarterSelectionResult.TooMany>(
            StarterSelection.validate(roomy, (1..7).map { id("mon$it") }),
        )
        assertEquals(StarterSelection.MAX_TEAM, many.max)
    }

    @Test
    fun `a full party is legal when the budget stretches to it`() {
        val roomy = StarterCatalogue(budget = 10, options = (1..8).map { StarterOption(id("mon$it"), 1) })
        val accepted = assertIs<StarterSelectionResult.Accepted>(
            StarterSelection.validate(roomy, (1..6).map { id("mon$it") }),
        )
        assertEquals(6, accepted.team.size)
        assertEquals(4, accepted.remaining)
    }

    @Test
    fun `the team keeps the order the player typed`() {
        // It becomes their party order and therefore their lead — the one part of selection that is a
        // tactical decision rather than an accounting one.
        assertEquals(listOf(dear, cheap), accept(dear, cheap).team.map { it.species })
    }

    @Test
    fun `the team cap matches the run party size`() {
        // Duplicated rather than imported so that starter/ does not depend on run/. If these drift, a
        // team of seven is silently truncated by the party and the player has paid for the one that
        // vanished.
        assertEquals(RunState.MAX_PARTY, StarterSelection.MAX_TEAM)
    }
}
