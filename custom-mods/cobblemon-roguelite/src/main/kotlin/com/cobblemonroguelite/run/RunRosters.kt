package com.cobblemonroguelite.run

import com.cobblemonroguelite.CobblemonRoguelite
import com.cobblemonroguelite.data.trainer.TrainerPick
import com.cobblemonroguelite.data.trainer.TrainerPickSource
import com.cobblemonroguelite.data.trainer.TrainerRoster
import com.cobblemonroguelite.data.trainer.TrainerRosters
import com.cobblemonroguelite.integration.RunOpponent
import com.cobblemonroguelite.wave.WaveDrawStream
import com.cobblemonroguelite.wave.WaveRandom
import net.minecraft.resources.ResourceLocation

/**
 * A run's pinned roster id, resolved — or not.
 *
 * ### Why "not loaded" is a state and not a fallback
 *
 * The tempting reading of a missing roster is "there are no promotions, so compose the waves from the
 * schedule alone and carry on". That is precisely the failure worth refusing: under a 5/10 schedule
 * four of the five Elite Four waves (§2.7 puts them at 182/184/186/188) are *wild* waves, and a run
 * that quietly composed them from the schedule would hand the player four catchable wild encounters
 * where the ladder was supposed to be, at wild levels, and would look like a working run the whole
 * way. The roster is the only thing that knows a wave was promoted, so without it we do not know what
 * any wave is — including the wild ones — and the honest answer is to say so and stop.
 */
sealed interface RunRoster {

    data class Loaded(val id: ResourceLocation, val roster: TrainerRoster) : RunRoster

    /** [id] is null for a run carrying no pinned id at all — see [RunState.trainerRoster]. */
    data class Missing(val id: ResourceLocation?) : RunRoster
}

/**
 * Binds the id a run pinned at its start to whatever is loaded under it now.
 *
 * ### Why a run pins an id and re-resolves it every wave
 *
 * Exactly [RunState.payoutTable]'s bargain, and for exactly its reason. A run is a multi-session
 * commitment (§2.19 puts it at days), so re-reading [RunConfig.trainerRoster] at wave 150 would let an
 * operator who switched rosters between somebody's wave 3 and their wave 150 change which ladder that
 * run is climbing, mid-climb. Pinning the id closes that.
 *
 * Pinning the *contents* would close more and is deliberately not done: a roster with a broken band or
 * a typo'd trainer id has to be fixable for runs already in flight, and a snapshot taken at run start
 * would freeze the bug into every run that had begun before anyone noticed. So an edit still reaches a
 * live run — which [TrainerBand.trainers][com.cobblemonroguelite.data.trainer.TrainerBand.trainers]
 * already documents as re-pointing waves the run has not reached — and a *substitution* does not.
 */
object RunRosters {

    /**
     * The roster a run pins unless configuration says otherwise.
     *
     * **Nothing ships at this id**, the same as [com.cobblemonroguelite.data.payout.PayoutTables.DEFAULT_TABLE]
     * and for §2.7's reason: our server's transcribed roster stays in a server-side datapack and a
     * published build ships a neutral one. Until some datapack writes it, every wave of every run
     * resolves [RunRoster.Missing] and refuses — which is the same shape of shipped default as
     * [RunWaves.UNIMPLEMENTED], and chosen the same way. The alternative "compose from the schedule
     * and carry on" is not a working mode with no content in it; it is a mode that silently drops the
     * half of §2.14 that makes trainer waves exist.
     *
     * Declared here rather than beside [TrainerRosters] because a server may hold several rosters and
     * *which one a run uses* is a run-loop decision — the registry's job ends at loading whatever is
     * in the folder.
     */
    val DEFAULT_ROSTER: ResourceLocation =
        ResourceLocation.fromNamespaceAndPath(CobblemonRoguelite.MOD_ID, "default")

    fun bind(id: ResourceLocation?): RunRoster {
        val roster = id?.let { TrainerRosters[it] } ?: return RunRoster.Missing(id)
        return RunRoster.Loaded(id, roster)
    }

    /**
     * Resolved per call rather than cached on the run. `/reload` replaces the registry's map wholesale,
     * so a [TrainerRoster] held on a [RunState] would keep serving the file as it was when the run
     * started — which is the snapshot this design says not to take.
     */
    fun bind(run: RunState): RunRoster = bind(run.trainerRoster)
}

/**
 * Which authored trainer a wave fights, with this run's recent history taken into account.
 *
 * ### Why this is not on [TrainerRoster]
 *
 * The roster's draw is a pure function of `(seed, wave)` and its own docs are explicit that keeping it
 * that way is the point. Avoiding repeats needs a third input that only a run has, so the run-aware
 * version lives on this side of the boundary and the roster stays testable and stateless.
 *
 * ### It is still deterministic, and that is not automatic
 *
 * The draw is the same `(seed, wave, TRAINER)` stream; what the history changes is the *list being
 * indexed*, never the number drawn from the stream. So a run resumed from a checkpoint reproduces its
 * pick as long as it reproduces its history — which is why [RunTrainerMemory] is persisted rather than
 * recomputed, and why [RunTrainerMemory.before] is keyed on the wave being planned.
 */
object RunTrainerSelection {

    /**
     * Who [wave] fights, or null when this roster serves it nothing — the same three null cases
     * [TrainerRoster.pickFor] documents.
     *
     * [recent] is [RunTrainerMemory.before]'s answer for this wave, most recent first. Empty [recent]
     * is required to reproduce [TrainerRoster.pickFor] exactly, so a run that has met nobody yet is
     * not a special case in either direction.
     */
    fun pick(
        roster: TrainerRoster,
        wave: Int,
        kind: RunOpponent,
        seed: Long,
        recent: List<ResourceLocation>,
    ): TrainerPick? {
        // A fixed encounter is an author naming one trainer for one wave, so there is nothing here to
        // avoid: honouring the history would mean silently ignoring the pin, which is the one thing an
        // override is for. Delegated whole so the promote/replace precedence stays in one place.
        //
        // A §2.36 rival meeting is the same delegation for a stronger reason. The no-repeat window
        // exists because a *pool* can serve the same trainer twice in a sitting; a rival has no pool,
        // and its six stage ids are the one set of trainers a run is SUPPOSED to meet repeatedly. Left
        // to fall through, [eligible] would find nothing to exclude and answer the same way — but only
        // by accident, and the accident stops holding the moment someone adds a second history-aware
        // rule here. Stated so the rival is never that rule's collateral.
        if (roster.isFixed(wave) || roster.isRivalMeeting(wave)) return roster.pickFor(wave, kind, seed)

        if (kind == RunOpponent.WILD) return null
        val band = roster.bandFor(wave, kind) ?: return null
        if (band.trainers.isEmpty()) return null

        val candidates = eligible(band.trainers, recent)
        val rng = WaveRandom.forDraw(seed, wave, WaveDrawStream.TRAINER)
        // Same index arithmetic as [TrainerRoster.pickFor], for the same reason: a modulo of a 64-bit
        // draw is biased toward low indices. Kept identical rather than shared because the roster must
        // stay callable without a run.
        val index = (rng.nextDouble() * candidates.size).toInt().coerceAtMost(candidates.lastIndex)
        return TrainerPick(candidates[index], TrainerPickSource.BAND, band.id)
    }

    /**
     * The pool minus whoever is still in the window — with the window shortened until something is
     * left, rather than abandoned when nothing is.
     *
     * A band smaller than the window is the case this exists for, and it is the case where repeats
     * hurt most. Dropping straight back to the full pool there would let the same trainer be drawn
     * twice running, in exactly the roster that can least afford it; walking the window down instead
     * keeps the strongest constraint the pool can actually satisfy, and only reaches "no constraint"
     * when the band holds a single distinct trainer, where there is genuinely no other answer.
     *
     * Duplicates survive filtering, which keeps repetition working as
     * [TrainerBand.trainers][com.cobblemonroguelite.data.trainer.TrainerBand.trainers]' only weighting
     * mechanism: a trainer listed twice is excluded twice or kept twice, never half of each.
     */
    private fun eligible(pool: List<ResourceLocation>, recent: List<ResourceLocation>): List<ResourceLocation> {
        for (window in recent.size downTo 1) {
            val avoid = recent.take(window).toSet()
            val remaining = pool.filterNot { it in avoid }
            if (remaining.isNotEmpty()) return remaining
        }
        return pool
    }
}
