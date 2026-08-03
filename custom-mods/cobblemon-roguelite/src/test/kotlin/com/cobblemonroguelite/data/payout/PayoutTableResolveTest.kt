package com.cobblemonroguelite.data.payout

import net.minecraft.resources.ResourceLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Resolution: which entries a finished run pays, and that it is a filter rather than a draw.
 *
 * The property under test is not really "the filter works" — it is that two identical runs pay
 * identically and that the result is explainable from the table by reading it. That is what makes
 * the one channel out of a sealed run (§1.1) auditable, and it is the reason this resolves
 * deterministically while [com.cobblemonroguelite.data.reward.RewardTable] rolls.
 */
class PayoutTableResolveTest {

    private fun grant(item: String) = PayoutGrant.Item(ResourceLocation.fromNamespaceAndPath("test", item), 1)

    private fun entry(
        id: String,
        outcomes: Set<RunOutcome>,
        minWave: Int = 1,
        maxWave: Int? = null,
    ) = PayoutEntry(id, outcomes, minWave, maxWave, grant(id))

    private val table = PayoutTable(
        id = ResourceLocation.fromNamespaceAndPath("test", "payout"),
        entries = listOf(
            entry("clear", setOf(RunOutcome.COMPLETED)),
            entry("deep", setOf(RunOutcome.COMPLETED, RunOutcome.WIPED), minWave = 50),
            entry("shallow", setOf(RunOutcome.WIPED, RunOutcome.ABANDONED), minWave = 1, maxWave = 49),
        ),
    )

    @Test
    fun `every matching entry pays, not one of them`() {
        assertEquals(listOf("clear", "deep"), table.entriesFor(RunOutcome.COMPLETED, wave = 200).map { it.id })
    }

    @Test
    fun `outcome gates independently of depth`() {
        // Wave 200 satisfies both bands; only the outcome separates them.
        assertEquals(listOf("deep"), table.entriesFor(RunOutcome.WIPED, wave = 200).map { it.id })
        assertEquals(emptyList(), table.entriesFor(RunOutcome.ABANDONED, wave = 200).map { it.id })
    }

    @Test
    fun `depth bands are inclusive at both ends and do not overlap by accident`() {
        assertEquals(listOf("shallow"), table.entriesFor(RunOutcome.WIPED, wave = 49).map { it.id })
        assertEquals(listOf("deep"), table.entriesFor(RunOutcome.WIPED, wave = 50).map { it.id })
    }

    @Test
    fun `paying nothing is a legitimate answer`() {
        // A table is entitled to pay nothing for a shallow clear, and callers must not read an empty
        // list as a failure to resolve.
        val empty = PayoutTable(
            id = ResourceLocation.fromNamespaceAndPath("test", "empty"),
            entries = listOf(entry("deep_only", setOf(RunOutcome.COMPLETED), minWave = 50)),
        )
        assertTrue(empty.entriesFor(RunOutcome.COMPLETED, wave = 1).isEmpty())
        assertTrue(empty.grantsFor(RunOutcome.COMPLETED, wave = 1).isEmpty())
    }

    @Test
    fun `the same run resolves the same payout every time`() {
        val once = table.grantsFor(RunOutcome.COMPLETED, wave = 137)
        repeat(20) { assertEquals(once, table.grantsFor(RunOutcome.COMPLETED, wave = 137)) }
        assertEquals(listOf(grant("clear"), grant("deep")), once)
    }
}
