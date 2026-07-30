package com.cobblemonroguelite.shop

import com.cobblemonroguelite.data.shop.ShopEntry
import com.cobblemonroguelite.data.shop.ShopTable

/**
 * The **paid** half of the between-wave step: a short row of ordinary consumables, always the same
 * ones, bought with credits as often as they can be afforded.
 *
 * ### It is not a random draw, and that is the point
 *
 * The first version of this file drew the shop row from a weighted table, the way [RewardOffer] draws
 * the free options. That was wrong, and observing PokéRogue is what settled it: their row is *Potion,
 * Ether, Revive* at wave 6 and *Potion, Ether, Revive* at wave 7 and 8 and 9. The same things, every
 * single wave.
 *
 * A shop that randomises its stock cannot be planned against, and planning is the only thing this half
 * contributes that [RewardOffer] does not. A player who is one Revive short decides to skip a purchase
 * now and buy it next wave; if the Revive might simply not be there, that decision cannot be made and
 * the row collapses into a worse version of the free offer. So stock is **authored order, deterministic
 * by wave** — no seed, no `Random`, nothing to reroll.
 *
 * ### Two things do change with depth
 *
 * **Prices rise.** Their Potion is ₽46 at wave 6, ₽48 at 7, ₽50 at 8, ₽52 at 9, ₽80 by 21. That is
 * [ShopEntry.priceCurve], which already existed for this purpose: credits-per-wave grows with depth
 * (see [CreditRules]) so a flat price becomes free by the late run.
 *
 * **The row gets longer.** Three entries early, five by wave 21 (they add Super Potion and Full Heal).
 * That is [ShopRules.shopSlotsAt] plus each entry's own `min_wave`, and the two are different tools:
 * `min_wave` says an item *exists* from a wave, the slot count says how many of the existing ones fit.
 * Authoring six items live at wave 1 with three slots shows the first three, which is a content
 * mistake worth being able to make rather than an error worth refusing.
 */
object ShopStock {

    /**
     * What is for sale at [wave]: the eligible entries in authored order, capped at the wave's slot
     * count.
     *
     * Authored order rather than sorted by price or name. The author's order is a statement about what
     * the row should look like, and re-sorting it would silently override that — a shop whose entries
     * move around between waves also reads as random even when the contents are fixed.
     */
    fun stockAt(table: ShopTable, wave: Int, slots: Int? = null): List<ShopEntry> {
        val count = slots ?: ShopSettings.shop.shopSlotsAt(wave)
        if (count <= 0) return emptyList()
        return table.entries.filter { it.appearsAt(wave) }.take(count)
    }

    /**
     * Resolve buying [entryId] at [wave] with [credits] on hand.
     *
     * The stock is recomputed here rather than accepted from the caller, for the same reason
     * [RewardOffer.take] recomputes the offer: an item the player can see on a stale screen but that is
     * not in this wave's row must be refused. Unlike the free offer there is no reroll count to get
     * wrong, so the only way to reach [PurchaseResult.NotStocked] is an entry gated out by wave or
     * pushed past the slot count.
     */
    fun buy(table: ShopTable, wave: Int, credits: Int, entryId: String): PurchaseResult {
        val entry = stockAt(table, wave).firstOrNull { it.id == entryId }
            ?: return if (table.entry(entryId) == null) {
                PurchaseResult.NoSuchEntry(entryId)
            } else {
                PurchaseResult.NotStocked(entryId)
            }
        val price = entry.priceAt(wave)
        if (credits < price) return PurchaseResult.NotEnoughCredits(have = credits, need = price)
        return PurchaseResult.Ok(entry = entry, price = price, remaining = credits - price)
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

    /** No entry with this id exists in the table at all. */
    data class NoSuchEntry(val id: String) : PurchaseResult

    /** The entry exists but is not in this wave's row — gated out by wave, or past the slot count. */
    data class NotStocked(val id: String) : PurchaseResult
}

/** What a reroll of the free options resolved to. See [RewardOffer.reroll]. */
sealed interface RerollResult {

    data class Ok(val price: Int, val remaining: Int) : RerollResult

    data class NotEnoughCredits(val have: Int, val need: Int) : RerollResult

    /** The server has not priced rerolls, which disables the mechanic. See [ShopRules.rerollCost]. */
    data object Disabled : RerollResult
}
