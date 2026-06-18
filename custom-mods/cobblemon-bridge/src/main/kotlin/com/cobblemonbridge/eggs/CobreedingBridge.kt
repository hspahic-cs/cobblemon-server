package com.cobblemonbridge.eggs

import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponentType
import net.minecraft.world.item.ItemStack
import org.slf4j.LoggerFactory
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Reflection bridge into Cobreeding 2.2.1 (`ludichat.cobbreeding.PokemonEgg`) for reading +
 * writing the egg's `TIMER` and `POKEMON_PROPERTIES` data components. We use reflection so
 * cobblemon-bridge compiles without a hard dep on Cobreeding.
 *
 * The Companion's getter methods (`getTIMER()`, `getSECOND()`, `getPOKEMON_PROPERTIES()`) each
 * return a `DataComponentType<T>` instance — once resolved, we cache it and read/write via
 * `ItemStack.get(...)` / `ItemStack.set(...)` directly. No further reflection per operation.
 */
object CobreedingBridge {

    private val log = LoggerFactory.getLogger("cobblemon-bridge/cobreeding")
    private const val EGG_CLASS = "ludichat.cobbreeding.PokemonEgg"
    private const val COMPANION_CLASS = "ludichat.cobbreeding.PokemonEgg\$Companion"

    @Volatile private var resolved: Boolean = false
    @Volatile private var unavailable: Boolean = false

    private var timerComponent: DataComponentType<Int>? = null
    private var pokemonPropertiesComponent: DataComponentType<String>? = null
    private var eggClass: Class<*>? = null
    private val warnedOnce = AtomicBoolean(false)

    // --- Breeding registry (PastureBreedingData.registry: Map<BlockPos, PastureBreedingData>) ---
    private const val BREEDING_DATA_CLASS = "ludichat.cobbreeding.PastureBreedingData"
    @Volatile private var breedingResolved: Boolean = false
    @Volatile private var breedingUnavailable: Boolean = false
    private var registryField: Field? = null
    private var getEggsMethod: Method? = null
    private val breedingWarnedOnce = AtomicBoolean(false)

    private fun resolve(): Boolean {
        if (resolved) return !unavailable
        synchronized(this) {
            if (resolved) return !unavailable
            try {
                eggClass = Class.forName(EGG_CLASS)
                val companion = Class.forName(COMPANION_CLASS)
                val instance = eggClass!!.getField("Companion").get(null)
                @Suppress("UNCHECKED_CAST")
                timerComponent = companion.getMethod("getTIMER").invoke(instance) as DataComponentType<Int>
                @Suppress("UNCHECKED_CAST")
                pokemonPropertiesComponent = companion.getMethod("getPOKEMON_PROPERTIES")
                    .invoke(instance) as DataComponentType<String>
                resolved = true
                unavailable = false
                log.info("Cobreeding bridge resolved — TIMER + POKEMON_PROPERTIES components ready")
                return true
            } catch (e: ClassNotFoundException) {
                warnOnce("Cobreeding not loaded — egg-by-defeat hatching disabled")
            } catch (e: Throwable) {
                warnOnce("Cobreeding reflection failed: ${e.javaClass.simpleName}: ${e.message}")
            }
            resolved = true
            unavailable = true
            return false
        }
    }

    fun available(): Boolean = resolve()

    fun isPokemonEgg(stack: ItemStack): Boolean {
        if (!resolve() || stack.isEmpty) return false
        return eggClass!!.isInstance(stack.item)
    }

    fun getTimer(stack: ItemStack): Int? {
        if (!resolve()) return null
        return stack.get(timerComponent!!)
    }

    fun setTimer(stack: ItemStack, ticks: Int) {
        if (!resolve()) return
        stack.set(timerComponent!!, ticks)
    }

    /** Returns the encrypted PokemonProperties string. Decoding would need Cobreeding's
     *  EggUtilities.decrypt — for chat display we just parse the leading `species=foo` token. */
    fun getPokemonPropertiesRaw(stack: ItemStack): String? {
        if (!resolve()) return null
        return stack.get(pokemonPropertiesComponent!!)
    }

    private fun warnOnce(msg: String) {
        if (warnedOnce.compareAndSet(false, true)) log.warn(msg)
    }

    private fun resolveBreeding(): Boolean {
        if (breedingResolved) return !breedingUnavailable
        synchronized(this) {
            if (breedingResolved) return !breedingUnavailable
            try {
                val cls = Class.forName(BREEDING_DATA_CLASS)
                registryField = cls.getField("registry")
                getEggsMethod = cls.getMethod("getEggs")
                breedingResolved = true
                breedingUnavailable = false
                log.info("Cobreeding breeding registry resolved — parent trade-lock active")
                return true
            } catch (e: Throwable) {
                if (breedingWarnedOnce.compareAndSet(false, true)) {
                    log.warn("Cobreeding breeding registry unavailable — breeding-parent trade-lock disabled: {}", e.message)
                }
                breedingResolved = true
                breedingUnavailable = true
                return false
            }
        }
    }

    /**
     * Snapshot of every active breeding pasture's egg count, keyed by block position. Returns the
     * number of **non-empty** egg stacks currently sitting in each pasture's breeding data (the
     * `eggs` list is a fixed-size NonNullList pre-filled with EMPTY, so size != count). Null when
     * Cobreeding isn't loaded. Called once per second on the server thread, so reading the registry
     * directly is safe (Cobreeding mutates it on the same thread during the pasture tick).
     */
    fun pastureEggCounts(): Map<BlockPos, Int>? {
        if (!resolveBreeding()) return null
        val raw = registryField!!.get(null) as? Map<*, *> ?: return null
        val out = HashMap<BlockPos, Int>(raw.size)
        for ((key, value) in raw) {
            val pos = key as? BlockPos ?: continue
            if (value == null) continue
            val eggs = getEggsMethod!!.invoke(value) as? List<*> ?: continue
            out[pos] = eggs.count { it is ItemStack && !it.isEmpty }
        }
        return out
    }
}
