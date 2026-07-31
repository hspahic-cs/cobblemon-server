package com.cobblemonroguelite.starter

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The stat panel's text.
 *
 * The bar is the part worth testing: it takes a number from a datapack, which means it takes numbers
 * nobody planned for. A bar that renders eleven pipes on an unusual species would break the alignment
 * of the whole panel, and a negative one would throw inside `repeat`.
 */
class StarterStatLinesTest {

    private fun pipes(text: String) = text.count { it == '|' }

    private val bulbasaur = StarterStatSheet(
        types = listOf("Grass", "Poison"),
        baseStats = listOf("hp" to 45, "atk" to 49, "def" to 49, "spa" to 65, "spd" to 65, "spe" to 45),
        abilities = listOf("Overgrow"),
        hiddenAbility = "Chlorophyll",
        growthRate = "Medium Slow",
    )

    @Test
    fun `every bar is exactly the declared width, whatever the stat`() {
        listOf(0, 1, 45, 100, 160, 255, 999).forEach { value ->
            assertEquals(
                StarterStatLines.BAR_WIDTH,
                pipes(StarterStatLines.bar(value)),
                "a base stat of $value did not render a full-width bar",
            )
        }
    }

    @Test
    fun `a stat at or past the scale fills the bar and never overflows it`() {
        val full = StarterStatLines.bar(StarterStatLines.BAR_WIDTH.let { StarterStatLines.BASE_STAT_FULL })
        // Everything before the trailing-colour marker is filled, so an unfilled bar would still carry §8.
        assertFalse(full.substringAfter("§8").contains("|"), "a maxed stat left unfilled pipes")
        assertEquals(StarterStatLines.BAR_WIDTH, pipes(StarterStatLines.bar(9999)))
    }

    @Test
    fun `a zero or negative stat renders an empty bar rather than throwing`() {
        assertFalse(StarterStatLines.bar(0).substringBefore("§8").contains("|"))
        assertEquals(StarterStatLines.BAR_WIDTH, pipes(StarterStatLines.bar(-20)))
    }

    @Test
    fun `bars are coloured by how full they are`() {
        assertTrue(StarterStatLines.bar(150).startsWith("§a"), "a high stat should read green")
        assertTrue(StarterStatLines.bar(80).startsWith("§e"), "a middling stat should read amber")
        assertTrue(StarterStatLines.bar(20).startsWith("§c"), "a low stat should read red")
    }

    @Test
    fun `a zero scale cannot divide by zero`() {
        assertEquals(StarterStatLines.BAR_WIDTH, pipes(StarterStatLines.bar(50, full = 0)))
    }

    @Test
    fun `the sheet totals its own base stats`() {
        assertEquals(318, bulbasaur.baseStatTotal)
    }

    @Test
    fun `the panel names types, stats, ability and growth`() {
        val lines = StarterStatLines.render(bulbasaur)
        assertTrue(lines.any { it.contains("Grass / Poison") })
        assertTrue(lines.any { it.contains("BST") && it.contains("318") })
        assertTrue(lines.any { it.contains("Overgrow") })
        assertTrue(lines.any { it.contains("Medium Slow") })
        // One row per stat, each labelled — six bars with no labels would be unreadable as a group.
        StarterStatLines.STAT_LABELS.forEach { label ->
            assertTrue(lines.any { it.contains(label.trim()) }, "no row for $label")
        }
    }

    @Test
    fun `a locked hidden ability is shown as locked rather than hidden`() {
        // Knowing a species HAS one you have not unlocked is what makes the unlock worth wanting.
        val locked = StarterStatLines.render(bulbasaur).single { it.contains("Hidden") }
        assertTrue(locked.contains("Chlorophyll"))
        assertTrue(locked.contains("locked"))

        val unlocked = StarterStatLines.render(bulbasaur.copy(hiddenAbilityUnlocked = true))
            .single { it.contains("Hidden") }
        assertFalse(unlocked.contains("locked"))
    }

    @Test
    fun `the IV floor line appears only when there is one to show`() {
        assertTrue(StarterStatLines.render(bulbasaur).none { it.contains("IVs") })

        val earned = StarterStatLines.render(bulbasaur.copy(ivFloor = listOf(31, 10, 10, 22, 10, 10)))
        assertTrue(earned.any { it.contains("IVs at least") && it.contains("31") && it.contains("22") })
    }

    @Test
    fun `a species with nothing readable renders nothing rather than blank rows`() {
        // Every Cobblemon read in StarterStatSheets is wrapped and may come back empty; the panel is
        // allowed to be short, but it must not be six unlabelled empty lines.
        val bare = StarterStatSheet(types = emptyList(), baseStats = emptyList(), abilities = emptyList())
        assertTrue(StarterStatLines.render(bare).isEmpty())
    }
}
