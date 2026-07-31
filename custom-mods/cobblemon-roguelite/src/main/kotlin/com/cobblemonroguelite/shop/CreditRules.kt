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
 * like a reward for the hard waves rather than a tax on the easy ones — and wild is now zero, so the
 * ordering starts at nothing. The actual numbers are a balance decision and belong to the server,
 * which is why they are settings and not constants — see [ShopSettings].
 */
data class CreditRules(
    /**
     * Wild waves pay **nothing** — see [creditsFor]. Zero rather than a small number, because a
     * trickle across the 160 wild waves of a run is most of its money arriving from the waves that
     * were meant to be the backdrop.
     */
    val wildMultiplier: Double = 0.0,
    /** A plain trainer wave pays the curve exactly. Every other multiplier is relative to this. */
    val trainerMultiplier: Double = 1.0,
    /**
     * Paid for clearing a rival wave (§2.36) — between a trainer and a boss, and that placement is
     * the point rather than a hedge.
     *
     * A rival takes neither the boss level multiplier nor boss shields (§2.32): it is hard because of
     * its team, which is the one lever it has that a plain trainer does not. Paying it boss rates
     * would reward it for pressure it does not apply; paying it trainer rates would ignore that a
     * player meets it six times across a run at a strength no band trainer reaches.
     */
    val rivalMultiplier: Double = 1.5,
    /** Paid for clearing a boss wave. */
    val bossMultiplier: Double = 2.0,

    /** The shared curve. Same instance the shop prices from — see [WaveMoneyCurve]. */
    val curve: WaveMoneyCurve = WaveMoneyCurve(),
) {

    /**
     * What clearing [wave] against [kind] pays.
     *
     * Coerced at zero rather than trusted: a server that sets a negative base or slope is
     * misconfigured, and the failure should be "this wave paid nothing" rather than a run whose
     * credit total goes backwards and underflows the shop's affordability checks.
     */
    /**
     * What clearing [wave] against [kind] pays.
     *
     * ### The curve, not a base plus a slope
     *
     * This used to be `base + wave × rate` per opponent kind, which drifts against a shop priced off
     * anything else. It now reads [WaveMoneyCurve] — the same curve the shop prices from — so what a
     * wave pays and what it charges move together by construction. See [WaveMoneyCurve] and
     * `docs/roguelite-economy-reference.md`.
     *
     * ### Wild waves pay nothing, and that is a deliberate divergence
     *
     * PokéRogue pays on every wave. Ours pays only on trainer, rival and boss waves, so that meeting a
     * trainer is a thing that happens *to* the run rather than another wave with more HP. Wild waves
     * are most of a run (§2.14) and they still pay in rewards — the free pick after every wave —
     * so this moves where money comes from rather than how much of it there is.
     *
     * Coerced at zero rather than trusted: a server that sets a negative multiplier is misconfigured,
     * and the failure should be "this wave paid nothing" rather than a credit total that goes backwards
     * and underflows the shop's affordability checks.
     */
    fun creditsFor(wave: Int, kind: RunOpponent): Int {
        val multiplier = when (kind) {
            RunOpponent.WILD -> wildMultiplier
            RunOpponent.TRAINER -> trainerMultiplier
            RunOpponent.RIVAL -> rivalMultiplier
            RunOpponent.BOSS -> bossMultiplier
        }
        if (multiplier <= 0.0) return 0
        return curve.amountAt(wave, multiplier).coerceAtLeast(0)
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
     * How many **free** reward options are offered, of which the player takes one ([RewardOffer]).
     *
     * Three, matching PokéRogue. The number is the mechanic: three doors and one key is a choice you
     * feel, and the tension is in the two you give up. Raising it dilutes that — at six options one of
     * them is usually obviously best — and lowering it to one removes the decision entirely.
     */
    val rewardOptions: Int = 3,
    /**
     * How many **paid** consumables the shop row holds, as `(min_wave, slots)` steps ([ShopStock]).
     *
     * The row grows with depth: PokéRogue shows three consumables early and five by wave 21. Growth is
     * a step list rather than a formula because it is a content shape, not a curve — an operator adding
     * a fourth consumable wants to say *when* it appears, not solve for a rate.
     *
     * Highest passed `min_wave` wins, so the order written here does not decide anything.
     */
    val shopSlots: List<Pair<Int, Int>> = listOf(1 to 3, 20 to 4, 40 to 5),
    /**
     * What the first reroll of the free options costs at wave 1, or null to disable rerolling.
     *
     * Null by default, because a price is a balance decision and a mode that ships with rerolling
     * priced by guesswork is worse than one where it is switched off until somebody chooses a number.
     */
    val rerollCost: Int? = null,
    /**
     * Multiplier applied per reroll already taken *this wave*, in hundredths. 150 means each reroll
     * costs half again as much as the last, which is what stops a large balance buying the whole table.
     */
    val rerollGrowthHundredths: Int = 150,
    /**
     * Added to the reroll price per wave of depth, in hundredths of a credit.
     *
     * Their reroll is ₽250 early and ₽750 by wave 21, so it scales with depth as well as with repeats.
     * Without this the reroll becomes free in real terms as earnings grow — the same failure
     * [ShopEntry.priceCurve] exists to prevent on the paid row.
     */
    val rerollPerWaveHundredths: Int = 2500,

    /**
     * The shop is shut on every wave that is a multiple of this. 10 is §2.19's boss cadence and
     * PokéRogue's own rule; 0 turns it off and keeps the shop open throughout.
     */
    val closedEvery: Int = 10,
) {

    /**
     * Whether the shop is shut at [wave] entirely.
     *
     * Every [closedEvery]th wave, which is the boss cadence. See [ShopStock.stockAt] for why.
     * Zero disables the rule rather than closing the shop on every wave, which is the reading a
     * server that wanted it always open would expect.
     */
    fun closedAt(wave: Int): Boolean = closedEvery > 0 && wave > 0 && wave % closedEvery == 0

    /** How many paid consumables are stocked at [wave]. */
    fun shopSlotsAt(wave: Int): Int =
        shopSlots.filter { it.first <= wave }.maxByOrNull { it.first }?.second ?: 0

    /**
     * What the [taken]-th reroll at [wave] costs, or null if rerolling is disabled.
     *
     * Depth is applied to the base *before* the repeat multiplier, so a second reroll deep in a run is
     * dearer than a second reroll early — the two scalings compound, which is what keeps rerolling from
     * becoming the default action once credits are plentiful.
     */
    fun rerollPrice(taken: Int, wave: Int = 1): Int? {
        val base = rerollCost ?: return null
        val withDepth = base + (wave.coerceAtLeast(1) - 1).toLong() * rerollPerWaveHundredths / HUNDREDTHS
        var price = withDepth.coerceAtLeast(base.toLong())
        repeat(taken.coerceAtLeast(0)) {
            price = price * rerollGrowthHundredths / HUNDREDTHS
            // A growth multiplier of 100 or less would make rerolls free-or-cheaper forever, and an
            // unbounded price would overflow. Clamping keeps a misconfigured multiplier expensive
            // rather than exploitable.
            if (price > MAX_PRICE) return MAX_PRICE.toInt()
        }
        return price.coerceAtLeast(withDepth).toInt()
    }

    private companion object {
        const val MAX_PRICE = 1_000_000L
        const val HUNDREDTHS = 100L
    }
}
