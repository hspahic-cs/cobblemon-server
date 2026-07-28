package com.cobblemonroguelite.run

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The two messages that are load-bearing rather than decorative.
 *
 * §2.10's penalty and §2.22's disclosure are both rules a player only ever learns from a line of
 * chat, so the wording is the mechanism: a penalty message that implies the wave was skipped, or a
 * pause message that names a Pokémon it guessed at, misinforms the player as effectively as wrong
 * code would. Neither is reachable without a dropped connection on a live server, so the strings are
 * asserted here.
 */
class RunMessagesTest {

    private fun penalised(wave: Int = 12, resumesAt: Int = 12, killed: List<String> = listOf("Gengar")) =
        RunMessages.interrupted(DisconnectOutcome.Penalised(wave, killed, resumesAt, ended = null)).string

    @Test
    fun `a drop leaves the player still owing the wave they fled`() {
        // The change §2.10 landed on: the Pokémon buys nothing. If this ever reads as progress, the
        // penalty is cheaper than the fight again and quitting a losing boss becomes correct play.
        val text = penalised()
        assertTrue("still on wave 12" in text, text)
        assertTrue("still ahead of you" in text, text)
    }

    @Test
    fun `the penalty message never claims the run moved on`() {
        val text = penalised()
        assertTrue("continues at" !in text, text)
        assertTrue("wave 13" !in text, text)
    }

    @Test
    fun `a wipe is told as a wipe rather than as a wave still owed`() {
        // The run is over, so "that fight is still ahead of you" would be describing a run that no
        // longer exists — the wipe branch has to win over the same-wave branch, not read after it.
        val ended = RunEndReport(
            RunEndCause.PARTY_WIPED, wave = 12, table = null,
            entries = emptyList(), delivery = PayoutDelivery.NOTHING, bonusPaid = false,
        )
        val text = RunMessages.interrupted(DisconnectOutcome.Penalised(12, listOf("Gengar"), 12, ended)).string
        assertTrue("last Pokémon" in text, text)
        assertTrue("still on wave" !in text, text)
    }

    @Test
    fun `a run found somewhere other than the interrupted wave is described by where it stands`() {
        // Only reachable if a marker outlived a wave move. The player is told the run's wave, not the
        // marker's, because the run's is the one they are about to be dropped into.
        val text = penalised(wave = 12, resumesAt = 14)
        assertTrue("wave 14" in text, text)
    }

    @Test
    fun `the mid-battle pause warning names no Pokemon and offers the confirm`() {
        // The marker still holds the party lead until the wave handler reports switches, so any name
        // in here would be wrong for every player who has switched — see [RunMessages.pause].
        val text = RunMessages.pause(PauseAdvice.MidBattle(7)).string
        assertTrue("wave 7" in text, text)
        assertTrue("/roguelite pause confirm" in text, text)
        assertTrue("does not pause" in text, text)
    }

    @Test
    fun `between waves is stated as free, with no warning attached`() {
        // Nothing conditional and nothing to confirm: hedging here is what would teach players to
        // skim the mid-battle warning, which is the one that costs a Pokémon.
        val text = RunMessages.pause(PauseAdvice.BetweenWaves(7)).string
        assertTrue("Safe to leave" in text, text)
        assertTrue("nothing is lost" in text, text)
        assertTrue("confirm" !in text, text)
    }

    @Test
    fun `acknowledging does not read as having paid anything`() {
        val text = RunMessages.pause(PauseAdvice.MidBattleAcknowledged(7)).string
        assertTrue("Nothing has been taken" in text, text)
        assertTrue("still live" in text, text)
    }

    @Test
    fun `pause with no run answers the question rather than refusing it`() {
        assertEquals(RunMessages.noRun().string, RunMessages.pause(PauseAdvice.NoRun).string)
    }
}
