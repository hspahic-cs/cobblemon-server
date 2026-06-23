package com.cobblemonbridge.trainer

import net.minecraft.world.entity.Entity
import org.slf4j.LoggerFactory
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Reflection bridge to RCTmod that resolves the highest team level of a [TrainerMob].
 *
 * Powers the trainer nameplate suffix (see `TrainerNameplateMixin`): players can read a
 * trainer's strongest Pokemon level off the nameplate instead of right-clicking every team.
 *
 * RCTmod isn't a compile-time dep, so we resolve the call chain lazily and degrade silently
 * (returning [UNAVAILABLE]) when the mod is absent or the data isn't loaded yet. Resolves the
 * chain `RCTMod.getInstance().getTrainerManager().getData(mob)` then
 * `LevelUtils.trainerLevel(tmd)` — both already used by RCT's own client trainer-card GUI, so
 * the team data is synced and available client-side.
 */
object TrainerLevelBridge {

    /** Sentinel for "no level available" — callers should not render a suffix. */
    const val UNAVAILABLE: Int = -1

    private val log = LoggerFactory.getLogger("cobblemon-bridge/rctmod")

    private const val RCTMOD_CLASS = "com.gitlab.srcmc.rctmod.api.RCTMod"
    private const val TRAINER_MOB_CLASS = "com.gitlab.srcmc.rctmod.world.entities.TrainerMob"
    private const val TRAINER_MOB_DATA_CLASS = "com.gitlab.srcmc.rctmod.api.data.pack.TrainerMobData"
    private const val LEVEL_UTILS_CLASS = "com.gitlab.srcmc.rctmod.api.utils.LevelUtils"

    @Volatile private var resolved: Boolean = false
    @Volatile private var unavailable: Boolean = false
    private var trainerMobClass: Class<*>? = null
    private var getInstance: Method? = null
    private var getTrainerManager: Method? = null
    private var getData: Method? = null
    private var trainerLevel: Method? = null
    private val warnedOnce = AtomicBoolean(false)

    private fun resolve(): Boolean {
        if (resolved) return !unavailable
        synchronized(this) {
            if (resolved) return !unavailable
            try {
                val rctMod = Class.forName(RCTMOD_CLASS)
                trainerMobClass = Class.forName(TRAINER_MOB_CLASS)
                val tmdClass = Class.forName(TRAINER_MOB_DATA_CLASS)
                val levelUtils = Class.forName(LEVEL_UTILS_CLASS)

                getInstance = rctMod.getMethod("getInstance")
                getTrainerManager = rctMod.getMethod("getTrainerManager")
                // TrainerManager.getData is overloaded; select the TrainerMob overload explicitly.
                getData = getTrainerManager!!.returnType.getMethod("getData", trainerMobClass)
                // LevelUtils.trainerLevel is overloaded; select the TrainerMobData overload (static).
                trainerLevel = levelUtils.getMethod("trainerLevel", tmdClass)

                resolved = true
                unavailable = false
                log.info("RCTmod level bridge resolved — trainer nameplate levels enabled")
                return true
            } catch (e: ClassNotFoundException) {
                warnOnce("RCTmod not loaded — trainer nameplate levels disabled")
            } catch (e: Throwable) {
                warnOnce("RCTmod level reflection failed: ${e.javaClass.simpleName}: ${e.message}")
            }
            resolved = true
            unavailable = true
            return false
        }
    }

    /**
     * The highest Pokemon level across [trainer]'s team, or [UNAVAILABLE] if [trainer] isn't a
     * TrainerMob, the mod is absent, the data isn't loaded, or anything goes wrong. Never throws.
     */
    fun maxTeamLevel(trainer: Entity): Int {
        if (!resolve()) return UNAVAILABLE
        if (!trainerMobClass!!.isInstance(trainer)) return UNAVAILABLE
        return try {
            val instance = getInstance!!.invoke(null)
            val manager = getTrainerManager!!.invoke(instance)
            val tmd = getData!!.invoke(manager, trainer) ?: return UNAVAILABLE
            val level = trainerLevel!!.invoke(null, tmd) as? Int ?: return UNAVAILABLE
            if (level > 0) level else UNAVAILABLE
        } catch (e: Throwable) {
            UNAVAILABLE
        }
    }

    private fun warnOnce(msg: String) {
        if (warnedOnce.compareAndSet(false, true)) log.warn(msg)
    }
}
