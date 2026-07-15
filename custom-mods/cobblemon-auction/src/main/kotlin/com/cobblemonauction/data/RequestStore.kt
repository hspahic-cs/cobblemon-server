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
 * Global store of active buy orders, persisted to `runtime/requests.json` as a flat array. The
 * request-side twin of [AuctionStore] — kept as a separate near-identical store rather than a
 * generic base, matching the codebase's convention. [LinkedHashMap] keyed by request id for O(1)
 * lookup/removal and stable pagination. Server thread only, save-on-mutate.
 */
class RequestStore(private val configDir: Path) {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val file = ConfigPaths.runtime(configDir, "requests.json")
    private val requests = LinkedHashMap<String, Request>()

    fun load() {
        if (!file.exists()) return
        try {
            val type = object : TypeToken<List<Request>>() {}.type
            val loaded: List<Request> = gson.fromJson(file.readText(), type) ?: emptyList()
            requests.clear()
            loaded.forEach { requests[it.id] = it }
        } catch (e: Exception) {
            log.error("Failed to load requests", e)
        }
    }

    fun save() {
        try {
            file.parent.createDirectories()
            file.writeText(gson.toJson(requests.values.toList()))
        } catch (e: Exception) {
            log.error("Failed to save requests", e)
        }
    }

    /** Newest-first for the browse GUI. */
    fun all(): List<Request> = requests.values.sortedByDescending { it.createdAt }

    fun get(id: String): Request? = requests[id]

    fun byRequester(uuid: UUID): List<Request> =
        requests.values.filter { it.requesterUuid == uuid.toString() }.sortedByDescending { it.createdAt }

    fun countByRequester(uuid: UUID): Int = requests.values.count { it.requesterUuid == uuid.toString() }

    fun add(request: Request) {
        requests[request.id] = request
        save()
    }

    /** Remove and return the request, or null if it was already gone (e.g. fulfilled concurrently). */
    fun remove(id: String): Request? {
        val removed = requests.remove(id)
        if (removed != null) save()
        return removed
    }

    /** Requests whose `expiresAt` is at or before [now]. */
    fun expired(now: Long): List<Request> = requests.values.filter { it.expiresAt <= now }

    companion object {
        private val log = LoggerFactory.getLogger("cobblemon-auction/requests")
    }
}
