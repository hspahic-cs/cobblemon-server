package com.cobblemonranked.battle

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Time-bank (chess-clock, Showdown-style) move clock for tournament matches. Driven once per
 * second by [RankedBattleManager.tickTournamentTimers]. Owns only the clock bookkeeping and the
 * action-bar HUD; the match roster and the timeout resolution are passed in each tick.
 *
 * Model — per player, per battle:
 *   - [TURN_ALLOWANCE_SECONDS] free every decision (resets each turn).
 *   - [RESERVE_BANK_SECONDS] reserve that persists across the whole battle and does NOT replenish.
 *   - A decision only eats the reserve *after* the allowance is spent. The reserve is debited by the
 *     actual overage when the decision completes.
 *   - Reserve runs out while still over the allowance → the player LOSES (via the [onTimeout]
 *     callback, wired to `forfeitMatch`). No auto-pick — that path never worked on forced switches,
 *     and a time-loss sidesteps having to force a legal choice into Showdown entirely.
 *
 * Pokémon turns are *simultaneous*: both actors have `mustChoose == true` during move selection, so
 * both clocks tick at once. The moment a player locks in, their `mustChoose` flips false and their
 * clock pauses (we commit that turn's overage). Forced switches (after a faint) are one-sided but
 * are just another timed decision under this model.
 *
 * HUD: for the entire match, each combatant's action bar shows their clock every second. The
 * `server-quests` datapack HUD is suppressed for these players via the [BATTLE_TAG] scoreboard tag
 * (added in `startBattle`, removed in `notifyMatchEnded`), so this HUD owns the bar uncontested.
 */
object TournamentTurnTimer {

    const val TURN_ALLOWANCE_SECONDS = 30
    const val RESERVE_BANK_SECONDS = 150

    /** Scoreboard tag stamped on both combatants for the life of a tournament match. The
     *  `server-quests` datapack skips its action-bar HUD for `@a[tag=!rt_battle]`, so this clock
     *  owns the action bar. Also cleared globally in the datapack's `load.mcfunction` as a
     *  crash backstop. */
    const val BATTLE_TAG = "rt_battle"

    /** Reserve seconds remaining, keyed by "battleId|actorUuid". Persists across decisions. */
    private val reserve = ConcurrentHashMap<String, Int>()

    /** Seconds spent on the *current* decision, keyed by "battleId|actorUuid". Reset each turn. */
    private val turnElapsed = ConcurrentHashMap<String, Int>()

    private fun keyOf(battleId: UUID, actorUuid: UUID) = "$battleId|$actorUuid"

    /**
     * Advance every timed match by one second. [matches] are the live tournament matches;
     * [hostOf] maps a participant UUID to the admin host that started the match (or null — unused
     * for messaging here but kept for parity with the announce audience). [onTimeout] is invoked
     * with the player whose clock hit zero; it must resolve the loss (end the battle, apply ELO).
     */
    fun tick(
        server: MinecraftServer,
        matches: List<ActiveRankedMatch>,
        @Suppress("UNUSED_PARAMETER") hostOf: (UUID) -> UUID?,
        onTimeout: (ServerPlayer) -> Unit,
    ) {
        val liveKeys = HashSet<String>()
        for (match in matches) {
            val battleId = match.battleId ?: continue
            val battle = Cobblemon.battleRegistry.getBattle(battleId) ?: continue
            val playerActors = battle.actors.filterIsInstance<PlayerBattleActor>()

            for (actor in playerActors) {
                val key = keyOf(battleId, actor.uuid)
                liveKeys.add(key)
                val bank = reserve.getOrPut(key) { RESERVE_BANK_SECONDS }
                val player = server.playerList.getPlayer(actor.uuid)
                val opponentChoosing = playerActors.any { it.uuid != actor.uuid && it.mustChoose }

                if (actor.mustChoose) {
                    val elapsed = (turnElapsed[key] ?: 0) + 1
                    turnElapsed[key] = elapsed
                    val overage = elapsed - TURN_ALLOWANCE_SECONDS

                    if (overage > 0) {
                        val reserveLeft = bank - overage
                        if (reserveLeft < 0) {
                            // Out of time — this player loses. Drop state and let [onTimeout]
                            // end the match; the battle disappears from [matches] next tick.
                            reserve.remove(key)
                            turnElapsed.remove(key)
                            if (player != null) onTimeout(player)
                            break // stop touching this (now-ending) battle's actors
                        }
                        actionBar(player, Component.literal(
                            "§c⏳ Your turn §7• §c§lReserve ${reserveLeft}s"))
                    } else {
                        val turnLeft = TURN_ALLOWANCE_SECONDS - elapsed
                        actionBar(player, Component.literal(
                            "§a⏳ Your turn §f${turnLeft}s §7• §bReserve ${bank}s"))
                    }
                } else {
                    // Not this actor's decision: commit the just-finished turn's overage (if any)
                    // to the reserve, then show a paused-clock line.
                    val elapsed = turnElapsed.remove(key)
                    if (elapsed != null) {
                        val overage = elapsed - TURN_ALLOWANCE_SECONDS
                        if (overage > 0) reserve[key] = (bank - overage).coerceAtLeast(0)
                    }
                    val bankNow = reserve[key] ?: bank
                    val waiting = if (opponentChoosing) "§7⏳ Waiting for opponent…" else "§7⏳ Resolving turn…"
                    actionBar(player, Component.literal("$waiting §7• §bReserve ${bankNow}s"))
                }
            }
        }
        // Prune state for actors/battles no longer live (battle ended, actor left, etc.).
        reserve.keys.retainAll(liveKeys)
        turnElapsed.keys.retainAll(liveKeys)
    }

    private fun actionBar(player: ServerPlayer?, msg: Component) {
        player?.displayClientMessage(msg, /* actionBar = */ true)
    }
}
