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
 * Same vanilla `GENERIC_9x6` approach as [TeamSelectionMenu] (server-side, no client jar/packets).
 * Layout:
 *  rows 0-1 (0-17)  — current PC box (18 shown); box navigation wraps (box 1 ← → last box)
 *  row  2   (18-26) — PC nav: prev arrow, box label, next arrow
 *  row  3   (27-32) — party (6); 33 divider; 34 "Selected N/9"; 35 "Specials X/4"
 *  row  4   (36-44) — selected roster (9 slots; click to remove)
 *  row  5   (45-51 filler, 52 confirm, 53 cancel)
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

    /** Pre-load the player's previously-locked roster (if re-opening via /join) by UUID. */
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
        for (i in 0 until 18) {
            val pokemon: Pokemon? = try { box?.get(i) } catch (e: Exception) { null }
            display.setItem(i, if (pokemon != null) pokemonStack(pokemon, pokemon in selected)
                                else filler(Items.LIGHT_GRAY_STAINED_GLASS_PANE))
        }

        display.setItem(18, named(Items.ARROW, "§7← Previous Box"))
        for (i in listOf(19, 20, 21, 23, 24, 25)) display.setItem(i, filler(Items.BLACK_STAINED_GLASS_PANE))
        display.setItem(22, named(Items.NAME_TAG, "Box ${currentBox + 1} / ${(boxes?.size ?: 1).coerceAtLeast(1)}"))
        display.setItem(26, named(Items.ARROW, "§7Next Box →"))

        for (i in 0 until 6) {
            val pokemon = party?.get(i)
            display.setItem(27 + i, if (pokemon != null) pokemonStack(pokemon, pokemon in selected)
                                     else filler(Items.LIGHT_GRAY_STAINED_GLASS_PANE))
        }
        display.setItem(33, named(Items.CHEST, "§7^ PC | Party ^"))

        val count = ItemStack(Items.PAPER)
        count.set(DataComponents.CUSTOM_NAME, Component.literal("§eRoster: ${selected.size}/${TournamentManager.ROSTER_SIZE}"))
        count.set(DataComponents.LORE, ItemLore(listOf(
            Component.literal("§7Pick exactly §f${TournamentManager.ROSTER_SIZE}§7 Pokémon."),
            Component.literal("§7Confirm to enter / update your roster."),
        )))
        display.setItem(34, count)

        val specials = specialsCount()
        val specialColor = if (specials >= TournamentManager.MAX_SPECIALS_ROSTER) "§c" else "§a"
        val specialStack = ItemStack(if (specials > 0) Items.NETHER_STAR else Items.GLASS)
        specialStack.set(DataComponents.CUSTOM_NAME,
            Component.literal("§dSpecials: $specialColor$specials§d/${TournamentManager.MAX_SPECIALS_ROSTER}"))
        specialStack.set(DataComponents.LORE, ItemLore(listOf(
            Component.literal("§7Legendary / Paradox / Ultra-Beast."),
            Component.literal("§7Max §c${TournamentManager.MAX_SPECIALS_ROSTER}§7 in your roster"),
            Component.literal("§7(so a 6-mon battle subset can have §c1§7)."),
        )))
        display.setItem(35, specialStack)

        // Row 4: selected roster (9 slots)
        for (i in 0 until TournamentManager.ROSTER_SIZE) {
            display.setItem(36 + i, if (i < selected.size) pokemonStack(selected[i], true)
                                     else named(Items.GRAY_STAINED_GLASS_PANE, "§8Empty Slot"))
        }

        for (i in 45..51) display.setItem(i, filler(Items.BLACK_STAINED_GLASS_PANE))
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
        when (slotId) {
            in 0 until 18 -> {
                val box = boxes.getOrNull(currentBox) ?: return
                val pokemon = try { box.get(slotId) } catch (e: Exception) { null } ?: return
                togglePokemon(pokemon, sp); repaint()
            }
            in 27 until 33 -> {
                val pokemon = party?.get(slotId - 27) ?: return
                togglePokemon(pokemon, sp); repaint()
            }
            in 36 until 45 -> {
                val idx = slotId - 36
                if (idx < selected.size) { selected.removeAt(idx); repaint() }
            }
            // Wrap-around navigation so "previous" from box 1 goes to the last box, and vice versa.
            18 -> { if (boxes.isNotEmpty()) { currentBox = (currentBox - 1 + boxes.size) % boxes.size; repaint() } }
            26 -> { if (boxes.isNotEmpty()) { currentBox = (currentBox + 1) % boxes.size; repaint() } }
            52 -> {
                if (selected.size != TournamentManager.ROSTER_SIZE) {
                    sp.sendSystemMessage(Component.literal(
                        "§c[Tournament] Pick exactly ${TournamentManager.ROSTER_SIZE} Pokémon (you have ${selected.size})."))
                    return
                }
                confirmed = true
                sp.closeContainer()
                onConfirm?.invoke(selected.toList())
            }
            53 -> { cancelled = true; sp.closeContainer(); onCancel?.invoke() }
        }
    }

    private fun togglePokemon(pokemon: Pokemon, sp: ServerPlayer) {
        if (pokemon in selected) {
            selected.remove(pokemon)
            return
        }
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
