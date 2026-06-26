package com.cobblemonwilderness.gen

import kotlin.test.Test
import kotlin.test.assertEquals

class WildernessGenStateTest {

    private val salt = 0x5A5A5A
    private val spacing = 10 // chunks → 160-block cells

    // Box matching the default keep-zone: blocks [-20480 .. 20479] on each axis.
    private fun configureDefault() =
        WildernessGenState.configure(true, salt, -20480, -20480, 20479, 20479)

    @Test
    fun `cell inside the box is left vanilla`() {
        configureDefault()
        // cell (0,0) = chunks [0..9] = blocks [0..159], squarely inside.
        assertEquals(0, WildernessGenState.cellSalt(0, 0, spacing))
    }

    @Test
    fun `cell wholly outside the box gets the cycle salt`() {
        configureDefault()
        // cell (200,200) = blocks [32000..32159], far outside.
        assertEquals(salt, WildernessGenState.cellSalt(200, 200, spacing))
        // outside on one axis only is still outside (doesn't intersect the box).
        assertEquals(salt, WildernessGenState.cellSalt(0, 200, spacing))
    }

    @Test
    fun `cell straddling the boundary is kept vanilla`() {
        configureDefault()
        // chunk 1279 (blocks 20464..20479) is the last inside +X; cell 127 = chunks [1270..1279]
        // touches the box → must stay put.
        assertEquals(0, WildernessGenState.cellSalt(127, 0, spacing))
        // cell 128 = chunks [1280..1289] = blocks [20480..] is wholly past the edge → relocate.
        assertEquals(salt, WildernessGenState.cellSalt(128, 0, spacing))
    }

    @Test
    fun `disabled or zero salt never relocates`() {
        WildernessGenState.disable()
        assertEquals(0, WildernessGenState.cellSalt(200, 200, spacing))
        // salt 0 is the "no prune yet" sentinel → inactive even if enabled.
        WildernessGenState.configure(true, 0, -20480, -20480, 20479, 20479)
        assertEquals(0, WildernessGenState.cellSalt(200, 200, spacing))
    }

    @Test
    fun `non-positive spacing is ignored`() {
        configureDefault()
        assertEquals(0, WildernessGenState.cellSalt(200, 200, 0))
    }
}
