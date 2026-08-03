package com.cobblemonroguelite.data.wild

import com.cobblemonroguelite.data.DataProblems
import net.minecraft.resources.ResourceLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The wild pool parse rules.
 *
 * This registry exists because its absence was the second thing that stopped a run being playable —
 * the seam it fills had no data-driven source at all, so a server could do everything right and still
 * refuse wave 1. The tests that matter most are therefore the ones about a pool that *looks* fine and
 * produces nothing: [an empty entry list is refused rather than loaded as a pool that draws nothing]
 * and [a zero weight disables an entry without deleting it].
 */
class WildPoolParseTest {

    private val file = ResourceLocation.fromNamespaceAndPath("test", "grassland")

    private class Parsed(val pool: WildPool?, val problems: DataProblems) {
        val messages: List<String> get() = problems.messages()
        fun mentions(vararg fragments: String) = messages.any { line -> fragments.all { it in line } }
    }

    private fun parse(json: String): Parsed {
        val problems = DataProblems(file)
        return Parsed(WildSpeciesPools.parseJson(file, json.reader(), problems), problems)
    }

    @Test
    fun `a minimal pool loads with the window left open`() {
        val parsed = parse("""{ "entries": [ { "species": "cobblemon:pidgey" } ] }""")
        val entry = assertNotNull(parsed.pool).entries.single()

        assertEquals(ResourceLocation.fromNamespaceAndPath("cobblemon", "pidgey"), entry.species)
        assertEquals(1.0, entry.weight)
        assertEquals(1, entry.minWave)
        assertNull(entry.maxWave)
        assertNull(entry.properties)
        // Open-ended means every wave, which is what a pool with one species has to mean.
        assertTrue(entry.covers(1) && entry.covers(200))
    }

    @Test
    fun `an empty entry list is refused rather than loaded as a pool that draws nothing`() {
        // The exact shape of the bug this registry was added for: a file that loads clean, a registry
        // that reports success, and wild waves that refuse with nothing to point at.
        val parsed = parse("""{ "entries": [] }""")
        assertNull(parsed.pool)
        assertTrue(parsed.mentions("entries", "cannot produce an encounter"), "${parsed.messages}")
    }

    @Test
    fun `a zero weight disables an entry without deleting it`() {
        // How the shipped example can exist without sending anybody to it.
        val parsed = parse(
            """{ "entries": [ { "species": "cobblemon:pidgey", "weight": 0 } ] }""",
        )
        val pool = assertNotNull(parsed.pool)
        assertEquals(0.0, pool.entries.single().weight)
        assertTrue(parsed.messages.isEmpty(), "a disabled entry is not a problem: ${parsed.messages}")
    }

    @Test
    fun `a wave window is respected at both ends`() {
        val parsed = parse(
            """{ "entries": [ { "species": "cobblemon:caterpie", "min_wave": 5, "max_wave": 10 } ] }""",
        )
        val entry = assertNotNull(parsed.pool).entries.single()
        assertTrue(!entry.covers(4) && entry.covers(5) && entry.covers(10) && !entry.covers(11))
    }

    @Test
    fun `a backwards window is refused, since nothing could ever match it`() {
        val parsed = parse(
            """{ "entries": [ { "species": "cobblemon:caterpie", "min_wave": 20, "max_wave": 5 } ] }""",
        )
        assertNull(parsed.pool)
        assertTrue(parsed.mentions("max_wave", "before min_wave"), "${parsed.messages}")
    }

    @Test
    fun `waves are 1-based`() {
        val parsed = parse("""{ "entries": [ { "species": "cobblemon:caterpie", "min_wave": 0 } ] }""")
        assertNull(parsed.pool)
        assertTrue(parsed.mentions("min_wave", "1-based"), "${parsed.messages}")
    }

    @Test
    fun `an unparseable species id is named rather than silently dropped`() {
        val parsed = parse("""{ "entries": [ { "species": "not a species id" } ] }""")
        assertNull(parsed.pool)
        assertTrue(parsed.mentions("species", "not a valid id"), "${parsed.messages}")
    }

    @Test
    fun `an unknown field is an error rather than a silent default`() {
        val parsed = parse(
            """{ "entries": [ { "species": "cobblemon:pidgey", "wieght": 3 } ] }""",
        )
        assertNull(parsed.pool)
        assertTrue(parsed.mentions("wieght"), "a typo should be reported: ${parsed.messages}")
    }

    @Test
    fun `properties ride through unparsed, and blank is the same as absent`() {
        // Cobblemon owns the properties grammar; validating it here would reject anything Cobblemon
        // learns to accept later.
        val parsed = parse(
            """{ "entries": [
                 { "species": "cobblemon:pidgey", "properties": "shiny=true" },
                 { "species": "cobblemon:rattata", "properties": "   " }
               ] }""",
        )
        val entries = assertNotNull(parsed.pool).entries
        assertEquals("shiny=true", entries[0].properties)
        assertNull(entries[1].properties)
    }
}
