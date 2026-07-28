package com.cobblemonroguelite.run

/**
 * What `/roguelite pause` has to tell a player, given where their run is.
 *
 * Four answers because the question — "what does it cost me to walk away right now?" — has four
 * genuinely different answers, and collapsing any two of them puts a wrong price in front of a
 * player. In particular [BetweenWaves] and [MidBattle] must never share wording: one is free and the
 * other is a Pokémon, and a message that hedges across both teaches players to ignore it.
 */
sealed interface PauseAdvice {

    /**
     * Nothing is in flight because nothing was started.
     *
     * A separate case rather than a refusal. §2.22's command exists to answer a question, and
     * "unknown command" or a silent no-op answers it with ambiguity — the player cannot tell whether
     * they have no run or whether pause simply does not work here, and those imply opposite things
     * about logging off.
     */
    data object NoRun : PauseAdvice

    /** Paid, seeded, no starter picked (§2.16). Nothing can be lost: there is no party yet. */
    data object StarterPending : PauseAdvice

    /** Between waves. Free, because the run is already checkpointed and no battle is open. */
    data class BetweenWaves(val wave: Int) : PauseAdvice

    /** A battle is live, so leaving costs the field (§2.10). This is the warning, not the answer. */
    data class MidBattle(val wave: Int) : PauseAdvice

    /**
     * The warning was read back with `confirm`.
     *
     * Distinct from [MidBattle] and deliberately **not** a state change: see [RunPause] for why
     * confirming takes nothing, clears nothing and ends nothing.
     */
    data class MidBattleAcknowledged(val wave: Int) : PauseAdvice
}

/**
 * §2.22, with nothing of the game in it: pausing discloses the disconnect penalty, it does not alter
 * it.
 *
 * ### Why a pure function for something this small
 *
 * The whole value of the command is that the price it quotes is the price §2.10 actually charges. A
 * pause that said "free" while a battle was open would be worse than no pause at all — it would make
 * a player confident about the exact moment the penalty fires. That divergence is one wrong branch,
 * it is invisible without a dropped connection to compare against, and here it is a test case.
 *
 * ### Confirming changes nothing, and that is the design
 *
 * Two things `pause confirm` was considered for and does not do:
 *
 * - **Charge the penalty immediately.** It would punish a player who read the warning and then
 *   decided to keep playing, and it would have to clear the marker to avoid charging twice — which
 *   would make the disconnect that follows *free*. Strictly worse in both directions.
 * - **End the battle cleanly.** There is no battle to end: [RunWaves] is unimplemented, so nothing
 *   can be live today. Building a forfeit path now would mean guessing at an interface that does not
 *   exist, and a forfeit is anyway a different decision — it would need §2.10's price argued again
 *   from scratch, since a clean surrender and a rage-quit are not obviously worth the same.
 *
 * So confirmation is purely an acknowledgement: proof the player was shown the price before they
 * paid it. The two-step exists for the same reason it does on `start` and `abandon`.
 *
 * ### It is a question, not a promise
 *
 * The advice describes this instant. A player who asks between waves, gets [BetweenWaves] and then
 * starts a wave before logging off is charged normally — nothing here holds a run open or grants
 * safe passage, and no state is written by asking.
 */
object RunPause {

    /**
     * @param run the player's live run, or null if they have none.
     * @param hasPendingStart whether a paid, unresolved start is on file. Only consulted when there
     *   is no run: the two cannot coexist ([RunController.start] refuses), and reading it in the
     *   other order would answer for the start instead of for the run.
     * @param confirmed whether the player typed the `confirm` sub-literal. Ignored anywhere it is not
     *   a mid-battle pause — confirming something that costs nothing is not a distinct situation, and
     *   a fifth case for it would be a fifth message saying the same thing.
     */
    fun advise(run: RunState?, hasPendingStart: Boolean, confirmed: Boolean): PauseAdvice {
        if (run == null) return if (hasPendingStart) PauseAdvice.StarterPending else PauseAdvice.NoRun
        // The marker is the same fact §2.10 attributes on, read from the same place. Deriving
        // "is a battle live" any other way — a handler flag, a player position, a battle registry —
        // is how the quoted price and the charged price drift apart.
        val battle = run.battle ?: return PauseAdvice.BetweenWaves(run.wave)
        return if (confirmed) PauseAdvice.MidBattleAcknowledged(battle.wave) else PauseAdvice.MidBattle(battle.wave)
    }
}
