package com.cobblemonranked.bp

import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BpManagerTest {

    @Test
    fun getBalance_returnsZeroForNewPlayer() {
        val uuid = UUID.randomUUID()
        assertEquals(0, BpManager.getBalance(uuid))
    }

    @Test
    fun setBalance_setsAndRetrieves() {
        val uuid = UUID.randomUUID()
        BpManager.setBalance(uuid, 100)
        assertEquals(100, BpManager.getBalance(uuid))
    }

    @Test
    fun addBalance_increments() {
        val uuid = UUID.randomUUID()
        BpManager.setBalance(uuid, 50)
        val result = BpManager.addBalance(uuid, 25)
        assertEquals(75, result)
        assertEquals(75, BpManager.getBalance(uuid))
    }

    @Test
    fun subtractBalance_succeeds_whenSufficientBalance() {
        val uuid = UUID.randomUUID()
        BpManager.setBalance(uuid, 100)
        assertTrue(BpManager.subtractBalance(uuid, 30))
        assertEquals(70, BpManager.getBalance(uuid))
    }

    @Test
    fun subtractBalance_fails_whenInsufficientBalance() {
        val uuid = UUID.randomUUID()
        BpManager.setBalance(uuid, 50)
        assertFalse(BpManager.subtractBalance(uuid, 100))
        assertEquals(50, BpManager.getBalance(uuid))
    }
}
