package com.cobblemonroguelite.payout

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * When a held payout is allowed to hit the ground.
 *
 * Every case here is a place a player can be standing at the moment they log in, and in three of them
 * dropping the payout destroys it: mid-teleport out of an arena, inside a sealed run whose blocks are
 * rewritten between waves, or on the respawn screen looking at the spot where they died. None of the
 * three is reachable from a unit test with a real player in it — which is exactly why the decision is
 * a pure function taking a description of where they are, rather than a chain of `if`s inside the
 * tick handler where it would first run in production.
 */
class PendingPayoutGateTest {

    private fun situation(
        owed: Boolean = true,
        alive: Boolean = true,
        ticksSinceLogin: Int = PendingPayoutGate.SETTLE_TICKS,
        inArena: Boolean = false,
        hasRun: Boolean = false,
    ) = DeliverySituation(owed, alive, ticksSinceLogin, inArena, hasRun)

    @Test
    fun `a settled player standing in the world is paid`() {
        assertEquals(DeliveryVerdict.DELIVER, PendingPayoutGate.evaluate(situation()))
    }

    @Test
    fun `nothing owed is terminal, not a wait`() {
        val verdict = PendingPayoutGate.evaluate(situation(owed = false))

        assertEquals(DeliveryVerdict.NOTHING_OWED, verdict)
        // The distinction the tick loop turns on: everything else re-arms, this one stops asking.
        assertFalse(verdict.keepWaiting)
        assertFalse(verdict.deliverable)
    }

    @Test
    fun `the tick of login is too early`() {
        // The login hooks teleport people — an ended run exits its arena, a player found in arena
        // space with no run is ejected to world spawn — and listener ordering between two game-bus
        // subscribers is not something to bet a payout on.
        val verdict = PendingPayoutGate.evaluate(situation(ticksSinceLogin = 0))

        assertEquals(DeliveryVerdict.SETTLING, verdict)
        assertTrue(verdict.keepWaiting)
    }

    @Test
    fun `one tick short of settled is still too early`() {
        assertEquals(
            DeliveryVerdict.SETTLING,
            PendingPayoutGate.evaluate(situation(ticksSinceLogin = PendingPayoutGate.SETTLE_TICKS - 1)),
        )
    }

    @Test
    fun `a dead player is waited on, and is reported as dead rather than as early`() {
        // Someone on the respawn screen is online by every test the server makes. Dropping there puts
        // the payout at the spot that killed them, where it despawns in five minutes.
        assertEquals(
            DeliveryVerdict.NOT_IN_THE_WORLD,
            PendingPayoutGate.evaluate(situation(alive = false, ticksSinceLogin = 0)),
        )
    }

    @Test
    fun `a player inside a run waits for it to end`() {
        val verdict = PendingPayoutGate.evaluate(situation(hasRun = true, inArena = true))

        // Reported as the run rather than the arena, because the run is the reason a player can
        // understand and the arena is a consequence of it.
        assertEquals(DeliveryVerdict.IN_A_RUN, verdict)
        assertTrue(verdict.keepWaiting)
    }

    @Test
    fun `arena space with no run is a wait, not a delivery`() {
        assertEquals(DeliveryVerdict.IN_AN_ARENA, PendingPayoutGate.evaluate(situation(inArena = true)))
    }

    @Test
    fun `waiting forever is preferred to delivering somewhere wrong`() {
        // There is no timeout, and this pins that: a player who has been inside a run for a week is
        // still waiting, because the alternative is a payout delivered into a place already decided
        // to be wrong. The debt is on disk; waiting costs nothing.
        val week = 20 * 60 * 60 * 24 * 7
        assertEquals(
            DeliveryVerdict.IN_A_RUN,
            PendingPayoutGate.evaluate(situation(ticksSinceLogin = week, hasRun = true)),
        )
    }
}
