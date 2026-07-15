package com.cobblemonauction.config

import com.cobblemonauction.gui.Gui
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.writeText

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
    fun `request knobs default and reload`(@TempDir dir: Path) {
        val first = AuctionConfig.load(dir)
        assertEquals(7, first.requestTtlDays)
        assertEquals(10, first.maxRequestsPerPlayer)
        assertEquals(7L * 24 * 60 * 60 * 1000, first.requestTtlMillis())

        val second = AuctionConfig.load(dir)
        assertEquals(first.requestTtlDays, second.requestTtlDays)
        assertEquals(first.maxRequestsPerPlayer, second.maxRequestsPerPlayer)
    }

    @Test
    fun `load seeds a non-empty suggested list on first boot`(@TempDir dir: Path) {
        val cfg = AuctionConfig.load(dir)
        assertTrue(cfg.requestable.isNotEmpty(), "expected a seeded suggested list")
        assertTrue(cfg.isSuggested("cobblemon:master_ball"))
        assertTrue(dir.resolve("cobblemon-auction/authored/requestable-items.json").exists())
    }

    @Test
    fun `whitelist parses categories and suggested prices from the file`(@TempDir dir: Path) {
        writeRequestable(dir, """
            {
              "cobblemon:fire_stone": { "category": "Stones", "suggestedPrice": 3000 },
              "cobblemon:master_ball": { "category": "Rare" }
            }
        """.trimIndent())
        val map = AuctionConfig.loadRequestable(dir, blocklist = emptyList())
        assertEquals(2, map.size)
        assertEquals("Stones", map["cobblemon:fire_stone"]?.category)
        assertEquals(3000, map["cobblemon:fire_stone"]?.suggestedPrice)
        assertEquals("Rare", map["cobblemon:master_ball"]?.category)
        assertNull(map["cobblemon:master_ball"]?.suggestedPrice)   // optional → absent
    }

    @Test
    fun `blocklist wins - an id in both is dropped from requestable`(@TempDir dir: Path) {
        writeRequestable(dir, """
            {
              "cobblemon:fire_stone": { "category": "Stones" },
              "cobblemon:master_ball": { "category": "Rare" }
            }
        """.trimIndent())
        val map = AuctionConfig.loadRequestable(dir, blocklist = listOf("cobblemon:master_ball"))
        assertTrue(map.containsKey("cobblemon:fire_stone"))
        assertFalse(map.containsKey("cobblemon:master_ball"), "blocklisted id must be dropped from requestable")
    }

    private fun writeRequestable(dir: Path, json: String) {
        val file = dir.resolve("cobblemon-auction/authored/requestable-items.json")
        file.parent.createDirectories()
        file.writeText(json)
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
