package com.cobblemonbridge.trade

import com.cobblemon.mod.common.Cobblemon
import com.cobblemonbridge.CobblemonBridge
import com.cobblemonbridge.economy.EconomyBridge
import com.cobblemonbridge.quests.LevelCap
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.SimpleContainer
import net.minecraft.world.entity.item.ItemEntity
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Server-side state machine for player-to-player trades.
 *
 * Lifecycle:
 *  1. `/trade <player>` → [request] stores a pending request keyed by target UUID.
 *  2. `/trade accept` → [accept] consumes the request, creates a [TradeSession], opens the
 *     shared [TradeMenu] for both players.
 *  3. Players click `+Pokémon` / `+Money` / drag items into their slots / click their
 *     Confirm slot. Any offer change un-confirms both sides via [TradeSession.unconfirmBoth].
 *  4. When [TradeSession.bothConfirmed] flips true, [execute] runs: validate level caps,
 *     money, party stability — then atomically transfer pokemon (overflow to PC), items
 *     (overflow drops at receiver's feet), and money.
 *  5. [cancel] / [handleDisconnect] / [handleMenuClose] all funnel through [refundAndClose],
 *     which returns escrowed items to their owners, drops the session, and closes menus.
 *
 * Money is NOT escrowed at +money time — we validate at execute time that the sender still
 * has it (cheap, avoids the "rollback if trade cancels after withdraw" branch). Items ARE
 * escrowed (they sit in the shared container). Pokemon stay in the sender's party but are
 * earmarked by UUID; we re-validate at execute that each is still there.
 *
 * Pokemon level-cap check uses [LevelCap.forPlayer] for the RECEIVER's cap, applied to each
 * incoming Pokémon — block-the-trade per the 0.7.10 spec (no PC routing for over-cap).
 */
object TradeManager {

    private const val REQUEST_TTL_MS: Long = 60_000L
    private const val CONTAINER_SIZE: Int = 54  // 6×9 chest, see TradeMenu for slot layout

    /** Active sessions, indexed by EACH participant's UUID (both keys point to the same
     *  session) so a single lookup tells us if a given player is mid-trade. */
    private val sessions: MutableMap<UUID, TradeSession> = ConcurrentHashMap()

    /** Pending requests keyed by the TARGET's UUID — at most one request per target at a
     *  time; a new request overwrites the prior one. */
    private val pending: MutableMap<UUID, PendingRequest> = ConcurrentHashMap()

    private data class PendingRequest(val fromUuid: UUID, val expiresAtMs: Long)

    // ─── lifecycle: request / accept / decline ──────────────────────────────────

    /** Sends a trade request from [from] to [target]. Both must be online + not already in
     *  a trade. Sends chat to target with click-to-accept / click-to-decline buttons. */
    fun request(from: ServerPlayer, target: ServerPlayer): Boolean {
        if (from.uuid == target.uuid) {
            from.sendSystemMessage(Component.literal("§c[Trade] You can't trade with yourself."))
            return false
        }
        if (sessionFor(from) != null) {
            from.sendSystemMessage(Component.literal("§c[Trade] You're already in a trade. §f/trade cancel§c first."))
            return false
        }
        if (sessionFor(target) != null) {
            from.sendSystemMessage(Component.literal("§c[Trade] ${target.gameProfile.name} is already in a trade."))
            return false
        }
        pending[target.uuid] = PendingRequest(from.uuid, System.currentTimeMillis() + REQUEST_TTL_MS)
        from.sendSystemMessage(Component.literal(
            "§e[Trade] Request sent to §f${target.gameProfile.name}§e. Expires in 60s."
        ))
        target.sendSystemMessage(Component.literal(
            "§e[Trade] §f${from.gameProfile.name}§e wants to trade. §a/trade accept §7or §c/trade decline"
        ))
        return true
    }

    fun accept(target: ServerPlayer): Boolean {
        val req = pending.remove(target.uuid) ?: run {
            target.sendSystemMessage(Component.literal("§c[Trade] No pending trade request."))
            return false
        }
        if (System.currentTimeMillis() > req.expiresAtMs) {
            target.sendSystemMessage(Component.literal("§c[Trade] That request expired."))
            return false
        }
        val from = target.server.playerList.getPlayer(req.fromUuid) ?: run {
            target.sendSystemMessage(Component.literal("§c[Trade] The other player is offline."))
            return false
        }
        if (sessionFor(target) != null || sessionFor(from) != null) {
            target.sendSystemMessage(Component.literal("§c[Trade] One of you is already in a trade."))
            return false
        }

        val session = TradeSession(
            sessionId = UUID.randomUUID(),
            p1Uuid = from.uuid,
            p2Uuid = target.uuid,
            p1Name = from.gameProfile.name,
            p2Name = target.gameProfile.name,
            container = SimpleContainer(CONTAINER_SIZE),
        )
        sessions[from.uuid] = session
        sessions[target.uuid] = session

        TradeMenu.openFor(from, target, session)
        from.sendSystemMessage(Component.literal("§a[Trade] ${target.gameProfile.name} accepted. Trade window opening…"))
        target.sendSystemMessage(Component.literal("§a[Trade] Trade window opening with ${from.gameProfile.name}…"))
        return true
    }

    fun decline(target: ServerPlayer): Boolean {
        val req = pending.remove(target.uuid) ?: run {
            target.sendSystemMessage(Component.literal("§c[Trade] No pending trade request to decline."))
            return false
        }
        val from = target.server.playerList.getPlayer(req.fromUuid)
        target.sendSystemMessage(Component.literal("§7[Trade] Declined."))
        from?.sendSystemMessage(Component.literal(
            "§7[Trade] §f${target.gameProfile.name}§7 declined your trade request."))
        return true
    }

    // ─── lookups ────────────────────────────────────────────────────────────────

    fun sessionFor(player: ServerPlayer): TradeSession? = sessions[player.uuid]
    fun sessionForUuid(uuid: UUID): TradeSession? = sessions[uuid]

    // ─── offer mutations ────────────────────────────────────────────────────────

    /** Stage the Pokémon at [partyIndex] in [player]'s party into their offer. Returns false
     *  if the slot is empty or already staged. Un-confirms both sides on success. */
    fun stagePokemon(player: ServerPlayer, partyIndex: Int): Boolean {
        val session = sessionFor(player) ?: return false
        val offer = session.offerOf(player.uuid) ?: return false
        val party = Cobblemon.storage.getParty(player)
        val mon = party.get(partyIndex) ?: run {
            player.sendSystemMessage(Component.literal("§c[Trade] Empty party slot."))
            return false
        }
        if (offer.pokemonUuids().contains(mon.uuid)) {
            player.sendSystemMessage(Component.literal("§7[Trade] That Pokémon is already in your offer."))
            return false
        }
        offer.pokemon.add(mon)
        session.unconfirmBoth()
        TradeMenu.refresh(session)
        return true
    }

    fun unstagePokemon(player: ServerPlayer, offerIndex: Int): Boolean {
        val session = sessionFor(player) ?: return false
        val offer = session.offerOf(player.uuid) ?: return false
        if (offerIndex !in offer.pokemon.indices) return false
        offer.pokemon.removeAt(offerIndex)
        session.unconfirmBoth()
        TradeMenu.refresh(session)
        return true
    }

    /** Set [player]'s money offer to [amount]. Clamps to [0, balance]. Un-confirms both. */
    fun setMoney(player: ServerPlayer, amount: Int): Boolean {
        val session = sessionFor(player) ?: return false
        val offer = session.offerOf(player.uuid) ?: return false
        val clamped = amount.coerceAtLeast(0)
            .coerceAtMost(EconomyBridge.getBalance(player.uuid))
        if (clamped == offer.money) return true
        offer.money = clamped
        session.unconfirmBoth()
        TradeMenu.refresh(session)
        return true
    }

    /** Called by TradeMenu when an item-slot click changes the container's content. Just
     *  un-confirms both sides and refreshes both views. */
    fun onItemSlotChanged(session: TradeSession) {
        session.unconfirmBoth()
        TradeMenu.refresh(session)
    }

    /** Toggle this player's confirm flag. If both sides end up confirmed, execute. */
    fun toggleConfirm(player: ServerPlayer): Boolean {
        val session = sessionFor(player) ?: return false
        val offer = session.offerOf(player.uuid) ?: return false
        offer.confirmed = !offer.confirmed
        if (session.bothConfirmed()) {
            execute(session)
        } else {
            TradeMenu.refresh(session)
        }
        return true
    }

    // ─── cancel paths ───────────────────────────────────────────────────────────

    fun cancel(player: ServerPlayer): Boolean {
        val session = sessionFor(player) ?: return false
        refundAndClose(session, reason = "${player.gameProfile.name} cancelled")
        return true
    }

    fun handleDisconnect(player: ServerPlayer) {
        val session = sessionFor(player) ?: return
        refundAndClose(session, reason = "${player.gameProfile.name} disconnected")
    }

    fun handleMenuClose(player: ServerPlayer) {
        val session = sessionFor(player) ?: return
        // Menu close = cancel. Refund + drop session. The other player will see their menu
        // close too via TradeMenu.closeFor.
        refundAndClose(session, reason = "${player.gameProfile.name} closed the trade window")
    }

    private fun refundAndClose(session: TradeSession, reason: String) {
        val server = session.let {
            // We need a server reference to look up players. Just pull from either online player.
            sessions[it.p1Uuid] ?: sessions[it.p2Uuid]
            null  // Unused — we look up via either player below
        }
        sessions.remove(session.p1Uuid)
        sessions.remove(session.p2Uuid)

        val p1 = TradeMenu.viewerOf(session, session.p1Uuid)
        val p2 = TradeMenu.viewerOf(session, session.p2Uuid)

        // Refund items: each item slot is owned by exactly one player; give back to that owner.
        for (slot in TradeMenu.itemSlotsFor(session.p1Uuid)) {
            val stack = session.container.removeItemNoUpdate(slot)
            if (!stack.isEmpty) returnItem(p1, stack)
        }
        for (slot in TradeMenu.itemSlotsFor(session.p2Uuid)) {
            val stack = session.container.removeItemNoUpdate(slot)
            if (!stack.isEmpty) returnItem(p2, stack)
        }

        TradeMenu.closeFor(session)

        val notice = Component.literal("§7[Trade] Cancelled — §f$reason§7. Items returned.")
        p1?.sendSystemMessage(notice)
        p2?.sendSystemMessage(notice)
    }

    /** Give [stack] back to [player], or drop at their feet if they're offline / inventory full. */
    private fun returnItem(player: ServerPlayer?, stack: net.minecraft.world.item.ItemStack) {
        if (player == null) return  // edge case: refund on disconnect with no online refundee
        val added = player.inventory.add(stack)
        if (!added || !stack.isEmpty) {
            // Drop the leftover (or all of it) at the player's feet.
            val drop = ItemEntity(player.level(), player.x, player.y, player.z, stack.copy())
            player.level().addFreshEntity(drop)
        }
    }

    // ─── execute ────────────────────────────────────────────────────────────────

    private fun execute(session: TradeSession) {
        val server = TradeMenu.viewerOf(session, session.p1Uuid)?.server
            ?: TradeMenu.viewerOf(session, session.p2Uuid)?.server
            ?: return
        val p1 = server.playerList.getPlayer(session.p1Uuid)
        val p2 = server.playerList.getPlayer(session.p2Uuid)
        if (p1 == null || p2 == null) {
            refundAndClose(session, reason = "a participant went offline")
            return
        }

        // 1. Validate level caps on each side (RECEIVER's cap vs incoming Pokémon levels).
        val capP1 = LevelCap.forPlayer(p1)
        val capP2 = LevelCap.forPlayer(p2)
        val violations = mutableListOf<String>()
        if (!LevelCap.isUncapped(capP1)) {
            for (mon in session.offer2.pokemon) if (mon.level > capP1) {
                violations += "${p1.gameProfile.name} (cap $capP1) can't accept ${mon.species.name} L${mon.level}"
            }
        }
        if (!LevelCap.isUncapped(capP2)) {
            for (mon in session.offer1.pokemon) if (mon.level > capP2) {
                violations += "${p2.gameProfile.name} (cap $capP2) can't accept ${mon.species.name} L${mon.level}"
            }
        }
        if (violations.isNotEmpty()) {
            val msg = "§c[Trade] Blocked — level cap violation:\n  §7" + violations.joinToString("\n  §7")
            p1.sendSystemMessage(Component.literal(msg))
            p2.sendSystemMessage(Component.literal(msg))
            session.unconfirmBoth()
            TradeMenu.refresh(session)
            return
        }

        // 2. Validate money — sender still has what they offered.
        if (EconomyBridge.getBalance(p1.uuid) < session.offer1.money) {
            unconfirmedNotice(session, p1, "${p1.gameProfile.name} can no longer cover \$${session.offer1.money}")
            return
        }
        if (EconomyBridge.getBalance(p2.uuid) < session.offer2.money) {
            unconfirmedNotice(session, p2, "${p2.gameProfile.name} can no longer cover \$${session.offer2.money}")
            return
        }

        // 3. Validate party stability — each staged Pokémon must still be in the sender's party.
        val party1 = Cobblemon.storage.getParty(p1)
        val party2 = Cobblemon.storage.getParty(p2)
        val party1Uuids = (0 until party1.size()).mapNotNull { party1.get(it)?.uuid }.toSet()
        val party2Uuids = (0 until party2.size()).mapNotNull { party2.get(it)?.uuid }.toSet()
        if (!session.offer1.pokemonUuids().all { it in party1Uuids }) {
            unconfirmedNotice(session, p1, "${p1.gameProfile.name}'s party changed — re-stage")
            return
        }
        if (!session.offer2.pokemonUuids().all { it in party2Uuids }) {
            unconfirmedNotice(session, p2, "${p2.gameProfile.name}'s party changed — re-stage")
            return
        }

        // 4. Atomic phase — past this point we mutate state.
        // 4a. Pokemon: remove from sender, add to receiver (overflow to PC).
        val incomingToP1 = session.offer2.pokemon.toList()
        val incomingToP2 = session.offer1.pokemon.toList()
        for (mon in incomingToP2) party1.remove(mon)
        for (mon in incomingToP1) party2.remove(mon)
        val pcP1 = Cobblemon.storage.getPC(p1)
        val pcP2 = Cobblemon.storage.getPC(p2)
        for (mon in incomingToP1) {
            if (!party1.add(mon)) pcP1.add(mon)
        }
        for (mon in incomingToP2) {
            if (!party2.add(mon)) pcP2.add(mon)
        }

        // 4b. Items: shovel each side's item slots into the other player's inventory.
        for (slot in TradeMenu.itemSlotsFor(p1.uuid)) {
            val stack = session.container.removeItemNoUpdate(slot)
            if (!stack.isEmpty) returnItem(p2, stack)  // received by p2
        }
        for (slot in TradeMenu.itemSlotsFor(p2.uuid)) {
            val stack = session.container.removeItemNoUpdate(slot)
            if (!stack.isEmpty) returnItem(p1, stack)  // received by p1
        }

        // 4c. Money: withdraw from each side, deposit to the other.
        if (session.offer1.money > 0) {
            EconomyBridge.withdraw(p1.uuid, session.offer1.money)
            EconomyBridge.deposit(p2.uuid, session.offer1.money)
        }
        if (session.offer2.money > 0) {
            EconomyBridge.withdraw(p2.uuid, session.offer2.money)
            EconomyBridge.deposit(p1.uuid, session.offer2.money)
        }

        // 5. Tear down session + close menus + chat summary.
        sessions.remove(session.p1Uuid)
        sessions.remove(session.p2Uuid)
        TradeMenu.closeFor(session)
        val summary = "§a[Trade] Complete: §f${session.p1Name}§a ↔ §f${session.p2Name}"
        p1.sendSystemMessage(Component.literal(summary))
        p2.sendSystemMessage(Component.literal(summary))
        CobblemonBridge.logger.info(
            "trade complete: {} <-> {} (mon {}/{}, $ {}/{})",
            session.p1Name, session.p2Name,
            session.offer1.pokemon.size, session.offer2.pokemon.size,
            session.offer1.money, session.offer2.money,
        )
    }

    private fun unconfirmedNotice(session: TradeSession, who: ServerPlayer, reason: String) {
        val msg = "§c[Trade] Blocked — §f$reason§c. Adjust and re-confirm."
        TradeMenu.viewerOf(session, session.p1Uuid)?.sendSystemMessage(Component.literal(msg))
        TradeMenu.viewerOf(session, session.p2Uuid)?.sendSystemMessage(Component.literal(msg))
        session.unconfirmBoth()
        TradeMenu.refresh(session)
    }
}
