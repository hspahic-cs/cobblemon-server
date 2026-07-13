package com.cobblemonmarket.bp

import net.minecraft.server.level.ServerPlayer
import org.slf4j.LoggerFactory

object TrVendorIntegration {
    private val log = LoggerFactory.getLogger("cobblemon-market/bp-vendor-tr")

    /**
     * Attempt to pay for a TR using a voucher. If player has tr_voucher, consume it
     * and return true. Otherwise return false so caller falls back to currency payment.
     *
     * Usage: In TR vendor purchase logic, add before currency deduction:
     *   if (TrVendorIntegration.tryPayWithVoucher(player as ServerPlayer)) {
     *       // Voucher was consumed, give the TR
     *       return true
     *   }
     *   // Otherwise fall back to currency payment
     */
    fun tryPayWithVoucher(player: ServerPlayer): Boolean {
        return try {
            val cls = Class.forName("com.cobblemonranked.bp.BpVoucher")
            val hasMethod = cls.getMethod("hasVoucher", net.minecraft.world.entity.player.Player::class.java, String::class.java)
            val consumeMethod = cls.getMethod("consumeVoucher", net.minecraft.world.entity.player.Player::class.java, String::class.java)

            val hasVoucher = hasMethod.invoke(null, player, "tr") as? Boolean ?: false
            if (hasVoucher) {
                consumeMethod.invoke(null, player, "tr") as? Boolean ?: false
            } else false
        } catch (e: Exception) {
            log.debug("BpVoucher not available or error: {}", e.message)
            false
        }
    }
}
