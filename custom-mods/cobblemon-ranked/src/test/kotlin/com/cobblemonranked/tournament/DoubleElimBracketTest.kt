package com.cobblemonranked.tournament

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.assertThrows

class DoubleElimBracketTest {

    private fun entrants(n: Int): List<BracketEntrant> =
        (1..n).map { BracketEntrant(UUID.randomUUID(), "P$it", it) }

    /** Play the whole bracket, resolving each ready match via [decide]; returns the champion. */
    private fun playOut(
        bracket: DoubleElimBracket,
        decide: (BracketEntrant, BracketEntrant) -> BracketEntrant,
    ): Pair<BracketEntrant, Int> {
        var played = 0
        var guard = 0
        while (!bracket.complete) {
            val ready = bracket.readyMatches()
            assertTrue(ready.isNotEmpty(), "bracket stalled: not complete but no ready matches")
            val m = ready.first()
            val (a, b) = bracket.playersOf(m.id)!!
            bracket.resolveByWinner(m.id, decide(a, b).uuid)
            played++
            assertTrue(guard++ < 10_000, "runaway bracket")
        }
        return bracket.champion!! to played
    }

    private val higherSeedWins: (BracketEntrant, BracketEntrant) -> BracketEntrant =
        { a, b -> if (a.seed < b.seed) a else b }

    @Test
    fun `standard seed order is correct`() {
        assertEquals(listOf(1, 2), DoubleElimBracket.standardSeedOrder(2))
        assertEquals(listOf(1, 4, 2, 3), DoubleElimBracket.standardSeedOrder(4))
        assertEquals(listOf(1, 8, 4, 5, 2, 7, 3, 6), DoubleElimBracket.standardSeedOrder(8))
    }

    @Test
    fun `power-of-two brackets - top seed wins and match count is 2S-2`() {
        for ((n, expectedPlayed) in listOf(2 to 2, 4 to 6, 8 to 14, 16 to 30)) {
            val bracket = DoubleElimBracket.generate(entrants(n))
            val (champ, played) = playOut(bracket, higherSeedWins)
            assertEquals(1, champ.seed, "top seed should win when higher seed always wins (N=$n)")
            assertEquals(expectedPlayed, played, "expected 2S-2 real matches for N=$n")
        }
    }

    @Test
    fun `non-power-of-two brackets complete with byes for top seeds`() {
        for (n in listOf(3, 5, 6, 7, 9, 11, 13)) {
            val bracket = DoubleElimBracket.generate(entrants(n))
            val (champ, _) = playOut(bracket, higherSeedWins)
            assertEquals(1, champ.seed, "top seed should win (N=$n)")
            assertTrue(bracket.complete)
        }
    }

    @Test
    fun `ready matches never contain a bye`() {
        val bracket = DoubleElimBracket.generate(entrants(5))
        // Drive a few rounds and assert every ready match is real-vs-real throughout.
        var guard = 0
        while (!bracket.complete) {
            for (m in bracket.readyMatches()) {
                assertTrue(m.a is Participant.Real && m.b is Participant.Real,
                    "ready match ${m.id} had a bye")
            }
            val m = bracket.readyMatches().first()
            val (a, b) = bracket.playersOf(m.id)!!
            bracket.resolveByWinner(m.id, higherSeedWins(a, b).uuid)
            assertTrue(guard++ < 10_000)
        }
    }

    @Test
    fun `grand final without reset ends immediately when WB champion wins`() {
        val es = entrants(2)
        val (e1, e2) = es[0] to es[1]
        val bracket = DoubleElimBracket.generate(es)
        // WB final (only WB match): seed 1 wins.
        val wb = bracket.readyMatches().single { it.side == BracketSide.WINNERS }
        bracket.resolveByWinner(wb.id, e1.uuid)
        // Grand final 1: WB champ (e1) wins → done, no reset.
        val gf1 = bracket.readyMatches().single()
        assertEquals(BracketSide.GRAND_FINAL, gf1.side)
        assertEquals(1, gf1.round)
        bracket.resolveByWinner(gf1.id, e1.uuid)
        assertTrue(bracket.complete)
        assertEquals(e1, bracket.champion)
    }

    @Test
    fun `grand final reset - LB champion must win twice`() {
        val es = entrants(2)
        val (e1, e2) = es[0] to es[1]
        val bracket = DoubleElimBracket.generate(es)
        val wb = bracket.readyMatches().single { it.side == BracketSide.WINNERS }
        bracket.resolveByWinner(wb.id, e1.uuid) // e1 → WB champ, e2 → LB champ
        val gf1 = bracket.readyMatches().single()
        bracket.resolveByWinner(gf1.id, e2.uuid) // LB champ upsets → reset
        assertFalse(bracket.complete, "a reset match must follow")
        val gf2 = bracket.readyMatches().single()
        assertEquals(2, gf2.round, "should be the grand-final reset match")
        bracket.resolveByWinner(gf2.id, e1.uuid) // WB champ takes the reset
        assertTrue(bracket.complete)
        assertEquals(e1, bracket.champion)
    }

    @Test
    fun `losers-bracket run - a once-beaten player can still win it all`() {
        // 4 players. Seed 2 loses in WB round 1 (upset by seed 3), then runs the losers bracket
        // and wins the whole thing. Verifies loser routing + reset all work together.
        val es = entrants(4)
        val e = es.associateBy { it.seed }
        val bracket = DoubleElimBracket.generate(es)

        fun ready(pred: (BracketMatch) -> Boolean) = bracket.readyMatches().first(pred)

        // WB R1: (1v4) and (2v3) per standard seeding. 1 beats 4; 3 upsets 2.
        bracket.resolveByWinner(ready { it.side == BracketSide.WINNERS && it.round == 1 && it.order == 0 }.id, e.getValue(1).uuid)
        bracket.resolveByWinner(ready { it.side == BracketSide.WINNERS && it.round == 1 && it.order == 1 }.id, e.getValue(3).uuid)
        // Now everything from here: seed 2 wins out. Higher-of-remaining otherwise.
        val champ = playOut(bracket) { a, b ->
            when {
                a.seed == 2 -> a
                b.seed == 2 -> b
                else -> if (a.seed < b.seed) a else b
            }
        }.first
        assertEquals(2, champ.seed)
    }

    @Test
    fun `fewer than two entrants is rejected`() {
        assertThrows<IllegalArgumentException> { DoubleElimBracket.generate(entrants(1)) }
    }
}
