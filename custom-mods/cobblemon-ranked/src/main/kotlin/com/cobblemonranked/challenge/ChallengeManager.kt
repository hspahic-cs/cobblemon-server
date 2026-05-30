package com.cobblemonranked.challenge

import com.cobblemon.mod.common.Cobblemon
import com.cobblemonranked.CobblemonRanked
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.Style
import net.minecraft.server.level.ServerPlayer
import java.time.LocalDate
import java.util.UUID

data class PendingChallenge(
    val challengerUuid: UUID,
    val targetUuid: UUID,
    val isForced: Boolean,
    /**
     * Cobble-dollar wager pool side. `0` = no money on the line (legacy flow).
     * Positive = each player owes this amount on accept; the winner takes
     * `2 * wagerPerSide` minus any handling. Capped at challenge time to
     * min(requested, 50% of challenger, 25% of target) before storing.
     */
    val wagerPerSide: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)

class ChallengeManager {
    private val pendingChallenges: MutableMap<UUID, PendingChallenge> = mutableMapOf()
    private val CHALLENGE_TIMEOUT_MS = 60_000L

    /**
     * Attempt to challenge a player. Returns null on success, or an error message.
     *
     * @param requestedWager Cobble dollars the challenger wants to wager per side. Capped
     *   internally at min(requested, 50% of challenger's balance, 25% of target's balance) —
     *   the final stored amount is the lower of those three. Pass `0` for a no-money
     *   challenge (legacy behaviour).
     */
    fun challenge(challenger: ServerPlayer, target: ServerPlayer, requestedWager: Int = 0): String? {
        if (challenger.uuid == target.uuid) {
            return "You can't challenge yourself."
        }

        if (isPlayerInBattle(target)) {
            return "${target.name.string} is already in a battle."
        }

        if (pendingChallenges.containsKey(challenger.uuid)) {
            return "You already have a pending challenge. Wait for it to expire or be answered."
        }

        val store = CobblemonRanked.eloStore
        val challengerData = store.getOrCreate(challenger.uuid, challenger.name.string)
        val targetData = store.getOrCreate(target.uuid, target.name.string)

        val effectiveWager = computeEffectiveWager(challenger.uuid, target.uuid, requestedWager)
        if (requestedWager > 0 && effectiveWager <= 0) {
            return "Wager rejected — one of you doesn't have enough to cover it (need at least 1 cobble after the caps)."
        }

        val challengerIsLower = challengerData.elo < targetData.elo
        val today = LocalDate.now().toString()

        // Force-accept only applies to non-wager challenges. Money on the line always
        // requires the target to explicitly /accept (per the design — "Wager challenges are
        // never automatically accepted, must be manually accepted by the challengee").
        val isForced = if (effectiveWager == 0 && challengerIsLower) {
            val lastForce = challengerData.forceLog[target.uuid.toString()]
            lastForce != today
        } else {
            false
        }

        val challenge = PendingChallenge(
            challengerUuid = challenger.uuid,
            targetUuid = target.uuid,
            isForced = isForced,
            wagerPerSide = effectiveWager,
        )
        pendingChallenges[target.uuid] = challenge

        if (isForced) {
            challengerData.forceLog[target.uuid.toString()] = today
            CobblemonRanked.eloStore.save()

            target.sendSystemMessage(
                Component.literal("[Ranked] ${challenger.name.string} (${challengerData.elo}) has forced you into a ranked match! Preparing team selection...")
            )
            challenger.sendSystemMessage(
                Component.literal("[Ranked] Force challenge sent to ${target.name.string} (${targetData.elo}). Preparing team selection...")
            )
            return null
        } else {
            val wagerNote = if (effectiveWager > 0) " §6Wager: §e\$${effectiveWager}§7 per side." else ""
            // Clickable accept (RUN) + decline (RUN) for the target.
            val acceptClick = Component.literal("§a[Accept]")
                .setStyle(Style.EMPTY
                    .withClickEvent(ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ranked accept"))
                    .withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("§aRun /ranked accept"))))
            val declineClick = Component.literal("§c[Decline]")
                .setStyle(Style.EMPTY
                    .withClickEvent(ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ranked decline"))
                    .withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("§cRun /ranked decline"))))
            val targetMsg = Component.literal(
                "§e[Ranked] §f${challenger.name.string} §7(ELO §e${challengerData.elo}§7) challenges you.$wagerNote §7 "
            ).append(acceptClick).append(Component.literal("§7 · ")).append(declineClick)
                .append(Component.literal("§7  (60s)"))
            target.sendSystemMessage(targetMsg)
            val notice = if (effectiveWager == requestedWager || requestedWager == 0)
                "Challenge sent to ${target.name.string} (${targetData.elo})."
            else
                "Challenge sent to ${target.name.string} (${targetData.elo}). Requested wager $requestedWager — capped to $effectiveWager."
            challenger.sendSystemMessage(Component.literal("[Ranked] $notice Waiting for response..."))
            return null
        }
    }

    /**
     * Cap a requested wager to what both parties can afford.
     *   challenger: at most 50% of their current balance
     *   target:     at most 25% of their current balance (because the challenger is the one
     *               imposing the bet — the target's exposure shouldn't be as steep)
     *   plus the lower of those AND the requested amount
     */
    fun computeEffectiveWager(challengerUuid: UUID, targetUuid: UUID, requested: Int): Int {
        if (requested <= 0) return 0
        val challengerCap = com.cobblemonranked.economy.EconomyBridge.getBalance(challengerUuid) / 2
        val targetCap = com.cobblemonranked.economy.EconomyBridge.getBalance(targetUuid) / 4
        return minOf(requested, challengerCap, targetCap).coerceAtLeast(0)
    }

    fun accept(player: ServerPlayer): PendingChallenge? {
        val challenge = pendingChallenges.remove(player.uuid) ?: return null
        if (System.currentTimeMillis() - challenge.timestamp > CHALLENGE_TIMEOUT_MS) {
            return null
        }
        return challenge
    }

    fun decline(player: ServerPlayer): Boolean {
        val challenge = pendingChallenges.remove(player.uuid) ?: return false
        val server = player.server
        val challenger = server.playerList.getPlayer(challenge.challengerUuid)
        challenger?.sendSystemMessage(
            Component.literal("[Ranked] ${player.name.string} declined your challenge.")
        )
        return true
    }

    fun getPendingForced(playerUuid: UUID): PendingChallenge? {
        val challenge = pendingChallenges[playerUuid]
        if (challenge != null && challenge.isForced) {
            pendingChallenges.remove(playerUuid)
            return challenge
        }
        return null
    }

    fun cleanupExpired() {
        val now = System.currentTimeMillis()
        val expired = pendingChallenges.filter { now - it.value.timestamp > CHALLENGE_TIMEOUT_MS }
        expired.forEach { (uuid, _) -> pendingChallenges.remove(uuid) }
    }

    private fun isPlayerInBattle(player: ServerPlayer): Boolean {
        return try {
            Cobblemon.battleRegistry.getBattleByParticipatingPlayer(player) != null
        } catch (e: Exception) {
            false
        }
    }
}
