package com.cobblemonroguelite.run

import net.minecraft.core.RegistryAccess
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The per-wave between-wave state, and the one thing it must never do: survive a wave advance.
 *
 * Both failures here are silent. A run that advanced with [RunState.rewardTakenThisWave] still set
 * arrives at the next wave with its free reward already spent; one that kept [RunState.rerollsThisWave]
 * arrives with rerolls already priced up. The player just finds the step missing or expensive, with
 * nothing to report.
 */
class RunStateBetweenWaveTest {

    private fun run() = RunState(seed = 1L).apply {
        rerollsThisWave = 3
        rewardTakenThisWave = true
    }

    @Test
    fun `advancing resets the reroll count and the taken flag`() {
        val state = run().apply { advanceTo(12) }
        assertEquals(12, state.wave)
        assertEquals(0, state.rerollsThisWave)
        assertFalse(state.rewardTakenThisWave)
    }

    @Test
    fun `advancing to the same wave still resets, because the step was completed`() {
        // Not a no-op: the only caller that lands here is the roster-missing path, which advances a run
        // whose wave it could not compose. The step for that wave is over either way.
        val state = run().apply { advanceTo(wave) }
        assertEquals(0, state.rerollsThisWave)
        assertFalse(state.rewardTakenThisWave)
    }

    @Test
    fun `per-wave state is independent of credits, which are run-scoped`() {
        // Credits deliberately survive a wave advance; the step state deliberately does not. If these
        // are ever reset together, a player loses their balance every wave.
        val state = RunState(seed = 1L).apply {
            credits = 500
            rerollsThisWave = 2
            advanceTo(5)
        }
        assertEquals(500, state.credits)
        assertEquals(0, state.rerollsThisWave)
    }

    @Test
    fun `a fresh run has not taken its reward and has not rerolled`() {
        val state = RunState(seed = 1L)
        assertEquals(0, state.rerollsThisWave)
        assertFalse(state.rewardTakenThisWave)
        assertTrue(state.wave >= 1)
    }

    @Test
    fun `the between-wave step survives a save and reload, which is the 2_16 promise`() {
        // Without the reroll count on disk, the offer recomputes to the FIRST three items and a relog
        // silently undoes a paid reroll. Without the taken flag, a relog hands out the free reward
        // again. Both are exploits reachable by pressing the quit button.
        val tag = RunState(wave = 7, seed = 3L).apply {
            rerollsThisWave = 2
            rewardTakenThisWave = true
        }.toNbt(RegistryAccess.EMPTY)
        assertEquals(2, tag.getInt("rerollsThisWave"))
        assertTrue(tag.getBoolean("rewardTakenThisWave"))
    }

    @Test
    fun `an older save with no step keys reads back as a step not yet started`() {
        // The safe direction: defaulting `rewardTaken` to true would silently eat a reward the player
        // had not taken, whereas defaulting it to false at worst hands back one reroll's value.
        val tag = RunState(wave = 7, seed = 3L).toNbt(RegistryAccess.EMPTY)
        tag.remove("rerollsThisWave")
        tag.remove("rewardTakenThisWave")
        assertEquals(0, tag.getInt("rerollsThisWave"))
        assertFalse(tag.getBoolean("rewardTakenThisWave"))
    }
}
