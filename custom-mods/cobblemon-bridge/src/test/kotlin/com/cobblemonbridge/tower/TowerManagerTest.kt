package com.cobblemonbridge.tower

import com.cobblemonbridge.tags.BridgeTags
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TowerManagerTest {

    // -----------------------------------------------------------------------------------------
    // leadersForDay must be a pure function of the epoch-day: the roll is never persisted, so
    // every caller (rotation, /tower status, a post-restart re-check) has to agree on the
    // lineup. Determinism + distinctness are the contract; day-to-day variety is sanity.
    // -----------------------------------------------------------------------------------------

    @Test
    fun `same day always yields the same three leaders`() {
        val day = 20_610L  // arbitrary epoch-day
        assertEquals(TowerManager.leadersForDay(day), TowerManager.leadersForDay(day))
    }

    @Test
    fun `pick is three distinct leaders from the pool`() {
        for (day in 0L..365L) {
            val pick = TowerManager.leadersForDay(day)
            assertEquals(3, pick.size, "day $day")
            assertEquals(3, pick.toSet().size, "day $day picked duplicates")
            assertTrue(TowerManager.POOL.containsAll(pick), "day $day picked outside the pool")
        }
    }

    @Test
    fun `lineup actually rotates across days`() {
        // Not a randomness proof — just guards against a constant-seed regression where every
        // day rolls the same trio.
        val lineups = (0L..30L).map { TowerManager.leadersForDay(it) }.toSet()
        assertTrue(lineups.size > 1, "30 consecutive days produced a single lineup")
    }

    @Test
    fun `pool is the 18 challenge leaders with unique ids`() {
        assertEquals(18, TowerManager.POOL.size)
        assertEquals(18, TowerManager.POOL.map { it.first }.toSet().size)
        assertTrue(TowerManager.POOL.all { it.first.startsWith("gym_") && it.first.endsWith("_challenge") })
    }

    // -----------------------------------------------------------------------------------------
    // Tower floor tag parsing (BridgeTags) — same shape as the other dot-payload tags.
    // -----------------------------------------------------------------------------------------

    @Test
    fun `tower floor tag parses 1 through 3 and rejects the rest`() {
        assertEquals(1, BridgeTags.parseTowerFloor("cobblemon_bridge.tower_floor.1"))
        assertEquals(3, BridgeTags.parseTowerFloor("cobblemon_bridge.tower_floor.3"))
        assertNull(BridgeTags.parseTowerFloor("cobblemon_bridge.tower_floor.4"))
        assertNull(BridgeTags.parseTowerFloor("cobblemon_bridge.tower_floor.0"))
        assertNull(BridgeTags.parseTowerFloor("cobblemon_bridge.tower_floor"))
        assertNull(BridgeTags.parseTowerFloor("cobblemon_bridge.tower"))
        assertNull(BridgeTags.parseTowerFloor("other.tower_floor.1"))
    }

    @Test
    fun `findTowerFloor picks the floor tag out of a mixed tag set`() {
        val tags = listOf("cobblemon_bridge.tower", "cobblemon_bridge.anchor.tower",
            "cobblemon_bridge.adjust_level.50", "cobblemon_bridge.tower_floor.2")
        assertEquals(2, BridgeTags.findTowerFloor(tags))
        assertTrue(BridgeTags.isTower(tags))
    }
}
