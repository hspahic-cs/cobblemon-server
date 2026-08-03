package com.cobblemonroguelite.progression

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * §2.15's candy sources and §2.17's IV floor — the arithmetic, with no Pokémon anywhere near it.
 *
 * That absence is the point. A `Pokemon` cannot be constructed outside a booted game, so every rule
 * expressed in terms of one is code that ships never having run. [SpeciesProgress] takes an [IvFloor]
 * and an int, and [RunProgression] is the thin adapter that produces them, so all of this executes.
 */
class CandyCreditTest {

    private val rules = CandyRules()

    @Test
    fun `an ordinary catch is worth one candy`() {
        val after = SpeciesProgress.EMPTY.creditCatch(IvFloor.flat(0), NOT_SHINY, rules)
        assertEquals(1, after.candy)
    }

    @Test
    fun `a shiny catch is worth the tier price instead of one, not as well as`() {
        // §2.15 quotes PokéRogue's 5/10/20, which are totals — a shiny is worth 5, not 6.
        assertEquals(5, SpeciesProgress.EMPTY.creditCatch(IvFloor.flat(0), 0, rules).candy)
        assertEquals(10, SpeciesProgress.EMPTY.creditCatch(IvFloor.flat(0), 1, rules).candy)
        assertEquals(20, SpeciesProgress.EMPTY.creditCatch(IvFloor.flat(0), 2, rules).candy)
    }

    @Test
    fun `a variant past the end of the table takes the top tier rather than throwing`() {
        // Cobblemon cannot produce this today (shiny is a boolean), but an addon inventing a tier
        // must not be able to crash a capture.
        assertEquals(20, SpeciesProgress.EMPTY.creditCatch(IvFloor.flat(0), 7, rules).candy)
    }

    @Test
    fun `candy accumulates across catches`() {
        var progress = SpeciesProgress.EMPTY
        repeat(3) { progress = progress.creditCatch(IvFloor.flat(0), NOT_SHINY, rules) }
        progress = progress.creditCatch(IvFloor.flat(0), 0, rules)
        assertEquals(8, progress.candy)
    }

    @Test
    fun `a catch raises the floor stat by stat and never lowers one`() {
        val first = SpeciesProgress.EMPTY.creditCatch(
            IvFloor(hp = 31, attack = 0, defence = 12, specialAttack = 5, specialDefence = 9, speed = 31),
            NOT_SHINY, rules,
        )
        // Base 10 holds wherever the catch was worse; the catch wins wherever it was better.
        assertEquals(IvFloor(31, 10, 12, 10, 10, 31), first.floor)

        val second = first.creditCatch(
            IvFloor(hp = 2, attack = 31, defence = 12, specialAttack = 31, specialDefence = 0, speed = 0),
            NOT_SHINY, rules,
        )
        assertEquals(IvFloor(31, 31, 12, 31, 10, 31), second.floor)
    }

    @Test
    fun `a fresh species is at base ten, not zero`() {
        // §2.17's flat base. Zero here would make an unearned species strictly worse than a random
        // wild Pokémon, which is the opposite of what the floor is for.
        assertEquals(IvFloor.flat(10), SpeciesProgress.EMPTY.floor)
        assertTrue(SpeciesProgress.EMPTY.isEmpty())
    }

    @Test
    fun `friendship converts at the threshold and carries the remainder`() {
        val threshold = rules.friendshipThreshold
        val part = SpeciesProgress.EMPTY.creditFriendship(threshold - 1, rules)
        assertEquals(0, part.candy)
        assertEquals(threshold - 1, part.friendship)

        // The carry is what stops two sub-threshold grants from being worth one candy and then
        // nothing at all.
        val paid = part.creditFriendship(2, rules)
        assertEquals(1, paid.candy)
        assertEquals(1, paid.friendship)
    }

    @Test
    fun `a grant several thresholds wide pays for all of them`() {
        val paid = SpeciesProgress.EMPTY.creditFriendship(rules.friendshipThreshold * 3 + 4, rules)
        assertEquals(3, paid.candy)
        assertEquals(4, paid.friendship)
    }

    @Test
    fun `friendship uses the per-cost threshold when the species cost is known`() {
        val scaled = CandyRules(friendshipThreshold = 150, friendshipThresholdByCost = mapOf(1 to 20))
        // A 1-cost species candies fast; an unknown cost falls back to the flat rule rather than
        // refusing, because this module does not own the cost table (§2.13).
        assertEquals(1, SpeciesProgress.EMPTY.creditFriendship(20, scaled, starterCost = 1).candy)
        assertEquals(0, SpeciesProgress.EMPTY.creditFriendship(20, scaled).candy)
    }

    @Test
    fun `a zero or negative friendship grant changes nothing`() {
        val before = SpeciesProgress(candy = 3, friendship = 40)
        assertEquals(before, before.creditFriendship(0, rules))
        assertEquals(before, before.creditFriendship(-100, rules))
    }

    private companion object {
        /** What [RunProgression] passes for a Pokémon that is not shiny. */
        const val NOT_SHINY = -1
    }
}
