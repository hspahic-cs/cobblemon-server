package com.cobblemonroguelite.data.shop

import com.cobblemonroguelite.data.DataProblems
import net.minecraft.resources.ResourceLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The shop table parse rules — where a server owner's mispricing either gets named or gets swallowed.
 *
 * Assertions on message wording are deliberate, for the reason
 * [com.cobblemonroguelite.data.reward.RewardTableParseTest] gives: a validator that rejects the right
 * file while naming the wrong field has failed at its only job.
 *
 * The last test loads the **example.json this mod actually ships**. That file is the first thing an
 * author copies, and an example that does not parse teaches the wrong schema — it caught a real
 * mistake when written (`"type": "ability_patch"`, where the reward type is `ability`).
 */
class ShopTableParseTest {

    private val file = ResourceLocation.fromNamespaceAndPath("test", "example")

    private class Parsed(val table: ShopTable?, val problems: DataProblems) {
        val messages: List<String> get() = problems.messages()
        fun mentions(vararg fragments: String) = messages.any { line -> fragments.all { it in line } }
    }

    private fun parse(json: String): Parsed {
        val problems = DataProblems(file)
        return Parsed(ShopTables.parseJson(file, json.reader(), problems), problems)
    }

    private fun oneEntry(fields: String) = """
        { "entries": [ { "id": "x", "price": 100, "reward": { "type": "level", "amount": 1 }$fields } ] }
    """.trimIndent()

    @Test
    fun `a minimal entry loads and takes documented defaults`() {
        val parsed = parse(oneEntry(""))
        val table = assertNotNull(parsed.table)
        assertEquals(1, table.entries.size)
        val entry = table.entries.single()
        assertEquals(1, entry.minWave)
        assertNull(entry.maxWave)
        assertNull(entry.priceCurve)
        assertTrue(parsed.problems.isEmpty(), "unexpected problems: ${parsed.messages}")
    }

    @Test
    fun `a price of zero is allowed as a giveaway`() {
        val parsed = parse("""{ "entries": [ { "id": "gift", "price": 0, "reward": { "type": "level", "amount": 1 } } ] }""")
        assertEquals(0, assertNotNull(parsed.table).entries.single().price)
    }

    @Test
    fun `a negative price is rejected because it would pay the player to take a reward`() {
        val parsed = parse("""{ "entries": [ { "id": "x", "price": -5, "reward": { "type": "level", "amount": 1 } } ] }""")
        assertNull(parsed.table)
        assertTrue(parsed.mentions("price", "negative"), parsed.messages.toString())
    }

    @Test
    fun `a mistyped price drops the entry rather than defaulting it`() {
        // The failure this layer exists for: a price that silently became a default would misprice
        // the shop and look like it loaded correctly.
        val parsed = parse("""{ "entries": [ { "id": "x", "price": "cheap", "reward": { "type": "level", "amount": 1 } } ] }""")
        assertNull(parsed.table)
    }

    @Test
    fun `max_wave below min_wave is rejected`() {
        val parsed = parse(oneEntry(""", "min_wave": 50, "max_wave": 10"""))
        assertNull(parsed.table)
        assertTrue(parsed.mentions("max_wave", "min_wave"), parsed.messages.toString())
    }

    @Test
    fun `a duplicate entry id is fatal rather than last-wins`() {
        val parsed = parse(
            """
            { "entries": [
              { "id": "dupe", "price": 1, "reward": { "type": "level", "amount": 1 } },
              { "id": "dupe", "price": 2, "reward": { "type": "level", "amount": 1 } }
            ] }
            """.trimIndent(),
        )
        assertNull(parsed.table)
        assertTrue(parsed.mentions("duplicate", "dupe"), parsed.messages.toString())
    }

    @Test
    fun `a weight field is rejected, because the paid row is not a weighted draw`() {
        // `weight` was a real field until the between-wave step was split in two. It is gone rather
        // than ignored: the paid row stocks the same items every wave in authored order, so a weight
        // would be config that silently does nothing — which is worse than an error.
        val parsed = parse(oneEntry(""", "weight": 2"""))
        assertNull(parsed.table)
        assertTrue(parsed.mentions("weight"), parsed.messages.toString())
    }

    @Test
    fun `an unknown field is an error rather than being ignored`() {
        val parsed = parse(oneEntry(""", "cost": 50"""))
        assertNull(parsed.table)
        assertTrue(parsed.mentions("cost"), parsed.messages.toString())
    }

    @Test
    fun `a table with no usable entries is not loaded`() {
        val parsed = parse("""{ "entries": [] }""")
        assertNull(parsed.table)
        assertTrue(parsed.mentions("no usable entries"), parsed.messages.toString())
    }

    @Test
    fun `one bad entry costs that entry and the table still loads`() {
        val parsed = parse(
            """
            { "entries": [
              { "id": "good", "price": 10, "reward": { "type": "level", "amount": 1 } },
              { "id": "bad", "price": -1, "reward": { "type": "level", "amount": 1 } }
            ] }
            """.trimIndent(),
        )
        val table = assertNotNull(parsed.table)
        assertEquals(listOf("good"), table.entries.map { it.id })
        assertTrue(parsed.mentions("dropped"), parsed.messages.toString())
    }

    @Test
    fun `a malformed price curve drops the entry instead of silently going flat`() {
        val parsed = parse(oneEntry(""", "price_curve": [ { "wave": 0, "weight": 100 } ]"""))
        assertNull(parsed.table)
        assertTrue(parsed.mentions("wave", "at least 1"), parsed.messages.toString())
    }

    @Test
    fun `a price curve is sorted by wave regardless of authoring order`() {
        val parsed = parse(
            oneEntry(""", "price_curve": [ { "wave": 100, "weight": 200 }, { "wave": 1, "weight": 100 } ]"""),
        )
        val curve = assertNotNull(assertNotNull(parsed.table).entries.single().priceCurve)
        assertEquals(listOf(1, 100), curve.points.map { it.wave })
    }

    @Test
    fun `the example json this mod ships actually parses`() {
        val json = checkNotNull(
            ShopTables::class.java.getResourceAsStream(
                "/data/cobblemon_roguelite/roguelite/shop_tables/example.json",
            ),
        ).reader().readText()
        val parsed = parse(json)
        val table = assertNotNull(parsed.table, "the shipped example failed to load: ${parsed.messages}")
        assertTrue(table.entries.isNotEmpty())
        assertTrue(parsed.problems.isEmpty(), "the shipped example produced problems: ${parsed.messages}")
    }
}
