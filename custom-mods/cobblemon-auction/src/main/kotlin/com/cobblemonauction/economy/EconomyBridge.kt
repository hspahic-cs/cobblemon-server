package com.cobblemonauction.economy

import org.slf4j.LoggerFactory
import java.lang.reflect.Method
import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Reflection bridge to NeoEssentials Economy — the active Vault economy provider on the server
 * (it backs `/balance`, `/pay`, `/eco`). Copied deliberately from cobblemon-market rather than
 * shared, to keep the mods dependency-free of each other.
 *
 * `EconomyManager.getInstance()` returns the singleton; `getBalance/addBalance/subtractBalance`
 * take a UUID + BigDecimal. Method handles cache after first resolution; if NeoEssentials is
 * absent everything degrades to a no-op (with `isAvailable()` reporting false) so the auction
 * house can fail closed on money operations instead of giving items away.
 */
object EconomyBridge {

    private val log = LoggerFactory.getLogger("cobblemon-auction/economy")
    private const val ECONOMY_CLASS = "com.zerog.neoessentials.economy.managers.EconomyManager"
    private const val M_GET_BALANCE = "getBalance"
    private const val M_ADD_BALANCE = "addBalance"
    private const val M_SUB_BALANCE = "subtractBalance"

    @Volatile private var resolvedManager: Any? = null
    @Volatile private var getBalanceMethod: Method? = null
    @Volatile private var addBalanceMethod: Method? = null
    @Volatile private var subBalanceMethod: Method? = null
    private val warnedOnce = AtomicBoolean(false)
    private val available = AtomicBoolean(false)

    private fun manager(): Any? {
        resolvedManager?.let { return it }
        return try {
            val cls = Class.forName(ECONOMY_CLASS)
            val mgr = cls.getMethod("getInstance").invoke(null)
            resolvedManager = mgr
            getBalanceMethod = mgr.javaClass.getMethod(M_GET_BALANCE, UUID::class.java)
            addBalanceMethod = mgr.javaClass.getMethod(M_ADD_BALANCE, UUID::class.java, BigDecimal::class.java)
            subBalanceMethod = mgr.javaClass.getMethod(M_SUB_BALANCE, UUID::class.java, BigDecimal::class.java)
            available.set(true)
            mgr
        } catch (e: ClassNotFoundException) {
            warnOnce("NeoEssentials Economy not loaded — auction currency operations disabled")
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
        log.error("EconomyBridge.getBalance failed", e); 0
    }

    /**
     * Credit [amount] to [uuid]. Returns true only if the credit actually happened. Returns FALSE
     * when the economy is unavailable or the reflected call throws, so callers that removed a
     * listing/request from escrow can roll it back instead of losing the money. Mirrors [withdraw].
     *
     * A non-positive amount is a no-op success (true). NeoEssentials' `addBalance` is symmetric with
     * `subtractBalance` and returns a Boolean, but we don't hard-depend on that: if the reflected
     * call returns a Boolean we honor it, otherwise a throw-free invocation counts as success.
     */
    fun deposit(uuid: UUID, amount: Int): Boolean {
        if (amount <= 0) return true
        return try {
            val mgr = manager() ?: return false
            val result = addBalanceMethod!!.invoke(mgr, uuid, BigDecimal(amount))
            (result as? Boolean) ?: true
        } catch (e: Throwable) {
            log.error("EconomyBridge.deposit failed", e); false
        }
    }

    /**
     * Debit [amount] from [uuid]. Returns true only if the debit actually happened. Returns
     * FALSE when economy is unavailable (fail closed) so callers never hand out goods for free.
     */
    fun withdraw(uuid: UUID, amount: Int): Boolean {
        if (amount <= 0) return true
        return try {
            val mgr = manager() ?: return false
            subBalanceMethod!!.invoke(mgr, uuid, BigDecimal(amount)) as Boolean
        } catch (e: Throwable) {
            log.error("EconomyBridge.withdraw failed", e); false
        }
    }

    fun isAvailable(): Boolean = available.get()

    private fun warnOnce(msg: String) {
        if (warnedOnce.compareAndSet(false, true)) {
            log.warn(msg)
        }
    }
}
