package com.cobblemonroguelite.data.starter

import com.cobblemonroguelite.data.DataProblems
import net.minecraft.resources.ResourceLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The starter cost parse rules.
 *
 * As elsewhere in `data/`, assertions are on message *wording*: a validator that rejects the right
 * file while naming the wrong field has failed at the only job it has. The cases that matter most
 * here are the ones where a mistake would otherwise **load** — a zero, a species priced twice — since
 * under §2.13 a wrong price is not a wrong number, it is a balance decision nobody made.
 */
class StarterCostTableParseTest {

    private val file = ResourceLocation.fromNamespaceAndPath("test", "costs")

    private class Parsed(val table: StarterCostTable?, val problems: DataProblems) {
        val messages: List<String> get() = problems.messages()
        fun mentions(vararg fragments: String) = messages.any { line -> fragments.all { it in line } }
    }

    private fun parse(json: String): Parsed {
        val problems = DataProblems(file)
        return Parsed(StarterCostTables.parseJson(file, json.reader(), problems), problems)
    }

    private fun id(name: String) = ResourceLocation.fromNamespaceAndPath("cobblemon", name)

    @Test
    fun `a minimal table loads`() {
        val parsed = parse(
            """
            {
              "costs": [
                { "species": "cobblemon:bulbasaur", "cost": 3 },
                { "species": "cobblemon:torchic", "cost": 4 }
              ]
            }
            """,
        )
        val table = assertNotNull(parsed.table)
        assertTrue(parsed.problems.isEmpty(), "unexpected problems: ${parsed.messages}")
        assertEquals(file, table.id)
        assertEquals(mapOf(id("bulbasaur") to 3, id("torchic") to 4), table.costs)
    }

    @Test
    fun `an underscore field is a comment and not an error`() {
        val parsed = parse(
            """
            {
              "_comment": "prices transcribed from our own table",
              "costs": [ { "species": "cobblemon:bulbasaur", "cost": 3 } ]
            }
            """,
        )
        assertNotNull(parsed.table)
        assertTrue(parsed.problems.isEmpty(), "unexpected problems: ${parsed.messages}")
    }

    @Test
    fun `a missing costs list rejects the file`() {
        val parsed = parse("""{ }""")
        assertNull(parsed.table)
        assertTrue(parsed.mentions("costs", "missing required field"))
    }

    @Test
    fun `an unknown field rejects rather than silently taking a default`() {
        // `"cots"` under a lenient reader would load a table with no prices in it and nothing in the
        // log — the exact failure this whole layer exists to prevent.
        val parsed = parse("""{ "costs": [ { "species": "cobblemon:bulbasaur", "cost": 3 } ], "budget": 10 }""")
        assertTrue(parsed.mentions("budget", "unknown field"))
    }

    @Test
    fun `a zero cost is dropped and named`() {
        // Zero is not a cheap starter, it is the budget switched off for that species. Silently
        // honouring it would be invisible until somebody worked out which one was free.
        val parsed = parse(
            """
            {
              "costs": [
                { "species": "cobblemon:bulbasaur", "cost": 0 },
                { "species": "cobblemon:torchic", "cost": 4 }
              ]
            }
            """,
        )
        val table = assertNotNull(parsed.table)
        assertEquals(mapOf(id("torchic") to 4), table.costs)
        assertTrue(parsed.mentions("cost", "at least 1"))
        assertTrue(parsed.mentions("1 entry/entries dropped"))
    }

    @Test
    fun `a negative cost is dropped`() {
        val parsed = parse("""{ "costs": [ { "species": "cobblemon:x", "cost": -2 }, { "species": "cobblemon:y", "cost": 3 } ] }""")
        assertEquals(mapOf(id("y") to 3), assertNotNull(parsed.table).costs)
    }

    @Test
    fun `a malformed species id is dropped and named`() {
        val parsed = parse(
            """
            {
              "costs": [
                { "species": "Not A Species", "cost": 3 },
                { "species": "cobblemon:torchic", "cost": 4 }
              ]
            }
            """,
        )
        assertEquals(mapOf(id("torchic") to 4), assertNotNull(parsed.table).costs)
        assertTrue(parsed.mentions("species", "Not A Species"))
    }

    @Test
    fun `a species priced twice in one file rejects the file`() {
        // Fatal rather than last-wins: which number the author meant is not guessable, and guessing
        // it is a balance decision.
        val parsed = parse(
            """
            {
              "costs": [
                { "species": "cobblemon:bulbasaur", "cost": 3 },
                { "species": "cobblemon:bulbasaur", "cost": 6 }
              ]
            }
            """,
        )
        assertNull(parsed.table)
        assertTrue(parsed.mentions("priced twice"))
    }

    @Test
    fun `a table where every entry failed is not loaded`() {
        // Would otherwise load as an empty table, price nothing, and look identical in the log to a
        // table that legitimately had nothing to say.
        val parsed = parse("""{ "costs": [ { "species": "cobblemon:bulbasaur", "cost": 0 } ] }""")
        assertNull(parsed.table)
        assertTrue(parsed.mentions("costs", "prices nothing"))
    }

    @Test
    fun `a fractional cost is rejected rather than truncated`() {
        val parsed = parse("""{ "costs": [ { "species": "cobblemon:bulbasaur", "cost": 3.5 } ] }""")
        assertNull(parsed.table)
        assertTrue(parsed.mentions("cost", "whole number"))
    }
}
