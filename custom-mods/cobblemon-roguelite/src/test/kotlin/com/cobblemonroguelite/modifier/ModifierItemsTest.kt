package com.cobblemonroguelite.modifier

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * The Kotlin half of §2.33's two-sided contract, tested the way [com.cobblemonroguelite.boss.BossShieldsTest]
 * tests the shields': the JS runs where no unit test can see it, so what is pinned here is everything
 * that breaks *silently* if it drifts — the Showdown ids the JS files' `name:` fields must equal, and
 * §2.34's upgrade ladder. [ModifierItems.mintStack] and [ModifierItems.heldShowdownId] touch
 * ItemStack/registries and are dev-VM territory, deliberately not reached from here.
 */
class ModifierItemsTest {

    // ── The id contract with the JS files ────────────────────────────────────────────────────

    @Test
    fun `multi lens tiers produce the ids the JS files declare`() {
        // These strings are `name:` in multi_lens_1.js / multi_lens_2.js, lowercased and stripped.
        // Change either side alone and a run Pokémon quietly holds an item Showdown has never
        // heard of — this assertion is the only thing that makes that loud.
        assertEquals("multilens1", ModifierItems.showdownId(PlayerModifier.MULTI_LENS, 1))
        assertEquals("multilens2", ModifierItems.showdownId(PlayerModifier.MULTI_LENS, 2))
    }

    @Test
    fun `a single-tier line carries no numeral in its id`() {
        // reviver_seed.js declares "Reviver Seed" -> reviverseed, not reviverseed1.
        assertEquals("reviverseed", ModifierItems.showdownId(PlayerModifier.REVIVER_SEED, 1))
    }

    @Test
    fun `a tier with no JS file is refused, not silently produced`() {
        // The BossShields.MAX_SHIELDS failure mode: an id with no file resolves to nothing and the
        // holder fights bare-handed with no log line. Refusing here keeps it a crash at mint time.
        assertFailsWith<IllegalArgumentException> { ModifierItems.showdownId(PlayerModifier.MULTI_LENS, 3) }
        assertFailsWith<IllegalArgumentException> { ModifierItems.showdownId(PlayerModifier.REVIVER_SEED, 2) }
        assertFailsWith<IllegalArgumentException> { ModifierItems.showdownId(PlayerModifier.MULTI_LENS, 0) }
    }

    // ── Reading a held id back ───────────────────────────────────────────────────────────────

    @Test
    fun `tierHeld inverts showdownId for every real tier`() {
        for (modifier in PlayerModifier.entries) {
            for (tier in 1..modifier.maxTier) {
                assertEquals(tier, ModifierItems.tierHeld(modifier, ModifierItems.showdownId(modifier, tier)))
            }
        }
    }

    @Test
    fun `tierHeld does not claim other items, other lines, or lookalike ids`() {
        assertEquals(0, ModifierItems.tierHeld(PlayerModifier.MULTI_LENS, null))
        assertEquals(0, ModifierItems.tierHeld(PlayerModifier.MULTI_LENS, "leftovers"))
        assertEquals(0, ModifierItems.tierHeld(PlayerModifier.MULTI_LENS, "reviverseed"))
        // Prefix alone must not match — the BossShields.isShieldItem rule, same reason.
        assertEquals(0, ModifierItems.tierHeld(PlayerModifier.MULTI_LENS, "multilens"))
        assertEquals(0, ModifierItems.tierHeld(PlayerModifier.MULTI_LENS, "multilens3"))
        assertEquals(0, ModifierItems.tierHeld(PlayerModifier.REVIVER_SEED, "reviverseed1"))
    }

    // ── §2.34: upgrade, never a second copy ──────────────────────────────────────────────────

    @Test
    fun `an empty or foreign held slot gets tier 1`() {
        assertEquals(
            ModifierItems.Decision.Grant(1, null),
            ModifierItems.decide(PlayerModifier.MULTI_LENS, null),
        )
        // Holding Leftovers (or any non-line item): fresh grant, displacement is the granter's
        // report to make.
        assertEquals(
            ModifierItems.Decision.Grant(1, null),
            ModifierItems.decide(PlayerModifier.MULTI_LENS, "leftovers"),
        )
        // Another modifier LINE is also foreign — a Reviver Seed holder picking Multi Lens is
        // choosing to switch lines, not to stack them.
        assertEquals(
            ModifierItems.Decision.Grant(1, null),
            ModifierItems.decide(PlayerModifier.MULTI_LENS, "reviverseed"),
        )
    }

    @Test
    fun `holding tier 1 upgrades to tier 2 in place`() {
        assertEquals(
            ModifierItems.Decision.Grant(2, 1),
            ModifierItems.decide(PlayerModifier.MULTI_LENS, "multilens1"),
        )
    }

    @Test
    fun `holding the ceiling is AlreadyMax, for one-tier and multi-tier lines alike`() {
        assertEquals(
            ModifierItems.Decision.AlreadyMax,
            ModifierItems.decide(PlayerModifier.MULTI_LENS, "multilens2"),
        )
        // A second Reviver Seed pick onto a holder must not silently re-mint the same item.
        assertEquals(
            ModifierItems.Decision.AlreadyMax,
            ModifierItems.decide(PlayerModifier.REVIVER_SEED, "reviverseed"),
        )
    }

    // ── Player-facing strings ────────────────────────────────────────────────────────────────

    @Test
    fun `display names use roman numerals on tiered lines and nothing on single-tier lines`() {
        // Roman because an arabic digit reads as a count, and §2.34's whole model is that tier is
        // identity, not quantity.
        assertEquals("Multi Lens I", ModifierItems.displayName(PlayerModifier.MULTI_LENS, 1))
        assertEquals("Multi Lens II", ModifierItems.displayName(PlayerModifier.MULTI_LENS, 2))
        assertEquals("Reviver Seed", ModifierItems.displayName(PlayerModifier.REVIVER_SEED, 1))
    }

    @Test
    fun `every tier has effect lore and it is never blank`() {
        for (modifier in PlayerModifier.entries) {
            for (tier in 1..modifier.maxTier) {
                val lore = ModifierItems.effectLore(modifier, tier)
                assert(lore.isNotBlank()) { "blank lore for ${modifier.id} tier $tier" }
            }
        }
    }

    @Test
    fun `table ids resolve and unknown ids do not`() {
        assertEquals(PlayerModifier.MULTI_LENS, PlayerModifier.byId("multi_lens"))
        assertEquals(PlayerModifier.REVIVER_SEED, PlayerModifier.byId("reviver_seed"))
        assertEquals(null, PlayerModifier.byId("multi_lens_1"))
        assertEquals(null, PlayerModifier.byId("multilens"))
    }
}
