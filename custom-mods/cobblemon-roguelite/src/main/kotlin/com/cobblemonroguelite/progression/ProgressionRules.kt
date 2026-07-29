package com.cobblemonroguelite.progression

/**
 * What a catch is worth, and what candy buys — the two halves of §2.15's candy economy, kept apart
 * because they fail differently. Earning rules are ours to pick and have defensible defaults;
 * **prices are balance and are the server owner's call**, so [CandyPrices] defaults to the two
 * reference numbers §2.15 quotes and nothing else. There is deliberately no full price table here:
 * inventing one would make it look decided, and everyone downstream would treat it as such.
 *
 * ### Why these are values rather than constants
 *
 * The same argument [com.cobblemonroguelite.run.RunConfig] makes. PokéRogue's numbers are tuned for
 * PokéRogue's run economy — theirs assumes daily runs and an egg gacha — and ours are not that yet.
 * A constant would have to be edited and rebuilt to retune; a config value can be set at setup by
 * the host mod (see [ProgressionSettings]) and, later, loaded from a file with nothing moving.
 *
 * @property candyPerCatch one candy per catch of that species, §2.15's first source.
 * @property shinyCandy what a *shiny* catch is worth instead of [candyPerCatch], indexed by variant
 *   tier — PokéRogue's 5/10/20. Note Cobblemon has no variant tiers: `Pokemon.shiny` is a boolean, so
 *   on this server only index 0 is ever reached and a shiny is worth 5. The deeper entries are kept
 *   because the list is the natural shape for the rule and because an addon that adds variants should
 *   not need this class changed — but nothing today produces them, and a reader should not conclude
 *   from their presence that variants exist.
 *
 *   This *replaces* the per-catch candy rather than adding to it, per §2.15's phrasing ("theirs is
 *   5/10/20"), so a shiny is worth 5, not 6.
 * @property friendshipPerWaveCleared friendship each surviving party member earns for a cleared wave —
 *   §2.15's third source, "friendship thresholds earned in battle". A wave and not a turn, because a
 *   turn-granular hook would write to the progression store from the battle loop for no gameplay
 *   difference the player could perceive.
 * @property friendshipThreshold friendship that converts into one candy, with the remainder carried
 *   (see [SpeciesProgress.creditFriendship]). Flat by default. PokéRogue scales this by the species'
 *   starter cost — theirs runs 20 for a 1-cost up to 450 for a 9-cost, so cheap species candy up fast
 *   and expensive ones barely at all — which is the same balance judgement §2.13 says their cost
 *   table carries, and therefore the same thing we may not ship. Use [friendshipThresholdByCost] to
 *   supply it server-side.
 * @property friendshipThresholdByCost per-starter-cost overrides for [friendshipThreshold]. Empty by
 *   default, which is the flat rule.
 */
data class CandyRules(
    val candyPerCatch: Int = 1,
    val shinyCandy: List<Int> = listOf(5, 10, 20),
    val friendshipPerWaveCleared: Int = 1,
    val friendshipThreshold: Int = 150,
    val friendshipThresholdByCost: Map<Int, Int> = emptyMap(),
) {
    init {
        require(candyPerCatch >= 0) { "candyPerCatch must not be negative, was $candyPerCatch" }
        require(shinyCandy.isNotEmpty()) { "shinyCandy must have at least a tier-0 entry" }
        require(shinyCandy.all { it >= 0 }) { "shinyCandy must not be negative: $shinyCandy" }
        require(friendshipThreshold > 0) { "friendshipThreshold must be positive, was $friendshipThreshold" }
        require(friendshipThresholdByCost.values.all { it > 0 }) {
            "friendshipThresholdByCost values must be positive: $friendshipThresholdByCost"
        }
    }

    /**
     * Candy for one catch. [shinyVariant] is 0 for an ordinary shiny; -1 (or any negative) means not
     * shiny at all, which is how the Cobblemon adapter expresses a boolean as a tier.
     *
     * A variant past the end of [shinyCandy] takes the last entry rather than throwing: an addon
     * inventing a tier 4 should hand out the top prize, not crash a catch.
     */
    fun candyForCatch(shinyVariant: Int): Int {
        if (shinyVariant < 0) return candyPerCatch
        return shinyCandy[minOf(shinyVariant, shinyCandy.lastIndex)]
    }

    /** The friendship-to-candy threshold for a species that costs [starterCost] points (§2.13). */
    fun friendshipThresholdFor(starterCost: Int): Int =
        friendshipThresholdByCost[starterCost] ?: friendshipThreshold
}

/** What candy is spent on. §2.15: "passive unlocks, cost reductions, and eggs". */
enum class CandyPurchase { PASSIVE, COST_REDUCTION, EGG }

/**
 * Prices, and the only three numbers in this file that are pure balance.
 *
 * §2.15 quotes PokéRogue's reference points — roughly 40 candy for a 3-cost species' passive, cost
 * reductions at 20 and 50 — and the task that produced this file was explicit that they are
 * configurable rather than decided. So [passiveCandy] is a **flat** default rather than a curve, and
 * [eggCandy] is null: an unpriced purchase is refused ([SpendResult.NotPriced]) rather than given a
 * number nobody chose. Refusing is the honest failure — a wrong price that works reads as a decision
 * and quietly becomes one.
 *
 * @property passiveCandy flat price of a passive unlock when [passiveCandyByCost] has no entry.
 * @property passiveCandyByCost per-starter-cost prices. PokéRogue's real table scales with cost;
 *   populate this server-side to have it.
 * @property costReductionCandy the price of the first, second, … cost reduction. The list length is
 *   therefore also the cap on how many a species may buy, which is deliberate: one number cannot
 *   drift out of step with the other.
 * @property costReductionAmount how many starter points each reduction takes off (§2.13's budget).
 * @property minimumStarterCost the floor a reduced cost cannot go below. Zero would make a species
 *   free and a 10-point budget infinite.
 * @property eggCandy price of an egg, or null for "not priced yet".
 */
data class CandyPrices(
    val passiveCandy: Int = 40,
    val passiveCandyByCost: Map<Int, Int> = emptyMap(),
    val costReductionCandy: List<Int> = listOf(20, 50),
    val costReductionAmount: Int = 1,
    val minimumStarterCost: Int = 1,
    val eggCandy: Int? = null,
) {
    init {
        require(passiveCandy >= 0) { "passiveCandy must not be negative, was $passiveCandy" }
        require(costReductionCandy.all { it >= 0 }) { "costReductionCandy must not be negative" }
        require(costReductionAmount >= 0) { "costReductionAmount must not be negative" }
        require(minimumStarterCost >= 1) { "minimumStarterCost must be at least 1" }
        require(eggCandy == null || eggCandy >= 0) { "eggCandy must not be negative" }
    }

    /** How many cost reductions a species may ever buy. */
    val maxCostReductions: Int get() = costReductionCandy.size

    /**
     * What [purchase] costs for a species at [starterCost] that has already bought
     * [costReductionsOwned] reductions, or null when it is not for sale — either unpriced ([eggCandy])
     * or exhausted (every cost reduction bought). The caller distinguishes those two; see
     * [SpeciesProgress.buy].
     */
    fun priceOf(purchase: CandyPurchase, starterCost: Int, costReductionsOwned: Int): Int? =
        when (purchase) {
            CandyPurchase.PASSIVE -> passiveCandyByCost[starterCost] ?: passiveCandy
            CandyPurchase.COST_REDUCTION -> costReductionCandy.getOrNull(costReductionsOwned)
            CandyPurchase.EGG -> eggCandy
        }

    /**
     * §2.13's budget cost of a species after the reductions its owner has bought.
     *
     * This is the whole outward-facing point of cost reductions, and it is arithmetic rather than
     * policy so that the starter side can call it without knowing anything about candy.
     */
    fun effectiveStarterCost(baseCost: Int, costReductionsOwned: Int): Int =
        maxOf(minimumStarterCost, baseCost - costReductionsOwned * costReductionAmount)
}

/**
 * The live progression configuration, in the shape [com.cobblemonroguelite.run.RunSettings] already
 * established: `@Volatile`, set once at setup by whatever mod is hosting us, resettable for tests.
 *
 * Kept separate from `RunSettings` rather than folded into `RunConfig`, because the lifetimes differ.
 * `RunConfig` tunes a run; this tunes what *outlives* runs. An operator who retunes payouts mid-season
 * changes the next run; an operator who retunes candy prices changes what everyone's stored balance is
 * worth, which is a different conversation and should not be reachable by editing the run table.
 */
object ProgressionSettings {

    @Volatile
    var candy: CandyRules = CandyRules()
        private set

    @Volatile
    var prices: CandyPrices = CandyPrices()
        private set

    fun set(candyRules: CandyRules = candy, candyPrices: CandyPrices = prices) {
        candy = candyRules
        prices = candyPrices
    }

    /** Restore the shipped defaults. For tests and for unloading a server-side integration. */
    fun reset() = set(CandyRules(), CandyPrices())
}
