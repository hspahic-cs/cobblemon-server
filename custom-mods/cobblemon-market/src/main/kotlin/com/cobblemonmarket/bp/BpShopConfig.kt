package com.cobblemonmarket.bp

import com.google.gson.Gson
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

object BpShopConfig {
    private val log = LoggerFactory.getLogger("cobblemon-market/bp")
    private val gson = Gson()
    private val configPath = Paths.get("config/bp-items.json")
    private var itemCache: Map<String, BpItemEntry>? = null

    fun loadConfig(): Map<String, BpItemEntry> {
        itemCache?.let { return it }

        if (!configPath.exists()) {
            log.warn("bp-items.json not found at $configPath")
            return emptyMap()
        }

        return try {
            val rawJson = configPath.readText()
            val root = gson.fromJson(rawJson, Map::class.java) as Map<String, Any>
            val items = root["items"] as Map<String, Map<String, Any>>

            val loaded = items.mapValues { (id, data) ->
                BpItemEntry(
                    id = id,
                    cost = (data["cost"] as Number).toInt(),
                    displayName = data["displayName"] as String? ?: id,
                    isVoucher = data["isVoucher"] as Boolean? ?: false,
                    voucherType = data["voucherType"] as String?
                )
            }
            itemCache = loaded
            loaded
        } catch (e: Exception) {
            log.error("Failed to load bp-items.json", e)
            emptyMap()
        }
    }

    fun getItem(id: String): BpItemEntry? = loadConfig()[id]

    fun getAllItems(): List<BpItemEntry> = loadConfig().values.toList()
}
