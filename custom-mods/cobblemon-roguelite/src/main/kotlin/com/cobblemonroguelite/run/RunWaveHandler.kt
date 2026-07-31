package com.cobblemonroguelite.run

import com.cobblemonroguelite.composition.WavePlan
import com.cobblemonroguelite.data.trainer.TrainerPick
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
 *   in somebody's party. A handler that wants captures routed into the run must carry the flag into
 *   [com.cobblemonroguelite.battle.RunBattles.track]; it defaults to false, and forgetting it costs
 *   the player the catch rather than leaking it — [com.cobblemonroguelite.battle.RunCapture] takes
 *   every capture back out of real storage first and only then asks whether the run may keep it.
 * - **The trainer is handed over, never drawn here.** `trainer` on [beginWave] is the pick the run
 *   already made — reconciled against fixed encounters and against this run's no-repeat memory, and
 *   recorded as the opponent this wave met. A handler that resolves its own would summon somebody
 *   else while the run's own history says otherwise, and a resume would then disagree with the
 *   battle the player remembers fighting.
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
 * - **Report who is on the field**, via [RunController.battleFieldChanged], on every switch and
 *   replacement. §2.10's disconnect penalty kills whatever the run says was out, and the controller
 *   can only stamp the lead at [beginWave] — so a handler that never reports leaves the penalty
 *   correct in size and wrong in aim, with nothing in the log to say so.
 *
 * ### What it does not have to do
 *
 * The §2.10 marker itself. [RunController] stamps it around this call and clears it when the wave
 * resolves — including when [beginWave] returns false — precisely so a handler cannot forget to, and
 * so a battle that dies inside an implementation is still attributable.
 */
fun interface RunWaveHandler {

    /**
     * Begin [plan] for [player]. Returns false if the wave could not be started, in which case the
     * run stays exactly where it is — at the same wave, unfinished, resumable. A failed start must
     * never advance or end a run.
     *
     * @param trainer who this wave fights, or null on a wild wave. Carried through rather than
     *   looked up because the draw is already done and already remembered — see the class docs.
     */
    fun beginWave(
        server: MinecraftServer,
        player: ServerPlayer,
        run: RunState,
        plan: WavePlan,
        trainer: TrainerPick?,
    ): Boolean
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
    val UNIMPLEMENTED = RunWaveHandler { _, player, _, plan, _ ->
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
    fun begin(
        server: MinecraftServer,
        player: ServerPlayer,
        run: RunState,
        plan: WavePlan,
        trainer: TrainerPick?,
    ): Boolean {
        val started = runCatching { handler.beginWave(server, player, run, plan, trainer) }
            .onFailure { log.error("roguelite: wave handler failed for {} at wave {}", player.uuid, plan.wave, it) }
            .getOrDefault(false)
        // Announced from the one choke point every wave passes through, so no handler has to
        // remember to. First playtest (2026-07-31): the player could not tell which wave they were
        // on, which turns "wave 10 felt broken" reports into "some wave felt broken" reports — the
        // wave number is the key every log line is filed under.
        //
        // Action bar AND chat, not either. The chat-only first cut was invisible in practice: the
        // announcement fires the instant the battle starts, which is the instant Cobblemon's battle
        // overlay takes the screen, so the line was only ever discoverable by scrolling back. The
        // action bar renders over the overlay; the chat line stays as the scrollback record.
        if (started) {
            val message = RunMessages.waveStarted(plan, trainer)
            player.sendSystemMessage(message)
            player.displayClientMessage(message, true)
        }
        return started
    }
}
