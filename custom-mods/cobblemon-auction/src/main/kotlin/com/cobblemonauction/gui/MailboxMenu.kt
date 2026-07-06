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
 *
 * Rows 0-4 (slots 0-44) hold up to 45 entries per page; row 5 is nav: [45] prev, [49] Back, [53] next.
 */
object MailboxMenu {

    private const val ROWS = 6
    private const val SLOTS = ROWS * 9
    private const val FIRST_ITEM_SLOT = 0
    private const val PAGE_SIZE = 45           // rows 0-4
    private const val PREV_SLOT = 45
    private const val BACK_SLOT = 49
    private const val NEXT_SLOT = 53

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

    private fun pageCount(player: ServerPlayer): Int {
        val n = entriesFor(player).size
        return ((n + PAGE_SIZE - 1) / PAGE_SIZE).coerceAtLeast(1)
    }

    private fun populate(container: Container, player: ServerPlayer, page: Int) {
        for (i in 0 until container.containerSize) container.setItem(i, ItemStack.EMPTY)
        val registries = player.level().registryAccess()
        val all = entriesFor(player)
        val pages = pageCount(player)
        val start = (page * PAGE_SIZE).coerceAtMost(all.size)
        val slice = all.subList(start, (start + PAGE_SIZE).coerceAtMost(all.size))
        for ((index, entry) in slice.withIndex()) {
            val stack = ItemStacks.decode(entry.item, registries)
            val display = if (stack.isEmpty) Gui.button(Items.BARRIER, "§cUnavailable item") else stack
            container.setItem(FIRST_ITEM_SLOT + index, Gui.withLore(display, listOf(
                "§7${entry.note}",
                "",
                "§aLeft-click to collect",
            )))
        }
        if (page > 0) container.setItem(PREV_SLOT, Gui.button(Items.ARROW, "§aPrevious Page", "§7Page $page / $pages"))
        container.setItem(BACK_SLOT, Gui.button(Items.BARRIER, "§aBack", "§7Return to the Auction House."))
        if (page < pages - 1) container.setItem(NEXT_SLOT, Gui.button(Items.ARROW, "§aNext Page", "§7Page ${page + 2} / $pages"))
        container.setChanged()
    }

    private class Impl(
        syncId: Int,
        inv: Inventory,
        private val container: Container,
        private val viewer: ServerPlayer,
    ) : ChestMenu(MenuType.GENERIC_9x6, syncId, inv, container, ROWS) {

        private var page = 0

        init { populate(container, viewer, page) }

        override fun clicked(slotId: Int, button: Int, clickType: ClickType, player: Player) {
            if (clickType == ClickType.QUICK_CRAFT || clickType == ClickType.SWAP) return
            if (slotId !in 0 until SLOTS) { super.clicked(slotId, button, clickType, player); return }
            val sp = player as? ServerPlayer ?: return

            when (slotId) {
                BACK_SLOT -> { BrowseMenu.open(sp); return }
                PREV_SLOT, NEXT_SLOT -> { changePage(if (slotId == PREV_SLOT) -1 else +1); return }
            }
            if (slotId >= PAGE_SIZE) return

            val entry = entriesFor(sp).getOrNull(page * PAGE_SIZE + slotId) ?: return
            collect(sp, entry)
            // Collecting shrank the list — clamp the page so we don't strand the viewer past the end.
            page = page.coerceAtMost(pageCount(sp) - 1)
            populate(container, viewer, page)
            broadcastChanges()
        }

        override fun quickMoveStack(player: Player, slotIndex: Int): ItemStack = ItemStack.EMPTY

        private fun changePage(delta: Int) {
            val next = page + delta
            if (next in 0 until pageCount(viewer)) { page = next; populate(container, viewer, page); broadcastChanges() }
        }

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
