package com.cobblemonroguelite.starter

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * What a hidden-ability unlock actually grants (§2.27).
 *
 * None of this is reachable through the grant itself in a plain JUnit run — a `Species`, a `Pokemon`
 * and Cobblemon's `Abilities` registry all need a booted server — so the decision was extracted into
 * [HiddenAbilityUnlock] precisely so it does not ship having never executed. What is under test is
 * every case where the unlock does *not* simply hand over the hidden ability, because those are the
 * ones that would otherwise present as candy spent for nothing, which is the failure §2.27 exists to
 * remove.
 *
 * [HiddenAbilityGrant] is the untested half by construction: it flattens a pool, calls this, and
 * writes the result with `updateAbility`. That needs the dev VM.
 */
class HiddenAbilityUnlockTest {

    private val installed = setOf("speedboost", "blaze", "solarpower", "hugepower", "truant")
    private val exists: (String) -> Boolean = { it in installed }

    /** Torchic: two ordinary abilities and Speed Boost in the slot Cobblemon never rolls. */
    private val torchic = listOf(
        PoolAbility("blaze", hidden = false),
        PoolAbility("speedboost", hidden = true),
    )

    @Test
    fun `the hidden ability is the default, and it is identified by its slot and not its position`() {
        // The pool is ordered by priority, not by "hidden last", so picking by index would be a
        // coincidence that holds until the first species that disagrees. Cobblemon tags the entry —
        // that tag is the only thing this may key on.
        val choice = HiddenAbilityUnlock.choose(torchic, override = null, abilityExists = exists)
        assertIs<HiddenAbilityChoice.FromPool>(choice)
        assertEquals("speedboost", choice.entry.name)
        assertEquals(1, choice.index)
    }

    @Test
    fun `a species with no hidden ability grants nothing rather than falling back to an ordinary one`() {
        // The exact sale §2.27 was written to prevent. Falling back to `blaze` here would be worse
        // than granting nothing: the player would have paid for an ability the Pokémon already had.
        val ordinaryOnly = listOf(PoolAbility("blaze", hidden = false))
        assertEquals(HiddenAbilityChoice.None, HiddenAbilityUnlock.choose(ordinaryOnly, null, exists))
        assertNull(HiddenAbilityUnlock.choose(ordinaryOnly, null, exists).ability)
    }

    @Test
    fun `a hidden entry naming an ability this server does not have is treated as none`() {
        // A species file may name an ability no installed mod defines; the entry still loads. Selling
        // an unlock against it would take candy and grant nothing, so the check is on the *ability*
        // and not merely on the slot being occupied.
        val ghost = listOf(
            PoolAbility("blaze", hidden = false),
            PoolAbility("abilityfromamodwedonothave", hidden = true),
        )
        assertEquals(HiddenAbilityChoice.None, HiddenAbilityUnlock.choose(ghost, null, exists))
    }

    @Test
    fun `the first resolvable hidden entry wins when a species declares more than one`() {
        val two = listOf(
            PoolAbility("notinstalled", hidden = true),
            PoolAbility("truant", hidden = true),
        )
        val choice = HiddenAbilityUnlock.choose(two, null, exists)
        assertIs<HiddenAbilityChoice.FromPool>(choice)
        assertEquals("truant", choice.entry.name)
        assertEquals(1, choice.index)
    }

    @Test
    fun `an override replaces the hidden ability, which is the whole point of having one`() {
        // §2.27's stated weakness and its fix: Truant is a joke and Speed Boost wins games, so a
        // server must be able to hand-assign as PokéRogue does. An override outside the pool is
        // pinned rather than coordinated — there is no pool entry for Cobblemon to carry forward.
        val truantOnly = listOf(PoolAbility("truant", hidden = true))
        val choice = HiddenAbilityUnlock.choose(truantOnly, override = "hugepower", abilityExists = exists)
        assertIs<HiddenAbilityChoice.Pinned>(choice)
        assertEquals("hugepower", choice.name)
    }

    @Test
    fun `an override that restates the species own hidden ability keeps the pool coordinate`() {
        // Writing a species' own hidden ability into the table is the natural way to document "this
        // one is fine" — and it would be a trap if doing so silently changed the evolution behaviour
        // by pinning what would otherwise be carried forward.
        val choice = HiddenAbilityUnlock.choose(torchic, override = "speedboost", abilityExists = exists)
        assertIs<HiddenAbilityChoice.FromPool>(choice)
        assertEquals(1, choice.index)
    }

    @Test
    fun `an override naming an ability nobody installed is reported rather than silently ignored`() {
        // Falling back to the hidden ability here would hide an operator's typo behind a working
        // feature: the table would say Huge Power and the game would hand out Truant forever.
        val choice = HiddenAbilityUnlock.choose(torchic, override = "hugepowre", abilityExists = exists)
        assertIs<HiddenAbilityChoice.Unknown>(choice)
        assertEquals("hugepowre", choice.name)
        assertNull(choice.ability)
    }

    @Test
    fun `an override is matched the way the game is read, not the way the registry is keyed`() {
        // `Abilities.get` lowercases and nothing else, so an operator writing the name as it is
        // printed would miss the entry — and a table that prices an ability nobody can be granted is
        // the expensive kind of typo.
        val choice = HiddenAbilityUnlock.choose(torchic, override = "Speed Boost", abilityExists = exists)
        assertIs<HiddenAbilityChoice.FromPool>(choice)
        assertEquals("speedboost", choice.entry.name)
        assertEquals("hugepower", HiddenAbilityUnlock.normalise("Huge-Power"))
    }

    @Test
    fun `a blank override is not an override`() {
        // The loader rejects blanks, but the decision must not depend on that: an empty string read
        // as "grant nothing" would take a species' hidden ability away over whitespace.
        val choice = HiddenAbilityUnlock.choose(torchic, override = "   ", abilityExists = exists)
        assertIs<HiddenAbilityChoice.FromPool>(choice)
        assertEquals("speedboost", choice.entry.name)
    }

    @Test
    fun `an empty pool grants nothing`() {
        assertEquals(HiddenAbilityChoice.None, HiddenAbilityUnlock.choose(emptyList(), null, exists))
        // …but an override still applies to it. A species with no abilities at all is a data fault,
        // and an operator assigning one is exactly how it gets worked around.
        assertIs<HiddenAbilityChoice.Pinned>(HiddenAbilityUnlock.choose(emptyList(), "blaze", exists))
    }
}
