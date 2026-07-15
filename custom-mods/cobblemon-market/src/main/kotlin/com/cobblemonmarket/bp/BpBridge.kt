package com.cobblemonmarket.bp

import org.slf4j.LoggerFactory
import java.util.UUID

object BpBridge {
    private val log = LoggerFactory.getLogger("cobblemon-market/bp-bridge")
    private var bpManagerClass: Class<*>? = null
    private var getBalanceMethod: java.lang.reflect.Method? = null
    private var addBalanceMethod: java.lang.reflect.Method? = null
    private var subtractBalanceMethod: java.lang.reflect.Method? = null
    private var setBalanceMethod: java.lang.reflect.Method? = null

    private fun ensureLoaded() {
        if (bpManagerClass != null) return

        try {
            bpManagerClass = Class.forName("com.cobblemonranked.bp.BpManager")
            val cls = bpManagerClass ?: return

            getBalanceMethod = cls.getMethod("getBalance", UUID::class.java)
            addBalanceMethod = cls.getMethod("addBalance", UUID::class.java, Int::class.javaPrimitiveType)
            subtractBalanceMethod = cls.getMethod("subtractBalance", UUID::class.java, Int::class.javaPrimitiveType)
            setBalanceMethod = cls.getMethod("setBalance", UUID::class.java, Int::class.javaPrimitiveType)
        } catch (e: Exception) {
            log.error("Failed to load BpManager via reflection", e)
        }
    }

    fun getBalance(playerUuid: UUID): Int {
        ensureLoaded()
        return try {
            val obj = bpManagerClass?.getDeclaredField("INSTANCE")?.get(null)
                ?: bpManagerClass?.getField("INSTANCE")?.get(null)
                ?: return 0
            (getBalanceMethod?.invoke(obj, playerUuid) as? Number)?.toInt() ?: 0
        } catch (e: Exception) {
            log.error("Failed to get BP balance for $playerUuid", e)
            0
        }
    }

    fun addBalance(playerUuid: UUID, amount: Int): Int {
        ensureLoaded()
        return try {
            val obj = bpManagerClass?.getField("INSTANCE")?.get(null) ?: return 0
            (addBalanceMethod?.invoke(obj, playerUuid, amount) as? Number)?.toInt() ?: 0
        } catch (e: Exception) {
            log.error("Failed to add BP balance for $playerUuid", e)
            0
        }
    }

    fun subtractBalance(playerUuid: UUID, amount: Int): Boolean {
        ensureLoaded()
        return try {
            val obj = bpManagerClass?.getField("INSTANCE")?.get(null) ?: return false
            (subtractBalanceMethod?.invoke(obj, playerUuid, amount) as? Boolean) ?: false
        } catch (e: Exception) {
            log.error("Failed to subtract BP balance for $playerUuid", e)
            false
        }
    }

    fun setBalance(playerUuid: UUID, amount: Int): Int {
        ensureLoaded()
        return try {
            val obj = bpManagerClass?.getField("INSTANCE")?.get(null) ?: return 0
            (setBalanceMethod?.invoke(obj, playerUuid, amount) as? Number)?.toInt() ?: 0
        } catch (e: Exception) {
            log.error("Failed to set BP balance for $playerUuid", e)
            0
        }
    }
}
