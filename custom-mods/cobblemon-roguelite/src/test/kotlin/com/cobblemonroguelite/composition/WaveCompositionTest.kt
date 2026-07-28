package com.cobblemonroguelite.composition

import com.cobblemonroguelite.integration.RunOpponent
import net.minecraft.resources.ResourceLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun id(path: String) = ResourceLocation.fromNamespaceAndPath("roguelite_test", path)

/**
 * The schedule, and the one property the schedule must have: it is the same for everybody.
 *
 * Reward table ids and interval values are config, so nothing here asserts a *particular* table or a
 * particular pacing — it asserts that what the config says is what comes out, and that the seed
 * cannot reach the schedule.
 */
class WaveCompositionTest {

    private val composition = WaveComposition()

    @Test
    fun `the schedule ignores the seed entirely`() {
        // The reason this matters: players compare progress by wave number and boss rosters are
        // authored against fixed wave indices (§2.7). A seed-dependent schedule would make "wave 190"
        // mean something different in every run.
        val reference = (1..200).map { composition.kindOf(it) }
        for (seed in listOf(0L, 1L, -1L, 4242L, Long.MAX_VALUE, Long.MIN_VALUE)) {
            val kinds = (1..200).map { composition.planFor(it, seed).kind }
            assertEquals(reference, kinds, "wave kinds moved with seed $seed")
        }
    }

    @Test
    fun `two instances of the same config compose identically`() {
        // Guards against anyone giving this class per-instance state later; a run rebuilt after a
        // restart has to compose the same waves the pre-restart instance did.
        val other = WaveComposition(WaveCompositionConfig())
        assertEquals(
            (1..200).map { composition.kindOf(it) },
            (1..200).map { other.kindOf(it) },
        )
    }

    @Test
    fun `bosses land on the boss interval and beat trainers where they collide`() {
        val bosses = (1..200).filter { composition.kindOf(it) == RunOpponent.BOSS }
        assertEquals((10..200 step 10).toList(), bosses)
    }

    @Test
    fun `trainers land on the trainer interval except where a boss took the slot`() {
        val trainers = (1..200).filter { composition.kindOf(it) == RunOpponent.TRAINER }
        assertEquals((5..200 step 5).filter { it % 10 != 0 }, trainers)
    }

    @Test
    fun `everything else is wild, and wild waves are the bulk of a run`() {
        // §2.14's whole point: catching is the party system, so if most waves were trainers the party
        // could not grow.
        // 20/20/160, not the 40 trainers + 20 bosses §2.19 quotes: 40 is `200/5`, which already
        // includes the 20 waves the boss interval takes over. The roster requirement the plan derives
        // from that number is therefore 40 authored things, not 60.
        val wild = composition.waveCount(RunOpponent.WILD)
        assertEquals(160, wild)
        assertEquals(20, composition.waveCount(RunOpponent.TRAINER))
        assertEquals(20, composition.waveCount(RunOpponent.BOSS))
        assertEquals(200, wild + 20 + 20)
    }

    @Test
    fun `only wild waves are catchable`() {
        for (wave in 1..200) {
            val plan = composition.planFor(wave, seed = 7L)
            assertEquals(plan.kind == RunOpponent.WILD, plan.catchable, "wave $wave catchability")
        }
    }

    @Test
    fun `intervals are config, not constants`() {
        val brisk = WaveComposition(WaveCompositionConfig(runLength = 30, trainerInterval = 3, bossInterval = 12))
        assertEquals(listOf(12, 24), (1..30).filter { brisk.kindOf(it) == RunOpponent.BOSS })
        assertEquals(RunOpponent.TRAINER, brisk.kindOf(3))
        assertEquals(RunOpponent.WILD, brisk.kindOf(4))
    }

    @Test
    fun `misaligned intervals are allowed but reported`() {
        assertTrue(WaveCompositionConfig().intervalsAligned())
        val skewed = WaveCompositionConfig(trainerInterval = 5, bossInterval = 7)
        assertFalse(skewed.intervalsAligned())
        // The consequence being flagged: wave 35 is both, and the boss wins, so a trainer slot is lost.
        assertEquals(RunOpponent.BOSS, WaveComposition(skewed).kindOf(35))
    }

    @Test
    fun `a run is runLength waves and the last one says so`() {
        assertTrue(composition.planFor(200, 1L).finalWave)
        assertFalse(composition.planFor(199, 1L).finalWave)
        assertFalse(composition.isBeyondRun(200))
        assertTrue(composition.isBeyondRun(201))
    }

    @Test
    fun `an overrun wave composes rather than throwing`() {
        // Lowering runLength under a live run must not crash the run it orphans.
        val plan = composition.planFor(240, seed = 3L)
        assertEquals(RunOpponent.BOSS, plan.kind)
        assertTrue(composition.isBeyondRun(240))
    }

    @Test
    fun `wave zero and below are refused`() {
        assertFailsWith<IllegalArgumentException> { composition.kindOf(0) }
        assertFailsWith<IllegalArgumentException> { composition.kindOf(-1) }
    }

    @Test
    fun `reward routing falls back to the per-kind table`() {
        val routed = WaveComposition(
            WaveCompositionConfig(
                rewards = RewardRouting(
                    byKind = mapOf(RunOpponent.WILD to id("wild"), RunOpponent.BOSS to id("boss")),
                ),
            ),
        )
        assertEquals(id("wild"), routed.rewardTableFor(1))
        assertEquals(id("boss"), routed.rewardTableFor(10))
        // A kind nobody routed rewards nothing — a legitimate authoring choice, not a fault.
        assertNull(routed.rewardTableFor(5))
    }

    @Test
    fun `a band overrides the per-kind table, first match winning`() {
        val routed = WaveComposition(
            WaveCompositionConfig(
                rewards = RewardRouting(
                    byKind = mapOf(RunOpponent.WILD to id("wild"), RunOpponent.BOSS to id("boss")),
                    bands = listOf(
                        RewardBand(minWave = 180, kind = RunOpponent.BOSS, tableId = id("endgame_boss")),
                        RewardBand(minWave = 180, tableId = id("endgame")),
                    ),
                ),
            ),
        )
        assertEquals(id("wild"), routed.rewardTableFor(179))
        assertEquals(id("endgame"), routed.rewardTableFor(181))
        assertEquals(id("endgame_boss"), routed.rewardTableFor(190))
        assertEquals(id("boss"), routed.rewardTableFor(170))
    }

    @Test
    fun `impossible config is refused at construction rather than mid-run`() {
        assertFailsWith<IllegalArgumentException> { WaveCompositionConfig(trainerInterval = 0) }
        assertFailsWith<IllegalArgumentException> { WaveCompositionConfig(bossInterval = 0) }
        assertFailsWith<IllegalArgumentException> { WaveCompositionConfig(runLength = 0) }
        assertFailsWith<IllegalArgumentException> { RewardBand(minWave = 0, tableId = id("x")) }
        assertFailsWith<IllegalArgumentException> { RewardBand(minWave = 20, maxWave = 10, tableId = id("x")) }
    }
}
