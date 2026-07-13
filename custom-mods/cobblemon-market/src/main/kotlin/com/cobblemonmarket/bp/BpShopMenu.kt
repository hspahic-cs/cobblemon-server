package com.cobblemonmarket.bp

import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.nbt.CompoundTag
import org.slf4j.LoggerFactory
import java.util.UUID

object BpShopMenu {
    private val log = LoggerFactory.getLogger("cobblemon-market/bp")

    fun openBpShop(player: ServerPlayer) {
        val items = BpShopConfig.getAllItems()
        if (items.isEmpty()) {
            player.sendSystemMessage(Component.literal("§c[BP Shop] Shop is not configured."))
            return
        }

        val balance = BpBridge.getBalance(player.uuid)
        player.sendSystemMessage(Component.literal(
            "§6[BP Shop] Current balance: §f$balance BP"
        ))
        player.sendSystemMessage(Component.literal(
            "§7Type §f/bp list §7to see available items, or interact with the BP shop NPC to purchase."
        ))
    }

    fun purchaseItem(player: ServerPlayer, itemId: String): Boolean {
        val entry = BpShopConfig.getItem(itemId)
        if (entry == null) {
            player.sendSystemMessage(Component.literal("§c[BP Shop] Item not found: $itemId"))
            return false
        }

        val balance = BpBridge.getBalance(player.uuid)
        if (balance < entry.cost) {
            player.sendSystemMessage(Component.literal(
                "§c[BP Shop] Insufficient BP. Cost: §f${entry.cost}§c, Balance: §f$balance"
            ))
            return false
        }

        // Deduct BP
        if (!BpBridge.subtractBalance(player.uuid, entry.cost)) {
            player.sendSystemMessage(Component.literal("§c[BP Shop] Failed to deduct BP."))
            return false
        }

        // Give item
        val itemStack = when {
            entry.isVoucher -> createVoucherItem(entry)
            else -> createRegularItem(itemId, entry)
        }

        if (itemStack.isEmpty) {
            // Restore BP if item creation failed
            BpBridge.addBalance(player.uuid, entry.cost)
            player.sendSystemMessage(Component.literal("§c[BP Shop] Could not create item. BP refunded."))
            return false
        }

        player.inventory.add(itemStack)
        player.sendSystemMessage(Component.literal(
            "§a[BP Shop] Purchased §f${entry.displayName}§a for §f${entry.cost} BP. Balance: §f${BpBridge.getBalance(player.uuid)}"
        ))
        return true
    }

    private fun createVoucherItem(entry: BpItemEntry): ItemStack {
        // Placeholder - Task 5 will implement proper voucher items with NBT tags
        return ItemStack(Items.PAPER, 1)
    }

    private fun createRegularItem(itemId: String, entry: BpItemEntry): ItemStack {
        // Placeholder - in production this would map itemId to actual Minecraft/Cobblemon items
        return ItemStack(Items.PAPER, 1)
    }
}
