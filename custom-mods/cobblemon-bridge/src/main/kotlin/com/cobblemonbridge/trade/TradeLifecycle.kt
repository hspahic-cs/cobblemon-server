package com.cobblemonbridge.trade

import net.minecraft.server.level.ServerPlayer
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent

/**
 * Server-side subscriptions that keep [TradeManager] in sync with player lifecycle.
 * Currently just one: a logout listener that funnels disconnects into
 * [TradeManager.handleDisconnect] so any in-flight trade is refunded + closed.
 *
 * Menu-close (closing the chest window) is handled by [TradeMenu.Impl.removed], not here.
 */
object TradeLifecycle {

    @SubscribeEvent
    fun onPlayerLogOut(event: PlayerEvent.PlayerLoggedOutEvent) {
        val player = event.entity as? ServerPlayer ?: return
        TradeManager.handleDisconnect(player)
    }
}
