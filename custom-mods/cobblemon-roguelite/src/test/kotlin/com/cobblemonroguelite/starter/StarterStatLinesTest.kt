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
        assertTrue(lines.any { it.contains("Growth:") && it.contains("Medium Slow") })
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
        // Single digits are zero-padded now, so the IV of 5 is the two-glyph field, not a bare " 5".
        assertTrue(atk.contains(StarterStatLines.figure(5, 2)), "the second IV landed on the wrong row: $atk")
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
    fun `a number field is the same pixel width at one, two and three digits`() {
        // The reported bug: a space is 4px and a digit is 6px, so padStart made `ATK  80` 2px narrower
        // than `ATK 130` and every bar after it started early. Padding with dimmed zeros makes the field
        // exactly three digit glyphs whatever the number.
        val digits = { field: String -> field.replace(Regex("§."), "").length }
        listOf(5, 80, 130, 255).forEach { value ->
            assertEquals(3, digits(StarterStatLines.figure(value, 3)), "figure($value) is not three glyphs")
        }
        // The pad is dimmed, not white, so it reads as alignment rather than as 080.
        assertTrue(StarterStatLines.figure(80, 3).startsWith("§80"), StarterStatLines.figure(80, 3))
        assertTrue(StarterStatLines.figure(130, 3).startsWith("§f"), StarterStatLines.figure(130, 3))
        // And an over-long number is never truncated to fit the field.
        assertEquals("§f1000", StarterStatLines.figure(1000, 3))
    }

    @Test
    fun `stat rows are all the same width, whatever their digits`() {
        val visible = { line: String -> line.replace(Regex("§."), "").length }
        val varied = bulbasaur.copy(
            baseStats = listOf("hp" to 5, "atk" to 130, "def" to 80, "spa" to 255, "spd" to 9, "spe" to 100),
            ivFloor = listOf(0, 31, 5, 10, 9, 30),
        )
        val rows = StarterStatLines.render(varied).filter { it.contains(StarterStatLines.COLUMN_DIVIDER) }
        assertEquals(6, rows.size)
        assertEquals(1, rows.map(visible).distinct().size, "rows are ragged: ${rows.map(visible)}")
    }

    @Test
    fun `growth is coloured by whether it helps`() {
        assertEquals("§a", StarterStatLines.growthColour("Fast"))
        assertEquals("§c", StarterStatLines.growthColour("Slow"))
        // Cobblemon hands these over with underscores; the sheet capitalises them for display, so both
        // spellings have to land on the same colour.
        assertEquals(StarterStatLines.growthColour("medium_fast"), StarterStatLines.growthColour("Medium Fast"))
    }

    @Test
    fun `the IV column carries no explanatory line`() {
        // It went through three wordings, each either the widest line on the panel or too terse to earn
        // its row. The column is two glyphs behind a divider; a player works that out once.
        val rows = StarterStatLines.render(bulbasaur.copy(ivFloor = listOf(31, 10, 10, 22, 10, 10)))
        assertTrue(rows.none { it.contains("every run") || it.contains("best so far") }, "$rows")
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
        assertTrue(rows.first().contains("Grass / Poison"), rows.first())
        // Growth is its own row at the bottom, and carries a colour of its own.
        val growth = rows.last()
        assertTrue(growth.contains("Growth:") && growth.contains("Medium Slow"), growth)
        assertTrue(growth.contains(StarterStatLines.growthColour("Medium Slow")), growth)
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
