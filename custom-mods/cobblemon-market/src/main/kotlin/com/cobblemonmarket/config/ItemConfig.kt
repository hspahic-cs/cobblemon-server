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
/**
 * Note on nullable fields: Gson constructs Kotlin data classes via Unsafe (bypassing the
 * constructor), so Kotlin default-parameter values like `vendorTag: String = ""` are NOT
 * applied — a missing JSON field deserializes to `null` / primitive zero, not the data-class
 * default. Hand-edited items.json files that omit `vendorTag` would land with `vendorTag = null`
 * at runtime and silently disappear from the default-vendor menu. Making these fields nullable
 * and reading through [vendorScope] / [isSellable] forces every caller to handle the null and
 * keeps the default-vendor and "sellable" semantics correct even if a field is missing.
 */
data class ItemEntry(
    val baseBuyPrice: Int,
    val baseSellPrice: Int,
    val baseStock: Int = 100,
    val elasticity: Double = 1.0,
    val maxStockMultiplier: Double = 10.0,
    val vendorTag: String? = "",
    val sellable: Boolean? = true,
    // ─── 0.7.11 market-overhaul fields (all optional, null = use the global default) ──
    /** Per-item override for the buy-side price multiplier clamp. Defaults to PricingEngine.SCALE_CLAMP. */
    val buyPriceClamp: Double? = null,
    /** Per-item override for the sell-side price multiplier clamp. Defaults to PricingEngine.SCALE_CLAMP. */
    val sellPriceClamp: Double? = null,
    /** Stock units consumed per unit bought. Server-wide default is 3.0 — see
     *  [effectiveBuyStockImpact]. Setting this on an item overrides the global; useful
     *  for high-volume items where shift-buy-64 needs to clear without hitting the
     *  stock floor (e.g., set to 1.0 for held items / TMs / carrots if scarcity isn't
     *  the design intent for them). */
    val buyStockImpact: Double? = null,
    /** Stock units returned per unit sold. Defaults to 1.0 — sell-side refill stays
     *  symmetric with raw items moved. */
    val sellStockImpact: Double? = null,
    /** Hard per-item floor on the buy price (applied after rounding, before the clamp ratio). Defaults to 0 = no floor beyond the global ratio. */
    val minBuyPrice: Int? = null,
)

/** Empty string = the default unscoped vendor. */
val ItemEntry.vendorScope: String get() = vendorTag ?: ""
val ItemEntry.isSellable: Boolean get() = sellable ?: true
val ItemEntry.effectiveBuyClamp: Double get() = buyPriceClamp ?: com.cobblemonmarket.pricing.PricingEngine.SCALE_CLAMP
val ItemEntry.effectiveSellClamp: Double get() = sellPriceClamp ?: com.cobblemonmarket.pricing.PricingEngine.SCALE_CLAMP
/** Server-wide default of 3.0 — buying drains stock 3x faster than restock refills,
 *  giving popular items a "going scarce" feel. Per-item override is supported (e.g.,
 *  set to 1.0 on items where bulk-buy needs to clear without stock-floor friction).
 *  Safe under the price clamp: the anti-arbitrage invariant holds at any impact ratio. */
val ItemEntry.effectiveBuyStockImpact: Double get() = buyStockImpact ?: 3.0
val ItemEntry.effectiveSellStockImpact: Double get() = sellStockImpact ?: 1.0
val ItemEntry.effectiveMinBuyPrice: Int get() = minBuyPrice ?: 0

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
