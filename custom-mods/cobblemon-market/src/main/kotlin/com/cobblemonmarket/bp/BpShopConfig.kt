package com.cobblemonmarket.bp

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.slf4j.LoggerFactory
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.readText

data class BpItemEntry(
    val id: String,
    val cost: Int,
    val displayName: String,
    val isVoucher: Boolean = false,
    val voucherType: String? = null
)

/**
 * Loads `config/bp-items.json`, which is a JSON **array** of item entries (id, cost, displayName,
 * isVoucher, voucherType). Order in the file is the order shown in the BP shop GUI.
 */
object BpShopConfig {
    private val log = LoggerFactory.getLogger("cobblemon-market/bp")
    private val gson = Gson()
    private val configPath = Paths.get("config/bp-items.json")
    private var itemCache: List<BpItemEntry>? = null

    fun loadConfig(): List<BpItemEntry> {
        itemCache?.let { return it }

        if (!configPath.exists()) {
            log.warn("bp-items.json not found at {}", configPath)
            return emptyList()
        }

        return try {
            val type = object : TypeToken<List<BpItemEntry>>() {}.type
            val loaded: List<BpItemEntry> = gson.fromJson(configPath.readText(), type) ?: emptyList()
            itemCache = loaded
            log.info("Loaded {} BP shop items", loaded.size)
            loaded
        } catch (e: Exception) {
            log.error("Failed to load bp-items.json", e)
            emptyList()
        }
    }

    /** Drop the in-memory cache so the next access re-reads the file. */
    fun reload() {
        itemCache = null
        loadConfig()
    }

    fun getItem(id: String): BpItemEntry? = loadConfig().firstOrNull { it.id == id }

    fun getAllItems(): List<BpItemEntry> = loadConfig()
}
