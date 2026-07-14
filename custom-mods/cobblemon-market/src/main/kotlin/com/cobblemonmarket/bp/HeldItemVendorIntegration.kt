package com.cobblemonmarket.bp

import net.minecraft.server.level.ServerPlayer

object HeldItemVendorIntegration {

    /**
     * Attempt to pay for a held item using a voucher. If the player holds a `held_item` voucher,
     * consume one and return true. Otherwise return false so the caller falls back to money.
     *
     * Usage: in held-item vendor purchase logic, before charging money:
     *   if (HeldItemVendorIntegration.tryPayWithVoucher(player)) { /* give item free */ return }
     */
    fun tryPayWithVoucher(player: ServerPlayer): Boolean = Vouchers.consume(player, "held_item")
}
