package com.cobblemonroguelite.run

import com.cobblemonroguelite.composition.WavePlan
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean

private val log = LoggerFactory.getLogger("cobblemon_roguelite/run")

/**
 * Fights one wave. **Nothing implements this yet** — it is the hole the run loop is built around.
 *
 * ### Why the seam is here rather than in `integration/`
 *
 * `integration/` is for things that are *somebody else's* — our economy, our arenas, the AI bridge —
 * and are registered in from outside (§2.9). The wave battle is not that: it is this module's own
 * work, and it is absent because the arena questions it depends on need a dev VM that does not
 * exist yet, not because it belongs to a host. Putting it here keeps that distinction honest, and
 * means the implementation can land as an ordinary class in `run/` without moving a seam.
 *
 * ### What an implementation owes, all of it decided already
 *
 * - **`clone(newUUID = false)` and no `heal()`** when building the battle party from [RunState.party],
 *   or hand the run Pokémon over uncloned. `Pokemon.clone()` defaults to a fresh UUID, which makes
 *   [RunState.kill] a silent no-op — permadeath never fires — and a `heal()` per wave removes the
 *   attrition the mode is built on. This is the single most repeated warning in the design docs
 *   because it fails silently in both directions.
 * - **The player's own party is never touched.** The battle party is synthetic, built from the run
 *   store (design decision 1).
 * - **No personal bag** (§2.11): bag actions are rejected outright for run battles.
 * - **[WavePlan.catchable] is authoritative** (§2.14): wild waves are catchable, trainer and boss
 *   waves never are. Re-deriving it from the wave number in the battle layer is how a boss ends up
 *   in somebody's party.
 * - **The arena is already prepared, and anything summoned into it needs the settle delay.**
 *   [RunController.resume] calls [com.cobblemonroguelite.arena.RunArenas.enter] before this, so the
 *   slot is stamped for the current wave band and its chunks hold a ticket by the time `beginWave` is
 *   called. A handler that summons on a later tick — RCTmod's `summon_persistent` does — must call
 *   [com.cobblemonroguelite.arena.RunArenas.prepare] again first and let
 *   [com.cobblemonroguelite.arena.ArenaConfig.settleTicks] pass. Skipping the ticket is not a
 *   theoretical hazard: a `setblock` into a cold arena was observed failing outright on dev, and the
 *   in-code equivalent fails *silently*.
 * - **Drive the result back through [RunController]** — `waveCleared`, `pokemonFainted`,
 *   `waveLost` — rather than mutating [RunState] directly. The controller owns checkpointing and
 *   run end; a handler that advances the wave itself will produce runs that are not persisted.
 *
 * ### What it must *not* do yet
 *
 * §2.10's disconnect attribution — the boot-identity stamp on battle start and the on-field kills on
 * reconnect — is a separate piece of work and is not part of this interface's contract today. The
 * hook point is [RunController.reconcileOnLogin].
 */
fun interface RunWaveHandler {

    /**
     * Begin [plan] for [player]. Returns false if the wave could not be started, in which case the
     * run stays exactly where it is — at the same wave, unfinished, resumable. A failed start must
     * never advance or end a run.
     */
    fun beginWave(server: MinecraftServer, player: ServerPlayer, run: RunState, plan: WavePlan): Boolean
}

/**
 * The registered wave handler, defaulting to one that cannot fight.
 *
 * ### Why the default refuses instead of pretending
 *
 * Every other default in this module ([com.cobblemonroguelite.integration.RunCharges],
 * [com.cobblemonroguelite.integration.RunPayouts], the AI provider) is chosen so the mode stays
 * playable with nothing registered. This one is the opposite and for the opposite reason: those
 * seams have a *coherent* no-op — a run with no fee, a payout with no bonus — and a wave battle does
 * not. The only no-op available is "count the wave as won", which would let a run be walked to wave
 * 200 by pressing a key, and pay out for it.
 *
 * So the honest default refuses, the run stops where it is with nothing lost, and the player is told
 * the mode is unfinished. That is a worse *experience* than a fake battle and a far better failure.
 */
object RunWaves {

    private val warned = AtomicBoolean(false)

    /** Refuses every wave. See the class docs for why this is not a no-op that returns true. */
    val UNIMPLEMENTED = RunWaveHandler { _, player, _, plan ->
        if (warned.compareAndSet(false, true)) {
            log.warn(
                "roguelite: no wave handler registered — run battles are not implemented yet " +
                    "(first refusal was {} at wave {}). Runs can be started, resumed and abandoned; " +
                    "they cannot be fought.",
                player.gameProfile.name, plan.wave,
            )
        }
        false
    }

    @Volatile
    private var handler: RunWaveHandler = UNIMPLEMENTED

    val current: RunWaveHandler get() = handler

    fun isImplemented(): Boolean = handler !== UNIMPLEMENTED

    fun register(handler: RunWaveHandler) {
        if (this.handler !== UNIMPLEMENTED) {
            log.warn(
                "roguelite: wave handler {} replaced by {}",
                this.handler.javaClass.name, handler.javaClass.name,
            )
        }
        this.handler = handler
    }

    fun reset() {
        handler = UNIMPLEMENTED
        warned.set(false)
    }

    /**
     * Begin a wave, treating a thrown handler as a wave that did not start.
     *
     * Failing *closed* here, unlike [com.cobblemonroguelite.integration.RunCharges] which fails open.
     * The asymmetry is the point: a charge that throws has left us unsure whether the player was
     * debited, and the cheaper mistake is to let them play. A battle that throws has left the run
     * unadvanced and the party intact, so treating it as "did not happen" is not a guess — it is
     * what actually happened.
     */
    fun begin(server: MinecraftServer, player: ServerPlayer, run: RunState, plan: WavePlan): Boolean =
        runCatching { handler.beginWave(server, player, run, plan) }
            .onFailure { log.error("roguelite: wave handler failed for {} at wave {}", player.uuid, plan.wave, it) }
            .getOrDefault(false)
}
