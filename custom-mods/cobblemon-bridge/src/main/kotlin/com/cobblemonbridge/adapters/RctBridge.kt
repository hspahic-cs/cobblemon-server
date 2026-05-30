package com.cobblemonbridge.adapters

import org.slf4j.LoggerFactory
import java.lang.reflect.Method
import java.util.Optional
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Reflection bridge to RCT's trainer registry, so we can map a Cobblemon `PokemonBattle.battleId`
 * directly to the trainer JSON id of the trainer that's fighting (e.g. `"gym_06_volkner"`).
 * Compile-time independent of rctmod / rctapi.
 *
 * Reflection chain (all in `com.gitlab.srcmc.rctmod.api`):
 * ```
 *   RCTMod.getInstance(): RCTMod                                 // static
 *     .getTrainerManager(): TrainerManager
 *     .getBattle(UUID): Optional<TrainerBattle>
 *   TrainerBattle.getTrainerId(): String                          // e.g. "gym_06_volkner"
 * ```
 *
 * Why this exists: Cobblemon's `TrainerBattleActor` exposes no link back to the world entity that
 * started the battle. Our gym spawn-functions stamp `cobblemon_bridge.gym_id.<N>` on the entity,
 * but at `BATTLE_VICTORY` we only have the actor object. Until 0.7.9 we worked around this with a
 * proximity scan around the player, which missed in edge cases (player teleported into arena before
 * `BATTLE_STARTED_PRE` could scan; trainer despawned post-battle; etc.) — hence the recurring
 * "had to beat the gym twice to get credit" bug. RCT keeps a battle→trainer map by battle UUID,
 * which is what we should have been using all along.
 *
 * Returns null if RCT isn't loaded, the battle id isn't in its registry, or any reflection step
 * fails. Caller falls back to the legacy proximity path on null.
 */
object RctBridge {

    private val log = LoggerFactory.getLogger("cobblemon-bridge/rct")
    private const val RCTMOD_CLASS = "com.gitlab.srcmc.rctmod.api.RCTMod"

    @Volatile private var resolved = false
    @Volatile private var getInstanceM: Method? = null
    @Volatile private var getManagerM: Method? = null
    @Volatile private var getBattleM: Method? = null
    @Volatile private var getTrainerIdM: Method? = null
    private val warnedOnce = AtomicBoolean(false)

    /**
     * Returns the trainer JSON id for the trainer participating in [battleUuid], or null if
     * the battle isn't a trainer battle / RCT isn't loaded / reflection fails. The string is
     * the bare JSON stem (matches `data/rctmod/trainers/<id>.json`), e.g. `"gym_06_volkner"`
     * or `"gym_03_korrina_challenge"`.
     */
    fun trainerIdForBattle(battleUuid: UUID): String? {
        if (!ensureResolved()) return null
        return try {
            val rct = getInstanceM!!.invoke(null) ?: return null
            val mgr = getManagerM!!.invoke(rct) ?: return null
            val opt = getBattleM!!.invoke(mgr, battleUuid) as? Optional<*> ?: return null
            val battle = opt.orElse(null) ?: return null
            getTrainerIdM!!.invoke(battle) as? String
        } catch (e: Throwable) {
            log.debug("trainerIdForBattle({}) reflection failed: {}", battleUuid, e.message)
            null
        }
    }

    /**
     * Parses an RCT trainer id (`gym_<N>_<name>[_challenge]`) into (gymId, isChallenge).
     * Returns null for ids that don't match the gym shape.
     */
    fun parseGymTrainerId(trainerId: String): Pair<Int, Boolean>? {
        val m = GYM_ID_REGEX.matchEntire(trainerId) ?: return null
        val gymId = m.groupValues[1].toIntOrNull() ?: return null
        val isChallenge = m.groupValues[2] == "_challenge"
        return gymId to isChallenge
    }

    /** `gym_06_volkner` → (6, false); `gym_03_korrina_challenge` → (3, true). The body
     *  segment is non-greedy + anchored so trailing `_challenge` is detected reliably even
     *  for multi-word trainer names like `gym_07_crasher_wake`. */
    private val GYM_ID_REGEX = Regex("""^gym_(\d+)_.+?(_challenge)?$""")

    private fun ensureResolved(): Boolean {
        if (resolved) return getInstanceM != null
        return try {
            val cls = Class.forName(RCTMOD_CLASS)
            val instanceM = cls.getMethod("getInstance")
            val mgrM = cls.getMethod("getTrainerManager")
            // Manager + Battle return types: we need their class objects so we can resolve methods.
            val mgr = instanceM.invoke(null)?.let { mgrM.invoke(it) }
                ?: error("RCTMod.getInstance().getTrainerManager() returned null at probe time")
            val battleM = mgr.javaClass.getMethod("getBattle", UUID::class.java)
            // TrainerBattle.getTrainerId(): String — resolve via the return type of getBattle's Optional.
            // We can't introspect generics, so we look it up on the class with a known name lookup.
            val tbCls = Class.forName("com.gitlab.srcmc.rctmod.api.data.TrainerBattle")
            val tidM = tbCls.getMethod("getTrainerId")
            getInstanceM = instanceM
            getManagerM = mgrM
            getBattleM = battleM
            getTrainerIdM = tidM
            resolved = true
            true
        } catch (e: ClassNotFoundException) {
            warnOnce("rctmod not loaded — gym-defeat RCT lookup disabled (proximity fallback only)")
            resolved = true; false
        } catch (e: Throwable) {
            warnOnce("RctBridge reflection probe failed: ${e.javaClass.simpleName}: ${e.message}")
            resolved = true; false
        }
    }

    private fun warnOnce(msg: String) {
        if (warnedOnce.compareAndSet(false, true)) log.warn(msg)
    }
}
