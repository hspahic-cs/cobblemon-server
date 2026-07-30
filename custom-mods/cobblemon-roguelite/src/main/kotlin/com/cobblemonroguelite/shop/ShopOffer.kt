package com.cobblemonroguelite.shop

import com.cobblemonroguelite.data.shop.ShopEntry
import com.cobblemonroguelite.data.shop.ShopTable
import kotlin.random.Random

/**
 * The pure half of the between-wave shop: what is on offer, what it costs, and whether a purchase is
 * allowed. Nothing here touches a `Pokemon`, a `MinecraftServer` or a store, which is what makes the
 * interesting cases testable — the same split [com.cobblemonroguelite.progression.RunProgression] and
 * [com.cobblemonroguelite.starter.HiddenAbilityUnlock] document making, for the same reason.
 *
 * ### The offer is derived, never stored
 *
 * A shop offer is a function of `(run seed, wave, rerolls taken)` and is recomputed whenever it is
 * needed. It would be easy to persist the drawn list into [com.cobblemonroguelite.run.RunState]
 * instead, and that is the trap: the offer would then be a second source of truth about a wave that
 * the seed already determines, and the two would disagree the first time a datapack was edited
 * mid-run. Deriving it means an operator who adds a shop entry changes what unopened shops contain —
 * which is the same already-documented consequence that editing a trainer band has (see
 * `trainer_rosters/example.json`), and is strictly better than a persisted list that can drift from
 * the table it was drawn from.
 *
 * §2.16 is satisfied by the same property: a paused run resumed a week later recomputes the identical
 * offer, because none of the three inputs changed.
 *
 * ### Why the reroll count is an input and not a mutation
 *
 * Rerolling has to change the offer without breaking reproducibility, so it is a *counter* that feeds
 * the stream rather than a re-draw from a live `Random`. Reroll three, log out, log in: still offer
 * three. A live sequence would have advanced past a state nothing persisted.
 */
object ShopOffer {

    /**
     * A salt, mixed into the stream so the shop draw cannot correlate with any other seeded decision
     * at the same wave.
     *
     * §2.16 makes salts part of the save format: appended, never renumbered, because changing one
     * silently re-rolls every existing run's answers for that decision. This is the shop's.
     */
    private const val SALT = 0x5_0FFE4_0DDL

    /**
     * The offer for [wave], as drawn from [table] with [rerolls] already taken.
     *
     * Deterministic in all three inputs, which is the whole contract.
     */
    fun offerFor(table: ShopTable, wave: Int, seed: Long, rerolls: Int = 0, slots: Int? = null): List<ShopEntry> {
        val count = slots ?: ShopSettings.shop.offerSlots
        return table.rollOffer(wave, count, streamFor(wave, seed, rerolls))
    }

    /**
     * The seeded stream for one (wave, rerolls) pair.
     *
     * SplitMix64-style mixing of the three inputs rather than `Random(seed + wave)`: adjacent waves
     * with an additive seed produce adjacent — and for small tables, frequently identical — first
     * draws, which would make wave 40 and wave 41 offer the same four items. Multiplying by large odd
     * constants decorrelates them.
     */
    private fun streamFor(wave: Int, seed: Long, rerolls: Int): Random {
        var z = seed xor SALT
        z += wave.toLong() * -0x61c8864680b583ebL
        z += rerolls.toLong() * 0x2545f4914f6cdd1dL
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a2bL
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        return Random(z xor (z ushr 31))
    }

    /**
     * Whether [entry] can be bought at [wave] with [credits] on hand.
     *
     * Takes the entry the *caller resolved from the current offer*, so this cannot be used to buy
     * something that is in the table but not on sale. That check belongs to the caller because only
     * it knows the reroll count; see [purchase].
     */
    fun priceOf(entry: ShopEntry, wave: Int): Int = entry.priceAt(wave)

    /**
     * Resolve and price a purchase of [entryId] against the offer actually on sale.
     *
     * The offer is recomputed here rather than accepted from the caller. That is the guard that
     * matters: a client (or a replayed command) naming an id that is in the table but not in this
     * wave's offer must be refused, and the only way to know is to draw the offer again. Since the
     * draw is deterministic, recomputing is free and cannot disagree with what the player was shown.
     */
    fun purchase(
        table: ShopTable,
        wave: Int,
        seed: Long,
        rerolls: Int,
        credits: Int,
        entryId: String,
    ): PurchaseResult {
        val offer = offerFor(table, wave, seed, rerolls)
        val entry = offer.firstOrNull { it.id == entryId }
            ?: return if (table.entry(entryId) == null) {
                PurchaseResult.NoSuchEntry(entryId)
            } else {
                // In the table but not on sale. A distinct case because it is the one a stale screen
                // produces, and telling the player "not on sale" rather than "no such item" is the
                // difference between a confusing bug report and an obvious refresh.
                PurchaseResult.NotOffered(entryId)
            }
        val price = entry.priceAt(wave)
        if (credits < price) return PurchaseResult.NotEnoughCredits(have = credits, need = price)
        return PurchaseResult.Ok(entry = entry, price = price, remaining = credits - price)
    }

    /**
     * Resolve and price a reroll of this wave's offer.
     *
     * Separate from [purchase] because it spends credits without granting a reward, and folding it in
     * would make [PurchaseResult.Ok] carry a nullable entry — which every call site would then have
     * to check, for the sake of one shared branch.
     */
    fun reroll(credits: Int, rerollsTaken: Int): RerollResult {
        val price = ShopSettings.shop.rerollPrice(rerollsTaken) ?: return RerollResult.Disabled
        if (credits < price) return RerollResult.NotEnoughCredits(have = credits, need = price)
        return RerollResult.Ok(price = price, remaining = credits - price)
    }
}

/**
 * What a purchase attempt resolved to. Deciding and *applying* are separate: nothing here mutates a
 * run, so a caller that fails to apply the reward has not already taken the credits.
 */
sealed interface PurchaseResult {

    /** Allowed. The caller charges [price], stores [remaining], and applies [entry]'s reward. */
    data class Ok(val entry: ShopEntry, val price: Int, val remaining: Int) : PurchaseResult

    data class NotEnoughCredits(val have: Int, val need: Int) : PurchaseResult

    /** No entry with this id exists in the table at all — a typo, or a table that changed under a run. */
    data class NoSuchEntry(val id: String) : PurchaseResult

    /** The entry exists but is not in this wave's offer. What a stale shop screen produces. */
    data class NotOffered(val id: String) : PurchaseResult
}

/** What a reroll attempt resolved to. */
sealed interface RerollResult {

    data class Ok(val price: Int, val remaining: Int) : RerollResult

    data class NotEnoughCredits(val have: Int, val need: Int) : RerollResult

    /** The server has not priced rerolls, which disables the mechanic. See [ShopRules.rerollCost]. */
    data object Disabled : RerollResult
}
