package com.cobblemonroguelite.progression

import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The half of the store that survives a restart, and the half that survives two threads.
 *
 * Both fail by *reading back plausibly*, which is why they are worth testing at all: a lost candy
 * count comes back as zero, which is a legal balance and looks exactly like a player who has not
 * played, and a lost IV floor comes back as base 10, which is a legal floor and looks exactly like a
 * species nobody has caught. Neither is detectable in play and neither is arguable after the fact.
 */
class ProgressionPersistenceTest {

    private val torchic = ResourceLocation.fromNamespaceAndPath("cobblemon", "torchic")
    private val gible = ResourceLocation.fromNamespaceAndPath("cobblemon", "gible")

    @Test
    fun `a player's progression round-trips`() {
        val before = PlayerProgression()
        before.update(torchic) {
            it.creditCatch(IvFloor(31, 4, 22, 31, 0, 18), shinyVariant = 0)
                .creditFriendship(200)
                .copy(hiddenAbilityUnlocked = true, costReductions = 1)
        }
        before.update(gible) { it.creditCatch(IvFloor.flat(24), shinyVariant = -1) }

        val after = PlayerProgression.fromNbt(before.toNbt())
        assertEquals(before.all(), after.all())
        assertEquals(IvFloor(31, 10, 22, 31, 10, 18), after.of(torchic).floor)
        assertTrue(after.of(torchic).hiddenAbilityUnlocked)
        assertEquals(1, after.of(torchic).costReductions)
    }

    @Test
    fun `candy earned in a run that was then lost is still there after a restart`() {
        // §1.1 as restated, and the property the whole store exists for: progression is earned by
        // playing, not by winning. The catch is credited at the moment it happens, into a file the
        // run has no say over — so a wipe on the next wave, and the restart after it, change nothing.
        val player = PlayerProgression()
        player.update(torchic) { it.creditCatch(IvFloor.flat(27), shinyVariant = -1) }
        val restarted = PlayerProgression.fromNbt(player.toNbt())

        assertEquals(1, restarted.of(torchic).candy)
        assertEquals(IvFloor.flat(27), restarted.of(torchic).floor)
    }

    @Test
    fun `an untouched species reads as base ten whether or not a row exists`() {
        val player = PlayerProgression()
        assertEquals(IvFloor.BASE, player.of(gible).floor)
        // Reading must not create a row — otherwise a starter screen listing every species would
        // persist one for each of them.
        assertTrue(player.isEmpty())
        assertFalse(player.toNbt().contains(gible.toString()))
    }

    @Test
    fun `an update that produces nothing does not store a row`() {
        val player = PlayerProgression()
        player.update(torchic) { it }
        assertTrue(player.isEmpty())
    }

    @Test
    fun `a damaged floor costs the floor and not the candy`() {
        val tag = SpeciesProgress(candy = 12, hiddenAbilityUnlocked = true).toNbt()
        tag.put("floor", CompoundTag().apply { putIntArray("ivs", intArrayOf(31, 31)) })

        val restored = SpeciesProgress.fromNbt(tag)
        assertEquals(12, restored.candy)
        assertTrue(restored.hiddenAbilityUnlocked)
        assertEquals(IvFloor.BASE, restored.floor)
    }

    @Test
    fun `a non-species key is skipped rather than failing the whole player`() {
        val tag = CompoundTag()
        tag.put("NOT A SPECIES ID", SpeciesProgress(candy = 5).toNbt())
        tag.put(torchic.toString(), SpeciesProgress(candy = 7).toNbt())

        val restored = PlayerProgression.fromNbt(tag)
        assertEquals(7, restored.of(torchic).candy)
        assertEquals(1, restored.all().size)
    }

    @Test
    fun `concurrent credits do not lose candy`() {
        // Catches land on the server thread and friendship lands from battle resolution, which
        // Cobblemon dispatches off-thread. A read-modify-write across those two would drop updates
        // occasionally and silently — the failure nobody ever reports. `compute` over an immutable
        // record is what rules it out, and this is the test that would catch a regression to a
        // get-then-set.
        val player = PlayerProgression()
        val threads = 8
        val perThread = 500
        val pool = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        try {
            repeat(threads) {
                pool.submit {
                    start.await()
                    repeat(perThread) {
                        player.update(torchic) { progress -> progress.copy(candy = progress.candy + 1) }
                    }
                }
            }
            start.countDown()
            pool.shutdown()
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS), "credit threads did not finish")
        } finally {
            pool.shutdownNow()
        }
        assertEquals(threads * perThread, player.of(torchic).candy)
    }

    @Test
    fun `concurrent purchases cannot double-spend`() {
        // Two shop screens, or one double-clicked button. `of()` then `update()` would let both
        // readers pass the affordability check and both deduct.
        val player = PlayerProgression()
        player.update(torchic) { it.copy(candy = 40) }

        val threads = 16
        val pool = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        val bought = java.util.concurrent.atomic.AtomicInteger()
        try {
            repeat(threads) {
                pool.submit {
                    start.await()
                    if (player.buy(torchic, CandyPurchase.HIDDEN_ABILITY) is SpendResult.Ok) bought.incrementAndGet()
                }
            }
            start.countDown()
            pool.shutdown()
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS), "purchase threads did not finish")
        } finally {
            pool.shutdownNow()
        }
        assertEquals(1, bought.get())
        assertEquals(0, player.of(torchic).candy)
    }
}
