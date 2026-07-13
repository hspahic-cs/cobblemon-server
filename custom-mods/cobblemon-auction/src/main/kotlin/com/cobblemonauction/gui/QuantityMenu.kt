package com.cobblemonauction.gui

import com.cobblemonauction.service.RequestService
import net.minecraft.network.chat.Component
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

/**
 * Quantity stepper for the create-request flow. Carries the chosen [itemId]; +/- buttons adjust a
 * count clamped to `[1, item.maxStackSize]` (held-hand fulfillment takes the whole count from one
 * main-hand stack, so the item's own stack size is the ceiling). Confirm forwards `(itemId, count)`
 * to the [PriceAnvilMenu] as a [PriceIntent.CreateRequest] — no persistent escrow, so backing out
 * costs nothing.
 *
 * Row 1: [10] -64  [11] -8  [12] -1  [13] item×count  [14] +1  [15] +8  [16] +64
 * Row 2: [18] Back   [22] Confirm
 */
object QuantityMenu {

    private const val ROWS = 3
    private const val SLOTS = ROWS * 9
    private const val MINUS_64 = 10
    private const val MINUS_8 = 11
    private const val MINUS_1 = 12
    private const val DISPLAY = 13
    private const val PLUS_1 = 14
    private const val PLUS_8 = 15
    private const val PLUS_64 = 16
    private const val BACK_SLOT = 18
    private const val CONFIRM_SLOT = 22

    fun open(player: ServerPlayer, itemId: String) {
        val container = SimpleContainer(SLOTS)
        val provider = SimpleMenuProvider(
            { syncId, inv, _ -> Impl(syncId, inv, container, player, itemId) },
            Component.literal("§0How many?"),
        )
        player.openMenu(provider)
    }

    private class Impl(
        syncId: Int,
        inv: Inventory,
        private val container: Container,
        private val viewer: ServerPlayer,
        private val itemId: String,
    ) : ChestMenu(MenuType.GENERIC_9x3, syncId, inv, container, ROWS) {

        private val maxStack = RequestService.maxStackFor(itemId).coerceAtLeast(1)
        private var count = 1

        init { populate() }

        override fun clicked(slotId: Int, button: Int, clickType: ClickType, player: Player) {
            if (clickType == ClickType.QUICK_CRAFT || clickType == ClickType.SWAP) return
            if (slotId !in 0 until SLOTS) { super.clicked(slotId, button, clickType, player); return }
            val sp = player as? ServerPlayer ?: return

            when (slotId) {
                MINUS_64 -> adjust(-64)
                MINUS_8 -> adjust(-8)
                MINUS_1 -> adjust(-1)
                PLUS_1 -> adjust(+1)
                PLUS_8 -> adjust(+8)
                PLUS_64 -> adjust(+64)
                BACK_SLOT -> CatalogMenu.open(sp)
                CONFIRM_SLOT -> PriceAnvilMenu.open(sp, PriceIntent.CreateRequest(itemId, count))
            }
        }

        override fun quickMoveStack(player: Player, slotIndex: Int): ItemStack = ItemStack.EMPTY

        private fun adjust(delta: Int) {
            val next = (count + delta).coerceIn(1, maxStack)
            if (next != count) { count = next; populate(); broadcastChanges() }
        }

        private fun populate() {
            for (i in 0 until container.containerSize) container.setItem(i, ItemStack.EMPTY)

            container.setItem(MINUS_64, Gui.button(Items.RED_STAINED_GLASS_PANE, "§c-64"))
            container.setItem(MINUS_8, Gui.button(Items.RED_STAINED_GLASS_PANE, "§c-8"))
            container.setItem(MINUS_1, Gui.button(Items.RED_STAINED_GLASS_PANE, "§c-1"))
            container.setItem(DISPLAY, Gui.withLore(Gui.itemStack(itemId, count), listOf(
                "§7Requesting: §f$count §7(max §f$maxStack§7)",
            )))
            container.setItem(PLUS_1, Gui.button(Items.GREEN_STAINED_GLASS_PANE, "§a+1"))
            container.setItem(PLUS_8, Gui.button(Items.GREEN_STAINED_GLASS_PANE, "§a+8"))
            container.setItem(PLUS_64, Gui.button(Items.GREEN_STAINED_GLASS_PANE, "§a+64"))
            container.setItem(BACK_SLOT, Gui.button(Items.BARRIER, "§aBack", "§7Return to the catalog."))
            container.setItem(CONFIRM_SLOT, Gui.button(Items.EMERALD, "§a§lSet a price →",
                "§7Request §f$count× ${Gui.prettyItemName(itemId)}§7.",
                "§7Click to enter the price you'll pay.",
            ))
            container.setChanged()
        }
    }
}
