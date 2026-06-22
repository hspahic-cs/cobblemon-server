package com.cobblemonranked.gui

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.storage.party.PartyStore
import com.cobblemon.mod.common.api.storage.pc.PCStore
import com.cobblemon.mod.common.item.PokemonItem
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemonranked.battle.countsAsLegendary
import com.cobblemonranked.battle.isParadox
import com.cobblemonranked.battle.rankedBanReason
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

/**
 * 6-pick team selector for ranked matches: choose up to 6 Pokémon from the player's PC + party.
 *
 * Vanilla `GENERIC_9x6` double chest (54 slots, server-side, no client jar/packets). The current PC
 * box is shown IN FULL — all 30 slots — as a 6×5 block in the left six columns (matching Cobblemon's
 * own PC box shape). The right three columns + bottom row carry navigation, the selected team,
 * counters, the party, and the confirm/cancel buttons.
 *
 * Slot map (row r, col c → r*9+c):
 *  - Box (30):     cols 0-5 of rows 0-4  → [BOX_SLOTS], in box-index order
 *  - Nav (3):      6 prev, 7 label, 8 next   (box navigation wraps: box 1 ↔ last)
 *  - Selected (6): 15,16,17 / 24,25,26  → [SELECTED_SLOTS] (click to remove)
 *  - Counters:     33 selected count, 34 legendary info, 35 "party ↓" label
 *  - Party (6):    45-50  → [PARTY_SLOTS]
 *  - Buttons:      52 confirm, 53 cancel  (51 + 42-44 filler)
 */
class TeamSelectionMenu private constructor(
    containerId: Int,
    private val playerInventory: Inventory,
    private val player: ServerPlayer?,
    private val maxLegendaries: Int,
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
        // Player inventory + hotbar (36 slots) — required so slot count matches the vanilla
        // ChestMenu the client creates. Locked to prevent interaction.
        for (row in 0 until 3) for (col in 0 until 9) {
            addSlot(LockedSlot(playerInventory, col + row * 9 + 9, 8 + col * 18, 103 + row * 18))
        }
        for (col in 0 until 9) addSlot(LockedSlot(playerInventory, col, 8 + col * 18, 161))
        repaint()
    }

    private fun repaint() {
        for (i in 0 until SLOT_COUNT) display.setItem(i, ItemStack.EMPTY)
        val boxes = pc?.boxes
        val box = boxes?.getOrNull(currentBox)

        // Box — full 30 slots in box-index order (6×5 block, left six columns).
        for (idx in 0 until BOX_CAPACITY) {
            val slot = BOX_SLOTS[idx]
            val pokemon: Pokemon? = try { box?.get(idx) } catch (e: Exception) { null }
            display.setItem(slot, if (pokemon != null) pokemonStack(pokemon, pokemon in selected)
                                   else filler(Items.LIGHT_GRAY_STAINED_GLASS_PANE))
        }

        // Nav (top-right)
        display.setItem(6, named(Items.ARROW, "§7← Previous Box"))
        display.setItem(7, named(Items.NAME_TAG, "§eBox ${currentBox + 1} §7/ ${(boxes?.size ?: 1).coerceAtLeast(1)}"))
        display.setItem(8, named(Items.ARROW, "§7Next Box →"))

        // Selected team (right panel)
        for (i in 0 until TEAM_SIZE) {
            val slot = SELECTED_SLOTS[i]
            display.setItem(slot, if (i < selected.size) pokemonStack(selected[i], true)
                                   else named(Items.GRAY_STAINED_GLASS_PANE, "§8Team Slot ${i + 1}"))
        }

        // Counters
        val info = ItemStack(Items.PAPER)
        info.set(DataComponents.CUSTOM_NAME, Component.literal("§eSelected: ${selected.size}/$TEAM_SIZE"))
        if (selected.isNotEmpty()) {
            info.set(DataComponents.LORE, ItemLore(selected.map { Component.literal("§7- ${it.species.name} Lv.${it.level}") }))
        }
        display.setItem(33, info)

        val legendaries = selected.count { it.countsAsLegendary() }
        val legColor = if (legendaries > maxLegendaries) "§c" else "§a"
        val legStack = ItemStack(if (legendaries > 0) Items.NETHER_STAR else Items.GLASS)
        legStack.set(DataComponents.CUSTOM_NAME, Component.literal("§dLegendaries: $legColor$legendaries§d/$maxLegendaries"))
        legStack.set(DataComponents.LORE, ItemLore(listOf(
            Component.literal("§7Legendary + Paradox count here."),
            Component.literal("§7Over the cap = auto-loss at match start."),
        )))
        display.setItem(34, legStack)
        display.setItem(35, named(Items.CHEST, "§7Your party §8↓"))
        for (i in intArrayOf(42, 43, 44)) display.setItem(i, filler(Items.BLACK_STAINED_GLASS_PANE))

        // Party (bottom-left)
        for (i in 0 until 6) {
            val slot = PARTY_SLOTS[i]
            val pokemon = party?.get(i)
            display.setItem(slot, if (pokemon != null) pokemonStack(pokemon, pokemon in selected)
                                   else filler(Items.LIGHT_GRAY_STAINED_GLASS_PANE))
        }

        display.setItem(51, filler(Items.BLACK_STAINED_GLASS_PANE))
        display.setItem(52, named(Items.LIME_CONCRETE,
            Component.literal("Confirm Team").withStyle(Style.EMPTY.withBold(true))))
        display.setItem(53, named(Items.RED_CONCRETE,
            Component.literal("Cancel").withStyle(Style.EMPTY.withBold(true))))

        broadcastChanges()
    }

    private fun pokemonStack(pokemon: Pokemon, isSelected: Boolean): ItemStack {
        val stack = PokemonItem.from(pokemon)
        val banReason = pokemon.rankedBanReason()
        val tag = when {
            banReason != null -> " §c[BANNED]"
            pokemon.isLegendary() -> " §c[LEGENDARY]"
            pokemon.isParadox() -> " §c[PARADOX]"
            else -> ""
        }
        val statePrefix = if (isSelected) "§a✓ " else ""
        stack.set(DataComponents.CUSTOM_NAME,
            Component.literal("$statePrefix${pokemon.species.name} Lv.${pokemon.level}$tag"))
        stack.set(DataComponents.LORE, ItemLore(listOf(
            Component.literal("§7Type: ${pokemon.primaryType.name}" + (pokemon.secondaryType?.let { "/${it.name}" } ?: "")),
            Component.literal("§7Ability: ${pokemon.ability.name}"),
            Component.literal("§7HP: ${pokemon.currentHealth}/${pokemon.maxHealth}"),
            if (banReason != null) Component.literal("§c✖ Banned in ranked PvP ($banReason)")
            else Component.literal(if (isSelected) "§eClick to deselect" else "§aClick to select"),
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
            slotId == 6 -> { if (boxes.isNotEmpty()) { currentBox = (currentBox - 1 + boxes.size) % boxes.size; repaint() } }
            slotId == 8 -> { if (boxes.isNotEmpty()) { currentBox = (currentBox + 1) % boxes.size; repaint() } }
            slotId == 52 -> {
                if (selected.isEmpty()) {
                    sp.sendSystemMessage(Component.literal("§c[Ranked] You must select at least 1 Pokemon!"))
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
        if (pokemon in selected) {
            selected.remove(pokemon)
            return
        }
        pokemon.rankedBanReason()?.let { reason ->
            sp.sendSystemMessage(Component.literal("§c[Ranked] $reason is banned in ranked PvP — can't add it."))
            return
        }
        if (selected.size < TEAM_SIZE) {
            selected.add(pokemon)
        } else {
            sp.sendSystemMessage(Component.literal("§c[Ranked] Team is full! Remove a Pokemon first."))
        }
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
        const val TEAM_SIZE = 6

        /** Container slots holding the 30 box cells, in box-index order (6×5 block, cols 0-5, rows 0-4). */
        private val BOX_SLOTS: IntArray = IntArray(BOX_CAPACITY) { idx ->
            val r = idx / 6; val c = idx % 6; r * COLS + c
        }
        private val BOX_SLOT_TO_INDEX: Map<Int, Int> = BOX_SLOTS.withIndex().associate { (idx, slot) -> slot to idx }

        private val SELECTED_SLOTS = intArrayOf(15, 16, 17, 24, 25, 26)
        private val SELECTED_SLOT_TO_INDEX: Map<Int, Int> = SELECTED_SLOTS.withIndex().associate { (idx, slot) -> slot to idx }

        private val PARTY_SLOTS = intArrayOf(45, 46, 47, 48, 49, 50)
        private val PARTY_SLOT_TO_INDEX: Map<Int, Int> = PARTY_SLOTS.withIndex().associate { (idx, slot) -> slot to idx }

        /** Server-side factory used by TeamSelectionMenuProvider with real player state. */
        internal fun forServer(
            containerId: Int,
            playerInventory: Inventory,
            player: ServerPlayer,
            maxLegendaries: Int,
            onConfirm: (List<Pokemon>) -> Unit,
            onCancel: () -> Unit,
        ): TeamSelectionMenu = TeamSelectionMenu(containerId, playerInventory, player, maxLegendaries, onConfirm, onCancel)
    }
}

class TeamSelectionMenuProvider(
    private val player: ServerPlayer,
    private val maxLegendaries: Int,
    private val onConfirm: (List<Pokemon>) -> Unit,
    private val onCancel: () -> Unit,
) : MenuProvider {
    override fun getDisplayName(): Component = Component.literal("Select Your Team")
    override fun createMenu(containerId: Int, inv: Inventory, ignored: Player): AbstractContainerMenu =
        TeamSelectionMenu.forServer(containerId, inv, player, maxLegendaries, onConfirm, onCancel)
}
