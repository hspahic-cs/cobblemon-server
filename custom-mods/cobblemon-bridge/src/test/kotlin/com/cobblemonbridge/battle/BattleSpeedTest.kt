package com.cobblemonbridge.battle

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BattleSpeedTest {

    /** BattleSpeed is a singleton; leave it at stock so test order can't matter. */
    @AfterEach
    fun reset() {
        BattleSpeed.set(1.0f)
    }

    @Test
    fun `stock multiplier leaves delays untouched`() {
        BattleSpeed.set(1.0f)
        assertEquals(1.5f, BattleSpeed.scale(1.5f))
        assertEquals(0.35f, BattleSpeed.scale(0.35f))
    }

    @Test
    fun `faster multiplier divides the delay`() {
        BattleSpeed.set(2.0f)
        assertEquals(0.75f, BattleSpeed.scale(1.5f))
        assertEquals(0.25f, BattleSpeed.scale(0.5f))
    }

    @Test
    fun `slower multiplier lengthens the delay`() {
        BattleSpeed.set(0.5f)
        assertEquals(3.0f, BattleSpeed.scale(1.5f))
    }

    @Test
    fun `scaling never collapses a delay below the floor`() {
        BattleSpeed.set(BattleSpeed.MAX_MULTIPLIER)
        assertTrue(BattleSpeed.scale(0.15f) >= 0.05f, "short delays must keep a nonzero gap")
    }

    @Test
    fun `delays already below the floor are not lengthened to it`() {
        BattleSpeed.set(BattleSpeed.MAX_MULTIPLIER)
        assertEquals(0.01f, BattleSpeed.scale(0.01f))
    }

    @Test
    fun `zero delay stays zero at any multiplier`() {
        BattleSpeed.set(3.0f)
        assertEquals(0.0f, BattleSpeed.scale(0.0f))
    }

    @Test
    fun `out-of-range multipliers clamp instead of being rejected`() {
        assertEquals(BattleSpeed.MAX_MULTIPLIER, BattleSpeed.set(100f))
        assertEquals(BattleSpeed.MIN_MULTIPLIER, BattleSpeed.set(0f))
    }
}
