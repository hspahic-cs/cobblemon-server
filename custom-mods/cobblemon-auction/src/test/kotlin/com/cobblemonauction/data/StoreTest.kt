package com.cobblemonauction.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID

/**
 * Persistence + lifecycle for the two Gson stores. Deliberately avoids any Minecraft class —
 * `item` is an opaque string here (the stores never decode it), so these run in plain JUnit.
 */
class StoreTest {

    private fun listing(
        id: String, seller: UUID, price: Int = 100, createdAt: Long = 1_000L, expiresAt: Long = 10_000L,
    ) = Listing(
        id = id, sellerUuid = seller.toString(), sellerName = "Seller",
        itemId = "minecraft:diamond", count = 3, item = "{id:\"minecraft:diamond\"}",
        price = price, fee = 5, createdAt = createdAt, expiresAt = expiresAt,
    )

    @Test
    fun `listings round-trip through save and load`(@TempDir dir: Path) {
        val seller = UUID.randomUUID()
        val a = AuctionStore(dir)
        a.add(listing("l1", seller, createdAt = 1))
        a.add(listing("l2", seller, createdAt = 2))

        val reloaded = AuctionStore(dir)
        reloaded.load()
        assertEquals(2, reloaded.all().size)
        assertEquals(2, reloaded.countBySeller(seller))
        // all() is newest-first by createdAt
        assertEquals("l2", reloaded.all().first().id)
    }

    @Test
    fun `remove returns the listing once then null`(@TempDir dir: Path) {
        val a = AuctionStore(dir)
        a.add(listing("x", UUID.randomUUID()))
        assertEquals("x", a.remove("x")?.id)
        assertNull(a.remove("x"))
        assertTrue(a.all().isEmpty())
    }

    @Test
    fun `expired selects only listings at or past the cutoff`(@TempDir dir: Path) {
        val a = AuctionStore(dir)
        a.add(listing("old", UUID.randomUUID(), expiresAt = 5_000))
        a.add(listing("fresh", UUID.randomUUID(), expiresAt = 20_000))
        val expired = a.expired(now = 10_000)
        assertEquals(listOf("old"), expired.map { it.id })
    }

    @Test
    fun `bySeller filters to the owner`(@TempDir dir: Path) {
        val me = UUID.randomUUID()
        val other = UUID.randomUUID()
        val a = AuctionStore(dir)
        a.add(listing("mine", me))
        a.add(listing("theirs", other))
        assertEquals(listOf("mine"), a.bySeller(me).map { it.id })
        assertEquals(1, a.countBySeller(other))
    }

    @Test
    fun `mailbox add remove and round-trip`(@TempDir dir: Path) {
        val uuid = UUID.randomUUID()
        val m = MailboxStore(dir)
        val entry = MailEntry("e1", "minecraft:diamond", 3, "{id:\"minecraft:diamond\"}", 1L, "Purchased")
        m.add(uuid, entry)
        assertEquals(1, m.count(uuid))

        val reloaded = MailboxStore(dir)
        reloaded.load()
        assertEquals(1, reloaded.count(uuid))
        assertEquals("e1", reloaded.remove(uuid, "e1")?.id)
        assertEquals(0, reloaded.count(uuid))
        assertNull(reloaded.remove(uuid, "e1"))
    }
}
