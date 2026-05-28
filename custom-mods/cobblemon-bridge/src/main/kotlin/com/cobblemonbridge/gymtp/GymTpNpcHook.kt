package com.cobblemonbridge.gymtp

import com.cobblemonbridge.CobblemonBridge
import com.cobblemonbridge.tags.BridgeTags
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResult
import net.neoforged.bus.api.EventPriority
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent

/**
 * Right-click handler for the gym-TP villager. Mirrors `cobblemon-market`'s
 * `MarketNpcHook` — `EntityInteract` at `HIGHEST` priority, cancel default, open menu.
 * Tag check uses [BridgeTags.GYM_TP_NPC]; the villager is stamped by `/gymtp spawn`.
 */
object GymTpNpcHook {

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    fun onEntityInteract(event: PlayerInteractEvent.EntityInteract) {
        if (event.level.isClientSide) return
        val player = event.entity as? ServerPlayer ?: return
        if (!BridgeTags.isGymTpNpc(event.target.tags)) return
        event.isCanceled = true
        event.cancellationResult = InteractionResult.SUCCESS
        try {
            GymTpMenu.open(player)
        } catch (t: Throwable) {
            CobblemonBridge.logger.error("Failed to open GymTpMenu for ${player.gameProfile.name}", t)
        }
    }
}
