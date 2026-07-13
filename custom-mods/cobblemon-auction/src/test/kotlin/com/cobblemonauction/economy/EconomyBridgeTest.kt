package com.cobblemonauction.economy

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * [EconomyBridge] fails closed when NeoEssentials isn't on the classpath (as in plain JUnit): the
 * reflected `EconomyManager` class can't be resolved, so `isAvailable()` is false and both `deposit`
 * and `withdraw` refuse a real (positive) amount. This is the guarantee the request/settle recovery
 * paths rely on — a deposit that "can't be made" must report false, not silently succeed.
 */
class EconomyBridgeTest {

    @Test
    fun `deposit returns false when the economy is unavailable`() {
        assertFalse(EconomyBridge.isAvailable(), "no NeoEssentials on the test classpath")
        assertFalse(EconomyBridge.deposit(UUID.randomUUID(), 100), "unavailable economy must not report success")
    }

    @Test
    fun `withdraw returns false when the economy is unavailable`() {
        assertFalse(EconomyBridge.withdraw(UUID.randomUUID(), 100))
    }

    @Test
    fun `non-positive amounts are a no-op success`() {
        // A zero/negative transfer moves nothing, so it can't "fail" — true regardless of availability.
        assertTrue(EconomyBridge.deposit(UUID.randomUUID(), 0))
        assertTrue(EconomyBridge.withdraw(UUID.randomUUID(), 0))
    }
}
