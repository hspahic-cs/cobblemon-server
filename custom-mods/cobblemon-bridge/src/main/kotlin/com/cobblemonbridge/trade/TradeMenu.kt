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
 *   0:  [P1 head]  [P1p0]   [P1p1]   [P1p2]   [status] [P2p2]   [P2p1]   [P2p0]   [P2 head]
 *   1:  [P1 conf]  [P1p3]   [P1p4]   [P1p5]   [help]   [P2p5]   [P2p4]   [P2p3]   [P2 conf]
 *   2:  [stg P1]   [stg P1] [itm P1] [itm P1] .        [itm P2] [itm P2] [stg P2] [stg P2]
 *   3:  [stg P1]   [stg P1] [itm P1] [itm P1] .        [itm P2] [itm P2] [stg P2] [stg P2]
 *   4:  [stg P1]   [stg P1] [itm P1] [itm P1] .        [itm P2] [itm P2] [stg P2] [stg P2]
 *   5:  [P1 +$]    [P1 $]   .        .        .        .        .        [P2 $]   [P2 +$]
 * ```
 *
 * **0.7.14 layout change** — replaced the opaque "+ Stage Pokémon" button with a direct
 * per-player party view in rows 0-1. Each player's 6 party slots flank their head:
 * P1's party 0-2 are slots 1-3, party 3-5 are slots 10-12; P2 is mirrored (slots 7→5 +
 * 14→16) so each player's party slot 0 is closest to their head. Click your party tile to
 * stage that Pokémon; click again (or click the staged tile in rows 2-4) to unstage. Tiles
 * already in the trade render with strikethrough lore + an "Already in trade" tag so
 * players never wonder why a click did nothing.
 *
 * Confirm tiles moved from row 5 (slots 45/53) to row 1 (slots 9/17) — same column as
 * each head, easier to mentally bind "my side". +$ buttons moved to row 5 (slots 45/53)
 * to fill the bottom row alongside the money displays.
 *
 * Pokémon staged-area slots (rows 2-4) are display-only (PokemonItem); clicking one
 * unstages. Item slots are full drag-and-drop, BUT ownership is enforced — only the
 * side's own player can put items into their slots. Divider column 4 is gray panes.
 *
 * Money is set via `/trade money <amount>` OR by clicking the +$ button (left = +100,
 * shift-left = +1000, right = -100, shift-right = clear). Each player's +$ tile shows
 * their CURRENT balance in lore so a clamp-to-balance silently zeroing the offer
 * (the 0.7.13 silent-failure bug on $0 accounts) is now visible to the player.
 */
object TradeMenu {

    private const val ROWS = 6
    private const val SLOTS = ROWS * 9

    // Header / status (row 0 ends)
    private const val P1_HEAD_SLOT = 0
    private const val STATUS_SLOT = 4
    private const val P2_HEAD_SLOT = 8

    // Per-player party tiles — rows 0-1, flanking each head. Index 0 = closest to head.
    private val P1_PARTY_SLOTS = listOf(1, 2, 3, 10, 11, 12)
    private val P2_PARTY_SLOTS = listOf(7, 6, 5, 14, 15, 16)

    // Confirm + help (row 1 ends)
    private const val P1_CONFIRM = 9
    private const val HELP_SLOT = 13
    private const val P2_CONFIRM = 17

    // Pokémon staged-area display slots (6 per side) — rows 2-4
    private val P1_POKEMON_SLOTS = listOf(18, 19, 27, 28, 36, 37)
    private val P2_POKEMON_SLOTS = listOf(25, 26, 34, 35, 43, 44)

    // Item drag-and-drop slots (6 per side) — rows 2-4
    private val P1_ITEM_SLOTS = listOf(20, 21, 29, 30, 38, 39)
    private val P2_ITEM_SLOTS = listOf(23, 24, 32, 33, 41, 42)

    // Divider column (gray panes)
    private val DIVIDER_SLOTS = listOf(22, 31, 40)

    // Bottom row (money)
    private const val P1_ADD_MONEY = 45
    private const val P1_MONEY_DISPLAY = 46
    private const val P2_MONEY_DISPLAY = 52
    private const val P2_ADD_MONEY = 53

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

    /** Re-renders every display tile (heads, status, money, confirm, pokemon icons, party
     *  tiles) from the current [session] state, then pushes the container to both clients. */
    fun refresh(session: TradeSession) {
        // Header tiles
        session.container.setItem(P1_HEAD_SLOT, headerStack(session, session.p1Uuid, session.p1Name, session.offer1))
        session.container.setItem(P2_HEAD_SLOT, headerStack(session, session.p2Uuid, session.p2Name, session.offer2))
        session.container.setItem(STATUS_SLOT, statusStack(session))

        // Per-player party tiles in rows 0-1 (replaces the 0.7.13 +mon button).
        renderParty(session, session.p1Uuid, P1_PARTY_SLOTS)
        renderParty(session, session.p2Uuid, P2_PARTY_SLOTS)

        // Money buttons (now in row 5, with per-player balance baked into the lore so the
        // 0.7.13 silent-clamp behavior is visible to the player).
        session.container.setItem(P1_ADD_MONEY, addMoneyStack(session.p1Uuid, session.offer1.money))
        session.container.setItem(P2_ADD_MONEY, addMoneyStack(session.p2Uuid, session.offer2.money))

        // Staged-area Pokémon slots (rows 2-4) — fill with PokemonItem renders + name + level
        renderPokemonSlots(session, session.offer1, P1_POKEMON_SLOTS)
        renderPokemonSlots(session, session.offer2, P2_POKEMON_SLOTS)

        // Money displays
        session.container.setItem(P1_MONEY_DISPLAY, moneyDisplayStack(session.offer1.money))
        session.container.setItem(P2_MONEY_DISPLAY, moneyDisplayStack(session.offer2.money))

        // Confirm tiles (now in row 1, adjacent to each head)
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

    /** Per-player money button — the lore shows the viewer's current balance so the
     *  setMoney clamp behavior is visible. Pre-0.7.14 this was a shared static stack and the
     *  clamp would silently zero a $0 player's offer with no in-menu hint as to why. */
    private fun addMoneyStack(playerUuid: UUID, currentOffer: Int): ItemStack {
        val stack = ItemStack(Items.GOLD_NUGGET)
        stack.set(DataComponents.CUSTOM_NAME, line("§6§l+ Add Money"))
        val balance = EconomyBridge.getBalance(playerUuid)
        val lore = mutableListOf<Component>(
            line("§7Your balance: §6\$$balance"),
            line("§7Offered: §6\$$currentOffer"),
            line(""),
            line("§7Left-click §f→ +\$100"),
            line("§7Shift-left §f→ +\$1,000"),
            line("§7Right-click §f→ -\$100"),
            line("§7Shift-right §f→ clear"),
            line("§7Or type §f/trade money <amount>§7."),
        )
        if (balance == 0) {
            lore.add(line(""))
            lore.add(line("§c§lYour balance is \$0 — offers will clamp to 0."))
        }
        stack.set(DataComponents.LORE, ItemLore(lore))
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
            line("§7• §fClick your party tiles§7 (rows 1-2) to stage Pokémon."),
            line("§7• §fDrag items§7 into your item slots (middle of menu)."),
            line("§7• §fClick +$ at bottom§7 or §f/trade money N§7 for cash."),
            line("§7• Both sides click §a§lConfirm§7 (next to your head) to execute."),
            line("§7• §f/trade cancel§7 or close window to abort."),
            line("§7• Over-cap mons blocked; party-full mons go to PC."),
        )))
        return stack
    }

    /** Render the 6 party tiles for [partyOwnerUuid] into [slots]. Tiles that the owner has
     *  already staged in the trade get a strikethrough name + "Already in trade" lore so the
     *  player can see at a glance which mons are committed. Empty party slots are gray panes.
     *
     *  If the party owner is offline (shouldn't happen during an active trade — execute
     *  refunds when a player drops — but defensive), the slots get a neutral placeholder. */
    private fun renderParty(session: TradeSession, partyOwnerUuid: UUID, slots: List<Int>) {
        val player = viewers[partyOwnerUuid]
        if (player == null) {
            val placeholder = ItemStack(Items.GRAY_STAINED_GLASS_PANE).also {
                it.set(DataComponents.CUSTOM_NAME, line("§8(party loading…)"))
            }
            for (slot in slots) session.container.setItem(slot, placeholder)
            return
        }
        val party = Cobblemon.storage.getParty(player)
        val offer = session.offerOf(partyOwnerUuid) ?: return
        val stagedUuids = offer.pokemonUuids().toSet()
        for ((i, slot) in slots.withIndex()) {
            val mon = party.get(i)
            if (mon == null) {
                val placeholder = ItemStack(Items.GRAY_STAINED_GLASS_PANE)
                placeholder.set(DataComponents.CUSTOM_NAME, line("§8(empty party slot)"))
                session.container.setItem(slot, placeholder)
                continue
            }
            val staged = mon.uuid in stagedUuids
            val stack = try {
                PokemonItem.from(mon)
            } catch (e: Throwable) {
                ItemStack(Items.PAPER)
            }
            val titlePrefix = if (staged) "§7§m" else "§a"
            stack.set(DataComponents.CUSTOM_NAME, line("$titlePrefix${mon.species.name} §7L${mon.level}"))
            val loreLines = mutableListOf<Component>(
                line("§7HP: §f${mon.currentHealth}/${mon.maxHealth}"),
                line(""),
            )
            if (staged) {
                loreLines.add(line("§e§l✓ Already in trade"))
                loreLines.add(line("§7Click to remove from offer."))
            } else {
                loreLines.add(line("§a§l→ Click to add to trade"))
            }
            stack.set(DataComponents.LORE, ItemLore(loreLines))
            session.container.setItem(slot, stack)
        }
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

            // Add-Money buttons.
            if (slotId == P1_ADD_MONEY) { if (isP1()) handleAddMoney(sp, button, clickType); return }
            if (slotId == P2_ADD_MONEY) { if (!isP1()) handleAddMoney(sp, button, clickType); return }

            // Party tiles (rows 0-1) — click your own to toggle staged/unstaged.
            val ownPartySlots = if (isP1()) P1_PARTY_SLOTS else P2_PARTY_SLOTS
            if (slotId in ownPartySlots) {
                val partyIdx = ownPartySlots.indexOf(slotId)
                val party = Cobblemon.storage.getParty(sp)
                val mon = party.get(partyIdx) ?: return  // empty party slot — no-op
                val offer = session.offerOf(sp.uuid) ?: return
                val existing = offer.pokemon.indexOfFirst { it.uuid == mon.uuid }
                if (existing >= 0) {
                    TradeManager.unstagePokemon(sp, existing)
                } else {
                    TradeManager.stagePokemon(sp, partyIdx)
                }
                return
            }
            // Other side's party tile — can look but not touch.
            val otherPartySlots = if (isP1()) P2_PARTY_SLOTS else P1_PARTY_SLOTS
            if (slotId in otherPartySlots) return

            // Staged-area Pokémon slots — clicking removes from offer.
            val ownPokemonSlots = if (isP1()) P1_POKEMON_SLOTS else P2_POKEMON_SLOTS
            if (slotId in ownPokemonSlots) {
                val offerIdx = ownPokemonSlots.indexOf(slotId)
                TradeManager.unstagePokemon(sp, offerIdx)
                return
            }
            // Other side's staged-area pokemon slot — no-op.
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
