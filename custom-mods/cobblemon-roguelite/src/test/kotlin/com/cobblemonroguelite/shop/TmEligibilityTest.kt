package com.cobblemonroguelite.shop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The TM learnset gate, over the pure overload — the live-Pokémon adapter cannot run here (a
 * `Pokemon` and a `Learnset` need a booted game), so what is pinned is the decision itself.
 *
 * The failure this guards is silent in both directions. Gate too loose and a Magikarp learns
 * Earthquake, which is the exact playtest ruling (2026-07-31) this exists to enforce; gate too tight
 * and a legal pick is refused after the player has already spent their wave's free option on the
 * command path. Neither throws, so neither would be caught by anything but a player.
 */
class TmEligibilityTest {

    private val learnable = listOf("thunderbolt", "surf", "protect")

    @Test
    fun `a move outside the learnset is refused and names both the species and the move`() {
        val reason = TmEligibility.blockReason(
            moveName = "earthquake", moveDisplay = "Earthquake", speciesName = "Magikarp",
            knownMoveNames = listOf("splash"), learnableMoveNames = learnable,
        )
        // The message is player-facing as-is (call sites only append the consequence), so it has to
        // carry enough to act on: WHO cannot learn WHAT is what lets the player re-aim.
        assertEquals("Magikarp can't learn Earthquake", reason)
    }

    @Test
    fun `a learnable, unknown move passes`() {
        assertNull(
            TmEligibility.blockReason(
                moveName = "surf", moveDisplay = "Surf", speciesName = "Lapras",
                knownMoveNames = listOf("icebeam"), learnableMoveNames = learnable,
            ),
        )
    }

    @Test
    fun `a move already known is refused as known, even when the learnset also lists it`() {
        val reason = TmEligibility.blockReason(
            moveName = "thunderbolt", moveDisplay = "Thunderbolt", speciesName = "Pikachu",
            knownMoveNames = listOf("thunderbolt"), learnableMoveNames = learnable,
        )
        assertEquals("Pikachu already knows Thunderbolt", reason)
    }

    @Test
    fun `already-knows wins over the learnset check`() {
        // A run Pokémon can know a move that is NOT in its legal learnset — a starter template, an
        // event move. Telling the player their Pokémon "can't learn" a move it is currently using
        // would be obviously wrong, so the ordering inside blockReason is load-bearing.
        val reason = TmEligibility.blockReason(
            moveName = "vcreate", moveDisplay = "V-create", speciesName = "Rayquaza",
            knownMoveNames = listOf("vcreate"), learnableMoveNames = learnable,
        )
        assertEquals("Rayquaza already knows V-create", reason)
    }

    @Test
    fun `move-name comparison is case-insensitive on both lists`() {
        // Moves.getByName normalises, but reward tables are hand-typed and the learnset side comes
        // from whatever a datapack wrote — the same tolerance every other comparison in this module
        // already extends.
        assertNull(
            TmEligibility.blockReason(
                moveName = "Surf", moveDisplay = "Surf", speciesName = "Lapras",
                knownMoveNames = listOf("IceBeam"), learnableMoveNames = listOf("SURF"),
            ),
        )
        assertTrue(
            TmEligibility.blockReason(
                moveName = "THUNDERBOLT", moveDisplay = "Thunderbolt", speciesName = "Pikachu",
                knownMoveNames = listOf("Thunderbolt"), learnableMoveNames = learnable,
            ) != null,
        )
    }

    @Test
    fun `an empty learnset refuses rather than passing`() {
        // The degenerate datapack case: a species whose learnset failed to load must not become a
        // species that can learn everything.
        assertTrue(
            TmEligibility.blockReason(
                moveName = "surf", moveDisplay = "Surf", speciesName = "MissingNo",
                knownMoveNames = emptyList(), learnableMoveNames = emptyList(),
            ) != null,
        )
    }

    // ------------------------------------------------------------------ the icon id

    @Test
    fun `a plain move id maps straight onto the SimpleTMs item path`() {
        assertEquals("tm_flamethrower", tmItemId("flamethrower"))
    }

    @Test
    fun `hyphens, capitals and spaces are normalised the way SimpleTMs keys its items`() {
        // SimpleTMs keys by lowercase-alphanumeric Showdown id (ops/gen-tm-items.py reads the list
        // out of the jar): tm_uturn, tm_willowisp. A hand-typed table saying "U-turn" must still
        // find the disc rather than silently painting the fallback book.
        assertEquals("tm_uturn", tmItemId("U-turn"))
        assertEquals("tm_willowisp", tmItemId("Will-O-Wisp"))
        assertEquals("tm_swordsdance", tmItemId("Swords Dance"))
    }
}
