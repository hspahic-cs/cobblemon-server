package com.cobblemonroguelite.battle

import com.cobblemonroguelite.composition.WavePlan
import com.cobblemonroguelite.data.trainer.TrainerPick
import com.cobblemonroguelite.integration.RunOpponent
import com.cobblemonroguelite.integration.RunTrainerBattleRequest
import com.cobblemonroguelite.integration.RunTrainerBattles
import com.cobblemonroguelite.run.RunState
import com.cobblemonroguelite.run.RunWaveHandler
import com.cobblemonroguelite.run.RunWaves
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("cobblemon_roguelite/battle")

/**
 * The wave-battle seam, filled in: §2.14's two paths and the routing between them.
 *
 * ### The split is a licence boundary, not a design one
 *
 * Wild waves are built here, in full, out of Cobblemon types this module already depends on. Trainer
 * and boss waves are an authored RCT trainer (§2.6) and RCT's licence is unverified (§1.2), so they
 * leave through [RunTrainerBattles] and are somebody else's to start. That is the entire reason there
 * are two paths rather than one, and it is why a server with no provider registered still plays the
 * 160 wild waves of a 200-wave run rather than none of it.
 *
 * ### Routing off [WavePlan.kind], never off the wave number
 *
 * The plan already decided, including any promotion a run's roster applied — a fixed encounter can
 * turn what the schedule calls a wild wave into a boss. Re-deriving the kind here from the interval
 * would fight that promotion and lose in the worst possible way: the wave would come out catchable,
 * at the wild level, and a boss would end up in somebody's party.
 */
object RunWaveBattles : RunWaveHandler {

    override fun beginWave(
        server: MinecraftServer,
        player: ServerPlayer,
        run: RunState,
        plan: WavePlan,
        trainer: TrainerPick?,
    ): Boolean = when (plan.kind) {
        RunOpponent.WILD -> RunWildBattle.start(server, player, run, plan)

        RunOpponent.TRAINER, RunOpponent.BOSS -> {
            if (trainer == null) {
                // The roster served nothing for a wave that needs a trainer. Refused rather than
                // substituted with a wild encounter: a run that quietly turns its boss waves into
                // wild ones is a run whose difficulty curve is wrong in a way nobody will attribute
                // to a missing roster entry.
                log.error(
                    "roguelite: wave {} is a {} wave but the run's roster served no trainer — refusing " +
                        "rather than fighting something else",
                    plan.wave, plan.kind,
                )
                false
            } else {
                RunTrainerBattles.begin(server, player, RunTrainerBattleRequest(plan, trainer))
            }
        }
    }

    /**
     * Wire the module's own battle layer up: the handler, the trackers and §2.11's guard.
     *
     * Registered through [RunWaves.register] like any other implementation rather than being wired in
     * as a special case, so a host mod that wants to replace the whole battle layer still can — and
     * so the "no handler registered" refusal stays the honest state of a build where this was never
     * called.
     */
    fun install() {
        RunWaves.register(this)
        RunBattles.register()
        RunBagGuard.register()
    }
}
