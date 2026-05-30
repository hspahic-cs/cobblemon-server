package com.cobblemoncarrots.profile

import org.slf4j.LoggerFactory
import java.lang.reflect.Method
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Soft cross-mod call into cobblemon-bridge's `FavoriteTracker` via reflection.
 *
 * Why reflection: cobblemon-carrots doesn't depend on cobblemon-bridge at compile time
 * (same pattern as our `EconomyBridge`). At runtime, if cobblemon-bridge is loaded we resolve
 * the singleton + `record(UUID, UUID, String, Int)` method once and cache the handle; if it's
 * absent we degrade silently to a no-op with a single warning.
 *
 * Carrot-feed credit moved here in 0.7.11 because the bridge-side POKEMON_HEALED hook fired
 * inconsistently for carrot heals (direct `currentHealth = …` field write doesn't always trip
 * Cobblemon's healed event) — driving credit from the actual heal flow on the carrots side
 * makes it deterministic.
 */
object FavoriteBridge {

    private val log = LoggerFactory.getLogger("cobblemon-carrots/profile/favorite")
    private const val TRACKER_CLASS = "com.cobblemonbridge.profile.FavoriteTracker"

    @Volatile private var trackerInstance: Any? = null
    @Volatile private var recordMethod: Method? = null
    private val warnedOnce = AtomicBoolean(false)

    private fun resolve(): Any? {
        trackerInstance?.let { return it }
        return try {
            val cls = Class.forName(TRACKER_CLASS)
            // Kotlin `object FavoriteTracker { companion object { fun get(): FavoriteTracker } }`
            // exposes get() on the companion object via `Companion` field — but the synthesized
            // `Companion.get` is also exposed as a static on the outer class. Try the static path
            // first; fall back to companion-field if not present.
            val staticGet = cls.declaredMethods.firstOrNull { it.name == "get" && it.parameterCount == 0 }
            val instance: Any? = if (staticGet != null) {
                staticGet.invoke(null)
            } else {
                val companionField = cls.getDeclaredField("Companion")
                val companion = companionField.get(null)
                companion.javaClass.getMethod("get").invoke(companion)
            }
            if (instance == null) {
                warnOnce("FavoriteTracker.get() returned null — bridge not initialized?"); null
            } else {
                recordMethod = instance.javaClass.getMethod(
                    "record",
                    UUID::class.java, UUID::class.java, String::class.java, Int::class.javaPrimitiveType,
                )
                trackerInstance = instance
                instance
            }
        } catch (e: ClassNotFoundException) {
            warnOnce("cobblemon-bridge not loaded — carrot-heal favorite credit disabled"); null
        } catch (e: Throwable) {
            warnOnce("FavoriteBridge reflection failed: ${e.javaClass.simpleName}: ${e.message}"); null
        }
    }

    fun record(playerUuid: UUID, pokemonUuid: UUID, species: String, hpAmount: Int) {
        if (hpAmount <= 0) return
        val instance = resolve() ?: return
        try {
            recordMethod!!.invoke(instance, playerUuid, pokemonUuid, species, hpAmount)
        } catch (e: Throwable) {
            log.warn("FavoriteBridge.record invoke failed", e)
        }
    }

    private fun warnOnce(msg: String) {
        if (warnedOnce.compareAndSet(false, true)) log.warn(msg)
    }
}
