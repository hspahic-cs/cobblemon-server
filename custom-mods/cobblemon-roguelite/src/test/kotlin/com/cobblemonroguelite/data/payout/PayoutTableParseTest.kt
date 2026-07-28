package com.cobblemonroguelite.data.payout

import com.cobblemonroguelite.data.DataProblems
import net.minecraft.resources.ResourceLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The payout parse rules.
 *
 * Held to a higher standard than the reward tables are, for one reason: a reward that loads wrong
 * costs a wave, while a payout that loads wrong is the only thing a run hands over permanently
 * (§1.1). The cases that matter most here are the ones where a mistake would *load* — a missing
 * `outcomes`, a partly-parsed outcome list — rather than the ones that obviously fail.
 *
 * As in [com.cobblemonroguelite.data.reward.RewardTableParseTest], assertions are on message
 * wording: a validator that rejects the right file while naming the wrong field has failed.
 */
class PayoutTableParseTest {

    private val file = ResourceLocation.fromNamespaceAndPath("test", "payout")

    private class Parsed(val table: PayoutTable?, val problems: DataProblems) {
        val messages: List<String> get() = problems.messages()
        fun mentions(vararg fragments: String) = messages.any { line -> fragments.all { it in line } }
    }

    private fun parse(json: String): Parsed {
        val problems = DataProblems(file)
        return Parsed(PayoutTables.parseJson(file, json.reader(), problems), problems)
    }

    @Test
    fun `a minimal entry loads with the depth bounds filled in`() {
        val parsed = parse(
            """
            {
              "entries": [
                {
                  "id": "clear",
                  "outcomes": [ "completed" ],
                  "grant": { "type": "item", "item": "minecraft:diamond" }
                }
              ]
            }
            """,
        )
        val table = assertNotNull(parsed.table)
        assertTrue(parsed.problems.isEmpty(), "unexpected problems: ${parsed.messages}")
        assertEquals(file, table.id)

        val entry = table.entries.single()
        assertEquals("clear", entry.id)
        assertEquals(setOf(RunOutcome.COMPLETED), entry.outcomes)
        assertEquals(1, entry.minWave)
        assertNull(entry.maxWave, "an absent max_wave must mean unbounded, not wave 0")
        assertEquals(PayoutGrant.Item(ResourceLocation.fromNamespaceAndPath("minecraft", "diamond"), 1), entry.grant)
    }

    @Test
    fun `an omitted outcomes list is rejected rather than meaning every outcome`() {
        val parsed = parse(
            """
            {
              "entries": [
                { "id": "clear", "grant": { "type": "item", "item": "diamond" } }
              ]
            }
            """,
        )
        assertNull(parsed.table, "an entry with no outcomes must not load as one that pays for all of them")
        assertTrue(
            parsed.mentions("outcomes", "completed, wiped, abandoned"),
            "the message must say what to write, not only that something is missing: ${parsed.messages}",
        )
    }

    @Test
    fun `an unknown outcome is named, with the accepted values`() {
        val parsed = parse(
            """
            {
              "entries": [
                { "id": "clear", "outcomes": [ "finished" ], "grant": { "type": "item", "item": "diamond" } }
              ]
            }
            """,
        )
        assertNull(parsed.table)
        assertTrue(parsed.mentions("outcomes", "'finished'", "completed, wiped, abandoned"), parsed.messages.toString())
    }

    @Test
    fun `one bad member fails the whole outcome list instead of narrowing it`() {
        val parsed = parse(
            """
            {
              "entries": [
                {
                  "id": "clear",
                  "outcomes": [ "completed", 7 ],
                  "grant": { "type": "item", "item": "diamond" }
                }
              ]
            }
            """,
        )
        // The dangerous reading is "loads, paying only completed" — the author wrote two conditions
        // and would get one, with the table looking like it works.
        assertNull(parsed.table)
        assertTrue(parsed.mentions("outcomes[1]", "expected a string"), parsed.messages.toString())
    }

    @Test
    fun `an empty outcome list is rejected`() {
        val parsed = parse(
            """
            {
              "entries": [
                { "id": "clear", "outcomes": [], "grant": { "type": "item", "item": "diamond" } }
              ]
            }
            """,
        )
        assertNull(parsed.table)
        assertTrue(parsed.mentions("outcomes", "at least one"), parsed.messages.toString())
    }

    @Test
    fun `a repeated outcome is reported and the entry is dropped`() {
        val parsed = parse(
            """
            {
              "entries": [
                {
                  "id": "clear",
                  "outcomes": [ "wiped", "wiped" ],
                  "grant": { "type": "item", "item": "diamond" }
                }
              ]
            }
            """,
        )
        assertNull(parsed.table, "a reported problem drops the entry, and a table with no entries does not load")
        assertTrue(parsed.mentions("outcomes", "more than once"), parsed.messages.toString())
    }

    @Test
    fun `an unqualified item id defaults to minecraft`() {
        val parsed = parse(
            """
            {
              "entries": [
                { "id": "clear", "outcomes": [ "completed" ], "grant": { "type": "item", "item": "diamond", "count": 3 } }
              ]
            }
            """,
        )
        val table = assertNotNull(parsed.table)
        assertEquals(PayoutGrant.Item(ResourceLocation.fromNamespaceAndPath("minecraft", "diamond"), 3), table.entries.single().grant)
    }

    @Test
    fun `a currency-shaped grant type is not expressible`() {
        val parsed = parse(
            """
            {
              "entries": [
                { "id": "cash", "outcomes": [ "completed" ], "grant": { "type": "currency", "amount": 500 } }
              ]
            }
            """,
        )
        // §2.20: the payout is not currency. The schema is where that is enforced, not a convention.
        assertNull(parsed.table)
        assertTrue(parsed.mentions("type", "'currency'"), parsed.messages.toString())
    }

    @Test
    fun `a count below one is rejected`() {
        val parsed = parse(
            """
            {
              "entries": [
                { "id": "clear", "outcomes": [ "completed" ], "grant": { "type": "item", "item": "diamond", "count": 0 } }
              ]
            }
            """,
        )
        assertNull(parsed.table)
        assertTrue(parsed.mentions("count", "at least 1"), parsed.messages.toString())
    }

    @Test
    fun `a max_wave before min_wave is rejected`() {
        val parsed = parse(
            """
            {
              "entries": [
                {
                  "id": "clear",
                  "outcomes": [ "wiped" ],
                  "min_wave": 50,
                  "max_wave": 10,
                  "grant": { "type": "item", "item": "diamond" }
                }
              ]
            }
            """,
        )
        assertNull(parsed.table)
        assertTrue(parsed.mentions("max_wave", "could never pay"), parsed.messages.toString())
    }

    @Test
    fun `a typo is an error rather than a silent default`() {
        val parsed = parse(
            """
            {
              "entries": [
                {
                  "id": "clear",
                  "outcomes": [ "completed" ],
                  "min_wav": 5,
                  "grant": { "type": "item", "item": "diamond" }
                }
              ]
            }
            """,
        )
        assertNull(parsed.table)
        assertTrue(parsed.mentions("min_wav", "unknown field"), parsed.messages.toString())
    }

    @Test
    fun `duplicate entry ids fail the file`() {
        val parsed = parse(
            """
            {
              "entries": [
                { "id": "clear", "outcomes": [ "completed" ], "grant": { "type": "item", "item": "diamond" } },
                { "id": "clear", "outcomes": [ "wiped" ], "grant": { "type": "item", "item": "emerald" } }
              ]
            }
            """,
        )
        assertNull(parsed.table, "which of two entries under one id a payout log names is not guessable")
        assertTrue(parsed.mentions("duplicate entry id", "clear"), parsed.messages.toString())
    }

    @Test
    fun `a bad entry costs that entry and the rest of the table still loads`() {
        val parsed = parse(
            """
            {
              "entries": [
                { "id": "good", "outcomes": [ "completed" ], "grant": { "type": "item", "item": "diamond" } },
                { "id": "bad", "outcomes": [ "completed" ], "grant": { "type": "item" } }
              ]
            }
            """,
        )
        val table = assertNotNull(parsed.table)
        assertEquals(listOf("good"), table.entries.map { it.id })
        assertTrue(parsed.mentions("item", "missing required field"), parsed.messages.toString())
        assertTrue(parsed.mentions("entries", "dropped"), parsed.messages.toString())
    }

    @Test
    fun `a table with no usable entries does not load`() {
        val parsed = parse("""{ "entries": [] }""")
        assertNull(parsed.table)
        assertTrue(parsed.mentions("entries", "no usable entries"), parsed.messages.toString())
    }
}
