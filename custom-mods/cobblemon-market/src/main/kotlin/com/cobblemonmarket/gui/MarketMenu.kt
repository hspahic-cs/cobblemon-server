package com.cobblemonmarket.gui

import com.cobblemonmarket.CobblemonMarket
import com.cobblemonmarket.bp.Vouchers
import com.cobblemonmarket.config.ItemEntry
import com.cobblemonmarket.config.effectiveBundleSize
import com.cobblemonmarket.config.effectiveBuyClamp
import com.cobblemonmarket.config.effectiveMinBuyPrice
import com.cobblemonmarket.config.effectiveSellClamp
import com.cobblemonmarket.config.isSellable
import com.cobblemonmarket.config.vendorScope
import com.cobblemonmarket.economy.EconomyBridge
import com.cobblemonmarket.economy.DraftSlotBridge
import com.cobblemonmarket.economy.HomeUpgradeBridge
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
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.ItemLore

/**
 * Right-click-on-shopkeeper chest GUI for buy/sell. Replaces the legacy `/buy` / `/sell`
 * command-from-anywhere flow — those are disabled so trades only happen at the vendor.
 *
 * The default Shopkeeper opens with a **tab bar** across the top of the GUI so one NPC serves
 * several categories: General (Pokémon supplies), Blocks & Decor (buy-only building materials),
 * and Upgrades (buyable account perks like extra `/sethome` slots). Scoped vendors (the TM and
 * Held-Item NPCs, spawned with `/market admin spawn <tag>`) open with a single implicit tab and
 * no tab bar — their layout is unchanged.
 *
 * Layout (54-slot GENERIC_9x6):
 *   Row 0: nav bar. Balance always sits in the top-right corner (slot 8).
 *     - With tabs (default shopkeeper): slots 0..N-1 = category tabs, slot 6 / slot 7 = previous /
 *       next page, slot 8 = balance.
 *     - Without tabs (scoped vendor): slot 0 = previous, slot 7 = next, slot 8 = balance.
 *   Rows 1-5: up to [PAGE_SIZE] content slots (45) — item catalog for item tabs, or the upgrade
 *     panel for the Upgrades tab.
 *
 * Click semantics on item slots:
 *   - Left-click           → buy 1 (a bundled item delivers its full stack per click)
 *   - Shift-left           → buy 16 (capped by balance/stock)
 *   - Right-click          → sell 1 from inventory
 *   - Shift-right          → sell 64 (capped by what the player owns)
 *
 * The whole chest is read-only with respect to item movement: shift-click and drag are dropped,
 * so players can't take the display copies into their inventory.
 */
object MarketMenu {

    private const val ROWS = 6
    private const val SLOTS = ROWS * 9
    private const val BALANCE_SLOT = 8               // top-right corner of row 0
    private const val FIRST_ITEM_SLOT = 9            // row 1 col 0
    private const val PAGE_SIZE = SLOTS - FIRST_ITEM_SLOT  // 45 content slots per page

    // Page arrows sit at different columns depending on whether the tab bar is present, so the
    // tabs (slots 0..N-1) never collide with the previous-page arrow. Balance sits in the top-right
    // corner (slot 8), so next-page is slot 7 — leaving the left of the row free for category tabs.
    private const val TABBED_PREV_SLOT = 6
    private const val SINGLE_PREV_SLOT = 0
    private const val NEXT_SLOT = 7

    /** Nav slot for the "Back to type list" button in the TM Merchant's type view. */
    private const val BACK_SLOT = 0

    /** Sentinel vendor tag for the consolidated TM Merchant (no items.json scope uses it). */
    const val TM_MERCHANT_TAG = "tm_merchant"

    /** Content slot the Upgrades tab renders the "extra home slot" purchase into (row 2, center). */
    private const val HOME_UPGRADE_SLOT = 22
    /** Content slot for the "draft team slot" purchase (row 2, right of the home upgrade). */
    private const val DRAFT_UPGRADE_SLOT = 24

    /** Price of a player's first purchased home slot (beyond the free baseline). */
    private const val FIRST_EXTRA_HOME_PRICE = 100_000
    /** Each subsequent home slot costs this much more than the previous one. */
    private const val HOME_PRICE_INCREMENT = 50_000

    /**
     * A category tab. [scope] is the item `vendorTag` this tab shows, or `null` for the Upgrades
     * tab (which renders the account-upgrade panel instead of an item catalog).
     */
    private data class Tab(val label: String, val icon: Item, val scope: String?)

    /** The default Shopkeeper's tabs, left to right. */
    private val DEFAULT_TABS = listOf(
        Tab("General Store", Items.EMERALD, ""),
        Tab("Blocks & Decor", Items.BRICKS, "blocks"),
        Tab("Special Items", Items.HEART_OF_THE_SEA, "special_items"),
        Tab("Upgrades", Items.NETHER_STAR, null),
    )

    /** Resolve a Cobblemon type gem (`cobblemon:<type>_gem`) for a type icon, falling back to paper. */
    private fun gemIcon(type: String): Item {
        val rl = ResourceLocation.tryParse("cobblemon:${type}_gem") ?: return Items.PAPER
        return BuiltInRegistries.ITEM.getOptional(rl).orElse(Items.PAPER)
    }

    /** The 18 Pokémon types, in TM-picker order (scope is `tm_<type>`, icon is that type's gem). */
    private val TM_TYPES = listOf(
        "normal", "fire", "water", "electric", "grass", "ice", "fighting", "poison",
        "ground", "flying", "psychic", "bug", "rock", "ghost", "dragon", "dark", "steel", "fairy",
    )

    /**
     * The TM Merchant's 18 type entries (scope `tm_<type>`), built lazily so the gem-icon registry
     * lookups happen at open-time rather than class-init.
     */
    private fun tmTypeTabs(): List<Tab> = TM_TYPES.map { type ->
        Tab(type.replaceFirstChar { it.uppercase() }, gemIcon(type), "tm_$type")
    }

    /**
     * Open the shopkeeper GUI.
     *
     * @param vendorTag `""` = the default Shopkeeper, which shows the [DEFAULT_TABS] tab bar.
     *   A non-empty tag = a single-category scoped vendor (e.g. `"tm_fire"`) with no tab bar.
     */
    fun open(player: ServerPlayer, vendorTag: String = "") {
        // TM Merchant: one NPC, two-level (type picker → that type's TRs). Its "tabs" are the 18
        // type entries, navigated via `selectedType` rather than a tab bar.
        if (vendorTag == TM_MERCHANT_TAG) {
            val types = tmTypeTabs()
            val container = SimpleContainer(SLOTS)
            populateTmMerchant(container, player, types, selectedType = null, page = 0)
            val provider = SimpleMenuProvider(
                { syncId, inv, _ -> Impl(syncId, inv, container, player, types, tmMerchant = true) },
                Component.literal("§0TM Merchant"),
            )
            player.openMenu(provider)
            return
        }

        val tabs = if (vendorTag.isEmpty()) DEFAULT_TABS
                   else listOf(Tab(formatTag(vendorTag), Items.EMERALD, vendorTag))
        val container = SimpleContainer(SLOTS)
        populate(container, player, tabs, activeTab = 0, page = 0)
        val title = if (vendorTag.isEmpty()) "§0Shopkeeper"
                    else "§0Market — ${formatTag(vendorTag)}"
        val provider = SimpleMenuProvider(
            { syncId, inv, _ -> Impl(syncId, inv, container, player, tabs) },
            Component.literal(title),
        )
        player.openMenu(provider)
    }

    /** Delegates to MarketCommands.vendorDisplayName so spawn-vendor names and GUI labels stay in
     *  sync. Strips a trailing " Vendor" so a scoped tab reads "Fire TMs" rather than "Fire TMs Vendor". */
    private fun formatTag(tag: String): String =
        com.cobblemonmarket.commands.MarketCommands.vendorDisplayName(tag).removeSuffix(" Vendor")

    /**
     * All entries for an item scope. The General Store (scope `""`) is grouped so like items sit
     * together — Poké Balls, HP potions, revives, status cures, PP restores, then candies — and is
     * tiered by price within each group (like a backpack's "sort by category"). Other scopes keep
     * their authored order. Both the render (`populate`) and the click→item mapping (`clicked`)
     * call this, so they always agree on order — the sort must stay deterministic.
     */
    private fun visibleItems(scope: String): List<Map.Entry<String, ItemEntry>> {
        val entries = CobblemonMarket.items.entries.filter { it.value.vendorScope == scope }
        if (scope != "") return entries
        return entries.sortedWith(
            compareBy({ generalStoreGroup(it.key) }, { it.value.baseBuyPrice }, { it.key }),
        )
    }

    /**
     * Coarse category rank for General-Store sorting, so like items cluster. Matched by item-id
     * suffix/keyword; anything unrecognised sorts last.
     */
    private fun generalStoreGroup(itemId: String): Int {
        val id = itemId.substringAfterLast(':')
        return when {
            id.endsWith("_ball")                                 -> 0  // Poké Balls
            id == "potion" || id.endsWith("_potion") ||
                id == "full_restore"                             -> 1  // HP potions
            id == "revive" || id == "max_revive"                 -> 2  // revives
            id.endsWith("_heal") || id == "antidote" ||
                id == "awakening"                                -> 3  // status cures
            id.endsWith("ether") || id.endsWith("elixir")        -> 4  // PP restores
            id.contains("candy")                                 -> 5  // candies
            else                                                 -> 6  // everything else
        }
    }

    /**
     * The voucher type redeemable at [scope], or null if that scope takes no voucher. TR vendors
     * (the TM Merchant and any `tm_<type>` vendor) accept `tr` vouchers; the held-item vendor
     * accepts `held_item` vouchers.
     */
    private fun voucherTypeForScope(scope: String?): String? = when {
        scope == null -> null
        scope == "held_items" -> "held_item"
        scope == TM_MERCHANT_TAG || scope.startsWith("tm_") -> "tr"
        else -> null
    }

    /**
     * If [player] holds a voucher valid for [scope], consume one and deliver a single [itemId] for
     * free (bundle-size units), then return true so the caller skips the money purchase. This is the
     * "check for a voucher before charging money" path. Only fires for single-item buys; bulk
     * (shift) buys still pay money.
     */
    private fun tryRedeemVoucher(player: ServerPlayer, scope: String?, itemId: String, entry: ItemEntry): Boolean {
        val vType = voucherTypeForScope(scope) ?: return false
        if (!Vouchers.consume(player, vType)) return false
        val item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId))
        val stack = ItemStack(item, entry.effectiveBundleSize)
        val name = stack.hoverName.string
        if (!player.inventory.add(stack)) player.drop(stack, false)
        player.sendSystemMessage(Component.literal("§a[Market] Redeemed a voucher for §f$name§a (no charge)."))
        return true
    }

    /** Number of pages this tab needs (upgrade/non-item tabs are always a single page). */
    private fun pageCount(tab: Tab): Int {
        val scope = tab.scope ?: return 1
        val n = visibleItems(scope).size
        return ((n + PAGE_SIZE - 1) / PAGE_SIZE).coerceAtLeast(1)
    }

    private fun prevSlot(tabs: List<Tab>): Int =
        if (tabs.size > 1) TABBED_PREV_SLOT else SINGLE_PREV_SLOT

    /** (Re)populate every slot from current market state. Called on open + after each interaction. */
    private fun populate(container: Container, player: ServerPlayer, tabs: List<Tab>, activeTab: Int, page: Int) {
        for (i in 0 until container.containerSize) container.setItem(i, ItemStack.EMPTY)
        container.setItem(BALANCE_SLOT, balanceStack(player))

        // Tab bar (only when there's more than one category).
        if (tabs.size > 1) {
            for ((i, tab) in tabs.withIndex()) {
                container.setItem(i, tabButtonStack(tab, active = i == activeTab))
            }
        }

        val tab = tabs[activeTab]
        val pages = pageCount(tab)
        val scope = tab.scope
        if (scope != null) {
            val all = visibleItems(scope)
            val start = page * PAGE_SIZE
            val slice = all.subList(start.coerceAtMost(all.size), (start + PAGE_SIZE).coerceAtMost(all.size))
            for ((index, kv) in slice.withIndex()) {
                container.setItem(FIRST_ITEM_SLOT + index, itemStackFor(kv.key, kv.value))
            }
        } else {
            // Upgrades tab: render the buyable account perks.
            container.setItem(HOME_UPGRADE_SLOT, homeUpgradeStack(player))
            container.setItem(DRAFT_UPGRADE_SLOT, draftUpgradeStack(player))
        }

        // Nav arrows: only fill slots where there's a page to go to.
        if (page > 0) container.setItem(prevSlot(tabs), navArrowStack("§a§lPrevious Page", page, pages))
        if (page < pages - 1) container.setItem(NEXT_SLOT, navArrowStack("§a§lNext Page", page, pages))
        container.setChanged()
    }

    // ─── TM Merchant (two-level: type picker → per-type TR list) ──────────────────────────────

    /**
     * (Re)populate the TM Merchant. [selectedType] `null` renders the 18-type picker grid; a
     * non-null index renders that type's TRs (scope `tm_<type>`) paginated, with a Back button.
     */
    private fun populateTmMerchant(
        container: Container, player: ServerPlayer,
        types: List<Tab>, selectedType: Int?, page: Int,
    ) {
        for (i in 0 until container.containerSize) container.setItem(i, ItemStack.EMPTY)
        container.setItem(BALANCE_SLOT, balanceStack(player))

        if (selectedType == null) {
            // Picker: 18 type icons filling the content area (rows 1-2).
            for ((i, type) in types.withIndex()) {
                container.setItem(FIRST_ITEM_SLOT + i, tmTypeIconStack(type))
            }
            container.setChanged()
            return
        }

        // Type view: that type's TRs, paginated, with a Back button (slot 0) and page arrows (6/8).
        val type = types[selectedType]
        val all = visibleItems(type.scope!!)
        val pages = ((all.size + PAGE_SIZE - 1) / PAGE_SIZE).coerceAtLeast(1)
        container.setItem(BACK_SLOT, tmBackStack(type))
        val start = page * PAGE_SIZE
        val slice = all.subList(start.coerceAtMost(all.size), (start + PAGE_SIZE).coerceAtMost(all.size))
        for ((index, kv) in slice.withIndex()) {
            container.setItem(FIRST_ITEM_SLOT + index, itemStackFor(kv.key, kv.value))
        }
        if (page > 0) container.setItem(TABBED_PREV_SLOT, navArrowStack("§a§lPrevious Page", page, pages))
        if (page < pages - 1) container.setItem(NEXT_SLOT, navArrowStack("§a§lNext Page", page, pages))
        container.setChanged()
    }

    private fun tmTypeIconStack(type: Tab): ItemStack {
        val stack = ItemStack(type.icon)
        stack.set(DataComponents.CUSTOM_NAME, line("§e§l${type.label} TMs"))
        val count = visibleItems(type.scope!!).size
        stack.set(DataComponents.LORE, ItemLore(listOf(
            line("§7$count moves available.") as Component,
            line("§8Click to browse.") as Component,
        )))
        return stack
    }

    private fun tmBackStack(type: Tab): ItemStack {
        val stack = ItemStack(Items.ARROW)
        stack.set(DataComponents.CUSTOM_NAME, line("§c§l◀ Back to Types"))
        stack.set(DataComponents.LORE, ItemLore(listOf(
            line("§7Viewing: §f${type.label} TMs") as Component,
        )))
        return stack
    }

    /** Component with italics off — vanilla auto-italicizes custom item names and lore. */
    private fun line(s: String): MutableComponent =
        Component.literal(s).setStyle(Style.EMPTY.withItalic(false))

    private fun tabButtonStack(tab: Tab, active: Boolean): ItemStack {
        val stack = ItemStack(tab.icon)
        stack.set(DataComponents.CUSTOM_NAME, line(if (active) "§a§l▶ ${tab.label}" else "§e${tab.label}"))
        stack.set(DataComponents.LORE, ItemLore(listOf(
            line(if (active) "§7Currently viewing." else "§8Click to view this category.") as Component,
        )))
        // A subtle enchant glint marks the active tab even for colour-blind players.
        if (active) stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)
        return stack
    }

    private fun navArrowStack(label: String, currentPage: Int, totalPages: Int): ItemStack {
        val stack = ItemStack(Items.ARROW)
        stack.set(DataComponents.CUSTOM_NAME, line(label))
        stack.set(DataComponents.LORE, ItemLore(listOf(
            line("§7Page ${currentPage + 1} / $totalPages"),
        )))
        return stack
    }

    private fun balanceStack(player: ServerPlayer): ItemStack {
        val bal = EconomyBridge.getBalance(player.uuid)
        val stack = ItemStack(Items.GOLD_INGOT)
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
        val buy = PricingEngine.buyPrice(
            entry.baseBuyPrice, state.stock, entry.baseStock, entry.elasticity,
            entry.effectiveBuyClamp, entry.effectiveMinBuyPrice,
        )
        val sell = PricingEngine.sellPrice(
            entry.baseSellPrice, state.stock, entry.baseStock, entry.elasticity,
            entry.effectiveSellClamp,
        )
        val stockNow = state.stock.toInt()
        val bundle = entry.effectiveBundleSize
        // Show the stack as a full 64 when bundled so the slot reads visually as "a stack".
        val stack = ItemStack(item, bundle.coerceIn(1, 64))
        val lore = mutableListOf<MutableComponent>()
        if (bundle > 1) {
            lore += line("§aBuy: §f\$$buy §8(×$bundle)")
        } else {
            lore += line("§aBuy: §f\$$buy §8(per unit)")
        }
        if (entry.isSellable) {
            lore += line("§cSell: §f\$$sell §8(per unit)")
            lore += line("§7Stock: §f$stockNow §8/ ${entry.baseStock} target")
            lore += line("")
            lore += line("§8Left-click: buy 1  ·  Shift: buy 16")
            lore += line("§8Right-click: sell 1  ·  Shift: sell 64")
        } else if (bundle > 1) {
            lore += line("§8(Buy only — not resold)")
            lore += line("")
            lore += line("§8Left-click: buy $bundle  ·  Shift: buy ${bundle * 16}")
        } else {
            lore += line("§8(Buy only — not resold)")
            lore += line("")
            lore += line("§8Left-click: buy 1  ·  Shift: buy 16")
        }
        stack.set(DataComponents.LORE, ItemLore(lore.map { it as Component }))
        return stack
    }

    // ─── Upgrades tab ────────────────────────────────────────────────────────────────────────

    /** Extra slots already purchased = effective limit minus the free baseline (never negative). */
    private fun homeExtrasPurchased(player: ServerPlayer): Int? {
        val current = HomeUpgradeBridge.currentMaxHomes(player) ?: return null
        val base = HomeUpgradeBridge.baseMaxHomes() ?: return null
        return (current - base).coerceAtLeast(0)
    }

    /** Cost of the player's next home slot, given how many extras they've already bought. */
    private fun nextHomePrice(extrasAlready: Int): Int =
        FIRST_EXTRA_HOME_PRICE + HOME_PRICE_INCREMENT * extrasAlready

    private fun homeUpgradeStack(player: ServerPlayer): ItemStack {
        val stack = ItemStack(Items.RED_BED)
        val current = HomeUpgradeBridge.currentMaxHomes(player)
        val lore = mutableListOf<MutableComponent>()
        if (current == null) {
            stack.set(DataComponents.CUSTOM_NAME, line("§7Extra Home Slot"))
            lore += line("§cUpgrades are temporarily unavailable.")
            stack.set(DataComponents.LORE, ItemLore(lore.map { it as Component }))
            return stack
        }
        stack.set(DataComponents.CUSTOM_NAME, line("§b✦ Extra Home Slot"))
        val price = nextHomePrice(homeExtrasPurchased(player) ?: 0)
        lore += line("§7Set more homes with §f/sethome <name>§7.")
        lore += line("")
        lore += line("§7Your home limit: §f$current")
        lore += line("§7After purchase: §a${current + 1}")
        lore += line("§ePrice: §f\$$price")
        lore += line("")
        lore += line("§8Left-click to purchase.")
        stack.set(DataComponents.LORE, ItemLore(lore.map { it as Component }))
        return stack
    }

    private fun draftUpgradeStack(player: ServerPlayer): ItemStack {
        val stack = ItemStack(Items.WRITABLE_BOOK)
        val lore = mutableListOf<MutableComponent>()
        val owned = DraftSlotBridge.ownedSlots(player.uuid)
        val max = DraftSlotBridge.maxSlots()
        if (owned == null || max == null) {
            stack.set(DataComponents.CUSTOM_NAME, line("§7Draft Team Slot"))
            lore += line("§cUpgrades are temporarily unavailable.")
            stack.set(DataComponents.LORE, ItemLore(lore.map { it as Component }))
            return stack
        }
        stack.set(DataComponents.CUSTOM_NAME, line("§b✦ Draft Team Slot"))
        lore += line("§7A permanent slot for a custom rental")
        lore += line("§7team — see §f/ranked draft§7.")
        lore += line("")
        lore += line("§7Slots owned: §f$owned§7/§f$max")
        if (owned >= max) {
            lore += line("§cYou own the maximum number of slots.")
        } else {
            lore += line("§ePrice: §f\$${DraftSlotBridge.slotCost(owned) ?: "?"}")
            lore += line("")
            lore += line("§8Left-click to purchase.")
        }
        stack.set(DataComponents.LORE, ItemLore(lore.map { it as Component }))
        return stack
    }

    private fun purchaseDraftSlot(player: ServerPlayer) {
        val owned = DraftSlotBridge.ownedSlots(player.uuid)
        val max = DraftSlotBridge.maxSlots()
        if (owned == null || max == null) {
            player.sendSystemMessage(Component.literal("§c[Upgrades] Draft slots are unavailable right now."))
            return
        }
        if (owned >= max) {
            player.sendSystemMessage(Component.literal("§c[Upgrades] You already own all $max draft slots."))
            return
        }
        val price = DraftSlotBridge.slotCost(owned)
        if (price == null) {
            player.sendSystemMessage(Component.literal("§c[Upgrades] Draft slots are unavailable right now."))
            return
        }
        val balance = EconomyBridge.getBalance(player.uuid)
        if (balance < price) {
            player.sendSystemMessage(Component.literal("§c[Upgrades] You need \$$price for the next draft slot — you have \$$balance."))
            return
        }
        // Grant first, then charge — mirrors purchaseHomeSlot so a failed grant never costs money.
        val newOwned = DraftSlotBridge.grantSlot(player.uuid)
        if (newOwned == null) {
            player.sendSystemMessage(Component.literal("§c[Upgrades] Couldn't grant the draft slot — no charge was made. Tell an admin."))
            return
        }
        if (!EconomyBridge.withdraw(player.uuid, price)) {
            CobblemonMarket.logger.warn(
                "Upgrades: granted draft slot $newOwned to ${player.gameProfile.name} but withdraw of \$$price failed",
            )
            player.sendSystemMessage(Component.literal("§c[Upgrades] Payment failed after the upgrade was applied — please tell an admin."))
            return
        }
        player.sendSystemMessage(Component.literal(
            "§a[Upgrades] Purchased draft team slot §f$newOwned§a for \$$price! §7Fill it with §f/ranked draft create§7."))
    }

    private fun purchaseHomeSlot(player: ServerPlayer) {
        val current = HomeUpgradeBridge.currentMaxHomes(player)
        if (current == null) {
            player.sendSystemMessage(Component.literal("§c[Upgrades] Home upgrades are unavailable right now."))
            return
        }
        val price = nextHomePrice(homeExtrasPurchased(player) ?: 0)

        val balance = EconomyBridge.getBalance(player.uuid)
        if (balance < price) {
            player.sendSystemMessage(Component.literal("§c[Upgrades] You need \$$price for the next home slot — you have \$$balance."))
            return
        }
        // Grant first (verified inside the bridge), then charge. If the grant silently fails we
        // never take the player's money.
        if (!HomeUpgradeBridge.grantHomeSlot(player, current + 1)) {
            player.sendSystemMessage(Component.literal("§c[Upgrades] Couldn't grant the home slot — no charge was made. Tell an admin."))
            return
        }
        if (!EconomyBridge.withdraw(player.uuid, price)) {
            CobblemonMarket.logger.warn(
                "Upgrades: granted home slot ${current + 1} to ${player.gameProfile.name} but withdraw of \$$price failed",
            )
            player.sendSystemMessage(Component.literal("§c[Upgrades] Payment failed after the upgrade was applied — please tell an admin."))
            return
        }
        val newMax = HomeUpgradeBridge.currentMaxHomes(player) ?: (current + 1)
        player.sendSystemMessage(Component.literal(
            "§a[Upgrades] Purchased an extra home slot for \$$price! §7You can now set §f$newMax§7 homes."))
    }

    // ─── Menu implementation ─────────────────────────────────────────────────────────────────

    private class Impl(
        syncId: Int,
        inv: Inventory,
        private val container: Container,
        private val viewer: ServerPlayer,
        private val tabs: List<Tab>,
        private val tmMerchant: Boolean = false,
    ) : ChestMenu(MenuType.GENERIC_9x6, syncId, inv, container, ROWS) {

        private var activeTab: Int = 0
        private var page: Int = 0

        /** TM Merchant only: `null` = showing the type picker; else the selected type index. */
        private var selectedType: Int? = null

        override fun clicked(slotId: Int, button: Int, clickType: ClickType, player: Player) {
            // Drag, number-key swaps, and double-click "collect all" are blocked entirely — they'd
            // pull the chest's display copies into the player's cursor/inventory (free extraction).
            if (clickType == ClickType.QUICK_CRAFT || clickType == ClickType.SWAP ||
                clickType == ClickType.PICKUP_ALL) return
            if (slotId !in 0 until SLOTS) {
                super.clicked(slotId, button, clickType, player)
                return
            }
            if (slotId == BALANCE_SLOT) return

            if (tmMerchant) {
                clickedTmMerchant(slotId, button, clickType, player)
                return
            }

            // ── Nav row ──
            if (slotId < FIRST_ITEM_SLOT) {
                // Tab switch (only when the tab bar is shown).
                if (tabs.size > 1 && slotId in tabs.indices) {
                    if (slotId != activeTab) {
                        activeTab = slotId
                        page = 0
                        populate(container, viewer, tabs, activeTab, page)
                        broadcastChanges()
                    }
                    return
                }
                // Page arrows.
                if (slotId == prevSlot(tabs) || slotId == NEXT_SLOT) {
                    val pages = pageCount(tabs[activeTab])
                    val next = if (slotId == NEXT_SLOT) page + 1 else page - 1
                    if (next in 0 until pages) {
                        page = next
                        populate(container, viewer, tabs, activeTab, page)
                        broadcastChanges()
                    }
                }
                return
            }

            val sp = player as? ServerPlayer ?: return
            val tab = tabs[activeTab]

            // ── Upgrades tab ──
            if (tab.scope == null) {
                // Only a plain left-click buys; ignore other clicks so it can't double-fire.
                if ((slotId == HOME_UPGRADE_SLOT || slotId == DRAFT_UPGRADE_SLOT) &&
                    button == 0 && (clickType == ClickType.PICKUP || clickType == ClickType.QUICK_MOVE)
                ) {
                    if (slotId == HOME_UPGRADE_SLOT) purchaseHomeSlot(sp) else purchaseDraftSlot(sp)
                    populate(container, viewer, tabs, activeTab, page)
                    broadcastChanges()
                }
                return
            }

            // ── Item tab ──
            val items = visibleItems(tab.scope)
            val itemIndex = page * PAGE_SIZE + (slotId - FIRST_ITEM_SLOT)
            if (itemIndex !in items.indices) return
            val (itemId, entry) = items[itemIndex]

            val (action, qty) = when {
                button == 0 && clickType == ClickType.PICKUP      -> "buy" to 1
                button == 0 && clickType == ClickType.QUICK_MOVE  -> "buy" to 16
                button == 1 && clickType == ClickType.PICKUP      -> "sell" to 1
                button == 1 && clickType == ClickType.QUICK_MOVE  -> "sell" to 64
                else -> return
            }
            if (action == "sell" && !entry.isSellable) {
                sp.sendSystemMessage(Component.literal("§c[Market] This vendor doesn't buy items back."))
                return
            }

            // Vendors check for a matching voucher before charging money (single-item buys only).
            if (action == "buy" && qty == 1 && tryRedeemVoucher(sp, tab.scope, itemId, entry)) {
                populate(container, viewer, tabs, activeTab, page)
                broadcastChanges()
                return
            }

            val result: TradeResult = if (action == "buy") TradeOps.buy(sp, itemId, qty) else TradeOps.sell(sp, itemId, qty)
            val delivered = if (action == "buy") qty * entry.effectiveBundleSize else qty
            reportTrade(sp, action, itemId, delivered, result)
            populate(container, viewer, tabs, activeTab, page)
            broadcastChanges()
        }

        /** TM Merchant click handling: type picker → per-type TR list, Back, pages, buy/sell. */
        private fun clickedTmMerchant(slotId: Int, button: Int, clickType: ClickType, player: Player) {
            val sel = selectedType
            if (sel == null) {
                // Picker: click a type icon to drill in.
                if (slotId >= FIRST_ITEM_SLOT) {
                    val idx = slotId - FIRST_ITEM_SLOT
                    if (idx in tabs.indices) {
                        selectedType = idx
                        page = 0
                        populateTmMerchant(container, viewer, tabs, selectedType, page)
                        broadcastChanges()
                    }
                }
                return
            }

            // Type view nav row.
            if (slotId < FIRST_ITEM_SLOT) {
                if (slotId == BACK_SLOT) {
                    selectedType = null
                    page = 0
                    populateTmMerchant(container, viewer, tabs, selectedType, page)
                    broadcastChanges()
                    return
                }
                if (slotId == TABBED_PREV_SLOT || slotId == NEXT_SLOT) {
                    val all = visibleItems(tabs[sel].scope!!)
                    val pages = ((all.size + PAGE_SIZE - 1) / PAGE_SIZE).coerceAtLeast(1)
                    val next = if (slotId == NEXT_SLOT) page + 1 else page - 1
                    if (next in 0 until pages) {
                        page = next
                        populateTmMerchant(container, viewer, tabs, selectedType, page)
                        broadcastChanges()
                    }
                }
                return
            }

            // Type view content: buy/sell a TR (TRs are buy-only, but keep the shared semantics).
            val sp = player as? ServerPlayer ?: return
            val items = visibleItems(tabs[sel].scope!!)
            val itemIndex = page * PAGE_SIZE + (slotId - FIRST_ITEM_SLOT)
            if (itemIndex !in items.indices) return
            val (itemId, entry) = items[itemIndex]

            val (action, qty) = when {
                button == 0 && clickType == ClickType.PICKUP      -> "buy" to 1
                button == 0 && clickType == ClickType.QUICK_MOVE  -> "buy" to 16
                button == 1 && clickType == ClickType.PICKUP      -> "sell" to 1
                button == 1 && clickType == ClickType.QUICK_MOVE  -> "sell" to 64
                else -> return
            }
            if (action == "sell" && !entry.isSellable) {
                sp.sendSystemMessage(Component.literal("§c[Market] This vendor doesn't buy items back."))
                return
            }
            // TR vendors check for a `tr` voucher before charging money (single-item buys only).
            if (action == "buy" && qty == 1 && tryRedeemVoucher(sp, tabs[sel].scope, itemId, entry)) {
                populateTmMerchant(container, viewer, tabs, selectedType, page)
                broadcastChanges()
                return
            }
            val result: TradeResult = if (action == "buy") TradeOps.buy(sp, itemId, qty) else TradeOps.sell(sp, itemId, qty)
            val delivered = if (action == "buy") qty * entry.effectiveBundleSize else qty
            reportTrade(sp, action, itemId, delivered, result)
            populateTmMerchant(container, viewer, tabs, selectedType, page)
            broadcastChanges()
        }

        /**
         * Shift-click semantics. Shift-clicking a CHEST slot is a no-op (don't let players take
         * display items). Shift-clicking an INVENTORY slot sells the whole stack at the current
         * per-unit price — but only when the active tab is an item tab that carries that item and
         * buys it back. Returning [ItemStack.EMPTY] tells vanilla's shift-loop nothing moved.
         */
        override fun quickMoveStack(player: Player, slotIndex: Int): ItemStack {
            if (slotIndex in 0 until SLOTS) return ItemStack.EMPTY
            if (tmMerchant) return ItemStack.EMPTY  // TM Merchant carries only buy-only TRs.
            val sp = player as? ServerPlayer ?: return ItemStack.EMPTY
            val scope = tabs[activeTab].scope ?: return ItemStack.EMPTY  // Upgrades tab: no selling.
            val slot = slots.getOrNull(slotIndex) ?: return ItemStack.EMPTY
            val stack = slot.item
            if (stack.isEmpty) return ItemStack.EMPTY
            val itemId = BuiltInRegistries.ITEM.getKey(stack.item).toString()
            val entry = CobblemonMarket.items[itemId] ?: return ItemStack.EMPTY
            // Refuse shift-sell against a tab that doesn't carry this item, or a buy-only entry.
            if (entry.vendorScope != scope || !entry.isSellable) return ItemStack.EMPTY
            val qty = stack.count
            val result = TradeOps.sell(sp, itemId, qty)
            reportTrade(sp, "sell", itemId, qty, result)
            populate(container, viewer, tabs, activeTab, page)
            broadcastChanges()
            return ItemStack.EMPTY
        }
    }

    private fun reportTrade(
        player: ServerPlayer,
        action: String,
        itemId: String,
        units: Int,
        result: TradeResult,
    ) {
        val name = formatItemName(itemId)
        val msg = when (result) {
            is TradeResult.Success -> Component.literal("§a[Market] ${action.replaceFirstChar(Char::uppercase)} $units× $name — \$${result.totalPrice} §7(stock → ${result.newStock.toInt()})")
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
