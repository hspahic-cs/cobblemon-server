package com.cobblemonroguelite.data.arena

import com.cobblemonroguelite.data.DataProblems
import net.minecraft.resources.ResourceLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The palette parse rules.
 *
 * As elsewhere in this package the assertions are on message *wording*, because a validator that
 * rejects the right file while naming the wrong field has failed at the only job it has, and the
 * person reading it has no editor open — only the log.
 *
 * The case worth the most attention is [an air block is refused wherever it appears]. Every other
 * mistake here produces a file that visibly fails; that one produces a file that loads, generates,
 * and leaves a player standing in a void dimension with nothing under them.
 */
class ArenaPaletteParseTest {

    private val file = ResourceLocation.fromNamespaceAndPath("test", "meadow")

    private class Parsed(val palette: ArenaPalette?, val problems: DataProblems) {
        val messages: List<String> get() = problems.messages()
        fun mentions(vararg fragments: String) = messages.any { line -> fragments.all { it in line } }
    }

    private fun parse(json: String): Parsed {
        val problems = DataProblems(file)
        return Parsed(ArenaPalettes.parseJson(file, json.reader(), problems), problems)
    }

    @Test
    fun `a floor block on its own is a complete palette`() {
        // The minimum a non-builder has to type. Everything else has a default, and the defaults are
        // "as big as the arena box, no rim, no pillars, power spot on".
        val parsed = parse("""{ "floor": "minecraft:grass_block" }""")
        val palette = assertNotNull(parsed.palette)
        assertTrue(parsed.problems.isEmpty(), "unexpected problems: ${parsed.messages}")

        assertEquals(file, palette.id, "the id is the file path, not a field")
        assertEquals(ResourceLocation.withDefaultNamespace("grass_block"), palette.floor)
        assertNull(palette.width, "an absent size means the arena box, decided against the box in force")
        assertNull(palette.depth)
        assertNull(palette.rim)
        assertNull(palette.pillars)
        assertTrue(palette.powerSpot, "section 2.5's confinement is on unless somebody says otherwise")
    }

    @Test
    fun `a full palette reads every field`() {
        val parsed = parse(
            """
            {
              "floor": "minecraft:basalt",
              "width": 41,
              "depth": 31,
              "rim": { "block": "minecraft:polished_basalt", "height": 2 },
              "pillars": { "block": "minecraft:magma_block", "height": 8, "inset": 3 },
              "power_spot": false
            }
            """,
        )
        val palette = assertNotNull(parsed.palette)
        assertTrue(parsed.problems.isEmpty(), "unexpected problems: ${parsed.messages}")
        assertEquals(41, palette.width)
        assertEquals(31, palette.depth)
        assertEquals(ArenaRim(ResourceLocation.withDefaultNamespace("polished_basalt"), 2), palette.rim)
        assertEquals(ArenaPillars(ResourceLocation.withDefaultNamespace("magma_block"), 8, 3), palette.pillars)
        assertEquals(false, palette.powerSpot)
    }

    @Test
    fun `an air block is refused wherever it appears`() {
        // The one mistake that reads as a legal palette. `"floor": "minecraft:air"` parses, registers,
        // generates, and hands back an arena with no floor in a void dimension.
        val floor = parse("""{ "floor": "minecraft:air" }""")
        assertNull(floor.palette)
        assertTrue(floor.mentions("floor", "places nothing"), floor.messages.toString())

        val rim = parse("""{ "floor": "minecraft:stone", "rim": { "block": "minecraft:air" } }""")
        assertNull(rim.palette)
        assertTrue(rim.mentions("rim.block", "places nothing"), rim.messages.toString())
    }

    @Test
    fun `a missing floor names the field`() {
        val parsed = parse("""{ "width": 20 }""")
        assertNull(parsed.palette)
        assertTrue(parsed.mentions("floor", "missing"), parsed.messages.toString())
    }

    @Test
    fun `an unparseable block id is named rather than dropped`() {
        val parsed = parse("""{ "floor": "Not A Block" }""")
        assertNull(parsed.palette)
        assertTrue(parsed.mentions("floor", "namespace:path"), parsed.messages.toString())
    }

    @Test
    fun `a typo is an error rather than a silent default`() {
        // JsonView's trade, and it matters here: `"widht": 20` under a lenient reader is a palette
        // that quietly fills the whole arena box, months after somebody meant it to be 20 across.
        val parsed = parse("""{ "floor": "minecraft:stone", "widht": 20 }""")
        assertNull(parsed.palette)
        assertTrue(parsed.mentions("widht", "unknown field"), parsed.messages.toString())
    }

    @Test
    fun `a platform below the minimum footprint is refused with the minimum named`() {
        val parsed = parse("""{ "floor": "minecraft:stone", "width": 2, "depth": 40 }""")
        assertNull(parsed.palette)
        assertTrue(parsed.mentions("width", "3x3"), parsed.messages.toString())
    }

    @Test
    fun `a zero-height rim is refused rather than read as no rim`() {
        // Two spellings of "no rim" would mean an author who typed one of them and got the other has
        // nothing to look at. Omitting the object is the spelling; zero is a mistake.
        val parsed = parse("""{ "floor": "minecraft:stone", "rim": { "block": "minecraft:stone", "height": 0 } }""")
        assertNull(parsed.palette)
        assertTrue(parsed.mentions("rim.height", "omit"), parsed.messages.toString())
    }

    @Test
    fun `power_spot must be a boolean, not a string that looks like one`() {
        // "false" is truthy under every lenient parser ever written, so a deliberate opt-out would
        // silently become the opposite of what was typed.
        val parsed = parse("""{ "floor": "minecraft:stone", "power_spot": "false" }""")
        assertNull(parsed.palette)
        assertTrue(parsed.mentions("power_spot", "true or false"), parsed.messages.toString())
    }

    @Test
    fun `the shipped example palette is a valid palette`() {
        // It ships in the jar's data folder, so if it stops parsing every server logs a rejected file
        // on boot — and the one file an owner copies to start from would be broken.
        val stream = javaClass.getResourceAsStream("/data/cobblemon_roguelite/roguelite/arena_palettes/example.json")
        val json = assertNotNull(stream, "example.json is missing from the mod's resources").reader().readText()
        val parsed = parse(json)
        assertNotNull(parsed.palette, "shipped example failed to parse: ${parsed.messages}")
        assertTrue(parsed.problems.isEmpty(), "shipped example has problems: ${parsed.messages}")
    }

    @Test
    fun `every problem in one file is reported in one pass`() {
        // DataProblems' whole argument: an owner with four typos fixes them in one sitting rather
        // than in four server restarts.
        val parsed = parse(
            """
            {
              "floor": "minecraft:air",
              "width": 1,
              "rim": { "block": "minecraft:stone", "height": -1 }
            }
            """,
        )
        assertNull(parsed.palette)
        assertTrue(parsed.mentions("floor"), parsed.messages.toString())
        assertTrue(parsed.mentions("width"), parsed.messages.toString())
        assertTrue(parsed.mentions("rim.height"), parsed.messages.toString())
    }
}
