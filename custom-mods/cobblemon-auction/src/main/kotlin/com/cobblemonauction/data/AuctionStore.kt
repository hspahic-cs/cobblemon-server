package com.cobblemonauction.data

import com.cobblemonauction.internal.ConfigPaths
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.util.UUID
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Global store of active listings, persisted to `runtime/listings.json` as a flat array.
 * Kept in insertion order in a [LinkedHashMap] keyed by listing id for O(1) lookup/removal and
 * stable pagination. All access is on the server thread, so no locking is needed; every mutating
 * method saves immediately (save-on-mutate) to survive a crash between ticks.
 */
class AuctionStore(private val configDir: Path) {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val file = ConfigPaths.runtime(configDir, "listings.json")
    private val listings = LinkedHashMap<String, Listing>()

    fun load() {
        if (!file.exists()) return
        try {
            val type = object : TypeToken<List<Listing>>() {}.type
            val loaded: List<Listing> = gson.fromJson(file.readText(), type) ?: emptyList()
            listings.clear()
            loaded.forEach { listings[it.id] = it }
        } catch (e: Exception) {
            log.error("Failed to load listings", e)
        }
    }

    fun save() {
        try {
            file.parent.createDirectories()
            file.writeText(gson.toJson(listings.values.toList()))
        } catch (e: Exception) {
            log.error("Failed to save listings", e)
        }
    }

    /** Newest-first for the browse GUI. */
    fun all(): List<Listing> = listings.values.sortedByDescending { it.createdAt }

    fun get(id: String): Listing? = listings[id]

    fun bySeller(uuid: UUID): List<Listing> =
        listings.values.filter { it.sellerUuid == uuid.toString() }.sortedByDescending { it.createdAt }

    fun countBySeller(uuid: UUID): Int = listings.values.count { it.sellerUuid == uuid.toString() }

    fun add(listing: Listing) {
        listings[listing.id] = listing
        save()
    }

    /** Remove and return the listing, or null if it was already gone (e.g. bought concurrently). */
    fun remove(id: String): Listing? {
        val removed = listings.remove(id)
        if (removed != null) save()
        return removed
    }

    /** Listings whose `expiresAt` is at or before [now]. */
    fun expired(now: Long): List<Listing> = listings.values.filter { it.expiresAt <= now }

    companion object {
        private val log = LoggerFactory.getLogger("cobblemon-auction/listings")
    }
}
