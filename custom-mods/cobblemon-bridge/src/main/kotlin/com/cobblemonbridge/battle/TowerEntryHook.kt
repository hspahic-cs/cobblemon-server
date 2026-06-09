package com.cobblemonbridge.battle

import com.cobblemonbridge.tags.BridgeTags
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResult
import net.neoforged.bus.api.EventPriority
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent

/**
 * Right-click handler for the battle-tower entry NPC — a villager tagged [BridgeTags.TOWER_ENTRY]
 * and placed by `/tower setentry`. Mirrors [com.cobblemonbridge.gymtp.GymTpNpcHook]: EntityInteract
 * at HIGHEST priority, cancel the default interaction, then hand off to
 * [TowerGauntletHook.startFromEntry], which gate-checks `beat_gym_10`, warps the player to floor 1,
 * and arms the run. The floor-1 leader is standing there; the player clicks them to start battle 1.
 */
object TowerEntryHook {

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    fun onEntityInteract(event: PlayerInteractEvent.EntityInteract) {
        if (event.level.isClientSide) return
        val player = event.entity as? ServerPlayer ?: return
        if (!BridgeTags.isTowerEntry(event.target.tags)) return
        event.isCanceled = true
        event.cancellationResult = InteractionResult.SUCCESS
        TowerGauntletHook.startFromEntry(player)
    }
}
