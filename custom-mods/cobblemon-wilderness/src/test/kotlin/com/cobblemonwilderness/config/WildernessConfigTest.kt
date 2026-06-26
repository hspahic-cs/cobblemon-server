package com.cobblemonwilderness.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WildernessConfigTest {

    // A config written by a build that predated the snapshot fields — no backup* keys.
    private val preSnapshotJson = """
        {
          "enabled": true,
          "dryRun": false,
          "intervalDays": 14,
          "dimensions": ["minecraft:overworld"],
          "box": { "minX": -20480, "minZ": -20480, "maxX": 20479, "maxZ": 20479 },
          "snapToRegions": true,
          "warnPlayersOutsideBox": true,
          "displayTimeZone": "America/New_York",
          "maxDeleteFraction": 0.9
        }
    """.trimIndent()

    @Test
    fun `pre-snapshot config backfills the new fields to defaults instead of false-null`() {
        val cfg = WildernessConfig.fromJsonWithDefaults(preSnapshotJson)
        // Without the backfill, gson would leave these at false / null / 0 — snapshots silently OFF.
        assertTrue(cfg.backupBeforeReset)
        assertEquals("wilderness-snapshots", cfg.backupDir)
        assertEquals(5, cfg.backupRetention)
        // Existing fields are still honored, not reset to defaults.
        assertTrue(cfg.enabled)
        assertFalse(cfg.dryRun)
    }

    @Test
    fun `explicit snapshot settings are preserved, not overwritten by defaults`() {
        val json = preSnapshotJson.trimEnd().dropLast(1) + // strip closing brace
            ""","backupBeforeReset": false, "backupDir": "/opt/snapshots/wild", "backupRetention": 2 }"""
        val cfg = WildernessConfig.fromJsonWithDefaults(json)
        assertFalse(cfg.backupBeforeReset)
        assertEquals("/opt/snapshots/wild", cfg.backupDir)
        assertEquals(2, cfg.backupRetention)
    }
}
