package com.cobblemonbridge.gymtp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pure-JVM tests for [GymTpVisibility]. Uses a fake [AdvancementChecker] (set of strings)
 * so we don't need a ServerPlayer / Minecraft runtime.
 */
class GymTpVisibilityTest {

    @Test
    fun `gym 1 is always visible even with no advancements`() {
        val entries = mapOf("1" to entry())
        val visible = GymTpVisibility.visibleFor(entries, checker())
        assertEquals(1, visible.size)
        assertEquals("1", visible[0].id)
        assertEquals(VisibleEntry.State.AVAILABLE, visible[0].state)
        assertEquals("Gym 1", visible[0].label)
    }

    @Test
    fun `numeric id visible after prereq beaten`() {
        val entries = mapOf("2" to entry())
        assertEquals(emptyList<VisibleEntry>(), GymTpVisibility.visibleFor(entries, checker()))
        val visible = GymTpVisibility.visibleFor(entries, checker("server:beat_gym_1"))
        assertEquals(1, visible.size)
        assertEquals(VisibleEntry.State.AVAILABLE, visible[0].state)
    }

    @Test
    fun `beaten gym shows as BEATEN`() {
        val entries = mapOf("3" to entry())
        val visible = GymTpVisibility.visibleFor(entries, checker("server:beat_gym_2", "server:beat_gym_3"))
        assertEquals(1, visible.size)
        assertEquals(VisibleEntry.State.BEATEN, visible[0].state)
    }

    @Test
    fun `beaten only with no prereq still surfaces as BEATEN`() {
        // E.g., admin-granted advancement without sequential gym progression.
        val entries = mapOf("5" to entry())
        val visible = GymTpVisibility.visibleFor(entries, checker("server:beat_gym_5"))
        assertEquals(1, visible.size)
        assertEquals(VisibleEntry.State.BEATEN, visible[0].state)
    }

    @Test
    fun `string id requires explicit unlock advancement`() {
        val entries = mapOf("rotating" to entry())  // no unlock set
        assertEquals(emptyList<VisibleEntry>(), GymTpVisibility.visibleFor(entries, checker("anything")))
    }

    @Test
    fun `string id with explicit unlock is visible iff player holds the unlock`() {
        val entries = mapOf("rotating" to entry(unlock = "server:beat_gym_10"))
        assertEquals(emptyList<VisibleEntry>(), GymTpVisibility.visibleFor(entries, checker()))
        val visible = GymTpVisibility.visibleFor(entries, checker("server:beat_gym_10"))
        assertEquals(1, visible.size)
        assertEquals(VisibleEntry.State.OTHER, visible[0].state)
        assertEquals("Rotating Gyms", visible[0].label)
    }

    @Test
    fun `explicit unlock overrides numeric default`() {
        // Gym 5 with custom unlock — even if you'd normally see it via beat_gym_4, here you don't.
        val entries = mapOf("5" to entry(unlock = "server:special_unlock"))
        assertEquals(emptyList<VisibleEntry>(), GymTpVisibility.visibleFor(entries, checker("server:beat_gym_4")))
        val visible = GymTpVisibility.visibleFor(entries, checker("server:special_unlock"))
        assertEquals(1, visible.size)
    }

    @Test
    fun `explicit unlock + numeric id + beaten surfaces BEATEN`() {
        val entries = mapOf("5" to entry(unlock = "server:special"))
        val visible = GymTpVisibility.visibleFor(
            entries,
            checker("server:special", "server:beat_gym_5"),
        )
        assertEquals(VisibleEntry.State.BEATEN, visible[0].state)
    }

    @Test
    fun `custom label wins over auto-formatted`() {
        val entries = mapOf("1" to entry(label = "Pewter City Gym"))
        val visible = GymTpVisibility.visibleFor(entries, checker())
        assertEquals("Pewter City Gym", visible[0].label)
    }

    @Test
    fun `numeric ids sort ascending, string ids preserve insertion order at the end`() {
        val entries = linkedMapOf(
            "rotating" to entry(unlock = "server:beat_gym_10"),
            "10" to entry(),
            "elite4" to entry(unlock = "server:beat_gym_10"),
            "2" to entry(),
            "1" to entry(),
        )
        val visible = GymTpVisibility.visibleFor(
            entries,
            checker(
                "server:beat_gym_1", "server:beat_gym_2", "server:beat_gym_9", "server:beat_gym_10",
            ),
        )
        assertEquals(listOf("1", "2", "10", "rotating", "elite4"), visible.map { it.id })
    }

    @Test
    fun `auto label for string id capitalizes and appends Gyms`() {
        val entries = mapOf(
            "rotating" to entry(unlock = "server:any"),
            "snake_island" to entry(unlock = "server:any"),
            "pro_oak" to entry(unlock = "server:any"),
        )
        val visible = GymTpVisibility.visibleFor(entries, checker("server:any"))
        assertEquals("Rotating Gyms", visible.first { it.id == "rotating" }.label)
        assertEquals("Snake Island Gyms", visible.first { it.id == "snake_island" }.label)
        assertEquals("Pro Oak Gyms", visible.first { it.id == "pro_oak" }.label)
    }

    private fun entry(unlock: String? = null, label: String? = null): GymEntry =
        GymEntry(WarpPos(0.0, 0.0, 0.0, "minecraft:overworld", 0f, 0f), unlock, label)

    private fun checker(vararg held: String): AdvancementChecker {
        val set = held.toHashSet()
        return AdvancementChecker { set.contains(it) }
    }
}
