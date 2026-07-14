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

    @Test
    fun `legacy intervalDays is honored when idleTtlDays is absent`() {
        // preSnapshotJson has intervalDays:14 and no idleTtlDays → rename migration keeps 14.
        val cfg = WildernessConfig.fromJsonWithDefaults(preSnapshotJson)
        assertEquals(14, cfg.idleTtlDays)
        // A different legacy value carries over verbatim.
        val sevenDay = preSnapshotJson.replace("\"intervalDays\": 14", "\"intervalDays\": 7")
        assertEquals(7, WildernessConfig.fromJsonWithDefaults(sevenDay).idleTtlDays)
    }

    @Test
    fun `missing idleTtlDays defaults to 14, not the Unsafe-zero`() {
        // Neither idleTtlDays nor legacy intervalDays present → declared default, not 0.
        val json = """{ "enabled": true, "backupDir": "wilderness-snapshots" }"""
        assertEquals(14, WildernessConfig.fromJsonWithDefaults(json).idleTtlDays)
    }

    @Test
    fun `an explicit idleTtlDays of zero is preserved as disabled`() {
        // 0 is a valid value (idle gate off); it must NOT be backfilled to 14.
        val json = """{ "enabled": true, "idleTtlDays": 0, "backupDir": "wilderness-snapshots" }"""
        assertEquals(0, WildernessConfig.fromJsonWithDefaults(json).idleTtlDays)
    }

    @Test
    fun `explicit idleTtlDays wins over any legacy intervalDays`() {
        val json = """{ "idleTtlDays": 30, "intervalDays": 7, "backupDir": "wilderness-snapshots" }"""
        assertEquals(30, WildernessConfig.fromJsonWithDefaults(json).idleTtlDays)
    }

    @Test
    fun `scheduleTimeZone defaults when absent`() {
        val json = """{ "enabled": true, "backupDir": "wilderness-snapshots" }"""
        assertEquals("America/New_York", WildernessConfig.fromJsonWithDefaults(json).scheduleTimeZone)
    }
}
