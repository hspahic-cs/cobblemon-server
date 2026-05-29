package com.cobblemonmarket.config

import com.cobblemonmarket.CobblemonMarket
import com.cobblemonmarket.internal.ConfigPaths
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Per-item market parameters.
 *
 * - `baseBuyPrice` / `baseSellPrice` — prices when stock equals `baseStock` (the natural
 *   spread between them is the merchant's margin).
 * - `baseStock` — target/equilibrium quantity. Restock pulls stock toward this value.
 * - `elasticity` — how sharply price reacts to deviation from `baseStock`.
 *   1.0 ≈ inversely proportional, <1 = stable (consumables like Poké Balls), >1 = volatile
 *   (rare items). **Set to 0.0 for a fixed price** — scale is x^0 = 1 always, so buy/sell
 *   equals the base regardless of stock.
 * - `maxStockMultiplier` — sells are rejected when stock would exceed `baseStock × maxStockMultiplier`.
 * - `vendorTag` — which vendor NPC carries this item. Empty string is the legacy default
 *   vendor (tag `cobblemon_bridge.market_vendor`). Non-empty maps to a vendor tagged
 *   `cobblemon_bridge.market_vendor.<tag>` — spawned with `/market admin spawn <tag>`.
 *   The same item id can appear under different tags if you want it sold by multiple shops
 *   (each entry tracks its own stock and price independently).
 * - `sellable` — when false, the right-click sell paths in the GUI are no-ops and the slot
 *   lore reads "Buy only". For TM vendors and other one-way shops.
 */
data class ItemEntry(
    val baseBuyPrice: Int,
    val baseSellPrice: Int,
    val baseStock: Int = 100,
    val elasticity: Double = 1.0,
    val maxStockMultiplier: Double = 10.0,
    val vendorTag: String = "",
    val sellable: Boolean = true,
)

object ItemConfig {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    fun load(configDir: Path): Map<String, ItemEntry> {
        val file = ConfigPaths.authored(configDir, "items.json")
        if (!file.exists()) {
            val defaults = defaultItems()
            save(configDir, defaults)
            return defaults
        }
        return try {
            val type = object : TypeToken<Map<String, ItemEntry>>() {}.type
            gson.fromJson(file.readText(), type)
        } catch (e: Exception) {
            CobblemonMarket.logger.error("Failed to load items config, using defaults", e)
            defaultItems()
        }
    }

    fun save(configDir: Path, items: Map<String, ItemEntry>) {
        val file = ConfigPaths.authored(configDir, "items.json")
        file.parent.createDirectories()
        file.writeText(gson.toJson(items))
    }

    // baseBuyPrice ≈ 3× baseSellPrice mirrors the previous spread; tune in items.json.
    private fun defaultItems(): Map<String, ItemEntry> = mapOf(
        "cobblemon:rare_candy" to ItemEntry(baseBuyPrice = 6000, baseSellPrice = 2000, elasticity = 2.0),
        "cobblemon:ultra_ball" to ItemEntry(baseBuyPrice = 900, baseSellPrice = 300, elasticity = 0.5),
        "cobblemon:great_ball" to ItemEntry(baseBuyPrice = 300, baseSellPrice = 100, elasticity = 1.0),
        "cobblemon:poke_ball" to ItemEntry(baseBuyPrice = 90, baseSellPrice = 30, elasticity = 0.3),
        "cobblemon:revive" to ItemEntry(baseBuyPrice = 1500, baseSellPrice = 500, elasticity = 1.0),
        // High-volume staple. cobblemon-carrots' healer buys at current price when the player
        // is short carrots — replaces the flat `carrotPrice` config when this entry exists.
        "minecraft:carrot" to ItemEntry(baseBuyPrice = 6, baseSellPrice = 3, baseStock = 1000, elasticity = 0.7),
    )
}
