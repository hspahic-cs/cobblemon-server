package com.cobblemonroguelite.run

import net.minecraft.resources.ResourceLocation
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Which roster a run uses, and where that answer comes from.
 *
 * The whole point of the pin is that it survives the configuration changing under it, and there is no
 * way to observe that from inside a run — a run whose ladder was swapped at wave 150 looks exactly
 * like a run whose ladder was always that one. So it gets asserted here instead, from the outside.
 *
 * The registry is empty in a test, so every binding resolves [RunRoster.Missing]; what is under test
 * is *which id* it is missing, which is precisely the question.
 */
class RunRostersTest {

    private fun id(path: String) = ResourceLocation.fromNamespaceAndPath("test", path)

    @AfterTest
    fun restoreSettings() = RunSettings.reset()

    @Test
    fun `a run binds the roster it pinned, not the one configured now`() {
        RunSettings.set(RunConfig(trainerRoster = id("retuned")))
        val run = RunState(seed = 1L, trainerRoster = id("pinned_at_start"))
        assertEquals(RunRoster.Missing(id("pinned_at_start")), RunRosters.bind(run))
    }

    @Test
    fun `a run carrying no pinned id does not silently borrow the configured one`() {
        // Falling back to the live config here would be the mid-run substitution the pin exists to
        // prevent, arrived at by a different route — and it would look like a working run.
        RunSettings.set(RunConfig(trainerRoster = id("configured")))
        assertEquals(RunRoster.Missing(null), RunRosters.bind(RunState(seed = 1L)))
    }

    @Test
    fun `an id with nothing loaded under it is missing, not empty`() {
        // "No roster" and "a roster with no bands" have to be different answers: the second is a
        // parse failure the registry already refuses, and treating the first as a usable empty roster
        // is how a run ends up composing its Elite Four waves as ordinary wild encounters.
        assertIs<RunRoster.Missing>(RunRosters.bind(RunRosters.DEFAULT_ROSTER))
    }

    @Test
    fun `the shipped default is the id a fresh run pins`() {
        assertEquals(RunRosters.DEFAULT_ROSTER, RunConfig().trainerRoster)
    }
}
