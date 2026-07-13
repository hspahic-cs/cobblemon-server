package com.cobblemonauction.gui

import com.cobblemonauction.CobblemonAuction
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
 * Read-only results grid for the create-request flow: the paginated matches of a free-text item
 * search (see [SearchAnvilMenu] / [com.cobblemonauction.service.RequestService.searchItems]).
 * Create Request goes straight to the search anvil, so there is no "suggested" landing screen —
 * this menu only ever shows search results. Clicking an item forwards its id to [QuantityMenu] →
 * [PriceAnvilMenu] (CreateRequest). The grid never yields display copies (`quickMoveStack` → EMPTY).
 *
 * Row 0: query header · Rows 1-4: items · Row 5: [45] prev [47] New search [49] Back [53] next
 */
object CatalogMenu {

    private const val ROWS = 6
    private const val SLOTS = ROWS * 9
    private const val FIRST_ITEM_SLOT = 9
    private const val PAGE_SIZE = 36           // rows 1-4
    private const val HEADER_SLOT = 4
    private const val PREV_SLOT = 45
    private const val SEARCH_SLOT = 47
    private const val BACK_SLOT = 49
    private const val NEXT_SLOT = 53

    /** How many search results [SearchAnvilMenu] fetches — two full grid pages. */
    const val SEARCH_LIMIT = PAGE_SIZE * 2

    /**
     * Show [ids] (pre-ranked by [com.cobblemonauction.service.RequestService.searchItems]) as a
     * paginated real-icon grid. [query] is echoed in the header; [capped] flags that the result set
     * hit [SEARCH_LIMIT] and may be incomplete (prompting the player to refine).
     */
    fun openResults(player: ServerPlayer, ids: List<String>, query: String, capped: Boolean) {
        val container = SimpleContainer(SLOTS)
        val provider = SimpleMenuProvider(
            { syncId, inv, _ -> Impl(syncId, inv, container, player, ids, query, capped) },
            Component.literal("§0Results: §8${query.take(24)}"),
        )
        player.openMenu(provider)
    }

    private class Impl(
        syncId: Int,
        inv: Inventory,
        private val container: Container,
        private val viewer: ServerPlayer,
        private val ids: List<String>,
        private val query: String,
        private val capped: Boolean,
    ) : ChestMenu(MenuType.GENERIC_9x6, syncId, inv, container, ROWS) {

        private var page = 0

        init { populate() }

        private fun pageCount(): Int = ((ids.size + PAGE_SIZE - 1) / PAGE_SIZE).coerceAtLeast(1)

        override fun clicked(slotId: Int, button: Int, clickType: ClickType, player: Player) {
            if (clickType == ClickType.QUICK_CRAFT || clickType == ClickType.SWAP) return
            if (slotId !in 0 until SLOTS) { super.clicked(slotId, button, clickType, player); return }
            val sp = player as? ServerPlayer ?: return

            when (slotId) {
                PREV_SLOT, NEXT_SLOT -> { changePage(if (slotId == PREV_SLOT) -1 else +1); return }
                SEARCH_SLOT -> { SearchAnvilMenu.open(sp); return }
                BACK_SLOT -> { BrowseMenu.open(sp, BrowseMenu.Tab.WANTED); return }
            }
            if (slotId < FIRST_ITEM_SLOT) return

            val itemId = ids.getOrNull(page * PAGE_SIZE + (slotId - FIRST_ITEM_SLOT)) ?: return
            QuantityMenu.open(sp, itemId)
        }

        // Read-only grid: never let players pull display copies out via shift-click.
        override fun quickMoveStack(player: Player, slotIndex: Int): ItemStack = ItemStack.EMPTY

        private fun changePage(delta: Int) {
            val next = page + delta
            if (next in 0 until pageCount()) { page = next; refresh() }
        }

        private fun refresh() {
            populate()
            broadcastChanges()
        }

        private fun populate() {
            for (i in 0 until container.containerSize) container.setItem(i, ItemStack.EMPTY)

            val headerLore = mutableListOf("§7${ids.size} item(s) match.")
            if (capped) headerLore += "§eShowing the first ${ids.size} — refine your search for more."
            headerLore += "§7Click an item to request it."
            container.setItem(HEADER_SLOT, Gui.button(
                Items.COMPASS, "§bResults for \"${query.take(24)}\"", *headerLore.toTypedArray()))

            val pages = pageCount()
            val start = (page * PAGE_SIZE).coerceAtMost(ids.size)
            val slice = ids.subList(start, (start + PAGE_SIZE).coerceAtMost(ids.size))
            for ((index, itemId) in slice.withIndex()) {
                val suggested = CobblemonAuction.config.requestable[itemId]?.suggestedPrice
                val itemLore = mutableListOf("§aClick to request this item")
                if (suggested != null) itemLore.add(0, "§7Suggested price: §f\$$suggested")
                container.setItem(FIRST_ITEM_SLOT + index, Gui.withLore(Gui.itemStack(itemId, 1), itemLore))
            }

            // --- Row 5: nav + new-search / back ---
            if (page > 0) container.setItem(PREV_SLOT, Gui.button(Items.ARROW, "§aPrevious Page", "§7Page $page / $pages"))
            container.setItem(SEARCH_SLOT, Gui.button(
                Items.COMPASS, "§b§lNew search", "§7Type a different item name."))
            container.setItem(BACK_SLOT, Gui.button(
                Items.BARRIER, "§aBack", "§7Return to the Auction House."))
            if (page < pages - 1) container.setItem(NEXT_SLOT, Gui.button(Items.ARROW, "§aNext Page", "§7Page ${page + 2} / $pages"))
            container.setChanged()
        }
    }
}
