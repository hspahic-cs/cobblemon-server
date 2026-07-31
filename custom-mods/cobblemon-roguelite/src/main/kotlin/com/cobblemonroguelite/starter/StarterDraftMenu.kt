package com.cobblemonroguelite.starter

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.item.PokemonItem
import com.cobblemonroguelite.run.RunCommands
import com.cobblemonroguelite.run.RunController
import com.cobblemonroguelite.run.RunStatus
import net.minecraft.core.component.DataComponents
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
 * The starter draft: a server-side chest GUI for spending the §2.13 budget, opened by `/roguelite
 * start confirm`.
 *
 * ### Why the typed command was never going to be the answer
 *
 * `/roguelite starter <id> <id> <id>` asks a player to know three species ids, spelled exactly, before
 * they have seen a single price — against a catalogue priced from PokéRogue's 542-species table. Per-slot
 * tab completion made that *possible*; it did not make it something a player would choose to do at the
 * front door of the mode. The command stays, because it is scriptable and because it is what the tests
 * drive, but it is no longer the way in.
 *
 * ### Same bet as the between-wave screen
 *
 * `ChestMenu` + `player.openMenu` renders on a vanilla client with no resource pack and no companion
 * mod, which keeps `cobblemon-roguelite` a one-jar install (§1.2). See
 * [com.cobblemonroguelite.shop.BetweenWaveMenu], which this deliberately mirrors down to the click
 * filtering — two menus in one mod that handle clicks differently is how one of them ends up with a
 * dupe bug the other does not.
 *
 * ### It owns no rules, including the ones it paints
 *
 * Every refusal comes from [StarterSelection.validate], which is also what decides whether a species in
 * the grid is drawn as affordable: the icon asks "would `picks + this` be accepted?" rather than doing
 * its own budget arithmetic. That is the difference between a display that can drift out of agreement
 * with the validator and one that cannot — and the drifting version is always the one no test covers.
 * Confirming re-validates through [RunController.chooseStarters], so nothing here is trusted.
 *
 * ### Why the catalogue is captured when the screen opens
 *
 * [StarterCatalogue] exists to make "the numbers a player was shown" and "the numbers they are judged
 * against" provably the same numbers, and re-deriving it on every repaint would quietly give that up —
 * a Pokémon caught in another window mid-draft would change prices under the player's cursor. It is
 * also free to capture: the catalogue is deterministic, so there is nothing to re-roll.
 */
object StarterDraftMenu {

    private const val ROWS = 6
    private const val SLOTS = ROWS * 9

    /** Row 0, far left: cycles [StarterDraftSort]. */
    private const val SORT_SLOT = 0

    /**
     * Row 0, far right: [StarterDraftMeter]'s five segments, filling towards the right-hand edge.
     *
     * Right-aligned rather than centred so the bar ends where the eye already goes for a "how full"
     * reading, and so the sort button at the other end of the row is never mistaken for part of it.
     */
    private val METER_SLOTS = listOf(4, 5, 6, 7, 8)

    /** Rows 1–4, full width. Kept as one number with [StarterDraftPaging.PER_PAGE] so they cannot disagree. */
    private const val GRID_FIRST = 9
    private const val GRID_SLOTS = StarterDraftPaging.PER_PAGE

    /** Row 5, left to right: page back, the picks, the budget, confirm, page forward. */
    private const val PREVIOUS_SLOT = 45
    private val PICK_SLOTS = listOf(47, 48, 49)
    private const val BUDGET_SLOT = 51
    private const val CONFIRM_SLOT = 52
    private const val NEXT_SLOT = 53

    /**
     * Open the draft for [player], or return false if they have no pending start.
     *
     * False rather than a message: both callers already have something to say about that case — the
     * command prints the catalogue in chat, and the start path has just printed a refusal.
     */
    fun openFor(player: ServerPlayer): Boolean {
        val status = RunController.status(player.server, player) as? RunStatus.AwaitingStarter ?: return false
        val catalogue = status.catalogue
        // An empty catalogue is an operator fault (an empty baseline pool, or every species unpriced),
        // and a chest of nothing is the worst possible way to report it. The chat path names the cause.
        if (catalogue.isEmpty) return false
        val container = SimpleContainer(SLOTS)
        player.openMenu(
            SimpleMenuProvider(
                { syncId, inv, _ -> Impl(syncId, inv, container, catalogue) },
                Component.literal("Choose your starters — ${catalogue.budget} points"),
            ),
        )
        return true
    }

    private class Impl(
        syncId: Int,
        inv: Inventory,
        private val container: Container,
        private val catalogue: StarterCatalogue,
    ) : ChestMenu(MenuType.GENERIC_9x6, syncId, inv, container, ROWS) {

        /**
         * The draft so far, in click order.
         *
         * Order is not cosmetic: [StarterSelection] hands the list to the party in the order it arrives,
         * so the first click is the lead. Held on the menu because a half-finished draft is not run
         * state — closing the window costs nothing and the catalogue is unchanged when it reopens.
         */
        private val picks = mutableListOf<ResourceLocation>()

        private var page = 0

        /**
         * The grid's order. A view, not a rule — see [StarterDraftSort].
         *
         * Held on the menu next to [page] because it is the same kind of thing: where the player is
         * looking. Neither survives the window closing, and neither is worth persisting.
         */
        private var sort = StarterDraftSort.CHEAPEST

        init {
            paint()
        }

        override fun clicked(slotId: Int, button: Int, clickType: ClickType, player: Player) {
            // The dupe vectors BetweenWaveMenu blocks, blocked identically: drag, number-key swap and
            // double-click-collect would each pull a display icon into a real inventory.
            if (clickType == ClickType.QUICK_CRAFT || clickType == ClickType.SWAP ||
                clickType == ClickType.PICKUP_ALL
            ) {
                return
            }
            if (slotId !in 0 until SLOTS) {
                super.clicked(slotId, button, clickType, player)
                return
            }
            // No super call for any chest slot: every one of them is a button.
            val sp = player as? ServerPlayer ?: return
            if (button != 0 || (clickType != ClickType.PICKUP && clickType != ClickType.QUICK_MOVE)) return

            when {
                slotId == SORT_SLOT -> cycleSort()
                slotId in GRID_FIRST until GRID_FIRST + GRID_SLOTS -> toggleFromGrid(slotId - GRID_FIRST)
                slotId in PICK_SLOTS -> removePick(PICK_SLOTS.indexOf(slotId))
                slotId == PREVIOUS_SLOT -> turnTo(page - 1)
                slotId == NEXT_SLOT -> turnTo(page + 1)
                slotId == CONFIRM_SLOT -> confirm(sp)
            }
        }

        // ------------------------------------------------------------------ actions

        /**
         * Re-sort, and go back to page 1.
         *
         * Staying on page 7 of a list that has just been reordered shows a screenful the player has no
         * way to relate to what they clicked. The point of sorting is to bring something to the front,
         * so the front is where it puts you.
         */
        private fun cycleSort() {
            sort = sort.next()
            page = 0
            paint()
        }

        private fun toggleFromGrid(index: Int) {
            val option = currentPage().options.getOrNull(index) ?: return
            // Clicking a picked species un-picks it, wherever it is clicked from. The alternative —
            // only the pick row removes — makes a player hunt for the slot they used, and the grid is
            // where their cursor already is.
            if (picks.remove(option.species)) {
                paint()
                return
            }
            // The draft cap is checked here and nowhere else in this file; StarterSelection owns the
            // number, and a full draft simply stops accepting clicks rather than reporting a refusal
            // the player can see coming from the three occupied slots.
            if (picks.size >= StarterSelection.MAX_STARTERS) return
            picks += option.species
            paint()
        }

        private fun removePick(index: Int) {
            if (index !in picks.indices) return
            picks.removeAt(index)
            paint()
        }

        private fun turnTo(index: Int) {
            val clamped = StarterDraftPaging.pageAt(catalogue.options, index).index
            if (clamped == page) return
            page = clamped
            paint()
        }

        /**
         * Hand the draft to the real validator.
         *
         * The window closes only on success. A refusal keeps it open with the picks intact, because
         * every refusal here is one the player can act on by changing a pick — closing the screen would
         * make them reopen it and start the draft again to fix a single click.
         */
        private fun confirm(player: ServerPlayer) {
            if (picks.isEmpty()) return
            // Reused rather than re-mapped: RunCommands already turns every StarterChoiceResult into the
            // message a player reads, and a second copy here would be the one that goes stale.
            if (RunCommands.chooseStarters(player, picks.toList()) > 0) {
                player.closeContainer()
                return
            }
            paint()
        }

        // ------------------------------------------------------------------ painting

        private fun currentPage(): StarterDraftPage =
            StarterDraftPaging.pageAt(sort.sort(catalogue.options), page)

        private fun spent(): Int = picks.sumOf { catalogue.costOf(it) ?: 0 }

        private fun paint() {
            for (slot in 0 until SLOTS) container.setItem(slot, ItemStack.EMPTY)

            val shown = currentPage()
            container.setItem(SORT_SLOT, sortIcon())
            paintMeter()
            shown.options.forEachIndexed { index, option ->
                container.setItem(GRID_FIRST + index, optionIcon(option))
            }

            PICK_SLOTS.forEachIndexed { index, slot -> container.setItem(slot, pickIcon(picks.getOrNull(index), index)) }

            if (shown.hasPrevious) container.setItem(PREVIOUS_SLOT, pageIcon("Previous", shown))
            if (shown.hasNext) container.setItem(NEXT_SLOT, pageIcon("Next", shown))
            container.setItem(BUDGET_SLOT, budgetIcon())
            container.setItem(CONFIRM_SLOT, confirmIcon())
            broadcastChanges()
        }

        private fun sortIcon() = label(
            Items.HOPPER,
            "§bSort: §f${sort.label}",
            listOf("§7Click to change.", "§8Next: ${sort.next().label}", "§8Order only — prices do not change."),
        )

        /**
         * The gauge, painted whether or not anything is spent.
         *
         * An unlit bar of grey panes is the empty state rather than five blank slots: a meter that only
         * appears once you have spent something is a meter nobody learns to read.
         */
        private fun paintMeter() {
            val spent = spent()
            val lit = StarterDraftMeter.filled(spent, catalogue.budget)
            METER_SLOTS.forEachIndexed { index, slot ->
                val on = index < lit
                val zone = StarterDraftMeter.zoneOf(index)
                val item = when {
                    !on -> Items.LIGHT_GRAY_STAINED_GLASS_PANE
                    zone == StarterDraftMeter.Zone.GREEN -> Items.LIME_STAINED_GLASS_PANE
                    zone == StarterDraftMeter.Zone.AMBER -> Items.YELLOW_STAINED_GLASS_PANE
                    else -> Items.RED_STAINED_GLASS_PANE
                }
                container.setItem(
                    slot,
                    label(
                        item,
                        "§f$spent §7of §f${catalogue.budget}§7 point(s) used",
                        listOf(
                            "§7${catalogue.budget - spent} left to spend.",
                            "§8The bar fills as you pick; red is the last of it.",
                        ),
                    ),
                )
            }
        }

        private fun optionIcon(option: StarterOption): ItemStack {
            val picked = option.species in picks
            // The validator, not a budget subtraction. See the class comment: this is the same question
            // the confirm button asks, so the grid cannot disagree with it.
            val takeable = picked || StarterSelection.validate(catalogue, picks + option.species) is StarterSelectionResult.Accepted
            val lore = mutableListOf("§7${option.cost} point(s)")
            lore += when {
                picked -> "§aIn your draft (slot ${picks.indexOf(option.species) + 1}) — click to remove"
                takeable -> "§aClick to add"
                picks.size >= StarterSelection.MAX_STARTERS -> "§8Draft is full"
                else -> "§cNot enough points left"
            }
            val colour = if (picked) "§a" else if (takeable) "§f" else "§8"
            // Two markers for one state, because a chest slot has nothing to draw a border on and no way
            // to tint the square itself. The enchantment glint is what carries at a glance across a grid
            // of thirty-six; the tick and the green name are what is left on a client whose resource pack
            // suppresses glint, and they read without relying on colour. A stack count was the obvious
            // third and does not work: vanilla hides a count of 1, so the first pick would show nothing.
            val name = if (picked) "$colour✔ ${nameOf(option.species)}" else "$colour${nameOf(option.species)}"
            return label(speciesIcon(option.species), name, lore, glint = picked)
        }

        private fun pickIcon(species: ResourceLocation?, index: Int): ItemStack {
            if (species == null) {
                return label(
                    Items.LIGHT_GRAY_STAINED_GLASS_PANE,
                    "§8Slot ${index + 1} — empty",
                    listOf("§8Click a Pokémon above.", "§8Up to ${StarterSelection.MAX_STARTERS}."),
                )
            }
            val cost = catalogue.costOf(species)
            return label(
                speciesIcon(species),
                "§a${index + 1}. ${nameOf(species)}",
                glint = true,
                lore = listOf(
                    "§7${cost ?: "?"} point(s)",
                    // Said here rather than in a tooltip nobody reads: the draft order is the party
                    // order, so slot 1 is the Pokémon that leads the first wave.
                    if (index == 0) "§7Leads the first wave." else "§7Party slot ${index + 1}.",
                    "§cClick to remove",
                ),
            )
        }

        private fun budgetIcon(): ItemStack {
            val spent = spent()
            return label(
                Items.GOLD_NUGGET,
                "§e${catalogue.budget - spent} of ${catalogue.budget} point(s) left",
                listOf(
                    "§7Spent §f$spent",
                    // §2.13: there is nothing to do with leftover points, and a player who does not know
                    // that will keep looking for the shop that spends them.
                    "§8Points left over are not carried into the run.",
                ),
            )
        }

        private fun confirmIcon(): ItemStack = when (val result = StarterSelection.validate(catalogue, picks)) {
            is StarterSelectionResult.Accepted -> label(
                Items.LIME_DYE,
                "§aStart the run",
                listOf("§7Spends §f${result.spent}§7 of §f${catalogue.budget}", "§8${picks.size} Pokémon."),
            )

            StarterSelectionResult.Empty ->
                label(Items.GRAY_DYE, "§8Start the run", listOf("§8Pick at least one Pokémon."))

            else -> label(Items.RED_DYE, "§cCannot start", listOf("§c${shortReason(result)}"))
        }

        private fun pageIcon(direction: String, shown: StarterDraftPage) = label(
            Items.ARROW,
            "§f$direction",
            listOf("§7Page §f${shown.humanIndex}§7 of §f${shown.pageCount}", "§8Cheapest first."),
        )

        /**
         * A one-line version of a refusal, for a button.
         *
         * [com.cobblemonroguelite.run.RunMessages.starterRejected] stays the wording a player is *told*
         * on a failed confirm — it explains what to do about it, which is right for chat and far too long
         * for lore. Nothing branches on these strings.
         */
        private fun shortReason(result: StarterSelectionResult): String = when (result) {
            is StarterSelectionResult.OverBudget -> "Over budget: ${result.spent} of ${result.budget}"
            is StarterSelectionResult.TooMany -> "At most ${result.max}"
            is StarterSelectionResult.Duplicate -> "Duplicate pick"
            is StarterSelectionResult.NotEligible -> "Not available to you"
            is StarterSelectionResult.Unpriced -> "No price set — tell an operator"
            StarterSelectionResult.Empty -> "Pick at least one"
            is StarterSelectionResult.Accepted -> ""
        }

        /**
         * The real sprite where Cobblemon can give one.
         *
         * [PokemonItem] is what `cobblemon-ranked`'s menus already use here, so a grid of forty-five
         * species reads as Pokémon rather than as forty-five identical bits of paper. A species the
         * registry cannot resolve falls back rather than throwing: an unresolvable id is an operator's
         * pricing table naming something this server does not have, and losing the whole screen over one
         * bad row would hide the other forty-four.
         */
        private fun speciesIcon(species: ResourceLocation): ItemStack =
            runCatching { PokemonSpecies.getByIdentifier(species)?.let { PokemonItem.from(it) } }
                .getOrNull() ?: ItemStack(Items.PAPER)

        private fun nameOf(species: ResourceLocation): String =
            runCatching { PokemonSpecies.getByIdentifier(species)?.name }.getOrNull() ?: species.path

        private fun label(icon: ItemStack, name: String, lore: List<String>, glint: Boolean = false): ItemStack {
            val stack = if (icon.isEmpty) ItemStack(Items.PAPER) else icon
            stack.set(DataComponents.CUSTOM_NAME, line(name))
            stack.set(DataComponents.LORE, ItemLore(lore.map { line(it) as Component }))
            // Set explicitly either way rather than only when true: these stacks are rebuilt per paint,
            // but PokemonItem.from is free to hand back something already carrying components, and an
            // un-picked species that glinted would be the same bug as a picked one that did not.
            stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, glint)
            return stack
        }

        private fun label(item: Item, name: String, lore: List<String>, glint: Boolean = false): ItemStack =
            label(ItemStack(item), name, lore, glint)

        /** Italics off, the way every other menu in this repo builds a label. */
        private fun line(text: String): MutableComponent =
            Component.literal(text).setStyle(Style.EMPTY.withItalic(false))
    }
}
