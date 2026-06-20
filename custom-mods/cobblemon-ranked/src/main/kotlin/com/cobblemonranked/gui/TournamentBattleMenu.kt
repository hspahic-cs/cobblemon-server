package com.cobblemonranked.gui

import com.cobblemon.mod.common.item.PokemonItem
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemonranked.battle.countsAsSpecial
import com.cobblemonranked.battle.specialCategory
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
 * Tournament-match team picker: choose a subset of [TEAM_SIZE] (6) from the player's locked
 * 9-Pokémon roster (the [pool]), with at most [MAX_SPECIALS] (1) Legendary/Paradox/Ultra-Beast.
 *
 * The player can also see the opponent's roster (species names only). If they select more than one
 * special, the selected slots holding the offending specials are re-labelled
 * "Too Many <Category> (Limit 1) - <Name>" and Confirm is blocked until they drop back to ≤1.
 *
 * Layout (vanilla `GENERIC_9x6`, server-side):
 *  row 0 (0-8)    — your 9 roster options (click to add/remove)
 *  row 1 (9-17)   — labels
 *  row 2 (18-26)  — opponent's roster (species name only; display)
 *  row 3 (27-32)  — your selected 6 (click to remove); 34 count; 35 specials
 *  row 4 (36-44)  — over-limit warning banner (only when >1 special selected)
 *  row 5 (45-51 filler, 52 confirm, 53 cancel)
 */
class TournamentBattleMenu private constructor(
    containerId: Int,
    private val playerInventory: Inventory,
    private val player: ServerPlayer?,
    private val pool: List<Pokemon>,
    private val opponentRoster: List<Pokemon>,
    private val onConfirm: ((List<Pokemon>) -> Unit)?,
    private val onCancel: (() -> Unit)?,
) : AbstractContainerMenu(MenuType.GENERIC_9x6, containerId) {

    private val display = SimpleContainer(SLOT_COUNT)
    private val selected = mutableListOf<Pokemon>()
    private var confirmed = false
    private var cancelled = false

    /** How many we require — normally 6, but never more than the pool actually has. */
    private val requiredSize = minOf(TEAM_SIZE, pool.size)

    init {
        for (row in 0 until ROWS) for (col in 0 until COLS) {
            addSlot(DisplaySlot(display, row * COLS + col, 8 + col * 18, 18 + row * 18))
        }
        for (row in 0 until 3) for (col in 0 until 9) {
            addSlot(LockedSlot(playerInventory, col + row * 9 + 9, 8 + col * 18, 103 + row * 18))
        }
        for (col in 0 until 9) addSlot(LockedSlot(playerInventory, col, 8 + col * 18, 161))
        repaint()
    }

    private fun specialsCount(): Int = selected.count { it.countsAsSpecial() }

    private fun repaint() {
        for (i in 0 until SLOT_COUNT) display.setItem(i, ItemStack.EMPTY)
        val overLimit = specialsCount() > MAX_SPECIALS

        // Row 0: your roster options
        for (i in 0 until 9) {
            val pk = pool.getOrNull(i)
            display.setItem(i, if (pk != null) optionStack(pk, pk in selected)
                                else filler(Items.LIGHT_GRAY_STAINED_GLASS_PANE))
        }

        // Row 1: labels
        for (i in 9..17) display.setItem(i, filler(Items.BLACK_STAINED_GLASS_PANE))
        display.setItem(9, named(Items.LIME_STAINED_GLASS_PANE, "§a↑ Your roster — pick $requiredSize"))
        display.setItem(17, named(Items.LIGHT_BLUE_STAINED_GLASS_PANE, "§bOpponent's roster ↓"))

        // Row 2: opponent roster (species name only)
        for (i in 0 until 9) {
            val pk = opponentRoster.getOrNull(i)
            display.setItem(18 + i, if (pk != null) opponentStack(pk)
                                     else filler(Items.LIGHT_GRAY_STAINED_GLASS_PANE))
        }

        // Row 3: selected team + counters
        for (i in 0 until 6) {
            display.setItem(27 + i, if (i < selected.size) selectedStack(selected[i], overLimit)
                                     else named(Items.GRAY_STAINED_GLASS_PANE, "§8Empty Slot"))
        }
        val count = ItemStack(Items.PAPER)
        count.set(DataComponents.CUSTOM_NAME, Component.literal("§eSelected: ${selected.size}/$requiredSize"))
        display.setItem(34, count)
        val specials = specialsCount()
        val specialStack = ItemStack(if (overLimit) Items.BARRIER else if (specials > 0) Items.NETHER_STAR else Items.GLASS)
        specialStack.set(DataComponents.CUSTOM_NAME,
            Component.literal("§dSpecials: ${if (overLimit) "§c" else "§a"}$specials§d/$MAX_SPECIALS"))
        specialStack.set(DataComponents.LORE, ItemLore(listOf(
            Component.literal("§7Legendary / Paradox / Ultra-Beast."),
            Component.literal("§7Max §c$MAX_SPECIALS§7 per battle."),
        )))
        display.setItem(35, specialStack)

        // Row 4: over-limit warning banner (only when >1 special)
        if (overLimit) {
            var slot = 36
            for (pk in selected.filter { it.countsAsSpecial() }) {
                if (slot > 44) break
                display.setItem(slot++, warningStack(pk))
            }
            while (slot <= 44) display.setItem(slot++, named(Items.RED_STAINED_GLASS_PANE, " "))
        } else {
            for (i in 36..44) display.setItem(i, filler(Items.BLACK_STAINED_GLASS_PANE))
        }

        // Row 5: confirm + cancel
        for (i in 45..51) display.setItem(i, filler(Items.BLACK_STAINED_GLASS_PANE))
        val ready = selected.size == requiredSize && !overLimit
        val confirmItem = when {
            overLimit -> Items.BARRIER
            ready -> Items.LIME_CONCRETE
            else -> Items.GRAY_CONCRETE
        }
        val confirmName = when {
            overLimit -> "§cToo Many Specials (Limit $MAX_SPECIALS)"
            ready -> "§aConfirm Team"
            else -> "§7Pick ${requiredSize - selected.size} more"
        }
        display.setItem(52, named(confirmItem, Component.literal(confirmName).withStyle(Style.EMPTY.withBold(true))))
        display.setItem(53, named(Items.RED_CONCRETE, Component.literal("Cancel").withStyle(Style.EMPTY.withBold(true))))

        broadcastChanges()
    }

    private fun optionStack(pokemon: Pokemon, isSelected: Boolean): ItemStack {
        val stack = PokemonItem.from(pokemon)
        val tag = pokemon.specialCategory()?.let { " §c[${it.uppercase()}]" } ?: ""
        val prefix = if (isSelected) "§a✓ " else ""
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("$prefix${pokemon.species.name} Lv.${pokemon.level}$tag"))
        stack.set(DataComponents.LORE, ItemLore(listOf(
            Component.literal("§7Type: ${pokemon.primaryType.name}" + (pokemon.secondaryType?.let { "/${it.name}" } ?: "")),
            Component.literal(if (isSelected) "§eClick to remove from team" else "§aClick to add to team"),
        )))
        return stack
    }

    /** A selected-team slot. When over the special limit, special members are re-labelled. */
    private fun selectedStack(pokemon: Pokemon, overLimit: Boolean): ItemStack {
        if (overLimit && pokemon.countsAsSpecial()) return warningStack(pokemon)
        val stack = PokemonItem.from(pokemon)
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("§a✓ ${pokemon.species.name} Lv.${pokemon.level}"))
        stack.set(DataComponents.LORE, ItemLore(listOf(Component.literal("§eClick to remove"))))
        return stack
    }

    private fun warningStack(pokemon: Pokemon): ItemStack {
        val stack = PokemonItem.from(pokemon)
        val cat = pokemon.specialCategory() ?: "Special"
        stack.set(DataComponents.CUSTOM_NAME,
            Component.literal("§c§lToo Many $cat (Limit $MAX_SPECIALS) §r§c- ${pokemon.species.name}"))
        stack.set(DataComponents.LORE, ItemLore(listOf(
            Component.literal("§cYou can only bring §f$MAX_SPECIALS§c special Pokémon."),
            Component.literal("§7Remove one to submit."),
        )))
        return stack
    }

    private fun opponentStack(pokemon: Pokemon): ItemStack {
        // Species name only — no level/stats/moves revealed.
        val stack = PokemonItem.from(pokemon)
        val tag = pokemon.specialCategory()?.let { " §c[${it.uppercase()}]" } ?: ""
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("§b${pokemon.species.name}$tag"))
        stack.set(DataComponents.LORE, ItemLore(listOf(Component.literal("§7Opponent's roster"))))
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
        when (slotId) {
            in 0 until 9 -> {
                val pk = pool.getOrNull(slotId) ?: return
                togglePokemon(pk, sp); repaint()
            }
            in 27 until 33 -> {
                val idx = slotId - 27
                if (idx < selected.size) { selected.removeAt(idx); repaint() }
            }
            52 -> {
                if (specialsCount() > MAX_SPECIALS) {
                    sp.sendSystemMessage(Component.literal(
                        "§c[Tournament] Too many Legendary/Paradox/Ultra-Beast — limit $MAX_SPECIALS. Remove one."))
                    return
                }
                if (selected.size != requiredSize) {
                    sp.sendSystemMessage(Component.literal(
                        "§c[Tournament] Pick exactly $requiredSize Pokémon (you have ${selected.size})."))
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
        if (pokemon in selected) { selected.remove(pokemon); return }
        if (selected.size >= requiredSize) {
            sp.sendSystemMessage(Component.literal("§c[Tournament] Team is full ($requiredSize). Remove one first."))
            return
        }
        // Specials are allowed to be added past the limit on purpose — the menu then shows the
        // "Too Many …" warning and blocks Confirm until the player drops back to ≤ the limit.
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
        const val TEAM_SIZE = 6
        const val MAX_SPECIALS = 1

        internal fun forServer(
            containerId: Int, inv: Inventory, player: ServerPlayer,
            pool: List<Pokemon>, opponentRoster: List<Pokemon>,
            onConfirm: (List<Pokemon>) -> Unit, onCancel: () -> Unit,
        ): TournamentBattleMenu =
            TournamentBattleMenu(containerId, inv, player, pool, opponentRoster, onConfirm, onCancel)
    }
}

class TournamentBattleMenuProvider(
    private val player: ServerPlayer,
    private val pool: List<Pokemon>,
    private val opponentRoster: List<Pokemon>,
    private val onConfirm: (List<Pokemon>) -> Unit,
    private val onCancel: () -> Unit,
) : MenuProvider {
    override fun getDisplayName(): Component = Component.literal("Tournament Match — pick 6 of 9")
    override fun createMenu(containerId: Int, inv: Inventory, ignored: Player): AbstractContainerMenu =
        TournamentBattleMenu.forServer(containerId, inv, player, pool, opponentRoster, onConfirm, onCancel)
}
