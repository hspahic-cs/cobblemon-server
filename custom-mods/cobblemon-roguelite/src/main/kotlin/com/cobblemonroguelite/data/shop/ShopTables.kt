package com.cobblemonroguelite.data.shop

import com.cobblemonroguelite.data.DataProblems
import com.cobblemonroguelite.data.JsonView
import com.cobblemonroguelite.data.RogueliteDataRegistry
import com.cobblemonroguelite.data.reward.CurvePoint
import com.cobblemonroguelite.data.reward.RunReward
import com.cobblemonroguelite.data.reward.WeightCurve
import net.minecraft.resources.ResourceLocation

/**
 * Every shop table on the server, loaded from `data/<namespace>/roguelite/shop_tables/<name>.json`.
 *
 * ### The file
 *
 * ```json
 * {
 *   "entries": [
 *     {
 *       "id": "protein",
 *       "price": 120,
 *       "min_wave": 1,
 *       "max_wave": null,
 *       "reward": { "type": "ev", "stat": "attack", "amount": 10 }
 *     },
 *     {
 *       "id": "ability_patch",
 *       "price": 400,
 *       "min_wave": 40,
 *       "price_curve": [ { "wave": 40, "weight": 100 }, { "wave": 200, "weight": 300 } ],
 *       "reward": { "type": "ability_patch" }
 *     }
 *   ]
 * }
 * ```
 *
 * `reward` is the same object the reward tables take — see [RunReward], whose sealed list is §2.12's
 * boundary on what a reward can be. A shop entry adds `price` and an optional `price_curve`, and it
 * has **no tiers**: the reward tables need tiers because rarity has to ramp with depth on its own,
 * whereas a shop's ramp is the price. Adding tiers here as well would give two independent knobs for
 * the same feeling and make neither predictable.
 *
 * ### Containment
 *
 * Same rule as [com.cobblemonroguelite.data.reward.RewardTables], and for the same reason: a bad
 * **entry** costs that entry and the table still loads; a bad **table-level** field costs the file.
 * An entry that raised any problem is dropped even when enough of it parsed to build one, because the
 * alternative is an entry that loads meaning something other than what was written — a `"price":
 * "cheap"` falling back to a default price is exactly the silent mispricing this layer exists to
 * prevent.
 *
 * A table whose entries all failed is an error, not an empty table: it would offer nothing forever
 * and be indistinguishable in the log from a shop that was deliberately quiet.
 */
object ShopTables : RogueliteDataRegistry<ShopTable>("shop_tables") {

    /**
     * The table the between-wave shop reads when nothing pins another, following the convention
     * [com.cobblemonroguelite.data.payout.PayoutTables.DEFAULT_TABLE] sets.
     *
     * A fixed id rather than "the first one loaded": with several tables installed, first-loaded depends
     * on pack order and would make the shop's contents change for reasons an operator cannot see. A
     * named default is a file they can find. Until one exists the paid half of the step is simply closed,
     * which the command says out loud.
     */
    val DEFAULT_TABLE: ResourceLocation =
        ResourceLocation.fromNamespaceAndPath(com.cobblemonroguelite.CobblemonRoguelite.MOD_ID, "default")

    /** The table at [DEFAULT_TABLE], or null when no datapack has written one. */
    fun default(): ShopTable? = this[DEFAULT_TABLE]

    public override fun parse(id: ResourceLocation, root: JsonView, problems: DataProblems): ShopTable? {
        var fatal = false
        val entryViews = root.requireObjectList("entries")
        root.expectNoUnknownKeys()
        if (entryViews == null) return null

        val entries = mutableListOf<ShopEntry>()
        val seen = mutableSetOf<String>()
        for (view in entryViews) {
            val before = problems.count
            val entry = parseEntry(view)
            if (entry == null || problems.count != before) continue
            if (!seen.add(entry.id)) {
                // Fatal rather than last-wins: with two entries under one id, `ShopStock.buy`'s
                // lookup is ambiguous, so a purchase could charge for one and grant the other.
                view.problem("id", "duplicate entry id '${entry.id}' in this table")
                fatal = true
                continue
            }
            entries += entry
        }

        if (entries.isEmpty()) {
            problems.add("entries", "no usable entries — a shop that can stock nothing is not loaded")
            return null
        }
        if (fatal) return null

        val dropped = entryViews.size - entries.size
        if (dropped > 0) problems.add("entries", "$dropped entry/entries dropped; the rest of the table loaded")

        return ShopTable(id, entries)
    }

    private fun parseEntry(view: JsonView): ShopEntry? {
        val entryId = view.requireString("id")
        // `price` becomes optional the moment `cost_multiplier` is written, because the multiplier
        // replaces it outright — requiring both would make every author write a number that is ignored.
        val costMultiplier = view.optionalDouble("cost_multiplier")
        val price = if (costMultiplier != null) (view.optionalInt("price") ?: 0) else view.requireInt("price")
        val rewardView = view.requireObject("reward")
        val minWave = view.optionalInt("min_wave") ?: 1
        val maxWave = view.optionalInt("max_wave")
        val priceCurve = if (view.hasField("price_curve")) parseCurve(view) else null
        val hadCurveField = view.hasField("price_curve")
        view.expectNoUnknownKeys()

        if (entryId == null || price == null || rewardView == null) return null
        if (hadCurveField && priceCurve == null) return null
        val reward = RunReward.parse(rewardView) ?: return null

        if (entryId.isBlank()) {
            view.problem("id", "must not be blank")
            return null
        }
        if (costMultiplier != null && costMultiplier < 0.0) {
            // Same reasoning as a negative price, one level up: a negative multiple pays the player to
            // take the item, which is an income loop that scales with depth.
            view.problem("cost_multiplier", "must not be negative, was $costMultiplier")
            return null
        }
        if (price < 0) {
            // Zero is allowed — a giveaway slot is a legitimate way to author a tutorial shop — but a
            // negative price would pay the player to accept a reward, which is an income loop.
            view.problem("price", "must not be negative, was $price")
            return null
        }
        if (minWave < 1) {
            view.problem("min_wave", "must be at least 1, was $minWave")
            return null
        }
        if (maxWave != null && maxWave < minWave) {
            view.problem("max_wave", "must not be below min_wave ($minWave), was $maxWave")
            return null
        }
        return ShopEntry(
            id = entryId,
            reward = reward,
            price = price,
            minWave = minWave,
            maxWave = maxWave,
            priceCurve = priceCurve,
            costMultiplier = costMultiplier,
        )
    }

    /**
     * The optional price curve, read with the reward tables' own [WeightCurve] rather than a second
     * interpolation format.
     *
     * `weight` means "percent of the base price" here, which is a reuse of the field name for a
     * different meaning and is worth being explicit about: 100 is the authored price, 300 is triple.
     * The alternative — a `multiplier` field on a copy of the curve type — would duplicate the
     * piecewise-line code and its tests to rename one key.
     */
    private fun parseCurve(view: JsonView): WeightCurve? {
        val pointViews = view.requireObjectList("price_curve") ?: return null
        if (pointViews.isEmpty()) {
            view.problem("price_curve", "must have at least one point")
            return null
        }
        val points = mutableListOf<CurvePoint>()
        var ok = true
        for (pointView in pointViews) {
            val wave = pointView.requireInt("wave")
            val weight = pointView.requireDouble("weight")
            pointView.expectNoUnknownKeys()
            if (wave == null || weight == null) {
                ok = false
                continue
            }
            if (wave < 1) {
                pointView.problem("wave", "must be at least 1, was $wave")
                ok = false
                continue
            }
            if (weight < 0.0) {
                pointView.problem("weight", "must not be negative, was $weight")
                ok = false
                continue
            }
            points += CurvePoint(wave, weight)
        }
        if (!ok) return null
        return WeightCurve(points.sortedBy { it.wave })
    }
}
