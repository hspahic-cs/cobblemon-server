package com.cobblemonroguelite.run

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemonroguelite.arena.ArenaFailure
import com.cobblemonroguelite.arena.ArenaResult
import com.cobblemonroguelite.arena.RunArenas
import com.cobblemonroguelite.composition.WaveComposition
import com.cobblemonroguelite.composition.WavePlan
import com.cobblemonroguelite.data.biome.RunBiomes
import com.cobblemonroguelite.data.payout.PayoutEntry
import com.cobblemonroguelite.data.payout.PayoutTables
import com.cobblemonroguelite.data.payout.RunOutcome
import com.cobblemonroguelite.integration.RunCharges
import com.cobblemonroguelite.integration.RunOpponent
import com.cobblemonroguelite.integration.RunPayout
import com.cobblemonroguelite.integration.RunPayouts
import com.cobblemonroguelite.progression.RunProgression
import com.cobblemonroguelite.shop.BetweenWaveMenu
import com.cobblemonroguelite.shop.ShopSettings
import com.cobblemonroguelite.starter.CobblemonPokedexUnlocks
import com.cobblemonroguelite.starter.StarterCatalogue
import com.cobblemonroguelite.starter.StarterCatalogueFactory
import com.cobblemonroguelite.starter.StarterFactory
import com.cobblemonroguelite.starter.StarterProgression
import com.cobblemonroguelite.starter.StarterSelection
import com.cobblemonroguelite.starter.StarterSelectionResult
import com.cobblemonroguelite.starter.StarterTeamResult
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ThreadLocalRandom

private val log = LoggerFactory.getLogger("cobblemon_roguelite/run")

/** How long a freshly-installed party settles before its first battle. See the resume path. */
private const val PARTY_SYNC_DELAY_TICKS = 20

/** Where a player stands with the mode right now. The three states a command can find them in. */
sealed interface RunStatus {

    data object None : RunStatus

    /**
     * Paid and seeded, still buying a team (§2.16). [catalogue] is rebuilt from the player's unlocks
     * and the price table each time it is asked for — under §2.13's budget it is not derived from the
     * seed, so there is nothing to reconstruct and nothing to keep in step with the persisted start.
     */
    data class AwaitingStarter(val pending: PendingStart, val catalogue: StarterCatalogue) : RunStatus

    /**
     * A live run. [step] is what would happen if they resumed — usually a wave to fight, but an
     * operator's config change can leave a run whose next step is to end (see [RunProgress]).
     */
    data class InProgress(val run: RunState, val step: WaveStep, val depthCap: Int?) : RunStatus
}

/** Outcome of buying a starting team out of a catalogue (§2.13). */
sealed interface StarterChoiceResult {

    /** [spent] and [remaining] are carried so the player is told what the budget went on. */
    data class Started(val run: RunState, val spent: Int, val remaining: Int) : StarterChoiceResult

    /** No pending start — they never paid, or they already bought a team. */
    data object NoPendingStart : StarterChoiceResult

    /**
     * The selection did not pass [StarterSelection]. The whole result is carried rather than
     * flattened to a boolean because every one of its cases needs different words, and half of them
     * name a species or a number the player has to see to fix it.
     */
    data class Rejected(val reason: StarterSelectionResult) : StarterChoiceResult

    /**
     * A species in an otherwise legal team could not be built on this server. The pending start is
     * **kept**, not consumed: the player paid, and leaving the record in place means they can buy
     * again — or buy something else — once an op has fixed the pool. See [StarterFactory].
     */
    data class SpeciesUnavailable(val species: ResourceLocation) : StarterChoiceResult
}

/** Outcome of trying to get a player back into their run. */
sealed interface ResumeResult {
    data object NoRun : ResumeResult
    data class AwaitingStarter(val catalogue: StarterCatalogue) : ResumeResult
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

    /**
     * The run's pinned trainer roster is not loaded, so the wave could not be composed at all. Like
     * [WaveUnavailable] the run is untouched and still resumable; unlike it, the fix is a datapack and
     * not a build — see [RunRoster] for why this refuses rather than composing the wave without it.
     */
    data class RosterUnavailable(val rosterId: ResourceLocation?) : ResumeResult

    /**
     * The inventory could not be stashed (isolation design E0–E5), so the wave did not start and
     * nothing observable changed. The reason is already worded for the player — every E-protocol
     * refusal is — so the command layer relays it rather than translating it.
     */
    data class StashRefused(val reason: String) : ResumeResult

    /**
     * §2.13: a caught Pokémon is waiting on a swap-or-release decision, so the run does not advance.
     *
     * A gate rather than a nudge. The held catch lives in one field on the run and nowhere else, so a
     * run that fought on with it pending would carry it to the end and destroy it there — by a
     * decision the player was never asked to make, which is the exact failure the prompt exists to
     * avoid. Blocking also keeps the prompt readable as a decision: it is the only thing between them
     * and the next wave.
     */
    data class CatchPending(val pokemon: Pokemon, val party: List<Pokemon>) : ResumeResult

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
     * @property resumesAt the wave the run now sits on. Since a drop no longer moves a run
     *   ([RunController.reconcileOnLogin]) this is [wave] in every ordinary case, and it is kept as a
     *   separate field because it is read off the run rather than off the marker: the two differing
     *   means a marker outlived a wave advance, and in that case the wave the player is *told* to go
     *   back to must be the run's, not the marker's.
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
 * [status] is computed **after** every one of the steps below, so a run that the penalty wiped or that
 * expiry deleted reports as [RunStatus.None] rather than as a run that is no longer there.
 *
 * @property interrupted §2.10's attribution, or null on the ordinary login.
 * @property expired §2.23: a run deleted while they were away, either by the boot sweep or by the check
 *   on this very login. Both arrive the same way — through the notice in [RunStore] — so the caller has
 *   one case to say rather than two, and the sentence does not depend on which one happened.
 * @property expiring set only when the surviving run is close enough to expiry to be worth a warning.
 *   Null on every ordinary login, which is what keeps this from being a line players learn to ignore.
 * @property returnedFromArena §2.23: they logged in standing in arena space and were put back outside
 *   it, because the lease on that arena ended when they logged out. Carried rather than merely done,
 *   since a teleport nobody explains is worse than the position it corrected.
 */
data class LoginReconciliation(
    val status: RunStatus,
    val interrupted: DisconnectOutcome? = null,
    val expired: RunExpiryNotice? = null,
    val expiring: RunExpiryStatus? = null,
    val returnedFromArena: Boolean = false,
)

/**
 * What a finished run paid, in enough detail to answer a dispute.
 *
 * The payout is the single channel out of a sealed run (§1.1), so every field here exists to make
 * "why did I get that" answerable from a log line rather than from a reconstruction: which table,
 * which entries within it, what reached the player and what did not.
 *
 * @property startedUnderOverride §2.25, carried out of the run at the one moment anything downstream
 *   would record a result. A leaderboard, a payout audit or a "deepest run this month" line reads this
 *   report and nothing else, so an inflated run that did not say so here would be indistinguishable
 *   from an honest one the instant the [RunState] was discarded.
 */
data class RunEndReport(
    val cause: RunEndCause,
    val wave: Int,
    val table: ResourceLocation?,
    val entries: List<PayoutEntry>,
    val delivery: PayoutDelivery,
    val bonusPaid: Boolean,
    val startedUnderOverride: Boolean = false,
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
            return RunStatus.InProgress(run, nextStep(run, cap), cap)
        }
        val pending = store.pending(player.uuid) ?: return RunStatus.None
        return RunStatus.AwaitingStarter(pending, catalogueFactory().catalogueFor(player.uuid))
    }

    /**
     * §2.22: what leaving right now would cost. Reads; writes nothing, ends nothing, takes nothing.
     *
     * Goes through the store rather than through [status] on purpose. [status] composes the next
     * wave, which is work this question does not need and, more to the point, is a different fact:
     * pausing is about the battle that is open *now*, and the only thing that knows about it is the
     * marker.
     */
    fun pause(server: MinecraftServer, player: ServerPlayer, confirmed: Boolean): PauseAdvice {
        val store = RunStore.of(server)
        val advice = RunPause.advise(store.get(player.uuid), store.pending(player.uuid) != null, confirmed)

        // D1 (isolation design): pause is a FULL arena exit — the human's stated intent, and the fix
        // for draft 1's fatal case (F1: a paused player standing in the arena holding their real
        // inventory, one death away from the drops guard voiding it, holding their dynamax_band next
        // to the power spot). The X-protocol first, then the party, then the same teleport-and-release
        // RunArenas.exit does for a run end. After this block the player is an ordinary citizen of the
        // server holding nothing of the run's — which is what makes the D2 command refusal, the drops
        // guard and the ender-chest guard all correct in keying on the tag: a paused player has none.
        //
        // Only from between waves. Mid-battle pause stays §2.22 disclosure-only, because the live
        // battle holds BattlePokemon wrapping these objects, and pulling them out from under it is
        // worse than the leak — the acknowledgement already says leaving now costs the wave.
        if (advice is PauseAdvice.BetweenWaves) {
            val run = store.get(player.uuid)
            runCatching { RunInventoryStash.exitSwap(player, run) }
                .onFailure { log.error("roguelite: pause exit swap failed for {}", player.gameProfile.name, it) }
            runCatching { RunPartySwap.restore(player) }
                .onFailure { log.error("roguelite: could not hand {}'s own party back on pause", player.gameProfile.name, it) }
            RunArenas.exit(server, player, run)
            store.checkpoint(server, player.uuid)
            run?.let { player.sendSystemMessage(RunMessages.pausedFully(it.wave)) }
        }
        return advice
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
     * Turn a pending start into a run, with [species] as its starting party at level 1 (§2.21).
     *
     * The team is **not** a full party and must not be assumed to be one: at §2.13's prices 10 points
     * buys two or three Pokémon, and catching is what takes a run to six.
     *
     * The seed comes from the persisted [PendingStart] and never from a fresh mint here — §2.16's
     * other half. It no longer decides the catalogue (a budget is not rolled), but it does decide the
     * team's IVs ([com.cobblemonroguelite.starter.StarterIvRoll]), so a re-mint at this point would
     * hand the player a different team from the one their paid, persisted start describes.
     */
    fun chooseStarters(
        server: MinecraftServer,
        player: ServerPlayer,
        species: List<ResourceLocation>,
    ): StarterChoiceResult {
        val store = RunStore.of(server)
        val pending = store.pending(player.uuid) ?: return StarterChoiceResult.NoPendingStart

        val catalogue = catalogueFactory().catalogueFor(player.uuid)
        val selection = StarterSelection.validate(catalogue, species)
        if (selection !is StarterSelectionResult.Accepted) return StarterChoiceResult.Rejected(selection)

        val config = RunSettings.current
        // The IV floor is looked up per species here rather than inside the factory, so that the
        // factory never receives a player — the same separation the cost source keeps (§2.15).
        val progression = StarterProgression.current
        val team = when (
            val built = StarterFactory.createTeam(
                species = selection.team.map { it.species },
                level = config.starterLevel,
                runSeed = pending.seed,
                ivFloor = { progression.ivFloor(player.uuid, it) },
                // §2.27, looked up here for the same reason the floor is: the factory never receives
                // a player. The store resolves §2.17's evolution-line root behind this call, so a
                // Charizard starter reads the unlock bought on Charmander.
                hiddenAbilityUnlocked = { progression.hiddenAbilityUnlocked(player.uuid, it) },
            )
        ) {
            is StarterTeamResult.Built -> built.team
            is StarterTeamResult.Unavailable -> return StarterChoiceResult.SpeciesUnavailable(built.species)
        }

        val run = RunState(
            wave = 1,
            party = team.toMutableList(),
            seed = pending.seed,
            payoutTable = config.payoutTable,
            // Pinned here and never re-read from config again, for the reason [RunConfig.trainerRoster]
            // gives: two hundred waves is days of play, and an operator swapping rosters must not move
            // a run that is already halfway up a ladder.
            trainerRoster = config.trainerRoster,
            // §2.25: stamped once, here, and never written again. Turning the override on halfway
            // through somebody's run must not retroactively mark it — and turning it *off* must not
            // launder a run that was started under it, which is the direction that matters.
            startedUnderOverride = RunDepthOverrides.isActive(player.uuid),
        )
        if (run.startedUnderOverride) {
            log.warn(
                "roguelite: {} started a run under an OPERATOR DEPTH OVERRIDE (§2.25) — it is marked as " +
                    "such for the rest of its life and its depth is not earned",
                player.gameProfile.name,
            )
        }
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
        log.info(
            "roguelite: {} started a run (seed={}, team={}, spent={}/{})",
            player.gameProfile.name, run.seed, species, selection.spent, catalogue.budget,
        )
        return StarterChoiceResult.Started(run, selection.spent, selection.remaining)
    }

    /** Put the player back where they were: choosing a starter, or fighting the wave they are on. */
    fun resume(server: MinecraftServer, player: ServerPlayer): ResumeResult {
        val store = RunStore.of(server)
        val run = store.get(player.uuid)
            ?: return store.pending(player.uuid)
                ?.let { ResumeResult.AwaitingStarter(catalogueFactory().catalogueFor(player.uuid)) }
                ?: ResumeResult.NoRun

        // Checked before the step is composed, not after. Composing is harmless, but [WaveStep.Fight]
        // is followed immediately by an arena entry and a teleport, and a player moved into their
        // arena to be told they cannot fight yet is a worse answer than the same words with nothing
        // moved. Deliberately does *not* claim a catch that would now fit — see [catchPrompt] for
        // why that side effect belongs on the command that reports it.
        run.pendingCatch?.let { return ResumeResult.CatchPending(it, run.partySnapshot()) }

        // User decision 2026-07-31: an all-fainted party at resume ENDS the run as a wipe, rather
        // than refusing forever — there is no healing outside the run's own shop, so a refusal was a
        // soft-lock wearing a message. The order matters against the revive-purchase path: the
        // between-wave shop opens on waveCleared, BEFORE anyone calls resume, so a player who still
        // has a marked Revive (or the money for one) uses it there; reaching resume with everyone
        // still down is the decision to stop. Faint-outside-battle is how this state arises at all —
        // battle wipes end through pokemonFainted — and whether run Pokémon should be usable as
        // vehicles in the first place remains an open question.
        if (run.partySnapshot().none { pokemon -> runCatching { !pokemon.isFainted() }.getOrDefault(true) }) {
            player.sendSystemMessage(RunMessages.partyFainted())
            return ResumeResult.Ended(endRun(server, player.uuid, RunEndCause.PARTY_WIPED))
        }

        return when (val step = nextStep(run, depthCapFor(player))) {
            is WaveStep.EndRun -> ResumeResult.Ended(endRun(server, player.uuid, step.cause))
            // Logged at ERROR because only an operator can act on it and nothing else will say so: the
            // player sees a run that will not start, and the datapack that would explain it is the one
            // that is missing.
            is WaveStep.NoRoster -> {
                log.error(
                    "roguelite: {} cannot fight wave {} — trainer roster '{}' is not loaded, so the wave " +
                        "cannot be composed (promotions are unknowable without it). The run is intact.",
                    player.gameProfile.name, step.wave, step.rosterId,
                )
                ResumeResult.RosterUnavailable(step.rosterId)
            }

            is WaveStep.Fight -> {
                // Arena before battle, and a failure here stops the resume dead. The ordering is the
                // decision: [RunArenas.enter] is what stamps the build for this wave band (§2.19) and
                // what force-loads the chunks the wave will summon into, so a handler called first
                // would be summoning into cold, empty void — which fails silently, the way the dev
                // `setblock` did before it was given a chunk ticket.
                //
                // Read before the call and compared after, because the transition is the arena's to
                // decide and the player's to be told about, and those are two layers. See
                // [announceBiome].
                val wasIn = run.biome?.biome
                // The two swaps, in the isolation design's order: inventory first (E-protocol —
                // every refusal happens before anything observable changes), then the party. Both are
                // arena-session-keyed now: they run here, on the way in, and NOWHERE else — the login
                // reconcile sweeps rather than installs (§2's revised contract).
                when (val stash = RunInventoryStash.enterSwap(player, run)) {
                    is RunInventoryStash.EntryResult.Refused -> {
                        player.sendSystemMessage(RunMessages.stashRefused(stash.reason))
                        return ResumeResult.StashRefused(stash.reason)
                    }
                    RunInventoryStash.EntryResult.Ok -> Unit
                }
                val partyFreshlyInstalled = runCatching { RunPartySwap.reconcile(player, run) }
                    .onFailure { log.error("roguelite: could not install {}'s run party", player.gameProfile.name, it) }
                    .getOrDefault(false)
                when (val arena = RunArenas.enter(server, player, run)) {
                    is ArenaResult.Failure -> {
                        // Both swaps undone: the player was not moved, and leaving them outside the
                        // arena tagged and holding run Pokémon is exactly the leak window §2 closes.
                        // exitSwap is the X-protocol, so this is a pause in all but name.
                        runCatching { RunInventoryStash.exitSwap(player, run) }
                            .onFailure { log.error("roguelite: could not undo the entry swap after an arena failure", it) }
                        runCatching { RunPartySwap.restore(player) }
                            .onFailure { log.error("roguelite: could not undo the party install after an arena failure", it) }
                        ResumeResult.ArenaUnavailable(arena.error)
                    }
                    is ArenaResult.Success -> {
                        announceBiome(player, run, wasIn)
                        // Stamped *before* the handler is called, not after. A handler that blocks,
                        // or that hands the battle to a thread and returns, can lose the player
                        // between the two calls, and a battle nobody marked is a battle a player can
                        // walk out of for free — which is the hole §2.10 exists to close.
                        val startWave: () -> Boolean = {
                            run.battle = RunBattleMarker(step.plan.wave, ServerBootId.current, openingField(run))
                            // §2.23's activity clock. Stamped on the wave *starting* and not only on it
                            // being cleared, because a player who is fighting is playing: a run parked
                            // on a wave somebody keeps failing to survive is in use, and an expiry that
                            // only counted wins would delete it out from under the attempt.
                            run.touch()
                            // Checkpointed because entering just wrote the entry point, and losing it
                            // costs the player their way home. The marker and the activity stamp ride
                            // along; neither needs its own flush (see [RunState.battle]).
                            store.checkpoint(server, player.uuid)
                            // `step.trainer` is who this wave fights, already reconciled against fixed
                            // encounters and this run's no-repeat window. A handler that drew its own
                            // would summon a different opponent from the one the run planned.
                            val ok = RunWaves.begin(server, player, run, step.plan, step.trainer)
                            // On refusal there is nothing to attribute. Cleared in memory only: the
                            // stale copy on disk carries this boot's id, and the only way to read that
                            // copy back is a restart, which resolves as a clean resume.
                            if (!ok) run.battle = null
                            ok
                        }
                        if (!partyFreshlyInstalled) {
                            if (startWave()) {
                                ResumeResult.WaveStarted(step.plan)
                            } else {
                                ResumeResult.WaveUnavailable(step.plan)
                            }
                        } else {
                            // The first live test's bug #5: a battle begun in the same tick as a party
                            // install builds the client's battle GUI against the party it had BEFORE
                            // the install packets applied — no move buttons, no opponent HP bar, stuck
                            // at T0. One second of settle is invisible next to the arena teleport, and
                            // only the first wave of a session pays it (every later wave's party is
                            // already installed). The preconditions are re-checked at fire time
                            // because a player can pause or vanish inside twenty ticks.
                            RunTicks.schedule(PARTY_SYNC_DELAY_TICKS) {
                                val online = server.playerList.getPlayer(player.uuid) ?: return@schedule
                                if (!RunInventoryStash.isTagged(online)) return@schedule
                                if (!startWave()) {
                                    online.sendSystemMessage(RunMessages.waveUnavailable(step.plan.wave, step.plan.kind))
                                }
                            }
                            ResumeResult.WaveStarted(step.plan)
                        }
                    }
                }
            }
        }
    }

    /**
     * §2.24: tell the player where they now are, when it is somewhere new.
     *
     * Keyed on the biome id changing rather than on the band advancing, and the difference is one a
     * player would notice: a rotation is free to draw the same biome twice in a row, and "you enter
     * the Grassy Field" printed while standing in the Grassy Field reads as a bug in a message that
     * exists to make a transition legible.
     *
     * Silent when the biome id no longer resolves — a datapack deleted mid-run. There is no display
     * name to say, and naming the raw id would show the player a file path in place of a place.
     */
    private fun announceBiome(player: ServerPlayer, run: RunState, previous: ResourceLocation?) {
        val current = run.biome?.biome ?: return
        if (current == previous) return
        val biome = RunBiomes[current] ?: return
        player.sendSystemMessage(RunMessages.enteredBiome(biome.displayName))
        log.info(
            "roguelite: {} entered biome '{}' at wave {}", player.gameProfile.name, biome.id, run.wave,
        )
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
        // §2.23 again, and the one that carries the guarantee: a wave was progressed, which is the
        // definition of activity. [resume] stamps it too, so this is only ever a small correction — but
        // it is the stamp that is true of a run whose owner is grinding without relogging, and losing it
        // would age a run that is being played every day.
        run.touch()
        val composition = RunSettings.composition
        val roster = RunRosters.bind(run)
        val cleared = clearedPlan(run, composition, roster)
        // Through the roster's plan and not the composition's, so a promoted Elite Four wave counts as
        // the boss battle it was fought as. §2.20's payout curves read this number.
        if (cleared.plan.kind == RunOpponent.BOSS) run.bossesCleared++
        // The one place the no-repeat window is written, and it is written for the wave that is now
        // *finished*. Recording at the start of a wave instead would double-count a wave re-fought
        // after §2.10's disconnect penalty, and recording on every plan would let `/roguelite status`
        // change who the player is about to meet.
        cleared.trainer?.let { run.trainerMemory.record(cleared.plan.wave, it.trainerId) }

        // Shop credits for the wave just won (§2.12's between-wave step needs something to spend).
        //
        // Paid off `cleared.plan`, the same re-composed plan the boss count above uses, so a promoted
        // Elite Four wave pays boss rates — the player fought a boss, whatever the bare interval said.
        //
        // Paid off the plan's WAVE NUMBER rather than a running total of waves cleared. The two are
        // equal today and diverge the moment §2.10's disconnect penalty sends a run back: paying per
        // wave cleared would make the penalty farmable for income, while paying per wave number makes
        // a re-fought wave pay exactly what it paid the first time. See [CreditRules].
        //
        // Before the store checkpoint below, so a crash between the two loses the credits rather than
        // banking them without the wave — the player re-fights the wave and earns them again.
        run.credits += ShopSettings.credits.creditsFor(cleared.plan.wave, cleared.plan.kind)

        // §2.15's third candy source: friendship earned in battle. Credited on the cleared wave and
        // not per turn — the store is written once here instead of once per battle action, and the
        // player perceives no difference. This is the only progression write outside the capture path,
        // and like that one it touches nothing of the player's real data: see [RunProgression].
        RunProgression.creditWaveFriendship(server, player, run.partySnapshot())

        // The depth cap is re-read from the player, so a run cleared by a player who has since logged
        // out falls back to "no cap" for this one decision. That errs towards letting the run
        // continue, which the next resume re-checks with the player present.
        val cap = server.playerList.getPlayer(player)?.let { depthCapFor(it) }
        val step = RunProgress.afterVictory(cleared.plan, run.seed, composition, roster, run.trainerMemory, cap)
        return when (step) {
            is WaveStep.Fight -> {
                run.advanceTo(step.plan.wave)
                store.checkpoint(server, player)
                // §2.12's between-wave step, PUSHED rather than waited for. Opening it here is what
                // makes the mode playable: a player who has to type `/roguelite reward` after every one
                // of 200 waves will stop playing long before they run out of waves.
                //
                // AFTER the advance and the checkpoint, deliberately. The screen reads the run it is
                // handed, so opening it first would paint the wave just finished; and a crash between
                // the two would otherwise leave a shop open on state that was never saved.
                //
                // Failure is ignored on purpose: openFor declines for a player who has logged out or is
                // still in a battle, and neither is a reason to fail a won wave. The commands remain as
                // the fallback for exactly those cases.
                server.playerList.getPlayer(player)?.let { runCatching { BetweenWaveMenu.openFor(it) } }
                // After the advance, so the wave named is the one about to be played rather than the
                // one that was just cleared inside the cap.
                auditOverride(server, player, run.wave)
                step
            }

            // The next wave cannot be composed, but the win already happened: the wave advances and the
            // refusal is the player's next resume, not this. Returning it unadvanced would make them
            // fight a wave they have beaten once the operator fixes the datapack.
            is WaveStep.NoRoster -> {
                run.advanceTo(cleared.plan.wave + 1)
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
     * The wave that was just won, re-composed.
     *
     * Re-composed rather than remembered because the handler reports a victory with a player id and
     * nothing else; it gives the same answer the resume gave, because nothing between the two writes
     * to [RunState.trainerMemory].
     *
     * **This is the one place a missing roster does not refuse**, and the asymmetry is deliberate. A
     * refusal costs a player a wave they have not fought yet — nothing. Refusing *here* would cost them
     * a wave they have already won, and would leave the run unable to advance past a fight it has
     * already beaten. So the win is banked off the bare schedule, which loses only a promotion's boss
     * count, and the ERROR names the roster so the operator sees the same fault the next resume will
     * report to the player.
     */
    private fun clearedPlan(run: RunState, composition: WaveComposition, roster: RunRoster): WaveStep.Fight =
        when (roster) {
            is RunRoster.Loaded -> RunProgress.planFor(run.wave, run.seed, composition, roster, run.trainerMemory)
            is RunRoster.Missing -> {
                log.error(
                    "roguelite: banking a win at wave {} with trainer roster '{}' not loaded — if that wave " +
                        "was a promoted boss it will not be counted as one",
                    run.wave, roster.id,
                )
                WaveStep.Fight(composition.planFor(run.wave, run.seed), trainer = null)
            }
        }

    /** The run's own next step: pinned roster, this run's opponent memory, the player's depth cap. */
    private fun nextStep(run: RunState, depthCap: Int?): WaveStep = RunProgress.nextStep(
        wave = run.wave,
        seed = run.seed,
        composition = RunSettings.composition,
        roster = RunRosters.bind(run),
        memory = run.trainerMemory,
        depthCap = depthCap,
    )

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

        // §2.2-reversed: permadeath has to reach the player's party too, or the run believes the
        // Pokémon is gone while it is still sitting in a party slot — visible, selectable, and
        // fightable with. Only ever the one that just fainted, and only when it is run-marked, so this
        // cannot reach anything of the player's even if a faint is reported for the wrong Pokémon.
        //
        // Not fatal if it fails: the run's own record is authoritative for whether the run is over, and
        // a stranded corpse is cleaned up by the restore at run end.
        if (RunPartySwap.isRunPokemon(pokemon)) {
            server.playerList.getPlayer(player)?.let { online ->
                runCatching { Cobblemon.storage.getParty(online).remove(pokemon) }
                    .onFailure { log.error("roguelite: could not remove a fainted run Pokémon from {}'s party", player, it) }
            }
        }

        if (!run.isWiped()) {
            store.checkpoint(server, player)
            return null
        }
        return endRun(server, player, RunEndCause.PARTY_WIPED)
    }

    /**
     * §2.13: a wild wave was caught, and the catch belongs to the run rather than to the player.
     *
     * **The caller owes the harder half.** By the time this is called the Pokémon must already have
     * been taken back out of the player's real party and PC — Cobblemon's capture flow puts it there
     * before anything of ours runs — because a Pokémon that is in the run party *and* in a real store
     * is one object being mutated by two owners, and the real one is the one that gets saved.
     * [RunCapture][com.cobblemonroguelite.battle.RunCapture] is where that happens and is the only
     * intended caller.
     *
     * Checkpointed immediately rather than at the next wave boundary. The party's other members can
     * afford to wait — they are also in the last checkpoint — but this one exists in memory only, so
     * the ordinary wave-boundary flush would leave a crash costing the player a catch they watched
     * land. Returns null when there is no run, which means a capture arrived for a run that has
     * already ended and the Pokémon is not ours to place.
     */
    fun pokemonCaught(server: MinecraftServer, player: UUID, pokemon: Pokemon): CatchRouting? {
        val store = RunStore.of(server)
        val run = store.get(player) ?: return null
        val routing = run.offer(pokemon)
        // Not on AlreadyDeciding: nothing changed, and a flush for a no-op is a file write per
        // failure in the one branch that is already shouting about a bug.
        if (routing !is CatchRouting.AlreadyDeciding) store.checkpoint(server, player)
        when (routing) {
            is CatchRouting.Joined -> {
                // §2.2-reversed. RunCapture has already taken this out of the player's real storage,
                // and the run party is now the visible party — so without this the catch joins the run
                // and disappears from the party screen the player is looking at. Marked first, so it is
                // releasable at run end like anything else the run handed over; §2.2's isolation still
                // holds for persistence, and this Pokémon does not survive the run.
                server.playerList.getPlayer(player)?.let { online ->
                    runCatching {
                        pokemon.persistentData.putLong(RunPartySwap.RUN_MARKER_KEY, run.seed)
                        Cobblemon.storage.getParty(online).add(pokemon)
                    }.onFailure {
                        log.error(
                            "roguelite: {} caught {} into the run but it could not be put in their party — " +
                                "the run holds it and the next login will reconcile",
                            player, pokemon.species.name, it,
                        )
                    }
                }
                log.info(
                    "roguelite: {} caught {} (level {}) into run party slot {}",
                    player, pokemon.species.name, pokemon.level, routing.slot,
                )
            }

            is CatchRouting.HeldForDecision -> log.info(
                "roguelite: {} caught {} with a full run party — held pending swap or release",
                player, pokemon.species.name,
            )

            is CatchRouting.AlreadyDeciding -> log.error(
                "roguelite: {} caught {} while still holding {} for a decision — the new catch is " +
                    "lost. A run should not be able to fight a wave with a catch pending; check " +
                    "ResumeResult.CatchPending is still gating.",
                player, pokemon.species.name, routing.held.species.name,
            )
        }
        return routing
    }

    /**
     * §2.13's prompt: what is being decided, without deciding it.
     *
     * The one thing it may change is claiming a catch the party now has room for — see
     * [RunState.claimPendingCatch]. That is here rather than in [resume] because it is the only entry
     * point that can *tell the player it happened*, and a slot silently filling itself is how somebody
     * later concludes the prompt is unreliable.
     */
    fun catchPrompt(server: MinecraftServer, player: ServerPlayer): CatchPrompt {
        val store = RunStore.of(server)
        val run = store.get(player.uuid) ?: return CatchPrompt.NoRun
        val held = run.pendingCatch ?: return CatchPrompt.NothingHeld
        val claimed = run.claimPendingCatch()
        if (claimed != null) {
            store.checkpoint(server, player.uuid)
            log.info(
                "roguelite: {}'s held {} joined the party at slot {} — a slot had opened since the catch",
                player.gameProfile.name, held.species.name, claimed,
            )
            return CatchPrompt.Joined(held, claimed)
        }
        return CatchPrompt.Held(held, run.partySnapshot())
    }

    /**
     * §2.13's decision, applied. **The caller must have confirmed it with the player**, on the same
     * terms as [abandon]: both branches destroy a Pokémon and neither is recoverable.
     *
     * Null means nothing was held — a second `confirm`, or a decision typed after the party had room
     * and the catch let itself in. Answering null rather than repeating the last outcome is what
     * makes the second `confirm` harmless: there is nothing left to destroy and nothing is claimed to
     * have been.
     */
    fun resolveCatch(server: MinecraftServer, player: ServerPlayer, decision: CatchDecision): CatchResolution? {
        val store = RunStore.of(server)
        val run = store.get(player.uuid) ?: return null
        val resolution = run.resolveCatch(decision) ?: return null
        // Not on NoSuchSlot: the run is untouched, and the catch is still held for the next attempt.
        if (resolution !is CatchResolution.NoSuchSlot) store.checkpoint(server, player.uuid)
        when (resolution) {
            is CatchResolution.Swapped -> log.info(
                "roguelite: {} swapped {} out of slot {} for {} — the discarded one is gone",
                player.gameProfile.name, resolution.discarded.species.name, resolution.slot,
                resolution.kept.species.name,
            )

            is CatchResolution.Released -> log.info(
                "roguelite: {} released their held {}", player.gameProfile.name, resolution.released.species.name,
            )

            is CatchResolution.NoSuchSlot -> Unit
        }
        return resolution
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

        // Said out loud, because it is the one casualty of a run end that nothing else accounts for:
        // the party is expected to die with the run, and a held catch is a Pokémon the player was
        // still being asked about. If this line ever appears next to a *cleared* run rather than an
        // abandoned or wiped one, something advanced a run past the gate that should have stopped it.
        run?.pendingCatch?.let {
            log.info(
                "roguelite: {}'s run ended holding an undecided {} — it goes with the run (§2.2)",
                player, it.species.name,
            )
        }

        // §2.2-reversed: the player's own team comes back and the run's is released. Every end path
        // funnels through here — wipe, abandon, completion, expiry — which is the whole reason this is
        // the only call site rather than six.
        //
        // Before the arena exit rather than after, so the swap is undone while the player is still
        // somewhere the run controls. Wrapped, because a storage fault must not stop a run ending: an
        // un-restored party is recoverable on the next login, a run stuck open is not. An offline
        // player is skipped entirely and reconciled when they next join.
        server.playerList.getPlayer(player)?.let { online ->
            // The inventory first (X-protocol), the party second, and BOTH before the arena exit and
            // the payout below — X6's rule: the §2.20 payout must land in the *restored* real
            // inventory, and this ordering inside the single endRun funnel is what covers the login
            // path too (F10: penalise → endRun → deliver used to run against the still-swapped bag).
            // run is null here on purpose: the store already ended it, and a bag with no run dies
            // with it (§2.35) — exitSwap's row-4 semantics.
            runCatching { RunInventoryStash.exitSwap(online, null) }
                .onFailure { log.error("roguelite: could not restore {}'s inventory at run end", player, it) }
            runCatching { RunPartySwap.restore(online) }
                .onFailure { log.error("roguelite: could not restore {}'s party at run end", player, it) }
        }

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

        val overridden = run?.startedUnderOverride == true
        log.info(
            "roguelite: run ended for {} — cause={} outcome={} wave={} table={} entries={} delivered={} " +
                "undelivered={} bonus={} startedUnderOverride={}",
            player, cause, outcome.key, wave, table?.id, entries.map { it.id }, delivery.delivered.size,
            delivery.undelivered.size, bonus, overridden,
        )
        return RunEndReport(cause, wave, table?.id, entries, delivery, bonus, overridden)
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
     * **The run stays on the wave it was interrupted on.** This is the half of the decision that has
     * to be weighed against what *losing* costs, and losing costs everything: permadeath takes each
     * Pokémon as it faints, so a lost wave is a wipe and the run is over. A penalty that took one
     * Pokémon and skipped the wave was therefore cheaper than the fight it was meant to deter —
     * anyone facing a boss they could not beat was better off pulling the plug. Keeping the run where
     * it is makes the price one Pokémon *and* the fight still owed, which no longer beats fighting.
     *
     * It does mean the interrupted wave is re-fought from the checkpoint, which is the retry §2.3
     * hands to §2.10 — but re-fighting it a Pokémon down, having paid for the privilege, is not the
     * free retry that exploit is about.
     *
     * Killing may of course wipe the party, and that goes through [endRun] like any other wipe —
     * payout, arena exit, store removal. A run left sitting at zero party members would be restored
     * from its next checkpoint as no run at all ([RunState.fromNbt] discards an empty party), and the
     * player would never be paid.
     *
     * ### The expiry half (§2.23)
     *
     * Checked first, before anything else can act on the run. A run past its retention period is
     * deleted and pays nothing — see [RunExpiry] for why that is not the same question
     * [com.cobblemonroguelite.payout.PendingPayoutLedger] answers — and doing it first is what stops a
     * six-month-old run being penalised for a disconnect it will not survive, which would log a
     * Pokémon's death inside a run that is about to cease to exist.
     *
     * The login check is a **backstop** to the boot sweep, not the main path: the sweep is what
     * reclaims storage from players who never return, and this is what catches a run that crossed its
     * period while the server happened to be up. Between the two there is one case neither covers, and
     * that is deliberate — a run cannot expire out from under a player who is already online, because
     * nothing re-checks during a session.
     *
     * ### The arena half
     *
     * Not §2.10 and not optional: a player who logs in inside an arena is in a void dimension with no
     * bed, no portal and nothing to fall onto, i.e. stuck permanently unless something moves them.
     *
     * Since §2.23 this is the *ordinary* case rather than the exceptional one. The arena lease ends at
     * logout ([RunArenas.release]) and the slot may since have been handed to somebody else and
     * re-stamped, so anybody who quit inside an arena logs back in standing in a box that is no longer
     * theirs. They are ejected to their run's entry point and re-enter through [resume], which takes a
     * fresh slot. The old exceptional case — arena space with no run at all — still lands here too and
     * is handled by the same call, with the same fallback chain.
     *
     * Note what is lost when there is no run: the return position lived on the [RunState] that ended,
     * so those players go to world spawn rather than to where they started. Preserving it would mean
     * keeping a record of ended runs purely to hold one position, and the alternative — leaving them in
     * the void — is not a trade.
     *
     * Returns what happened so the caller can tell the player; sends nothing itself.
     */
    fun reconcileOnLogin(server: MinecraftServer, player: ServerPlayer): LoginReconciliation {
        val store = RunStore.of(server)
        expireIfStale(server, player.uuid)
        // Taken whether or not this login expired anything: the notice may have been written by the
        // boot sweep months ago. Removing it here is what makes the sentence get said exactly once.
        val notice = store.takeExpiryNotice(player.uuid)

        val run = store.get(player.uuid)
        val interrupted = run?.let { attributeInterruption(server, player, it) }

        // After the attribution, which can end a run and eject on its own — asking first would move a
        // player whose run is about to be ended and then move them again.
        val returned = evictFromArena(server, player)

        // Status read *after* all of it, never before: expiry can delete the run and the penalty can
        // move the wave or empty the party, and a status captured first would describe a run that is no
        // longer there — including handing the login hook an InProgress holding an emptied party.
        val status = status(server, player)
        return LoginReconciliation(
            status = status,
            interrupted = interrupted,
            expired = notice,
            expiring = nearExpiry(status),
            returnedFromArena = returned,
        )
    }

    /**
     * Put a player who logged in inside arena space back outside it. True when it moved them.
     *
     * Ejects to the entry point of whatever run they still have, which is strictly better than the
     * world-spawn fallback this used to be: a player who quit mid-run now returns to where they were
     * standing before the run started, rather than to spawn. A player with no run has no entry point to
     * offer and gets the fallback, with the warning [RunArenas.eject] logs.
     */
    private fun evictFromArena(server: MinecraftServer, player: ServerPlayer): Boolean {
        if (!RunArenas.isInArena(player)) return false
        val run = RunStore.of(server).get(player.uuid)
        log.info(
            "roguelite: {} logged in inside an arena they no longer hold — returning them to {}",
            player.gameProfile.name, run?.entry?.dimension ?: "world spawn",
        )
        RunArenas.eject(server, player, run?.entry)
        return true
    }

    /** §2.23's warning window, for a run that survived the login. Null unless it is worth saying. */
    private fun nearExpiry(status: RunStatus): RunExpiryStatus? {
        val run = (status as? RunStatus.InProgress)?.run ?: return null
        val evaluated = RunExpiry.evaluate(run, System.currentTimeMillis(), RunSettings.current.expiry)
        return evaluated.takeIf { it.nearExpiry }
    }

    /**
     * §2.23: delete [player]'s run if nobody has played it inside its retention period.
     *
     * ### Why this is not [endRun]
     *
     * Because [endRun] pays. Every other way a run stops resolves a payout table, hands grants over and
     * writes a [RunEndReport]; expiry does none of it, on §2.23's rule that half a year of silence is
     * not owed anything and that the payment would land on somebody who is not there anyway. Sharing
     * the path and suppressing the payout with a flag would put "do not mint items" behind a boolean,
     * which is the direction that has to be impossible rather than merely correct today.
     *
     * The run's arena needs no explicit release: occupancy is derived from the store, so removing the
     * entry frees whatever it held, and a run reaching here holds nothing anyway — its owner is offline
     * or has only just connected.
     *
     * Returns the notice it wrote, or null when there was nothing to expire, which is nearly every call.
     */
    private fun expireIfStale(server: MinecraftServer, player: UUID): RunExpiryNotice? {
        val store = RunStore.of(server)
        val run = store.get(player) ?: return null
        val now = System.currentTimeMillis()
        val status = RunExpiry.evaluate(run, now, RunSettings.current.expiry)
        if (!status.expired) return null
        return expire(server, player, run, status, now)
    }

    /**
     * §2.23's sweep: every run on the server, once, at start.
     *
     * This and not the login check is what the feature is *for*. Expiry is storage hygiene, and the
     * runs worth reclaiming belong to players who are not logging in — a lazy check would keep exactly
     * the runs it was written to remove. Running it at start rather than on a timer is enough because
     * the periods are measured in weeks: a run that becomes stale during an uptime is collected at the
     * next restart, or at its owner's next login, whichever comes first.
     *
     * Returns how many were deleted, for the caller's log line.
     */
    fun expireStaleRuns(server: MinecraftServer): Int {
        val store = RunStore.of(server)
        val now = System.currentTimeMillis()
        val stale = RunExpiry.stale(store.activeRuns(), now, RunSettings.current.expiry)
        for ((player, status) in stale) {
            // Re-fetched rather than taken from the snapshot: the snapshot is a copy, and between it
            // and here a player could in principle have connected and had their run ended some other
            // way. Expiring a run that is no longer in the store would write a notice about nothing.
            val run = store.get(player) ?: continue
            expire(server, player, run, status, now)
        }
        return stale.size
    }

    /** The deletion itself, shared by the sweep and the login backstop. */
    private fun expire(
        server: MinecraftServer,
        player: UUID,
        run: RunState,
        status: RunExpiryStatus,
        now: Long,
    ): RunExpiryNotice {
        val notice = RunExpiryNotice.of(run.wave, status, now)
        // Logged before the removal and with everything a dispute would need, on
        // [PendingPayoutHooks]'s rule: this line is the only record that the run existed once the store
        // entry is gone, and unlike a payout there is nothing downstream that will mention it again.
        log.info(
            "roguelite: expiring {}'s run at wave {} — untouched for {} day(s), which is past the {} day " +
                "retention its depth earns. It pays nothing (§2.23). Party was {}.",
            player, run.wave, status.idleDays, status.periodDays,
            run.partySnapshot().map { it.species.name },
        )
        RunStore.of(server).expire(server, player, notice)
        return notice
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

        // The wave is deliberately not touched — see [reconcileOnLogin] for why skipping it made
        // quitting cheaper than fighting. Two guards went with the advance and are gone rather than
        // left standing: "only when something was killed" and "only into a real fight, never into an
        // ending". Both existed solely to bound the mutation, and with no mutation there is nothing
        // for them to bound — a run that is now over stays where it is and the ordinary resume path
        // ends it with the player present and the right cause, which is what they were protecting.
        store.checkpoint(server, player.uuid)
        log.info(
            "roguelite: {} dropped mid-wave {} — lost {}, run still owes wave {}",
            player.gameProfile.name, verdict.wave, killed, run.wave,
        )
        return DisconnectOutcome.Penalised(verdict.wave, killed, run.wave, null)
    }

    /** Null means no cap. Denied reads as depth zero, which [RunProgress] ends the run on. */
    private fun depthCapFor(player: ServerPlayer): Int? = depthGateFor(player).cap

    /** §2.18's gate for this player, with §2.25's override applied. The one place both are read. */
    private fun depthGateFor(player: ServerPlayer): DepthGateResult =
        RunSettings.current.depthGate.evaluate(
            VanillaAdvancements.of(player),
            RunDepthOverrides.isActive(player.uuid),
        )

    /**
     * §2.25's "obvious in the log when it is in force", said at the moment it is actually buying
     * something.
     *
     * Not on every evaluation: the gate is read on `/roguelite status`, on every resume and after
     * every wave, so a line per read would be noise that an operator learns to scroll past — which is
     * the same as not logging it. This fires only when the honest gate would have ended the run
     * *here*, so every line is one wave the player is playing that their badges do not entitle them
     * to, and the first line names the wave it started at.
     */
    private fun auditOverride(server: MinecraftServer, player: UUID, wave: Int) {
        val online = server.playerList.getPlayer(player) ?: return
        if (!RunDepthOverrides.isActive(player)) return
        val honest = RunSettings.current.depthGate.evaluate(VanillaAdvancements.of(online))
        if (honest.allows(wave)) return
        log.warn(
            "roguelite: {} is at wave {} under an OPERATOR DEPTH OVERRIDE — their badges allow {}. " +
                "This run is marked as started under an override; do not read its depth as earned.",
            online.gameProfile.name, wave, honest.cap,
        )
    }

    /**
     * Built per call rather than held. The pool source, the price source and the budget all live in
     * configuration that can be replaced at runtime, and a cached factory would keep serving what was
     * configured at boot — including a price table a `/reload` has since corrected.
     */
    private fun catalogueFactory(): StarterCatalogueFactory = RunSettings.current.let { config ->
        StarterCatalogueFactory(
            pool = config.starterPool,
            unlocks = CobblemonPokedexUnlocks,
            costs = config.starterCosts,
            budget = config.starterBudget,
        )
    }

    /**
     * The start steps, bound to a real server and player. Nothing decides anything here — the order
     * is [RunStart]'s and each of these is one call.
     */
    private class ServerRunStartContext(
        private val server: MinecraftServer,
        private val player: ServerPlayer,
    ) : RunStartContext {

        override fun depthGate(): DepthGateResult = depthGateFor(player)

        override fun arenaAvailable(): Boolean = RunArenas.hasCapacity(server)

        override fun charge(quoteOnly: Boolean) =
            if (quoteOnly) RunCharges.quote(server, player.uuid) else RunCharges.charge(server, player.uuid)

        override fun starterCatalogue(): StarterCatalogue = catalogueFactory().catalogueFor(player.uuid)

        /**
         * A random seed rather than a counter or the clock. Consecutive seeds are what a counter
         * produces and what a coarse clock approximates, and
         * [com.cobblemonroguelite.starter.StarterIvRoll] has to mix hard to keep those from
         * correlating — feeding it uncorrelated values costs nothing and removes the dependency on
         * that mixing being good enough.
         */
        override fun mintSeed(): Long = ThreadLocalRandom.current().nextLong()

        override fun persistSeed(seed: Long) {
            RunStore.of(server).beginPending(server, player.uuid, PendingStart(seed, System.currentTimeMillis()))
        }
    }
}
