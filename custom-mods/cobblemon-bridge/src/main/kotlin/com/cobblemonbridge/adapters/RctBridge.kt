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
 * Reflection chain (all in `com.gitlab.srcmc.rctmod`):
 * ```
 *   RCTMod.getInstance(): RCTMod                                 // static
 *     .getTrainerManager(): TrainerManager
 *     .getBattle(UUID): Optional<TrainerBattle>
 *   TrainerBattle.getTrainerSideMobs(): List<TrainerMob>          // 1 mob in 1v1 gym fights
 *   TrainerMob.getTrainerId(): String                             // e.g. "gym_06_volkner"
 * ```
 *
 * **0.7.14 fix:** between 0.7.9 (when this bridge was first written) and now, RCT moved the
 * `getTrainerId()` accessor off `TrainerBattle` and onto `TrainerMob`. The old reflection
 * chain blew up with `NoSuchMethodException: TrainerBattle.getTrainerId()` on every gym
 * defeat — Branch 0 in `GymDefeatHook` was silently disabled, and every defeat was forced
 * through Branch 1/2 (stash + proximity). Players who right-clicked the trainer first got
 * credit (EntityInteract stash); players who walked into LOS engagement without right-clicking
 * (more common for non-ops who aren't testing edge cases) hit the proximity path which can
 * miss when the trainer is > 8 blocks away at `BATTLE_STARTED_PRE`. End-effect: gym wins
 * looked unreliable, especially for non-op players.
 *
 * Why this exists in the first place: Cobblemon's `TrainerBattleActor` exposes no link back
 * to the world entity that started the battle. Our gym spawn-functions stamp
 * `cobblemon_bridge.gym_id.<N>` on the entity, but at `BATTLE_VICTORY` we only have the actor
 * object. RCT's manager keeps a battle→mob map by battle UUID, which is the authoritative
 * source.
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
    @Volatile private var getTrainerSideMobsM: Method? = null
    @Volatile private var mobGetTrainerIdM: Method? = null
    private val warnedOnce = AtomicBoolean(false)

    /**
     * Returns the trainer JSON id for the trainer participating in [battleUuid], or null if
     * the battle isn't a trainer battle / RCT isn't loaded / reflection fails. The string is
     * the bare JSON stem (matches `data/rctmod/trainers/<id>.json`), e.g. `"gym_06_volkner"`
     * or `"gym_03_korrina_challenge"`.
     *
     * Pulls the first mob from `TrainerBattle.getTrainerSideMobs()`. For 1v1 gym fights that's
     * the only entry; for multi-vs-multi battles we'd want the mob whose id matches the gym
     * convention, but no gym fights are multi-mob today so first-is-fine.
     */
    fun trainerIdForBattle(battleUuid: UUID): String? {
        if (!ensureResolved()) return null
        return try {
            val rct = getInstanceM!!.invoke(null) ?: return null
            val mgr = getManagerM!!.invoke(rct) ?: return null
            val opt = getBattleM!!.invoke(mgr, battleUuid) as? Optional<*> ?: return null
            val battle = opt.orElse(null) ?: return null
            val mobs = getTrainerSideMobsM!!.invoke(battle) as? List<*> ?: return null
            val mob = mobs.firstOrNull { it != null } ?: return null
            mobGetTrainerIdM!!.invoke(mob) as? String
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
            // We need real instances to introspect the dynamic return types — generics give us
            // nothing here, so pin everything statically off known class names.
            val mgr = instanceM.invoke(null)?.let { mgrM.invoke(it) }
                ?: error("RCTMod.getInstance().getTrainerManager() returned null at probe time")
            val battleM = mgr.javaClass.getMethod("getBattle", UUID::class.java)
            val tbCls = Class.forName("com.gitlab.srcmc.rctmod.api.data.TrainerBattle")
            val tsmM = tbCls.getMethod("getTrainerSideMobs")
            val mobCls = Class.forName("com.gitlab.srcmc.rctmod.world.entities.TrainerMob")
            val tidM = mobCls.getMethod("getTrainerId")
            getInstanceM = instanceM
            getManagerM = mgrM
            getBattleM = battleM
            getTrainerSideMobsM = tsmM
            mobGetTrainerIdM = tidM
            resolved = true
            log.info("RctBridge resolved — gym-defeat RCT direct lookup active")
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
