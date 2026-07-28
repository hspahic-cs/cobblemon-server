package com.cobblemonroguelite.integration

import com.cobblemon.mod.common.api.battles.model.ai.BattleAI
import com.cobblemon.mod.common.battles.ai.StrongBattleAI
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("cobblemon_roguelite/integration")

/** The three wave kinds of §2.14. Wild waves are catchable; trainer and boss waves are not. */
enum class RunOpponent { WILD, TRAINER, BOSS }

/**
 * Everything a provider is told about the opponent it is being asked to drive.
 *
 * Deliberately structural, not tuned: wave index and wave kind are facts the run loop already
 * knows, and they are the minimum a provider needs to be more than all-or-nothing. Without the
 * kind, a server-side upgrade could not run a sharper AI on a boss than on a wild encounter
 * without a second seam; with it, the difficulty dial of §2.8 is a provider-side concern and
 * nothing about it has to be decided in this module.
 *
 * Nothing here implies a difficulty curve. Choosing what wave 7 should feel like is reward-table
 * and config territory (§2.12, §2.14), not this interface's business.
 */
data class RunBattleAiRequest(
    val wave: Int,
    val opponent: RunOpponent,
)

/**
 * Supplies the [BattleAI] that drives run opponents.
 *
 * Exists because of §2.8: our poke-engine AI calls out to a self-hosted Python service, and nobody
 * downloading this mod has one. Hard-wiring it would make the mode unusable outside our server and
 * would put a `com.cobblemonserver.pokeai` import in a module that is not allowed to have one
 * (§2.9). So the bridge becomes an opt-in upgrade registered from `cobblemon-bridge`, and what
 * ships is Cobblemon's own AI.
 */
fun interface RunBattleAiProvider {

    /**
     * Build an AI for one opponent. Called per battle, not per turn.
     *
     * Implementations must return a **fresh** instance per call unless they are genuinely
     * stateless: Cobblemon's own [StrongBattleAI] carries a per-battle `ActiveTracker`, so a
     * shared instance would leak one wave's read of the field into the next one.
     */
    fun create(request: RunBattleAiRequest): BattleAI
}

/**
 * The registered AI supplier, defaulting to Cobblemon's [StrongBattleAI].
 *
 * ### Why this default and not ours
 *
 * §2.8 in one line: the shipped default cannot be our bridge. [StrongBattleAI] ships inside
 * Cobblemon itself, which this module already depends on, so it is the only competent AI that is
 * guaranteed present in every install — including a standalone one with no server-side mods at
 * all. Our `PokeEngineAI` already uses it as *its* fallback, so a run whose bridge is down and a
 * run on a published build degrade to exactly the same opponent rather than to two different ones.
 *
 * ### Threading
 *
 * Same contract as [RunPayouts]: register once during setup, read per battle. Reads here are more
 * likely to happen off the server thread than payout reads are (battle setup and Showdown dispatch
 * are not reliably main-thread), so the `@Volatile` is doing real work, not documentation.
 *
 * ### Registering twice
 *
 * Last writer wins, logged at WARN, for the same reason as [RunPayouts.register] — swapping a
 * server-side AI is a legitimate op action and must not be able to brick the mode.
 */
object RunBattleAi {

    /**
     * Skill passed to [StrongBattleAI], which coerces it to 0..5. We ship the top of that range
     * because it is the *absence* of a handicap, not a difficulty decision: [StrongBattleAI]'s
     * skill knob works by rolling to make the AI fail its own choice, and baking a random-blunder
     * rate into the shipped default would silently set the mode's difficulty before anyone has
     * decided what it should be. §2.8's real dial lives with the wave table and the provider.
     */
    const val DEFAULT_SKILL = 5

    /** Cobblemon's own AI, fresh per battle. See the class docs for why this is what ships. */
    val COBBLEMON_STRONG = RunBattleAiProvider { StrongBattleAI(DEFAULT_SKILL) }

    @Volatile
    private var provider: RunBattleAiProvider = COBBLEMON_STRONG

    val current: RunBattleAiProvider get() = provider

    fun isRegistered(): Boolean = provider !== COBBLEMON_STRONG

    fun register(provider: RunBattleAiProvider) {
        val previous = this.provider
        if (previous !== COBBLEMON_STRONG) {
            log.warn(
                "roguelite: battle AI provider {} replaced by {} — only the latter will drive opponents",
                previous.javaClass.name, provider.javaClass.name,
            )
        }
        this.provider = provider
    }

    /** Restore the shipped default. For tests and for unloading a server-side integration. */
    fun reset() {
        provider = COBBLEMON_STRONG
    }

    /**
     * Build the AI for one opponent, falling back to [COBBLEMON_STRONG] if the provider throws.
     *
     * The fallback is not defensive noise: a provider that reaches a network service can fail at
     * construction time (bridge down, config missing), and the alternative to falling back is a
     * run that cannot start its next battle at all. A wave fought against Cobblemon's AI instead
     * of ours is a worse opponent; a wave that never starts is a dead run and a lost party.
     */
    fun create(request: RunBattleAiRequest): BattleAI =
        runCatching { provider.create(request) }
            .onFailure {
                log.error(
                    "roguelite: battle AI provider failed for wave {} ({}) — falling back to StrongBattleAI",
                    request.wave, request.opponent, it,
                )
            }
            .getOrElse { COBBLEMON_STRONG.create(request) }
}
