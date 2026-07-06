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
 * Pending sale receipts for OFFLINE sellers, persisted to `runtime/sale_receipts.json` as
 * `{ "<uuid>": [SaleReceipt, ...] }`. When a listing sells and its seller is online they're told
 * immediately (nothing stored here); when they're offline the receipt is queued and flushed as a
 * summary on their next login, then cleared. Save-on-mutate, server thread only.
 */
class SalesReceiptStore(private val configDir: Path) {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val file = ConfigPaths.runtime(configDir, "sale_receipts.json")
    private val pending = HashMap<String, MutableList<SaleReceipt>>()

    fun load() {
        if (!file.exists()) return
        try {
            val type = object : TypeToken<HashMap<String, MutableList<SaleReceipt>>>() {}.type
            val loaded: HashMap<String, MutableList<SaleReceipt>> = gson.fromJson(file.readText(), type) ?: HashMap()
            pending.clear()
            pending.putAll(loaded)
        } catch (e: Exception) {
            log.error("Failed to load sale receipts", e)
        }
    }

    fun save() {
        try {
            file.parent.createDirectories()
            file.writeText(gson.toJson(pending))
        } catch (e: Exception) {
            log.error("Failed to save sale receipts", e)
        }
    }

    fun pending(uuid: UUID): List<SaleReceipt> = pending[uuid.toString()]?.toList() ?: emptyList()

    fun add(uuid: UUID, receipt: SaleReceipt) {
        pending.getOrPut(uuid.toString()) { mutableListOf() }.add(receipt)
        save()
    }

    /** Drop all pending receipts for a seller (after their login summary is delivered). */
    fun clear(uuid: UUID) {
        if (pending.remove(uuid.toString()) != null) save()
    }

    companion object {
        private val log = LoggerFactory.getLogger("cobblemon-auction/receipts")
    }
}
