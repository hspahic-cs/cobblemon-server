package com.cobblemonauction.config

import com.cobblemonauction.internal.ConfigPaths
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * One entry in the requestable-items whitelist (`authored/requestable-items.json`). [category]
 * groups the item under a Catalog tab; [suggestedPrice], if present, pre-fills the price-anvil hint.
 */
data class RequestableItem(
    val category: String = "Misc",
    val suggestedPrice: Int? = null,
)

/**
 * Authored config for the auction house. Written with defaults on first boot to
 * `config/cobblemon-auction/authored/config.json`.
 *
 * @property listingTtlDays       Days a listing stays live before it expires back to the
 *                                seller's mailbox.
 * @property maxListingsPerPlayer Cap on a player's simultaneous active listings.
 * @property requestTtlDays       Days a buy-order (request) stays live before it expires and
 *                                refunds the escrow to the requester.
 * @property maxRequestsPerPlayer Cap on a player's simultaneous active requests.
 * @property minPrice/maxPrice    Inclusive bounds on the total price a seller may set — reused as
 *                                the escrow bounds for requests (same currency space).
 * @property listingFeePercent    Fee charged when a listing is created, as a percent of the asking
 *                                price. It's a deposit: refunded to the seller if the item sells,
 *                                kept (destroyed — a currency sink) if the listing expires or is
 *                                cancelled. Set to 0 to disable fees.
 * @property minListingFee        Floor on the listing fee so cheap listings still cost something
 *                                (anti-spam). The fee never exceeds the asking price itself.
 * @property blocklist            Item ids (namespace:path) that may not be listed. Living
 *                                Pokémon aren't itemstacks in Cobblemon, so there's nothing to
 *                                block there today; this covers Poké Balls and any future/edge
 *                                items we decide to keep off the market. Also wins over the
 *                                requestable whitelist: any id present in both is dropped from
 *                                requestable on load.
 */
data class AuctionConfig(
    val listingTtlDays: Int = 7,
    val maxListingsPerPlayer: Int = 30,
    val requestTtlDays: Int = 7,
    val maxRequestsPerPlayer: Int = 30,
    val minPrice: Int = 1,
    val maxPrice: Int = 1_000_000,
    val listingFeePercent: Double = 5.0,
    val minListingFee: Int = 1,
    val blocklist: List<String> = DEFAULT_BLOCKLIST,
) {
    /**
     * The reconciled requestable-items whitelist, populated by [load] from
     * `authored/requestable-items.json` (a SEPARATE file from config.json). Marked transient so Gson
     * never (de)serializes it into config.json; [load] always assigns it before returning, so it is
     * never observed unset. Insertion order is the file's order, preserved for stable Catalog tabs.
     */
    @Transient
    var requestable: Map<String, RequestableItem> = emptyMap()
        internal set

    /** TTL in milliseconds, clamped to a sane floor so a misconfig can't expire listings instantly. */
    fun ttlMillis(): Long = listingTtlDays.coerceAtLeast(1).toLong() * 24L * 60L * 60L * 1000L

    /** Request TTL in milliseconds, same floor as [ttlMillis]. */
    fun requestTtlMillis(): Long = requestTtlDays.coerceAtLeast(1).toLong() * 24L * 60L * 60L * 1000L

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

    // Direct list check — no cached `by lazy` set, because Gson serializes a lazy delegate's
    // backing field into config.json as junk ("blockedSet$delegate": {...}). The blocklist is
    // tiny, so a linear scan is fine.
    fun isBlocked(itemId: String): Boolean = itemId in blocklist

    /** Whether [itemId] is on the (already blocklist-reconciled) requestable whitelist. */
    fun isRequestable(itemId: String): Boolean = itemId in requestable

    companion object {
        private val log = LoggerFactory.getLogger("cobblemon-auction/config")
        private val gson = GsonBuilder().setPrettyPrinting().create()

        // Empty by default: Cobblemon Pokémon aren't itemstacks, so there's nothing exploitable to
        // block out of the box (empty Poké Balls are ordinary tradeable items). Operators can still
        // add specific item ids/tags here if a future item needs keeping off the market.
        private val DEFAULT_BLOCKLIST = emptyList<String>()

        // Small in-code seed written to authored/requestable-items.json on first boot so a fresh dev
        // world (no server-override present) isn't left with a dead feature. The authoritative list
        // is the one shipped under modpack/server-overrides/.../authored/requestable-items.json — a
        // deploy replacing this file with the repo version is the intended model. Favours items the
        // market NPC does NOT sell (evolution stones/items, mints, EV vitamins, master ball).
        private val DEFAULT_REQUESTABLE: Map<String, RequestableItem> = linkedMapOf(
            // Evolution stones
            "cobblemon:fire_stone" to RequestableItem("Evolution Stones", 3000),
            "cobblemon:water_stone" to RequestableItem("Evolution Stones", 3000),
            "cobblemon:thunder_stone" to RequestableItem("Evolution Stones", 3000),
            "cobblemon:leaf_stone" to RequestableItem("Evolution Stones", 3000),
            "cobblemon:moon_stone" to RequestableItem("Evolution Stones", 3000),
            "cobblemon:sun_stone" to RequestableItem("Evolution Stones", 3000),
            "cobblemon:shiny_stone" to RequestableItem("Evolution Stones", 3500),
            "cobblemon:dusk_stone" to RequestableItem("Evolution Stones", 3500),
            "cobblemon:dawn_stone" to RequestableItem("Evolution Stones", 3500),
            "cobblemon:ice_stone" to RequestableItem("Evolution Stones", 3000),
            // Evolution items
            "cobblemon:dragon_scale" to RequestableItem("Evolution Items", 4000),
            "cobblemon:upgrade" to RequestableItem("Evolution Items", 4000),
            "cobblemon:dubious_disc" to RequestableItem("Evolution Items", 4000),
            "cobblemon:electirizer" to RequestableItem("Evolution Items", 4000),
            "cobblemon:magmarizer" to RequestableItem("Evolution Items", 4000),
            "cobblemon:protector" to RequestableItem("Evolution Items", 4000),
            "cobblemon:reaper_cloth" to RequestableItem("Evolution Items", 4000),
            "cobblemon:prism_scale" to RequestableItem("Evolution Items", 4000),
            "cobblemon:oval_stone" to RequestableItem("Evolution Items", 3000),
            "cobblemon:sachet" to RequestableItem("Evolution Items", 4000),
            "cobblemon:whipped_dream" to RequestableItem("Evolution Items", 4000),
            "cobblemon:black_augurite" to RequestableItem("Evolution Items", 5000),
            "cobblemon:chipped_pot" to RequestableItem("Evolution Items", 4000),
            "cobblemon:cracked_pot" to RequestableItem("Evolution Items", 4000),
            "cobblemon:galarica_cuff" to RequestableItem("Evolution Items", 4000),
            "cobblemon:galarica_wreath" to RequestableItem("Evolution Items", 4000),
            "cobblemon:peat_block" to RequestableItem("Evolution Items", 5000),
            "cobblemon:metal_alloy" to RequestableItem("Evolution Items", 6000),
            "cobblemon:sweet_apple" to RequestableItem("Evolution Items", 3000),
            "cobblemon:tart_apple" to RequestableItem("Evolution Items", 3000),
            "cobblemon:syrupy_apple" to RequestableItem("Evolution Items", 3000),
            "cobblemon:auspicious_armor" to RequestableItem("Evolution Items", 6000),
            "cobblemon:malicious_armor" to RequestableItem("Evolution Items", 6000),
            "cobblemon:masterpiece_teacup" to RequestableItem("Evolution Items", 6000),
            "cobblemon:unremarkable_teacup" to RequestableItem("Evolution Items", 4000),
            // Nature mints
            "cobblemon:adamant_mint" to RequestableItem("Nature Mints", 2500),
            "cobblemon:jolly_mint" to RequestableItem("Nature Mints", 2500),
            "cobblemon:timid_mint" to RequestableItem("Nature Mints", 2500),
            "cobblemon:modest_mint" to RequestableItem("Nature Mints", 2500),
            "cobblemon:bold_mint" to RequestableItem("Nature Mints", 2500),
            "cobblemon:calm_mint" to RequestableItem("Nature Mints", 2500),
            "cobblemon:careful_mint" to RequestableItem("Nature Mints", 2500),
            "cobblemon:impish_mint" to RequestableItem("Nature Mints", 2500),
            "cobblemon:brave_mint" to RequestableItem("Nature Mints", 2500),
            "cobblemon:lonely_mint" to RequestableItem("Nature Mints", 2500),
            "cobblemon:naughty_mint" to RequestableItem("Nature Mints", 2500),
            "cobblemon:relaxed_mint" to RequestableItem("Nature Mints", 2500),
            "cobblemon:mild_mint" to RequestableItem("Nature Mints", 2500),
            "cobblemon:quiet_mint" to RequestableItem("Nature Mints", 2500),
            "cobblemon:hasty_mint" to RequestableItem("Nature Mints", 2500),
            "cobblemon:naive_mint" to RequestableItem("Nature Mints", 2500),
            "cobblemon:rash_mint" to RequestableItem("Nature Mints", 2500),
            "cobblemon:sassy_mint" to RequestableItem("Nature Mints", 2500),
            "cobblemon:lax_mint" to RequestableItem("Nature Mints", 2500),
            "cobblemon:gentle_mint" to RequestableItem("Nature Mints", 2500),
            "cobblemon:serious_mint" to RequestableItem("Nature Mints", 2500),
            // EV vitamins
            "cobblemon:hp_up" to RequestableItem("Vitamins", 2000),
            "cobblemon:protein" to RequestableItem("Vitamins", 2000),
            "cobblemon:iron" to RequestableItem("Vitamins", 2000),
            "cobblemon:calcium" to RequestableItem("Vitamins", 2000),
            "cobblemon:zinc" to RequestableItem("Vitamins", 2000),
            "cobblemon:carbos" to RequestableItem("Vitamins", 2000),
            // Rare / competitive
            "cobblemon:ability_patch" to RequestableItem("Rare", 8000),
            "cobblemon:master_ball" to RequestableItem("Rare", 50000),
        )

        fun load(configDir: Path): AuctionConfig {
            val cfg = loadConfigFile(configDir)
            cfg.requestable = loadRequestable(configDir, cfg.blocklist)
            return cfg
        }

        private fun loadConfigFile(configDir: Path): AuctionConfig {
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

        /**
         * Load the requestable-items whitelist, seeding the file with [DEFAULT_REQUESTABLE] if absent,
         * then reconcile it against [blocklist]: any id in both is DROPPED from requestable (blocklist
         * wins) and logged, so the two lists can't silently contradict each other. The request path
         * then trusts the reconciled whitelist and never re-checks the blocklist per-request.
         */
        internal fun loadRequestable(configDir: Path, blocklist: List<String>): Map<String, RequestableItem> {
            val file = ConfigPaths.authored(configDir, "requestable-items.json")
            val raw: Map<String, RequestableItem> = if (!file.exists()) {
                try {
                    file.parent.createDirectories()
                    file.writeText(gson.toJson(DEFAULT_REQUESTABLE))
                    log.info("Wrote default requestable-items whitelist (${DEFAULT_REQUESTABLE.size} items)")
                } catch (e: Exception) {
                    log.warn("Failed to write default requestable-items whitelist: ${e.message}")
                }
                DEFAULT_REQUESTABLE
            } else {
                try {
                    val type = object : TypeToken<LinkedHashMap<String, RequestableItem>>() {}.type
                    gson.fromJson(file.readText(), type) ?: LinkedHashMap()
                } catch (e: Exception) {
                    log.error("Failed to parse requestable-items whitelist — request feature disabled", e)
                    LinkedHashMap()
                }
            }

            val blocked = blocklist.toHashSet()
            val reconciled = LinkedHashMap<String, RequestableItem>()
            for ((id, item) in raw) {
                if (id in blocked) {
                    log.warn("Requestable id '$id' is also on the blocklist — dropping from requestable (blocklist wins)")
                    continue
                }
                reconciled[id] = item
            }
            return reconciled
        }
    }
}
