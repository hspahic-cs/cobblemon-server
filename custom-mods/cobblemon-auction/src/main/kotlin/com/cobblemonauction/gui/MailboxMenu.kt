package com.cobblemonauction.gui

import com.cobblemonauction.CobblemonAuction
import com.cobblemonauction.data.ItemStacks
import com.cobblemonauction.data.MailEntry
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
 * A player's mailbox: stacks from purchases and returned listings. Left-click an entry to collect
 * it — it goes to your inventory, and any overflow is dropped at your feet (never lost). Delivery
 * is done programmatically, not via container item movement, so the menu stays fully server-driven.
 * Slot 49 is a Back button.
 */
object MailboxMenu {

    private const val ROWS = 6
    private const val SLOTS = ROWS * 9
    private const val FIRST_ITEM_SLOT = 0
    private const val ITEM_SLOTS = 45
    private const val BACK_SLOT = 49

    fun open(player: ServerPlayer) {
        val container = SimpleContainer(SLOTS)
        val provider = SimpleMenuProvider(
            { syncId, inv, _ -> Impl(syncId, inv, container, player) },
            Component.literal("§0Mailbox"),
        )
        player.openMenu(provider)
    }

    private fun entriesFor(player: ServerPlayer): List<MailEntry> =
        CobblemonAuction.mailboxStore.entries(player.uuid)

    private fun populate(container: Container, player: ServerPlayer) {
        for (i in 0 until container.containerSize) container.setItem(i, ItemStack.EMPTY)
        val registries = player.level().registryAccess()
        val entries = entriesFor(player).take(ITEM_SLOTS)
        for ((index, entry) in entries.withIndex()) {
            val stack = ItemStacks.decode(entry.item, registries)
            val display = if (stack.isEmpty) Gui.button(Items.BARRIER, "§cUnavailable item") else stack
            container.setItem(FIRST_ITEM_SLOT + index, Gui.withLore(display, listOf(
                "§7${entry.note}",
                "",
                "§aLeft-click to collect",
            )))
        }
        container.setItem(BACK_SLOT, Gui.button(Items.ARROW, "§aBack", "§7Return to the Auction House."))
        container.setChanged()
    }

    private class Impl(
        syncId: Int,
        inv: Inventory,
        private val container: Container,
        private val viewer: ServerPlayer,
    ) : ChestMenu(MenuType.GENERIC_9x6, syncId, inv, container, ROWS) {

        init { populate(container, viewer) }

        override fun clicked(slotId: Int, button: Int, clickType: ClickType, player: Player) {
            if (clickType == ClickType.QUICK_CRAFT || clickType == ClickType.SWAP) return
            if (slotId !in 0 until SLOTS) { super.clicked(slotId, button, clickType, player); return }
            val sp = player as? ServerPlayer ?: return

            if (slotId == BACK_SLOT) { BrowseMenu.open(sp); return }
            if (slotId >= ITEM_SLOTS) return

            val entry = entriesFor(sp).getOrNull(slotId) ?: return
            collect(sp, entry)
            populate(container, viewer)
            broadcastChanges()
        }

        override fun quickMoveStack(player: Player, slotIndex: Int): ItemStack = ItemStack.EMPTY

        private fun collect(sp: ServerPlayer, entry: MailEntry) {
            // Remove first so a delivery hiccup can't leave a claimable duplicate behind.
            val removed = CobblemonAuction.mailboxStore.remove(sp.uuid, entry.id) ?: return
            val stack = ItemStacks.decode(removed.item, sp.level().registryAccess())
            if (stack.isEmpty) {
                sp.sendSystemMessage(Component.literal("§c[AH] That mail item was corrupt and could not be delivered."))
                return
            }
            // Inventory.add mutates 'stack', shrinking it as it fits; drop whatever doesn't.
            if (!sp.inventory.add(stack) && !stack.isEmpty) sp.drop(stack, false)
            sp.sendSystemMessage(Component.literal(
                "§a[AH] Collected ${removed.count}× ${Gui.prettyItemName(removed.itemId)}."))
        }
    }
}
