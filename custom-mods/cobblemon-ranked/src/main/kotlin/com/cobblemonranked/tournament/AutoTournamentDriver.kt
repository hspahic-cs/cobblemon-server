package com.cobblemonranked.tournament

import com.cobblemonranked.battle.BattleMode
import com.cobblemonranked.battle.RankedBattleManager
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Drives an automated double-elimination tournament to completion.
 *
 * Lifecycle (all admin/command-driven start, then hands-off):
 *  1. `/ranked tournament close auto` → [start]: seed the [DoubleElimBracket] by ELO and schedule.
 *  2. Up to [MAX_CONCURRENT] matches are "active" at once. A match becoming active opens a
 *     **ready gate**: both players are told they're up and must type `/ready` within
 *     [READY_SECONDS]. Both ready → [RankedBattleManager.startAutoTournamentMatch] runs the match
 *     (team-select → battle, with the per-match 2-minute selection clock + 3-strike cancel rule
 *     enforced inside RankedBattleManager).
 *  3. A no-show at the ready gate forfeits: one ready → that player advances; neither → the higher
 *     seed advances. Every terminal outcome (battle result, ready no-show, selection DQ/timeout)
 *     funnels back through [reportOutcome]/[progress], which advances the bracket and schedules the
 *     next matches. When the bracket completes, the champion is announced and state cleared.
 *
 * Not persisted — a server restart abandons an in-progress tournament (matches were never
 * persisted anyway). Only one tournament runs at a time.
 */
object AutoTournamentDriver {

    const val MAX_CONCURRENT = 2
    const val READY_SECONDS = 180

    /** Ready-gate reminder thresholds (seconds remaining) at which we ping un-readied players. */
    private val READY_REMINDERS = intArrayOf(120, 60, 30, 10)

    private enum class Phase { AWAITING_READY, IN_MATCH }

    private class Live(
        val matchId: Int,
        val p1: UUID,
        val p2: UUID,
        var phase: Phase,
        var readyDeadlineSec: Long,
        val ready: MutableSet<UUID> = ConcurrentHashMap.newKeySet(),
        var lastReminder: Int = Int.MAX_VALUE,
    )

    private var bracket: DoubleElimBracket? = null
    private var mode: BattleMode = BattleMode.SINGLES
    private var seedByUuid: Map<UUID, Int> = emptyMap()
    private var nameByUuid: Map<UUID, String> = emptyMap()

    private val active = ConcurrentHashMap<Int, Live>()
    private val playerToMatch = ConcurrentHashMap<UUID, Int>()

    fun isActive(): Boolean = bracket?.let { !it.complete } ?: false

    private fun nowSec(): Long = System.currentTimeMillis() / 1000L

    // ---------------------------------------------------------------------------------------------

    /** Begin an automated tournament. [entrants] must already carry seeds (1 = top). */
    fun start(server: MinecraftServer, entrants: List<BracketEntrant>, mode: BattleMode): String? {
        if (isActive()) return "A tournament is already running. Use /ranked tournament cancel first."
        if (entrants.size < 2) return "Need at least 2 entrants to run a bracket."
        val b = try {
            DoubleElimBracket.generate(entrants)
        } catch (e: Exception) {
            CobblemonRankedLog.error("Failed to generate bracket", e)
            return "Failed to build the bracket: ${e.message}"
        }
        bracket = b
        this.mode = mode
        seedByUuid = entrants.associate { it.uuid to it.seed }
        nameByUuid = entrants.associate { it.uuid to it.name }
        active.clear()
        playerToMatch.clear()

        broadcast(server, "§6§l[Tournament] §r§e${mode.displayName} double-elimination bracket started " +
            "with §f${entrants.size} §eplayers, seeded by ELO!")
        val top = entrants.sortedBy { it.seed }.take(4).joinToString("§7, §f") { "#${it.seed} ${it.name}" }
        broadcast(server, "§7[Tournament] Top seeds: §f$top§7. When you're up, you'll be told to §a/ready§7.")
        schedule(server)
        return null
    }

    /** Abort the current tournament (admin). */
    fun abort(server: MinecraftServer) {
        if (bracket == null) return
        broadcast(server, "§c[Tournament] The tournament was cancelled by an admin.")
        clearState()
    }

    private fun clearState() {
        bracket = null
        active.clear()
        playerToMatch.clear()
        seedByUuid = emptyMap()
        nameByUuid = emptyMap()
    }

    // ---------------------------------------------------------------------------------------------

    /** Per-second driver: ready-gate reminders + no-show handling, then (re)schedule. */
    fun tick(server: MinecraftServer) {
        val b = bracket ?: return
        val now = nowSec()
        for (live in active.values.toList()) {
            if (live.phase != Phase.AWAITING_READY) continue
            val remaining = (live.readyDeadlineSec - now).toInt()
            if (remaining <= 0) {
                resolveNoShow(server, live)
                continue
            }
            // Remind whoever hasn't readied yet at each threshold.
            for (bkt in READY_REMINDERS) {
                if (remaining <= bkt && live.lastReminder > bkt) {
                    live.lastReminder = bkt
                    val label = b.match(live.matchId).label()
                    for (uuid in listOf(live.p1, live.p2)) {
                        if (uuid in live.ready) continue
                        val opp = if (uuid == live.p1) live.p2 else live.p1
                        server.playerList.getPlayer(uuid)?.sendSystemMessage(Component.literal(
                            "§e[Tournament] §6$label §7vs §f${nameByUuid[opp] ?: "?"}§7 — type §a/ready §7(§c${fmt(remaining)}§7 left)"))
                    }
                    break
                }
            }
        }
        schedule(server)
    }

    /** Fill open concurrency slots with the next ready bracket matches. */
    private fun schedule(server: MinecraftServer) {
        val b = bracket ?: return
        while (active.size < MAX_CONCURRENT) {
            val next = b.readyMatches().firstOrNull { !active.containsKey(it.id) } ?: break
            beginReadyGate(server, next.id)
        }
        if (b.complete && active.isEmpty()) finish(server)
    }

    private fun beginReadyGate(server: MinecraftServer, matchId: Int) {
        val b = bracket ?: return
        val (ea, eb) = b.playersOf(matchId) ?: return
        val live = Live(matchId, ea.uuid, eb.uuid, Phase.AWAITING_READY, nowSec() + READY_SECONDS)
        active[matchId] = live
        playerToMatch[ea.uuid] = matchId
        playerToMatch[eb.uuid] = matchId
        val label = b.match(matchId).label()
        broadcast(server, "§6[Tournament] §eNext up §7($label)§e: §f${ea.name} §7vs §f${eb.name}§7 — " +
            "both must type §a/ready§7 within §f3:00§7.")
        for (uuid in listOf(ea.uuid, eb.uuid)) {
            val opp = if (uuid == ea.uuid) eb.name else ea.name
            server.playerList.getPlayer(uuid)?.sendSystemMessage(Component.literal(
                "§a§l[Tournament] You're up! §r§f$label §7vs §f$opp§7. Type §a/ready §7within §f3:00 §7or you forfeit."))
        }
    }

    /** A player typed /ready. Returns a chat status regardless of whether they were actually up. */
    fun markReady(player: ServerPlayer): Component {
        if (!isActive()) return Component.literal("§c[Tournament] No tournament is running.")
        val matchId = playerToMatch[player.uuid]
            ?: return Component.literal("§7[Tournament] You're not up for a match right now.")
        val live = active[matchId] ?: return Component.literal("§7[Tournament] You're not up for a match right now.")
        if (live.phase != Phase.AWAITING_READY) return Component.literal("§7[Tournament] Your match is already starting.")
        if (!live.ready.add(player.uuid)) return Component.literal("§a[Tournament] You're ready — waiting for your opponent.")

        val oppUuid = if (player.uuid == live.p1) live.p2 else live.p1
        if (live.ready.containsAll(listOf(live.p1, live.p2))) {
            startMatch(player.server, live)
            return Component.literal("§a[Tournament] Both ready — starting your match!")
        }
        player.server.playerList.getPlayer(oppUuid)?.sendSystemMessage(Component.literal(
            "§e[Tournament] §f${player.name.string}§e is ready — §a/ready §ewhen you are."))
        return Component.literal("§a[Tournament] Ready! Waiting for §f${nameByUuid[oppUuid] ?: "opponent"}§a.")
    }

    private fun startMatch(server: MinecraftServer, live: Live) {
        live.phase = Phase.IN_MATCH
        val p1 = server.playerList.getPlayer(live.p1)
        val p2 = server.playerList.getPlayer(live.p2)
        if (p1 == null || p2 == null) {
            // Someone dropped between readying and starting — resolve as a no-show for the absentee.
            val winner = when {
                p1 != null -> live.p1
                p2 != null -> live.p2
                else -> higherSeed(live.p1, live.p2)
            }
            announceForfeit(server, live, winner, "left before the match could start")
            progress(server, live.matchId, winner)
            return
        }
        val err = RankedBattleManager.startAutoTournamentMatch(p1, p2)
        if (err != null) {
            // Couldn't start (e.g. roster problem). Advance the higher seed so the bracket survives.
            val winner = higherSeed(live.p1, live.p2)
            broadcast(server, "§c[Tournament] Couldn't start ${nameByUuid[live.p1]} vs ${nameByUuid[live.p2]}: $err")
            announceForfeit(server, live, winner, "match could not be started")
            progress(server, live.matchId, winner)
        }
        // On success, RankedBattleManager owns the match and will call reportOutcome when it ends.
    }

    private fun resolveNoShow(server: MinecraftServer, live: Live) {
        val winner = when (live.ready.size) {
            1 -> live.ready.first()
            0 -> higherSeed(live.p1, live.p2)
            else -> higherSeed(live.p1, live.p2) // both ready but still awaiting is not expected
        }
        val reason = if (live.ready.isEmpty()) "neither player readied up" else "opponent didn't ready up"
        announceForfeit(server, live, winner, reason)
        progress(server, live.matchId, winner)
    }

    private fun announceForfeit(server: MinecraftServer, live: Live, winnerUuid: UUID, reason: String) {
        val loserUuid = if (winnerUuid == live.p1) live.p2 else live.p1
        broadcast(server, "§6[Tournament] §f${nameByUuid[winnerUuid] ?: "?"} §aadvances §7— " +
            "§f${nameByUuid[loserUuid] ?: "?"} §7$reason.")
    }

    /**
     * Called by [RankedBattleManager] when an auto-tournament match reaches a terminal result
     * (battle victory, or a pre-battle forfeit/DQ/selection-timeout). Advances the bracket.
     */
    fun reportOutcome(server: MinecraftServer, winnerUuid: UUID, loserUuid: UUID) {
        val matchId = playerToMatch[winnerUuid] ?: playerToMatch[loserUuid] ?: return
        progress(server, matchId, winnerUuid)
    }

    /** Advance the bracket for [matchId] with [winnerUuid], then schedule/finish. */
    private fun progress(server: MinecraftServer, matchId: Int, winnerUuid: UUID) {
        val b = bracket ?: return
        val live = active.remove(matchId)
        if (live != null) {
            playerToMatch.remove(live.p1)
            playerToMatch.remove(live.p2)
        }
        val m = b.match(matchId)
        if (m.isReady) {
            try {
                b.resolveByWinner(matchId, winnerUuid)
            } catch (e: Exception) {
                CobblemonRankedLog.error("Failed to advance bracket match $matchId", e)
            }
        }
        if (b.complete) finish(server) else schedule(server)
    }

    private fun finish(server: MinecraftServer) {
        val champ = bracket?.champion
        if (champ != null) {
            broadcast(server, "§6§l[Tournament] §r§e🏆 §f${champ.name} §ewins the ${mode.displayName} tournament! §7GG to all ${nameByUuid.size} entrants.")
        }
        clearState()
    }

    /** Higher seed = smaller seed number. Falls back to [a] if seeds are unknown. */
    fun higherSeed(a: UUID, b: UUID): UUID {
        val sa = seedByUuid[a] ?: Int.MAX_VALUE
        val sb = seedByUuid[b] ?: Int.MAX_VALUE
        return if (sa <= sb) a else b
    }

    // ---------------------------------------------------------------------------------------------

    /** Human-readable status for `/ranked tournament bracket`. */
    fun statusLines(): List<String> {
        val b = bracket ?: return listOf("§7[Tournament] No automated tournament is running.")
        if (b.complete) return listOf("§6[Tournament] Complete — champion: §f${b.champion?.name ?: "?"}")
        val lines = ArrayList<String>()
        lines.add("§6[Tournament] §e${mode.displayName} double-elim §7— ${nameByUuid.size} entrants")
        val now = nowSec()
        if (active.isEmpty()) {
            lines.add("§7  (no active matches this moment)")
        } else {
            for (live in active.values.sortedBy { it.matchId }) {
                val m = b.match(live.matchId)
                val names = "§f${nameByUuid[live.p1]} §7vs §f${nameByUuid[live.p2]}"
                val state = when (live.phase) {
                    Phase.AWAITING_READY -> {
                        val left = (live.readyDeadlineSec - now).coerceAtLeast(0)
                        val got = live.ready.size
                        "§eawaiting /ready §7($got/2, ${fmt(left.toInt())} left)"
                    }
                    Phase.IN_MATCH -> "§ain progress"
                }
                lines.add("§7  ${m.label()}: $names §7— $state")
            }
        }
        val remaining = b.readyMatches().count { !active.containsKey(it.id) }
        if (remaining > 0) lines.add("§7  +$remaining more match(es) queued")
        return lines
    }

    private fun fmt(seconds: Int): String {
        val s = seconds.coerceAtLeast(0)
        return "%d:%02d".format(s / 60, s % 60)
    }

    private fun broadcast(server: MinecraftServer, msg: String) {
        val c = Component.literal(msg)
        server.playerList.players.forEach { it.sendSystemMessage(c) }
    }
}

/** Small logger indirection so the pure-JVM bracket test doesn't drag in the mod entrypoint. */
private object CobblemonRankedLog {
    fun error(msg: String, e: Throwable) = com.cobblemonranked.CobblemonRanked.logger.error(msg, e)
}
