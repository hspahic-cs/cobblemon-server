package com.cobblemonauction.config

import com.cobblemonauction.internal.ConfigPaths
import com.google.gson.GsonBuilder
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Authored config for the auction house. Written with defaults on first boot to
 * `config/cobblemon-auction/authored/config.json`.
 *
 * @property listingTtlDays       Days a listing stays live before it expires back to the
 *                                seller's mailbox.
 * @property maxListingsPerPlayer Cap on a player's simultaneous active listings.
 * @property minPrice/maxPrice    Inclusive bounds on the total price a seller may set.
 * @property listingFeePercent    Fee charged when a listing is created, as a percent of the asking
 *                                price. It's a deposit: refunded to the seller if the item sells,
 *                                kept (destroyed — a currency sink) if the listing expires or is
 *                                cancelled. Set to 0 to disable fees.
 * @property minListingFee        Floor on the listing fee so cheap listings still cost something
 *                                (anti-spam). The fee never exceeds the asking price itself.
 * @property blocklist            Item ids (namespace:path) that may not be listed. Living
 *                                Pokémon aren't itemstacks in Cobblemon, so there's nothing to
 *                                block there today; this covers Poké Balls and any future/edge
 *                                items we decide to keep off the market.
 */
data class AuctionConfig(
    val listingTtlDays: Int = 7,
    val maxListingsPerPlayer: Int = 10,
    val minPrice: Int = 1,
    val maxPrice: Int = 1_000_000,
    val listingFeePercent: Double = 5.0,
    val minListingFee: Int = 1,
    val blocklist: List<String> = DEFAULT_BLOCKLIST,
) {
    /** TTL in milliseconds, clamped to a sane floor so a misconfig can't expire listings instantly. */
    fun ttlMillis(): Long = listingTtlDays.coerceAtLeast(1).toLong() * 24L * 60L * 60L * 1000L

    /**
     * Listing fee for an item priced at [price]: `ceil(price * pct/100)`, floored at [minListingFee]
     * and never more than the price itself. Returns 0 when [listingFeePercent] and [minListingFee]
     * are both 0 (fees disabled).
     */
    fun listingFee(price: Int): Int {
        if (listingFeePercent <= 0.0 && minListingFee <= 0) return 0
        val pct = kotlin.math.ceil(price * listingFeePercent / 100.0).toInt()
        return maxOf(minListingFee, pct).coerceIn(0, price)
    }

    fun isBlocked(itemId: String): Boolean = itemId in blockedSet
    private val blockedSet: Set<String> by lazy { blocklist.toHashSet() }

    companion object {
        private val log = LoggerFactory.getLogger("cobblemon-auction/config")
        private val gson = GsonBuilder().setPrettyPrinting().create()

        private val DEFAULT_BLOCKLIST = listOf(
            "cobblemon:poke_ball",
            "cobblemon:great_ball",
            "cobblemon:ultra_ball",
            "cobblemon:master_ball",
        )

        fun load(configDir: Path): AuctionConfig {
            val file = ConfigPaths.authored(configDir, "config.json")
            if (!file.exists()) {
                val def = AuctionConfig()
                try {
                    file.parent.createDirectories()
                    file.writeText(gson.toJson(def))
                    log.info("Wrote default auction config to ${file.parent.fileName}/config.json")
                } catch (e: Exception) {
                    log.warn("Failed to write default auction config: ${e.message}")
                }
                return def
            }
            return try {
                gson.fromJson(file.readText(), AuctionConfig::class.java) ?: AuctionConfig()
            } catch (e: Exception) {
                log.error("Failed to parse auction config — using defaults", e)
                AuctionConfig()
            }
        }
    }
}
