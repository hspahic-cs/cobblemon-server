package com.cobblemonroguelite.run

import net.minecraft.core.RegistryAccess
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The two pieces of run state that had to reach disk for §2.16 to hold: the seed of a run whose
 * starter has not been chosen, and the payout table a run is pinned to.
 *
 * Both are round-trips rather than behaviour, and both are worth a test for the same reason: they
 * fail by *reading back plausibly*. A pending start that loses its seed comes back as seed 0, which
 * is a legal seed and hands every affected player the same run; a run that loses its table id comes
 * back paying from the default, which is a table that may well exist.
 */
class PendingStartTest {

    @Test
    fun `a pending start round-trips its seed`() {
        val start = PendingStart(seed = -900L, startedAtMillis = 1_700_000_000_000L)
        val restored = PendingStart.fromNbt(start.toNbt())
        assertEquals(start, restored)
    }

    @Test
    fun `a pending start with no seed is refused rather than defaulted to zero`() {
        assertNull(PendingStart.fromNbt(CompoundTag()))
        // Present-but-zero is a different thing and must survive: 0 is a seed like any other.
        assertEquals(0L, PendingStart.fromNbt(CompoundTag().apply { putLong("seed", 0L) })?.seed)
    }

    @Test
    fun `a run round-trips the payout table it was pinned to`() {
        val table = ResourceLocation.fromNamespaceAndPath("cobblemon_roguelite", "seasonal")
        val tag = RunState(wave = 4, seed = 8L, payoutTable = table).toNbt(RegistryAccess.EMPTY)
        assertEquals(table.toString(), tag.getString("payoutTable"))
    }

    @Test
    fun `a run with no pinned table writes no key`() {
        // Absence has to stay absent, because null is what a run started before a table was
        // configured looks like, and it falls back to the default at payout.
        val tag = RunState(seed = 8L).toNbt(RegistryAccess.EMPTY)
        assertEquals("", tag.getString("payoutTable"))
    }
}
