package com.cobblemonroguelite.payout

import com.cobblemonroguelite.data.payout.PayoutGrant
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.resources.ResourceLocation
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The ledger, and the two properties it is worth having at all: exactly once, and it survives a
 * restart.
 *
 * Both fail *plausibly*, which is the reason for testing them rather than reading them. A payout paid
 * twice looks like a generous payout; a payout lost to a restart looks like a payout the table never
 * had an entry for. Neither is arguable after the fact, and the case that produces both — a run that
 * ended while its owner was disconnected — is the one nobody is watching when it happens.
 */
class PendingPayoutLedgerTest {

    private val player = UUID.fromString("00000000-0000-0000-0000-00000000beef")
    private val diamond = ResourceLocation.fromNamespaceAndPath("minecraft", "diamond")
    private val candy = ResourceLocation.fromNamespaceAndPath("cobblemon", "rare_candy")

    private fun payout(vararg grants: PayoutGrant, owedAt: Long = 1_700_000_000_000L) =
        PendingPayout(grants.toList(), owedAt)

    @Test
    fun `a held payout round-trips through a restart`() {
        val before = PendingPayoutLedger()
        before.hold(player, payout(PayoutGrant.Item(diamond, 4), PayoutGrant.Item(candy, 128)))

        val after = PendingPayoutLedger.fromNbt(before.toNbt())

        assertEquals(before.peek(player), after.peek(player))
        assertEquals(2, after.peek(player).single().grants.size)
        assertEquals(1_700_000_000_000L, after.peek(player).single().owedAtEpochMs)
    }

    @Test
    fun `two payouts owed to one player both survive`() {
        // Reachable: a payout held on Monday that delivery has been deferring (the player logs
        // straight into a run every time), plus a second run that also ended while they were away.
        // The failure this guards is `hold` replacing rather than appending, which would destroy the
        // first payout at the exact moment the system looked like it was working.
        val ledger = PendingPayoutLedger()
        ledger.hold(player, payout(PayoutGrant.Item(diamond, 1)))
        ledger.hold(player, payout(PayoutGrant.Item(candy, 2)))

        val restarted = PendingPayoutLedger.fromNbt(ledger.toNbt())

        assertEquals(2, restarted.peek(player).size)
        assertEquals(listOf(1, 2), restarted.peek(player).flatMap { it.grants }.map { (it as PayoutGrant.Item).count })
    }

    @Test
    fun `claiming twice pays once`() {
        val ledger = PendingPayoutLedger()
        ledger.hold(player, payout(PayoutGrant.Item(diamond, 4)))

        val first = ledger.claim(player)
        val second = ledger.claim(player)

        assertEquals(1, first.size)
        assertTrue(second.isEmpty())
        assertFalse(ledger.isOwed(player))
    }

    @Test
    fun `two threads claiming at once pay once`() {
        // The real shape of the race is a login event and the tick loop, or a tick loop and an
        // operator command. A read-then-remove would let both through and mint a second payout out of
        // nothing, which §2.2 refuses on principle and which nothing downstream could detect.
        val ledger = PendingPayoutLedger()
        ledger.hold(player, payout(PayoutGrant.Item(diamond, 4)))

        val threads = 16
        val start = CountDownLatch(1)
        val done = CountDownLatch(threads)
        val winners = AtomicInteger()
        val pool = Executors.newFixedThreadPool(threads)
        repeat(threads) {
            pool.submit {
                start.await()
                if (ledger.claim(player).isNotEmpty()) winners.incrementAndGet()
                done.countDown()
            }
        }
        start.countDown()
        assertTrue(done.await(10, TimeUnit.SECONDS), "claims did not finish")
        pool.shutdown()

        assertEquals(1, winners.get())
        assertFalse(ledger.isOwed(player))
    }

    @Test
    fun `holding and claiming concurrently loses nothing`() {
        // The other side of the same race: a run ending for one player while another is being paid.
        // A lost hold is a payout that was earned and never recorded — the failure the store exists
        // to end — so the count has to come out exact rather than approximately right.
        val ledger = PendingPayoutLedger()
        val players = (1..32).map { UUID.randomUUID() }
        val pool = Executors.newFixedThreadPool(8)
        val start = CountDownLatch(1)
        val done = CountDownLatch(players.size)
        players.forEach { uuid ->
            pool.submit {
                start.await()
                repeat(4) { ledger.hold(uuid, payout(PayoutGrant.Item(diamond, 1))) }
                done.countDown()
            }
        }
        start.countDown()
        assertTrue(done.await(10, TimeUnit.SECONDS), "holds did not finish")
        pool.shutdown()

        assertEquals(players.size, ledger.players().size)
        assertEquals(players.size * 4, players.sumOf { ledger.peek(it).size })
    }

    @Test
    fun `an empty payout is never recorded`() {
        // Otherwise a run that pays nothing to an offline player leaves a row that arms delivery on
        // every login for the life of the world and hands over nothing each time.
        val ledger = PendingPayoutLedger()
        ledger.hold(player, PendingPayout(emptyList(), 0L))

        assertFalse(ledger.isOwed(player))
        assertTrue(ledger.toNbt().allKeys.isEmpty())
    }

    @Test
    fun `an unreadable grant is dropped and the readable ones in the same payout still pay`() {
        // "Logged loudly, not dropped from the ledger silently" cuts both ways: the bad entry goes
        // (it can never be handed over), and the nine good ones next to it must not go with it.
        val tag = CompoundTag()
        val payouts = ListTag()
        val one = CompoundTag()
        val grants = ListTag()
        grants.add(CompoundTag().apply { putString("type", "item"); putString("item", "minecraft:diamond"); putInt("count", 2) })
        grants.add(CompoundTag().apply { putString("type", "sackful_of_money"); putInt("count", 9) })
        grants.add(CompoundTag().apply { putString("type", "item"); putString("item", "not a valid id"); putInt("count", 1) })
        one.put("grants", grants)
        one.putLong("owedAt", 7L)
        payouts.add(one)
        tag.put(player.toString(), payouts)

        val ledger = PendingPayoutLedger.fromNbt(tag)

        assertEquals(listOf(PayoutGrant.Item(diamond, 2)), ledger.peek(player).single().grants)
    }

    @Test
    fun `a payout whose every grant is unreadable is not kept as an empty debt`() {
        val grants = ListTag()
        grants.add(CompoundTag().apply { putString("type", "item"); putString("item", "minecraft:diamond"); putInt("count", -1) })
        val one = CompoundTag().apply { put("grants", grants) }

        assertNull(PendingPayout.fromNbt(one))
    }

    @Test
    fun `a row under a key that is not a UUID does not take the other rows with it`() {
        val tag = CompoundTag()
        tag.put("definitely-not-a-uuid", ListTag().apply { add(payout(PayoutGrant.Item(diamond, 1)).toNbt()) })
        tag.put(player.toString(), ListTag().apply { add(payout(PayoutGrant.Item(candy, 3)).toNbt()) })

        val ledger = PendingPayoutLedger.fromNbt(tag)

        assertEquals(setOf(player), ledger.players())
        assertEquals(listOf(PayoutGrant.Item(candy, 3)), ledger.peek(player).single().grants)
    }
}
