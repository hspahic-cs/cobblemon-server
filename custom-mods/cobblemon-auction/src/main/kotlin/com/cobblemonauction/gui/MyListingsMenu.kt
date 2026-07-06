package com.cobblemonauction.gui

import com.cobblemonauction.CobblemonAuction
import com.cobblemonauction.data.ItemStacks
import com.cobblemonauction.data.Listing
import com.cobblemonauction.service.AuctionService
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
 * The caller's own active listings. Left-click a listing to cancel it — the item is returned to
 * the seller's mailbox (never dropped in-world). Cancelling is safe (no money moves, no loss), so
 * it's single-click.
 *
 * Rows 0-4 (slots 0-44) hold up to 45 listings per page; row 5 is nav: [45] prev, [49] Back, [53] next.
 */
object MyListingsMenu {

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
            Component.literal("§0Your Listings"),
        )
        player.openMenu(provider)
    }

    private fun listingsFor(player: ServerPlayer): List<Listing> {
        // Hide listings in their final minute so the owner's view matches the browser: they vanish
        // a minute before nominal expiry (then land in the mailbox on the next sweep), never sitting
        // in a zeroed-out state. Filtering here keeps the click-index mapping aligned with populate.
        val now = System.currentTimeMillis()
        return CobblemonAuction.auctionStore.bySeller(player.uuid)
            .filter { AuctionService.effectiveExpiry(it) > now }
    }

    private fun pageCount(player: ServerPlayer): Int {
        val n = listingsFor(player).size
        return ((n + PAGE_SIZE - 1) / PAGE_SIZE).coerceAtLeast(1)
    }

    private fun populate(container: Container, player: ServerPlayer, page: Int) {
        for (i in 0 until container.containerSize) container.setItem(i, ItemStack.EMPTY)
        val now = System.currentTimeMillis()
        val registries = player.level().registryAccess()
        val all = listingsFor(player)
        val pages = pageCount(player)
        val start = (page * PAGE_SIZE).coerceAtMost(all.size)
        val slice = all.subList(start, (start + PAGE_SIZE).coerceAtMost(all.size))
        for ((index, listing) in slice.withIndex()) {
            val stack = ItemStacks.decode(listing.item, registries)
            val display = if (stack.isEmpty) Gui.button(Items.BARRIER, "§cUnavailable listing") else stack
            container.setItem(FIRST_ITEM_SLOT + index, Gui.withLore(display, listOf(
                "§ePrice: §f\$${listing.price} §7(for ${listing.count})",
                "§7Expires in: §f${Gui.timeLeft(AuctionService.effectiveExpiry(listing) - now)}",
                "",
                "§cLeft-click to cancel §7(returns to Mailbox)",
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

            val listing = listingsFor(sp).getOrNull(page * PAGE_SIZE + slotId) ?: return
            when (AuctionService.cancel(sp, listing.id)) {
                is AuctionService.CancelResult.Success ->
                    sp.sendSystemMessage(Component.literal(
                        "§a[AH] Cancelled listing for ${listing.count}× ${Gui.prettyItemName(listing.itemId)} — returned to your Mailbox."))
                AuctionService.CancelResult.Gone ->
                    sp.sendSystemMessage(Component.literal("§c[AH] That listing is no longer active."))
                AuctionService.CancelResult.NotOwner ->
                    sp.sendSystemMessage(Component.literal("§c[AH] That isn't your listing."))
            }
            page = page.coerceAtMost(pageCount(sp) - 1)
            populate(container, viewer, page)
            broadcastChanges()
        }

        override fun quickMoveStack(player: Player, slotIndex: Int): ItemStack = ItemStack.EMPTY

        private fun changePage(delta: Int) {
            val next = page + delta
            if (next in 0 until pageCount(viewer)) { page = next; populate(container, viewer, page); broadcastChanges() }
        }
    }
}
