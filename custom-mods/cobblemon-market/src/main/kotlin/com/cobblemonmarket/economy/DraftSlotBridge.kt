package com.cobblemonmarket.economy

import org.slf4j.LoggerFactory
import java.lang.reflect.Method
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Reflection bridge to cobblemon-ranked's `DraftTeams`, used by the Upgrades vendor to sell
 * permanent draft-team slots (the custom-rental feature — see the ranked mod's
 * docs/rental-drafts-plan.md). Ranked owns the slot state and the price ladder
 * (`draftSlotCosts` in its config); this bridge only reads them and records a purchase.
 *
 * Same local-copy convention as [EconomyBridge]/[HomeUpgradeBridge]: no compile-time
 * dependency between the custom mods, and everything degrades to "unavailable" (null/false)
 * if cobblemon-ranked isn't loaded, so the shop keeps working without the upgrade item.
 */
object DraftSlotBridge {

    private val log = LoggerFactory.getLogger("cobblemon-market/draft-slot")
    private const val DRAFT_TEAMS_CLASS = "com.cobblemonranked.rental.DraftTeams"

    @Volatile private var ownedSlotsMethod: Method? = null
    @Volatile private var maxSlotsMethod: Method? = null
    @Volatile private var slotCostMethod: Method? = null
    @Volatile private var grantSlotMethod: Method? = null
    private val warnedOnce = AtomicBoolean(false)

    private fun resolve(): Boolean {
        if (grantSlotMethod != null) return true
        return try {
            // Kotlin object with @JvmStatic members — plain static methods on the class.
            val cls = Class.forName(DRAFT_TEAMS_CLASS)
            ownedSlotsMethod = cls.getMethod("ownedSlots", UUID::class.java)
            maxSlotsMethod = cls.getMethod("maxSlots")
            slotCostMethod = cls.getMethod("slotCost", Int::class.javaPrimitiveType)
            grantSlotMethod = cls.getMethod("grantSlot", UUID::class.java)
            true
        } catch (e: Throwable) {
            warnOnce("cobblemon-ranked DraftTeams reflection failed: ${e.javaClass.simpleName}: ${e.message}")
            false
        }
    }

    fun isAvailable(): Boolean = resolve()

    /** Slots the player permanently owns, or null if cobblemon-ranked is unavailable. */
    fun ownedSlots(player: UUID): Int? {
        if (!resolve()) return null
        return try {
            ownedSlotsMethod!!.invoke(null, player) as Int
        } catch (e: Throwable) {
            log.error("ownedSlots failed", e); null
        }
    }

    /** Operator-configured ceiling on unlockable slots, or null if unavailable. */
    fun maxSlots(): Int? {
        if (!resolve()) return null
        return try {
            maxSlotsMethod!!.invoke(null) as Int
        } catch (e: Throwable) {
            log.error("maxSlots failed", e); null
        }
    }

    /** One-time price of the next slot for a player who owns [ownedCount], or null. */
    fun slotCost(ownedCount: Int): Int? {
        if (!resolve()) return null
        return try {
            slotCostMethod!!.invoke(null, ownedCount) as Int
        } catch (e: Throwable) {
            log.error("slotCost failed", e); null
        }
    }

    /**
     * Records one purchased slot and returns the new owned count, or null on failure — callers
     * charge only after a non-null return, mirroring [HomeUpgradeBridge]'s grant-then-charge.
     */
    fun grantSlot(player: UUID): Int? {
        if (!resolve()) return null
        return try {
            grantSlotMethod!!.invoke(null, player) as Int
        } catch (e: Throwable) {
            log.error("grantSlot failed", e); null
        }
    }

    private fun warnOnce(msg: String) {
        if (warnedOnce.compareAndSet(false, true)) log.warn(msg)
    }
}
