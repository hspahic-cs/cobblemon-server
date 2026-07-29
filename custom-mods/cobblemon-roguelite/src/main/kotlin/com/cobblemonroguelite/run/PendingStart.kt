package com.cobblemonroguelite.run

import net.minecraft.nbt.CompoundTag

/**
 * A run that has been paid for and seeded but whose starting team has not been bought yet.
 *
 * ### Why this is persisted state and not a field on a GUI
 *
 * §2.16 requires the seed to be minted **and written down** before anything derived from it is
 * shown. Holding it only in the open selection screen would mean a player who disconnects mid-choice
 * comes back to a different run — and since the fee was already taken at the door (§2.16's allowance
 * consumption), they would have paid for a run and been handed another one.
 *
 * Under §2.13's superseded random offer that was also the anti-reroll guarantee: the offer was drawn
 * from the seed, so an unpersisted seed meant quitting at the selection screen until the draft was
 * good. A budget catalogue is not drawn, so that particular exploit no longer exists — but the seed
 * still decides the team's IVs
 * ([com.cobblemonroguelite.starter.StarterIvRoll]), so it still has to be written down before the
 * player can act on it.
 *
 * So the moment the charge succeeds, this record exists and is on disk. Everything after it is
 * recoverable: [RunStore.pending] is what a reconnect reads to put the player back in front of the
 * same catalogue.
 *
 * ### Why the catalogue itself is not stored here
 *
 * It is a pure function of the player's eligible species and the price table — see
 * [com.cobblemonroguelite.starter.StarterCatalogueFactory] — so rebuilding it costs a map lookup,
 * and storing it would be a second copy that can disagree with the first. Two inputs can move
 * underneath it: catching a new species on the server widens it, and a `/reload` can re-price it.
 * Both are accepted rather than closed, for the reason that class gives — snapshotting them would be
 * save state we then have to version, and it would also freeze a price an operator has since fixed.
 *
 * ### Why there is no expiry on this
 *
 * A pending start that is never resolved sits in the save file forever, and that is the correct
 * behaviour today: the player paid, and no timeout we invent here gives them their fee back (there
 * is deliberately no refund seam — see [com.cobblemonroguelite.integration.RunCharges]). Expiry of
 * *runs* is an open question in the plan (§5); this record inherits whatever that decides.
 *
 * @property seed the run's seed. Once written, the run that grows out of this record must use it.
 * @property startedAtMillis wall-clock time the charge was taken. Recorded for op diagnosis and for
 *   whatever §5's expiry rule turns out to be — nothing reads it to make a decision today.
 */
data class PendingStart(
    val seed: Long,
    val startedAtMillis: Long,
) {

    fun toNbt(): CompoundTag = CompoundTag().apply {
        putLong("seed", seed)
        putLong("startedAt", startedAtMillis)
    }

    companion object {
        /**
         * Restore a pending start, or null if the tag cannot supply a seed.
         *
         * A missing seed reads as 0, which is a legal seed — so absence is checked directly rather
         * than inferred from the value. A pending start silently defaulting to seed 0 would hand
         * every affected player the identical run, which is the failure [RunState.seed] refuses a
         * default in order to avoid.
         */
        fun fromNbt(tag: CompoundTag): PendingStart? {
            if (!tag.contains("seed")) return null
            return PendingStart(
                seed = tag.getLong("seed"),
                startedAtMillis = tag.getLong("startedAt"),
            )
        }
    }
}
