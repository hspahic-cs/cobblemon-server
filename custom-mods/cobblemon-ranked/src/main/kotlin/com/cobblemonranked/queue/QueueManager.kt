package com.cobblemonranked.queue

import com.cobblemonranked.CobblemonRanked
import com.cobblemonranked.battle.RankedBattleManager
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.Style
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Open matchmaking queue for ranked PvP.
 *
 * Usage flow:
 *   1. Player A types `/queue` — joins the queue, broadcast announces them.
 *   2. Player B types `/queue` — if A and B haven't played each other this session,
 *      they're paired immediately and pushed into [RankedBattleManager.startTeamSelection].
 *   3. If they HAVE played each other already this session, B keeps waiting until someone
 *      else queues (or until A's matchmaking partner is matched off the queue first).
 *
 * `/queue auto` adds the player with the auto-requeue flag — when their match ends they get
 * added back to the queue automatically. They stay in auto mode until they `/queue cancel`.
 *
 * Played-this-session state is in-memory only; restarting the server resets which pairings
 * are eligible. ELO is read from [CobblemonRanked.eloStore] at queue-join time for the broadcast.
 */
object QueueManager {

    private data class Entry(val uuid: UUID, val name: String, val auto: Boolean)

    /** Insertion-ordered queue of waiting players. Key is UUID; value carries name + auto flag. */
    private val queue: java.util.LinkedHashMap<UUID, Entry> = java.util.LinkedHashMap()
    private val queueLock = Any()

    /**
     * Pairs of (sorted UUIDs) that have already played each other this server session. Cleared
     * on restart — we don't persist "who has played whom" across server restarts.
     */
    private val playedPairs: MutableSet<Pair<UUID, UUID>> = ConcurrentHashMap.newKeySet()

    /** UUIDs currently in /queue auto mode — they re-enter the queue when their match ends. */
    private val autoQueuers: MutableSet<UUID> = ConcurrentHashMap.newKeySet()

    /** Returns true if [a] and [b] are already paired in any active or pending ranked context. */
    private fun pairKey(a: UUID, b: UUID): Pair<UUID, UUID> =
        if (a.toString() < b.toString()) a to b else b to a

    /**
     * Add [player] to the queue. Tries to pair immediately; if no eligible partner is in the
     * queue, the player waits and a public announcement goes out so others know to /queue too.
     * Returns a short status code string for the command handler to surface to the player.
     */
    fun join(player: ServerPlayer, auto: Boolean): JoinResult {
        val server = player.server
        synchronized(queueLock) {
            if (queue.containsKey(player.uuid)) {
                if (auto && !autoQueuers.contains(player.uuid)) {
                    autoQueuers += player.uuid
                    return JoinResult.AlreadyQueued(autoFlipped = true)
                }
                return JoinResult.AlreadyQueued(autoFlipped = false)
            }
            if (auto) autoQueuers += player.uuid

            // Look for the first queued player we haven't played yet.
            val partnerUuid = queue.keys.firstOrNull { other ->
                pairKey(player.uuid, other) !in playedPairs
            }

            if (partnerUuid != null) {
                val partner = queue.remove(partnerUuid)!!
                val partnerPlayer = server.playerList.getPlayer(partnerUuid)
                if (partnerPlayer == null) {
                    // Partner went offline between queueing and matching — fall through to "wait".
                    queue.remove(partnerUuid)
                    autoQueuers -= partnerUuid
                    broadcastJoin(server, player)
                    queue[player.uuid] = Entry(player.uuid, player.name.string, auto)
                    return JoinResult.WaitingForPartner
                }
                playedPairs += pairKey(player.uuid, partnerUuid)
                RankedBattleManager.startTeamSelection(partnerPlayer, player)
                return JoinResult.Matched(partner.name)
            }

            // No eligible partner — wait.
            queue[player.uuid] = Entry(player.uuid, player.name.string, auto)
            broadcastJoin(server, player)
            return JoinResult.WaitingForPartner
        }
    }

    /** Leave the queue + clear auto flag. Returns true if the player was actually queued. */
    fun leave(playerUuid: UUID): Boolean {
        synchronized(queueLock) {
            val wasQueued = queue.remove(playerUuid) != null
            autoQueuers -= playerUuid
            return wasQueued
        }
    }

    /**
     * Called when a ranked match ends. Re-queues both players if either had the auto flag set.
     * Also records the pair as having played each other so the queue won't immediately rematch
     * them next time they both queue.
     */
    fun onMatchEnded(server: MinecraftServer, p1: UUID, p2: UUID) {
        playedPairs += pairKey(p1, p2)
        for (uuid in listOf(p1, p2)) {
            if (autoQueuers.contains(uuid)) {
                val online = server.playerList.getPlayer(uuid) ?: continue
                join(online, auto = true)
            }
        }
    }

    fun showQueue(viewer: ServerPlayer) {
        synchronized(queueLock) {
            if (queue.isEmpty()) {
                viewer.sendSystemMessage(Component.literal("§7[Queue] No one is queued right now — §f/queue§7 to start."))
                return
            }
            viewer.sendSystemMessage(Component.literal("§e§l[Queue] §r§fwaiting (${queue.size}):"))
            for ((i, e) in queue.values.withIndex()) {
                val elo = CobblemonRanked.eloStore.getOrCreate(e.uuid, e.name).elo
                val tag = if (e.auto) " §8(auto)" else ""
                viewer.sendSystemMessage(Component.literal("§7  ${i + 1}. §f${e.name} §7(ELO §e$elo§7)$tag"))
            }
        }
    }

    private fun broadcastJoin(server: MinecraftServer, player: ServerPlayer) {
        val elo = CobblemonRanked.eloStore.getOrCreate(player.uuid, player.name.string).elo
        val queueClick = Component.literal("§a[/queue]")
            .setStyle(Style.EMPTY
                .withClickEvent(ClickEvent(ClickEvent.Action.RUN_COMMAND, "/queue"))
                .withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    Component.literal("§aClick to join the queue"))))
        val challengeClick = Component.literal("§b[/challenge ${player.name.string}]")
            .setStyle(Style.EMPTY
                .withClickEvent(ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/ranked challenge ${player.name.string} "))
                .withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    Component.literal("§bClick to fill /ranked challenge in your chat box"))))

        val msg = Component.literal("§e§l[Ranked Queue] §r§f${player.name.string} §7(ELO §e$elo§7) is queueing. ")
            .append(queueClick)
            .append(Component.literal("§7 to join, or "))
            .append(challengeClick)
            .append(Component.literal("§7 to challenge directly."))

        server.playerList.players.forEach { it.sendSystemMessage(msg) }
    }

    sealed class JoinResult {
        data class Matched(val partnerName: String) : JoinResult()
        object WaitingForPartner : JoinResult()
        data class AlreadyQueued(val autoFlipped: Boolean) : JoinResult()
    }
}
