package com.cobblemonroguelite.payout

import com.cobblemonroguelite.data.payout.PayoutGrant
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.resources.ResourceLocation
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("cobblemon_roguelite/payout")

/**
 * A payout that was owed to somebody who was not there to take it.
 *
 * ### Why this exists at all
 *
 * A run can end while its player is offline — §2.10's disconnect penalty kills what was on the field
 * at the next login, and if that wipes the party the run ends and owes a payout; an operator clearing
 * a run does the same thing without the login. [com.cobblemonroguelite.run.RunPayoutDelivery] used to
 * hand those grants back as *undelivered* and log at ERROR, which lost nothing but reached nobody.
 * This is the record that holds them until the player comes back.
 *
 * ### Why it is not on the [com.cobblemonroguelite.run.RunState]
 *
 * A pending payout **outlives the run that owed it**. By the time it is owed the run has already been
 * removed from [com.cobblemonroguelite.run.RunStore] — [com.cobblemonroguelite.run.RunStore.end]
 * flushes that removal on purpose, because a run that survives its own payout is a run that can be
 * finished and paid twice. Hanging the debt off the thing that was just deleted to prevent a double
 * payout would be an odd way to keep a payout.
 *
 * ### What it deliberately does not record
 *
 * The outcome, the wave and the table id. They belong to the run, the run is gone, and the run-end
 * line in [com.cobblemonroguelite.run.RunController] already wrote all three next to this player's
 * UUID at the moment the debt was taken on. Copying them here would be a second copy of an audit
 * record that would go stale the first time anyone hand-edited the file, to serve a message the
 * player does not need: "your run ended while you were away, here is what it paid" is the whole of
 * what they have to be told.
 *
 * @property grants what the table resolved, **unresolved**. Item ids are not looked up here on
 *   purpose — see [PayoutDropPlanner]. A datapack reload between the run ending and the player
 *   returning can add the id back, and resolving early would turn a recoverable id into a permanent
 *   loss recorded in a save file.
 * @property owedAtEpochMs wall clock at the moment the debt was taken on. Nothing reads it to make a
 *   decision (see [PendingPayoutLedger] on expiry); it is here so the log line can say how long
 *   somebody has been away, and so that a future expiry policy is a code change rather than a
 *   migration on a file that never wrote the field down.
 */
data class PendingPayout(
    val grants: List<PayoutGrant>,
    val owedAtEpochMs: Long,
) {

    val isEmpty: Boolean get() = grants.isEmpty()

    fun toNbt(): CompoundTag {
        val tag = CompoundTag()
        val list = ListTag()
        for (grant in grants) {
            when (grant) {
                // Exhaustive on purpose: PayoutGrant is sealed so that a new grant kind is a compile
                // error here rather than an entry that writes nothing and reads back as a payout the
                // player never got.
                is PayoutGrant.Item -> list.add(
                    CompoundTag().apply {
                        putString(TYPE_KEY, ITEM_TYPE)
                        putString(ITEM_KEY, grant.item.toString())
                        putInt(COUNT_KEY, grant.count)
                    },
                )
            }
        }
        tag.put(GRANTS_KEY, list)
        tag.putLong(OWED_AT_KEY, owedAtEpochMs)
        return tag
    }

    companion object {

        private const val GRANTS_KEY = "grants"
        private const val OWED_AT_KEY = "owedAt"
        private const val TYPE_KEY = "type"
        private const val ITEM_KEY = "item"
        private const val COUNT_KEY = "count"
        private const val ITEM_TYPE = "item"
        private const val TAG_COMPOUND = 10

        fun of(grants: List<PayoutGrant>, nowEpochMs: Long = System.currentTimeMillis()): PendingPayout =
            PendingPayout(grants.toList(), nowEpochMs)

        /**
         * Read one back. Null when there is nothing left worth holding.
         *
         * A grant that fails to read is **logged at ERROR naming the id** and dropped from this
         * record, and that is the only way a grant may ever leave the ledger. The alternative —
         * discarding the whole record because one entry is unreadable — would lose the nine grants
         * that were fine along with the one that was not, and the alternative to *that* — keeping an
         * unreadable entry forever — is a record that can never be settled and will re-log on every
         * login for the life of the world.
         */
        fun fromNbt(tag: CompoundTag): PendingPayout? {
            val list = tag.getList(GRANTS_KEY, TAG_COMPOUND)
            val grants = mutableListOf<PayoutGrant>()
            for (i in 0 until list.size) {
                val entry = list.getCompound(i)
                val grant = grantFromNbt(entry)
                if (grant == null) {
                    log.error(
                        "roguelite: a held payout grant could not be read and is being dropped — raw entry was {}",
                        entry,
                    )
                    continue
                }
                grants += grant
            }
            if (grants.isEmpty()) return null
            return PendingPayout(grants, tag.getLong(OWED_AT_KEY))
        }

        private fun grantFromNbt(tag: CompoundTag): PayoutGrant? {
            return when (val type = tag.getString(TYPE_KEY)) {
                ITEM_TYPE -> {
                    val raw = tag.getString(ITEM_KEY)
                    val id = ResourceLocation.tryParse(raw)
                    val count = tag.getInt(COUNT_KEY)
                    when {
                        id == null -> {
                            log.error("roguelite: held payout names item '{}', which is not a valid id", raw)
                            null
                        }

                        count < 1 -> {
                            log.error("roguelite: held payout of '{}' has count {} — refusing it", raw, count)
                            null
                        }

                        else -> PayoutGrant.Item(id, count)
                    }
                }

                else -> {
                    // Reachable in exactly one way that is not corruption: a save written by a newer
                    // build that had a grant kind this one does not. Loud rather than silent for that
                    // reason — it is a downgrade, and the player is owed something we cannot name.
                    log.error("roguelite: held payout has unknown grant type '{}'", type)
                    null
                }
            }
        }
    }
}
