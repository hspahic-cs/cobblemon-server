package com.cobblemonranked.gui

import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemonranked.CobblemonRanked
import com.cobblemonranked.rental.RentalTeams
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.resources.ResourceLocation
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
 * Rental-team picker: choose one of the prebuilt [RentalTeams] instead of building your own party.
 * Opened from [TeamSelectionMenu]'s "Rent a Team" button; picking a team feeds the same `onConfirm`
 * that the normal team-select uses (so it flows into `pendingTeams` → battle), building the rental
 * Pokémon fresh — the player's real storage is never read or touched.
 *
 * Vanilla `GENERIC_9x6` chest, server-side. The four team buttons sit on row 2 (slots 19/21/23/25);
 * a Back button (slot 49) returns to the build-your-own selector. Closing without picking or going
 * Back cancels the match, matching the parent menu's behaviour.
 */
class RentalTeamMenu private constructor(
    containerId: Int,
    private val playerInventory: Inventory,
    private val player: ServerPlayer?,
    private val onConfirm: ((List<Pokemon>) -> Unit)?,
    private val onCancel: (() -> Unit)?,
    private val onBack: (() -> Unit)?,
    /** If set, picking a team calls this with the chosen [RentalTeams.RentalTeam] instead of building
     *  it and invoking [onConfirm] — used by the tournament "rent as your roster" flow. */
    private val onPickTeam: ((RentalTeams.RentalTeam) -> Unit)? = null,
) : AbstractContainerMenu(MenuType.GENERIC_9x6, containerId) {

    private val display = SimpleContainer(SLOT_COUNT)
    /** Set when we hand off to another menu (Back) or confirm, so [removed] doesn't cancel the match. */
    private var navigatingAway = false

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

    private fun repaint() {
        for (i in 0 until SLOT_COUNT) display.setItem(i, filler(Items.BLACK_STAINED_GLASS_PANE))

        val teams = RentalTeams.all()
        for (i in TEAM_SLOTS.indices) {
            val slot = TEAM_SLOTS[i]
            val team = teams.getOrNull(i)
            display.setItem(slot, if (team != null) teamStack(team) else filler(Items.GRAY_STAINED_GLASS_PANE))
        }

        display.setItem(4, named(Items.WRITABLE_BOOK,
            Component.literal("Rent a Team").withStyle(Style.EMPTY.withBold(true))).also {
            it.set(DataComponents.LORE, ItemLore(listOf(
                Component.literal("§7Prebuilt competitive teams — ready to"),
                Component.literal("§7battle, no breeding or training needed."),
                Component.literal("§8 "),
                Component.literal("§7New here? Run §f/ranked guide"),
                Component.literal("§7for how to play each team."),
            )))
        })
        display.setItem(49, named(Items.ARROW,
            Component.literal("§e← Back to Your Team")))

        broadcastChanges()
    }

    private fun teamStack(team: RentalTeams.RentalTeam): ItemStack {
        val stack = ItemStack(resolveItem(team.icon))
        stack.set(DataComponents.CUSTOM_NAME,
            Component.literal("§a§l${team.name}").withStyle(Style.EMPTY.withItalic(false)))
        // Coalesce a null/blank difficulty — an older rentals.json (written before this field) would
        // leave it null since Gson bypasses the Kotlin default.
        val diff = (team.difficulty as String?)?.ifBlank { null } ?: "Moderate"
        val lore = mutableListOf<Component>()
        lore.add(Component.literal("§7Difficulty: ${diffColor(diff)}$diff"))
        lore.add(Component.literal("§7${team.archetype}"))
        lore.add(Component.literal(" "))
        team.members.forEach { mon ->
            lore.add(Component.literal("§8• §f${displayName(mon)}"))
        }
        lore.add(Component.literal(" "))
        lore.add(Component.literal("§eClick to battle with this team"))
        stack.set(DataComponents.LORE, ItemLore(lore))
        return stack
    }

    /** "chiyu" → "Chiyu", "slowking" + form "galar" → "Slowking-Galar". */
    private fun displayName(mon: RentalTeams.RentalMon): String {
        val base = mon.species.replaceFirstChar { it.uppercase() }
        return if (mon.form != null) "$base-${mon.form.replaceFirstChar { it.uppercase() }}" else base
    }

    private fun diffColor(difficulty: String): String = when (difficulty.lowercase()) {
        "beginner" -> "§a"
        "moderate" -> "§e"
        "hard" -> "§6"
        "expert" -> "§c"
        else -> "§7"
    }

    private fun resolveItem(id: String): Item {
        val item = try {
            BuiltInRegistries.ITEM.get(ResourceLocation.parse(id))
        } catch (e: Exception) {
            Items.PAPER
        }
        return if (item == Items.AIR) Items.PAPER else item
    }

    override fun stillValid(player: Player): Boolean = true

    override fun clicked(slotId: Int, button: Int, type: ClickType, player: Player) {
        val sp = this.player ?: return
        when (slotId) {
            in TEAM_SLOT_TO_INDEX -> {
                val idx = TEAM_SLOT_TO_INDEX.getValue(slotId)
                val team = RentalTeams.all().getOrNull(idx) ?: return
                // Tournament "rent as roster" mode: hand back the team, skip building a battle party.
                onPickTeam?.let { pick ->
                    navigatingAway = true
                    sp.closeContainer()
                    pick(team)
                    return
                }
                val built = try {
                    RentalTeams.build(team)
                } catch (e: Exception) {
                    CobblemonRanked.logger.error("Failed to build rental team '${team.id}'", e)
                    sp.sendSystemMessage(Component.literal(
                        "§c[Ranked] That rental team failed to load — tell an admin. Pick another."))
                    return
                }
                navigatingAway = true
                sp.closeContainer()
                sp.sendSystemMessage(Component.literal("§a[Ranked] Rented §f${team.name}§a — team locked in!"))
                onConfirm?.invoke(built)
            }
            49 -> {
                navigatingAway = true
                sp.closeContainer()
                onBack?.invoke()
            }
        }
    }

    override fun quickMoveStack(player: Player, index: Int): ItemStack = ItemStack.EMPTY

    override fun removed(player: Player) {
        super.removed(player)
        if (!navigatingAway) onCancel?.invoke()
    }

    private fun named(item: Item, name: Component): ItemStack {
        val stack = ItemStack(item)
        stack.set(DataComponents.CUSTOM_NAME, name)
        return stack
    }
    private fun filler(item: Item): ItemStack = named(item, Component.literal(" "))

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

        private val TEAM_SLOTS = intArrayOf(19, 21, 23, 25)
        private val TEAM_SLOT_TO_INDEX: Map<Int, Int> =
            TEAM_SLOTS.withIndex().associate { (idx, slot) -> slot to idx }

        internal fun forServer(
            containerId: Int,
            playerInventory: Inventory,
            player: ServerPlayer,
            onConfirm: (List<Pokemon>) -> Unit,
            onCancel: () -> Unit,
            onBack: () -> Unit,
            onPickTeam: ((RentalTeams.RentalTeam) -> Unit)? = null,
        ): RentalTeamMenu =
            RentalTeamMenu(containerId, playerInventory, player, onConfirm, onCancel, onBack, onPickTeam)
    }
}

class RentalTeamMenuProvider(
    private val player: ServerPlayer,
    private val onConfirm: (List<Pokemon>) -> Unit,
    private val onCancel: () -> Unit,
    private val onBack: () -> Unit,
    private val onPickTeam: ((RentalTeams.RentalTeam) -> Unit)? = null,
) : MenuProvider {
    override fun getDisplayName(): Component = Component.literal("Rent a Team")
    override fun createMenu(containerId: Int, inv: Inventory, ignored: Player): AbstractContainerMenu =
        RentalTeamMenu.forServer(containerId, inv, player, onConfirm, onCancel, onBack, onPickTeam)
}
