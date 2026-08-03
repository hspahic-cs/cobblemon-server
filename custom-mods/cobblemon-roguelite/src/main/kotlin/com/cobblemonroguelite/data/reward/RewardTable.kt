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
     *
     * [party] feeds the [RewardEntry.scaledBy] conditions and may be null, which means "no party
     * scaling" — every conditional entry keeps its written weight. Callers that *have* a party must
     * pass it: a null here is what makes a full-health party see Revives, which is exactly the
     * failure the condition mechanism exists to prevent.
     */
    fun roll(wave: Int, random: Random, exclude: Set<String> = emptySet(), party: PartyState? = null): RewardEntry? {
        // Filtered on the *effective* weight, not just the wave band: an entry scaled to zero (a
        // Revive with nobody fainted) must not exist for this draw at all. Leaving it in would let
        // the tier pick land on a tier whose every entry is currently zero, which turns "no Revives
        // today" into "one of your three options is silently missing".
        val eligible = entries.filter { it.appearsAt(wave) && it.id !in exclude && it.weightAt(party) > 0.0 }
        if (eligible.isEmpty()) return null
        val byTier = eligible.groupBy { it.tier }
        val tier = pick(tiers.filter { it.id in byTier }, random) { it.curve.weightAt(wave) } ?: return null
        return pick(byTier.getValue(tier.id), random) { it.weightAt(party) }
    }

    /**
     * Draw up to [count] *distinct* rewards, for an offer the player picks from.
     *
     * Distinct by entry id rather than by reward content: two entries that both grant a Rare Candy
     * are two authored things and the author is entitled to have both appear. Returns fewer than
     * [count] — possibly none — when the table runs out of eligible entries, which is why the caller
     * gets a list rather than a fixed-size array.
     */
    fun rollOffer(wave: Int, count: Int, random: Random, party: PartyState? = null): List<RewardEntry> {
        val offer = mutableListOf<RewardEntry>()
        val taken = mutableSetOf<String>()
        while (offer.size < count) {
            val entry = roll(wave, random, taken, party) ?: break
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
 * What a reward roll is allowed to know about the run party.
 *
 * A deliberately tiny summary rather than the party itself, so the reward layer stays a pure data
 * module a test can drive with two numbers — the same reasoning that keeps game objects out of
 * [com.cobblemonroguelite.data.trainer.GeneratedMember]. Computed by the shop layer from the real
 * party at the moment of the roll.
 *
 * @property missingHealth sum over the party of each member's missing-HP fraction, so 0.0 is a
 *   full-health party and a party of six at half health is 3.0.
 * @property fainted how many members are currently fainted.
 */
data class PartyState(val missingHealth: Double, val fainted: Int) {
    init {
        require(missingHealth >= 0.0) { "missingHealth is a sum of fractions, got $missingHealth" }
        require(fainted >= 0) { "fainted is a count, got $fainted" }
    }
}

/**
 * The party conditions an entry's weight can scale by — PokéRogue's healing-item weight *functions*
 * (`(party: Pokemon[]) => …` in their `init-modifier-pools.ts`), reshaped into a closed set.
 *
 * Reshaped, not ported: their every healing item carries its own bespoke piecewise function, and a
 * datapack cannot ship code (`RunReward` is sealed for the same §2.12 reason). What survives is the
 * property that made the design worth stealing — **a full-health party is not offered potions, a
 * party with no faints is not offered revives** — as the written weight multiplied by a linear
 * factor of the party's actual state. The per-item piecewise shapes are the fidelity paid for
 * keeping content out of code.
 */
enum class PartyCondition {
    /** Weight × the party's total missing-HP fraction ([PartyState.missingHealth]). Zero when whole. */
    INJURED,

    /** Weight × the fainted count. Zero while everyone stands. */
    FAINTED;

    fun factor(party: PartyState): Double = when (this) {
        INJURED -> party.missingHealth
        FAINTED -> party.fainted.toDouble()
    }
}

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
 * @property scaledBy scales [weight] by the party's state at roll time, or null for the written
 *   weight always. One consequence is deliberate and worth knowing: a scaled offer is a function of
 *   the party *now*, so drinking a Potion between opening the screen and picking can change what is
 *   on offer — [com.cobblemonroguelite.shop.RewardOffer.take] recomputes and refuses a vanished
 *   entry through its existing stale-screen guard, and the player sees a fresh offer that no longer
 *   wastes a slot on healing they just did.
 */
data class RewardEntry(
    val id: String,
    val tier: String,
    val weight: Double,
    val minWave: Int,
    val maxWave: Int?,
    val reward: RunReward,
    val scaledBy: PartyCondition? = null,
) {
    fun appearsAt(wave: Int): Boolean = wave >= minWave && (maxWave == null || wave <= maxWave)

    /** The weight this draw actually uses: the written weight, scaled when a condition applies. */
    fun weightAt(party: PartyState?): Double {
        val condition = scaledBy ?: return weight
        if (party == null) return weight
        return weight * condition.factor(party)
    }
}
