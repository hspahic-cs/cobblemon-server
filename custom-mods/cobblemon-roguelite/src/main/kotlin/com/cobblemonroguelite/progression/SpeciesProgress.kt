package com.cobblemonroguelite.progression

import net.minecraft.nbt.CompoundTag

/**
 * Everything one player has earned for one species, and the only place §2.15's and §2.17's
 * arithmetic lives.
 *
 * ### Immutable, and why that is the thread-safety answer
 *
 * [com.cobblemonroguelite.run.RunState] is mutable and guards its party with a monitor, because it
 * holds live Pokémon that a battle mutates in place. Nothing here is live: a progression record is
 * six numbers. So this is an immutable value and every change produces a new one, which lets
 * [PlayerProgression] apply changes with `ConcurrentHashMap.compute` — atomic per species, with no
 * lock of ours to take in the wrong order.
 *
 * That matters because the writes genuinely do not share a thread. A catch arrives on the server
 * thread (see [com.cobblemonroguelite.battle.RunCapture] on why the capture path is inline), while
 * friendship arrives from the battle-resolution path, which Cobblemon dispatches off-thread. A
 * read-modify-write of a mutable record across those two would lose candy occasionally and silently,
 * and "occasionally and silently" is the failure mode nobody ever reports as a bug.
 *
 * ### Why this is not derived from anything Cobblemon stores
 *
 * It cannot be. [com.cobblemonroguelite.battle.RunDexGuard] vetoes the Pokédex write for anything
 * that happens inside a run — it has to, or a run would unlock its own starters (§2.15) — so the dex
 * has no record of an in-run catch at all. And Cobblemon's Pokédex stores forms, genders and shiny
 * states, never IVs, so even without the veto it could not carry §2.17's floor. This store is the
 * only witness that an in-run catch happened, which is exactly what makes §1.1's restatement real:
 * the Pokémon does not leave the run, the *fact that it was caught* does.
 *
 * @property candy unspent candy. A balance, not a lifetime total — spending lowers it.
 * @property floor §2.17's IV floor, [IvFloor.BASE] until a run catch raises it.
 * @property passiveUnlocked whether this species' passive ability has been bought (§2.15).
 * @property costReductions how many starter-cost reductions have been bought. Capped by the length of
 *   [CandyPrices.costReductionCandy].
 * @property friendship friendship banked toward the next candy, i.e. the remainder below the
 *   threshold. Persisted rather than recomputed because there is nothing to recompute it from —
 *   the Pokémon that earned it died with its run.
 */
data class SpeciesProgress(
    val candy: Int = 0,
    val floor: IvFloor = IvFloor.BASE,
    val passiveUnlocked: Boolean = false,
    val costReductions: Int = 0,
    val friendship: Int = 0,
) {

    /** True when nothing has been earned or bought, i.e. storing this row would say nothing. */
    fun isEmpty(): Boolean = this == EMPTY

    /**
     * Credit one catch: candy for it, and its IVs into the floor.
     *
     * **Deliberately independent of the run's outcome.** Progression is earned by playing, not by
     * winning (§1.1's "progression is not sealed"), so this is applied at the moment of the catch and
     * nothing about a later wipe reaches back for it. The alternative — bank it and award at run end —
     * would make a wipe on wave 199 erase two hundred waves of catching, which is the single most
     * demoralising thing a roguelite can do and is not what PokéRogue does either.
     *
     * @param shinyVariant negative for not shiny; see [CandyRules.candyForCatch].
     */
    fun creditCatch(
        caughtIvs: IvFloor,
        shinyVariant: Int,
        rules: CandyRules = ProgressionSettings.candy,
    ): SpeciesProgress = copy(
        candy = candy + rules.candyForCatch(shinyVariant),
        floor = floor.raisedBy(caughtIvs),
    )

    /**
     * Add friendship earned in battle, converting each whole threshold into candy and **carrying the
     * remainder**.
     *
     * Carrying rather than resetting to zero is the part worth stating: a reset would make a single
     * large grant (a config change, a boss wave worth several waves' friendship) discard everything
     * above the line, so two grants of 100 against a threshold of 150 would be worth one candy and
     * then nothing — and the player would have no way to see why. The loop also handles a grant
     * several thresholds wide, which a single `if` would silently truncate.
     */
    fun creditFriendship(
        gained: Int,
        rules: CandyRules = ProgressionSettings.candy,
        starterCost: Int = UNKNOWN_STARTER_COST,
    ): SpeciesProgress {
        if (gained <= 0) return this
        val threshold = rules.friendshipThresholdFor(starterCost)
        val total = friendship + gained
        return copy(candy = candy + total / threshold, friendship = total % threshold)
    }

    /**
     * Raise the floor without granting candy. For an in-run catch that should not pay twice — and,
     * more usefully, for repair tooling. Separate from [creditCatch] so that neither can be done by
     * accident while meaning the other.
     */
    fun raiseFloor(caughtIvs: IvFloor): SpeciesProgress = copy(floor = floor.raisedBy(caughtIvs))

    /**
     * Attempt a purchase. Pure: the result carries the new record and the caller decides whether to
     * store it, which is what lets a UI quote a price without committing to it.
     *
     * Every refusal is its own case rather than a null, because the four of them need four different
     * things said to the player — "you need 12 more", "you already own that", "there are no more to
     * buy" and "the server has not priced eggs" are not interchangeable, and collapsing them is how a
     * button ends up doing nothing with no explanation.
     */
    fun buy(
        purchase: CandyPurchase,
        starterCost: Int = UNKNOWN_STARTER_COST,
        prices: CandyPrices = ProgressionSettings.prices,
    ): SpendResult {
        if (purchase == CandyPurchase.PASSIVE && passiveUnlocked) return SpendResult.AlreadyOwned
        val price = prices.priceOf(purchase, starterCost, costReductions)
            ?: return when (purchase) {
                // A null price means two different things depending on the purchase, and the two are
                // not the same failure: cost reductions run out (the player bought them all), eggs
                // are simply not priced (the operator has not decided). See [CandyPrices.eggCandy].
                CandyPurchase.COST_REDUCTION -> SpendResult.SoldOut
                else -> SpendResult.NotPriced
            }
        if (candy < price) return SpendResult.NotEnoughCandy(have = candy, need = price)

        val bought = when (purchase) {
            CandyPurchase.PASSIVE -> copy(candy = candy - price, passiveUnlocked = true)
            CandyPurchase.COST_REDUCTION -> copy(candy = candy - price, costReductions = costReductions + 1)
            // An egg leaves no mark on this record on purpose: what an egg *is* belongs to whatever
            // grants it, and a counter here would be a second copy of a fact that store already owns.
            CandyPurchase.EGG -> copy(candy = candy - price)
        }
        return SpendResult.Ok(bought, spent = price)
    }

    /** §2.13's cost of starting with this species, after the reductions bought for it. */
    fun effectiveStarterCost(baseCost: Int, prices: CandyPrices = ProgressionSettings.prices): Int =
        prices.effectiveStarterCost(baseCost, costReductions)

    /**
     * Written sparsely: only fields that differ from the default are stored. Two hundred species with
     * three fields each would otherwise be most of the file, and the omissions read back identically
     * because [fromNbt] defaults to the same values.
     */
    fun toNbt(): CompoundTag = CompoundTag().apply {
        if (candy != 0) putInt(CANDY_KEY, candy)
        if (!floor.isBase()) put(FLOOR_KEY, floor.toNbt())
        if (passiveUnlocked) putBoolean(PASSIVE_KEY, true)
        if (costReductions != 0) putInt(REDUCTIONS_KEY, costReductions)
        if (friendship != 0) putInt(FRIENDSHIP_KEY, friendship)
    }

    companion object {

        /**
         * The record of a species nobody has touched. Handed out by [PlayerProgression.of] instead of
         * null so that every reader gets base-10 IVs and zero candy without writing the branch — and,
         * more to the point, without a *missing* row and an *empty* row ever behaving differently.
         */
        val EMPTY = SpeciesProgress()

        /**
         * What [buy] and [creditFriendship] use when the caller does not know the species' §2.13
         * starter cost — which is the normal case in this module, since the cost table is the starter
         * side's data (and, per §2.13, is PokéRogue's data and stays server-side).
         *
         * It resolves to the flat default price and the flat friendship threshold, i.e. the per-cost
         * maps are simply not consulted. That is the correct degradation: a cost we do not know cannot
         * be looked up, and refusing the purchase would make every unpriced-by-cost server unable to
         * spend candy at all.
         */
        const val UNKNOWN_STARTER_COST = -1

        private const val CANDY_KEY = "candy"
        private const val FLOOR_KEY = "floor"
        private const val PASSIVE_KEY = "passive"
        private const val REDUCTIONS_KEY = "costReductions"
        private const val FRIENDSHIP_KEY = "friendship"

        /**
         * Read a record back. Missing keys are defaults, which is what [toNbt]'s sparseness relies on.
         *
         * A damaged IV floor falls back to [IvFloor.BASE] rather than failing the whole record: candy
         * and unlocks are the part a player would notice losing, and dropping them over a truncated
         * int array would turn a small corruption into a large one.
         */
        fun fromNbt(tag: CompoundTag): SpeciesProgress = SpeciesProgress(
            candy = tag.getInt(CANDY_KEY),
            floor = if (tag.contains(FLOOR_KEY)) {
                IvFloor.fromNbt(tag.getCompound(FLOOR_KEY)) ?: IvFloor.BASE
            } else {
                IvFloor.BASE
            },
            passiveUnlocked = tag.getBoolean(PASSIVE_KEY),
            costReductions = tag.getInt(REDUCTIONS_KEY),
            friendship = tag.getInt(FRIENDSHIP_KEY),
        )
    }
}

/** The outcome of a [SpeciesProgress.buy]. See there for why the refusals are not one case. */
sealed interface SpendResult {

    /** Bought. [progress] is the record to store; storing it is the caller's job. */
    data class Ok(val progress: SpeciesProgress, val spent: Int) : SpendResult

    data class NotEnoughCandy(val have: Int, val need: Int) : SpendResult

    /** A passive that is already unlocked. Buying it twice would be a pure candy sink for nothing. */
    data object AlreadyOwned : SpendResult

    /** Every cost reduction for this species has been bought. */
    data object SoldOut : SpendResult

    /** The server has not given this purchase a price — see [CandyPrices]. Not the player's fault. */
    data object NotPriced : SpendResult
}
