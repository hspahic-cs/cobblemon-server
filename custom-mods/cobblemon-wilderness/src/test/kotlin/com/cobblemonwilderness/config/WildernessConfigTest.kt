package com.cobblemonwilderness.config

import com.cobblemonwilderness.internal.ConfigPaths
import com.cobblemonwilderness.reset.RegionResetter
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText
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

    @Test
    fun `pre-W3 config without minKeepBoxSideBlocks backfills the floor and re-arms the breaker`() {
        // preSnapshotJson predates minKeepBoxSideBlocks; the backfill must land the default (1024) so
        // the breaker fires on a collapsed box and passes a sane one (anchor at origin, inside cfg.box).
        val cfg = WildernessConfig.fromJsonWithDefaults(preSnapshotJson)
        assertEquals(1024, cfg.minKeepBoxSideBlocks)
        assertTrue(RegionResetter.isBoxDegenerate(BoundingBox(0, 0, 0, 0), cfg.minKeepBoxSideBlocks, cfg.mustContainX, cfg.mustContainZ))
        assertFalse(RegionResetter.isBoxDegenerate(cfg.box, cfg.minKeepBoxSideBlocks, cfg.mustContainX, cfg.mustContainZ))
    }

    @Test
    fun `a non-positive minKeepBoxSideBlocks is treated as absent and restored to the default`() {
        // A written 0/negative would silently disable the breaker → always coerced back to the default.
        assertEquals(1024, WildernessConfig.fromJsonWithDefaults("""{ "minKeepBoxSideBlocks": 0, "backupDir": "x" }""").minKeepBoxSideBlocks)
        assertEquals(1024, WildernessConfig.fromJsonWithDefaults("""{ "minKeepBoxSideBlocks": -5, "backupDir": "x" }""").minKeepBoxSideBlocks)
    }

    @Test
    fun `an explicit positive minKeepBoxSideBlocks is preserved`() {
        assertEquals(2048, WildernessConfig.fromJsonWithDefaults("""{ "minKeepBoxSideBlocks": 2048, "backupDir": "x" }""").minKeepBoxSideBlocks)
    }

    @Test
    fun `load rewrites a pre-W-schema config to the current schema on disk`() {
        val dir = Files.createTempDirectory("wild-cfg")
        try {
            val file = ConfigPaths.authored(dir, "config.json")
            file.parent.createDirectories()
            // A pre-W config: no minKeepBoxSideBlocks, and stale relocation/idle-era keys present.
            file.writeText(
                """
                {
                  "enabled": true,
                  "dryRun": false,
                  "intervalDays": 14,
                  "idleTtlDays": 14,
                  "maxDeleteFraction": 0.9,
                  "reseedStructuresOutsideBox": true,
                  "dimensions": ["minecraft:overworld"],
                  "box": { "minX": -20480, "minZ": -20480, "maxX": 20479, "maxZ": 20479 },
                  "backupDir": "wilderness-snapshots"
                }
                """.trimIndent()
            )

            val cfg = WildernessConfig.load(dir)
            // Runtime value is the backfilled default.
            assertEquals(1024, cfg.minKeepBoxSideBlocks)

            // The file was rewritten to the current schema: current field present, legacy keys dropped.
            val persisted = file.readText()
            assertTrue(persisted.contains("minKeepBoxSideBlocks"), "expected minKeepBoxSideBlocks in: $persisted")
            for (legacy in listOf("maxDeleteFraction", "idleTtlDays", "intervalDays", "reseedStructuresOutsideBox")) {
                assertFalse(persisted.contains(legacy), "legacy key '$legacy' should have been dropped: $persisted")
            }
            // Loading the rewritten file again is a no-op (already in schema) and preserves values.
            val reloaded = WildernessConfig.load(dir)
            assertEquals(1024, reloaded.minKeepBoxSideBlocks)
            assertTrue(reloaded.enabled)
            assertFalse(reloaded.dryRun)
            assertEquals(-20480, reloaded.box.minX)
            assertEquals(20479, reloaded.box.maxX)
        } finally {
            dir.toFile().deleteRecursively()
        }
    }
}
