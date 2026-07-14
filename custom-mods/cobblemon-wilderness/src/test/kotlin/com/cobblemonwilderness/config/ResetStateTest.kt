package com.cobblemonwilderness.config

import com.cobblemonwilderness.gen.WildernessGenState
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
    fun `resetGeneration round-trips through save and load`() {
        val dir = tempDir()
        try {
            val s = ResetState.load(dir)
            s.forceNextBoot = true
            s.forceBreakerOverride = true
            s.lastResetEpochMillis["minecraft:overworld"] = 999L
            s.bumpGeneration(WildernessGenState.regionKey(5, 3))
            s.bumpGeneration(WildernessGenState.regionKey(5, 3))     // → 2
            s.bumpGeneration(WildernessGenState.regionKey(-1, -2))   // → 1
            s.save()

            val r = ResetState.load(dir)
            assertEquals(2, r.generationOf(WildernessGenState.regionKey(5, 3)))
            assertEquals(1, r.generationOf(WildernessGenState.regionKey(-1, -2)))
            assertEquals(0, r.generationOf(WildernessGenState.regionKey(9, 9))) // absent → 0
            assertTrue(r.hasAnyGeneration())
            assertTrue(r.forceNextBoot)
            assertTrue(r.forceBreakerOverride)
            assertEquals(999L, r.lastResetEpochMillis["minecraft:overworld"])
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `resetGeneration serializes as an array of number pairs, not a string-keyed map`() {
        val dir = tempDir()
        try {
            val s = ResetState.load(dir)
            s.bumpGeneration(WildernessGenState.regionKey(5, 3)) // key = (5<<32)|3 = 21474836483
            s.save()
            val json = ConfigPaths.runtime(dir, "state.json").readText()
            assertTrue(json.contains("resetGeneration"))
            // The packed long key appears as a NUMBER (array element), not a quoted object key.
            assertTrue(json.contains("21474836483"), "expected packed key as a number in: $json")
            assertFalse(json.contains("\"21474836483\""), "long key must not be stringified")
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `legacy state with structureSalt and no resetGeneration migrates to empty generations`() {
        val dir = tempDir()
        try {
            val file = ConfigPaths.runtime(dir, "state.json")
            file.parent.createDirectories()
            // A state.json written before T3: carries the dropped `structureSalt`, no `resetGeneration`.
            file.writeText(
                """
                {
                  "lastResetEpochMillis": { "minecraft:overworld": 123456789 },
                  "forceNextBoot": false,
                  "structureSalt": 7
                }
                """.trimIndent()
            )

            val r = ResetState.load(dir)
            assertFalse(r.hasAnyGeneration())
            assertEquals(0, r.generationOf(WildernessGenState.regionKey(0, 0)))
            assertEquals(123456789L, r.lastResetEpochMillis["minecraft:overworld"])
            // A subsequent bump + save works from the migrated (empty) baseline.
            r.bumpGeneration(WildernessGenState.regionKey(1, 1))
            r.save()
            assertEquals(1, ResetState.load(dir).generationOf(WildernessGenState.regionKey(1, 1)))
        } finally {
            dir.toFile().deleteRecursively()
        }
    }
}
