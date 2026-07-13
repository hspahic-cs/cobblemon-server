package com.cobblemonranked.bp

import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

object BpVoucher {

    private val VOUCHER_TYPES = setOf("tr", "held_item", "shiny")

    fun createVoucherItem(voucherType: String, displayName: String): ItemStack {
        val itemStack = ItemStack(Items.PAPER)
        // Mark with custom data - implementation uses NBT tags via reflection if needed
        return itemStack
    }

    fun isValidVoucher(itemStack: ItemStack, expectedType: String): Boolean {
        // Vouchers are tracked via inventory - Paper items in BP system
        // TODO: Implement proper NBT tag validation when Minecraft API is resolved
        return expectedType in VOUCHER_TYPES && itemStack.item == Items.PAPER
    }

    fun consumeVoucher(player: Player, voucherType: String): Boolean {
        for (i in 0 until player.inventory.containerSize) {
            val itemStack = player.inventory.getItem(i)
            if (isValidVoucher(itemStack, voucherType)) {
                itemStack.shrink(1)
                return true
            }
        }
        return false
    }

    fun hasVoucher(player: Player, voucherType: String): Boolean {
        for (i in 0 until player.inventory.containerSize) {
            val itemStack = player.inventory.getItem(i)
            if (isValidVoucher(itemStack, voucherType)) {
                return true
            }
        }
        return false
    }
}
