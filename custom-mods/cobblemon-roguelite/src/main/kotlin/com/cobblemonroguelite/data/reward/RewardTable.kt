package com.cobblemonroguelite.data.reward

import net.minecraft.resources.ResourceLocation
import kotlin.random.Random

/**
 * One loaded reward table: what a run can hand out, and how that changes with depth.
 *
 * ### The two-stage draw, and why it is two stages
 *
 * A single flat weight list cannot express "rare things get likelier the deeper you are" without
 * every entry carrying its own curve, which puts the shape of the rarity ramp in a hundred places
 * and makes it impossible to retune. So a draw picks a **tier** first — by that tier's weight at the
 * current wave, read off [RewardTier.curve] — and then an **entry within it**, by flat weight. The
 * curve is the balance dial; entry weights say only how a tier's contents split among themselves,
 * which is what an author is actually able to reason about.
 *
 * ### Wave-band gating is separate from the curve, deliberately
 *
 * A tier curve says how *likely* a tier is at a wave. [RewardEntry.minWave]/[RewardEntry.maxWave]
 * say whether an entry *exists* there at all. They look interchangeable and are not: the curve is a
 * smooth balance knob that gets retuned, the band is a hard content rule ("no Master Ball before
 * wave 30") that should survive retuning. Folding one into the other would mean every rebalance of
 * the curve risks reopening content that was closed on purpose.
 *
 * ### No `replace` flag
 *
 * Datapack override rules already handle this — see [com.cobblemonroguelite.data.RogueliteDataRegistry].
 */
data class RewardTable(
    val id: ResourceLocation,
    val tiers: List<RewardTier>,
    val entries: List<RewardEntry>,
) {

    /**
     * Draw one reward for [wave], or null if nothing is eligible.
     *
     * Null is a real answer and callers must handle it: a table can legitimately have nothing at a
     * given depth (every entry gated out, or every tier's curve at zero). Treating null as an error
     * would make "no reward this wave" impossible to author.
     *
     * [random] is passed in rather than taken from a global source because run generation is seeded
     * (`RunState.seed`) — an unseeded draw here would make rewards reroll on resume, which is the
     * exploit §2.3 is holding the seed to prevent.
     */
    fun roll(wave: Int, random: Random, exclude: Set<String> = emptySet()): RewardEntry? {
        val eligible = entries.filter { it.appearsAt(wave) && it.id !in exclude }
        if (eligible.isEmpty()) return null
        val byTier = eligible.groupBy { it.tier }
        val tier = pick(tiers.filter { it.id in byTier }, random) { it.curve.weightAt(wave) } ?: return null
        return pick(byTier.getValue(tier.id), random) { it.weight }
    }

    /**
     * Draw up to [count] *distinct* rewards, for an offer the player picks from.
     *
     * Distinct by entry id rather than by reward content: two entries that both grant a Rare Candy
     * are two authored things and the author is entitled to have both appear. Returns fewer than
     * [count] — possibly none — when the table runs out of eligible entries, which is why the caller
     * gets a list rather than a fixed-size array.
     */
    fun rollOffer(wave: Int, count: Int, random: Random): List<RewardEntry> {
        val offer = mutableListOf<RewardEntry>()
        val taken = mutableSetOf<String>()
        while (offer.size < count) {
            val entry = roll(wave, random, taken) ?: break
            offer += entry
            taken += entry.id
        }
        return offer
    }

    private fun <E> pick(candidates: List<E>, random: Random, weightOf: (E) -> Double): E? {
        val total = candidates.sumOf(weightOf)
        if (total <= 0.0) return null
        var roll = random.nextDouble() * total
        for (candidate in candidates) {
            roll -= weightOf(candidate)
            if (roll <= 0.0) return candidate
        }
        // Floating-point summation can leave `roll` a hair above zero after the last subtraction.
        // Falling through to the final candidate is the correct rounding, not a fallback for a bug.
        return candidates.lastOrNull { weightOf(it) > 0.0 }
    }
}

/** A rarity band. [id] is table-local; the curve is how likely the band is at a given wave. */
data class RewardTier(
    val id: String,
    val curve: WeightCurve,
)

/**
 * How a tier's weight moves with wave depth, as breakpoints with straight lines between them.
 *
 * ### Why breakpoints and not a formula
 *
 * The obvious alternative is a parameterised curve — `base + linear * wave + quadratic * wave²`,
 * which is roughly what PokéRogue does for level scaling. It is smaller to write and it forces every
 * author into the shape *we* picked. Breakpoints impose no shape: a flat tier is one point, a ramp
 * is two, a tier that appears at wave 10, peaks at 30 and fades by 50 is four, and none of that
 * needed a new field. Since the whole point of §2.12 is that contents and curve are the owner's call
 * and are expected to change often, the format should not be quietly holding an opinion about what
 * curves are expressible.
 *
 * Outside the breakpoints the value is held flat at the nearest end, so a curve does not have to
 * cover every wave a run might reach — and, more usefully, a run that goes deeper than the author
 * planned does not fall off a cliff into weight zero.
 */
data class WeightCurve(val points: List<CurvePoint>) {

    fun weightAt(wave: Int): Double {
        val first = points.first()
        if (wave <= first.wave) return first.weight
        val last = points.last()
        if (wave >= last.wave) return last.weight
        for (index in 1 until points.size) {
            val upper = points[index]
            if (wave <= upper.wave) {
                val lower = points[index - 1]
                val progress = (wave - lower.wave).toDouble() / (upper.wave - lower.wave)
                return lower.weight + (upper.weight - lower.weight) * progress
            }
        }
        return last.weight
    }

    companion object {
        /** A tier that is equally likely at every depth. What a table gets when it declares no tiers. */
        fun flat(weight: Double = 1.0) = WeightCurve(listOf(CurvePoint(1, weight)))
    }
}

data class CurvePoint(val wave: Int, val weight: Double)

/**
 * One rewardable thing.
 *
 * @property id table-local and required. It names the entry in every log line the loader writes, and
 *   it is what [RewardTable.rollOffer] de-duplicates on — an auto-generated index would do neither
 *   job, since it changes the moment the author inserts a line above.
 * @property tier which [RewardTier] this belongs to. Tables that declare no tiers get a synthetic
 *   one, so this is never empty.
 * @property weight share of its tier. Positive; a zero-weight entry can never be drawn and is
 *   rejected at load rather than left as a line the author thinks is doing something.
 * @property minWave first wave this can appear on, inclusive.
 * @property maxWave last wave, inclusive, or null for no limit.
 */
data class RewardEntry(
    val id: String,
    val tier: String,
    val weight: Double,
    val minWave: Int,
    val maxWave: Int?,
    val reward: RunReward,
) {
    fun appearsAt(wave: Int): Boolean = wave >= minWave && (maxWave == null || wave <= maxWave)
}
