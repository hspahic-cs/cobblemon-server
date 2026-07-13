package com.cobblemonmarket.bp

import com.cobblemonmarket.CobblemonMarket
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResult
import net.neoforged.bus.api.EventPriority
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent

object BpShopNpcHook {

    private const val BP_SHOP_TAG = "cobblemon_bridge.market_vendor.bp_shop"

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    fun onEntityInteract(event: PlayerInteractEvent.EntityInteract) {
        if (event.level.isClientSide) return
        val player = event.entity as? ServerPlayer ?: return
        if (BP_SHOP_TAG !in event.target.tags) return

        event.isCanceled = true
        event.cancellationResult = InteractionResult.SUCCESS
        try {
            BpShopMenu.openBpShop(player)
        } catch (t: Throwable) {
            CobblemonMarket.logger.error("Failed to open BP shop menu for ${player.gameProfile.name}", t)
        }
    }
}
