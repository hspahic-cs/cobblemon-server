package com.cobblemonroguelite.run

import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemonroguelite.arena.ArenaFailure
import com.cobblemonroguelite.arena.ArenaResult
import com.cobblemonroguelite.arena.RunArenas
import com.cobblemonroguelite.composition.WavePlan
import com.cobblemonroguelite.data.payout.PayoutEntry
import com.cobblemonroguelite.data.payout.PayoutTables
import com.cobblemonroguelite.data.payout.RunOutcome
import com.cobblemonroguelite.integration.RunCharges
import com.cobblemonroguelite.integration.RunOpponent
import com.cobblemonroguelite.integration.RunPayout
import com.cobblemonroguelite.integration.RunPayouts
import com.cobblemonroguelite.starter.CobblemonPokedexUnlocks
import com.cobblemonroguelite.starter.StarterFactory
import com.cobblemonroguelite.starter.StarterOffer
import com.cobblemonroguelite.starter.StarterOfferFactory
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ThreadLocalRandom

private val log = LoggerFactory.getLogger("cobblemon_roguelite/run")

/** Where a player stands with the mode right now. The three states a command can find them in. */
sealed interface RunStatus {

    data object None : RunStatus

    /** Paid and seeded, still choosing (§2.16). [offer] is recomputed from the persisted seed. */
    data class AwaitingStarter(val pending: PendingStart, val offer: StarterOffer) : RunStatus

    /**
     * A live run. [step] is what would happen if they resumed — usually a wave to fight, but an
     * operator's config change can leave a run whose next step is to end (see [RunProgress]).
     */
    data class InProgress(val run: RunState, val step: WaveStep, val depthCap: Int?) : RunStatus
}

/** Outcome of picking a starter out of an offer. */
sealed interface StarterChoiceResult {

    data class Started(val run: RunState) : StarterChoiceResult

    /** No pending start — they never paid, or they already chose. */
    data object NoPendingStart : StarterChoiceResult

    /** The species is real but was not one of the three they were shown. */
    data object NotOffered : StarterChoiceResult

    /**
     * The species could not be built. The pending start is **kept**, not consumed: the player paid,
     * and leaving the record in place means they can pick again — or pick a different one — once an
     * op has fixed the pool. See [StarterFactory].
     */
    data object SpeciesUnavailable : StarterChoiceResult
}

/** Outcome of trying to get a player back into their run. */
sealed interface ResumeResult {
    data object NoRun : ResumeResult
    data class AwaitingStarter(val offer: StarterOffer) : ResumeResult
    data class WaveStarted(val plan: WavePlan) : ResumeResult

    /** The wave could not be started. The run is untouched and still resumable — see [RunWaves]. */
    data class WaveUnavailable(val plan: WavePlan) : ResumeResult

    /**
     * The arena could not be made ready, so the player was **not** teleported and the run was not
     * advanced. Separate from [WaveUnavailable] because the two need different things from an
     * operator — a missing structure file versus an unimplemented battle — and because this one is
     * the case where continuing anyway would drop somebody into a void dimension.
     */
    data class ArenaUnavailable(val failure: ArenaFailure) : ResumeResult
    data class Ended(val report: RunEndReport) : ResumeResult
}

/**
 * What §2.10's attribution did to a run whose battle was interrupted. Null on the ordinary login.
 *
 * Surfaced to the caller instead of being logged and forgotten because a player who reconnects to a
 * smaller party and no explanation has been handed a bug, not a penalty: the deterrent only works if
 * they know the rule, and the rule only reads as fair if they are told the server *didn't* restart.
 */
sealed interface DisconnectOutcome {

    /** The server restarted under them. The wave is theirs to fight again, unchanged. */
    data class CleanResume(val wave: Int) : DisconnectOutcome

    /**
     * Their connection dropped mid-battle and the field paid for it.
     *
     * @property killed species names of what was taken, in party order — for the message, which has
     *   to name them. Empty means the on-field Pokémon had already fainted before the drop, i.e. the
     *   penalty found nothing left to take.
     * @property resumesAt the wave the run now sits on: [wave] + 1 normally, or [wave] itself where
     *   advancing would have walked the run off its own end. See [RunController.reconcileOnLogin].
     * @property ended set when the penalty was the last of the party, in which case the run is over
     *   and has already been paid out.
     */
    data class Penalised(
        val wave: Int,
        val killed: List<String>,
        val resumesAt: Int,
        val ended: RunEndReport?,
    ) : DisconnectOutcome
}

/**
 * What [RunController.reconcileOnLogin] found, and what it did about it.
 *
 * [status] is computed **after** any attribution, so a run wiped by the penalty reports as
 * [RunStatus.None] rather than as a run with an empty party.
 */
data class LoginReconciliation(
    val status: RunStatus,
    val interrupted: DisconnectOutcome? = null,
)

/**
 * What a finished run paid, in enough detail to answer a dispute.
 *
 * The payout is the single channel out of a sealed run (§1.1), so every field here exists to make
 * "why did I get that" answerable from a log line rather than from a reconstruction: which table,
 * which entries within it, what reached the player and what did not.
 */
data class RunEndReport(
    val cause: RunEndCause,
    val wave: Int,
    val table: ResourceLocation?,
    val entries: List<PayoutEntry>,
    val delivery: PayoutDelivery,
    val bonusPaid: Boolean,
) {
    val outcome: RunOutcome get() = cause.outcome
}

/**
 * The run lifecycle: start, progress, end.
 *
 * ### What this owns, and what it refuses to own
 *
 * It owns the *order* things happen in and the *persistence* boundary. Everything that decides
 * something lives elsewhere and is called from here — [RunStart] for the start order, [RunProgress]
 * for what comes after a wave, [RunDepthGate] for how deep, the payout tables for what a run pays.
 * That is what keeps the interesting parts unit-testable while this class stays a wiring layer that
 * needs a server.
 *
 * It does **not** own the battle. [RunWaves] is the seam and nothing implements it yet, so a run
 * today can be started, resumed, abandoned and ended, and cannot be fought.
 *
 * ### Threading
 *
 * Server thread, all of it. [RunStore.end] says why that matters for the payout in particular: it
 * flushes inline on the server thread and only queues off it, and the queued version reopens a
 * crash window in which a run is restored with its reward already banked. Battle callbacks that
 * arrive off-thread — the AI bridge answers asynchronously — must hop through `server.execute`
 * before calling in here.
 */
object RunController {

    fun status(server: MinecraftServer, player: ServerPlayer): RunStatus {
        val store = RunStore.of(server)
        val run = store.get(player.uuid)
        if (run != null) {
            val cap = depthCapFor(player)
            return RunStatus.InProgress(run, RunProgress.nextStep(run.wave, run.seed, RunSettings.composition, cap), cap)
        }
        val pending = store.pending(player.uuid) ?: return RunStatus.None
        return RunStatus.AwaitingStarter(pending, offerFactory().offerFor(player.uuid, pending.seed))
    }

    /** Price a run for a confirmation prompt. Takes nothing; consumes no free allowance. */
    fun quoteStart(server: MinecraftServer, player: ServerPlayer): RunStartQuote =
        RunStart.quote(ServerRunStartContext(server, player))

    /**
     * Start a run — gate, charge, mint, persist, offer (§2.16, in that order).
     *
     * Refuses outright when a run or a pending start already exists, rather than replacing it the
     * way [RunStore.start] permits. Replacement is a legitimate operation but it discards an
     * unrecoverable party, and a confirmation that came from a `/roguelite start` the player typed
     * to see the *price* is not consent to throw a run away. Abandoning is its own command with its
     * own confirmation.
     */
    fun start(server: MinecraftServer, player: ServerPlayer): RunStartResult? {
        val store = RunStore.of(server)
        if (store.hasRun(player.uuid) || store.pending(player.uuid) != null) return null
        return RunStart.begin(ServerRunStartContext(server, player))
    }

    /**
     * Turn a pending start into a run, with [species] as its only party member at level 1 (§2.21).
     *
     * The seed comes from the persisted [PendingStart] and never from a fresh mint here: this is the
     * other half of §2.16's guarantee, and a re-mint at this point would make the offer the player
     * is looking at describe a run they are not about to play.
     */
    fun chooseStarter(server: MinecraftServer, player: ServerPlayer, species: ResourceLocation): StarterChoiceResult {
        val store = RunStore.of(server)
        val pending = store.pending(player.uuid) ?: return StarterChoiceResult.NoPendingStart
        val offer = offerFactory().offerFor(player.uuid, pending.seed)
        if (!offer.contains(species)) return StarterChoiceResult.NotOffered

        val config = RunSettings.current
        val starter = StarterFactory.create(species, config.starterLevel) ?: return StarterChoiceResult.SpeciesUnavailable

        val run = RunState(
            wave = 1,
            party = mutableListOf(starter),
            seed = pending.seed,
            payoutTable = config.payoutTable,
        )
        // Order: create the run, then drop the pending start. The reverse leaves a crash between the
        // two costing the player their paid start entirely, where this way it costs nothing — the
        // load path drops a pending start whose run already exists.
        store.start(player.uuid, run)
        store.clearPending(player.uuid)

        // The arena is claimed here rather than at the first wave, because this is the moment the run
        // becomes real and therefore the moment [RunStore] starts vouching for the slot being taken.
        // Recorded before any teleport, so a run abandoned at wave 1 still returns them home.
        run.entry = RunEntryPoint.of(player)
        when (val arena = RunArenas.assign(server, run)) {
            is ArenaResult.Success -> Unit
            // Not fatal to the run and deliberately not a refusal: the fee is taken, the party
            // exists, and the start gate already said there was a slot — so this is a server fault
            // between the gate and here. The run stays resumable and [resume] tries the arena again,
            // which is the only path that can succeed once an operator has fixed it.
            is ArenaResult.Failure -> log.error(
                "roguelite: {} started a run but got no arena ({}) — they will be told on resume",
                player.gameProfile.name, arena.error,
            )
        }
        store.checkpoint(server, player.uuid)
        log.info("roguelite: {} started a run (seed={}, starter={})", player.gameProfile.name, run.seed, species)
        return StarterChoiceResult.Started(run)
    }

    /** Put the player back where they were: choosing a starter, or fighting the wave they are on. */
    fun resume(server: MinecraftServer, player: ServerPlayer): ResumeResult {
        val store = RunStore.of(server)
        val run = store.get(player.uuid)
            ?: return store.pending(player.uuid)
                ?.let { ResumeResult.AwaitingStarter(offerFactory().offerFor(player.uuid, it.seed)) }
                ?: ResumeResult.NoRun

        return when (val step = RunProgress.nextStep(run.wave, run.seed, RunSettings.composition, depthCapFor(player))) {
            is WaveStep.EndRun -> ResumeResult.Ended(endRun(server, player.uuid, step.cause))
            is WaveStep.Fight -> {
                // Arena before battle, and a failure here stops the resume dead. The ordering is the
                // decision: [RunArenas.enter] is what stamps the build for this wave band (§2.19) and
                // what force-loads the chunks the wave will summon into, so a handler called first
                // would be summoning into cold, empty void — which fails silently, the way the dev
                // `setblock` did before it was given a chunk ticket.
                when (val arena = RunArenas.enter(server, player, run)) {
                    is ArenaResult.Failure -> ResumeResult.ArenaUnavailable(arena.error)
                    is ArenaResult.Success -> {
                        // Stamped *before* the handler is called, not after. A handler that blocks,
                        // or that hands the battle to a thread and returns, can lose the player
                        // between the two calls, and a battle nobody marked is a battle a player can
                        // walk out of for free — which is the hole §2.10 exists to close.
                        run.battle = RunBattleMarker(step.plan.wave, ServerBootId.current, openingField(run))
                        // Checkpointed because entering just wrote three fields — slot, entry point,
                        // stamped template — and losing them costs the player their way home. The
                        // marker rides along; it does not need its own flush (see [RunState.battle]).
                        store.checkpoint(server, player.uuid)
                        if (RunWaves.begin(server, player, run, step.plan)) {
                            ResumeResult.WaveStarted(step.plan)
                        } else {
                            // No battle started, so there is nothing to attribute. Cleared in memory
                            // only: the stale copy on disk carries this boot's id, and the only way
                            // to read that copy back is a restart, which resolves as a clean resume.
                            run.battle = null
                            ResumeResult.WaveUnavailable(step.plan)
                        }
                    }
                }
            }
        }
    }

    /**
     * The wave in progress was won.
     *
     * Advances and checkpoints; it deliberately does **not** chain straight into the next battle.
     * The between-wave reward and shop steps belong in that gap (§2.12), and a controller that
     * auto-started wave N+1 would have to be unpicked to make room for them.
     *
     * Returns what happens next, so the caller can offer it — or null if there was no run, which
     * means a battle result arrived for a run that has already ended and must be ignored rather than
     * acted on.
     */
    fun waveCleared(server: MinecraftServer, player: UUID): WaveStep? {
        val store = RunStore.of(server)
        val run = store.get(player) ?: return null
        // The battle resolved, so the marker goes. Clearing on *every* exit from a battle is what
        // keeps §2.10 from firing on people who did nothing wrong: a marker left behind by a won
        // wave turns the player's next ordinary logout into a rage-quit.
        run.battle = null
        val composition = RunSettings.composition
        val cleared = composition.planFor(run.wave, run.seed)
        if (cleared.kind == RunOpponent.BOSS) run.bossesCleared++

        // The depth cap is re-read from the player, so a run cleared by a player who has since logged
        // out falls back to "no cap" for this one decision. That errs towards letting the run
        // continue, which the next resume re-checks with the player present.
        val cap = server.playerList.getPlayer(player)?.let { depthCapFor(it) }
        val step = RunProgress.afterVictory(cleared, run.seed, composition, cap)
        return when (step) {
            is WaveStep.Fight -> {
                run.wave = step.plan.wave
                store.checkpoint(server, player)
                step
            }

            is WaveStep.EndRun -> {
                endRun(server, player, step.cause)
                step
            }
        }
    }

    /**
     * Permadeath: [pokemon] is gone from the run for good (§2.13).
     *
     * **Identity contract.** [RunState.kill] matches on UUID, so whatever the battle layer hands back
     * has to be the run Pokémon or a `clone(newUUID = false)` of it. A default `clone()` makes this
     * call a silent no-op: the party never shrinks, the run never wipes, and nothing in the log says
     * so. Nor may the battle layer `heal()` the party between waves — that removes the attrition the
     * whole mode is built on, and it is just as invisible.
     *
     * Returns the wipe report when that was the last party member.
     */
    fun pokemonFainted(server: MinecraftServer, player: UUID, pokemon: Pokemon): RunEndReport? {
        val store = RunStore.of(server)
        val run = store.get(player) ?: return null
        if (!run.kill(pokemon)) {
            log.warn(
                "roguelite: faint reported for {} that is not in {}'s run party — check the battle party " +
                    "is built with clone(newUUID = false)",
                pokemon.uuid, player,
            )
            return null
        }
        if (!run.isWiped()) {
            store.checkpoint(server, player)
            return null
        }
        return endRun(server, player, RunEndCause.PARTY_WIPED)
    }

    /**
     * The wave battle was lost.
     *
     * Under permadeath a defeat should already have arrived as a faint per party member, so this is
     * a reconciliation point rather than the main path: if the party is empty the run ends, and if it
     * is not, the run stays exactly where it is and the discrepancy is logged. Ending a run whose
     * party is still standing would destroy Pokémon that never fainted.
     */
    fun waveLost(server: MinecraftServer, player: UUID): RunEndReport? {
        val run = RunStore.of(server).get(player) ?: return null
        // Same reason as [waveCleared]: the battle is over either way, and a marker that outlives it
        // charges the player for a disconnect that never happened.
        run.battle = null
        if (run.isWiped()) return endRun(server, player, RunEndCause.PARTY_WIPED)
        log.warn(
            "roguelite: {} lost wave {} with {} Pokémon still alive — run left in place",
            player, run.wave, run.partySnapshot().size,
        )
        return null
    }

    /**
     * Walk away from a run (§2.16). The caller **must** have confirmed this with the player: the
     * party it discards is unrecoverable and the fee is not refunded — there is deliberately no
     * refund seam.
     */
    fun abandon(server: MinecraftServer, player: ServerPlayer): RunEndReport? {
        val store = RunStore.of(server)
        // A pending start is a paid run with no party yet. Dropping the record is the whole of
        // abandoning one, and it must still work — otherwise a player who dislikes their offer is
        // stuck with a start they cannot resolve and cannot clear. It still goes through the payout
        // path, as an abandoned run at wave 1: the table author decides whether that pays anything,
        // and short-circuiting it here would decide for them.
        if (!store.hasRun(player.uuid)) {
            return if (store.clearPending(player.uuid) != null) {
                endRun(server, player.uuid, RunEndCause.PLAYER_ABANDONED)
            } else {
                null
            }
        }
        return endRun(server, player.uuid, RunEndCause.PLAYER_ABANDONED)
    }

    /**
     * End a run and pay it out, in that order: resolve the table, hand the grants over, *then* offer
     * the host its optional extra (§2.20).
     *
     * The order is the decision. The module's own payout is the audited channel and does not depend
     * on anything being registered, so it goes first and a provider that throws cannot take it with
     * it — [RunPayouts.pay] swallows exactly because the real payout has already happened by then.
     *
     * Resolution is a **filter, not a draw** ([com.cobblemonroguelite.data.payout.PayoutTable]): two
     * identical runs pay identically, and the variance lives inside the run where it belongs.
     */
    private fun endRun(server: MinecraftServer, player: UUID, cause: RunEndCause): RunEndReport {
        val store = RunStore.of(server)
        val run = store.end(server, player)
        val wave = run?.wave ?: 1
        val outcome = cause.outcome

        // Before the payout, because the payout can throw through a provider and a player left
        // standing in a void arena is the worse of the two failures. The removal from the store has
        // already freed the slot — occupancy is derived from active runs — so this is only the
        // teleport, and it no-ops for a player who is offline or was never in there.
        server.playerList.getPlayer(player)?.let { RunArenas.exit(server, it, run) }

        val tableId = run?.payoutTable ?: PayoutTables.DEFAULT_TABLE
        val table = PayoutTables[tableId]
        if (table == null) {
            // Not an error: §2.20 deferred the contents of the payout and nothing ships at the
            // default id, so "no table" is the shipped state. Logged all the same, because the run
            // that pays nothing and the run that pays nothing *because nobody wrote a table* look
            // identical to the player.
            log.info("roguelite: no payout table '{}' — {} ({}) at wave {} pays nothing", tableId, player, outcome.key, wave)
        }
        val entries = table?.entriesFor(outcome, wave).orEmpty()
        val delivery = RunPayoutDelivery.deliver(server, player, entries.map { it.grant })
        val bonus = RunPayouts.pay(server, player, RunPayout(outcome, wave, table?.id, delivery.delivered))

        log.info(
            "roguelite: run ended for {} — cause={} outcome={} wave={} table={} entries={} delivered={} undelivered={} bonus={}",
            player, cause, outcome.key, wave, table?.id, entries.map { it.id }, delivery.delivered.size,
            delivery.undelivered.size, bonus,
        )
        return RunEndReport(cause, wave, table?.id, entries, delivery, bonus)
    }

    /**
     * The Pokémon out at the start of a wave, as far as this layer can know it.
     *
     * The party lead, because singles is what a wave is and the lead is what a singles battle opens
     * with. It is a **placeholder for the truth**, not the truth: the moment the player switches, the
     * field is something else, and only the battle layer knows that. [battleFieldChanged] is how it
     * says so, and a handler that never calls it leaves the penalty landing on the lead rather than on
     * whoever was actually out — the right *size* of loss aimed at the wrong Pokémon.
     */
    private fun openingField(run: RunState): List<UUID> = run.partySnapshot().take(1).map { it.uuid }

    /**
     * The battle layer reporting who is on the field now — §2.10's penalty is aimed by this.
     *
     * Call it on every switch, faint-replacement and forced swap. Not calling it is not an error and
     * has no visible symptom until somebody disconnects, which is exactly the sort of omission this
     * module's warnings exist for: the penalty still takes one Pokémon, just not the one that was out.
     *
     * A no-op when no battle is marked, so a late report from a battle that already resolved cannot
     * resurrect a marker and turn the player's next logout into a disconnect penalty.
     *
     * **Deliberately does not checkpoint.** A switch can happen every turn and a checkpoint serializes
     * the whole party, and the disk copy of the marker is not what the attribution reads — see
     * [RunState.battle].
     */
    fun battleFieldChanged(server: MinecraftServer, player: UUID, onField: List<Pokemon>) {
        val run = RunStore.of(server).get(player) ?: return
        val marker = run.battle ?: return
        run.battle = marker.copy(onField = onField.map { it.uuid })
    }

    /**
     * Called when a player logs in: §2.10's attribution, then the arena safety net.
     *
     * ### The attribution
     *
     * A run carrying a battle-in-progress marker ([RunBattleMarker]) was interrupted, and the marker's
     * [ServerBootId] says by whom. A different boot means the server restarted under them and the
     * wave is theirs to fight again untouched. The same boot means their connection went away, and
     * the Pokémon that were on the field are killed — permadeath, through [RunState.kill], the same
     * as if they had fainted. Neither side of that comparison is anything the player can reach, which
     * is the whole reason it is the boot id being compared and not, say, a timestamp.
     *
     * **The run then continues at the next wave**, which is the half of the decision that makes the
     * penalty a penalty rather than a rewind: leaving them on the same wave would let them re-fight it
     * from the checkpoint, which is the retry exploit §2.3 hands to §2.10 to close.
     *
     * The one exception is a next wave that would not be a wave at all — the final wave, a lowered run
     * length, the badge cap. Advancing there would end the run, and every one of those endings pays as
     * a *completed* run (see [RunEndCause]), so pulling the plug on wave 200 would pay exactly like
     * clearing it. So the advance only happens into a real fight; otherwise the run stays where it is
     * and the ordinary resume path ends it properly, with the player present and the right cause.
     *
     * Killing may of course wipe the party, and that goes through [endRun] like any other wipe —
     * payout, arena exit, store removal. A run left sitting at zero party members would be restored
     * from its next checkpoint as no run at all ([RunState.fromNbt] discards an empty party), and the
     * player would never be paid.
     *
     * ### The arena half
     *
     * Not §2.10 and not optional: a
     * player who logs in inside an arena with no run to be there for is in a void dimension with no
     * bed, no portal and nothing to fall onto, i.e. stuck permanently. That happens whenever a run
     * ends while its owner is offline — expiry, an operator clearing a run — because the teleport in
     * [endRun] can only move a player who is connected.
     *
     * Note what is lost in that case: the return position lived on the [RunState] that just ended, so
     * these players go to world spawn rather than to where they started. Preserving it would mean
     * keeping a record of ended runs purely to hold one position, and the alternative — leaving them
     * in the void — is not a trade.
     *
     * Returns what happened so the caller can tell the player; sends nothing itself.
     */
    fun reconcileOnLogin(server: MinecraftServer, player: ServerPlayer): LoginReconciliation {
        val run = RunStore.of(server).get(player.uuid)
        if (run != null) {
            val interrupted = attributeInterruption(server, player, run)
            // Status read *after* the attribution, never before: the penalty can move the wave and can
            // end the run outright, and a status captured first would describe a run that is no longer
            // there — including handing the login hook an InProgress holding an emptied party.
            return LoginReconciliation(status(server, player), interrupted)
        }
        val status = status(server, player)
        if (RunArenas.isInArena(player)) {
            log.info("roguelite: {} logged in inside an arena with no run — ejecting", player.gameProfile.name)
            RunArenas.eject(server, player, entry = null)
        }
        return LoginReconciliation(status)
    }

    /** §2.10, one login's worth. Null when there was no battle to attribute, which is most logins. */
    private fun attributeInterruption(
        server: MinecraftServer,
        player: ServerPlayer,
        run: RunState,
    ): DisconnectOutcome? {
        val party = run.partySnapshot()
        val verdict = DisconnectAttribution.verdict(run.battle, ServerBootId.current, party.map { it.uuid })
        return when (verdict) {
            is DisconnectVerdict.NoBattle -> {
                log.debug("roguelite: {} logged in mid-run at wave {}", player.gameProfile.name, run.wave)
                null
            }

            is DisconnectVerdict.ServerRestarted -> {
                // Cleared and checkpointed rather than left alone. The marker has served its purpose,
                // and one that survives this login is a marker the *next* disconnect would compare
                // against a boot that now matches — charging them for our restart, one login late.
                run.battle = null
                RunStore.of(server).checkpoint(server, player.uuid)
                log.info(
                    "roguelite: {} was mid-wave {} across a restart — resuming clean, no penalty",
                    player.gameProfile.name, verdict.wave,
                )
                DisconnectOutcome.CleanResume(verdict.wave)
            }

            is DisconnectVerdict.PlayerDropped -> penalise(server, player, run, verdict)
        }
    }

    private fun penalise(
        server: MinecraftServer,
        player: ServerPlayer,
        run: RunState,
        verdict: DisconnectVerdict.PlayerDropped,
    ): DisconnectOutcome {
        val store = RunStore.of(server)
        val onField = verdict.casualties.toSet()
        // The `kill` filter is not belt-and-braces: a Pokémon can have fainted between the drop and
        // this login — a battle thread finishing its last turn into a disconnected player — and
        // permadeath already took it. `kill` answers false there and it is reported as nothing,
        // because telling somebody they lost the same Pokémon twice is how a correct penalty reads
        // as a duplicate bug.
        val killed = run.partySnapshot()
            .filter { it.uuid in onField && run.kill(it) }
            .map { it.species.name }
        run.battle = null

        if (run.isWiped()) {
            // Through [endRun] and not by leaving the run sitting empty: the party-wipe payout, the
            // arena exit and the removal from the store all live there, and a zero-party run would be
            // silently discarded by the next load as "no run" — the player would simply never be paid.
            val report = endRun(server, player.uuid, RunEndCause.PARTY_WIPED)
            log.info(
                "roguelite: {} dropped mid-wave {} — lost {} and wiped",
                player.gameProfile.name, verdict.wave, killed,
            )
            return DisconnectOutcome.Penalised(verdict.wave, killed, run.wave, report)
        }

        // Advance only if the penalty actually took something. A drop that found nothing on the field
        // left to take is not a penalty, and advancing on it would be a free wave skip — the exact
        // trade §2.10 is meant to make unprofitable, handed over for nothing.
        if (killed.isNotEmpty()) {
            val next = RunProgress.nextStep(verdict.wave + 1, run.seed, RunSettings.composition, depthCapFor(player))
            // Only into a real fight — see the class-level note on why an advance that would *end* the
            // run is refused here and left to the resume path. And only forwards: a marker that
            // somehow outlived a wave advance would otherwise move the run *back* to the wave it names,
            // which is the retry exploit arriving through the door built to close it.
            if (next is WaveStep.Fight && next.plan.wave > run.wave) run.wave = next.plan.wave
        }
        store.checkpoint(server, player.uuid)
        log.info(
            "roguelite: {} dropped mid-wave {} — lost {}, run continues at wave {}",
            player.gameProfile.name, verdict.wave, killed, run.wave,
        )
        return DisconnectOutcome.Penalised(verdict.wave, killed, run.wave, null)
    }

    /** Null means no cap. Denied reads as depth zero, which [RunProgress] ends the run on. */
    private fun depthCapFor(player: ServerPlayer): Int? =
        when (val gate = RunSettings.current.depthGate.evaluate(VanillaAdvancements.of(player))) {
            is DepthGateResult.Allowed -> gate.maxWave
            is DepthGateResult.Denied -> 0
        }

    /**
     * Built per call rather than held. The pool source lives in configuration that can be replaced at
     * runtime, and a cached factory would keep serving the pool that was configured at boot.
     */
    private fun offerFactory(): StarterOfferFactory =
        StarterOfferFactory(RunSettings.current.starterPool, CobblemonPokedexUnlocks)

    /**
     * The five start steps, bound to a real server and player. Nothing decides anything here — the
     * order is [RunStart]'s and each of these is one call.
     */
    private class ServerRunStartContext(
        private val server: MinecraftServer,
        private val player: ServerPlayer,
    ) : RunStartContext {

        override fun depthGate(): DepthGateResult =
            RunSettings.current.depthGate.evaluate(VanillaAdvancements.of(player))

        override fun arenaAvailable(): Boolean = RunArenas.hasCapacity(server)

        override fun charge(quoteOnly: Boolean) =
            if (quoteOnly) RunCharges.quote(server, player.uuid) else RunCharges.charge(server, player.uuid)

        /**
         * A random seed rather than a counter or the clock. Consecutive seeds are what a counter
         * produces and what a coarse clock approximates, and [StarterOfferFactory] has to mix hard
         * to keep those from correlating — feeding it uncorrelated values costs nothing and removes
         * the dependency on that mixing being good enough.
         */
        override fun mintSeed(): Long = ThreadLocalRandom.current().nextLong()

        override fun persistSeed(seed: Long) {
            RunStore.of(server).beginPending(server, player.uuid, PendingStart(seed, System.currentTimeMillis()))
        }

        override fun starterOffer(seed: Long): StarterOffer =
            StarterOfferFactory(RunSettings.current.starterPool, CobblemonPokedexUnlocks).offerFor(player.uuid, seed)
    }
}
