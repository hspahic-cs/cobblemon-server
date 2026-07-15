package com.cobblemonmarket.data

import com.cobblemonmarket.CobblemonMarket
import com.cobblemonmarket.config.ItemEntry
import com.cobblemonmarket.internal.ConfigPaths
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

class MarketStore(private val configDir: Path) {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val file = ConfigPaths.runtime(configDir, "state.json")
    private val states: MutableMap<String, ItemState> = mutableMapOf()

    fun load() {
        if (!file.exists()) return
        try {
            val type = object : TypeToken<MutableMap<String, ItemState>>() {}.type
            val loaded: MutableMap<String, ItemState> = gson.fromJson(file.readText(), type)
            states.clear()
            states.putAll(loaded)
        } catch (e: Exception) {
            CobblemonMarket.logger.error("Failed to load market state", e)
        }
    }

    fun save() {
        file.parent.createDirectories()
        file.writeText(gson.toJson(states))
    }

    fun getOrCreate(itemId: String): ItemState {
        return states.getOrPut(itemId) { ItemState() }
    }

    fun getAll(): Map<String, ItemState> = states.toMap()

    /**
     * Reconciles persisted state against the current item config. Two jobs:
     *
     *  1. **New items** get stock = baseStock so a fresh server starts at equilibrium prices
     *     instead of zero stock (which would scale everything by ~baseStock^elasticity).
     *  2. **Changed baseStock** rescales the item's current stock to preserve its fullness ratio
     *     (stock / baseStock). Without this, editing baseStock in config leaves the old absolute
     *     stock in place — e.g. raising baseStock 200 → 500 would strand stock at 200 (40% full)
     *     and spike prices. Rescaling keeps the price exactly where it was across the change.
     *     The rescaled stock is capped at baseStock × maxStockMultiplier.
     */
    fun ensureInitialized(items: Map<String, ItemEntry>) {
        var changed = false
        for ((itemId, entry) in items) {
            val base = entry.baseStock.toDouble()
            val existing = states[itemId]
            when {
                existing == null -> {
                    states[itemId] = ItemState(stock = base, baseStockRef = base)
                    changed = true
                }
                // Legacy record with no recorded reference: adopt the current base without rescaling.
                existing.baseStockRef <= 0.0 -> {
                    existing.baseStockRef = base
                    changed = true
                }
                // baseStock changed in config: rescale current stock to the same fullness ratio.
                existing.baseStockRef != base -> {
                    val ratio = existing.stock / existing.baseStockRef
                    val maxStock = base * entry.maxStockMultiplier
                    existing.stock = (ratio * base).coerceIn(0.0, maxStock)
                    existing.baseStockRef = base
                    changed = true
                }
            }
        }
        if (changed) save()
    }

    fun setStock(itemId: String, stock: Double) {
        getOrCreate(itemId).stock = stock.coerceAtLeast(0.0)
        save()
    }

    /**
     * Records one batch-level price-history entry for the chart shown by `/market price`.
     * Bounded by [com.cobblemonmarket.config.MarketConfig.priceHistorySize]; oldest entries
     * are dropped when the cap is hit.
     *
     * `priceBefore`/`priceAfter` should be the per-unit one-trade price at the stock in
     * effect immediately before and immediately after the batch — they drive the open/close
     * of each candle. `playerUuid`/`playerName` identify the trader for the same-player
     * same-day grouping logic.
     */
    fun recordPriceTick(
        itemId: String, type: String,
        playerUuid: String, playerName: String,
        pricePerUnit: Int, priceBefore: Int, priceAfter: Int,
        quantity: Int,
    ) {
        val state = getOrCreate(itemId)
        state.priceHistory.add(PriceTick(
            type = type,
            timestamp = System.currentTimeMillis(),
            pricePerUnit = pricePerUnit,
            quantity = quantity,
            playerUuid = playerUuid,
            playerName = playerName,
            priceBefore = priceBefore,
            priceAfter = priceAfter,
        ))
        val cap = CobblemonMarket.config.priceHistorySize
        while (state.priceHistory.size > cap) {
            state.priceHistory.removeAt(0)
        }
        save()
    }
}
