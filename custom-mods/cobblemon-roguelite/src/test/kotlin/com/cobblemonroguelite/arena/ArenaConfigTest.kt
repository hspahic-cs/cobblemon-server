package com.cobblemonroguelite.arena

import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The config's own refusals, and build band routing.
 *
 * The refusals are the interesting half. Both of the things [ArenaConfig] rejects — overlapping slots
 * and slots inside twice Mega Showdown's power-spot range — produce a server that boots, runs, and is
 * wrong in a way that only shows up with two concurrent runs at the right moment. Turning them into a
 * construction failure means the operator who typed the number finds out immediately, with the number
 * in the message.
 */
class ArenaConfigTest {

    private fun template(name: String): ArenaBuild =
        ArenaBuild.Template(ResourceLocation.fromNamespaceAndPath("cobblemon_roguelite", name))

    private fun palette(name: String): ArenaBuild =
        ArenaBuild.Palette(ResourceLocation.fromNamespaceAndPath("cobblemon_roguelite", name))

    @Test
    fun `spacing inside the arena footprint is refused`() {
        val error = assertFailsWith<IllegalArgumentException> {
            ArenaConfig(spacing = 64, box = ArenaBox(width = 64, depth = 64))
        }
        assertTrue(error.message!!.contains("overlap"))
    }

    @Test
    fun `spacing inside twice the power spot range is refused`() {
        // Clears the footprint check and is still wrong: a power spot in arena N reaches arena N+1,
        // which presents as Dynamax working where nobody placed a spot for it.
        val error = assertFailsWith<IllegalArgumentException> {
            ArenaConfig(spacing = 33, box = ArenaBox(width = 16, height = 8, depth = 16))
        }
        assertTrue(error.message!!.contains("power spot"))
    }

    @Test
    fun `an arena box big enough to stall the server thread is refused`() {
        // Every pass over an arena runs inline on the server thread, and a full-box palette plans one
        // block per cell of its footprint. A typo of an extra digit is a hitch turning into a hang.
        assertFailsWith<IllegalArgumentException> { ArenaBox(width = 640, height = 320, depth = 640) }
    }

    @Test
    fun `the shipped defaults construct`() {
        val config = ArenaConfig()
        assertEquals(1024, config.spacing)
        assertEquals(32, config.layout().capacity)
        // Middle of the floor, one block up, so a player is never placed inside the template's floor.
        assertEquals(BlockPos(32, 1, 32), config.entryOffset)
    }

    @Test
    fun `with no bands every wave gets the same build`() {
        val builds = ArenaBuilds(default = template("arena"))
        assertEquals(template("arena"), builds.buildFor(1))
        assertEquals(template("arena"), builds.buildFor(200))
    }

    @Test
    fun `bands are matched in order, first match wins`() {
        // Same precedence convention as RewardRouting, deliberately: an author reads it top-down and
        // will meet both. An overlapping band is legal and resolves to the earlier one.
        val builds = ArenaBuilds(
            default = template("arena"),
            bands = listOf(
                ArenaBand(minWave = 1, maxWave = 50, build = template("early")),
                ArenaBand(minWave = 25, maxWave = 150, build = template("mid")),
                ArenaBand(minWave = 151, build = template("late")),
            ),
        )
        assertEquals(template("early"), builds.buildFor(1))
        assertEquals(template("early"), builds.buildFor(50))
        assertEquals(template("mid"), builds.buildFor(51))
        assertEquals(template("mid"), builds.buildFor(150))
        assertEquals(template("late"), builds.buildFor(151))
        assertEquals(template("late"), builds.buildFor(10_000))
    }

    @Test
    fun `a wave no band covers falls back to the default build`() {
        // Unlike reward routing, "nothing" is not an option here: an arena that is not stamped is a
        // void with a player in it, so an uncovered wave has to resolve to something.
        val builds = ArenaBuilds(
            default = template("arena"),
            bands = listOf(ArenaBand(minWave = 100, build = template("late"))),
        )
        assertEquals(template("arena"), builds.buildFor(99))
    }

    @Test
    fun `a palette and a template with the same id are different builds`() {
        // Load-bearing, not pedantry. `ns:meadow` is a legal id for both, and [RunArenas.prepare]
        // decides whether to re-stamp by comparing the wanted build against the standing one. Comparing
        // bare ids would report "already correct" after switching a biome from an .nbt to a palette,
        // and skip the stamp that replaces the arena.
        assertNotEquals<ArenaBuild>(template("meadow"), palette("meadow"))

        val builds = ArenaBuilds(
            default = template("meadow"),
            bands = listOf(ArenaBand(minWave = 100, build = palette("meadow"))),
        )
        assertEquals(template("meadow"), builds.buildFor(99))
        assertEquals(palette("meadow"), builds.buildFor(100))
    }

    @Test
    fun `a band that could never match is refused at construction`() {
        assertFailsWith<IllegalArgumentException> { ArenaBand(minWave = 50, maxWave = 10, build = template("x")) }
        assertFailsWith<IllegalArgumentException> { ArenaBand(minWave = 0, build = template("x")) }
    }
}
