package com.cobblemonauction.gui

import com.cobblemonauction.CobblemonAuction
import com.cobblemonauction.data.ItemStacks
import com.cobblemonauction.data.Listing
import com.cobblemonauction.economy.EconomyBridge
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
 * The Auctioneer's main window: a paginated grid of every active listing. Buying is two-click
 * (arm, then confirm) so a stray click can't spend money. Row 0 is a nav/action bar; the listing
 * grid is read-only with respect to item movement (display copies can't be extracted).
 *
 * Row 0:  [0] prev  [2] Sell held  [4] balance  [6] My Listings  [7] Mailbox  [8] next
 * Rows 1-5: up to 45 listings, newest first.
 */
object BrowseMenu {

    private const val ROWS = 6
    private const val SLOTS = ROWS * 9
    private const val PREV_SLOT = 0
    private const val SELL_SLOT = 2
    private const val BALANCE_SLOT = 4
    private const val LISTINGS_SLOT = 6
    private const val MAILBOX_SLOT = 7
    private const val NEXT_SLOT = 8
    private const val FIRST_ITEM_SLOT = 9
    private const val PAGE_SIZE = SLOTS - FIRST_ITEM_SLOT   // 45

    fun open(player: ServerPlayer) {
        val container = SimpleContainer(SLOTS)
        val provider = SimpleMenuProvider(
            { syncId, inv, _ -> Impl(syncId, inv, container, player) },
            Component.literal("§0Auction House"),
        )
        player.openMenu(provider)
    }

    private fun activeListings(now: Long): List<Listing> =
        CobblemonAuction.auctionStore.all().filter { it.expiresAt > now }

    private fun pageCount(now: Long): Int {
        val n = activeListings(now).size
        return ((n + PAGE_SIZE - 1) / PAGE_SIZE).coerceAtLeast(1)
    }

    private fun populate(container: Container, player: ServerPlayer, page: Int, armedId: String?) {
        for (i in 0 until container.containerSize) container.setItem(i, ItemStack.EMPTY)

        val bal = EconomyBridge.getBalance(player.uuid)
        container.setItem(BALANCE_SLOT, Gui.button(
            Items.GOLD_INGOT, "§6Your Balance: §e\$$bal",
            "§7Left-click a listing to select it.",
            "§7Left-click again to confirm the purchase.",
        ))
        val cfg = CobblemonAuction.config
        val sellLore = mutableListOf(
            "§7Hold the item you want to sell,",
            "§7then click here to set a price.",
        )
        if (cfg.listingFeePercent > 0 || cfg.minListingFee > 0) {
            val pct = cfg.listingFeePercent
            val pctStr = if (pct % 1.0 == 0.0) pct.toInt().toString() else pct.toString()
            sellLore += ""
            sellLore += "§7Listing fee: §f$pctStr% §7(min §f\$${cfg.minListingFee}§7)"
            sellLore += "§8Refunded if the item sells."
        }
        container.setItem(SELL_SLOT, Gui.button(Items.EMERALD, "§aSell Held Item", *sellLore.toTypedArray()))
        val mailCount = CobblemonAuction.mailboxStore.count(player.uuid)
        container.setItem(MAILBOX_SLOT, Gui.button(
            Items.ENDER_CHEST, "§bMailbox§7 ($mailCount)",
            if (mailCount > 0) "§eYou have $mailCount item(s) to collect." else "§7Empty.",
        ))
        container.setItem(LISTINGS_SLOT, Gui.button(
            Items.WRITABLE_BOOK, "§eYour Listings",
            "§7View and cancel items you've listed.",
        ))

        val now = System.currentTimeMillis()
        val all = activeListings(now)
        val pages = pageCount(now)
        val start = page * PAGE_SIZE
        val slice = all.subList(start.coerceAtMost(all.size), (start + PAGE_SIZE).coerceAtMost(all.size))
        val registries = player.level().registryAccess()
        for ((index, listing) in slice.withIndex()) {
            container.setItem(FIRST_ITEM_SLOT + index, listingStack(listing, registries, now, armedId == listing.id))
        }

        if (page > 0) container.setItem(PREV_SLOT, Gui.button(Items.ARROW, "§aPrevious Page", "§7Page ${page} / $pages"))
        if (page < pages - 1) container.setItem(NEXT_SLOT, Gui.button(Items.ARROW, "§aNext Page", "§7Page ${page + 2} / $pages"))
        container.setChanged()
    }

    private fun listingStack(listing: Listing, registries: net.minecraft.core.HolderLookup.Provider, now: Long, armed: Boolean): ItemStack {
        val stack = ItemStacks.decode(listing.item, registries)
        if (stack.isEmpty) {
            return Gui.button(Items.BARRIER, "§cUnavailable listing", "§7This item couldn't be loaded.")
        }
        val lore = mutableListOf(
            "§ePrice: §f\$${listing.price} §7(for ${listing.count})",
            "§7Seller: §f${listing.sellerName}",
            "§7Expires in: §f${Gui.timeLeft(listing.expiresAt - now)}",
            "",
            if (armed) "§e§lClick again to confirm purchase" else "§aLeft-click to buy",
        )
        return Gui.withLore(stack, lore)
    }

    private class Impl(
        syncId: Int,
        inv: Inventory,
        private val container: Container,
        private val viewer: ServerPlayer,
    ) : ChestMenu(MenuType.GENERIC_9x6, syncId, inv, container, ROWS) {

        private var page = 0
        private var armedId: String? = null

        init { populate(container, viewer, page, armedId) }

        override fun clicked(slotId: Int, button: Int, clickType: ClickType, player: Player) {
            if (clickType == ClickType.QUICK_CRAFT || clickType == ClickType.SWAP) return
            if (slotId !in 0 until SLOTS) { super.clicked(slotId, button, clickType, player); return }
            val sp = player as? ServerPlayer ?: return

            when (slotId) {
                BALANCE_SLOT -> return
                SELL_SLOT -> { startSell(sp); return }
                LISTINGS_SLOT -> { MyListingsMenu.open(sp); return }
                MAILBOX_SLOT -> { MailboxMenu.open(sp); return }
                PREV_SLOT, NEXT_SLOT -> { changePage(if (slotId == PREV_SLOT) -1 else +1); return }
            }
            if (slotId < FIRST_ITEM_SLOT) return

            val now = System.currentTimeMillis()
            val all = activeListings(now)
            val idx = page * PAGE_SIZE + (slotId - FIRST_ITEM_SLOT)
            val listing = all.getOrNull(idx) ?: return

            if (armedId == listing.id) {
                armedId = null
                reportBuy(sp, listing, AuctionService.buy(sp, listing.id))
            } else {
                armedId = listing.id
                sp.sendSystemMessage(Component.literal(
                    "§e[AH] Click again to confirm buying ${listing.count}× ${Gui.prettyItemName(listing.itemId)} for \$${listing.price}."
                ))
            }
            refresh()
        }

        // Read-only grid: never let players pull display copies out via shift-click.
        override fun quickMoveStack(player: Player, slotIndex: Int): ItemStack = ItemStack.EMPTY

        private fun startSell(sp: ServerPlayer) {
            when (val r = AuctionService.beginSell(sp)) {
                is AuctionService.BeginResult.Ready -> PriceAnvilMenu.open(sp)
                AuctionService.BeginResult.NoItemInHand ->
                    sp.sendSystemMessage(Component.literal("§c[AH] Hold the item you want to sell in your main hand, then click Sell."))
                is AuctionService.BeginResult.Blocked ->
                    sp.sendSystemMessage(Component.literal("§c[AH] ${Gui.prettyItemName(r.itemId)} can't be listed on the market."))
                is AuctionService.BeginResult.TooManyListings ->
                    sp.sendSystemMessage(Component.literal("§c[AH] You already have the maximum ${r.max} active listings."))
                AuctionService.BeginResult.AlreadySelling ->
                    sp.sendSystemMessage(Component.literal("§c[AH] Finish setting a price for your current item first."))
            }
        }

        private fun changePage(delta: Int) {
            val pages = pageCount(System.currentTimeMillis())
            val next = page + delta
            if (next in 0 until pages) { page = next; refresh() }
        }

        private fun refresh() {
            populate(container, viewer, page, armedId)
            broadcastChanges()
        }

        private fun reportBuy(sp: ServerPlayer, listing: Listing, result: AuctionService.BuyResult) {
            val name = Gui.prettyItemName(listing.itemId)
            val msg = when (result) {
                is AuctionService.BuyResult.Success ->
                    "§a[AH] Bought ${listing.count}× $name for \$${listing.price}. Collect it from your Mailbox."
                AuctionService.BuyResult.Gone -> "§c[AH] That listing is no longer available."
                AuctionService.BuyResult.OwnListing -> "§c[AH] You can't buy your own listing — cancel it from Your Listings."
                is AuctionService.BuyResult.InsufficientBalance ->
                    "§c[AH] You need \$${result.need} but only have \$${result.have}."
                AuctionService.BuyResult.EconomyUnavailable -> "§c[AH] The economy is unavailable right now — try again later."
            }
            sp.sendSystemMessage(Component.literal(msg))
        }
    }
}
