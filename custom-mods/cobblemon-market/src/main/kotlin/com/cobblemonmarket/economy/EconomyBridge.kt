package com.cobblemonmarket.economy

import org.slf4j.LoggerFactory
import java.lang.reflect.Method
import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Reflection bridge to NeoEssentials Economy — the active Vault economy provider on the
 * server. Replaces Cobblemon Economy (Fabric mod loaded via Sinytra Connector) which never
 * actually loaded on dev because Connector beta.14 was rejected by NeoForge at scan time;
 * the symptom was every `deposit` / `withdraw` silently no-op'ing. NeoEssentials backs
 * `/balance`, `/pay`, `/eco`, etc. so its balances are the only ones that have ever existed
 * on this server. See 0.7.8 release notes.
 *
 * `EconomyManager.getInstance()` returns the singleton; `getBalance(UUID)`,
 * `addBalance(UUID, BigDecimal)`, `subtractBalance(UUID, BigDecimal)` mirror what we used to
 * call on CE — same shapes, just a different singleton + FQN.
 *
 * Method handles cache after first successful resolution; failures degrade silently to
 * no-ops with a single warning so the rest of the mod keeps running when NeoEssentials is
 * absent (e.g. running with a different economy provider).
 */
object EconomyBridge {

    private val log = LoggerFactory.getLogger("cobblemon-market/economy")
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
            warnOnce("NeoEssentials Economy not loaded — currency operations disabled")
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

    fun deposit(uuid: UUID, amount: Int) {
        if (amount <= 0) return
        try {
            val mgr = manager() ?: return
            addBalanceMethod!!.invoke(mgr, uuid, BigDecimal(amount))
        } catch (e: Throwable) {
            log.error("EconomyBridge.deposit failed", e)
        }
    }

    fun withdraw(uuid: UUID, amount: Int): Boolean {
        if (amount <= 0) return true
        return try {
            val mgr = manager() ?: return false
            subBalanceMethod!!.invoke(mgr, uuid, BigDecimal(amount)) as Boolean
        } catch (e: Throwable) {
            log.error("EconomyBridge.withdraw failed", e); false
        }
    }

    /**
     * Returns the top [limit] balances as `(uuid, balance)` pairs, sorted by balance
     * descending. NeoEssentials doesn't expose a `getTopBalance(N)` method like CE did, but
     * `getAllBalances(): Map<UUID, BigDecimal>` returns every persisted balance (online and
     * offline) — we sort + truncate client-side. Cost is fine: balance count is bounded by
     * player count, and `/market leaderboard` is human-paced.
     *
     * Returns an empty list when NeoEssentials is not loaded.
     */
    fun getTopBalance(limit: Int): List<Pair<UUID, Int>> = try {
        val mgr = manager() ?: return emptyList()
        val method = mgr.javaClass.getMethod("getAllBalances")
        @Suppress("UNCHECKED_CAST")
        val raw = method.invoke(mgr) as Map<UUID, BigDecimal>
        raw.entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { it.key to it.value.toInt() }
    } catch (e: Throwable) {
        log.error("EconomyBridge.getTopBalance failed", e); emptyList()
    }

    fun isAvailable(): Boolean = available.get()

    private fun warnOnce(msg: String) {
        if (warnedOnce.compareAndSet(false, true)) {
            log.warn(msg)
        }
    }
}
