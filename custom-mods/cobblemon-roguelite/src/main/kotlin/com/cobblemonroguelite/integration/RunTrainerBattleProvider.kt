package com.cobblemonroguelite.integration

import com.cobblemonroguelite.composition.WavePlan
import com.cobblemonroguelite.data.trainer.TrainerPick
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean

private val log = LoggerFactory.getLogger("cobblemon_roguelite/integration")

/**
 * One trainer or boss wave, described without naming anything this module is allowed to name.
 *
 * [trainer] is an id and stays an id all the way here: §1.2/§2.6 leave RCTmod's licence unverified,
 * so nothing in this module may compile against `rctapi` and nothing here can look the trainer up,
 * check it exists, or scale it. An id that names nothing is therefore a failure the *provider*
 * reports, from the only side of the seam that can see the registry.
 *
 * [plan] rides along whole rather than being unpacked into a level and a boss flag. The provider
 * needs the level (§2.6 mutates the NPC's team to it) and the kind (bosses take the ×1.2 the curve
 * has already applied), and handing it the plan means a later field on [WavePlan] reaches
 * implementations without a second version of this class.
 */
data class RunTrainerBattleRequest(
    val plan: WavePlan,
    val trainer: TrainerPick,
)

/**
 * Starts the trainer and boss waves of §2.14.
 *
 * ### Why this is a seam and the wild path is not
 *
 * A wild wave is a Cobblemon Pokémon in a Cobblemon battle, so
 * [com.cobblemonroguelite.run.RunWaveHandler]'s implementation builds it in-module and owes nobody
 * anything. A trainer wave is an **authored RCT trainer** (§2.6), and RCT is a soft dependency whose
 * licence is unverified — so the module can carry the roster, the schedule and the id, and cannot
 * carry the summon. That line is the whole reason the two wave kinds are built by different code:
 * it is a licence boundary, not a design preference.
 *
 * Note what the split buys beyond the licence: §2.14 already de-risked RCT by making wild-wave
 * scaling independent of NPC level mutation. This finishes the job — with no provider registered,
 * 160 of a run's 200 waves still work.
 *
 * ### The provider owns the whole battle, including its end
 *
 * An implementation summons the trainer, scales the team to [RunTrainerBattleRequest.plan]'s level
 * and starts the battle. It does **not** report the outcome, the faints, or who is on the field:
 * the module adopts any battle that starts while a run carries a battle marker
 * (`battle/RunBattles`), so permadeath, §2.10's field tracking and the wave result all run on
 * trainer waves without the provider knowing they exist. A provider that also reported them would
 * double every faint.
 */
fun interface RunTrainerBattleProvider {

    /**
     * Start the wave. Returns false when it could not be started — an id that names no trainer, a
     * summon that failed, RCT missing — in which case the run stays where it is and is resumable.
     *
     * Called on the server thread from inside the wave transition, with the arena already stamped
     * and holding a chunk ticket. An implementation that summons on a *later* tick (RCTmod's
     * `summon_persistent` does) must re-take the ticket through
     * [com.cobblemonroguelite.arena.RunArenas.prepare] and let
     * [com.cobblemonroguelite.arena.ArenaConfig.settleTicks] pass first — a summon into a cold arena
     * fails silently, which is the failure mode this whole arena layer is arranged around.
     *
     * Returning true means "a battle is starting", not "a battle has started": a provider that
     * summons asynchronously should return true and let the module adopt the battle when it appears.
     */
    fun begin(
        server: MinecraftServer,
        player: ServerPlayer,
        request: RunTrainerBattleRequest,
    ): Boolean
}

/**
 * The registered trainer-battle starter, defaulting to one that refuses.
 *
 * ### Why this refuses where [RunCharges] and [RunBattleAi] do not
 *
 * Exactly [com.cobblemonroguelite.run.RunWaves]'s reasoning, and this is the second place it
 * applies. Those two seams have a coherent no-op — a run with no fee, an opponent driven by
 * Cobblemon's own AI — and a *battle* does not. The only no-op available here is "count the wave as
 * won", and a run that counts its 20 trainer waves and 20 boss waves as free wins is a run walked to
 * wave 200 and paid out for it.
 *
 * So a server with no provider plays 160 of the 200 waves and stops at the first trainer wave with
 * its party and its progress intact, which is a worse experience and a far better failure than a
 * mode that pays out for battles nobody fought.
 *
 * ### Threading and registering twice
 *
 * Same contract as the rest of `integration/`: register once during another mod's setup, read on the
 * server thread at the wave transition, `@Volatile` because the ordering between mods is not ours to
 * enforce, last writer wins with a WARN so two mods fighting over the seam is visible in the log
 * rather than in play.
 */
object RunTrainerBattles {

    /** Set the first time [UNIMPLEMENTED] refuses, so the WARN is once per boot and not once per wave. */
    private val warned = AtomicBoolean(false)

    /** Refuses every trainer and boss wave. See the class docs for why this is not a free win. */
    val UNIMPLEMENTED = RunTrainerBattleProvider { _, player, request ->
        if (warned.compareAndSet(false, true)) {
            log.warn(
                "roguelite: no trainer battle provider registered — trainer and boss waves cannot be " +
                    "fought (first refusal was {} at wave {}, trainer '{}'). Wild waves are unaffected; " +
                    "a server that wants the other 40 must register one from a mod that may talk to RCT.",
                player.gameProfile.name, request.plan.wave, request.trainer.trainerId,
            )
        }
        false
    }

    @Volatile
    private var provider: RunTrainerBattleProvider = UNIMPLEMENTED

    /** The active provider. Exposed so a host can assert its own registration won. */
    val current: RunTrainerBattleProvider get() = provider

    fun isImplemented(): Boolean = provider !== UNIMPLEMENTED

    fun register(provider: RunTrainerBattleProvider) {
        val previous = this.provider
        if (previous !== UNIMPLEMENTED) {
            log.warn(
                "roguelite: trainer battle provider {} replaced by {} — only the latter will summon",
                previous.javaClass.name, provider.javaClass.name,
            )
        }
        this.provider = provider
    }

    /** Restore the shipped default. For tests and for unloading a server-side integration. */
    fun reset() {
        provider = UNIMPLEMENTED
        warned.set(false)
    }

    /**
     * Start the wave, treating a thrown provider as a wave that did not start.
     *
     * Fails **closed**, the same call [com.cobblemonroguelite.run.RunWaves.begin] makes and for the
     * same reason: a provider that threw has left the run unadvanced and the party intact, so
     * "it did not happen" is not a guess about the state, it is the state. Failing open here would
     * mean crediting a wave to a summon that blew up.
     */
    fun begin(
        server: MinecraftServer,
        player: ServerPlayer,
        request: RunTrainerBattleRequest,
    ): Boolean =
        runCatching { provider.begin(server, player, request) }
            .onFailure {
                log.error(
                    "roguelite: trainer battle provider failed for wave {} (trainer '{}') — the wave did not start",
                    request.plan.wave, request.trainer.trainerId, it,
                )
            }
            .getOrDefault(false)
}
