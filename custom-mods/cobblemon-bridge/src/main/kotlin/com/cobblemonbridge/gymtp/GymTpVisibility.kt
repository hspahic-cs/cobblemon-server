package com.cobblemonbridge.gymtp

import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("cobblemon_bridge/gymtp/visibility")

/**
 * Pure visibility logic, isolated from Minecraft types so it can be unit-tested with a fake
 * [AdvancementChecker]. The real impl wraps `ServerPlayer.advancements` (see [GymTpMenu]).
 */
fun interface AdvancementChecker {
    /** True iff the player has the named advancement completed. */
    fun has(advancementId: String): Boolean
}

data class VisibleEntry(
    val id: String,
    val entry: GymEntry,
    val state: State,
    val label: String,
) {
    enum class State { BEATEN, AVAILABLE, OTHER }
}

object GymTpVisibility {

    /**
     * Build the list shown to a player, in stable display order: numeric ids ascending first,
     * then non-numeric in insertion order.
     *
     * Visibility rule (matches spec §"Visibility rule"):
     *   - If entry.unlockAdvancement is non-null: visible iff player holds it.
     *   - Else if id parses as int N:
     *       * N == 1 → always visible
     *       * player has server:beat_gym_<N>     → visible (BEATEN)
     *       * player has server:beat_gym_<N-1>   → visible (AVAILABLE)
     *   - Else: hidden, warn at load time (warning emitted by caller; this fn just hides).
     */
    fun visibleFor(entries: Map<String, GymEntry>, checker: AdvancementChecker): List<VisibleEntry> {
        val (numeric, other) = entries.entries.partition { it.key.toIntOrNull() != null }
        val numericSorted = numeric.sortedBy { it.key.toInt() }
        val ordered = numericSorted + other

        return ordered.mapNotNull { (id, entry) ->
            val n = id.toIntOrNull()
            val state: VisibleEntry.State? = when {
                entry.unlockAdvancement != null -> {
                    if (checker.has(entry.unlockAdvancement)) {
                        // Explicit unlock + numeric id → still surface BEATEN if they have it.
                        if (n != null && checker.has(beatAdvancement(n))) VisibleEntry.State.BEATEN
                        else VisibleEntry.State.OTHER
                    } else null
                }
                n != null -> when {
                    checker.has(beatAdvancement(n)) -> VisibleEntry.State.BEATEN
                    n == 1 || checker.has(beatAdvancement(n - 1)) -> VisibleEntry.State.AVAILABLE
                    else -> null
                }
                else -> null
            }
            state?.let { VisibleEntry(id, entry, it, label(id, entry)) }
        }
    }

    private fun beatAdvancement(n: Int): String = "server:beat_gym_$n"

    private fun label(id: String, entry: GymEntry): String {
        entry.label?.let { return it }
        val n = id.toIntOrNull()
        if (n != null) return "Gym $n"
        return id.split('_', '-')
            .joinToString(" ") { it.replaceFirstChar(Char::uppercase) } + " Gyms"
    }

    /**
     * Warn once per non-numeric id with no unlock — caller invokes at store-load time so an op
     * who configures a string-keyed entry without an unlock sees a hint, not silent invisibility.
     */
    fun warnUnlockMissing(entries: Map<String, GymEntry>) {
        for ((id, entry) in entries) {
            if (id.toIntOrNull() == null && entry.unlockAdvancement == null) {
                log.warn(
                    "gym-tp: entry '{}' has no unlockAdvancement and id isn't numeric — it will be hidden from all players. Set with: /gymtp set {} unlock <advancement>",
                    id, id,
                )
            }
        }
    }
}
