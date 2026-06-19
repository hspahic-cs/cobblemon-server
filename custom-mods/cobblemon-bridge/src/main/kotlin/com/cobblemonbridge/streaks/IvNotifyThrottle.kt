package com.cobblemonbridge.streaks

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Throttles Cobblemon Unchained's per-catch IV streak chat messages.
 *
 * Unchained fires `unchained.notification.iv.*` ("…with perfect IVs!") on EVERY boosted catch once
 * a per-species IV streak crosses its threshold, which floods chat for any dedicated hunter. The mod
 * exposes no cadence option — `notifyPlayer` is a plain on/off boolean — so
 * [com.cobblemonbridge.mixin.UnchainedIvNotifyThrottleMixin] gates the notify call through here:
 * only every [EVERY]-th boosted IV catch per (player, species) is shown. Shiny and hidden-ability
 * notifications are never routed here, so they fire exactly as before.
 *
 * The counter is in-memory and purely cosmetic — it gates the *message*, never the IV boost itself —
 * so resetting on server restart is harmless (worst case: one early/late message in a streak).
 */
object IvNotifyThrottle {

    /** Show one message per this many boosted IV catches (per player + species). */
    private const val EVERY = 5L

    private val counts = ConcurrentHashMap<String, Long>()

    /**
     * Records one boosted IV catch and decides whether its message should reach the player.
     *
     * @return true on every [EVERY]-th catch (5th, 10th, 15th, …); false otherwise.
     */
    @JvmStatic
    fun shouldShow(playerId: UUID, speciesId: String): Boolean {
        val n = counts.merge("$playerId|$speciesId", 1L) { a, b -> a + b }!!
        return n % EVERY == 0L
    }
}
