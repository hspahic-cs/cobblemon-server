package com.cobblemonroguelite.run

import net.minecraft.nbt.CompoundTag

/**
 * A run that has been paid for and seeded but whose starter has not been chosen yet.
 *
 * ### Why this is persisted state and not a field on a GUI
 *
 * §2.16 requires the seed to be minted **and written down** before anything derived from it is
 * shown, and the starter offer is the first thing derived from it. Holding the seed only in the
 * open selection screen would mean a player who disconnects while looking at three species comes
 * back to a re-rolled offer — and since the fee was already taken at the door (§2.16's allowance
 * consumption), they would have paid for a run and been handed a different one. Worse, it is the
 * exact input a player would learn to exploit: quit at the offer screen until the draft is good.
 *
 * So the moment the charge succeeds, this record exists and is on disk. Everything after it is
 * recoverable: [RunStore.pending] is what a reconnect reads to put the player back in front of the
 * same offer.
 *
 * ### Why the offer itself is not stored here
 *
 * The offer is a pure function of `(seed, eligible species)` — see
 * [com.cobblemonroguelite.starter.StarterOfferFactory] — so recomputing it from the seed gives the
 * same three species, and storing it would be a second copy that can disagree with the first. The
 * one input that can move underneath it is the player's Pokédex, and that class already states why
 * that re-roll is accepted rather than closed: triggering it costs catching a species you have never
 * caught, and closing it would mean snapshotting the eligible set into the save.
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
