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
 * Read-only picker over the requestable-items whitelist for the create-request flow. Category tabs
 * on row 0 (same pattern as the Wanted/For-Sale toggle), a paginated item grid in rows 1-4, and nav
 * on row 5. Clicking an item forwards its id to the [QuantityMenu]; the grid never yields display
 * copies (`quickMoveStack` → EMPTY).
 *
 * Row 0:    up to 9 category tabs
 * Rows 1-4: up to 36 items for the active category, per page
 * Row 5:    [45] prev  [49] Back  [53] next
 */
object CatalogMenu {

    private const val ROWS = 6
    private const val SLOTS = ROWS * 9
    private const val FIRST_ITEM_SLOT = 9
    private const val PAGE_SIZE = 36           // rows 1-4
    private const val PREV_SLOT = 45
    private const val BACK_SLOT = 49
    private const val NEXT_SLOT = 53

    fun open(player: ServerPlayer) {
        val container = SimpleContainer(SLOTS)
        val provider = SimpleMenuProvider(
            { syncId, inv, _ -> Impl(syncId, inv, container, player) },
            Component.literal("§0Request Catalog"),
        )
        player.openMenu(provider)
    }

    /** Distinct categories in the whitelist's file order (capped to the tab row). */
    private fun categories(): List<String> =
        CobblemonAuction.config.requestable.values.map { it.category }.distinct().take(9)

    private fun itemsIn(category: String): List<String> =
        CobblemonAuction.config.requestable.filterValues { it.category == category }.keys.toList()

    private fun pageCount(category: String): Int {
        val n = itemsIn(category).size
        return ((n + PAGE_SIZE - 1) / PAGE_SIZE).coerceAtLeast(1)
    }

    private fun populate(container: Container, category: String, page: Int) {
        for (i in 0 until container.containerSize) container.setItem(i, ItemStack.EMPTY)

        val cats = categories()
        for ((i, cat) in cats.withIndex()) {
            val active = cat == category
            container.setItem(i, Gui.button(
                if (active) Items.WRITABLE_BOOK else Items.BOOK,
                if (active) "§a§l$cat" else "§e$cat",
                if (active) "§7Showing this category." else "§7Click to browse §f$cat§7.",
            ))
        }

        val items = itemsIn(category)
        val pages = pageCount(category)
        val start = (page * PAGE_SIZE).coerceAtMost(items.size)
        val slice = items.subList(start, (start + PAGE_SIZE).coerceAtMost(items.size))
        for ((index, itemId) in slice.withIndex()) {
            val suggested = CobblemonAuction.config.requestable[itemId]?.suggestedPrice
            val lore = mutableListOf("§aClick to request this item")
            if (suggested != null) lore.add(0, "§7Suggested price: §f\$$suggested")
            container.setItem(FIRST_ITEM_SLOT + index, Gui.withLore(Gui.itemStack(itemId, 1), lore))
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

        private var category: String = categories().firstOrNull() ?: ""
        private var page = 0

        init { populate(container, category, page) }

        override fun clicked(slotId: Int, button: Int, clickType: ClickType, player: Player) {
            if (clickType == ClickType.QUICK_CRAFT || clickType == ClickType.SWAP) return
            if (slotId !in 0 until SLOTS) { super.clicked(slotId, button, clickType, player); return }
            val sp = player as? ServerPlayer ?: return

            when (slotId) {
                BACK_SLOT -> { BrowseMenu.open(sp, BrowseMenu.Tab.WANTED); return }
                PREV_SLOT, NEXT_SLOT -> { changePage(if (slotId == PREV_SLOT) -1 else +1); return }
            }

            val cats = categories()
            if (slotId < cats.size) {                       // a category tab
                val picked = cats[slotId]
                if (picked != category) { category = picked; page = 0; refresh() }
                return
            }
            if (slotId < FIRST_ITEM_SLOT) return            // gap between tab row and grid

            val itemId = itemsIn(category).getOrNull(page * PAGE_SIZE + (slotId - FIRST_ITEM_SLOT)) ?: return
            QuantityMenu.open(sp, itemId)
        }

        // Read-only grid: never let players pull display copies out via shift-click.
        override fun quickMoveStack(player: Player, slotIndex: Int): ItemStack = ItemStack.EMPTY

        private fun changePage(delta: Int) {
            val next = page + delta
            if (next in 0 until pageCount(category)) { page = next; refresh() }
        }

        private fun refresh() {
            populate(container, category, page)
            broadcastChanges()
        }
    }
}
