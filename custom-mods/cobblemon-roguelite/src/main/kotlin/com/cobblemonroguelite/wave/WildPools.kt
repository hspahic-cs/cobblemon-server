package com.cobblemonroguelite.wave

import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean

private val log = LoggerFactory.getLogger("cobblemon_roguelite/wave")

/**
 * Which species wild waves may draw from, for the ~160 waves of a run that are wild ones.
 *
 * ### Why this is a holder here and not a field on `RunConfig`
 *
 * It belongs on the config beside `starterPool`, and it is not there yet because the config is a
 * single data class and adding to it is a change to somebody else's file mid-flight. Putting it here
 * keeps the battle layer buildable now and makes the eventual move a one-line redirect: everything
 * that reads a pool reads [current], and nothing constructs one.
 *
 * The alternative — a hardcoded species list — is the one thing this must not be. [StaticWaveSpeciesPool]
 * already says why: which Pokémon appear at which waves is content, and a placeholder list is exactly
 * the kind of placeholder that quietly becomes the shipped balance.
 *
 * ### The default is empty, and empty means the wave does not start
 *
 * [WildWaveGenerator] answers null for an empty pool and calls it a configuration fault, which is
 * what it is. The battle layer turns that into a refused wave: the run stops where it is with its
 * party intact, and the log names the wave that could not be composed. Substituting anything — a
 * Bulbasaur, a random registry entry — would hide a server with no wild pool behind a mode that
 * merely feels wrong, and would do it 160 waves out of 200.
 */
object WildPools {

    /** Set the first time [EMPTY] is drawn from, so an unconfigured server says so once per boot. */
    private val warned = AtomicBoolean(false)

    /** Offers nothing at every wave. See the class docs for why that refuses rather than substitutes. */
    val EMPTY = WaveSpeciesPool { wave ->
        if (warned.compareAndSet(false, true)) {
            log.warn(
                "roguelite: no wild species pool registered — every wild wave will refuse to start " +
                    "(first was wave {}). Wild waves are most of a run; a server that wants them must " +
                    "register a pool.",
                wave,
            )
        }
        emptyList()
    }

    @Volatile
    private var pool: WaveSpeciesPool = EMPTY

    val current: WaveSpeciesPool get() = pool

    fun isRegistered(): Boolean = pool !== EMPTY

    fun register(pool: WaveSpeciesPool) {
        this.pool = pool
    }

    /** Restore the shipped default. For tests and for unloading a server-side integration. */
    fun reset() {
        pool = EMPTY
        warned.set(false)
    }

    /**
     * A generator over the pool in force, built per call.
     *
     * Per call and not cached for [com.cobblemonroguelite.run.RunSettings]' reason inverted: the
     * generator is stateless and cheap, but it *captures* the pool, so a cached one would keep
     * drawing from the pool that was registered at boot. The curve comes from the live composition
     * config so a wild wave's level and its plan's level stay the same number rather than two
     * opinions about it.
     */
    fun generator(curve: WaveLevelCurve): WildWaveGenerator = WildWaveGenerator(pool, curve)
}
