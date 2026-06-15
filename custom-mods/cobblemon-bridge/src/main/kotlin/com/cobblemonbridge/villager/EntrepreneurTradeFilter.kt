package com.cobblemonbridge.villager

import com.cobblemonbridge.CobblemonBridge
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.RandomSource
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.village.VillagerTradesEvent

/**
 * Strips the Legendary Monuments "Entrepreneur" villager's **Light Stone Shard** and
 * **Dark Stone Shard** trades (the Tier-4 wares that lead to Reshiram / Zekrom).
 *
 * The Entrepreneur's trades are registered in code by LegendaryMonuments
 * (`ModVillagers.registerTrades` → `TradeOfferHelper.registerVillagerOffers`), so they can't be
 * removed by a datapack — they apply to every Entrepreneur, on every level-up. NeoForge fires
 * [VillagerTradesEvent] (on the game bus) once per profession while assembling the per-level trade
 * lists, and the lists it hands us are mutable, so we filter them here.
 *
 * Scope is deliberately narrow (server-owner call): only the two stone shards are removed. The
 * other legendary-flavored wares are intentionally kept — the Silver Wing (Lugia) and Celestica
 * Flute (→ Azure Flute → Arceus) have **no other acquisition path**, and the four Treasure-of-Ruin
 * Seals are left as-is. Reshiram / Zekrom stay obtainable: the Ultra gacha crate sells the full
 * Light Stone / Dark Stone directly, so removing the shard trade is a balance nerf, not a soft-lock.
 *
 * Identification is by the offer's *result* item rather than a list index (robust to LM reordering
 * its pool): each [ItemListing] is invoked with a null entity (the LM trade factories ignore the
 * entity arg — verified in 7.8 bytecode) and a throwaway RNG, and removed iff it sells a blocked id.
 * Any listing that throws when evaluated is kept, so we never drop a trade we couldn't inspect.
 */
object EntrepreneurTradeFilter {

    private val ENTREPRENEUR = ResourceLocation.fromNamespaceAndPath("legendarymonuments", "entrepreneur")
    private val BLOCKED_RESULTS = setOf(
        ResourceLocation.fromNamespaceAndPath("legendarymonuments", "lightstone_shard"),
        ResourceLocation.fromNamespaceAndPath("legendarymonuments", "darkstone_shard"),
    )

    @SubscribeEvent
    fun onVillagerTrades(event: VillagerTradesEvent) {
        val profKey = BuiltInRegistries.VILLAGER_PROFESSION.getKey(event.type) ?: return
        if (profKey != ENTREPRENEUR) return

        val rng = RandomSource.create()
        var removed = 0
        for (listings in event.trades.values) {
            listings.removeIf { listing ->
                val resultId = try {
                    listing.getOffer(null, rng)?.result?.let { BuiltInRegistries.ITEM.getKey(it.item) }
                } catch (t: Throwable) {
                    null  // couldn't evaluate → keep the trade
                }
                (resultId != null && resultId in BLOCKED_RESULTS).also { if (it) removed++ }
            }
        }
        if (removed > 0) {
            CobblemonBridge.logger.info(
                "Removed {} Light/Dark Stone Shard trade(s) from the Entrepreneur villager pool", removed,
            )
        }
    }
}
