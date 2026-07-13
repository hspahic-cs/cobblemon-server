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
 * Pending fulfillment receipts for OFFLINE requesters, persisted to `runtime/request_receipts.json`
 * as `{ "<uuid>": [RequestReceipt, ...] }`. The request-side twin of [SalesReceiptStore]: when a
 * request is filled and its requester is online they're told immediately (nothing stored here);
 * offline, a receipt is queued and flushed as a summary on their next login, then cleared.
 * Save-on-mutate, server thread only.
 */
class RequestReceiptStore(private val configDir: Path) {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val file = ConfigPaths.runtime(configDir, "request_receipts.json")
    private val pending = HashMap<String, MutableList<RequestReceipt>>()

    fun load() {
        if (!file.exists()) return
        try {
            val type = object : TypeToken<HashMap<String, MutableList<RequestReceipt>>>() {}.type
            val loaded: HashMap<String, MutableList<RequestReceipt>> = gson.fromJson(file.readText(), type) ?: HashMap()
            pending.clear()
            pending.putAll(loaded)
        } catch (e: Exception) {
            log.error("Failed to load request receipts", e)
        }
    }

    fun save() {
        try {
            file.parent.createDirectories()
            file.writeText(gson.toJson(pending))
        } catch (e: Exception) {
            log.error("Failed to save request receipts", e)
        }
    }

    fun pending(uuid: UUID): List<RequestReceipt> = pending[uuid.toString()]?.toList() ?: emptyList()

    fun add(uuid: UUID, receipt: RequestReceipt) {
        pending.getOrPut(uuid.toString()) { mutableListOf() }.add(receipt)
        save()
    }

    /** Drop all pending receipts for a requester (after their login summary is delivered). */
    fun clear(uuid: UUID) {
        if (pending.remove(uuid.toString()) != null) save()
    }

    companion object {
        private val log = LoggerFactory.getLogger("cobblemon-auction/request-receipts")
    }
}
