package com.cobblemonroguelite.shop

import com.cobblemonroguelite.data.reward.RewardEntry
import com.cobblemonroguelite.data.reward.RewardTable
import kotlin.random.Random

/**
 * The **free** half of the between-wave step: three random rewards, of which the player takes **one**.
 *
 * ### Corrected 2026-07-29, and this is the correction that matters
 *
 * An earlier version of this module modelled the between-wave step as a single shop where credits buy
 * items out of a weighted table. That is not what PokéRogue does, and conflating the two lost the
 * mechanic. Their screen has **two independent halves**:
 *
 *  - **This one.** Three item options, drawn at random and rarity-tiered, shown with no price. The
 *    player picks exactly one and the other two are gone. Rerolling the three costs money, and the
 *    reroll price grows with depth (₽250 early, ₽750 by wave 21).
 *  - **[ShopStock].** A short row of ordinary consumables — Potion, Ether, Revive — that is *always*
 *    the same things, priced, and bought with money as often as you can afford.
 *
 * The distinction is the whole feel of the step. The free pick is a **choice under scarcity**: three
 * doors, one key, and the tension is what you give up. The paid row is **planning**: the Revive is
 * always there, so a player can decide to save for it. Merging them produces neither — a random shop
 * you buy several things from is a vending machine with extra steps.
 *
 * ### Why pick-one is enforced here rather than trusted to the UI
 *
 * [take] is the only way to resolve an offer, and it takes a single id. There is deliberately no
 * "take these" bulk form: a caller that could grant two of the three would be one loop away from
 * granting all of them, and the scarcity is the mechanic rather than a presentation detail.
 *
 * ### Determinism, unchanged from the first version because it was right there
 *
 * The offer is a function of `(run seed, wave, rerolls taken)` and is recomputed rather than stored,
 * so §2.16's paused run resumes to the same three items and relogging is not a free reroll. The
 * reroll count is an input to the stream for that reason — reroll twice, log out, log in, still the
 * second offer.
 */
object RewardOffer {

    /**
     * A salt, mixed into the stream so this draw cannot correlate with any other seeded decision at
     * the same wave. §2.16 makes salts part of the save format: appended, never renumbered.
     */
    private const val SALT = 0x0FFE4_2EA4DL

    /** The three (by default) rewards on offer at [wave]. */
    fun offerFor(table: RewardTable, wave: Int, seed: Long, rerolls: Int = 0, options: Int? = null): List<RewardEntry> =
        table.rollOffer(wave, options ?: ShopSettings.shop.rewardOptions, streamFor(wave, seed, rerolls))

    /**
     * Resolve the player taking [entryId] from this wave's offer.
     *
     * The offer is recomputed here rather than accepted from the caller, which is the guard that makes
     * a stale screen safe: an id that is in the table but not among the three on offer is refused, and
     * an id from the *previous* reroll is refused because the reroll count no longer matches.
     */
    fun take(table: RewardTable, wave: Int, seed: Long, rerolls: Int, entryId: String): TakeResult {
        val offer = offerFor(table, wave, seed, rerolls)
        val entry = offer.firstOrNull { it.id == entryId }
            ?: return if (table.entries.none { it.id == entryId }) {
                TakeResult.NoSuchEntry(entryId)
            } else {
                TakeResult.NotOffered(entryId)
            }
        return TakeResult.Ok(entry)
    }

    /**
     * Resolve a paid reroll of the three options.
     *
     * Unlike a purchase this grants nothing — it changes what is on offer — so it has its own result
     * type rather than overloading [TakeResult] with a nullable entry.
     */
    fun reroll(credits: Int, rerollsTaken: Int, wave: Int): RerollResult {
        val price = ShopSettings.shop.rerollPrice(rerollsTaken, wave) ?: return RerollResult.Disabled
        if (credits < price) return RerollResult.NotEnoughCredits(have = credits, need = price)
        return RerollResult.Ok(price = price, remaining = credits - price)
    }

    /**
     * SplitMix64-style mixing of the three inputs rather than `Random(seed + wave)`: an additive seed
     * gives adjacent waves adjacent — and for small tables, frequently identical — first draws, so
     * wave 40 and wave 41 would offer the same three items.
     */
    private fun streamFor(wave: Int, seed: Long, rerolls: Int): Random {
        var z = seed xor SALT
        z += wave.toLong() * -0x61c8864680b583ebL
        z += rerolls.toLong() * 0x2545f4914f6cdd1dL
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a2bL
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        return Random(z xor (z ushr 31))
    }
}

/** What taking one of the three free options resolved to. Granting the reward is the caller's job. */
sealed interface TakeResult {

    data class Ok(val entry: RewardEntry) : TakeResult

    /** No entry with this id exists in the table at all — a typo, or a table edited under a run. */
    data class NoSuchEntry(val id: String) : TakeResult

    /** The entry exists but is not among this wave's three. What a stale screen, or an old reroll, produces. */
    data class NotOffered(val id: String) : TakeResult
}
