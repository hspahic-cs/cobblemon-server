package com.cobblemonauction.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID

/**
 * Persistence + lifecycle for [RequestStore], the request-side twin of [AuctionStore]. Deliberately
 * avoids any Minecraft class — a [Request] carries only an item id + count (no serialized stack), so
 * these run in plain JUnit.
 */
class RequestStoreTest {

    private fun request(
        id: String, requester: UUID, price: Int = 100, createdAt: Long = 1_000L, expiresAt: Long = 10_000L,
    ) = Request(
        id = id, requesterUuid = requester.toString(), requesterName = "Requester",
        itemId = "cobblemon:master_ball", count = 2,
        price = price, createdAt = createdAt, expiresAt = expiresAt,
    )

    @Test
    fun `requests round-trip through save and load`(@TempDir dir: Path) {
        val requester = UUID.randomUUID()
        val a = RequestStore(dir)
        a.add(request("r1", requester, createdAt = 1))
        a.add(request("r2", requester, createdAt = 2))

        val reloaded = RequestStore(dir)
        reloaded.load()
        assertEquals(2, reloaded.all().size)
        assertEquals(2, reloaded.countByRequester(requester))
        // all() is newest-first by createdAt
        assertEquals("r2", reloaded.all().first().id)
    }

    @Test
    fun `remove returns the request once then null`(@TempDir dir: Path) {
        val a = RequestStore(dir)
        a.add(request("x", UUID.randomUUID()))
        assertEquals("x", a.remove("x")?.id)
        assertNull(a.remove("x"))
        assertTrue(a.all().isEmpty())
    }

    @Test
    fun `expired selects only requests at or past the cutoff`(@TempDir dir: Path) {
        val a = RequestStore(dir)
        a.add(request("old", UUID.randomUUID(), expiresAt = 5_000))
        a.add(request("fresh", UUID.randomUUID(), expiresAt = 20_000))
        val expired = a.expired(now = 10_000)
        assertEquals(listOf("old"), expired.map { it.id })
    }

    @Test
    fun `byRequester and countByRequester filter to the owner`(@TempDir dir: Path) {
        val me = UUID.randomUUID()
        val other = UUID.randomUUID()
        val a = RequestStore(dir)
        a.add(request("mine", me))
        a.add(request("theirs", other))
        assertEquals(listOf("mine"), a.byRequester(me).map { it.id })
        assertEquals(1, a.countByRequester(me))
        assertEquals(1, a.countByRequester(other))
    }

    @Test
    fun `request receipts add clear and round-trip`(@TempDir dir: Path) {
        val uuid = UUID.randomUUID()
        val store = RequestReceiptStore(dir)
        store.add(uuid, RequestReceipt("id1", "cobblemon:master_ball", 2, 5000, "Seller", 1L))
        assertEquals(1, store.pending(uuid).size)

        val reloaded = RequestReceiptStore(dir)
        reloaded.load()
        assertEquals(1, reloaded.pending(uuid).size)
        reloaded.clear(uuid)
        assertTrue(reloaded.pending(uuid).isEmpty())
    }
}
