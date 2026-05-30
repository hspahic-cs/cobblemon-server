package com.cobblemonranked.economy

import org.slf4j.LoggerFactory
import java.lang.reflect.Method
import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Reflection bridge to NeoEssentials Economy (active Vault provider on the server, replaces Cobblemon Economy which never loaded — see 0.7.8 release notes).
 *
 * Each custom mod that touches currency keeps its own local copy of this file rather than
 * importing a shared one — keeps each mod independent of the others at compile time. The
 * pattern was lifted from `cobblemon-market` / `cobblemon-bridge` / `cobblemon-carrots`.
 */
object EconomyBridge {

    private val log = LoggerFactory.getLogger("cobblemon-ranked/economy")
    private const val ECONOMY_CLASS = "com.zerog.neoessentials.economy.managers.EconomyManager"

    @Volatile private var resolvedManager: Any? = null
    @Volatile private var getBalanceMethod: Method? = null
    @Volatile private var addBalanceMethod: Method? = null
    @Volatile private var subBalanceMethod: Method? = null
    private val warnedOnce = AtomicBoolean(false)

    private fun manager(): Any? {
        resolvedManager?.let { return it }
        return try {
            val cls = Class.forName(ECONOMY_CLASS)
            val mgr = cls.getMethod("getInstance").invoke(null)
            resolvedManager = mgr
            getBalanceMethod = mgr.javaClass.getMethod("getBalance", UUID::class.java)
            addBalanceMethod = mgr.javaClass.getMethod("addBalance", UUID::class.java, BigDecimal::class.java)
            subBalanceMethod = mgr.javaClass.getMethod("subtractBalance", UUID::class.java, BigDecimal::class.java)
            mgr
        } catch (e: ClassNotFoundException) {
            warnOnce("NeoEssentials Economy not loaded — wager operations disabled")
            null
        } catch (e: Throwable) {
            warnOnce("NeoEssentials Economy reflection failed: ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    fun getBalance(uuid: UUID): Int = try {
        val mgr = manager() ?: return 0
        (getBalanceMethod!!.invoke(mgr, uuid) as BigDecimal).toInt()
    } catch (e: Throwable) {
        log.error("getBalance failed", e); 0
    }

    fun deposit(uuid: UUID, amount: Int) {
        if (amount <= 0) return
        try {
            val mgr = manager() ?: return
            addBalanceMethod!!.invoke(mgr, uuid, BigDecimal(amount))
        } catch (e: Throwable) {
            log.error("deposit failed", e)
        }
    }

    fun withdraw(uuid: UUID, amount: Int): Boolean {
        if (amount <= 0) return true
        return try {
            val mgr = manager() ?: return false
            subBalanceMethod!!.invoke(mgr, uuid, BigDecimal(amount)) as Boolean
        } catch (e: Throwable) {
            log.error("withdraw failed", e); false
        }
    }

    private fun warnOnce(msg: String) {
        if (warnedOnce.compareAndSet(false, true)) log.warn(msg)
    }
}
