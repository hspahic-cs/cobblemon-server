package com.cobblemonmarket.bp

import net.minecraft.server.level.ServerPlayer

object TrVendorIntegration {

    /**
     * Attempt to pay for a TR using a voucher. If the player holds a `tr` voucher, consume one and
     * return true. Otherwise return false so the caller falls back to money.
     *
     * Usage: in TR vendor purchase logic, before charging money:
     *   if (TrVendorIntegration.tryPayWithVoucher(player)) { /* give the TR free */ return }
     */
    fun tryPayWithVoucher(player: ServerPlayer): Boolean = Vouchers.consume(player, "tr")
}
