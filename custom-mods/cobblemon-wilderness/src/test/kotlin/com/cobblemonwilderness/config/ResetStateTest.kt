package com.cobblemonwilderness.config

import com.cobblemonwilderness.internal.ConfigPaths
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ResetStateTest {

    private fun tempDir(): Path = Files.createTempDirectory("wild-state")

    @Test
    fun `flags and last-reset round-trip through save and load`() {
        val dir = tempDir()
        try {
            val s = ResetState.load(dir)
            s.forceNextBoot = true
            s.forceBreakerOverride = true
            s.lastResetEpochMillis["minecraft:overworld"] = 999L
            s.save()

            val r = ResetState.load(dir)
            assertTrue(r.forceNextBoot)
            assertTrue(r.forceBreakerOverride)
            assertEquals(999L, r.lastResetEpochMillis["minecraft:overworld"])
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `legacy state with generation fields loads cleanly, dropping them`() {
        val dir = tempDir()
        try {
            val file = ConfigPaths.runtime(dir, "state.json")
            file.parent.createDirectories()
            // A state.json written by the removed relocation approach: carries `structureSalt` and
            // `resetGeneration`, both now unknown → Gson ignores them, the rest loads.
            file.writeText(
                """
                {
                  "lastResetEpochMillis": { "minecraft:overworld": 123456789 },
                  "forceNextBoot": true,
                  "structureSalt": 7,
                  "resetGeneration": [[21474836483, 2], [-4294967294, 1]]
                }
                """.trimIndent()
            )

            val r = ResetState.load(dir)
            assertEquals(123456789L, r.lastResetEpochMillis["minecraft:overworld"])
            assertTrue(r.forceNextBoot)
            assertFalse(r.forceBreakerOverride) // absent → default
            // Re-saving writes only the current fields; the legacy keys do not come back.
            r.save()
            assertFalse(file.readText().contains("resetGeneration"))
            assertFalse(file.readText().contains("structureSalt"))
        } finally {
            dir.toFile().deleteRecursively()
        }
    }
}
