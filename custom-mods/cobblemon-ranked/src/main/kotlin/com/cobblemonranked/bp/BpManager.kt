package com.cobblemonranked.bp

import com.google.gson.Gson
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.nio.file.Paths
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

object BpManager {
    private val log = LoggerFactory.getLogger("cobblemon-ranked/bp")
    private val gson = Gson()

    private val basePath: Path = Paths.get("bp").also { it.createDirectories() }
    private val balanceCache = ConcurrentHashMap<UUID, Int>()

    private data class BpData(val balance: Int)

    private fun fileFor(uuid: UUID): Path = basePath.resolve("$uuid.json")

    fun getBalance(playerUuid: UUID): Int {
        balanceCache[playerUuid]?.let { return it }

        val file = fileFor(playerUuid)
        if (!file.exists()) return 0

        return try {
            val data = gson.fromJson(file.readText(), BpData::class.java)
            data.balance.also { balanceCache[playerUuid] = it }
        } catch (e: Exception) {
            log.error("Failed to read BP file for $playerUuid", e)
            0
        }
    }

    fun addBalance(playerUuid: UUID, amount: Int): Int {
        if (amount < 0) throw IllegalArgumentException("Amount must be non-negative")
        val current = getBalance(playerUuid)
        val newBalance = current + amount
        return setBalance(playerUuid, newBalance)
    }

    fun setBalance(playerUuid: UUID, amount: Int): Int {
        if (amount < 0) throw IllegalArgumentException("Balance cannot be negative")

        val file = fileFor(playerUuid)
        try {
            val data = BpData(amount)
            file.writeText(gson.toJson(data))
            balanceCache[playerUuid] = amount
            return amount
        } catch (e: Exception) {
            log.error("Failed to write BP file for $playerUuid", e)
            throw e
        }
    }

    fun subtractBalance(playerUuid: UUID, amount: Int): Boolean {
        if (amount < 0) throw IllegalArgumentException("Amount must be non-negative")
        val current = getBalance(playerUuid)
        if (current < amount) return false
        setBalance(playerUuid, current - amount)
        return true
    }
}
