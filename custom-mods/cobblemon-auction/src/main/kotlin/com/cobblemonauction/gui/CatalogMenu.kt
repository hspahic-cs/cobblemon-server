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
 * Read-only item picker for the create-request flow, in two modes:
 *
 *  - **Suggested** ([open]) — the curated suggestions from config, laid out as category tabs on
 *    row 0 with a paginated grid below, plus a prominent "Search all items" button. This is the
 *    default entry from the Auction House's Create Request action.
 *  - **Results** ([openResults]) — the paginated grid of a completed free-text search over the full
 *    item registry, with a header echoing the query and affordances to run a new search or return to
 *    the suggestions.
 *
 * Clicking an item in either mode forwards its id to the [QuantityMenu] → [PriceAnvilMenu]
 * (CreateRequest) flow. The grid never yields display copies (`quickMoveStack` → EMPTY).
 *
 * Suggested — Row 0: category tabs · Rows 1-4: items · Row 5: [45] prev [47] Search [49] Back [53] next
 * Results   — Row 0: query header  · Rows 1-4: items · Row 5: [45] prev [47] New search [49] Suggested [53] next
 */
object CatalogMenu {

    private const val ROWS = 6
    private const val SLOTS = ROWS * 9
    private const val FIRST_ITEM_SLOT = 9
    private const val PAGE_SIZE = 36           // rows 1-4
    private const val HEADER_SLOT = 4          // results mode: query summary on row 0
    private const val PREV_SLOT = 45
    private const val SEARCH_SLOT = 47
    private const val BACK_SLOT = 49
    private const val NEXT_SLOT = 53

    /** How many search results [SearchAnvilMenu] fetches — two full grid pages. */
    const val SEARCH_LIMIT = PAGE_SIZE * 2

    /** Suggested mode: the curated grid + a search shortcut. */
    fun open(player: ServerPlayer) {
        val container = SimpleContainer(SLOTS)
        val provider = SimpleMenuProvider(
            { syncId, inv, _ -> Impl(syncId, inv, container, player, Mode.Suggested) },
            Component.literal("§0Request Catalog"),
        )
        player.openMenu(provider)
    }

    /**
     * Results mode: show [ids] (pre-ranked by [com.cobblemonauction.service.RequestService.searchItems])
     * as a paginated real-icon grid. [query] is echoed in the header; [capped] flags that the result
     * set hit [SEARCH_LIMIT] and may be incomplete (prompting the player to refine).
     */
    fun openResults(player: ServerPlayer, ids: List<String>, query: String, capped: Boolean) {
        val container = SimpleContainer(SLOTS)
        val provider = SimpleMenuProvider(
            { syncId, inv, _ -> Impl(syncId, inv, container, player, Mode.Results(ids, query, capped)) },
            Component.literal("§0Results: §8${query.take(24)}"),
        )
        player.openMenu(provider)
    }

    private sealed interface Mode {
        data object Suggested : Mode
        data class Results(val ids: List<String>, val query: String, val capped: Boolean) : Mode
    }

    /** Distinct categories in the suggestions' file order (capped to the tab row). */
    private fun categories(): List<String> =
        CobblemonAuction.config.requestable.values.map { it.category }.distinct().take(9)

    private fun itemsIn(category: String): List<String> =
        CobblemonAuction.config.requestable.filterValues { it.category == category }.keys.toList()

    private class Impl(
        syncId: Int,
        inv: Inventory,
        private val container: Container,
        private val viewer: ServerPlayer,
        private val mode: Mode,
    ) : ChestMenu(MenuType.GENERIC_9x6, syncId, inv, container, ROWS) {

        private var category: String = categories().firstOrNull() ?: ""
        private var page = 0

        init { populate() }

        /** The item ids shown in the current grid page's underlying list (all pages). */
        private fun currentItems(): List<String> = when (mode) {
            is Mode.Suggested -> itemsIn(category)
            is Mode.Results -> mode.ids
        }

        private fun pageCount(): Int {
            val n = currentItems().size
            return ((n + PAGE_SIZE - 1) / PAGE_SIZE).coerceAtLeast(1)
        }

        override fun clicked(slotId: Int, button: Int, clickType: ClickType, player: Player) {
            if (clickType == ClickType.QUICK_CRAFT || clickType == ClickType.SWAP) return
            if (slotId !in 0 until SLOTS) { super.clicked(slotId, button, clickType, player); return }
            val sp = player as? ServerPlayer ?: return

            when (slotId) {
                PREV_SLOT, NEXT_SLOT -> { changePage(if (slotId == PREV_SLOT) -1 else +1); return }
                SEARCH_SLOT -> { SearchAnvilMenu.open(sp); return }
                BACK_SLOT -> {
                    // Suggested → back to the Auction House; Results → back to the suggestions.
                    when (mode) {
                        is Mode.Suggested -> BrowseMenu.open(sp, BrowseMenu.Tab.WANTED)
                        is Mode.Results -> open(sp)
                    }
                    return
                }
            }

            if (mode is Mode.Suggested) {
                val cats = categories()
                if (slotId < cats.size) {                   // a category tab
                    val picked = cats[slotId]
                    if (picked != category) { category = picked; page = 0; refresh() }
                    return
                }
            }
            if (slotId < FIRST_ITEM_SLOT) return            // gap between the header row and the grid

            val itemId = currentItems().getOrNull(page * PAGE_SIZE + (slotId - FIRST_ITEM_SLOT)) ?: return
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

            when (mode) {
                is Mode.Suggested -> populateTabs()
                is Mode.Results -> populateHeader(mode)
            }

            val items = currentItems()
            val pages = pageCount()
            val start = (page * PAGE_SIZE).coerceAtMost(items.size)
            val slice = items.subList(start, (start + PAGE_SIZE).coerceAtMost(items.size))
            for ((index, itemId) in slice.withIndex()) {
                val suggested = CobblemonAuction.config.requestable[itemId]?.suggestedPrice
                val lore = mutableListOf("§aClick to request this item")
                if (suggested != null) lore.add(0, "§7Suggested price: §f\$$suggested")
                container.setItem(FIRST_ITEM_SLOT + index, Gui.withLore(Gui.itemStack(itemId, 1), lore))
            }

            // --- Row 5: nav + mode-specific actions ---
            if (page > 0) container.setItem(PREV_SLOT, Gui.button(Items.ARROW, "§aPrevious Page", "§7Page $page / $pages"))
            container.setItem(SEARCH_SLOT, Gui.button(
                Items.SPYGLASS, "§b§lSearch all items",
                "§7Type any item name to request it —",
                "§7not just the suggestions here.",
            ))
            when (mode) {
                is Mode.Suggested -> container.setItem(BACK_SLOT, Gui.button(
                    Items.BARRIER, "§aBack", "§7Return to the Auction House."))
                is Mode.Results -> container.setItem(BACK_SLOT, Gui.button(
                    Items.BOOK, "§aSuggested items", "§7Back to the curated suggestions."))
            }
            if (page < pages - 1) container.setItem(NEXT_SLOT, Gui.button(Items.ARROW, "§aNext Page", "§7Page ${page + 2} / $pages"))
            container.setChanged()
        }

        private fun populateTabs() {
            val cats = categories()
            for ((i, cat) in cats.withIndex()) {
                val active = cat == category
                container.setItem(i, Gui.button(
                    if (active) Items.WRITABLE_BOOK else Items.BOOK,
                    if (active) "§a§l$cat" else "§e$cat",
                    if (active) "§7Showing this category." else "§7Click to browse §f$cat§7.",
                ))
            }
        }

        private fun populateHeader(mode: Mode.Results) {
            val lore = mutableListOf("§7${mode.ids.size} item(s) match.")
            if (mode.ids.isEmpty()) lore += "§7Try a different name."
            if (mode.capped) lore += "§eShowing the first ${mode.ids.size} — refine your search for more."
            lore += "§7Click an item to request it."
            container.setItem(HEADER_SLOT, Gui.button(
                Items.SPYGLASS, "§bResults for \"${mode.query.take(24)}\"", *lore.toTypedArray()))
        }
    }
}
