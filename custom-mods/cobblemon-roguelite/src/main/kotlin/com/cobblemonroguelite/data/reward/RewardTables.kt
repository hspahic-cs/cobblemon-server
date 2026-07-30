package com.cobblemonroguelite.data.reward

import com.cobblemonroguelite.data.DataProblems
import com.cobblemonroguelite.data.JsonView
import com.cobblemonroguelite.data.RogueliteDataRegistry
import net.minecraft.resources.ResourceLocation

/**
 * Every reward table on the server, loaded from `data/<namespace>/roguelite/reward_tables/<name>.json`.
 *
 * ### The file
 *
 * ```json
 * {
 *   "tiers": [
 *     { "id": "common", "curve": [ { "wave": 1, "weight": 100 } ] },
 *     { "id": "rare",   "curve": [ { "wave": 1, "weight": 0 }, { "wave": 20, "weight": 40 } ] }
 *   ],
 *   "entries": [
 *     {
 *       "id": "protein",
 *       "tier": "common",
 *       "weight": 1,
 *       "min_wave": 1,
 *       "max_wave": null,
 *       "reward": { "type": "ev", "stat": "attack", "amount": 10 }
 *     }
 *   ]
 * }
 * ```
 *
 * `tiers` may be omitted entirely, in which case the whole table is one flat weighted list and
 * entries must not name a tier. That is not a special case for its own sake: the first tables anyone
 * writes are small, and making them declare a one-line tier they do not need is the kind of friction
 * that gets a format worked around instead of used.
 *
 * ### Containment
 *
 * A bad **entry** costs that entry; the table still loads. A bad **table-level** field — no
 * `entries`, a malformed tier, an entry pointing at a tier that does not exist — costs the file,
 * because there is no sensible partial reading of it. Both are logged with the file, the field path,
 * and what was wrong. What never happens is a table that loads and quietly does something other than
 * what was written.
 *
 * The one case handled as an error rather than a warning is a table whose entries *all* failed: it
 * would load as an empty table, roll nothing forever, and look identical in the log to a table that
 * simply had a quiet wave.
 */
object RewardTables : RogueliteDataRegistry<RewardTable>("reward_tables") {

    /**
     * The table the free between-wave offer reads, following the same convention as
     * [com.cobblemonroguelite.data.payout.PayoutTables.DEFAULT_TABLE] — a named file rather than
     * whichever pack happened to load first, so the three options a player sees are traceable to one id.
     */
    val DEFAULT_TABLE: ResourceLocation =
        ResourceLocation.fromNamespaceAndPath(com.cobblemonroguelite.CobblemonRoguelite.MOD_ID, "default")

    /** The table at [DEFAULT_TABLE], or null when no datapack has written one. */
    fun default(): RewardTable? = this[DEFAULT_TABLE]

    /**
     * Tier every entry gets when the table declares none. Not writable by an author — declaring a
     * tier called `default` and also relying on the no-tiers shorthand cannot both happen, since
     * declaring any tier turns the shorthand off.
     */
    const val DEFAULT_TIER = "default"

    public override fun parse(id: ResourceLocation, root: JsonView, problems: DataProblems): RewardTable? {
        var fatal = false

        val tiers = parseTiers(root)
        val entryViews = root.requireObjectList("entries")
        root.expectNoUnknownKeys()
        // Bail before touching entries when the tier block itself is broken. Every entry names a
        // tier, so carrying on would bury the one real problem under an "unknown tier" line per
        // entry — the file is already lost either way, and the log is the only thing left to get
        // right.
        if (tiers == null || entryViews == null) return null

        val entries = mutableListOf<RewardEntry>()
        val seen = mutableSetOf<String>()
        for (view in entryViews) {
            // Any problem raised while reading an entry drops the entry, even if enough of it parsed
            // to build one. Otherwise a field that failed its *type* check — `"weight": "lots"` —
            // silently falls back to whatever the code does with a null, and the entry loads meaning
            // something other than what was written, which is the failure this whole layer exists to
            // prevent.
            val before = problems.count
            val entry = parseEntry(view, tiers) { fatal = true }
            if (entry == null || problems.count != before) continue
            if (!seen.add(entry.id)) {
                // Fatal rather than last-wins: with two entries under one id, `rollOffer`'s
                // de-duplication and every log line naming the entry are both ambiguous, and which
                // one the author meant is not guessable.
                view.problem("id", "duplicate entry id '${entry.id}' in this table")
                fatal = true
                continue
            }
            entries += entry
        }

        if (entries.isEmpty()) {
            problems.add("entries", "no usable entries — a table that can roll nothing is not loaded")
            return null
        }
        if (fatal) return null

        val dropped = entryViews.size - entries.size
        if (dropped > 0) problems.add("entries", "$dropped entry/entries dropped; the rest of the table loaded")
        return RewardTable(id, tiers.tiers, entries)
    }

    /** The tier block, and whether the author actually wrote one. See [parseTiers]. */
    private class Tiers(val tiers: List<RewardTier>, val declared: Boolean) {
        val names: String get() = tiers.joinToString(", ") { it.id }
    }

    /**
     * Returns the declared tiers, a single synthetic flat one when none are declared, or null when
     * the tier block is malformed.
     *
     * A malformed tier is fatal for the file rather than skippable, for the reason given at the call
     * site. [Tiers.declared] is carried rather than inferred from the contents because an author may
     * legitimately declare exactly one tier and call it `default`, which would otherwise be
     * indistinguishable from the synthetic one and would reject their entries for naming it.
     */
    private fun parseTiers(root: JsonView): Tiers? {
        val views = root.optionalObjectList("tiers")
        if (views == null) {
            // Null also covers `"tiers": 5`, which JsonView has already complained about. Falling
            // through to the flat-table default there would load a table the author did not write.
            return if (root.hasField("tiers")) null
            else Tiers(listOf(RewardTier(DEFAULT_TIER, WeightCurve.flat())), declared = false)
        }
        if (views.isEmpty()) {
            root.problem("tiers", "must have at least one tier, or be omitted entirely for a flat table")
            return null
        }

        val tiers = mutableListOf<RewardTier>()
        var ok = true
        for (view in views) {
            val tierId = view.requireString("id")
            val curve = parseCurve(view)
            view.expectNoUnknownKeys()
            if (tierId == null || curve == null) {
                ok = false
                continue
            }
            if (tierId.isBlank()) {
                view.problem("id", "must not be blank")
                ok = false
                continue
            }
            if (tiers.any { it.id == tierId }) {
                view.problem("id", "duplicate tier id '$tierId'")
                ok = false
                continue
            }
            if (curve.points.all { it.weight == 0.0 }) {
                // Not fatal: zeroing a tier out is a legitimate way to shelve it without deleting
                // the entries under it. Worth a line so it is not a surprise later.
                view.problem("curve", "tier '$tierId' has weight 0 at every wave and can never be drawn")
            }
            tiers += RewardTier(tierId, curve)
        }
        return if (ok) Tiers(tiers, declared = true) else null
    }

    private fun parseCurve(view: JsonView): WeightCurve? {
        val pointViews = view.requireObjectList("curve") ?: return null
        if (pointViews.isEmpty()) {
            view.problem("curve", "must have at least one point")
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
            val previous = points.lastOrNull()
            if (previous != null && wave <= previous.wave) {
                // Interpolation reads the points in order; an out-of-order point would make the
                // curve mean something the author cannot predict from reading the file.
                pointView.problem("wave", "curve points must be in increasing wave order ($wave came after ${previous.wave})")
                ok = false
                continue
            }
            points += CurvePoint(wave, weight)
        }
        return if (ok && points.isNotEmpty()) WeightCurve(points) else null
    }

    private fun parseEntry(view: JsonView, tiers: Tiers, markFatal: () -> Unit): RewardEntry? {
        val entryId = view.requireString("id")
        val tier = parseTier(view, tiers, markFatal)
        val weight = view.requireDouble("weight")
        val minWave = view.optionalInt("min_wave")
        val maxWave = view.optionalInt("max_wave")
        val rewardView = view.requireObject("reward")
        val reward = rewardView?.let { RunReward.parse(it) }
        view.expectNoUnknownKeys()

        var ok = entryId != null && tier != null && weight != null && reward != null
        if (entryId != null && entryId.isBlank()) {
            view.problem("id", "must not be blank")
            ok = false
        }
        if (weight != null && weight <= 0.0) {
            view.problem("weight", "must be greater than 0, was $weight — a zero-weight entry can never be drawn")
            ok = false
        }
        if (minWave != null && minWave < 1) {
            view.problem("min_wave", "must be at least 1, was $minWave")
            ok = false
        }
        if (maxWave != null && maxWave < (minWave ?: 1)) {
            view.problem("max_wave", "$maxWave is before min_wave ${minWave ?: 1}, so this entry could never appear")
            ok = false
        }
        if (!ok) return null

        return RewardEntry(
            id = entryId!!,
            tier = tier!!,
            weight = weight!!,
            minWave = minWave ?: 1,
            maxWave = maxWave,
            reward = reward!!,
        )
    }

    private fun parseTier(view: JsonView, tiers: Tiers, markFatal: () -> Unit): String? {
        val named = view.optionalString("tier")
        if (!tiers.declared) {
            if (named != null) {
                view.problem("tier", "this table declares no \"tiers\", so entries must not name one")
                markFatal()
                return null
            }
            return DEFAULT_TIER
        }
        if (named == null) {
            view.problem("tier", "missing required field (this table declares tiers: ${tiers.names})")
            markFatal()
            return null
        }
        if (tiers.tiers.none { it.id == named }) {
            view.problem("tier", "unknown tier '$named' (declared: ${tiers.names})")
            markFatal()
            return null
        }
        return named
    }
}
