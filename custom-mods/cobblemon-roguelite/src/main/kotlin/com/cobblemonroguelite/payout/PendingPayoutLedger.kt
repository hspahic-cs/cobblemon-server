package com.cobblemonroguelite.payout

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private val log = LoggerFactory.getLogger("cobblemon_roguelite/payout")

/**
 * Who is owed what, and the rule that they are paid exactly once.
 *
 * ### Why this is a plain class and [PendingPayoutStore] is the `SavedData`
 *
 * Same split as [com.cobblemonroguelite.progression.PlayerProgression] under
 * [com.cobblemonroguelite.progression.ProgressionStore], and for the same reason: everything that
 * decides anything lives here, where it can be tested without a booted server. A payout that is paid
 * twice mints items out of nothing and a payout that is paid zero times is the bug this whole file
 * exists to fix, so "exactly once" is not something that should first execute in production.
 *
 * ### The exactly-once argument
 *
 * [claim] is a single `ConcurrentHashMap.remove`. That is the whole of the mutual exclusion: the map
 * hands the list to exactly one caller and every other caller — a second login event, a retry from
 * the tick loop, a command — gets an empty list. There is no read-then-remove window for two callers
 * to both pass through, which is precisely the shape that would pay twice.
 *
 * That leaves the *crash* window, which cannot be closed on this side because the items and the
 * ledger are two different files. [PendingPayoutStore.claim] settles it by flushing the removal to
 * disk **before** the items are dropped, so a crash in between loses the payout rather than
 * duplicating it. That direction is deliberate:
 *
 * - Paying twice is unbounded and undetectable. The crash window is identical on every restart, so a
 *   server crash-looping in it duplicates the payout every time, and nothing in the log distinguishes
 *   the second copy from the first. §2.2 refuses a faucet this module can open by itself; a payout
 *   that reprints itself is exactly that faucet.
 * - Paying zero is bounded, single, and repairable. It is bounded because the record is gone and can
 *   only fail once; repairable because [PendingPayoutStore.claim] writes the full grant list to the
 *   log *before* the flush, so an operator can hand it back with `/give` from a line that names the
 *   player and every item and count.
 *
 * "Not silently zero" is the part that is load-bearing there, and it is why the pre-claim log line is
 * at INFO on the happy path rather than being written only when something goes wrong: a line that is
 * only emitted on failure is a line nobody has ever seen work.
 *
 * ### Expiry: there is none, and that is the decision
 *
 * §2.23 decided run expiry is generous because a run is a handful of Pokémon and storage is cheap.
 * The same argument applies here *more strongly*, and it is worth saying why rather than assuming it:
 * a held payout is a few item ids and counts — orders of magnitude smaller than the six serialized
 * Pokémon a run carries — and a player can hold at most one run, so this file grows by roughly one
 * short list per player who ever had a run end while they were away.
 *
 * But the size argument is not the reason for the answer, because expiry there and expiry here are
 * not the same act. §2.23 could delete an abandoned run and pay nothing, on the grounds that somebody
 * who has not touched a run in six months is not owed anything. A held payout is the opposite case:
 * it is *already owed*. It was earned, the table resolved it, and the only reason it is sitting here
 * rather than in the player's hands is that the server chose the moment of delivery — often by
 * killing their party while they were disconnected. Expiring it would take back something the mode
 * had already decided to hand over, to save bytes it does not need to save.
 *
 * So: **a held payout never expires.** If this file ever does become a problem, the honest fix is an
 * operator command that lists and clears it, not a timer that quietly deletes debts.
 *
 * The one thing that is watched is [CROWDED_QUEUE]: a single player accumulating a stack of held
 * payouts means delivery has been deferring for a long time (see [PendingPayoutGate]), which is a bug
 * rather than a player being unlucky, and it should be visible in the log before it is visible as a
 * pile of items.
 */
class PendingPayoutLedger {

    private val owed = ConcurrentHashMap<UUID, List<PendingPayout>>()

    fun isOwed(player: UUID): Boolean = owed.containsKey(player)

    /** What [player] is owed, without taking it. For status commands and for logging. */
    fun peek(player: UUID): List<PendingPayout> = owed[player].orEmpty()

    fun players(): Set<UUID> = owed.keys.toSet()

    fun isEmpty(): Boolean = owed.isEmpty()

    /**
     * Take on a debt. Returns the queue depth afterwards, so the caller can log it.
     *
     * Appends rather than replaces. Two held payouts for one player is rare but reachable — a payout
     * held on Monday that has not been delivered yet (the player has been inside a run every time
     * they logged in) and a second run that also ended while they were offline — and replacing would
     * silently destroy the first one, which is the failure mode this file was written to end.
     */
    fun hold(player: UUID, payout: PendingPayout): Int {
        if (payout.isEmpty) return owed[player]?.size ?: 0
        val queue = owed.compute(player) { _, existing -> (existing ?: emptyList()) + payout }.orEmpty()
        if (queue.size >= CROWDED_QUEUE) {
            log.error(
                "roguelite: {} is now holding {} undelivered payouts — delivery has been deferring for a " +
                    "long time and should be looked at; nothing is lost, but nothing is arriving either",
                player, queue.size,
            )
        }
        return queue.size
    }

    /**
     * Take everything [player] is owed, once. Every other caller gets an empty list.
     *
     * See the class docs: this single `remove` is the exactly-once mechanism, and the reason nothing
     * here reads before it removes.
     */
    fun claim(player: UUID): List<PendingPayout> = owed.remove(player).orEmpty()

    fun toNbt(): CompoundTag {
        val tag = CompoundTag()
        owed.forEach { (player, payouts) ->
            val list = ListTag()
            payouts.forEach { if (!it.isEmpty) list.add(it.toNbt()) }
            // An empty list is not written. A player key with nothing under it would be re-read as a
            // debt of nothing and would re-arm delivery on every login forever.
            if (list.isNotEmpty()) tag.put(player.toString(), list)
        }
        return tag
    }

    companion object {

        /** Queue depth at which a held payout stops looking like bad luck and starts looking like a bug. */
        const val CROWDED_QUEUE = 8

        private const val TAG_COMPOUND = 10

        fun fromNbt(tag: CompoundTag): PendingPayoutLedger {
            val ledger = PendingPayoutLedger()
            for (key in tag.allKeys) {
                val player = runCatching { UUID.fromString(key) }.getOrNull()
                if (player == null) {
                    // ERROR, not WARN as the other stores use for the same shape. A malformed key in
                    // the progression file costs one player their candy and is visible to them the
                    // next time they look; a malformed key here is a debt that will never be paid and
                    // that nobody will ever think to look for.
                    log.error("roguelite: held payouts under non-UUID key '{}' — that debt cannot be paid", key)
                    continue
                }
                val list = tag.getList(key, TAG_COMPOUND)
                val payouts = (0 until list.size).mapNotNull { PendingPayout.fromNbt(list.getCompound(it)) }
                if (payouts.isEmpty()) {
                    log.error("roguelite: {} had held payouts on disk but none of them could be read", player)
                    continue
                }
                ledger.owed[player] = payouts
            }
            return ledger
        }
    }
}
