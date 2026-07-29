package com.cobblemonroguelite.data.biome

import com.cobblemonroguelite.data.DataProblems
import net.minecraft.resources.ResourceLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The biome parse rules.
 *
 * As elsewhere in this package, the assertions are on message *wording*: a validator that rejects the
 * right file while naming the wrong field has failed at the only job it has, and the person reading
 * the message has no editor open — only the log.
 *
 * The case worth the most attention is [a zero weight is a disabled biome, not a rejected one], since
 * that is how this mod ships an example without sending anybody to it.
 */
class RunBiomeParseTest {

    private val file = ResourceLocation.fromNamespaceAndPath("test", "meadow")

    private class Parsed(val biome: RunBiome?, val problems: DataProblems) {
        val messages: List<String> get() = problems.messages()
        fun mentions(vararg fragments: String) = messages.any { line -> fragments.all { it in line } }
    }

    private fun parse(json: String): Parsed {
        val problems = DataProblems(file)
        return Parsed(RunBiomes.parseJson(file, json.reader(), problems), problems)
    }

    private val minimal = """
        {
          "display_name": "Grassy Field",
          "arena_template": "test:arena/grassland",
          "minecraft_biome": "minecraft:plains"
        }
    """

    @Test
    fun `a minimal biome loads with the band left open`() {
        val parsed = parse(minimal)
        val biome = assertNotNull(parsed.biome)
        assertTrue(parsed.problems.isEmpty(), "unexpected problems: ${parsed.messages}")

        assertEquals(file, biome.id, "the id is the file path, not a field")
        assertEquals("Grassy Field", biome.displayName)
        assertEquals(ResourceLocation.fromNamespaceAndPath("test", "arena/grassland"), biome.arenaTemplate)
        assertEquals(ResourceLocation.fromNamespaceAndPath("minecraft", "plains"), biome.minecraftBiome)
        assertEquals(1, biome.minWave)
        assertNull(biome.maxWave, "an absent max_wave must mean unbounded, not wave 0")
        assertEquals(1.0, biome.weight)
        assertTrue(biome.covers(1) && biome.covers(200))
    }

    @Test
    fun `wave bounds are inclusive on both ends`() {
        val biome = assertNotNull(
            parse(
                """
                {
                  "display_name": "Volcano",
                  "arena_template": "test:arena/volcano",
                  "minecraft_biome": "minecraft:basalt_deltas",
                  "min_wave": 100,
                  "max_wave": 150
                }
                """,
            ).biome,
        )
        assertTrue(biome.covers(100) && biome.covers(150))
        assertTrue(!biome.covers(99) && !biome.covers(151))
    }

    @Test
    fun `a zero weight is a disabled biome, not a rejected one`() {
        // How the shipped example documents the schema without appearing in anybody's run. It has to
        // *load* — a rejected file logs an error every reload, which teaches operators to ignore them.
        val parsed = parse(
            """
            {
              "display_name": "Example",
              "arena_template": "test:arena/example",
              "minecraft_biome": "minecraft:meadow",
              "weight": 0
            }
            """,
        )
        val biome = assertNotNull(parsed.biome)
        assertTrue(parsed.problems.isEmpty(), "a disabled biome must not be an error: ${parsed.messages}")
        assertEquals(0.0, biome.weight)
    }

    @Test
    fun `a missing required field names the field`() {
        val parsed = parse("""{ "display_name": "Nowhere" }""")
        assertNull(parsed.biome)
        assertTrue(parsed.mentions("arena_template", "missing"), parsed.messages.toString())
        assertTrue(parsed.mentions("minecraft_biome", "missing"), parsed.messages.toString())
    }

    @Test
    fun `an unparseable id is named rather than dropped`() {
        val parsed = parse(
            """
            {
              "display_name": "Broken",
              "arena_template": "test:arena/ok",
              "minecraft_biome": "Not An Id"
            }
            """,
        )
        assertNull(parsed.biome)
        assertTrue(parsed.mentions("minecraft_biome", "namespace:path"), parsed.messages.toString())
    }

    @Test
    fun `a backwards band is refused rather than loaded as unreachable`() {
        val parsed = parse(
            """
            {
              "display_name": "Impossible",
              "arena_template": "test:arena/ok",
              "minecraft_biome": "minecraft:plains",
              "min_wave": 100,
              "max_wave": 50
            }
            """,
        )
        assertNull(parsed.biome)
        assertTrue(parsed.mentions("max_wave", "before min_wave"), parsed.messages.toString())
    }

    @Test
    fun `a blank display name is refused`() {
        // It is what a player is told they arrived in, and "You have arrived in ." is a message that
        // reads as a bug in the mode rather than a mistake in a file.
        val parsed = parse(
            """
            {
              "display_name": "  ",
              "arena_template": "test:arena/ok",
              "minecraft_biome": "minecraft:plains"
            }
            """,
        )
        assertNull(parsed.biome)
        assertTrue(parsed.mentions("display_name", "blank"), parsed.messages.toString())
    }

    @Test
    fun `a typo in a field name is reported instead of silently defaulted`() {
        // `wieght` under a lenient reader loads clean, takes the default, and shows up months later
        // as "my rare biome appears as often as the common one".
        val parsed = parse(
            """
            {
              "display_name": "Typo",
              "arena_template": "test:arena/ok",
              "minecraft_biome": "minecraft:plains",
              "wieght": 5
            }
            """,
        )
        assertNull(parsed.biome)
        assertTrue(parsed.mentions("wieght", "unknown field"), parsed.messages.toString())
    }

    @Test
    fun `underscore fields are comments and do not fail the file`() {
        val parsed = parse(
            """
            {
              "_comment": "example only",
              "display_name": "Commented",
              "arena_template": "test:arena/ok",
              "minecraft_biome": "minecraft:plains"
            }
            """,
        )
        assertNotNull(parsed.biome)
        assertTrue(parsed.problems.isEmpty(), parsed.messages.toString())
    }
}
