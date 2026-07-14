package com.cobblemonmarket.bp

import com.cobblemonmarket.CobblemonMarket
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.Container
import net.minecraft.world.SimpleContainer
import net.minecraft.world.SimpleMenuProvider
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.inventory.ClickType
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.ItemLore

/**
 * Right-click-on-NPC chest GUI for the Battle Points shop. Layout mirrors [com.cobblemonmarket.gui.MarketMenu]'s
 * scoped-vendor path but pays in BP instead of money:
 *
 *   Row 0: BP balance in the top-right corner (slot 8).
 *   Rows 1-5: up to 45 shop items from `config/bp-items.json`, in file order.
 *
 * Left-click an item to buy one. The chest is read-only w.r.t. item movement — drags, number-key
 * swaps, and shift/double clicks are dropped so players can't lift the display copies.
 */
object BpShopMenu {

    private const val ROWS = 6
    private const val SLOTS = ROWS * 9
    private const val BALANCE_SLOT = 8
    private const val FIRST_ITEM_SLOT = 9
    private const val PAGE_SIZE = SLOTS - FIRST_ITEM_SLOT   // 45

    private fun line(s: String): Component =
        Component.literal(s).setStyle(Style.EMPTY.withItalic(false))

    fun openBpShop(player: ServerPlayer) {
        val items = BpShopConfig.getAllItems()
        if (items.isEmpty()) {
            player.sendSystemMessage(Component.literal("§c[BP Shop] Shop is not configured (bp-items.json missing or empty)."))
            return
        }
        val container = SimpleContainer(SLOTS)
        populate(container, player, items)
        val provider = SimpleMenuProvider(
            { syncId, inv, _ -> Impl(syncId, inv, container, player, items) },
            Component.literal("§0Battle Points Shop"),
        )
        player.openMenu(provider)
    }

    private fun populate(container: Container, player: ServerPlayer, items: List<BpItemEntry>) {
        for (i in 0 until container.containerSize) container.setItem(i, ItemStack.EMPTY)
        container.setItem(BALANCE_SLOT, balanceStack(player))
        val slice = items.take(PAGE_SIZE)
        for ((index, entry) in slice.withIndex()) {
            container.setItem(FIRST_ITEM_SLOT + index, BpItems.displayStack(entry))
        }
        container.setChanged()
    }

    private fun balanceStack(player: ServerPlayer): ItemStack {
        val bal = BpBridge.getBalance(player.uuid)
        val stack = ItemStack(Items.HEART_OF_THE_SEA)
        stack.set(DataComponents.CUSTOM_NAME, line("§b§lYour Battle Points: §f$bal BP"))
        stack.set(DataComponents.LORE, ItemLore(listOf(
            line("§7Left-click an item to buy it."),
            line("§7Earn BP from tournaments."),
        )))
        return stack
    }

    private class Impl(
        syncId: Int,
        inv: Inventory,
        private val container: Container,
        private val viewer: ServerPlayer,
        private val items: List<BpItemEntry>,
    ) : ChestMenu(MenuType.GENERIC_9x6, syncId, inv, container, ROWS) {

        override fun clicked(slotId: Int, button: Int, clickType: ClickType, player: Player) {
            // Block anything that could extract the display copies.
            if (clickType == ClickType.QUICK_CRAFT || clickType == ClickType.SWAP ||
                clickType == ClickType.PICKUP_ALL) return
            if (slotId !in 0 until SLOTS) {
                super.clicked(slotId, button, clickType, player)
                return
            }
            if (slotId < FIRST_ITEM_SLOT) return   // nav row / balance — inert

            // Only a plain left-click buys; ignore everything else so it can't double-fire.
            if (button != 0 || (clickType != ClickType.PICKUP && clickType != ClickType.QUICK_MOVE)) return

            val sp = player as? ServerPlayer ?: return
            val index = slotId - FIRST_ITEM_SLOT
            if (index !in items.indices) return
            purchase(sp, items[index])
            populate(container, viewer, items)
            broadcastChanges()
        }

        private fun purchase(player: ServerPlayer, entry: BpItemEntry) {
            val balance = BpBridge.getBalance(player.uuid)
            if (balance < entry.cost) {
                player.sendSystemMessage(Component.literal(
                    "§c[BP Shop] Not enough BP for §f${entry.displayName}§c — costs §f${entry.cost}§c, you have §f$balance§c."))
                return
            }
            // Deduct first, then deliver; refund if delivery fails so BP is never lost silently.
            if (!BpBridge.subtractBalance(player.uuid, entry.cost)) {
                player.sendSystemMessage(Component.literal("§c[BP Shop] Could not deduct BP. Try again."))
                return
            }
            val delivered = try {
                BpItems.grant(player, entry)
            } catch (t: Throwable) {
                CobblemonMarket.logger.error("BP shop: grant threw for ${entry.id}", t)
                false
            }
            if (!delivered) {
                BpBridge.addBalance(player.uuid, entry.cost)
                player.sendSystemMessage(Component.literal("§c[BP Shop] Couldn't deliver §f${entry.displayName}§c. BP refunded."))
                return
            }
            player.sendSystemMessage(Component.literal(
                "§a[BP Shop] Bought §f${entry.displayName}§a for §b${entry.cost} BP§a. Balance: §f${BpBridge.getBalance(player.uuid)} BP"))
        }
    }
}
