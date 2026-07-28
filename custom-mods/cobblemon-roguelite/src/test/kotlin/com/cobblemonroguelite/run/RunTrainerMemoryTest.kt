package com.cobblemonroguelite.run

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.resources.ResourceLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The window itself, separately from what selection does with it.
 *
 * The properties under test are the ones a run's replayability rests on rather than the ones a player
 * would describe: that asking a wave twice gives one answer, and that eviction cannot change the
 * answer for a wave already being looked at. Both are silent when wrong — the run simply meets a
 * different trainer than the checkpoint said it would, and nothing anywhere reports a mismatch.
 */
class RunTrainerMemoryTest {

    private fun id(path: String) = ResourceLocation.fromNamespaceAndPath("test", path)

    private fun memoryOf(waves: IntRange): RunTrainerMemory =
        RunTrainerMemory().apply { waves.forEach { record(it, id("t_$it")) } }

    @Test
    fun `before hands back the most recent first`() {
        val memory = memoryOf(1..3)
        assertEquals(listOf(id("t_3"), id("t_2"), id("t_1")), memory.before(4))
    }

    @Test
    fun `before excludes the wave being planned and anything after it`() {
        // A wave that avoided itself would draw differently the second time it was looked at, which
        // is every re-plan: `/roguelite status`, and §2.10 handing an interrupted wave back.
        val memory = memoryOf(1..5)
        assertEquals(listOf(id("t_2"), id("t_1")), memory.before(3))
    }

    @Test
    fun `before never returns more than the window`() {
        val memory = memoryOf(1..40)
        assertEquals(RunTrainerMemory.WINDOW, memory.before(41).size)
    }

    @Test
    fun `recording the current wave does not change what that wave sees`() {
        // The reason capacity is one greater than the window. If eviction could shrink the set a wave
        // is looking at, the pick made when the wave started and the pick re-derived when it was won
        // would disagree — and the second one is what gets written into the memory.
        val memory = memoryOf(1..40)
        val before = memory.before(41)
        memory.record(41, id("t_41"))
        assertEquals(before, memory.before(41))
    }

    @Test
    fun `recording a wave twice is the same as recording it once`() {
        val once = memoryOf(1..10).apply { record(11, id("x")) }
        val twice = memoryOf(1..10).apply { record(11, id("x")); record(11, id("x")) }
        assertEquals(once, twice)
        assertEquals(once.before(12), twice.before(12))
    }

    @Test
    fun `the stored history stays bounded across a whole run`() {
        // 200 waves must not grow the checkpoint. This is the only reason the window has an upper
        // bound at all in the file, as opposed to in the balance.
        val memory = RunTrainerMemory()
        (1..200).filter { it % 5 == 0 }.forEach { memory.record(it, id("t_$it")) }
        assertTrue(memory.entries().size <= RunTrainerMemory.WINDOW + 1, "history grew to ${memory.entries().size}")
    }

    @Test
    fun `a checkpoint round-trip preserves the history`() {
        val memory = memoryOf(1..6)
        assertEquals(memory, RunTrainerMemory.fromNbt(memory.toNbt()))
    }

    @Test
    fun `an unreadable entry costs an avoided repeat and not the run`() {
        // Deliberately the opposite failure direction to RunState.fromNbt's schema check: this field
        // is the least important in the file and must never be the reason a party is discarded.
        val list = ListTag().apply {
            add(CompoundTag().apply { putInt("wave", 5); putString("trainer", "test:kept") })
            add(CompoundTag().apply { putInt("wave", 10); putString("trainer", "not a valid id") })
            add(CompoundTag().apply { putInt("wave", 0); putString("trainer", "test:bad_wave") })
        }
        assertEquals(listOf(id("kept")), RunTrainerMemory.fromNbt(list).before(20))
    }
}
