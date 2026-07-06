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
    fun `blocklist is empty by default so ordinary items list freely`() {
        val cfg = AuctionConfig()
        // Empty Poké Balls are ordinary tradeable items; nothing is blocked out of the box.
        assertFalse(cfg.isBlocked("cobblemon:poke_ball"))
        assertFalse(cfg.isBlocked("minecraft:diamond"))
    }

    @Test
    fun `blocklist matches items an operator adds`() {
        val cfg = AuctionConfig(blocklist = listOf("cobblemon:master_ball"))
        assertTrue(cfg.isBlocked("cobblemon:master_ball"))
        assertFalse(cfg.isBlocked("cobblemon:poke_ball"))
    }

    @Test
    fun `ttl floors at one day even if misconfigured to zero`() {
        assertEquals(24L * 60 * 60 * 1000, AuctionConfig(listingTtlDays = 0).ttlMillis())
    }

    @Test
    fun `listing fee is percent of price, floored at the minimum, capped at price`() {
        val cfg = AuctionConfig(listingFeePercent = 5.0, minListingFee = 1)
        assertEquals(50, cfg.listingFee(1000))                 // 5% of 1000
        assertEquals(5, cfg.listingFee(100))                   // 5% of 100
        assertEquals(1, cfg.listingFee(3))                     // ceil(0.15)=1, meets the min
        // fee can never exceed the asking price itself
        assertEquals(1, AuctionConfig(listingFeePercent = 0.0, minListingFee = 5).listingFee(1))
        // both knobs zero → fees disabled
        assertEquals(0, AuctionConfig(listingFeePercent = 0.0, minListingFee = 0).listingFee(1000))
    }

    @Test
    fun `timeLeft formats day hour and minute buckets`() {
        assertEquals("under a minute", Gui.timeLeft(0))
        assertEquals("under a minute", Gui.timeLeft(59_000L))
        assertEquals("42m", Gui.timeLeft(42 * 60_000L))
        assertEquals("5h 12m", Gui.timeLeft((5 * 60 + 12) * 60_000L))
        assertEquals("3d 4h", Gui.timeLeft((3 * 1440 + 4 * 60) * 60_000L))
    }
}
