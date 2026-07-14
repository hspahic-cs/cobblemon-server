package com.cobblemonwilderness.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WildernessConfigTest {

    // A config written by a build that predated the snapshot fields — no backup* keys. Also carries
    // the now-removed `intervalDays`/`idleTtlDays` keys, which must be tolerated (ignored) on load.
    private val preSnapshotJson = """
        {
          "enabled": true,
          "dryRun": false,
          "intervalDays": 14,
          "idleTtlDays": 14,
          "dimensions": ["minecraft:overworld"],
          "box": { "minX": -20480, "minZ": -20480, "maxX": 20479, "maxZ": 20479 },
          "snapToRegions": true,
          "warnPlayersOutsideBox": true,
          "displayTimeZone": "America/New_York",
          "maxDeleteFraction": 0.9
        }
    """.trimIndent()

    @Test
    fun `pre-snapshot config backfills the snapshot fields to their defaults`() {
        val cfg = WildernessConfig.fromJsonWithDefaults(preSnapshotJson)
        // Snapshots now default OFF — the pipeline owns backups; the backfill must not leave the
        // absent backupDir/retention at null/0 (they'd read as garbage), it restores the defaults.
        assertFalse(cfg.backupBeforeReset)
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
    fun `removed idle and relocation keys are tolerated on load`() {
        // Keys from the removed idle/relocation approaches must not break deserialization.
        val json = """
            { "enabled": true, "idleTtlDays": 30, "intervalDays": 7,
              "reseedStructuresOutsideBox": true, "backupDir": "wilderness-snapshots" }
        """.trimIndent()
        val cfg = WildernessConfig.fromJsonWithDefaults(json)
        assertTrue(cfg.enabled)
        assertEquals("wilderness-snapshots", cfg.backupDir)
    }

    @Test
    fun `scheduleTimeZone defaults when absent`() {
        val json = """{ "enabled": true, "backupDir": "wilderness-snapshots" }"""
        assertEquals("America/New_York", WildernessConfig.fromJsonWithDefaults(json).scheduleTimeZone)
    }
}
