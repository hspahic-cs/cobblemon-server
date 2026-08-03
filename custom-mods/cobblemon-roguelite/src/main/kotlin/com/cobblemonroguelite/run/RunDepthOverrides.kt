package com.cobblemonroguelite.run

import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private val log = LoggerFactory.getLogger("cobblemon_roguelite/run")

/**
 * Who is currently exempt from §2.18's badge gate — §2.25's operator override.
 *
 * ### Why this is not a testing convenience
 *
 * The gate reads *server* advancements. On a dev server nobody has beaten a gym, so every run is
 * capped at the shallowest configured tier and the back half of a 200-wave ladder — the boss waves,
 * the flat level-100 tail, the Elite Four waves at 182–190 — cannot be reached by the people who have
 * to test it. Without this the deep half of the mode ships having never been played.
 *
 * ### Why it is held in memory and never persisted
 *
 * A restart clears it, and that is the feature. An override is a lever an operator pulls to test
 * something; one that survives a restart is one that outlives the test and quietly becomes how the
 * server runs, which is the exact failure §2.25's "never the default" is about. The cost is having to
 * re-issue the command after a restart, which lands on an operator with a console open.
 *
 * What *is* persisted is the consequence: [RunState.startedUnderOverride] is stamped onto every run
 * created while this is on, so a run that finishes at wave 180 can be told from an honest one long
 * after the override itself is gone. That split is deliberate — the lever is ephemeral, the evidence
 * is not.
 *
 * ### Per player, and by UUID
 *
 * Not a server-wide switch, because a server-wide one is indistinguishable from having no gate and
 * would silently uncap every player who happened to start a run while it was on. UUIDs and not names
 * so that the grant follows the account rather than whatever it was called at the time.
 *
 * A `ConcurrentHashMap`-backed set because the commands that write it and the run loop that reads it
 * are both on the server thread today, and "both on the server thread today" is the sort of thing a
 * future battle callback quietly stops being true.
 */
object RunDepthOverrides {

    private val overridden: MutableSet<UUID> = ConcurrentHashMap.newKeySet()

    fun isActive(player: UUID): Boolean = overridden.contains(player)

    /** Everyone currently overridden. A snapshot, for the command that lists them. */
    fun active(): Set<UUID> = overridden.toSet()

    /**
     * Turn the override on or off for [player]. Returns true when the state actually changed.
     *
     * Logged at WARN in both directions, and at WARN rather than INFO on purpose: this is the line an
     * operator reading a log has to be able to find when a leaderboard entry looks impossible, and it
     * has to be findable *before* they know to look for it. [by] is the source that asked, so the log
     * says who, not only what.
     */
    fun set(player: UUID, playerName: String, on: Boolean, by: String): Boolean {
        val changed = if (on) overridden.add(player) else overridden.remove(player)
        if (!changed) return false
        if (on) {
            log.warn(
                "roguelite: DEPTH GATE OVERRIDDEN for {} ({}) by {} — their runs ignore the badge gate " +
                    "and any run they start from now on is marked as started under an override",
                playerName, player, by,
            )
        } else {
            log.warn("roguelite: depth gate override lifted for {} ({}) by {}", playerName, player, by)
        }
        return true
    }

    /** Drop every override. For tests, and for a server-side integration being unloaded. */
    fun clear() = overridden.clear()
}
