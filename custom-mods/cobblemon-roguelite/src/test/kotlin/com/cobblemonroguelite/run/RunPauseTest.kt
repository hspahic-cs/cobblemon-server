package com.cobblemonroguelite.run

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * §2.22: the price `/roguelite pause` quotes has to be the price §2.10 charges.
 *
 * The failure that matters is one branch answering "safe to leave" while a battle is open, which is
 * worse than having no command at all — it would put a player's confidence exactly on the moment the
 * penalty fires. It needs a dropped connection to notice in play, so it is asserted here.
 */
class RunPauseTest {

    private fun run(wave: Int = 7, battle: RunBattleMarker? = null) =
        RunState(wave = wave, seed = 42L, battle = battle)

    private fun marker(wave: Int) = RunBattleMarker(wave, UUID.randomUUID(), listOf(UUID.randomUUID()))

    @Test
    fun `no run and no pending start is nothing to lose`() {
        assertEquals(PauseAdvice.NoRun, RunPause.advise(run = null, hasPendingStart = false, confirmed = false))
    }

    @Test
    fun `a paid start with no starter picked is safe to leave`() {
        assertEquals(PauseAdvice.StarterPending, RunPause.advise(run = null, hasPendingStart = true, confirmed = false))
    }

    @Test
    fun `between waves is free and names the wave the run is saved at`() {
        assertEquals(PauseAdvice.BetweenWaves(7), RunPause.advise(run(), hasPendingStart = false, confirmed = false))
    }

    @Test
    fun `a live marker is a price, not a pause`() {
        val advice = RunPause.advise(run(battle = marker(7)), hasPendingStart = false, confirmed = false)
        assertEquals(PauseAdvice.MidBattle(7), advice)
    }

    @Test
    fun `confirming mid-battle acknowledges and nothing more`() {
        val run = run(battle = marker(7))
        val advice = RunPause.advise(run, hasPendingStart = false, confirmed = true)
        assertEquals(PauseAdvice.MidBattleAcknowledged(7), advice)
        // The half of §2.22 that is a promise: confirming does not charge the penalty early and does
        // not clear the marker. Clearing it would make the disconnect that follows free, which is the
        // hole §2.10 exists to close, opened by the command that explains it.
        assertEquals(marker(7).wave, run.battle?.wave)
        assertEquals(7, run.wave)
    }

    @Test
    fun `the quoted wave is the battle's, not the run's`() {
        // They agree in ordinary play. Where they do not, the player is mid-battle on the wave the
        // marker names, and quoting the run's would name a fight they are not in.
        val advice = RunPause.advise(run(wave = 9, battle = marker(8)), hasPendingStart = false, confirmed = false)
        assertEquals(PauseAdvice.MidBattle(8), advice)
    }

    @Test
    fun `a pending start is never consulted while a run exists`() {
        // The two cannot coexist, and answering for the start would tell a player mid-battle that
        // there is nothing to lose.
        val advice = RunPause.advise(run(battle = marker(7)), hasPendingStart = true, confirmed = false)
        assertEquals(PauseAdvice.MidBattle(7), advice)
    }
}
