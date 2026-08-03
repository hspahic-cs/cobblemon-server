package com.cobblemonroguelite.battle

import com.cobblemonroguelite.composition.WavePlan
import com.cobblemonroguelite.data.trainer.GeneratedTeam
import com.cobblemonroguelite.data.trainer.TrainerPick
import com.cobblemonroguelite.integration.RunOpponent
import com.cobblemonroguelite.integration.RunTrainerBattleRequest
import com.cobblemonroguelite.integration.RunTrainerBattles
import com.cobblemonroguelite.run.RunRoster
import com.cobblemonroguelite.run.RunRosters
import com.cobblemonroguelite.run.RunSettings
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

        // §2.36's rival leaves through the same seam as the other two, and for the same reason: the NPC
        // is an RCT trainer (`rgl_rival_1`..`rgl_rival_6`), so summoning it is on the far side of the
        // licence boundary. What makes a rival a rival — which meeting, how much of the team it has
        // gained — is decided in this module and handed over as a team, so a provider written before
        // §2.36 needs no change to fight one.
        RunOpponent.TRAINER, RunOpponent.BOSS, RunOpponent.RIVAL -> {
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
                RunTrainerBattles.begin(
                    server, player, RunTrainerBattleRequest(plan, trainer, teamFor(run, plan, trainer)),
                )
            }
        }
    }

    /**
     * The opponent's team when the run's roster generates one for this trainer, null when it does not.
     *
     * ### Why the team is built here and not when the wave was planned
     *
     * Because a plan is made more than once. [com.cobblemonroguelite.run.RunProgress.planFor] is
     * re-run on resume and again after a victory, and it is deliberately cheap and pure; the team is
     * only ever needed at the moment a battle actually starts. Generating it here also keeps
     * [com.cobblemonroguelite.run.WaveStep] and [com.cobblemonroguelite.run.RunWaveHandler] unchanged,
     * so a host that replaced the whole battle layer before §2.30 still compiles.
     *
     * It costs nothing in determinism: generation is a pure function of `(seed, wave)` and the roster,
     * so building it later gives the same team building it earlier would have (see
     * [com.cobblemonroguelite.data.trainer.TrainerTeamGenerator]).
     *
     * ### Re-resolving the roster, and what a missing one means here
     *
     * The roster is re-bound rather than carried, for [com.cobblemonroguelite.run.RunRosters]' reason:
     * `/reload` replaces the registry wholesale, and a roster held from planning time would generate
     * from a file that is no longer on disk. A roster that has gone missing *between* planning and
     * this call falls back to the authored team with a warning — the wave still happens, against the
     * trainer's own RCT team, which is a worse fight and not a lost run.
     */
    private fun teamFor(run: RunState, plan: WavePlan, trainer: TrainerPick): GeneratedTeam? {
        val roster = (RunRosters.bind(run) as? RunRoster.Loaded)?.roster
        if (roster == null) {
            log.warn(
                "roguelite: wave {}'s roster is no longer loaded, so trainer '{}' fights its authored " +
                    "team — a generated one cannot be built without the roster that describes it",
                plan.wave, trainer.trainerId,
            )
            return null
        }
        val team = roster.teamFor(
            trainerId = trainer.trainerId,
            wave = plan.wave,
            // The live curve, not [plan.level]: a generated team's levels are per member
            // (PokéRogue's getPartyLevels spread), so the flat wave level stops here — it still
            // rides the seam for the authored path's forcing and for the log line.
            curve = RunSettings.composition.config.curve,
            boss = plan.kind == RunOpponent.BOSS,
            seed = run.seed,
        )
        // Logged as species and items rather than as properties strings: this line exists so that a
        // "that leader's team was wrong" report can be checked against the roster without replaying
        // the seed, and a wall of `species=... level=...` is not readable at that length.
        if (team != null) {
            log.debug(
                "roguelite: wave {} generated {} for '{}': {}",
                plan.wave, team.members.size, trainer.trainerId, team.describe(),
            )
        }
        return team
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
        // §2.13 and §2.15's two halves. Registered here rather than beside the run lifecycle because
        // both are gated on a wave battle being live, and a build with no battle layer has no way to
        // reach either — a capture guard installed without the battles it guards would be a
        // subscription that can only ever fire on somebody else's catch.
        RunCapture.register()
        RunDexGuard.register()
        // §2.43's run passives: registered with the battle layer because everything they do is
        // gated on battle EXP — a build with no battle layer has no gains for them to touch.
        RunExpPassives.register()
    }
}
