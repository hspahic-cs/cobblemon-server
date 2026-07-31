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
    fun `each IV rides on its own stat row rather than in one wide line`() {
        // The wide "IVs HP10 Atk10 …" line asked a player to match six numbers back to six rows by
        // counting, and was the widest thing on the screen.
        val rows = StarterStatLines.render(bulbasaur.copy(ivFloor = listOf(31, 5, 10, 22, 10, 10)))

        val hp = rows.single { it.startsWith("§7HP") }
        assertTrue(hp.contains("45"), "the base stat left the row: $hp")
        assertTrue(hp.contains(StarterStatLines.COLUMN_DIVIDER), "no divider on the row: $hp")
        assertTrue(hp.contains("31"), "the IV is not on its stat's row: $hp")

        val atk = rows.single { it.startsWith("§7ATK") }
        assertTrue(atk.contains(" 5"), "the second IV landed on the wrong row: $atk")
        // Every stat row carries one, so none of the six is left unexplained.
        assertEquals(6, rows.count { it.contains(StarterStatLines.COLUMN_DIVIDER) })
    }

    @Test
    fun `IV bars are narrower than base stat bars and scale to 31`() {
        val rows = StarterStatLines.render(bulbasaur.copy(ivFloor = List(6) { StarterIvFloor.MAX_IV }))
        val hp = rows.single { it.startsWith("§7HP") }
        // Base-stat bar plus IV bar, and nothing else on the row draws pipes.
        assertEquals(StarterStatLines.BAR_WIDTH + StarterStatLines.IV_BAR_WIDTH, pipes(hp))
        // A perfect IV fills its bar.
        assertEquals(StarterStatLines.IV_BAR_WIDTH, pipes(hp.substringAfter(StarterStatLines.COLUMN_DIVIDER)))
    }

    @Test
    fun `the sheet says whether the floor is the base one or an earned one`() {
        val base = StarterStatLines.render(bulbasaur.copy(ivFloor = List(6) { StarterIvFloor.BASE }))
        assertTrue(base.any { it.contains("every run starts here") }, "base floor unlabelled: $base")

        val earned = StarterStatLines.render(bulbasaur.copy(ivFloor = listOf(31, 10, 10, 22, 10, 10)))
        assertTrue(earned.any { it.contains("your best so far") }, "earned floor unlabelled: $earned")
        // Both name the column the numbers are in, since nothing else does any more.
        assertTrue(earned.any { it.contains("IVs") })
    }

    @Test
    fun `no floor at all leaves the stat rows alone`() {
        // The progression store failing to answer is not the same as a floor of zero.
        val rows = StarterStatLines.render(bulbasaur)
        assertTrue(rows.none { it.contains(StarterStatLines.COLUMN_DIVIDER) })
        assertEquals(StarterStatLines.BAR_WIDTH, pipes(rows.single { it.startsWith("§7HP") }))
    }

    @Test
    fun `the panel is three blocks separated by rules`() {
        val rows = StarterStatLines.render(bulbasaur.copy(ivFloor = List(6) { StarterIvFloor.BASE }))
        assertEquals(2, rows.count { it == StarterStatLines.RULE }, "expected identity | stats | trailer")
        // Growth belongs with "what is this species", not alone at the bottom.
        val identity = rows.first()
        assertTrue(identity.contains("Grass / Poison") && identity.contains("Medium Slow"), identity)
    }

    @Test
    fun `a rule is never the first or last line`() {
        // A block with nothing under it would draw a rule against the edge of the tooltip.
        listOf(
            bulbasaur,
            bulbasaur.copy(abilities = emptyList(), hiddenAbility = null),
            bulbasaur.copy(types = emptyList(), growthRate = null),
            bulbasaur.copy(baseStats = emptyList()),
        ).forEach { sheet ->
            val rows = StarterStatLines.render(sheet)
            assertTrue(rows.firstOrNull() != StarterStatLines.RULE, "leading rule: $rows")
            assertTrue(rows.lastOrNull() != StarterStatLines.RULE, "trailing rule: $rows")
        }
    }

    @Test
    fun `every stat label is the same width, which is why they are upper case`() {
        // Minecraft's font is proportional: `t` is 4px against 6px for almost everything else, so the
        // old `Atk`/`Def` labels made the bars start in different columns.
        val widths = StarterStatLines.STAT_LABELS.map { label -> label.length }
        assertEquals(1, widths.distinct().size, "labels are not the same length: ${StarterStatLines.STAT_LABELS}")
        assertTrue(
            StarterStatLines.STAT_LABELS.all { label -> label.all { it.isUpperCase() || it == ' ' } },
            "a lower-case glyph is a different width: ${StarterStatLines.STAT_LABELS}",
        )
    }

    @Test
    fun `no line is much wider than a stat row`() {
        // Width has been the problem twice now: first the single wide "IVs HP10 Atk10 …" line, then the
        // sentence explaining the divider. A tooltip is only as narrow as its longest line, so this
        // pins the thing that keeps regressing rather than trusting the next author to notice.
        val visible = { line: String -> line.replace(Regex("§."), "").length }
        val rows = StarterStatLines.render(
            bulbasaur.copy(ivFloor = listOf(31, 10, 10, 22, 10, 10), hiddenAbilityUnlocked = true),
        )
        val statRow = visible(rows.single { it.startsWith("§7HP") })
        rows.forEach { line ->
            assertTrue(
                visible(line) <= statRow + 8,
                "'$line' is ${visible(line)} wide against a stat row's $statRow",
            )
        }
    }

    @Test
    fun `a species with nothing readable renders nothing rather than blank rows`() {
        // Every Cobblemon read in StarterStatSheets is wrapped and may come back empty; the panel is
        // allowed to be short, but it must not be six unlabelled empty lines.
        val bare = StarterStatSheet(types = emptyList(), baseStats = emptyList(), abilities = emptyList())
        assertTrue(StarterStatLines.render(bare).isEmpty())
    }
}
