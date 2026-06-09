package com.cobblemonbridge.adapters

import org.slf4j.LoggerFactory
import java.lang.reflect.Method
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Reflection bridge to cobblemon-poke-ai's `FoulPlayBattles` registry — tells whether a battle ran
 * the foul-play (poke-engine, `pe`) AI without a compile-time dependency on that mod. Used by
 * [com.cobblemonbridge.battle.GymDefeatHook] to apply the foul-play fight-money multiplier.
 * Degrades to "not foul-play" when the mod is absent.
 */
object PokeAiBridge {

    private val log = LoggerFactory.getLogger("cobblemon-bridge/pokeai")
    private const val FOUL_PLAY_CLASS = "com.cobblemonserver.pokeai.ai.FoulPlayBattles"

    @Volatile private var resolved = false
    @Volatile private var unavailable = false
    private var wasFoulPlayM: Method? = null
    private val warnedOnce = AtomicBoolean(false)

    private fun resolve(): Boolean {
        if (resolved) return !unavailable
        synchronized(this) {
            if (resolved) return !unavailable
            try {
                wasFoulPlayM = Class.forName(FOUL_PLAY_CLASS).getMethod("wasFoulPlay", UUID::class.java)
                resolved = true
                unavailable = false
                log.info("cobblemon-poke-ai bridge resolved — foul-play fight multiplier ready")
                return true
            } catch (e: Throwable) {
                if (warnedOnce.compareAndSet(false, true)) {
                    log.info("cobblemon-poke-ai not present — foul-play money multiplier disabled")
                }
            }
            resolved = true
            unavailable = true
            return false
        }
    }

    /** True if [battleId] ran the foul-play AI (per cobblemon-poke-ai's registry). */
    fun wasFoulPlay(battleId: UUID): Boolean {
        if (!resolve()) return false
        return try {
            wasFoulPlayM!!.invoke(null, battleId) as? Boolean ?: false
        } catch (e: Throwable) {
            false
        }
    }
}
