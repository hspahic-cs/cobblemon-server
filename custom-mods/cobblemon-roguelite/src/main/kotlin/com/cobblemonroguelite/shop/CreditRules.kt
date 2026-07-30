package com.cobblemonroguelite.shop

import com.cobblemonroguelite.integration.RunOpponent

/**
 * What a cleared wave pays, and what the between-wave shop charges for the privilege of caring.
 *
 * ### Why credits are not the server's currency, restated where it is enforced
 *
 * §2.18 charges real currency to *enter* a run and §2.20 pays out in things that are not money, so
 * the mode is a sink. Credits exist inside that sink: they are earned per wave, spent between waves,
 * and [com.cobblemonroguelite.run.RunState.credits] is discarded when the run ends. Nothing here
 * converts in either direction, and nothing should — the moment credits become sellable the entry
 * fee stops being a cost and the roguelite becomes the best money loop on the server, which is the
 * exact failure §2.20 was written to avoid.
 *
 * That is also why this file has no notion of a balance ceiling or of banking between runs. Hoarding
 * is self-punishing: an unspent credit is a shop item the player did not take into the next fight.
 *
 * ### The shape of the earn curve, and the one thing it must not be
 *
 * Payment is **base-by-kind plus a per-wave slope**, both integers, and the slope is applied to the
 * wave rather than to the number of waves cleared. Those are the same number today and would diverge
 * the moment §2.10's disconnect penalty sends a player back to an earlier wave: paying by waves
 * cleared would let a player farm the penalty for income. Paying by wave number makes a re-fought
 * wave pay exactly what it paid the first time, which is the property that closes the loop.
 *
 * Payment is ordered wild < trainer < rival < boss, because that ordering is what makes the shop feel
 * like a reward for the hard waves rather than a tax on the easy ones. The actual
 * numbers are a balance decision and belong to the server, which is why they are settings and not
 * constants — see [ShopSettings].
 */
data class CreditRules(
    /** Paid for clearing a wild wave. The floor of the curve; 160 of a 200-wave run are these. */
    val wildBase: Int = 10,
    /** Paid for clearing a plain trainer wave. */
    val trainerBase: Int = 25,
    /**
     * Paid for clearing a rival wave (§2.36) — between a trainer and a boss, and that placement is
     * the point rather than a hedge.
     *
     * A rival takes neither the boss level multiplier nor boss shields (§2.32): it is hard because of
     * its team, which is the one lever it has that a plain trainer does not. Paying it boss rates
     * would reward it for pressure it does not apply; paying it trainer rates would ignore that a
     * player meets it six times across a run at a strength no band trainer reaches.
     */
    val rivalBase: Int = 40,
    /** Paid for clearing a boss wave. */
    val bossBase: Int = 60,
    /**
     * Added per wave of depth, in hundredths of a credit, so the ramp can be gentler than one credit
     * per wave without turning the whole calculation into floating point.
     *
     * Hundredths and not a `Double` because credits are counted, compared and persisted as `Int`, and
     * a rate expressed as `0.35` invites the arithmetic to be done in `Double` somewhere else and
     * rounded differently there. An integer rate keeps one rounding site, which is [creditsFor].
     */
    val perWaveHundredths: Int = 35,
) {

    /**
     * What clearing [wave] against [kind] pays.
     *
     * Coerced at zero rather than trusted: a server that sets a negative base or slope is
     * misconfigured, and the failure should be "this wave paid nothing" rather than a run whose
     * credit total goes backwards and underflows the shop's affordability checks.
     */
    fun creditsFor(wave: Int, kind: RunOpponent): Int {
        val base = when (kind) {
            RunOpponent.WILD -> wildBase
            RunOpponent.TRAINER -> trainerBase
            RunOpponent.RIVAL -> rivalBase
            RunOpponent.BOSS -> bossBase
        }
        val depth = (wave.coerceAtLeast(1) - 1) * perWaveHundredths / HUNDREDTHS
        return (base + depth).coerceAtLeast(0)
    }

    private companion object {
        const val HUNDREDTHS = 100
    }
}

/**
 * The dials [CreditRules] and the shop read, held the way this module holds every other
 * host-supplied setting: `@Volatile`, set once at setup by whatever mod is hosting us, resettable in
 * tests. Same reasoning as [com.cobblemonroguelite.progression.ProgressionSettings].
 */
object ShopSettings {

    /** How wave clears pay. */
    @Volatile
    var credits: CreditRules = CreditRules()

    /** How the between-wave shop is stocked and priced. */
    @Volatile
    var shop: ShopRules = ShopRules()

    /** For tests, and for a host that wants to re-apply config on reload. */
    fun reset() {
        credits = CreditRules()
        shop = ShopRules()
    }
}

/**
 * How the between-wave shop behaves, independent of what is in it.
 *
 * What is *in* it is a datapack table ([com.cobblemonroguelite.data.shop.ShopTable]) because that is
 * content; how many slots it offers and whether a reroll is possible is mechanism, and lives here.
 */
data class ShopRules(
    /**
     * How many items are offered between waves.
     *
     * A fixed number and not "everything affordable", because the shop is a *choice* — PokéRogue's
     * between-wave screen works because you cannot have all of it. An offer that grows with the
     * player's balance would turn the late run into a shopping list.
     */
    val offerSlots: Int = 4,
    /**
     * What a reroll of the offer costs, or null to disable rerolling.
     *
     * Null by default. A reroll is a real mechanic in PokéRogue but it interacts with the seeded
     * offer (§2.16): the draw is a function of (run seed, wave), so a reroll needs to be part of that
     * function's input or the offer stops being reproducible on a resumed run. [ShopOffer] takes a
     * reroll count for exactly that reason, and this price is what makes it reachable.
     */
    val rerollCost: Int? = null,
    /**
     * Multiplier applied to a reroll's cost per reroll already taken this wave, in hundredths.
     * 150 means each reroll costs half again as much as the last, which is what stops a large balance
     * from simply buying the whole table.
     */
    val rerollGrowthHundredths: Int = 150,
) {

    /** What the [taken]-th reroll of this wave costs, or null if rerolling is disabled. */
    fun rerollPrice(taken: Int): Int? {
        val base = rerollCost ?: return null
        var price = base.toLong()
        repeat(taken.coerceAtLeast(0)) {
            price = price * rerollGrowthHundredths / 100
            // A growth multiplier of 100 or less would make rerolls free-or-cheaper forever, and an
            // unbounded price would overflow. Clamping keeps a misconfigured multiplier expensive
            // rather than exploitable.
            if (price > MAX_PRICE) return MAX_PRICE.toInt()
        }
        return price.coerceAtLeast(base.toLong()).toInt()
    }

    private companion object {
        const val MAX_PRICE = 1_000_000L
    }
}
