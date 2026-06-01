package com.cobblemonmarket.economy

import com.cobblemonmarket.CobblemonMarket
import com.cobblemonmarket.config.effectiveBuyClamp
import com.cobblemonmarket.config.effectiveBuyStockImpact
import com.cobblemonmarket.config.effectiveMinBuyPrice
import com.cobblemonmarket.config.effectiveSellClamp
import com.cobblemonmarket.config.effectiveSellStockImpact
import com.cobblemonmarket.pricing.PricingEngine
import kotlin.math.ceil
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import kotlin.math.floor

/**
 * Outcome of a trade attempt. Carries enough info for the caller (chat command, admin
 * trade) to render an appropriate message without re-running any pricing math.
 *
 * `newStock` is the resulting stock after the trade — useful for the success message
 * so players can see the inventory level move. Stored as Double because passive restock
 * accrues fractionally between trades.
 */
sealed class TradeResult {
    data class Success(val totalPrice: Int, val newStock: Double) : TradeResult()
    data class InsufficientBalance(val have: Int, val need: Int) : TradeResult()
    data class InsufficientItems(val itemId: String, val have: Int, val need: Int) : TradeResult()
    data class OutOfStock(val itemId: String, val available: Int, val requested: Int) : TradeResult()
    data class MarketSaturated(val itemId: String, val currentStock: Int, val maxStock: Int) : TradeResult()
    object NoInventorySpace : TradeResult()
    data class UnknownItem(val itemId: String) : TradeResult()
    object EconomyFailed : TradeResult()
}

/**
 * Server-side market transaction logic. Single source of truth shared between the
 * `/market buy|sell` and `/market admin trade` commands.
 */
object TradeOps {

    fun buy(player: ServerPlayer, itemId: String, qty: Int): TradeResult {
        if (qty <= 0) return TradeResult.UnknownItem(itemId)
        val entry = CobblemonMarket.items[itemId] ?: return TradeResult.UnknownItem(itemId)
        val state = CobblemonMarket.marketStore.getOrCreate(itemId)

        // Stock must cover the *virtual* drain (qty × buyStockImpact), not just the items
        // physically being purchased. With impact=3, buying 1 drains 3 stock — stock=2 blocks.
        val stockNeeded = ceil(qty * entry.effectiveBuyStockImpact).toInt()
        val available = floor(state.stock).toInt()
        if (available < stockNeeded) return TradeResult.OutOfStock(itemId, available, stockNeeded)

        val result = PricingEngine.simulateBatchBuy(
            entry.baseBuyPrice, entry.baseStock, entry.elasticity,
            state.stock, qty,
            clamp = entry.effectiveBuyClamp,
            stockImpact = entry.effectiveBuyStockImpact,
            minBuyPrice = entry.effectiveMinBuyPrice,
        )
        val totalCost = result.totalPrice

        val balance = EconomyBridge.getBalance(player.uuid)
        if (balance < totalCost) return TradeResult.InsufficientBalance(balance, totalCost)
        if (!hasInventorySpace(player, qty)) return TradeResult.NoInventorySpace
        if (!EconomyBridge.withdraw(player.uuid, totalCost)) return TradeResult.EconomyFailed

        val priceBefore = PricingEngine.buyPrice(
            entry.baseBuyPrice, state.stock, entry.baseStock, entry.elasticity,
            entry.effectiveBuyClamp, entry.effectiveMinBuyPrice,
        )
        state.stock = result.finalStock
        val priceAfter = PricingEngine.buyPrice(
            entry.baseBuyPrice, state.stock, entry.baseStock, entry.elasticity,
            entry.effectiveBuyClamp, entry.effectiveMinBuyPrice,
        )

        val item: Item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId))
        player.inventory.add(ItemStack(item, qty))
        val avgPrice = (totalCost + qty / 2) / qty
        CobblemonMarket.marketStore.recordPriceTick(
            itemId = itemId, type = "buy",
            playerUuid = player.uuid.toString(), playerName = player.name.string,
            pricePerUnit = avgPrice, priceBefore = priceBefore, priceAfter = priceAfter,
            quantity = qty,
        )
        CobblemonMarket.marketStore.save()

        return TradeResult.Success(totalCost, state.stock)
    }

    /**
     * Buys [qty] units of [itemId] from the market without delivering them to the player's
     * inventory. Money is withdrawn and stock is decremented exactly as in [buy]; the items are
     * assumed to be consumed by the caller (e.g., cobblemon-carrots feeding them straight into
     * a heal). Skips inventory-space checks since nothing is added.
     *
     * Records a `"buy"` price-history tick so the market's analytics treat this identically to
     * a normal purchase — the average price field is the per-unit average paid.
     */
    fun buyForConsumption(player: ServerPlayer, itemId: String, qty: Int): TradeResult {
        if (qty <= 0) return TradeResult.UnknownItem(itemId)
        val entry = CobblemonMarket.items[itemId] ?: return TradeResult.UnknownItem(itemId)
        val state = CobblemonMarket.marketStore.getOrCreate(itemId)

        val stockNeeded = ceil(qty * entry.effectiveBuyStockImpact).toInt()
        val available = floor(state.stock).toInt()
        if (available < stockNeeded) return TradeResult.OutOfStock(itemId, available, stockNeeded)

        val result = PricingEngine.simulateBatchBuy(
            entry.baseBuyPrice, entry.baseStock, entry.elasticity,
            state.stock, qty,
            clamp = entry.effectiveBuyClamp,
            stockImpact = entry.effectiveBuyStockImpact,
            minBuyPrice = entry.effectiveMinBuyPrice,
        )
        val totalCost = result.totalPrice

        val balance = EconomyBridge.getBalance(player.uuid)
        if (balance < totalCost) return TradeResult.InsufficientBalance(balance, totalCost)
        if (!EconomyBridge.withdraw(player.uuid, totalCost)) return TradeResult.EconomyFailed

        val priceBefore = PricingEngine.buyPrice(
            entry.baseBuyPrice, state.stock, entry.baseStock, entry.elasticity,
            entry.effectiveBuyClamp, entry.effectiveMinBuyPrice,
        )
        state.stock = result.finalStock
        val priceAfter = PricingEngine.buyPrice(
            entry.baseBuyPrice, state.stock, entry.baseStock, entry.elasticity,
            entry.effectiveBuyClamp, entry.effectiveMinBuyPrice,
        )

        val avgPrice = (totalCost + qty / 2) / qty
        CobblemonMarket.marketStore.recordPriceTick(
            itemId = itemId, type = "buy",
            playerUuid = player.uuid.toString(), playerName = player.name.string,
            pricePerUnit = avgPrice, priceBefore = priceBefore, priceAfter = priceAfter,
            quantity = qty,
        )
        CobblemonMarket.marketStore.save()

        return TradeResult.Success(totalCost, state.stock)
    }

    fun sell(player: ServerPlayer, itemId: String, qty: Int): TradeResult {
        if (qty <= 0) return TradeResult.UnknownItem(itemId)
        val entry = CobblemonMarket.items[itemId] ?: return TradeResult.UnknownItem(itemId)
        val state = CobblemonMarket.marketStore.getOrCreate(itemId)

        val itemRef: Item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId))
        val have = countItems(player, itemRef)
        if (have < qty) return TradeResult.InsufficientItems(itemId, have, qty)

        // Cap on how saturated the market can get — past this point the shop refuses
        // to absorb more rather than paying near-zero per unit. Sells add stock at
        // entry.effectiveSellStockImpact per unit (default 1.0).
        val maxStock = (entry.baseStock * entry.maxStockMultiplier).toInt()
        val stockAdded = qty * entry.effectiveSellStockImpact
        if (state.stock + stockAdded > maxStock) {
            return TradeResult.MarketSaturated(itemId, state.stock.toInt(), maxStock)
        }

        val result = PricingEngine.simulateBatchSell(
            entry.baseSellPrice, entry.baseStock, entry.elasticity,
            state.stock, qty,
            clamp = entry.effectiveSellClamp,
            stockImpact = entry.effectiveSellStockImpact,
        )
        val totalProceeds = result.totalPrice

        val priceBefore = PricingEngine.sellPrice(
            entry.baseSellPrice, state.stock, entry.baseStock, entry.elasticity,
            entry.effectiveSellClamp,
        )
        removeItems(player, itemRef, qty)
        state.stock = result.finalStock
        val priceAfter = PricingEngine.sellPrice(
            entry.baseSellPrice, state.stock, entry.baseStock, entry.elasticity,
            entry.effectiveSellClamp,
        )

        EconomyBridge.deposit(player.uuid, totalProceeds)
        QuestRewards.checkIncomeThresholds(player)

        val avgPrice = (totalProceeds + qty / 2) / qty
        CobblemonMarket.marketStore.recordPriceTick(
            itemId = itemId, type = "sell",
            playerUuid = player.uuid.toString(), playerName = player.name.string,
            pricePerUnit = avgPrice, priceBefore = priceBefore, priceAfter = priceAfter,
            quantity = qty,
        )
        CobblemonMarket.marketStore.save()

        return TradeResult.Success(totalProceeds, state.stock)
    }

    fun countItems(player: Player, item: Item): Int =
        player.inventory.items.sumOf { if (it.item == item) it.count else 0 }

    fun removeItems(player: Player, item: Item, count: Int) {
        var remaining = count
        for (stack in player.inventory.items) {
            if (remaining <= 0) break
            if (stack.item == item) {
                val take = minOf(remaining, stack.count)
                stack.shrink(take)
                remaining -= take
            }
        }
    }

    fun hasInventorySpace(player: Player, count: Int): Boolean {
        var space = 0
        for (s in player.inventory.items) if (s.isEmpty) space += 64
        return space >= count
    }
}
