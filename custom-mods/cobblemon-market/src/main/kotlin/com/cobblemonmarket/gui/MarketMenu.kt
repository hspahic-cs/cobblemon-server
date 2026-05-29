package com.cobblemonmarket.gui

import com.cobblemonmarket.CobblemonMarket
import com.cobblemonmarket.config.ItemEntry
import com.cobblemonmarket.config.isSellable
import com.cobblemonmarket.config.vendorScope
import com.cobblemonmarket.economy.EconomyBridge
import com.cobblemonmarket.economy.TradeOps
import com.cobblemonmarket.economy.TradeResult
import com.cobblemonmarket.pricing.PricingEngine
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.resources.ResourceLocation
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
import net.minecraft.world.item.component.ItemLore

/**
 * Right-click-on-shopkeeper chest GUI for buy/sell. Replaces the legacy `/buy` / `/sell`
 * command-from-anywhere flow — those are disabled so trades only happen at the vendor.
 *
 * Layout (54-slot GENERIC_9x6):
 *   Row 0: nav bar.
 *     - Slot 0 = previous-page arrow (only when on a non-first page).
 *     - Slot 4 = balance display (gold ingot with current balance + keyboard hints).
 *     - Slot 8 = next-page arrow (only when there's a next page).
 *   Rows 1-5: up to [PAGE_SIZE] item slots (45). The TM vendors push the entry count past
 *     a single page — `tm_normal` alone has ~169 entries — so the GUI pages through them
 *     in stable registration order.
 *
 * Click semantics on item slots:
 *   - Left-click           → buy 1
 *   - Shift-left           → buy 16 (capped by balance/stock)
 *   - Right-click          → sell 1 from inventory
 *   - Shift-right          → sell 64 (capped by what the player owns)
 *
 * Click semantics on nav slots:
 *   - Slot 0 / Slot 8      → previous / next page; the chest rebuilds in place.
 *
 * After each transaction the slot's lore is refreshed in place. Title-bar balance refreshes
 * too. The whole chest is read-only with respect to item movement: shift-click and drag are
 * dropped, so players can't take the display copies into their inventory.
 */
object MarketMenu {

    private const val ROWS = 6
    private const val SLOTS = ROWS * 9
    private const val PREV_SLOT = 0                  // row 0 col 0 — previous-page arrow
    private const val BALANCE_SLOT = 4               // center of row 0
    private const val NEXT_SLOT = 8                  // row 0 col 8 — next-page arrow
    private const val FIRST_ITEM_SLOT = 9            // row 1 col 0
    private const val PAGE_SIZE = SLOTS - FIRST_ITEM_SLOT  // 45 item slots per page

    /**
     * Open the market GUI scoped to a specific vendor.
     *
     * @param vendorTag The vendor identifier. `""` = the legacy default market (every entry
     *   whose `vendorTag` is empty). Non-empty = only items whose `vendorTag` matches —
     *   e.g. `"tm_fire"` for the Fire-TM shop.
     */
    fun open(player: ServerPlayer, vendorTag: String = "") {
        val container = SimpleContainer(SLOTS)
        populate(container, player, vendorTag, page = 0)
        val title = if (vendorTag.isEmpty()) "§0Market"
                    else "§0Market — ${formatTag(vendorTag)}"
        val provider = SimpleMenuProvider(
            { syncId, inv, _ -> Impl(syncId, inv, container, player, vendorTag) },
            Component.literal(title),
        )
        player.openMenu(provider)
    }

    /** "tm_fire" → "Fire TMs"; falls back to the raw tag if it doesn't match the known shape. */
    private fun formatTag(tag: String): String =
        if (tag.startsWith("tm_")) tag.removePrefix("tm_").replaceFirstChar { it.uppercase() } + " TMs"
        else tag.replace('_', ' ').replaceFirstChar { it.uppercase() }

    /** All entries this vendor carries, in stable registration order. */
    private fun visibleItems(vendorTag: String): List<Map.Entry<String, ItemEntry>> =
        CobblemonMarket.items.entries.filter { it.value.vendorScope == vendorTag }

    /** Number of pages needed for this vendor's catalog (at least 1, even when empty). */
    private fun pageCount(vendorTag: String): Int {
        val n = visibleItems(vendorTag).size
        return ((n + PAGE_SIZE - 1) / PAGE_SIZE).coerceAtLeast(1)
    }

    /** (Re)populate every slot from current market state. Called on open + after each trade. */
    private fun populate(container: Container, player: ServerPlayer, vendorTag: String, page: Int) {
        for (i in 0 until container.containerSize) container.setItem(i, ItemStack.EMPTY)
        container.setItem(BALANCE_SLOT, balanceStack(player))

        val all = visibleItems(vendorTag)
        val pages = pageCount(vendorTag)
        val start = page * PAGE_SIZE
        val slice = all.subList(start.coerceAtMost(all.size), (start + PAGE_SIZE).coerceAtMost(all.size))
        for ((index, kv) in slice.withIndex()) {
            container.setItem(FIRST_ITEM_SLOT + index, itemStackFor(kv.key, kv.value))
        }

        // Nav arrows: only fill slots where there's a page to go to. Bare empty slots when at
        // the edge of the range — clicking an empty slot is a no-op in [Impl.clicked].
        if (page > 0) container.setItem(PREV_SLOT, navArrowStack("§a§lPrevious Page", page, pages))
        if (page < pages - 1) container.setItem(NEXT_SLOT, navArrowStack("§a§lNext Page", page, pages))
        container.setChanged()
    }

    /** Component with italics off — vanilla auto-italicizes custom item names and lore. */
    private fun line(s: String): MutableComponent =
        Component.literal(s).setStyle(Style.EMPTY.withItalic(false))

    private fun navArrowStack(label: String, currentPage: Int, totalPages: Int): ItemStack {
        val stack = ItemStack(net.minecraft.world.item.Items.ARROW)
        stack.set(DataComponents.CUSTOM_NAME, line(label))
        stack.set(DataComponents.LORE, ItemLore(listOf(
            line("§7Page ${currentPage + 1} / $totalPages"),
        )))
        return stack
    }

    private fun balanceStack(player: ServerPlayer): ItemStack {
        val bal = EconomyBridge.getBalance(player.uuid)
        val stack = ItemStack(net.minecraft.world.item.Items.GOLD_INGOT)
        stack.set(DataComponents.CUSTOM_NAME, line("§6Your Balance: §e\$$bal"))
        stack.set(DataComponents.LORE, ItemLore(listOf(
            line("§7Left-click an item to buy 1."),
            line("§7Shift-left for 16."),
            line("§7Right-click to sell 1."),
            line("§7Shift-right to sell 64."),
        )))
        return stack
    }

    private fun itemStackFor(itemId: String, entry: ItemEntry): ItemStack {
        val rl = ResourceLocation.tryParse(itemId) ?: return ItemStack.EMPTY
        val item = BuiltInRegistries.ITEM.getOptional(rl).orElse(null) ?: return ItemStack.EMPTY
        val state = CobblemonMarket.marketStore.getOrCreate(itemId)
        val buy = PricingEngine.buyPrice(entry.baseBuyPrice, state.stock, entry.baseStock, entry.elasticity)
        val sell = PricingEngine.sellPrice(entry.baseSellPrice, state.stock, entry.baseStock, entry.elasticity)
        val stockNow = state.stock.toInt()
        val stack = ItemStack(item)
        val lore = mutableListOf<MutableComponent>()
        lore += line("§aBuy: §f\$$buy §8(per unit)")
        if (entry.isSellable) {
            lore += line("§cSell: §f\$$sell §8(per unit)")
            lore += line("§7Stock: §f$stockNow §8/ ${entry.baseStock} target")
            lore += line("")
            lore += line("§8Left-click: buy 1  ·  Shift: buy 16")
            lore += line("§8Right-click: sell 1  ·  Shift: sell 64")
        } else {
            lore += line("§8(Buy only — not resold)")
            lore += line("")
            lore += line("§8Left-click: buy 1  ·  Shift: buy 16")
        }
        stack.set(DataComponents.LORE, ItemLore(lore.map { it as Component }))
        return stack
    }

    private class Impl(
        syncId: Int,
        inv: Inventory,
        private val container: Container,
        private val viewer: ServerPlayer,
        private val vendorTag: String,
    ) : ChestMenu(MenuType.GENERIC_9x6, syncId, inv, container, ROWS) {

        private var page: Int = 0

        override fun clicked(slotId: Int, button: Int, clickType: ClickType, player: Player) {
            // Drag and number-key swaps are blocked entirely — they'd pull display items.
            if (clickType == ClickType.QUICK_CRAFT || clickType == ClickType.SWAP) return
            if (slotId !in 0 until SLOTS) {
                super.clicked(slotId, button, clickType, player)
                return
            }
            if (slotId == BALANCE_SLOT) return
            // Page-nav arrows. Clicks on an arrow when there's no page in that direction
            // are a no-op (the slot is empty, so itemIndex / out-of-range check below would
            // also reject — we just want to handle them earlier and consistently).
            if (slotId == PREV_SLOT || slotId == NEXT_SLOT) {
                val pages = pageCount(vendorTag)
                val next = when (slotId) {
                    PREV_SLOT -> page - 1
                    NEXT_SLOT -> page + 1
                    else -> page
                }
                if (next in 0 until pages) {
                    page = next
                    populate(container, viewer, vendorTag, page)
                    broadcastChanges()
                }
                return
            }
            if (slotId < FIRST_ITEM_SLOT) return
            val items = visibleItems(vendorTag)
            val pageStart = page * PAGE_SIZE
            val itemIndex = pageStart + (slotId - FIRST_ITEM_SLOT)
            if (itemIndex !in items.indices) return
            val (itemId, entry) = items[itemIndex]
            val sp = player as? ServerPlayer ?: return

            val (action, qty) = when {
                button == 0 && clickType == ClickType.PICKUP    -> "buy" to 1
                button == 0 && clickType == ClickType.QUICK_MOVE -> "buy" to 16
                button == 1 && clickType == ClickType.PICKUP    -> "sell" to 1
                button == 1 && clickType == ClickType.QUICK_MOVE -> "sell" to 64
                else -> return
            }
            if (action == "sell" && !entry.isSellable) {
                sp.sendSystemMessage(Component.literal("§c[Market] This vendor doesn't buy items back."))
                return
            }

            val result: TradeResult = if (action == "buy") TradeOps.buy(sp, itemId, qty) else TradeOps.sell(sp, itemId, qty)
            reportTrade(sp, action, itemId, qty, result)
            populate(container, viewer, vendorTag, page)
            broadcastChanges()
        }

        /**
         * Shift-click semantics. The vanilla default would try to move items between the chest
         * (display items) and the player inventory in BOTH directions, which would either yank
         * display items into the player's bags OR merge a player stack into a display slot
         * (which our next [populate] then wipes — silent item loss). We override both
         * directions:
         *
         *   - Shift-click a CHEST slot → no-op (don't let players take display items).
         *   - Shift-click an INVENTORY slot → if the item has a market entry, sell the entire
         *     stack at the current per-unit sell price. Otherwise no-op so the player doesn't
         *     lose anything they shift-clicked by mistake.
         *
         * Returning [ItemStack.EMPTY] tells vanilla's shift-loop in `AbstractContainerMenu`
         * that nothing was moved — the loop exits and our own logic (the optional sell call)
         * is the only side-effect.
         */
        override fun quickMoveStack(player: Player, slotIndex: Int): ItemStack {
            if (slotIndex in 0 until SLOTS) return ItemStack.EMPTY
            val sp = player as? ServerPlayer ?: return ItemStack.EMPTY
            val slot = slots.getOrNull(slotIndex) ?: return ItemStack.EMPTY
            val stack = slot.item
            if (stack.isEmpty) return ItemStack.EMPTY
            val itemId = BuiltInRegistries.ITEM.getKey(stack.item).toString()
            val entry = CobblemonMarket.items[itemId] ?: return ItemStack.EMPTY
            // Refuse shift-sell against a vendor that doesn't carry this item, or against a
            // buy-only entry — otherwise the player'd silently lose the stack.
            if (entry.vendorScope != vendorTag || !entry.isSellable) return ItemStack.EMPTY
            val qty = stack.count
            val result = TradeOps.sell(sp, itemId, qty)
            reportTrade(sp, "sell", itemId, qty, result)
            populate(container, viewer, vendorTag, page)
            broadcastChanges()
            return ItemStack.EMPTY
        }
    }

    private fun reportTrade(
        player: ServerPlayer,
        action: String,
        itemId: String,
        qty: Int,
        result: TradeResult,
    ) {
        val name = formatItemName(itemId)
        val msg = when (result) {
            is TradeResult.Success -> Component.literal("§a[Market] ${action.replaceFirstChar(Char::uppercase)} ${result.totalPrice / qty.coerceAtLeast(1)}×$qty $name — \$${result.totalPrice} §7(stock → ${result.newStock.toInt()})")
            is TradeResult.InsufficientBalance -> Component.literal("§c[Market] Need \$${result.need}, you have \$${result.have}")
            is TradeResult.InsufficientItems -> Component.literal("§c[Market] You only have ${result.have}× $name (need ${result.need})")
            is TradeResult.OutOfStock -> Component.literal("§c[Market] Only ${result.available}× $name available")
            is TradeResult.MarketSaturated -> Component.literal("§c[Market] Market saturated for $name — try later")
            TradeResult.NoInventorySpace -> Component.literal("§c[Market] Inventory full")
            is TradeResult.UnknownItem -> Component.literal("§c[Market] Unknown item: ${result.itemId}")
            TradeResult.EconomyFailed -> Component.literal("§c[Market] Economy unavailable")
        }
        player.sendSystemMessage(msg)
    }

    private fun formatItemName(itemId: String): String =
        itemId.substringAfterLast(':').split('_').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
}
