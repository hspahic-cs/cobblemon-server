package com.cobblemonroguelite.starter

import net.minecraft.resources.ResourceLocation

/** What a player's proposed starting team came to. */
sealed interface StarterSelectionResult {

    /**
     * The team is legal and this is what it costs.
     *
     * [team] preserves the order the player asked for, because that becomes their party order and
     * therefore their lead — the one part of the selection that is a tactical decision rather than an
     * accounting one.
     */
    data class Accepted(val team: List<StarterOption>, val spent: Int, val remaining: Int) : StarterSelectionResult

    /** Nothing was picked. A run needs at least one Pokémon; there is no party to start with. */
    data object Empty : StarterSelectionResult

    /** Species the player asked for that are not in their catalogue at all — unknown, or excluded. */
    data class NotEligible(val species: List<ResourceLocation>) : StarterSelectionResult

    /**
     * Species that *are* eligible but that no cost source could price. Split from [NotEligible]
     * because the fix is different and belongs to a different person: this one is an operator's
     * missing table entry, not a player's typo.
     */
    data class Unpriced(val species: List<ResourceLocation>) : StarterSelectionResult

    /** The same species twice. See [StarterSelection] for why that is refused rather than allowed. */
    data class Duplicate(val species: ResourceLocation) : StarterSelectionResult

    /** More picks than a party holds, whatever they cost. */
    data class TooMany(val max: Int) : StarterSelectionResult

    /** The team is legal in every other way and costs more than the player has. */
    data class OverBudget(val spent: Int, val budget: Int) : StarterSelectionResult
}

/**
 * The budget arithmetic (§2.13): a player spends up to their points on a starting team.
 *
 * ### Under-spending is legal, and that is a decision
 *
 * A team must fit the budget *exactly or under*. Leftover points are simply not spent — there is no
 * carry, no conversion, no rebate, because every one of those would be a second currency to explain
 * and a second thing to balance. What it does mean is that "one expensive Pokémon" and "three cheap
 * ones" are both real openings, which is the trade the budget exists to pose.
 *
 * ### Why the same species may not be taken twice
 *
 * Two of a species is legal in mainline and is not obviously broken here, but it is a strictly
 * narrower decision than the budget is asking for — and it interacts badly with everything keyed on
 * species. Candy, cost reductions and the §2.17 IV floor are all *per species*, so a doubled pick
 * would be one purchase discounted twice and two starters floored identically, which reads as a
 * discovered trick rather than a choice. Refusing is also the reversible direction: allowing it later
 * costs a line, forbidding it later invalidates saved teams.
 *
 * ### Why validation is a pure function over the catalogue
 *
 * The catalogue the player was shown carries its own budget and its own prices ([StarterCatalogue]),
 * so the numbers they were judged against are provably the ones they saw. Re-reading the price of a
 * species here — from config, from the table, from progression — would open a window where a
 * `/reload` between the screen and the confirm silently changes what a team costs.
 */
object StarterSelection {

    /**
     * Most starters a team may contain, whatever the budget allows.
     *
     * Mirrors [com.cobblemonroguelite.run.RunState.MAX_PARTY] and is duplicated rather than imported
     * so that `starter/` does not depend on `run/` — the dependency already runs the other way. A test
     * asserts the two are equal, so drift breaks the build rather than the game: a team of seven would
     * be silently truncated by the party, and the player would have paid points for the missing one.
     */
    const val MAX_TEAM = 6

    fun validate(catalogue: StarterCatalogue, picks: List<ResourceLocation>): StarterSelectionResult {
        if (picks.isEmpty()) return StarterSelectionResult.Empty

        // Duplicates first. They are the only fault that makes the rest of the checks lie — a
        // repeated species would be counted twice against the budget and reported twice as unknown.
        val seen = mutableSetOf<ResourceLocation>()
        picks.forEach { if (!seen.add(it)) return StarterSelectionResult.Duplicate(it) }

        // Unpriced before ineligible: an unpriced species *is* absent from the catalogue, so the
        // ineligible check would also catch it and would blame the player for an operator's gap.
        val unpriced = picks.filter { it in catalogue.unpriced }
        if (unpriced.isNotEmpty()) return StarterSelectionResult.Unpriced(unpriced)

        val missing = picks.filterNot(catalogue::contains)
        if (missing.isNotEmpty()) return StarterSelectionResult.NotEligible(missing)

        // Party size before budget. Both are refusals, but this one does not depend on the prices,
        // so reporting it first keeps "you picked eight" from arriving as "you cannot afford this".
        if (picks.size > MAX_TEAM) return StarterSelectionResult.TooMany(MAX_TEAM)

        val team = picks.map { StarterOption(it, catalogue.costOf(it)!!) }
        val spent = team.sumOf { it.cost }
        if (spent > catalogue.budget) return StarterSelectionResult.OverBudget(spent, catalogue.budget)

        return StarterSelectionResult.Accepted(team, spent, catalogue.budget - spent)
    }
}
