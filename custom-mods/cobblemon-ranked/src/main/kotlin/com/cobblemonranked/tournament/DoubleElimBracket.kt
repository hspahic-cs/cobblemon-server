package com.cobblemonranked.tournament

import java.util.UUID

/**
 * A pure, Minecraft-free double-elimination bracket engine. Seeded by the caller (seed 1 = top),
 * padded to the next power of two with byes given to the top seeds. Losers drop into a canonical
 * losers bracket; the grand final supports a bracket reset (the winners-bracket finalist must be
 * beaten twice).
 *
 * The engine is a state machine: [readyMatches] reports the matches whose two participants are both
 * known real players and which haven't been played; the driver plays one and calls [resolveByWinner].
 * Byes ("walkovers") resolve automatically at generation and as they propagate, so they never appear
 * as ready matches. When the last match resolves, [champion] is set and [complete] is true.
 *
 * This class owns NO scheduling, timing, or concurrency — that's the live driver's job. It is
 * deliberately deterministic and unit-tested.
 */
class DoubleElimBracket private constructor(
    val matches: List<BracketMatch>,
    private val byId: Map<Int, BracketMatch>,
    private val gf1Id: Int,
    private val gf2Id: Int,
) {
    var champion: BracketEntrant? = null
        private set
    var complete: Boolean = false
        private set

    fun match(id: Int): BracketMatch = byId.getValue(id)

    /** Matches playable right now (both real players, not yet played), in a stable schedule order. */
    fun readyMatches(): List<BracketMatch> = matches
        .filter { it.isReady }
        .sortedWith(compareBy({ it.side.ordinal }, { it.round }, { it.order }))

    /**
     * Record the result of a ready match. [winnerUuid] must be one of the match's two real players.
     * Propagates the winner (and loser) to their downstream slots, auto-resolving any byes, and
     * handles grand-final reset logic.
     */
    fun resolveByWinner(matchId: Int, winnerUuid: UUID) {
        val m = byId.getValue(matchId)
        require(m.isReady) { "match $matchId is not ready to be resolved" }
        val a = m.a as Participant.Real
        val b = m.b as Participant.Real
        val winnerIsA = when (winnerUuid) {
            a.entrant.uuid -> true
            b.entrant.uuid -> false
            else -> throw IllegalArgumentException("winner $winnerUuid is not in match $matchId")
        }
        recordResult(m, winnerIsA)
    }

    /** The two real players of a ready match, or null if it isn't a real-vs-real match. */
    fun playersOf(matchId: Int): Pair<BracketEntrant, BracketEntrant>? {
        val m = byId.getValue(matchId)
        val a = m.a as? Participant.Real ?: return null
        val b = m.b as? Participant.Real ?: return null
        return a.entrant to b.entrant
    }

    private fun recordResult(m: BracketMatch, winnerIsA: Boolean) {
        m.winner = if (winnerIsA) m.a else m.b
        m.loser = if (winnerIsA) m.b else m.a
        m.played = true
        if (m.side == BracketSide.GRAND_FINAL) {
            handleGrandFinal(m, winnerIsA)
            return
        }
        propagate(m)
    }

    private fun propagate(m: BracketMatch) {
        m.winnerTo?.let { (tid, intoA) -> setSlot(byId.getValue(tid), intoA, m.winner!!) }
        m.loserTo?.let { (tid, intoA) -> setSlot(byId.getValue(tid), intoA, m.loser!!) }
    }

    private fun setSlot(t: BracketMatch, intoA: Boolean, p: Participant) {
        if (intoA) t.a = p else t.b = p
        tryAutoResolve(t)
    }

    /** Resolve a match automatically iff it involves a bye (walkover). Real-vs-real waits for play. */
    private fun tryAutoResolve(t: BracketMatch) {
        if (!t.bothResolved || t.played || t.winner != null) return
        val a = t.a!!
        val b = t.b!!
        val aBye = a is Participant.Bye
        val bBye = b is Participant.Bye
        if (!aBye && !bBye) return // real vs real — needs a played result
        when {
            aBye && bBye -> { t.winner = Participant.Bye; t.loser = Participant.Bye }
            aBye -> { t.winner = b; t.loser = Participant.Bye }
            else -> { t.winner = a; t.loser = Participant.Bye }
        }
        // A walkover isn't "played" (no battle), but it still advances participants.
        if (t.side == BracketSide.GRAND_FINAL) {
            // Byes never reach the grand final in a valid bracket; guard defensively.
            (t.winner as? Participant.Real)?.let { champion = it.entrant; complete = true }
            return
        }
        propagate(t)
    }

    private fun handleGrandFinal(m: BracketMatch, winnerIsA: Boolean) {
        if (m.id == gf1Id) {
            if (winnerIsA) {
                // Slot A is the winners-bracket champion (undefeated) — they win outright.
                champion = (m.a as Participant.Real).entrant
                complete = true
            } else {
                // Losers-bracket champion handed the WB finalist their first loss → reset.
                val gf2 = byId.getValue(gf2Id)
                gf2.a = m.winner // LB champ
                gf2.b = m.loser  // WB champ
                // gf2 is now real-vs-real → becomes ready on the next scan.
            }
        } else {
            champion = (m.winner as Participant.Real).entrant
            complete = true
        }
    }

    // ------------------------------------------------------------------------------------------

    companion object {
        /**
         * Build a seeded double-elimination bracket. [entrants] need not be pre-sorted; they're
         * ordered by [BracketEntrant.seed] ascending (1 = top seed). Requires at least 2 entrants.
         */
        fun generate(entrants: List<BracketEntrant>): DoubleElimBracket {
            require(entrants.size >= 2) { "a tournament needs at least 2 entrants" }
            val seeded = entrants.sortedBy { it.seed }
            val n = seeded.size
            var s = 1
            while (s < n) s = s shl 1 // next power of two
            val k = Integer.numberOfTrailingZeros(s) // number of winners-bracket rounds
            val bySeed = seeded.associateBy { it.seed }

            val matches = ArrayList<BracketMatch>()
            var nextId = 0
            fun add(side: BracketSide, round: Int, order: Int, aRef: Slot, bRef: Slot): BracketMatch {
                val m = BracketMatch(nextId++, side, round, order, aRef, bRef)
                matches.add(m)
                return m
            }

            // --- Winners bracket ---
            val seedOrder = standardSeedOrder(s)
            val wbRounds = ArrayList<List<BracketMatch>>()
            val wbR1 = ArrayList<BracketMatch>()
            for (j in 0 until s / 2) {
                wbR1.add(add(BracketSide.WINNERS, 1, j, Slot.SeedRef(seedOrder[2 * j]), Slot.SeedRef(seedOrder[2 * j + 1])))
            }
            wbRounds.add(wbR1)
            for (r in 2..k) {
                val prev = wbRounds[r - 2]
                val cur = ArrayList<BracketMatch>()
                for (j in 0 until prev.size / 2) {
                    cur.add(add(BracketSide.WINNERS, r, j, Slot.Winner(prev[2 * j].id), Slot.Winner(prev[2 * j + 1].id)))
                }
                wbRounds.add(cur)
            }
            val wbFinal = wbRounds[k - 1][0]

            // --- Losers bracket ---
            // lbChampRef is the feeder for the LB champion (→ grand final slot B).
            val lbChampRef: Slot = if (k == 1) {
                // S == 2: no losers bracket; the WB final loser goes straight to the grand final.
                Slot.Loser(wbFinal.id)
            } else {
                var lbRound = 1
                // LB round 1: pair up the first-round WB losers.
                val r1Losers = wbRounds[0].map { Slot.Loser(it.id) }
                var prev: List<Slot> = run {
                    val lb1 = ArrayList<BracketMatch>()
                    for (j in 0 until r1Losers.size / 2) {
                        lb1.add(add(BracketSide.LOSERS, lbRound, j, r1Losers[2 * j], r1Losers[2 * j + 1]))
                    }
                    lb1.map { Slot.Winner(it.id) }
                }
                for (r in 2..k) {
                    // "Major" round: LB survivors vs the losers dropping from WB round r.
                    lbRound++
                    val wbLosers = wbRounds[r - 1].map { Slot.Loser(it.id) }
                    val major = ArrayList<BracketMatch>()
                    for (j in prev.indices) {
                        major.add(add(BracketSide.LOSERS, lbRound, j, prev[j], wbLosers[j]))
                    }
                    prev = major.map { Slot.Winner(it.id) }
                    if (prev.size > 1) {
                        // "Minor" round: LB survivors play each other.
                        lbRound++
                        val minor = ArrayList<BracketMatch>()
                        for (j in 0 until prev.size / 2) {
                            minor.add(add(BracketSide.LOSERS, lbRound, j, prev[2 * j], prev[2 * j + 1]))
                        }
                        prev = minor.map { Slot.Winner(it.id) }
                    }
                }
                prev[0] // Winner of the LB final
            }

            // --- Grand final (+ reset) ---
            val gf1 = add(BracketSide.GRAND_FINAL, 1, 0, Slot.Winner(wbFinal.id), lbChampRef)
            val gf2 = add(BracketSide.GRAND_FINAL, 2, 0, Slot.Winner(gf1.id), Slot.Loser(gf1.id))

            val byId = matches.associateBy { it.id }
            val bracket = DoubleElimBracket(matches, byId, gf1.id, gf2.id)

            // --- Wire reverse feeder links (skip GF2: its slots are filled manually on reset). ---
            for (m in matches) {
                if (m.id == gf2.id) continue
                bracket.link(m.aRef, m.id, true)
                bracket.link(m.bRef, m.id, false)
            }

            // --- Seed WB round 1 and cascade byes. ---
            for (m in wbR1) {
                m.a = participantForSeed((m.aRef as Slot.SeedRef).seed, bySeed, n)
                m.b = participantForSeed((m.bRef as Slot.SeedRef).seed, bySeed, n)
            }
            for (m in wbR1) bracket.tryAutoResolve(m)

            return bracket
        }

        private fun participantForSeed(seed: Int, bySeed: Map<Int, BracketEntrant>, n: Int): Participant =
            if (seed <= n) Participant.Real(bySeed.getValue(seed)) else Participant.Bye

        /** Standard single-elimination seeding order for a bracket of size [s] (a power of two). */
        fun standardSeedOrder(s: Int): List<Int> {
            var rounds = listOf(1, 2)
            while (rounds.size < s) {
                val sum = rounds.size * 2 + 1
                val next = ArrayList<Int>(rounds.size * 2)
                for (r in rounds) { next.add(r); next.add(sum - r) }
                rounds = next
            }
            return rounds
        }
    }

    private fun link(ref: Slot, targetId: Int, intoA: Boolean) {
        when (ref) {
            is Slot.Winner -> byId.getValue(ref.matchId).winnerTo = targetId to intoA
            is Slot.Loser -> byId.getValue(ref.matchId).loserTo = targetId to intoA
            is Slot.SeedRef -> {}
        }
    }
}

/** A tournament entrant with a seed (1 = top seed). */
data class BracketEntrant(val uuid: UUID, val name: String, val seed: Int)

enum class BracketSide { WINNERS, LOSERS, GRAND_FINAL }

/** How a match slot is fed. */
sealed interface Slot {
    data class Winner(val matchId: Int) : Slot
    data class Loser(val matchId: Int) : Slot
    data class SeedRef(val seed: Int) : Slot
}

/** Who occupies a slot: a real entrant, or a bye (empty). */
sealed interface Participant {
    data class Real(val entrant: BracketEntrant) : Participant
    object Bye : Participant
}

class BracketMatch(
    val id: Int,
    val side: BracketSide,
    val round: Int,
    val order: Int,
    val aRef: Slot,
    val bRef: Slot,
) {
    var a: Participant? = null
    var b: Participant? = null
    var winner: Participant? = null
    var loser: Participant? = null
    var played: Boolean = false
    var winnerTo: Pair<Int, Boolean>? = null // (matchId, intoSlotA)
    var loserTo: Pair<Int, Boolean>? = null

    val bothResolved: Boolean get() = a != null && b != null
    private val realVsReal: Boolean get() = a is Participant.Real && b is Participant.Real
    val isReady: Boolean get() = !played && winner == null && realVsReal

    fun label(): String = when (side) {
        BracketSide.WINNERS -> "Winners R$round"
        BracketSide.LOSERS -> "Losers R$round"
        BracketSide.GRAND_FINAL -> if (round == 1) "Grand Final" else "Grand Final (reset)"
    }
}
