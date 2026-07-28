package com.cobblemonroguelite.starter

import net.minecraft.resources.ResourceLocation
import org.slf4j.LoggerFactory
import java.util.Random
import java.util.UUID

private val log = LoggerFactory.getLogger("cobblemon_roguelite/starter")

/**
 * The species a player may choose between at the start of a run. They pick exactly one; the party
 * grows from there by catching (§2.13).
 *
 * Holds species ids and nothing else — no rarity band, no "you unlocked this" marker. A field of
 * that kind would be read by the selection GUI, then by the shop, and by then the Pokédex would be
 * deciding how strong a run is instead of what is in it (§2.15).
 */
data class StarterOffer(val species: List<ResourceLocation>) {

    val isEmpty: Boolean get() = species.isEmpty()

    fun contains(id: ResourceLocation): Boolean = species.contains(id)
}

/**
 * Builds the starter offer: baseline pool ∪ Pokédex unlocks, sampled down to the configured offer
 * size with a seeded draw.
 *
 * ### Why the two inputs are separate objects
 *
 * [pool] answers "what is always available" and [unlocks] answers "what has this player earned".
 * The union is taken here, in one place, and produces a plain set of ids — after that line nothing
 * downstream can tell the two apart, which is how §2.15's "unlocking is not power" survives contact
 * with a future weighting table. [weighting] is handed one species id at a time and is never given
 * the player, so it cannot consult the Pokédex even if someone wants it to.
 *
 * ### Why the draw is seeded off the run seed
 *
 * The same reason wave generation is (§2.3): a checkpointable run whose contents are rolled at call
 * time is rerollable by pulling the plug. Offers are a pure function of (run seed, eligible set), so
 * a player who disconnects at the selection screen and reconnects is shown the same three species.
 *
 * Two things this deliberately does *not* protect against, both of which belong upstream:
 *
 * - **The caller must fix the seed before showing the offer.** Generating a fresh seed on each
 *   `/roguelite start` makes abandon-and-restart a reroll. The plan lists entry gating as an open
 *   question (§5, phase 2); this class cannot answer it, it can only be honest that a new seed is a
 *   new offer.
 * - **A changing Pokédex changes the offer.** Catching a new species between disconnect and
 *   reconnect widens the eligible set and therefore re-rolls the draw. That is not a reroll exploit
 *   worth closing — the cost of triggering it is catching a species you had never caught — and
 *   closing it would mean snapshotting the eligible set into the run, which is state we would then
 *   have to version.
 */
class StarterOfferFactory(
    private val pool: StarterPoolSource,
    private val unlocks: CaughtSpeciesSource,
    private val weighting: StarterWeighting = StarterWeighting.Uniform,
) {

    /**
     * Every species [player] could be shown. The union is unordered on purpose: an offer must not
     * be able to depend on whether a species was baseline or earned, and a `List` here would tempt
     * exactly that.
     */
    fun eligibleSpecies(player: UUID): Set<ResourceLocation> {
        val baseline = pool.baselinePool()
        if (baseline.isEmpty()) {
            // Not a warning. §2.15 makes the baseline mandatory because the dex-only offer is worst
            // for the player meeting the mode for the first time, and a server in this state hands
            // a new player nothing at all.
            log.error("roguelite: starter baseline pool is empty — new players will get no offer; check the starter config")
        }
        return baseline.toMutableSet().apply { addAll(unlocks.caughtSpecies(player)) }
    }

    fun offerFor(player: UUID, runSeed: Long): StarterOffer =
        offerFrom(eligibleSpecies(player), runSeed)

    /**
     * The draw itself, taking the eligible set directly.
     *
     * Split out so the sampling can be tested without a Pokédex, and — more usefully — so the
     * signature records that everything past this point works from species ids alone. Nothing here
     * receives the player.
     */
    fun offerFrom(eligible: Set<ResourceLocation>, runSeed: Long): StarterOffer {
        // Sorting is what makes the offer reproducible at all. `eligible` arrives from a set union
        // over Cobblemon's Pokédex map, whose iteration order tracks insertion and so differs
        // between a fresh login and a resumed session. Seeding the RNG identically but feeding it
        // candidates in a different order yields a different offer, which would look exactly like
        // the reroll exploit the seed exists to prevent.
        val candidates = eligible.sortedBy { it.toString() }.toMutableList()
        if (candidates.isEmpty()) return StarterOffer(emptyList())

        val size = pool.offerSize().coerceIn(1, candidates.size)
        val rng = Random(starterSeed(runSeed))
        val picked = ArrayList<ResourceLocation>(size)
        repeat(size) {
            picked.add(candidates.removeAt(drawIndex(candidates, rng)))
        }
        return StarterOffer(picked)
    }

    /**
     * Index of one weighted draw over [candidates], which must be non-empty.
     *
     * Weights are recomputed per draw rather than once up front so that a weighting implementation
     * backed by a live config table cannot desync from the list it is weighting — the cost is a few
     * dozen lookups per run, once.
     */
    private fun drawIndex(candidates: List<ResourceLocation>, rng: Random): Int {
        val weights = LongArray(candidates.size) { weighting.weight(candidates[it]).coerceAtLeast(0).toLong() }
        val total = weights.sum()
        if (total <= 0L) {
            // A weighting table that zeroes out everything still has to produce an offer. Failing
            // to uniform is a visibly wrong offer; failing to empty is a player who cannot start a
            // run and no obvious reason why.
            log.warn("roguelite: every eligible starter weighted 0 — falling back to a uniform draw")
            return rng.nextInt(candidates.size)
        }
        // `nextDouble` rather than a bounded integer draw because weights sum as Long and could
        // overflow an Int bound; the multiply is deterministic, which is all the seeding needs.
        var roll = (rng.nextDouble() * total).toLong().coerceIn(0L, total - 1L)
        for (i in weights.indices) {
            roll -= weights[i]
            if (roll < 0L) return i
        }
        return candidates.lastIndex
    }

    companion object {
        /**
         * Derives the starter draw's seed from the run seed.
         *
         * The salt and the mix are both needed. `wave/` will derive its own streams from the same
         * run seed, and `java.util.Random` seeded with the same value twice produces the same
         * sequence — so an unsalted starter draw would be a visible function of the wave-1 roll, and
         * a player who learned the mapping could read their first opponents off the offer screen.
         * The splitmix64 finaliser then spreads the salted value, because `Random`'s own scrambler
         * leaves nearby seeds producing correlated first draws, and consecutive run seeds are
         * exactly what a counter-based or time-based seed generator produces.
         *
         * Changing either constant reshuffles every in-flight run's offer. That is only visible to
         * a player mid-selection, but it is not free.
         */
        internal fun starterSeed(runSeed: Long): Long {
            var z = runSeed xor STARTER_SALT
            z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
            z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
            return z xor (z ushr 31)
        }

        private const val STARTER_SALT = 0x5354_4152_5445_5231L // "STARTER1"
    }
}
