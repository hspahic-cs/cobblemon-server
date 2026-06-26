package com.cobblemonserver.pokeai.ai

import com.cobblemon.mod.common.api.battles.model.actor.AIBattleActor
import com.cobblemon.mod.common.battles.PassActionResponse
import com.cobblemon.mod.common.battles.ShowdownActionResponse
import com.cobblemon.mod.common.util.server
import org.slf4j.LoggerFactory
import java.util.concurrent.Executors

/**
 * Runs a [PokeEngineAI] actor's move selection OFF the Minecraft server thread.
 *
 * The bridge call (poke-engine MCTS) takes ~1-2s. Cobblemon invokes
 * `AIBattleActor.onChoiceRequested()` synchronously on the server thread, so
 * doing the HTTP+search there freezes the whole server for the duration of every
 * AI turn — visible as multi-second "Can't keep up" lag and dropped battle
 * packets (the player's move menu fails to render → apparent softlock).
 *
 * Instead we compute the choice on a worker thread and submit it via
 * [AIBattleActor.setActionResponses] back on the server thread. The battle simply
 * waits for the response — exactly as it already does for a human player who
 * hasn't clicked yet. Reading battle state off-thread is safe here because the
 * battle is paused awaiting this actor's response (nothing mutates its state
 * until every actor has answered); only the final submission touches the battle,
 * and that hops back to the server thread.
 *
 * [AIBattleActorMixin] cancels the vanilla synchronous path when [tryDispatch]
 * takes over.
 */
object AsyncChoiceDispatcher {
    private val log = LoggerFactory.getLogger("cobblemon_poke_ai/async")

    // Daemon pool — one task per in-flight AI choice. Battles now search in
    // parallel instead of serializing on the server thread.
    private val executor = Executors.newCachedThreadPool { r ->
        Thread(r, "pokeai-bridge").apply { isDaemon = true }
    }

    /**
     * If [actorAny] is a PokeEngineAI actor with a pending request, compute its
     * choice off-thread and return true (caller cancels the vanilla path).
     * Returns false for non-pe actors or when there's no request/server, so the
     * vanilla synchronous path runs unchanged.
     */
    fun tryDispatch(actorAny: Any): Boolean {
        val actor = actorAny as? AIBattleActor ?: return false
        if (actor.battleAI !is PokeEngineAI) return false
        val request = actor.request ?: return false
        val server = server() ?: return false // no server thread to hop to (dev/headless) — fall back to sync

        val battle = actor.battle
        val side = actor.getSide()
        val active = actor.activePokemon.toList()

        executor.execute {
            val responses: List<ShowdownActionResponse> = try {
                request.iterate(active) { battleMon, moveset, forceSwitch ->
                    actor.battleAI.choose(battleMon, battle, side, moveset, forceSwitch)
                }
            } catch (e: Throwable) {
                // choose() is already crash-safe internally; this is a last resort.
                log.error("async pe choice failed for battle={}; passing", battle.battleId, e)
                active.map { PassActionResponse }
            }
            server.execute {
                // The battle can advance while we searched off-thread: a faint
                // forces a replacement and Cobblemon issues a NEW request for this
                // actor with a different active mon. `responses` were computed for
                // the OLD request, so applying them makes Cobblemon reject a move
                // the new active mon doesn't have (IllegalActionChoiceException —
                // e.g. Ash's Snorlax told to use Goodra's Draco Meteor). Drop the
                // stale result; the new request triggered its own tryDispatch, so
                // it gets answered by that dispatch (no softlock).
                if (actor.request !== request) {
                    log.warn(
                        "stale pe choice for battle={} — request advanced before " +
                            "dispatch; dropping (current request handled separately)",
                        battle.battleId,
                    )
                    return@execute
                }
                actor.setActionResponses(responses)
                actor.pokemonList.forEach { it.willBeSwitchedIn = false }
            }
        }
        return true
    }
}
