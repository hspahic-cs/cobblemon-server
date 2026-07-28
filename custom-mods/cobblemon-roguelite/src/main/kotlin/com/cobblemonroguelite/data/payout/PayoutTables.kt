package com.cobblemonroguelite.data.payout

import com.cobblemonroguelite.CobblemonRoguelite
import com.cobblemonroguelite.data.DataProblems
import com.cobblemonroguelite.data.JsonView
import com.cobblemonroguelite.data.RogueliteDataRegistry
import net.minecraft.resources.ResourceLocation

/**
 * Every payout table on the server, loaded from `data/<namespace>/roguelite/payout_tables/<name>.json`.
 *
 * ### Why this is a datapack and not a second mechanism
 *
 * §2.20 says to reuse §2.12's convention rather than inventing one, and that is load-bearing rather
 * than tidy: the reload lifecycle, the containment rules and the error reporting all live in
 * [RogueliteDataRegistry], and a second copy of them would be a second set of rules for how a bad
 * file behaves — on the one table whose contents leave the run.
 *
 * ### The file
 *
 * ```json
 * {
 *   "entries": [
 *     {
 *       "id": "cleared",
 *       "outcomes": [ "completed" ],
 *       "min_wave": 1,
 *       "max_wave": null,
 *       "grant": { "type": "item", "item": "minecraft:diamond", "count": 1 }
 *     }
 *   ]
 * }
 * ```
 *
 * ### Why `outcomes` is required when every other gate has a default
 *
 * `min_wave` defaults to 1 and `max_wave` to unbounded, because "pays at any depth" is the obvious
 * reading of an omitted band and the cost of getting it wrong is a payout that fires a little too
 * often. `outcomes` has no default at all. An omitted list would have to mean "every outcome", and
 * the author who forgot the field would then be paying wipes and walk-aways exactly what they pay a
 * cleared run — which is both the most expensive mistake this schema can make and an invisible one,
 * since a table that over-pays looks like a table that works. Making the field mandatory costs one
 * line per entry and removes the case entirely.
 *
 * ### Containment
 *
 * A bad **entry** costs that entry and the table still loads; a bad **table-level** field costs the
 * file. Same split as [com.cobblemonroguelite.data.reward.RewardTables], for the same reason: a
 * partly-loaded table announces itself in the log, a silently-defaulted one does not.
 */
object PayoutTables : RogueliteDataRegistry<PayoutTable>("payout_tables") {

    /**
     * The table a run pays from unless something says otherwise.
     *
     * **Nothing ships at this id.** §2.20 decided the *shape* of the payout and deferred its
     * contents, so writing a table here would be inventing the balance it deferred, and a shipped
     * table is much harder to remove later than to add. Until one exists, a finished run resolves an
     * empty payout — which is why [PayoutTable.entriesFor] returning nothing is a first-class answer
     * and why the run-end path must log the miss rather than treat it as a crash.
     */
    val DEFAULT_TABLE: ResourceLocation =
        ResourceLocation.fromNamespaceAndPath(CobblemonRoguelite.MOD_ID, "default")

    /** The table at [DEFAULT_TABLE], or null when no datapack has written one. */
    fun default(): PayoutTable? = this[DEFAULT_TABLE]

    public override fun parse(id: ResourceLocation, root: JsonView, problems: DataProblems): PayoutTable? {
        val entryViews = root.requireObjectList("entries")
        root.expectNoUnknownKeys()
        if (entryViews == null) return null

        var fatal = false
        val entries = mutableListOf<PayoutEntry>()
        val seen = mutableSetOf<String>()
        for (view in entryViews) {
            // Any problem raised while reading an entry drops the entry, even when enough of it
            // parsed to build one — otherwise a field that failed its type check falls back to a
            // default and the entry pays out something other than what was written.
            val before = problems.count
            val entry = parseEntry(view)
            if (entry == null || problems.count != before) continue
            if (!seen.add(entry.id)) {
                // Fatal rather than last-wins: with two entries under one id, the log line naming
                // what a run paid is ambiguous, and which one the author meant is not guessable.
                view.problem("id", "duplicate entry id '${entry.id}' in this table")
                fatal = true
                continue
            }
            entries += entry
        }

        if (entries.isEmpty()) {
            problems.add("entries", "no usable entries — a table that can pay nothing is not loaded")
            return null
        }
        if (fatal) return null

        val dropped = entryViews.size - entries.size
        if (dropped > 0) problems.add("entries", "$dropped entry/entries dropped; the rest of the table loaded")
        return PayoutTable(id, entries)
    }

    private fun parseEntry(view: JsonView): PayoutEntry? {
        val entryId = view.requireString("id")
        val outcomes = parseOutcomes(view)
        val minWave = view.optionalInt("min_wave")
        val maxWave = view.optionalInt("max_wave")
        val grantView = view.requireObject("grant")
        val grant = grantView?.let { PayoutGrant.parse(it) }
        view.expectNoUnknownKeys()

        var ok = entryId != null && outcomes != null && grant != null
        if (entryId != null && entryId.isBlank()) {
            view.problem("id", "must not be blank")
            ok = false
        }
        if (minWave != null && minWave < 1) {
            view.problem("min_wave", "must be at least 1, was $minWave")
            ok = false
        }
        if (maxWave != null && maxWave < (minWave ?: 1)) {
            view.problem("max_wave", "$maxWave is before min_wave ${minWave ?: 1}, so this entry could never pay")
            ok = false
        }
        if (!ok) return null

        return PayoutEntry(
            id = entryId!!,
            outcomes = outcomes!!,
            minWave = minWave ?: 1,
            maxWave = maxWave,
            grant = grant!!,
        )
    }

    /**
     * Read the required `outcomes` list. Null means unusable; an empty list is rejected rather than
     * loaded, since an entry that pays for no outcome is a line the author thinks is doing something.
     */
    private fun parseOutcomes(view: JsonView): Set<RunOutcome>? {
        val names = view.requireStringList("outcomes")
        if (names == null) {
            // The generic "missing required field" is already recorded. This second line exists
            // because this is the one field with no default, and an author who omitted it needs to
            // be told what to write, not only that something is absent.
            if (!view.hasField("outcomes")) {
                view.problem("outcomes", "every entry must say which run outcomes it pays for (${RunOutcome.keys})")
            }
            return null
        }
        if (names.isEmpty()) {
            view.problem("outcomes", "must name at least one outcome (${RunOutcome.keys})")
            return null
        }
        val outcomes = linkedSetOf<RunOutcome>()
        var ok = true
        for (name in names) {
            val outcome = RunOutcome.byKey(name.lowercase())
            if (outcome == null) {
                view.problem("outcomes", "'$name' is not a run outcome (expected one of: ${RunOutcome.keys})")
                ok = false
                continue
            }
            if (!outcomes.add(outcome)) {
                // Harmless to the set, worth a line: a repeat usually means the author meant to write
                // a different outcome and copied the wrong one.
                view.problem("outcomes", "'$name' is listed more than once")
            }
        }
        return if (ok) outcomes else null
    }
}
