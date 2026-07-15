package com.cobblemonauction.gui

import com.cobblemonauction.CobblemonAuction
import com.cobblemonauction.data.ItemStacks
import com.cobblemonauction.data.Listing
import com.cobblemonauction.data.Request
import com.cobblemonauction.economy.EconomyBridge
import com.cobblemonauction.service.AuctionService
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
 * The Auctioneer's main window: a two-tab market. **For Sale** is the paginated grid of active
 * listings (buy / cancel-own); **Wanted** is the grid of active requests (fulfill by holding the
 * item / cancel-own). Both actions are two-click (arm, then confirm) so a stray click can't move
 * money. The grid is read-only w.r.t. item movement (display copies can't be extracted).
 *
 * Row 0:  [0] prev  [1] For Sale  [2] Wanted  [4] balance  [5] context action
 *         [6] Your Listings/Requests  [7] Mailbox  [8] next
 * Rows 1-5: up to 45 entries for the active tab, newest first.
 */
object BrowseMenu {

    enum class Tab { FOR_SALE, WANTED }

    private const val ROWS = 6
    private const val SLOTS = ROWS * 9
    private const val PREV_SLOT = 0
    private const val FORSALE_TAB = 1
    private const val WANTED_TAB = 2
    private const val BALANCE_SLOT = 4
    private const val ACTION_SLOT = 5
    private const val MINE_SLOT = 6
    private const val MAILBOX_SLOT = 7
    private const val NEXT_SLOT = 8
    private const val FIRST_ITEM_SLOT = 9
    private const val PAGE_SIZE = SLOTS - FIRST_ITEM_SLOT   // 45

    fun open(player: ServerPlayer) = open(player, Tab.FOR_SALE)

    fun open(player: ServerPlayer, tab: Tab) {
        val container = SimpleContainer(SLOTS)
        val provider = SimpleMenuProvider(
            { syncId, inv, _ -> Impl(syncId, inv, container, player, tab) },
            Component.literal("§0Auction House"),
        )
        player.openMenu(provider)
    }

    private fun activeListings(now: Long): List<Listing> =
        CobblemonAuction.auctionStore.all().filter { AuctionService.effectiveExpiry(it) > now }

    private fun activeRequests(now: Long): List<Request> =
        CobblemonAuction.requestStore.all().filter { RequestService.effectiveExpiry(it) > now }

    private fun pageCount(tab: Tab, now: Long): Int {
        val n = if (tab == Tab.FOR_SALE) activeListings(now).size else activeRequests(now).size
        return ((n + PAGE_SIZE - 1) / PAGE_SIZE).coerceAtLeast(1)
    }

    private fun populate(container: Container, player: ServerPlayer, tab: Tab, page: Int, armedId: String?) {
        for (i in 0 until container.containerSize) container.setItem(i, ItemStack.EMPTY)

        val now = System.currentTimeMillis()
        val cfg = CobblemonAuction.config

        // --- Row 0: tabs + nav + context action ---
        container.setItem(FORSALE_TAB, Gui.button(
            if (tab == Tab.FOR_SALE) Items.EMERALD_BLOCK else Items.EMERALD,
            if (tab == Tab.FOR_SALE) "§a§lFor Sale" else "§eFor Sale",
            "§7Items other players are selling.",
        ))
        container.setItem(WANTED_TAB, Gui.button(
            if (tab == Tab.WANTED) Items.GOLD_BLOCK else Items.GOLD_INGOT,
            if (tab == Tab.WANTED) "§6§lWanted" else "§eWanted",
            "§7Buy orders you can fill for cash.",
        ))
        val bal = EconomyBridge.getBalance(player.uuid)
        container.setItem(BALANCE_SLOT, Gui.button(
            Items.GOLD_INGOT, "§6Your Balance: §e\$$bal",
            "§7Left-click an entry to select it.",
            "§7Left-click again to confirm.",
        ))

        if (tab == Tab.FOR_SALE) {
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
            container.setItem(ACTION_SLOT, Gui.button(Items.WRITABLE_BOOK, "§aSell Held Item", *sellLore.toTypedArray()))
            container.setItem(MINE_SLOT, Gui.button(
                Items.BOOK, "§eYour Listings", "§7View and cancel items you've listed."))
        } else {
            container.setItem(ACTION_SLOT, Gui.button(
                Items.WRITABLE_BOOK, "§aCreate Request",
                "§7Post a buy order for an item.",
                "§7You pay up front; a seller fills it.",
            ))
            container.setItem(MINE_SLOT, Gui.button(
                Items.BOOK, "§eYour Requests", "§7View and cancel your buy orders."))
        }

        val mailCount = CobblemonAuction.mailboxStore.count(player.uuid)
        container.setItem(MAILBOX_SLOT, Gui.button(
            Items.ENDER_CHEST, "§bMailbox§7 ($mailCount)",
            if (mailCount > 0) "§eYou have $mailCount item(s) to collect." else "§7Empty.",
        ))

        // --- Grid ---
        val pages = pageCount(tab, now)
        val start = page * PAGE_SIZE
        val ownUuid = player.uuid.toString()
        val registries = player.level().registryAccess()
        if (tab == Tab.FOR_SALE) {
            val all = activeListings(now)
            val slice = all.subList(start.coerceAtMost(all.size), (start + PAGE_SIZE).coerceAtMost(all.size))
            for ((index, listing) in slice.withIndex()) {
                container.setItem(FIRST_ITEM_SLOT + index,
                    listingStack(listing, registries, now, armedId == listing.id, listing.sellerUuid == ownUuid))
            }
        } else {
            val all = activeRequests(now)
            val slice = all.subList(start.coerceAtMost(all.size), (start + PAGE_SIZE).coerceAtMost(all.size))
            for ((index, request) in slice.withIndex()) {
                container.setItem(FIRST_ITEM_SLOT + index,
                    requestStack(request, now, armedId == request.id, request.requesterUuid == ownUuid))
            }
        }

        if (page > 0) container.setItem(PREV_SLOT, Gui.button(Items.ARROW, "§aPrevious Page", "§7Page ${page} / $pages"))
        if (page < pages - 1) container.setItem(NEXT_SLOT, Gui.button(Items.ARROW, "§aNext Page", "§7Page ${page + 2} / $pages"))
        container.setChanged()
    }

    private fun listingStack(listing: Listing, registries: net.minecraft.core.HolderLookup.Provider, now: Long, armed: Boolean, own: Boolean): ItemStack {
        val stack = ItemStacks.decode(listing.item, registries)
        if (stack.isEmpty) {
            return Gui.button(Items.BARRIER, "§cUnavailable listing", "§7This item couldn't be loaded.")
        }
        val action = when {
            own && armed -> "§c§lClick again to CANCEL this listing"
            own -> "§eYour listing §7— click to cancel (to Mailbox)"
            armed -> "§e§lClick again to confirm purchase"
            else -> "§aLeft-click to buy"
        }
        val lore = mutableListOf(
            "§ePrice: §f\$${listing.price} §7(for ${listing.count})",
            "§7Seller: §f${listing.sellerName}",
            "§7Expires in: §f${Gui.timeLeft(AuctionService.effectiveExpiry(listing) - now)}",
            "",
            action,
        )
        return Gui.withLore(stack, lore)
    }

    private fun requestStack(request: Request, now: Long, armed: Boolean, own: Boolean): ItemStack {
        val action = when {
            own && armed -> "§c§lClick again to CANCEL this request"
            own -> "§eYour request §7— click to cancel (refunds \$${request.price})"
            armed -> "§e§lClick again to fill it §7(from your main hand)"
            else -> "§aHold the item & left-click to fill"
        }
        val lore = mutableListOf(
            "§eWants: §f${request.count}×",
            "§ePays: §f\$${request.price}",
            "§7Requester: §f${request.requesterName}",
            "§7Expires in: §f${Gui.timeLeft(RequestService.effectiveExpiry(request) - now)}",
            "",
            action,
        )
        return Gui.withLore(Gui.itemStack(request.itemId, request.count), lore)
    }

    private class Impl(
        syncId: Int,
        inv: Inventory,
        private val container: Container,
        private val viewer: ServerPlayer,
        private var tab: Tab,
    ) : ChestMenu(MenuType.GENERIC_9x6, syncId, inv, container, ROWS) {

        private var page = 0
        private var armedId: String? = null

        init { populate(container, viewer, tab, page, armedId) }

        override fun clicked(slotId: Int, button: Int, clickType: ClickType, player: Player) {
            if (clickType == ClickType.QUICK_CRAFT || clickType == ClickType.SWAP) return
            if (slotId !in 0 until SLOTS) { super.clicked(slotId, button, clickType, player); return }
            val sp = player as? ServerPlayer ?: return

            when (slotId) {
                BALANCE_SLOT -> return
                FORSALE_TAB -> { switchTab(Tab.FOR_SALE); return }
                WANTED_TAB -> { switchTab(Tab.WANTED); return }
                ACTION_SLOT -> { onAction(sp); return }
                MINE_SLOT -> { if (tab == Tab.FOR_SALE) MyListingsMenu.open(sp) else MyRequestsMenu.open(sp); return }
                MAILBOX_SLOT -> { MailboxMenu.open(sp); return }
                PREV_SLOT, NEXT_SLOT -> { changePage(if (slotId == PREV_SLOT) -1 else +1); return }
            }
            if (slotId < FIRST_ITEM_SLOT) return

            val gridIndex = page * PAGE_SIZE + (slotId - FIRST_ITEM_SLOT)
            if (tab == Tab.FOR_SALE) onListingClick(sp, gridIndex) else onRequestClick(sp, gridIndex)
        }

        // Read-only grid: never let players pull display copies out via shift-click.
        override fun quickMoveStack(player: Player, slotIndex: Int): ItemStack = ItemStack.EMPTY

        private fun switchTab(next: Tab) {
            if (next == tab) return
            // An armed selection never carries across tabs (the index space differs); pagination is per-tab.
            tab = next
            page = 0
            armedId = null
            refresh()
        }

        private fun onAction(sp: ServerPlayer) {
            // Create Request goes straight to the item search (no "suggested" catalog landing).
            if (tab == Tab.FOR_SALE) startSell(sp) else SearchAnvilMenu.open(sp)
        }

        private fun onListingClick(sp: ServerPlayer, gridIndex: Int) {
            // Re-resolve the armed index against the CURRENT live list so a shifted list can't action
            // the wrong row.
            val listing = activeListings(System.currentTimeMillis()).getOrNull(gridIndex) ?: return
            val own = listing.sellerUuid == sp.uuid.toString()
            if (armedId == listing.id) {
                armedId = null
                if (own) reportCancel(sp, listing, AuctionService.cancel(sp, listing.id))
                else reportBuy(sp, listing, AuctionService.buy(sp, listing.id))
            } else {
                armedId = listing.id
                val name = Gui.prettyItemName(listing.itemId)
                sp.sendSystemMessage(Component.literal(
                    if (own) "§e[AH] Click again to CANCEL your listing of ${listing.count}× $name — it returns to your Mailbox."
                    else "§e[AH] Click again to confirm buying ${listing.count}× $name for \$${listing.price}."
                ))
            }
            refresh()
        }

        private fun onRequestClick(sp: ServerPlayer, gridIndex: Int) {
            val request = activeRequests(System.currentTimeMillis()).getOrNull(gridIndex) ?: return
            val own = request.requesterUuid == sp.uuid.toString()
            if (armedId == request.id) {
                armedId = null
                if (own) reportRequestCancel(sp, request, RequestService.cancel(sp, request.id))
                else reportFulfill(sp, request, RequestService.fulfill(sp, request.id))
            } else {
                armedId = request.id
                val name = Gui.prettyItemName(request.itemId)
                sp.sendSystemMessage(Component.literal(
                    if (own) "§e[AH] Click again to CANCEL your request for ${request.count}× $name — \$${request.price} is refunded."
                    else "§e[AH] Hold ${request.count}× $name in your main hand, then click again to fill this request for \$${request.price}."
                ))
            }
            refresh()
        }

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
            val pages = pageCount(tab, System.currentTimeMillis())
            val next = page + delta
            if (next in 0 until pages) { page = next; refresh() }
        }

        private fun refresh() {
            populate(container, viewer, tab, page, armedId)
            broadcastChanges()
        }

        private fun reportBuy(sp: ServerPlayer, listing: Listing, result: AuctionService.BuyResult) {
            val name = Gui.prettyItemName(listing.itemId)
            val msg = when (result) {
                is AuctionService.BuyResult.Success ->
                    "§a§l[AH] Purchased! §r§a${listing.count}× $name is now in your §bMailbox§a — " +
                        "open the Auctioneer and click §bMailbox§a to collect it."
                AuctionService.BuyResult.Gone -> "§c[AH] That listing is no longer available."
                AuctionService.BuyResult.OwnListing -> "§c[AH] You can't buy your own listing — cancel it from Your Listings."
                is AuctionService.BuyResult.InsufficientBalance ->
                    "§c[AH] You need \$${result.need} but only have \$${result.have}."
                AuctionService.BuyResult.EconomyUnavailable -> "§c[AH] The economy is unavailable right now — try again later."
            }
            sp.sendSystemMessage(Component.literal(msg))
        }

        private fun reportCancel(sp: ServerPlayer, listing: Listing, result: AuctionService.CancelResult) {
            val name = Gui.prettyItemName(listing.itemId)
            val msg = when (result) {
                is AuctionService.CancelResult.Success ->
                    "§a[AH] Cancelled your listing of ${listing.count}× $name — returned to your Mailbox."
                AuctionService.CancelResult.Gone -> "§c[AH] That listing is no longer active."
                AuctionService.CancelResult.NotOwner -> "§c[AH] That isn't your listing."
            }
            sp.sendSystemMessage(Component.literal(msg))
        }

        private fun reportFulfill(sp: ServerPlayer, request: Request, result: RequestService.FulfillResult) {
            val name = Gui.prettyItemName(request.itemId)
            val msg = when (result) {
                is RequestService.FulfillResult.Success ->
                    "§a§l[AH] Filled! §r§aYou handed over ${request.count}× $name and were paid §e\$${request.price}§a."
                RequestService.FulfillResult.Gone -> "§c[AH] That request is no longer available."
                RequestService.FulfillResult.OwnRequest -> "§c[AH] You can't fill your own request — cancel it from Your Requests."
                is RequestService.FulfillResult.NeedItem ->
                    "§c[AH] Hold §f${result.count}× $name§c in your main hand to fill this request (you have ${result.have})."
                RequestService.FulfillResult.EconomyUnavailable -> "§c[AH] The economy is unavailable right now — try again later."
                RequestService.FulfillResult.Error -> "§c[AH] Couldn't fill that request — your item was not taken."
            }
            sp.sendSystemMessage(Component.literal(msg))
        }

        private fun reportRequestCancel(sp: ServerPlayer, request: Request, result: RequestService.CancelResult) {
            val name = Gui.prettyItemName(request.itemId)
            val msg = when (result) {
                is RequestService.CancelResult.Success ->
                    "§a[AH] Cancelled your request for ${request.count}× $name — \$${request.price} refunded to your balance."
                RequestService.CancelResult.Gone -> "§c[AH] That request is no longer active."
                RequestService.CancelResult.NotOwner -> "§c[AH] That isn't your request."
                RequestService.CancelResult.EconomyUnavailable ->
                    "§c[AH] The economy is unavailable right now — request kept, try again later."
            }
            sp.sendSystemMessage(Component.literal(msg))
        }
    }
}
