package com.cobblemonauction.config

import com.cobblemonauction.gui.Gui
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.exists

class AuctionConfigTest {

    @Test
    fun `load writes defaults on first boot then reads them back`(@TempDir dir: Path) {
        val first = AuctionConfig.load(dir)
        assertEquals(7, first.listingTtlDays)
        assertTrue(dir.resolve("cobblemon-auction/authored/config.json").exists())

        val second = AuctionConfig.load(dir)   // now reads the file it just wrote
        assertEquals(first.listingTtlDays, second.listingTtlDays)
        assertEquals(first.maxListingsPerPlayer, second.maxListingsPerPlayer)
    }

    @Test
    fun `blocklist matches default poke balls but not other items`() {
        val cfg = AuctionConfig()
        assertTrue(cfg.isBlocked("cobblemon:poke_ball"))
        assertTrue(cfg.isBlocked("cobblemon:master_ball"))
        assertFalse(cfg.isBlocked("minecraft:diamond"))
    }

    @Test
    fun `ttl floors at one day even if misconfigured to zero`() {
        assertEquals(24L * 60 * 60 * 1000, AuctionConfig(listingTtlDays = 0).ttlMillis())
    }

    @Test
    fun `timeLeft formats day hour and minute buckets`() {
        assertEquals("expiring", Gui.timeLeft(0))
        assertEquals("42m", Gui.timeLeft(42 * 60_000L))
        assertEquals("5h 12m", Gui.timeLeft((5 * 60 + 12) * 60_000L))
        assertEquals("3d 4h", Gui.timeLeft((3 * 1440 + 4 * 60) * 60_000L))
    }
}
