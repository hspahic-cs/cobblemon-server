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
 * it's single-click. Slot 49 is a Back button to the browser.
 */
object MyListingsMenu {

    private const val ROWS = 6
    private const val SLOTS = ROWS * 9
    private const val FIRST_ITEM_SLOT = 0
    private const val ITEM_SLOTS = 45          // rows 0-4
    private const val BACK_SLOT = 49           // row 5 center

    fun open(player: ServerPlayer) {
        val container = SimpleContainer(SLOTS)
        val provider = SimpleMenuProvider(
            { syncId, inv, _ -> Impl(syncId, inv, container, player) },
            Component.literal("§0Your Listings"),
        )
        player.openMenu(provider)
    }

    private fun listingsFor(player: ServerPlayer): List<Listing> =
        CobblemonAuction.auctionStore.bySeller(player.uuid)

    private fun populate(container: Container, player: ServerPlayer) {
        for (i in 0 until container.containerSize) container.setItem(i, ItemStack.EMPTY)
        val now = System.currentTimeMillis()
        val registries = player.level().registryAccess()
        val listings = listingsFor(player).take(ITEM_SLOTS)
        for ((index, listing) in listings.withIndex()) {
            val stack = ItemStacks.decode(listing.item, registries)
            val display = if (stack.isEmpty) Gui.button(Items.BARRIER, "§cUnavailable listing") else stack
            container.setItem(FIRST_ITEM_SLOT + index, Gui.withLore(display, listOf(
                "§ePrice: §f\$${listing.price} §7(for ${listing.count})",
                "§7Expires in: §f${Gui.timeLeft(listing.expiresAt - now)}",
                "",
                "§cLeft-click to cancel §7(returns to Mailbox)",
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

            val listing = listingsFor(sp).getOrNull(slotId) ?: return
            when (val r = AuctionService.cancel(sp, listing.id)) {
                is AuctionService.CancelResult.Success ->
                    sp.sendSystemMessage(Component.literal(
                        "§a[AH] Cancelled listing for ${listing.count}× ${Gui.prettyItemName(listing.itemId)} — returned to your Mailbox."))
                AuctionService.CancelResult.Gone ->
                    sp.sendSystemMessage(Component.literal("§c[AH] That listing is no longer active."))
                AuctionService.CancelResult.NotOwner ->
                    sp.sendSystemMessage(Component.literal("§c[AH] That isn't your listing."))
            }
            populate(container, viewer)
            broadcastChanges()
        }

        override fun quickMoveStack(player: Player, slotIndex: Int): ItemStack = ItemStack.EMPTY
    }
}
