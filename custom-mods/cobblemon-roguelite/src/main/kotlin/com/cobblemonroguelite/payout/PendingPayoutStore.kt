package com.cobblemonroguelite.payout

import com.cobblemonroguelite.data.payout.PayoutGrant
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.saveddata.SavedData
import org.slf4j.LoggerFactory
import java.util.UUID

private val log = LoggerFactory.getLogger("cobblemon_roguelite/payout")

/**
 * Payouts owed to players who were not online to take them, on disk.
 *
 * ### Why world [SavedData], for the third time
 *
 * The same argument [com.cobblemonroguelite.run.RunStore] and
 * [com.cobblemonroguelite.progression.ProgressionStore] make: raw `player.persistentData` does not
 * survive a death-respawn, because `ServerPlayer.restoreFrom` copies only the `PlayerPersisted`
 * subtag across the clone. The failure that would cause here is a particularly bad one — a player
 * logs in owed a payout, is killed by whatever was standing where they logged out, and the debt is
 * erased by the respawn before the delivery gate ever opened.
 *
 * ### Why a third file rather than a key in either of the other two
 *
 * Lifetime, again, and it is the *reason this store exists at all*: a held payout outlives the run
 * that owed it. [com.cobblemonroguelite.run.RunStore.end] deletes the run and flushes that deletion
 * immediately, precisely so a crash cannot restore a run that has already been paid — so the run file
 * is the one file that is guaranteed not to be holding anything by the time the debt exists. And it
 * is not the progression file either: progression is permanent and never settled, whereas every row
 * here is a debt that is supposed to disappear. A file whose normal state is empty is worth being
 * able to look at and see empty.
 *
 * ### Flushing
 *
 * Both mutations flush, and the flush on [claim] is not merely prompt but *ordered*: it happens
 * before the items are handed over. [PendingPayoutLedger] argues why that ordering is the whole
 * exactly-once story.
 */
class PendingPayoutStore private constructor(
    private val ledger: PendingPayoutLedger,
) : SavedData() {

    fun isOwed(player: UUID): Boolean = ledger.isOwed(player)

    fun peek(player: UUID): List<PendingPayout> = ledger.peek(player)

    /** Everyone currently owed something. For op tooling and for the boot line. */
    fun owedPlayers(): Set<UUID> = ledger.players()

    /**
     * Record that [player] is owed [grants], and get it onto disk now.
     *
     * Flushed for [com.cobblemonroguelite.run.RunStore.end]'s reason turned around. That flush exists
     * so a crash cannot resurrect a run that was already paid; this one exists so a crash cannot lose
     * a payout that was already earned. They are the two halves of the same instant — the run is
     * removed and the debt is written — and leaving either to the next autosave puts a window in it
     * where the run is over and nothing owes anything.
     */
    fun hold(server: MinecraftServer, player: UUID, grants: List<PayoutGrant>): PendingPayout? {
        if (grants.isEmpty()) return null
        val payout = PendingPayout.of(grants)
        val depth = ledger.hold(player, payout)
        setDirty()
        flush(server, player)
        log.info(
            "roguelite: {} was offline at payout — holding {} grant(s) {} for delivery at their next login " +
                "(queue depth {})",
            player, grants.size, grants.describe(), depth,
        )
        return payout
    }

    /**
     * Take everything [player] is owed and get the removal onto disk **before** the caller hands
     * anything over.
     *
     * ### Read this before changing the order of the three statements below
     *
     * The log line comes first, then the claim, then the flush, and only then may the caller drop
     * anything. The log line is first because it is the recovery copy: if the process dies at any
     * point after it, an operator can reconstruct the payout from a line that names the player, every
     * item and every count. The flush is before the return because a claim that is only in memory is
     * not a claim — the server would come back up still owing it, and the payout would be handed over
     * a second time.
     *
     * ### Server thread
     *
     * Call this on the server thread. [flush] defers through `server.execute`, which runs inline when
     * we are already on it; from any other thread the write is merely *queued*, which reopens exactly
     * the double-pay window this method is shaped to close. The only caller is the tick loop in
     * [PendingPayoutHooks], and the check below exists so that a future second caller finds out from
     * a log line rather than from a duplicated payout.
     */
    fun claim(server: MinecraftServer, player: UUID): List<PendingPayout> {
        if (!ledger.isOwed(player)) return emptyList()
        if (!server.isSameThread) {
            log.error(
                "roguelite: held payout for {} is being claimed off the server thread — the removal will " +
                    "only be queued, so a crash in the next tick can pay it twice",
                player,
            )
        }
        val owed = ledger.peek(player)
        // Before the claim, deliberately. See the method docs: this line is the only copy of the
        // payout that survives a crash between here and the items existing in the world.
        log.info(
            "roguelite: paying {} {} held payout(s) {} — if nothing follows this line, that payout was lost " +
                "and can be restored from it",
            player, owed.size, owed.flatMap { it.grants }.describe(),
        )
        val claimed = ledger.claim(player)
        if (claimed.isEmpty()) return emptyList()
        setDirty()
        flush(server, player)
        return claimed
    }

    /**
     * Identical to [com.cobblemonroguelite.run.RunStore.flush] and
     * [com.cobblemonroguelite.progression.ProgressionStore.flush], for the identical reason: an
     * inline `save()` from an off-thread caller can race the world autosave already walking the same
     * `DimensionDataStorage`, and the loser is a truncated file. On the server thread `execute` runs
     * the block inline, which is what makes the ordering guarantee in [claim] real rather than
     * aspirational.
     */
    private fun flush(server: MinecraftServer, player: UUID) {
        server.execute {
            runCatching { server.overworld().dataStorage.save() }
                .onFailure { log.error("roguelite: held payout flush failed for {}", player, it) }
        }
    }

    override fun save(tag: CompoundTag, registries: HolderLookup.Provider): CompoundTag {
        tag.put(OWED_KEY, ledger.toNbt())
        tag.putInt(SCHEMA_KEY, SCHEMA_VERSION)
        return tag
    }

    companion object {

        /** File name under `<world>/data/`. Its normal state is an empty `owed` tag. */
        const val DATA_NAME = "cobblemon_roguelite_pending_payouts"

        /**
         * Bump when the meaning of anything [save] writes changes. Same one-int insurance
         * [com.cobblemonroguelite.progression.ProgressionStore] carries, and wanted here for a reason
         * that file does not have: the grant encoding is a copy of a *datapack* schema, so it moves
         * whenever [PayoutGrant] gains a kind.
         */
        const val SCHEMA_VERSION = 1

        private const val OWED_KEY = "owed"
        private const val SCHEMA_KEY = "schema"

        fun of(server: MinecraftServer): PendingPayoutStore {
            val factory = SavedData.Factory(
                { PendingPayoutStore(PendingPayoutLedger()) },
                { tag, _ -> load(tag) },
            )
            return server.overworld().dataStorage.computeIfAbsent(factory, DATA_NAME)
        }

        private fun load(tag: CompoundTag): PendingPayoutStore {
            val ledger = PendingPayoutLedger.fromNbt(tag.getCompound(OWED_KEY))
            // Logged even when empty, unlike the other two stores. An empty run file is the normal
            // state of a server nobody is playing on; an empty payout file is the normal state of a
            // *healthy* one, and the line that says so is what tells an operator that the file was
            // read at all when somebody reports a payout that never arrived.
            log.info("roguelite: {} player(s) are owed a held payout", ledger.players().size)
            return PendingPayoutStore(ledger)
        }
    }
}

/** The grant list as it appears in a log line an operator has to be able to act on. */
internal fun List<PayoutGrant>.describe(): String = joinToString(", ", "[", "]") { grant ->
    when (grant) {
        is PayoutGrant.Item -> "${grant.count}x ${grant.item}"
    }
}
