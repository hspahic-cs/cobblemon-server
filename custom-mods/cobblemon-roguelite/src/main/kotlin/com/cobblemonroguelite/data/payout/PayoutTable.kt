package com.cobblemonroguelite.data.payout

import net.minecraft.resources.ResourceLocation

/**
 * How a run ended, as far as the payout is concerned.
 *
 * Three outcomes rather than a boolean because they are priced differently and a table has to be
 * able to say so: a cleared run, a wipe, and a walk-away are three different things a player did.
 * §2.16 in particular leans on the third being nameable — abandoning is a legitimate move that the
 * entry fee prices, so a table owner needs to be able to decide whether it pays anything at all
 * rather than having that decided for them here.
 *
 * If the run loop grows its own richer status type, this stays the payout-facing projection of it
 * and should be *mapped* onto rather than duplicated: what the payout table can distinguish is a
 * schema promise to server owners, and it must not silently gain cases because an internal enum did.
 */
enum class RunOutcome {
    /** The run reached its end (§2.19's wave 200) with a party still standing. */
    COMPLETED,

    /** The party was wiped out. The run still reached some depth, which a table may still pay for. */
    WIPED,

    /** The player walked away from a run in progress. */
    ABANDONED,

    ;

    /** The name a table author writes. Lowercase because nothing else in these files shouts. */
    val key: String get() = name.lowercase()

    companion object {
        fun byKey(key: String): RunOutcome? = entries.firstOrNull { it.key == key }

        val keys: String get() = entries.joinToString(", ") { it.key }
    }
}

/**
 * One loaded payout table: what a finished run hands over, by how it ended and how deep it got.
 *
 * ### Why this is not a weighted draw, unlike the reward table
 *
 * [com.cobblemonroguelite.data.reward.RewardTable] rolls, because variance *between* runs is the
 * point of a reward. The payout is the opposite thing. It is the single metered channel out of an
 * otherwise sealed run (§1.1), so what matters is that it is auditable and that two identical runs
 * pay identically — a player who cleared wave 200 and got noticeably less than the last person who
 * did has been taxed by a die roll at the only moment the mode is handing out something permanent.
 * The variance already exists: two hundred waves of it, in the reward table, inside the run where it
 * belongs.
 *
 * So resolution is a filter, not a draw: every entry that matches the outcome and the depth pays,
 * all of them, in table order. A payout that scales with depth is written as banded entries, which
 * also means the bands are readable as a list rather than inferred from a curve.
 *
 * ### No `replace` flag, and no tiers
 *
 * Datapack override rules already cover replacement — see [com.cobblemonroguelite.data.RogueliteDataRegistry].
 * Tiers exist in the reward table only to make a rarity ramp retunable in one place, and there is no
 * rarity here to ramp.
 */
data class PayoutTable(
    val id: ResourceLocation,
    val entries: List<PayoutEntry>,
) {

    /**
     * Every entry that pays for a run that ended [outcome] at [wave].
     *
     * Returns entries rather than bare grants so the run-end path can name them in the log. That is
     * not convenience: the payout is the one thing a run gives a player permanently, and "wave 143,
     * wiped, paid entries [deep_run, consolation]" is the difference between a dispute that can be
     * answered and one that cannot.
     *
     * An empty result is a real answer, not a failure. A table is entitled to pay nothing for a wipe
     * on wave 3, and callers must not treat "nothing to hand over" as an error.
     */
    fun entriesFor(outcome: RunOutcome, wave: Int): List<PayoutEntry> =
        entries.filter { it.pays(outcome, wave) }

    fun grantsFor(outcome: RunOutcome, wave: Int): List<PayoutGrant> =
        entriesFor(outcome, wave).map { it.grant }
}

/**
 * One thing a table pays, and the conditions under which it pays it.
 *
 * @property id table-local and required. Names the entry in the log line that records what a run
 *   paid; an auto-generated index would change the moment the author inserts a line above it.
 * @property outcomes which run endings this pays for. **Required** — see [PayoutTables] for why this
 *   one field has no default.
 * @property minWave shallowest depth this pays at, inclusive.
 * @property maxWave deepest, inclusive, or null for no limit. Present so a consolation payout can be
 *   switched off once a better one takes over, rather than stacking with it forever.
 */
data class PayoutEntry(
    val id: String,
    val outcomes: Set<RunOutcome>,
    val minWave: Int,
    val maxWave: Int?,
    val grant: PayoutGrant,
) {
    fun pays(outcome: RunOutcome, wave: Int): Boolean =
        outcome in outcomes && wave >= minWave && (maxWave == null || wave <= maxWave)
}
