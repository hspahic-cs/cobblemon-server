package com.cobblemonauction.gui

import com.cobblemonauction.CobblemonAuction
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResult
import net.neoforged.bus.api.EventPriority
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent

/**
 * Right-click handler for the Auctioneer NPC. Any entity tagged `cobblemon_auction.auctioneer`
 * (mirroring cobblemon-market's `cobblemon_bridge.market_vendor` scheme) opens the browse GUI.
 * Priority HIGHEST so we run before Cobblemon's own NPC interaction handler and cancel it.
 */
object AuctionNpcHook {

    private const val AUCTIONEER_TAG = "cobblemon_auction.auctioneer"

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    fun onEntityInteract(event: PlayerInteractEvent.EntityInteract) {
        if (event.level.isClientSide) return
        val player = event.entity as? ServerPlayer ?: return
        if (AUCTIONEER_TAG !in event.target.tags) return
        event.isCanceled = true
        event.cancellationResult = InteractionResult.SUCCESS
        try {
            BrowseMenu.open(player)
        } catch (t: Throwable) {
            CobblemonAuction.logger.error("Failed to open auction browser for ${player.gameProfile.name}", t)
        }
    }
}
