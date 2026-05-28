package com.cobblemonbridge.gymtp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

class GymTpStoreTest {

    @Test
    fun `load returns empty when file is missing`(@TempDir dir: Path) {
        val store = GymTpStore.load(dir.resolve("missing.json"))
        assertEquals(emptyMap<String, GymEntry>(), store.entries())
    }

    @Test
    fun `load returns empty when file is malformed json`(@TempDir dir: Path) {
        val file = dir.resolve("bad.json")
        file.writeText("{ this is not json ::")
        val store = GymTpStore.load(file)
        assertEquals(emptyMap<String, GymEntry>(), store.entries())
    }

    @Test
    fun `set persists across reload and preserves insertion order`(@TempDir dir: Path) {
        val file = dir.resolve("gym_tps.json")
        val a = GymTpStore.load(file)
        a.set("1", entry(100.0, 64.0, 200.0))
        a.set("2", entry(150.0, 64.0, 250.0))
        a.set("rotating", entry(500.0, 70.0, 500.0, unlock = "server:beat_gym_10"))

        val b = GymTpStore.load(file)
        assertEquals(listOf("1", "2", "rotating"), b.entries().keys.toList())
        assertEquals(100.0, b.get("1")!!.position.x)
        assertEquals("server:beat_gym_10", b.get("rotating")!!.unlockAdvancement)
    }

    @Test
    fun `remove returns true only when entry existed`(@TempDir dir: Path) {
        val store = GymTpStore.load(dir.resolve("gym_tps.json"))
        store.set("1", entry(0.0, 0.0, 0.0))
        assertTrue(store.remove("1"))
        assertNull(store.get("1"))
        assertEquals(false, store.remove("1"))
    }

    @Test
    fun `save writes via temp+rename so original is never truncated`(@TempDir dir: Path) {
        val file = dir.resolve("gym_tps.json")
        val store = GymTpStore.load(file)
        store.set("1", entry(1.0, 2.0, 3.0))
        assertTrue(file.exists())
        assertNotNull(file.readText())
        // Tmp file should not linger after a successful save.
        val tmp = dir.resolve("gym_tps.json.tmp")
        assertEquals(false, tmp.exists(), "temp file should be renamed away")
    }

    @Test
    fun `set on existing id preserves order and updates value`(@TempDir dir: Path) {
        val file = dir.resolve("gym_tps.json")
        val store = GymTpStore.load(file)
        store.set("1", entry(1.0, 0.0, 0.0))
        store.set("2", entry(2.0, 0.0, 0.0))
        store.set("1", entry(99.0, 0.0, 0.0))
        assertEquals(listOf("1", "2"), store.entries().keys.toList())
        assertEquals(99.0, store.get("1")!!.position.x)
    }

    private fun entry(x: Double, y: Double, z: Double, unlock: String? = null, label: String? = null) =
        GymEntry(WarpPos(x, y, z, "minecraft:overworld", 0f, 0f), unlock, label)
}
