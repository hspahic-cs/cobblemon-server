package com.cobblemonranked.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * Verifies the round-trip used by the new `/ranked admin setarena 1|2` and `clearpos` commands:
 * mutate the in-memory [RankedConfig] via `copy()`, save through [RankedConfig.save], and
 * reload — the new position must survive verbatim.
 *
 * The command handlers themselves take a CommandSourceStack and can't be exercised here without
 * a Minecraft runtime; this test covers the persistence contract they rely on.
 */
class RankedArenaConfigTest {

    @Test
    fun `freshly-loaded config has both arena slots null`(@TempDir dir: Path) {
        val cfg = RankedConfig.load(dir)
        assertNull(cfg.arenaPos1)
        assertNull(cfg.arenaPos2)
        assertFalse(cfg.isArenaConfigured())
    }

    @Test
    fun `setarena 1 persists across reload`(@TempDir dir: Path) {
        RankedConfig.load(dir)  // creates the default file
        val arena = ArenaPos(10.5, 64.0, -7.5, "minecraft:the_nether", 90f, -10f)
        val updated = RankedConfig.load(dir).copy(arenaPos1 = arena)
        RankedConfig.save(dir, updated)

        val reloaded = RankedConfig.load(dir)
        assertEquals(arena, reloaded.arenaPos1)
        assertNull(reloaded.arenaPos2)
        assertFalse(reloaded.isArenaConfigured(), "needs both to be configured")
    }

    @Test
    fun `setting both slots flips isArenaConfigured to true`(@TempDir dir: Path) {
        val a1 = ArenaPos(0.0, 64.0, 0.0)
        val a2 = ArenaPos(20.0, 64.0, 20.0)
        var cfg = RankedConfig.load(dir)
        cfg = cfg.copy(arenaPos1 = a1)
        RankedConfig.save(dir, cfg)
        cfg = RankedConfig.load(dir).copy(arenaPos2 = a2)
        RankedConfig.save(dir, cfg)

        val reloaded = RankedConfig.load(dir)
        assertNotNull(reloaded.arenaPos1)
        assertNotNull(reloaded.arenaPos2)
        assertTrue(reloaded.isArenaConfigured())
    }

    @Test
    fun `clearpos 1 returns slot to null without disturbing slot 2`(@TempDir dir: Path) {
        val a2 = ArenaPos(50.0, 70.0, 50.0, "minecraft:overworld", 180f, 0f)
        var cfg = RankedConfig.load(dir).copy(
            arenaPos1 = ArenaPos(1.0, 1.0, 1.0),
            arenaPos2 = a2,
        )
        RankedConfig.save(dir, cfg)

        cfg = RankedConfig.load(dir).copy(arenaPos1 = null)
        RankedConfig.save(dir, cfg)

        val reloaded = RankedConfig.load(dir)
        assertNull(reloaded.arenaPos1)
        assertEquals(a2, reloaded.arenaPos2)
    }
}
