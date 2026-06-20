package com.cobblemonranked.gui

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.storage.party.PartyStore
import com.cobblemon.mod.common.api.storage.pc.PCStore
import com.cobblemon.mod.common.item.PokemonItem
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemonranked.battle.countsAsSpecial
import com.cobblemonranked.battle.specialCategory
import com.cobblemonranked.tournament.TournamentManager
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.MenuProvider
import net.minecraft.world.SimpleContainer
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ClickType
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.ItemLore
import java.util.UUID

/**
 * Tournament roster picker: choose exactly [TournamentManager.ROSTER_SIZE] (9) Pokémon from PC +
 * party, with at most [TournamentManager.MAX_SPECIALS_ROSTER] (4) Legendary/Paradox/Ultra-Beast.
 *
 * Vanilla `GENERIC_9x6` double chest (54 slots, server-side, no client jar/packets). The current PC
 * box is shown IN FULL — all 30 slots — as a 6×5 block in the left six columns (matching Cobblemon's
 * own PC box shape). The right three columns + bottom row carry navigation, the selected roster,
 * counters, the party, and the confirm/cancel buttons.
 *
 * Slot map (row r, col c → r*9+c):
 *  - Box (30):     cols 0-5 of rows 0-4  → [BOX_SLOTS], in box-index order
 *  - Nav (3):      6 prev, 7 label, 8 next   (box navigation wraps: box 1 ↔ last)
 *  - Selected (9): 15,16,17 / 24,25,26 / 33,34,35  → [SELECTED_SLOTS] (click to remove)
 *  - Counters:     42 roster count, 43 specials, 44 "party ↓" label
 *  - Party (6):    45-50  → [PARTY_SLOTS]
 *  - Buttons:      52 confirm, 53 cancel  (51 filler)
 */
class TournamentJoinMenu private constructor(
    containerId: Int,
    private val playerInventory: Inventory,
    private val player: ServerPlayer?,
    private val initialUuids: Set<UUID>,
    private val onConfirm: ((List<Pokemon>) -> Unit)?,
    private val onCancel: (() -> Unit)?,
) : AbstractContainerMenu(MenuType.GENERIC_9x6, containerId) {

    private val display = SimpleContainer(SLOT_COUNT)
    private val selected = mutableListOf<Pokemon>()
    private var currentBox: Int = 0
    private var confirmed = false
    private var cancelled = false
    private val party: PartyStore? = player?.let { Cobblemon.storage.getParty(it) }
    private val pc: PCStore? = player?.let { Cobblemon.storage.getPC(it) }

    init {
        for (row in 0 until ROWS) for (col in 0 until COLS) {
            addSlot(DisplaySlot(display, row * COLS + col, 8 + col * 18, 18 + row * 18))
        }
        for (row in 0 until 3) for (col in 0 until 9) {
            addSlot(LockedSlot(playerInventory, col + row * 9 + 9, 8 + col * 18, 103 + row * 18))
        }
        for (col in 0 until 9) addSlot(LockedSlot(playerInventory, col, 8 + col * 18, 161))
        prefillSelection()
        repaint()
    }

    private fun prefillSelection() {
        if (initialUuids.isEmpty() || player == null) return
        val owned = TournamentManager.collectOwnedPokemon(player)
        for (id in initialUuids) {
            val pk = owned[id] ?: continue
            if (selected.size < TournamentManager.ROSTER_SIZE) selected.add(pk)
        }
    }

    private fun specialsCount(): Int = selected.count { it.countsAsSpecial() }

    private fun repaint() {
        for (i in 0 until SLOT_COUNT) display.setItem(i, ItemStack.EMPTY)
        val boxes = pc?.boxes
        val box = boxes?.getOrNull(currentBox)

        // Box — full 30 slots in box-index order.
        for (idx in 0 until BOX_CAPACITY) {
            val slot = BOX_SLOTS[idx]
            val pokemon: Pokemon? = try { box?.get(idx) } catch (e: Exception) { null }
            display.setItem(slot, if (pokemon != null) pokemonStack(pokemon, pokemon in selected)
                                   else filler(Items.LIGHT_GRAY_STAINED_GLASS_PANE))
        }

        // Nav (top-right corner)
        display.setItem(6, named(Items.ARROW, "§7← Previous Box"))
        display.setItem(7, named(Items.NAME_TAG, "§eBox ${currentBox + 1} §7/ ${(boxes?.size ?: 1).coerceAtLeast(1)}"))
        display.setItem(8, named(Items.ARROW, "§7Next Box →"))

        // Selected roster (right panel)
        for (i in 0 until TournamentManager.ROSTER_SIZE) {
            val slot = SELECTED_SLOTS[i]
            display.setItem(slot, if (i < selected.size) pokemonStack(selected[i], true)
                                   else named(Items.GRAY_STAINED_GLASS_PANE, "§8Roster Slot ${i + 1}"))
        }

        // Counters
        val count = ItemStack(Items.PAPER)
        count.set(DataComponents.CUSTOM_NAME, Component.literal("§eRoster: ${selected.size}/${TournamentManager.ROSTER_SIZE}"))
        count.set(DataComponents.LORE, ItemLore(listOf(
            Component.literal("§7Pick exactly §f${TournamentManager.ROSTER_SIZE}§7 Pokémon, then Confirm."),
            Component.literal("§7Click a roster slot to remove it."),
        )))
        display.setItem(42, count)

        val specials = specialsCount()
        val specialColor = if (specials >= TournamentManager.MAX_SPECIALS_ROSTER) "§c" else "§a"
        val specialStack = ItemStack(if (specials > 0) Items.NETHER_STAR else Items.GLASS)
        specialStack.set(DataComponents.CUSTOM_NAME,
            Component.literal("§dSpecials: $specialColor$specials§d/${TournamentManager.MAX_SPECIALS_ROSTER}"))
        specialStack.set(DataComponents.LORE, ItemLore(listOf(
            Component.literal("§7Legendary / Paradox / Ultra-Beast."),
            Component.literal("§7Max §c${TournamentManager.MAX_SPECIALS_ROSTER}§7 in your roster"),
            Component.literal("§7(a 6-mon battle subset may bring §c1§7)."),
        )))
        display.setItem(43, specialStack)
        display.setItem(44, named(Items.CHEST, "§7Your party §8↓"))

        // Party (bottom-left)
        for (i in 0 until 6) {
            val slot = PARTY_SLOTS[i]
            val pokemon = party?.get(i)
            display.setItem(slot, if (pokemon != null) pokemonStack(pokemon, pokemon in selected)
                                   else filler(Items.LIGHT_GRAY_STAINED_GLASS_PANE))
        }

        display.setItem(51, filler(Items.BLACK_STAINED_GLASS_PANE))
        val ready = selected.size == TournamentManager.ROSTER_SIZE
        display.setItem(52, named(if (ready) Items.LIME_CONCRETE else Items.GRAY_CONCRETE,
            Component.literal(if (ready) "§aConfirm Roster" else "§7Pick ${TournamentManager.ROSTER_SIZE - selected.size} more")
                .withStyle(Style.EMPTY.withBold(true))))
        display.setItem(53, named(Items.RED_CONCRETE,
            Component.literal("Cancel").withStyle(Style.EMPTY.withBold(true))))

        broadcastChanges()
    }

    private fun pokemonStack(pokemon: Pokemon, isSelected: Boolean): ItemStack {
        val stack = PokemonItem.from(pokemon)
        val tag = pokemon.specialCategory()?.let { " §c[${it.uppercase()}]" } ?: ""
        val statePrefix = if (isSelected) "§a✓ " else ""
        stack.set(DataComponents.CUSTOM_NAME,
            Component.literal("$statePrefix${pokemon.species.name} Lv.${pokemon.level}$tag"))
        stack.set(DataComponents.LORE, ItemLore(listOf(
            Component.literal("§7Type: ${pokemon.primaryType.name}" + (pokemon.secondaryType?.let { "/${it.name}" } ?: "")),
            Component.literal("§7Ability: ${pokemon.ability.name}"),
            Component.literal(if (isSelected) "§eClick to remove" else "§aClick to add to roster"),
        )))
        return stack
    }

    private fun named(item: Item, name: String): ItemStack = named(item, Component.literal(name))
    private fun named(item: Item, name: Component): ItemStack {
        val stack = ItemStack(item)
        stack.set(DataComponents.CUSTOM_NAME, name)
        return stack
    }
    private fun filler(item: Item): ItemStack = named(item, " ")

    override fun stillValid(player: Player): Boolean = true

    override fun clicked(slotId: Int, button: Int, type: ClickType, player: Player) {
        val sp = this.player ?: return
        val boxes = pc?.boxes ?: return
        when {
            slotId in BOX_SLOT_TO_INDEX -> {
                val box = boxes.getOrNull(currentBox) ?: return
                val idx = BOX_SLOT_TO_INDEX.getValue(slotId)
                val pokemon = try { box.get(idx) } catch (e: Exception) { null } ?: return
                togglePokemon(pokemon, sp); repaint()
            }
            slotId in PARTY_SLOT_TO_INDEX -> {
                val pokemon = party?.get(PARTY_SLOT_TO_INDEX.getValue(slotId)) ?: return
                togglePokemon(pokemon, sp); repaint()
            }
            slotId in SELECTED_SLOT_TO_INDEX -> {
                val idx = SELECTED_SLOT_TO_INDEX.getValue(slotId)
                if (idx < selected.size) { selected.removeAt(idx); repaint() }
            }
            // Wrap-around navigation so "previous" from box 1 goes to the last box, and vice versa.
            slotId == 6 -> { if (boxes.isNotEmpty()) { currentBox = (currentBox - 1 + boxes.size) % boxes.size; repaint() } }
            slotId == 8 -> { if (boxes.isNotEmpty()) { currentBox = (currentBox + 1) % boxes.size; repaint() } }
            slotId == 52 -> {
                if (selected.size != TournamentManager.ROSTER_SIZE) {
                    sp.sendSystemMessage(Component.literal(
                        "§c[Tournament] Pick exactly ${TournamentManager.ROSTER_SIZE} Pokémon (you have ${selected.size})."))
                    return
                }
                confirmed = true
                sp.closeContainer()
                onConfirm?.invoke(selected.toList())
            }
            slotId == 53 -> { cancelled = true; sp.closeContainer(); onCancel?.invoke() }
        }
    }

    private fun togglePokemon(pokemon: Pokemon, sp: ServerPlayer) {
        if (pokemon in selected) { selected.remove(pokemon); return }
        if (selected.size >= TournamentManager.ROSTER_SIZE) {
            sp.sendSystemMessage(Component.literal("§c[Tournament] Roster is full (${TournamentManager.ROSTER_SIZE}). Remove one first."))
            return
        }
        if (pokemon.countsAsSpecial() && specialsCount() >= TournamentManager.MAX_SPECIALS_ROSTER) {
            sp.sendSystemMessage(Component.literal(
                "§c[Tournament] Max ${TournamentManager.MAX_SPECIALS_ROSTER} Legendary/Paradox/Ultra-Beast in a roster."))
            return
        }
        selected.add(pokemon)
    }

    override fun quickMoveStack(player: Player, index: Int): ItemStack = ItemStack.EMPTY

    override fun removed(player: Player) {
        super.removed(player)
        if (!confirmed && !cancelled) { cancelled = true; onCancel?.invoke() }
    }

    private class DisplaySlot(c: SimpleContainer, slot: Int, x: Int, y: Int) : Slot(c, slot, x, y) {
        override fun mayPlace(stack: ItemStack) = false
        override fun mayPickup(player: Player) = false
    }
    private class LockedSlot(inv: Inventory, slot: Int, x: Int, y: Int) : Slot(inv, slot, x, y) {
        override fun mayPlace(stack: ItemStack) = false
        override fun mayPickup(player: Player) = false
    }

    companion object {
        const val ROWS = 6
        const val COLS = 9
        const val SLOT_COUNT = ROWS * COLS
        const val BOX_CAPACITY = 30

        /** Container slots holding the 30 box cells, in box-index order (6×5 block, cols 0-5, rows 0-4). */
        private val BOX_SLOTS: IntArray = IntArray(BOX_CAPACITY) { idx ->
            val r = idx / 6; val c = idx % 6; r * COLS + c
        }
        private val BOX_SLOT_TO_INDEX: Map<Int, Int> = BOX_SLOTS.withIndex().associate { (idx, slot) -> slot to idx }

        private val SELECTED_SLOTS = intArrayOf(15, 16, 17, 24, 25, 26, 33, 34, 35)
        private val SELECTED_SLOT_TO_INDEX: Map<Int, Int> = SELECTED_SLOTS.withIndex().associate { (idx, slot) -> slot to idx }

        private val PARTY_SLOTS = intArrayOf(45, 46, 47, 48, 49, 50)
        private val PARTY_SLOT_TO_INDEX: Map<Int, Int> = PARTY_SLOTS.withIndex().associate { (idx, slot) -> slot to idx }

        internal fun forServer(
            containerId: Int, inv: Inventory, player: ServerPlayer, initialUuids: Set<UUID>,
            onConfirm: (List<Pokemon>) -> Unit, onCancel: () -> Unit,
        ): TournamentJoinMenu = TournamentJoinMenu(containerId, inv, player, initialUuids, onConfirm, onCancel)
    }
}

class TournamentJoinMenuProvider(
    private val player: ServerPlayer,
    private val initialUuids: Set<UUID>,
    private val onConfirm: (List<Pokemon>) -> Unit,
    private val onCancel: () -> Unit,
) : MenuProvider {
    override fun getDisplayName(): Component = Component.literal("Tournament Roster — pick 9")
    override fun createMenu(containerId: Int, inv: Inventory, ignored: Player): AbstractContainerMenu =
        TournamentJoinMenu.forServer(containerId, inv, player, initialUuids, onConfirm, onCancel)
}
