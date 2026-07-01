package com.cobblemonranked.battle

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor
import com.cobblemon.mod.common.battles.MoveActionResponse
import com.cobblemon.mod.common.battles.SwitchActionResponse
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Per-decision move timer for tournament matches. Driven once per second by
 * [RankedBattleManager.tickTournamentTimers]. Owns only the countdown bookkeeping; the match
 * roster and the admin-host lookup are passed in each tick.
 *
 * The clock is per player and per decision. Cobblemon (Showdown) sets `actor.mustChoose = true`
 * with an attached `actor.request` whenever an actor owes a choice, and clears it once they
 * submit. There is no server-side "new turn" event, so we poll `mustChoose`: while it's true we
 * count the actor's seconds; when it flips false (they chose, or it isn't their decision) we drop
 * their timer. This re-arms automatically every turn and on each mid-battle forced switch, and it
 * gives the per-player property for free — a player who already locked in has `mustChoose == false`
 * and sees no countdown.
 *
 * Timeline (seconds elapsed on the current decision):
 *   - 0                          armed, 60s left, silent
 *   - warnings at 30/15/10 left  chat
 *   - warnings at 5/3/2/1/0 left action bar (0 left == 60s elapsed)
 *   - 61..63                     silent grace
 *   - 64                         auto-select fires
 */
object TournamentTurnTimer {

    private const val LIMIT_SECONDS = 60
    private const val GRACE_SECONDS = 4

    /** Seconds-remaining values that warn in chat (the sparse, early ones). */
    private val CHAT_WARNINGS = setOf(30, 15, 10)
    /** Seconds-remaining values that warn on the action bar (the rapid final ones). */
    private val ACTION_BAR_WARNINGS = setOf(5, 3, 2, 1, 0)

    /** Elapsed seconds on the current decision, keyed by "battleId|actorUuid". */
    private val elapsed = ConcurrentHashMap<String, Int>()

    private fun keyOf(battleId: UUID, actorUuid: UUID) = "$battleId|$actorUuid"

    /**
     * Advance every timed match by one second. [matches] are the live tournament matches;
     * [hostOf] maps a participant UUID to the admin host that started the match (or null).
     */
    fun tick(server: MinecraftServer, matches: List<ActiveRankedMatch>, hostOf: (UUID) -> UUID?) {
        val live = HashSet<String>()
        for (match in matches) {
            val battleId = match.battleId ?: continue
            val battle = Cobblemon.battleRegistry.getBattle(battleId) ?: continue
            for (actor in battle.actors) {
                if (actor !is PlayerBattleActor) continue
                val key = keyOf(battleId, actor.uuid)

                if (!actor.mustChoose) {
                    // Not this actor's decision (submitted, or waiting on the opponent) — disarm.
                    elapsed.remove(key)
                    continue
                }

                val e = (elapsed[key] ?: -1) + 1
                elapsed[key] = e
                live.add(key)
                val secondsLeft = LIMIT_SECONDS - e
                val player = server.playerList.getPlayer(actor.uuid)

                when {
                    e >= LIMIT_SECONDS + GRACE_SECONDS -> {
                        forcePick(server, battle, actor, hostOf(actor.uuid))
                        elapsed.remove(key)
                        live.remove(key)
                    }
                    secondsLeft in CHAT_WARNINGS ->
                        player?.sendSystemMessage(chatWarning(secondsLeft))
                    secondsLeft in ACTION_BAR_WARNINGS ->
                        player?.displayClientMessage(actionBarWarning(secondsLeft), true)
                }
            }
        }
        // Prune state for actors/battles no longer choosing (battle ended, actor left, etc.).
        elapsed.keys.retainAll(live)
    }

    /**
     * Submit an automatic choice for [actor] and, if one actually fired, announce it to the
     * scoped audience (both players, spectators, host). No-op with no announcement if the actor
     * has no request or no legal action to take.
     */
    private fun forcePick(server: MinecraftServer, battle: PokemonBattle, actor: BattleActor, hostUuid: UUID?) {
        val request = actor.request ?: return
        if (request.wait) return

        val playerName = server.playerList.getPlayer(actor.uuid)?.name?.string ?: "A player"

        // A faint (or any forced-switch slot) means the only legal action is a switch. Otherwise
        // it's a normal turn: default to attacking with move slot 1 (next legal move if disabled /
        // out of PP; Showdown substitutes Struggle when nothing has PP, and that lands here too).
        if (request.forceSwitch.getOrNull(0) == true) {
            val activeUuids = actor.activePokemon.mapNotNull { it.battlePokemon?.uuid }.toSet()
            val next = actor.pokemonList.firstOrNull { it.health > 0 && it.uuid !in activeUuids } ?: return
            actor.forceChoose(SwitchActionResponse(next.uuid))
            val name = next.effectedPokemon.species.translatedName.string
            announce(server, battle, hostUuid, Component.literal(
                "§6[Tournament] §e$playerName §7ran out of time. §f$name §7was sent in."))
        } else {
            val moveset = request.active?.getOrNull(0) ?: return
            val move = moveset.moves.firstOrNull { it.canBeUsed() }
                ?: moveset.moves.firstOrNull()
                ?: return
            actor.forceChoose(MoveActionResponse(move.move))
            announce(server, battle, hostUuid, Component.literal(
                "§6[Tournament] §e$playerName §7ran out of time. §f${move.move} §7was auto-selected."))
        }
    }

    /** Send [msg] to exactly the two players, the battle's spectators, and the host (deduped). */
    private fun announce(server: MinecraftServer, battle: PokemonBattle, hostUuid: UUID?, msg: Component) {
        val recipients = LinkedHashSet<UUID>()
        battle.players.forEach { recipients.add(it.uuid) }
        recipients.addAll(battle.spectators)
        hostUuid?.let { recipients.add(it) }
        recipients.forEach { server.playerList.getPlayer(it)?.sendSystemMessage(msg) }
    }

    private fun chatWarning(secondsLeft: Int): Component {
        val color = if (secondsLeft <= 10) "§c" else "§e"
        return Component.literal("§e[Tournament] $color$secondsLeft§e seconds to choose your move.")
    }

    private fun actionBarWarning(secondsLeft: Int): Component =
        if (secondsLeft <= 0) Component.literal("§c§lTime!")
        else Component.literal("§c§l$secondsLeft…")
}
