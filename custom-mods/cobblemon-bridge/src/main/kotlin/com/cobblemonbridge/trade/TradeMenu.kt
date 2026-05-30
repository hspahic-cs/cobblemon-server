package com.cobblemonbridge.trade

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.item.PokemonItem
import com.cobblemonbridge.economy.EconomyBridge
import com.mojang.authlib.properties.PropertyMap
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.Container
import net.minecraft.world.SimpleMenuProvider
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.inventory.ClickType
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.ItemLore
import net.minecraft.world.item.component.ResolvableProfile
import java.util.Optional
import java.util.UUID

/**
 * Shared 6×9 chest GUI for [TradeSession]. Both players open a `ChestMenu` backed by the
 * session's single [TradeSession.container], so vanilla container-sync pushes updates to
 * both clients in real time.
 *
 * Slot layout (cols left→right 0..8, rows top→bottom 0..5):
 * ```
 *   0:  [P1 head ] .       .       .     [status] .       .       .     [P2 head ]
 *   1:  [+mon P1] [+$  P1] .       .       .       .       .     [+$  P2] [+mon P2]
 *   2:  [mon P1 ] [mon P1] [itm P1] [itm P1] .     [itm P2] [itm P2] [mon P2] [mon P2]
 *   3:  [mon P1 ] [mon P1] [itm P1] [itm P1] .     [itm P2] [itm P2] [mon P2] [mon P2]
 *   4:  [mon P1 ] [mon P1] [itm P1] [itm P1] .     [itm P2] [itm P2] [mon P2] [mon P2]
 *   5:  [P1 conf] [P1 $  ] .       .     [help  ] .       .     [P2 $  ] [P2 conf]
 * ```
 * Pokémon slots are display-only (PokemonItem); clicking one un-stages that mon. Item slots
 * are full drag-and-drop, BUT ownership is enforced — only the side's own player can put
 * items into their slots. Divider column 4 is gray panes (click no-op).
 *
 * Money is set via `/trade money <amount>` OR by clicking the +$ button (left = +100,
 * shift-left = +1000, right = -100, shift-right = clear).
 *
 * Pokémon are staged via the +mon button — left-click stages the lowest-index party slot
 * that isn't already staged. Right-click stages by walking the party in reverse order. No
 * sub-menu (sub-menus close the trade window and would cancel the session via
 * [TradeManager.handleMenuClose]).
 */
object TradeMenu {

    private const val ROWS = 6
    private const val SLOTS = ROWS * 9

    // Header / status
    private const val P1_HEAD_SLOT = 0
    private const val STATUS_SLOT = 4
    private const val P2_HEAD_SLOT = 8

    // Buttons row
    private const val P1_ADD_MON = 9
    private const val P1_ADD_MONEY = 10
    private const val P2_ADD_MONEY = 16
    private const val P2_ADD_MON = 17

    // Pokémon display slots (6 per side)
    private val P1_POKEMON_SLOTS = listOf(18, 19, 27, 28, 36, 37)
    private val P2_POKEMON_SLOTS = listOf(25, 26, 34, 35, 43, 44)

    // Item drag-and-drop slots (6 per side)
    private val P1_ITEM_SLOTS = listOf(20, 21, 29, 30, 38, 39)
    private val P2_ITEM_SLOTS = listOf(23, 24, 32, 33, 41, 42)

    // Divider column (gray panes)
    private val DIVIDER_SLOTS = listOf(22, 31, 40)

    // Bottom row
    private const val P1_CONFIRM = 45
    private const val P1_MONEY_DISPLAY = 46
    private const val HELP_SLOT = 49
    private const val P2_MONEY_DISPLAY = 52
    private const val P2_CONFIRM = 53

    /** Live viewer registry — used by [refresh] / [closeFor] / [viewerOf] to push updates +
     *  close menus without needing a server lookup. Cleared on menu close. */
    private val viewers: MutableMap<UUID, ServerPlayer> = mutableMapOf()

    /** Item slot ids owned by [player]. Used by [TradeManager.refundAndClose] to know whose
     *  items to refund where. */
    fun itemSlotsFor(playerUuid: UUID): List<Int> {
        // We don't know p1/p2 here without the session — caller looks it up. This helper
        // exists for symmetry; cancel/execute paths call the overload below.
        return P1_ITEM_SLOTS  // safe default; overridden in refund path via session lookup
    }

    /** Resolves which player UUID owns each item slot for [session]. Used during refund/exec. */
    fun itemSlotsFor(session: TradeSession, playerUuid: UUID): List<Int> = when (playerUuid) {
        session.p1Uuid -> P1_ITEM_SLOTS
        session.p2Uuid -> P2_ITEM_SLOTS
        else -> emptyList()
    }

    /** Looks up the current online ServerPlayer for the given UUID, scoped to this session. */
    fun viewerOf(session: TradeSession, uuid: UUID): ServerPlayer? = viewers[uuid]

    fun openFor(p1: ServerPlayer, p2: ServerPlayer, session: TradeSession) {
        seedDecor(session)
        viewers[p1.uuid] = p1
        viewers[p2.uuid] = p2

        val provider = SimpleMenuProvider(
            { syncId, inv, viewer ->
                Impl(syncId, inv, session.container, session, viewer as ServerPlayer)
            },
            Component.literal("§0§lTrade — ${session.p1Name} ↔ ${session.p2Name}"),
        )
        p1.openMenu(provider)
        p2.openMenu(provider)
        refresh(session)
    }

    fun closeFor(session: TradeSession) {
        for (uuid in listOf(session.p1Uuid, session.p2Uuid)) {
            val player = viewers.remove(uuid) ?: continue
            // Only close OUR menu, not whatever the player has open now (in case they got
            // bounced out earlier). We can't easily test that, but closeContainer() is safe
            // — at worst it closes nothing.
            player.closeContainer()
        }
    }

    /** Re-renders every display tile (heads, status, money, confirm, pokemon icons) from the
     *  current [session] state, then pushes the container to both clients. */
    fun refresh(session: TradeSession) {
        // Header tiles
        session.container.setItem(P1_HEAD_SLOT, headerStack(session, session.p1Uuid, session.p1Name, session.offer1))
        session.container.setItem(P2_HEAD_SLOT, headerStack(session, session.p2Uuid, session.p2Name, session.offer2))
        session.container.setItem(STATUS_SLOT, statusStack(session))

        // Action buttons (static labels — interaction handled in clicked())
        session.container.setItem(P1_ADD_MON, addMonStack())
        session.container.setItem(P1_ADD_MONEY, addMoneyStack())
        session.container.setItem(P2_ADD_MON, addMonStack())
        session.container.setItem(P2_ADD_MONEY, addMoneyStack())

        // Pokémon display slots — fill with PokemonItem renders + name + level lore
        renderPokemonSlots(session, session.offer1, P1_POKEMON_SLOTS)
        renderPokemonSlots(session, session.offer2, P2_POKEMON_SLOTS)

        // Money displays
        session.container.setItem(P1_MONEY_DISPLAY, moneyDisplayStack(session.offer1.money))
        session.container.setItem(P2_MONEY_DISPLAY, moneyDisplayStack(session.offer2.money))

        // Confirm tiles
        session.container.setItem(P1_CONFIRM, confirmStack(session.offer1.confirmed))
        session.container.setItem(P2_CONFIRM, confirmStack(session.offer2.confirmed))

        session.container.setChanged()
        // Push to both clients
        for (uuid in listOf(session.p1Uuid, session.p2Uuid)) {
            val player = viewers[uuid] ?: continue
            (player.containerMenu as? Impl)?.broadcastChanges()
        }
    }

    // ─── decor / tile builders ──────────────────────────────────────────────────

    private fun seedDecor(session: TradeSession) {
        val pane = ItemStack(Items.GRAY_STAINED_GLASS_PANE).also {
            it.set(DataComponents.CUSTOM_NAME, line(" "))
        }
        for (slot in 0 until SLOTS) session.container.setItem(slot, pane)
        // Item slots get cleared (vacant ItemStack.EMPTY) so they're drop-targets
        for (slot in P1_ITEM_SLOTS + P2_ITEM_SLOTS) {
            session.container.setItem(slot, ItemStack.EMPTY)
        }
        // Help tile
        session.container.setItem(HELP_SLOT, helpStack())
    }

    private fun line(s: String): MutableComponent =
        Component.literal(s).setStyle(Style.EMPTY.withItalic(false))

    private fun headerStack(session: TradeSession, uuid: UUID, name: String, offer: TradeOffer): ItemStack {
        val stack = ItemStack(Items.PLAYER_HEAD)
        stack.set(DataComponents.PROFILE, ResolvableProfile(
            Optional.of(name), Optional.of(uuid), PropertyMap(),
        ))
        stack.set(DataComponents.CUSTOM_NAME, line("§e§l$name"))
        val itemCount = itemSlotsFor(session, uuid).count { !session.container.getItem(it).isEmpty }
        stack.set(DataComponents.LORE, ItemLore(listOf(
            line("§7Pokémon offered: §f${offer.pokemon.size}"),
            line("§7Items offered:   §f$itemCount"),
            line("§7Money offered:   §6\$${offer.money}"),
            line(""),
            line(if (offer.confirmed) "§a✓ Confirmed" else "§7Not confirmed yet"),
        )))
        return stack
    }

    private fun statusStack(session: TradeSession): ItemStack {
        val (item, name) = when {
            session.bothConfirmed() -> Items.LIME_DYE to "§a§lExecuting…"
            session.offer1.confirmed || session.offer2.confirmed -> Items.YELLOW_DYE to "§e§lOne side confirmed"
            else -> Items.LIGHT_GRAY_DYE to "§7Awaiting both confirms"
        }
        val stack = ItemStack(item)
        stack.set(DataComponents.CUSTOM_NAME, line(name))
        return stack
    }

    private fun addMonStack(): ItemStack {
        val stack = ItemStack(Items.LIME_CONCRETE)
        stack.set(DataComponents.CUSTOM_NAME, line("§a§l+ Stage Pokémon"))
        stack.set(DataComponents.LORE, ItemLore(listOf(
            line("§7Left-click §f→ stage next un-staged party slot"),
            line("§7Right-click §f→ stage from highest party slot first"),
            line("§7Click a staged Pokémon below to remove it."),
        )))
        return stack
    }

    private fun addMoneyStack(): ItemStack {
        val stack = ItemStack(Items.GOLD_NUGGET)
        stack.set(DataComponents.CUSTOM_NAME, line("§6§l+ Add Money"))
        stack.set(DataComponents.LORE, ItemLore(listOf(
            line("§7Left-click §f→ +\$100"),
            line("§7Shift-left §f→ +\$1,000"),
            line("§7Right-click §f→ -\$100"),
            line("§7Shift-right §f→ clear"),
            line("§7Or type §f/trade money <amount>§7."),
        )))
        return stack
    }

    private fun moneyDisplayStack(amount: Int): ItemStack {
        val stack = ItemStack(Items.GOLD_INGOT)
        stack.set(DataComponents.CUSTOM_NAME, line("§6§l\$$amount"))
        if (amount == 0) {
            stack.set(DataComponents.LORE, ItemLore(listOf(line("§8(no money offered)"))))
        }
        return stack
    }

    private fun confirmStack(confirmed: Boolean): ItemStack {
        val stack = ItemStack(if (confirmed) Items.LIME_WOOL else Items.RED_WOOL)
        stack.set(DataComponents.CUSTOM_NAME, line(
            if (confirmed) "§a§l✓ Confirmed (click to un-confirm)"
            else "§c§lClick to Confirm"
        ))
        stack.set(DataComponents.LORE, ItemLore(listOf(
            line("§7Both sides must confirm to execute."),
            line("§7Any change to either offer un-confirms both."),
        )))
        return stack
    }

    private fun helpStack(): ItemStack {
        val stack = ItemStack(Items.WRITABLE_BOOK)
        stack.set(DataComponents.CUSTOM_NAME, line("§e§lHow to trade"))
        stack.set(DataComponents.LORE, ItemLore(listOf(
            line("§7• Drag items into §fyour§7 item slots."),
            line("§7• Use §f+ Stage Pokémon§7 to add party mons."),
            line("§7• Use §f+ Add Money§7 or §f/trade money N§7."),
            line("§7• Both sides click §a§lConfirm§7 to execute."),
            line("§7• §f/trade cancel§7 or close window to abort."),
            line("§7• Over-cap mons blocked; party-full mons go to PC."),
        )))
        return stack
    }

    private fun renderPokemonSlots(session: TradeSession, offer: TradeOffer, slots: List<Int>) {
        for ((i, slot) in slots.withIndex()) {
            val mon = offer.pokemon.getOrNull(i)
            if (mon == null) {
                val placeholder = ItemStack(Items.LIGHT_BLUE_STAINED_GLASS_PANE)
                placeholder.set(DataComponents.CUSTOM_NAME, line("§8Empty Pokémon slot"))
                session.container.setItem(slot, placeholder)
            } else {
                val stack = try {
                    PokemonItem.from(mon)
                } catch (e: Throwable) {
                    ItemStack(Items.PAPER).also {
                        it.set(DataComponents.CUSTOM_NAME, line("§f${mon.species.name}"))
                    }
                }
                stack.set(DataComponents.CUSTOM_NAME, line("§b${mon.species.name} §7L${mon.level}"))
                stack.set(DataComponents.LORE, ItemLore(listOf(
                    line("§7HP: §f${mon.currentHealth}/${mon.maxHealth}"),
                    line(""),
                    line("§8Click to remove from offer."),
                )))
                session.container.setItem(slot, stack)
            }
        }
    }

    // ─── Impl (per-player ChestMenu instance) ───────────────────────────────────

    private class Impl(
        syncId: Int,
        inv: Inventory,
        container: Container,
        private val session: TradeSession,
        private val viewer: ServerPlayer,
    ) : ChestMenu(MenuType.GENERIC_9x6, syncId, inv, container, ROWS) {

        private fun isP1(): Boolean = viewer.uuid == session.p1Uuid

        override fun clicked(slotId: Int, button: Int, clickType: ClickType, player: Player) {
            // Reject anything but the trade participants (shouldn't happen — menu opened only
            // for the two).
            val sp = player as? ServerPlayer ?: return
            if (sp.uuid != session.p1Uuid && sp.uuid != session.p2Uuid) return

            // Out-of-range = vanilla (this means player inventory slots; vanilla handles).
            if (slotId !in 0 until SLOTS) { super.clicked(slotId, button, clickType, player); return }

            // Decor / display tiles — no-op.
            if (slotId in DIVIDER_SLOTS ||
                slotId == P1_HEAD_SLOT || slotId == P2_HEAD_SLOT ||
                slotId == STATUS_SLOT || slotId == HELP_SLOT ||
                slotId == P1_MONEY_DISPLAY || slotId == P2_MONEY_DISPLAY) return

            // Confirm buttons — own-side only.
            if (slotId == P1_CONFIRM) { if (isP1()) TradeManager.toggleConfirm(sp); return }
            if (slotId == P2_CONFIRM) { if (!isP1()) TradeManager.toggleConfirm(sp); return }

            // Add-Pokémon buttons.
            if (slotId == P1_ADD_MON) { if (isP1()) handleAddMon(sp, button); return }
            if (slotId == P2_ADD_MON) { if (!isP1()) handleAddMon(sp, button); return }

            // Add-Money buttons.
            if (slotId == P1_ADD_MONEY) { if (isP1()) handleAddMoney(sp, button, clickType); return }
            if (slotId == P2_ADD_MONEY) { if (!isP1()) handleAddMoney(sp, button, clickType); return }

            // Pokémon display slots — clicking removes from offer.
            val ownPokemonSlots = if (isP1()) P1_POKEMON_SLOTS else P2_POKEMON_SLOTS
            if (slotId in ownPokemonSlots) {
                val offerIdx = ownPokemonSlots.indexOf(slotId)
                TradeManager.unstagePokemon(sp, offerIdx)
                return
            }
            // Other side's pokemon slot — no-op.
            val otherPokemonSlots = if (isP1()) P2_POKEMON_SLOTS else P1_POKEMON_SLOTS
            if (slotId in otherPokemonSlots) return

            // Item slots — enforce ownership.
            val ownItemSlots = if (isP1()) P1_ITEM_SLOTS else P2_ITEM_SLOTS
            val otherItemSlots = if (isP1()) P2_ITEM_SLOTS else P1_ITEM_SLOTS
            if (slotId in ownItemSlots) {
                super.clicked(slotId, button, clickType, player)
                TradeManager.onItemSlotChanged(session)
                return
            }
            if (slotId in otherItemSlots) return  // can't touch the other side's items

            // Fallback (shouldn't reach).
        }

        override fun quickMoveStack(player: Player, slotIndex: Int): ItemStack {
            // Shift-clicking from PLAYER inventory → into the trade window. Only let it land
            // in OUR item slots, never the other side's, never pokemon/decor slots.
            val sp = player as? ServerPlayer ?: return ItemStack.EMPTY
            if (sp.uuid != session.p1Uuid && sp.uuid != session.p2Uuid) return ItemStack.EMPTY
            val mine = if (sp.uuid == session.p1Uuid) P1_ITEM_SLOTS else P2_ITEM_SLOTS

            // Slot is in player inv if slotIndex >= SLOTS (vanilla offset).
            if (slotIndex >= SLOTS) {
                // Move from inv into first empty owned item slot.
                val slot = slots.getOrNull(slotIndex) ?: return ItemStack.EMPTY
                val stack = slot.item
                if (stack.isEmpty) return ItemStack.EMPTY
                val target = mine.firstOrNull { session.container.getItem(it).isEmpty }
                if (target == null) return ItemStack.EMPTY  // no room
                session.container.setItem(target, stack.copy())
                slot.set(ItemStack.EMPTY)
                TradeManager.onItemSlotChanged(session)
                return ItemStack.EMPTY
            }
            // Shift-click FROM the chest grid: pull back into player inv only if it's our own
            // item slot.
            if (slotIndex !in mine) return ItemStack.EMPTY
            val stack = session.container.getItem(slotIndex)
            if (stack.isEmpty) return ItemStack.EMPTY
            val added = sp.inventory.add(stack.copy())
            if (added) {
                session.container.setItem(slotIndex, ItemStack.EMPTY)
                TradeManager.onItemSlotChanged(session)
            }
            return ItemStack.EMPTY
        }

        override fun removed(player: Player) {
            super.removed(player)
            val sp = player as? ServerPlayer ?: return
            // Menu close = cancel (unless we're tearing it down via closeFor as part of
            // execute/cancel, in which case the session has already been removed and
            // handleMenuClose is a no-op via sessionFor returning null).
            TradeManager.handleMenuClose(sp)
        }

        private fun handleAddMon(sp: ServerPlayer, button: Int) {
            val party = Cobblemon.storage.getParty(sp)
            val offer = session.offerOf(sp.uuid) ?: return
            val stagedUuids = offer.pokemonUuids().toSet()
            val range = if (button == 0) 0 until party.size() else (party.size() - 1) downTo 0
            for (i in range) {
                val mon = party.get(i) ?: continue
                if (mon.uuid in stagedUuids) continue
                TradeManager.stagePokemon(sp, i)
                return
            }
            sp.sendSystemMessage(Component.literal(
                "§7[Trade] No more party Pokémon to stage."
            ))
        }

        private fun handleAddMoney(sp: ServerPlayer, button: Int, clickType: ClickType) {
            val offer = session.offerOf(sp.uuid) ?: return
            val delta = when {
                button == 0 && clickType == ClickType.PICKUP -> +100
                button == 0 && clickType == ClickType.QUICK_MOVE -> +1000
                button == 1 && clickType == ClickType.PICKUP -> -100
                button == 1 && clickType == ClickType.QUICK_MOVE -> -offer.money  // shift-right = clear
                else -> 0
            }
            if (delta == 0) return
            TradeManager.setMoney(sp, offer.money + delta)
        }
    }
}
