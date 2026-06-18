package com.cobblemonbridge.battle

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Per-player, per-gym battle cooldown — stops gym battles from being spam-farmed for EXP.
 *
 * After a player starts a gym battle, that gym goes on a [COOLDOWN_TICKS] cooldown for them; a
 * re-challenge before it expires is blocked with a chat message. Keyed by (player UUID, gym id)
 * so each gym tracks its own timer. Cooldowns live in memory only — a server restart clears
 * them, which is fine for a 2-minute anti-spam timer.
 *
 * Elite Four gyms (20-24) are included too. Because the timer is per-(player, gym), normal gauntlet
 * progression (20→21→…, each member fought once per run) is never blocked — only re-fighting the
 * same member within the window is. The trade-off is that retrying a member you just lost to waits
 * out the cooldown; the gauntlet's own consecutive-or-restart pacing lives in [E4GauntletHook].
 */
object GymCooldown {

    /** 2 minutes at 20 ticks/second. */
    const val COOLDOWN_TICKS: Long = 2L * 60L * 20L

    /** (player, gymId) -> game-tick the gym was last challenged. */
    private val lastChallenge = ConcurrentHashMap<Pair<UUID, Int>, Long>()

    /** Ticks of cooldown remaining for this player/gym, or 0 if ready to battle. */
    fun remainingTicks(player: UUID, gymId: Int, nowTicks: Long): Long {
        val last = lastChallenge[player to gymId] ?: return 0L
        return (COOLDOWN_TICKS - (nowTicks - last)).coerceAtLeast(0L)
    }

    /** Record that the player just started a battle with this gym. */
    fun record(player: UUID, gymId: Int, nowTicks: Long) {
        lastChallenge[player to gymId] = nowTicks
    }
}
