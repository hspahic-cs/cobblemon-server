package com.cobblemonbridge.roguelite

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The one piece of the roguelite trainer path that is decidable without a server.
 *
 * Everything else in `roguelite/` is reflection into two mods that are not on any classpath here, so
 * it is verified at boot (eager resolve, loud on failure) rather than in a test. This is the bit
 * that is pure logic and easy to get quietly wrong: a roster written with bare trainer ids and one
 * written with namespaced ones must both find the same RCT trainer.
 */
class RctTrainerPartsTest {

    @Test
    fun `a bare roster id is tried without its implied minecraft namespace first`() {
        // `ResourceLocation.tryParse("gym_01_clay")` yields minecraft:gym_01_clay, and RCT keys its
        // registry on the bare stem. Trying "minecraft:gym_01_clay" first would miss every trainer.
        assertEquals(
            listOf("gym_01_clay", "minecraft:gym_01_clay"),
            RctTrainerParts.trainerIdCandidates("minecraft", "gym_01_clay"),
        )
    }

    @Test
    fun `an rctmod-namespaced id is treated as the same bare id`() {
        assertEquals(
            listOf("bt_01_ground", "rctmod:bt_01_ground"),
            RctTrainerParts.trainerIdCandidates("rctmod", "bt_01_ground"),
        )
    }

    @Test
    fun `any other namespace was written deliberately and is tried as-is first`() {
        // Our own datapack does carry ids like `server:gym_01_ground` — see DataPackManagerMixin,
        // which exists because RCT chokes on the colon in one specific resource lookup.
        assertEquals(
            listOf("server:gym_01_ground", "gym_01_ground"),
            RctTrainerParts.trainerIdCandidates("server", "gym_01_ground"),
        )
    }
}
