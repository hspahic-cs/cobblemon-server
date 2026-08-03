package com.cobblemonroguelite.payout

/**
 * Where the player is, as far as deciding whether to drop a payout at their feet is concerned.
 *
 * @property owed whether there is anything to hand over at all.
 * @property alive false for a player who is dead or has been removed from the world. A player looking
 *   at the respawn screen is *online* by every test the server makes, and dropping a payout at the
 *   spot where they died is the one delivery that is guaranteed to be lost.
 * @property ticksSinceLogin how long they have been in the world. See [PendingPayoutGate.SETTLE_TICKS].
 * @property inArena whether they are standing in arena space
 *   ([com.cobblemonroguelite.arena.RunArenas.isInArena]).
 * @property hasRun whether they have an active run.
 */
data class DeliverySituation(
    val owed: Boolean,
    val alive: Boolean,
    val ticksSinceLogin: Int,
    val inArena: Boolean,
    val hasRun: Boolean,
)

/** Why a held payout was or was not dropped this tick. Named cases so the log can say which. */
enum class DeliveryVerdict {
    /** Hand it over now. */
    DELIVER,

    /** Nothing owed — the armed entry can be dropped. */
    NOTHING_OWED,

    /** Too soon after login; the world around them is still moving. */
    SETTLING,

    /** Dead or leaving. Wait; they will be alive again or gone. */
    NOT_IN_THE_WORLD,

    /** They are inside a run. The payout waits for it to end. */
    IN_A_RUN,

    /** Arena space with no run to explain it — they are about to be teleported out. */
    IN_AN_ARENA,

    ;

    val deliverable: Boolean get() = this == DELIVER

    /** True while there is still a reason to keep checking. [NOTHING_OWED] is the only terminal one. */
    val keepWaiting: Boolean get() = this != DELIVER && this != NOTHING_OWED
}

/**
 * When, in a login, a held payout is allowed to hit the ground.
 *
 * ### Why not simply "on login"
 *
 * Because a player who has just logged in is not reliably anywhere. The login sequence this module
 * itself runs ([com.cobblemonroguelite.run.RunLoginHooks]) can teleport them twice — §2.10's
 * attribution can wipe their party and end the run, which exits the arena, and a player found in
 * arena space with no run is ejected to world spawn. Items dropped before that lands are dropped in
 * the place they are being moved *out of*: a void arena that will be re-stamped for the next run, or
 * the middle of a teleport. They would despawn five minutes later in a dimension nobody is standing
 * in, and the log would say the payout was paid.
 *
 * That is the same failure as dropping the items at the moment the run ended, which is the thing this
 * whole feature exists to avoid. The fix is the same in both cases: wait until there is a player
 * standing somewhere they will still be standing in a moment.
 *
 * ### The three waits
 *
 * - **Settling.** [SETTLE_TICKS] after login, so the login hooks have run and any teleport they make
 *   has landed. Ordering between two game-bus listeners is not something to rely on, and a delay
 *   measured in ticks is a far cheaper guarantee than a promise about registration order.
 * - **A run.** A player inside a run is inside a *sealed* one (§1.1). Dropping the previous run's
 *   payout into it puts permanent items in the one place the mode is careful to let nothing out of,
 *   in a dimension whose blocks get overwritten between waves. It waits, and the wait is safe because
 *   the ledger is on disk.
 * - **An arena with no run.** Transient by construction — the login hook ejects them — and worth its
 *   own case only so that the payout is not delivered into the two or three ticks before it does.
 *
 * ### Why there is no timeout on the waits
 *
 * Because there is nowhere better to give up *to*. A player who stays inside a run for a week is a
 * player whose payout stays on disk for a week, which costs nothing and loses nothing; a timeout
 * would only convert a safe wait into a delivery made somewhere we already decided was wrong.
 */
object PendingPayoutGate {

    /**
     * Ticks between logging in and being considered settled — one second at 20 TPS.
     *
     * Long enough for the login hooks and any teleport they cause to have run (they are synchronous
     * and happen on the login tick), short enough that the payout appears while the player is still
     * looking at the place they arrived. Not longer, on purpose: a payout that lands thirty seconds
     * after login is a payout the player has already walked away from.
     */
    const val SETTLE_TICKS = 20

    /**
     * Ticks between re-checks once a delivery has been deferred. Half a second: the deferrals are
     * arena and run membership, which change on a teleport, so this is only about not asking the
     * question twenty times a second for the length of somebody's run.
     */
    const val RETRY_TICKS = 10

    fun evaluate(situation: DeliverySituation): DeliveryVerdict = when {
        !situation.owed -> DeliveryVerdict.NOTHING_OWED
        // Before the settle check rather than after: a player who logged in dead has not started
        // settling, and reporting them as merely early would hide the reason from the log.
        !situation.alive -> DeliveryVerdict.NOT_IN_THE_WORLD
        situation.ticksSinceLogin < SETTLE_TICKS -> DeliveryVerdict.SETTLING
        situation.hasRun -> DeliveryVerdict.IN_A_RUN
        situation.inArena -> DeliveryVerdict.IN_AN_ARENA
        else -> DeliveryVerdict.DELIVER
    }
}
