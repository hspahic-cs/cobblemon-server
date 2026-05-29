package com.cobblemonmarket.gui

import com.cobblemonmarket.CobblemonMarket
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResult
import net.neoforged.bus.api.EventPriority
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent

/**
 * Right-click handler for any market vendor NPC. Two tag schemes are recognised:
 *
 *  - **Default vendor**: tag `cobblemon_bridge.market_vendor` (no suffix). Spawned by the
 *    legacy `/function server:market/spawn_npc` or `/market admin spawn` (no arg).
 *    [MarketMenu] opens with vendorTag="", showing every entry whose `vendorTag` is empty.
 *  - **Scoped vendor**: tag `cobblemon_bridge.market_vendor.<tag>` — e.g.
 *    `cobblemon_bridge.market_vendor.tm_fire`. The menu opens scoped to that tag and only
 *    shows items whose `vendorTag` matches.
 *
 * The first matching tag wins. Priority HIGHEST so we run before Cobblemon's NPC
 * interaction handler.
 */
object MarketNpcHook {

    private const val VENDOR_TAG_PREFIX = "cobblemon_bridge.market_vendor"

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    fun onEntityInteract(event: PlayerInteractEvent.EntityInteract) {
        if (event.level.isClientSide) return
        val player = event.entity as? ServerPlayer ?: return
        val vendorTag = resolveVendorTag(event.target.tags) ?: return
        event.isCanceled = true
        event.cancellationResult = InteractionResult.SUCCESS
        try {
            MarketMenu.open(player, vendorTag)
        } catch (t: Throwable) {
            CobblemonMarket.logger.error("Failed to open MarketMenu for ${player.gameProfile.name}", t)
        }
    }

    /**
     * Returns the vendor scope id for this villager, or null if it isn't a market vendor.
     *   `cobblemon_bridge.market_vendor`         → ""        (default)
     *   `cobblemon_bridge.market_vendor.tm_fire` → "tm_fire" (scoped)
     */
    private fun resolveVendorTag(tags: Collection<String>): String? {
        if (VENDOR_TAG_PREFIX in tags) return ""
        val scoped = tags.firstOrNull { it.startsWith("$VENDOR_TAG_PREFIX.") } ?: return null
        return scoped.removePrefix("$VENDOR_TAG_PREFIX.")
    }
}
