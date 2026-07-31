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
import java.util.UUID

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
 * ### Two controls were removed rather than kept
 *
 * A sort cycle and a stats toggle used to sit on the control row, and the screen read as cluttered with
 * them there — five competing icon languages across a book, nuggets, a hopper, a spyglass, dyes and
 * glass panes, on a screen whose whole job is "look at Pokémon".
 *
 * Neither was load-bearing once the cost tabs existed. [StarterCatalogue.options] is ordered by (cost,
 * then id), so inside a single-cost tab every entry ties on cost and the tie-break carries it — the
 * grid is alphabetical for free, which was the only ordering the sort cycle offered that the tabs do
 * not. The stats toggle guarded against a noisy tooltip, and the tooltip stopped being noisy; a toggle
 * whose off state nobody wants is a slot spent on nothing.
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

    /** Row 0, all nine slots: [StarterDraftFilter.tabsFor]'s cost tabs. */
    private val TAB_SLOTS = (0..8).toList()

    /**
     * The right-hand column, **bottom to top** — [StarterDraftMeter]'s five segments.
     *
     * Bottom-up because that is the only way a vertical gauge reads: a bar that drains downwards as
     * you spend would say the opposite of what it means. It owns the column outright, which is why the
     * grid is eight wide rather than nine.
     */
    private val METER_SLOTS = listOf(53, 44, 35, 26, 17)

    /**
     * Rows 1–4, columns 0–7. Not contiguous, because the meter has the ninth column.
     *
     * Built from the layout rather than written out so it cannot drift from
     * [StarterDraftPaging.PER_PAGE]; the `require` is what says so out loud if it ever does.
     */
    private val GRID_SLOTS: List<Int> = (1..4).flatMap { row -> (0..7).map { column -> row * 9 + column } }

    /**
     * Row 5, left to right: the two page arrows, the picks, confirm.
     *
     * A sort cycle and a stats toggle used to sit at 50 and 51; both are gone, for the reasons in this
     * file's header. Their slots stay empty rather than being backfilled, because the row reads as three
     * groups — navigate, draft, commit — and filling the gap would merge the last two.
     */
    private const val PREVIOUS_SLOT = 45
    private const val NEXT_SLOT = 46
    private val PICK_SLOTS = listOf(47, 48, 49)
    private const val CONFIRM_SLOT = 52

    init {
        require(GRID_SLOTS.size == StarterDraftPaging.PER_PAGE) {
            "the grid holds ${GRID_SLOTS.size} slots but a page is ${StarterDraftPaging.PER_PAGE} entries"
        }
    }

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
                { syncId, inv, _ -> Impl(syncId, inv, container, catalogue, player.uuid) },
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
        /** Whose progression the stat sheets are read against — the IV floor and the hidden-ability
         *  unlock are per player (§2.15, §2.17), so a shared sheet would show one player another's. */
        private val viewer: UUID,
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

        /** Which cost tab is open. [StarterDraftFilter.All] until a player narrows it. */
        private var filter: StarterDraftFilter = StarterDraftFilter.All

        /**
         * The tabs, derived once from the captured catalogue.
         *
         * Constant for the life of the window because the catalogue is, so recomputing them per paint
         * would be a distinct-and-sort over 542 entries on every click to reach the same answer.
         */
        private val tabs: List<StarterDraftFilter> = StarterDraftFilter.tabsFor(catalogue.options, TAB_SLOTS.size)

        /**
         * Sheets built once per open, not once per paint.
         *
         * Painting touches thirty-six icons and every sheet is a species lookup plus two progression
         * reads. None of the inputs can change while the window is open — the catalogue is captured,
         * and the IV floor only moves when a run ends — so rebuilding them on every click would be
         * work with no possible different answer.
         */
        private val sheets = mutableMapOf<ResourceLocation, StarterStatSheet?>()

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
                slotId in TAB_SLOTS -> selectTab(TAB_SLOTS.indexOf(slotId))
                slotId in GRID_SLOTS -> toggleFromGrid(GRID_SLOTS.indexOf(slotId))
                slotId in PICK_SLOTS -> removePick(PICK_SLOTS.indexOf(slotId))
                slotId == PREVIOUS_SLOT -> turnTo(page - 1)
                slotId == NEXT_SLOT -> turnTo(page + 1)
                slotId == CONFIRM_SLOT -> confirm(sp)
            }
        }

        // ------------------------------------------------------------------ actions

        /** Resets to page 1: the page you were on no longer means anything against a different list. */
        private fun selectTab(index: Int) {
            val chosen = tabs.getOrNull(index) ?: return
            if (chosen == filter) return
            filter = chosen
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

        /**
         * The open tab's slice, in the catalogue's own order.
         *
         * That order is (cost, then id), which is why the sort control could go: inside a single-cost
         * tab every entry ties on cost, so the tie-break carries it and the grid is alphabetical for
         * free — which was the only thing the sort cycle offered that the tabs do not.
         */
        private fun visible(): List<StarterOption> = catalogue.options.filter(filter::matches)

        private fun currentPage(): StarterDraftPage = StarterDraftPaging.pageAt(visible(), page)

        private fun spent(): Int = picks.sumOf { catalogue.costOf(it) ?: 0 }

        private fun paint() {
            for (slot in 0 until SLOTS) container.setItem(slot, ItemStack.EMPTY)

            val shown = currentPage()
            tabs.forEachIndexed { index, tab ->
                TAB_SLOTS.getOrNull(index)?.let { container.setItem(it, tabIcon(tab)) }
            }
            paintMeter()
            shown.options.forEachIndexed { index, option ->
                GRID_SLOTS.getOrNull(index)?.let { container.setItem(it, optionIcon(option)) }
            }

            PICK_SLOTS.forEachIndexed { index, slot -> container.setItem(slot, pickIcon(picks.getOrNull(index), index)) }

            if (shown.hasPrevious) container.setItem(PREVIOUS_SLOT, pageIcon("Previous", shown))
            if (shown.hasNext) container.setItem(NEXT_SLOT, pageIcon("Next", shown))
            container.setItem(CONFIRM_SLOT, confirmIcon())
            broadcastChanges()
        }

        /**
         * One cost tab: **as many nuggets as the tab costs**.
         *
         * The first version was one nugget per tab with the price in the custom name, and in play that
         * was a row of six identical nuggets — the name only renders on hover, so the tab row carried
         * no information until you pointed at it one slot at a time. A stack count renders in the slot
         * itself, so "which of these is the 3-point tab" is answered by looking.
         *
         * The 1-point tab is the known rough edge: vanilla hides a count of 1, so it shows a bare
         * nugget. It is still the leftmost tab after All and it still says so on hover, and there is no
         * component that forces the count to draw — the alternative would be a client mod for one slot.
         *
         * Selection is marked the same way a picked species is — glint, tick, green — rather than with
         * a fourth visual language nobody has learned yet.
         */
        private fun tabIcon(tab: StarterDraftFilter): ItemStack {
            val selected = tab == filter
            val available = catalogue.options.count(tab::matches)
            val icon = when (tab) {
                is StarterDraftFilter.All -> ItemStack(Items.BOOK)
                is StarterDraftFilter.Exactly -> ItemStack(Items.GOLD_NUGGET, tab.cost)
                is StarterDraftFilter.AtLeast -> ItemStack(Items.GOLD_NUGGET, tab.cost)
            }
            return label(
                icon,
                if (selected) "§a✔ ${tab.label}" else "§f${tab.label}",
                listOf(
                    if (tab is StarterDraftFilter.All) "§7Every Pokémon you can buy" else "§7Costing §f${tab.label}§7 point(s)",
                    "§7$available available",
                    if (selected) "§8Showing this now." else "§8Click to show only these.",
                ),
                glint = selected,
            )
        }

        private fun sheetFor(species: ResourceLocation): StarterStatSheet? =
            sheets.getOrPut(species) { StarterStatSheets.of(viewer, species) }

        /**
         * The gauge, painted whether or not anything is spent.
         *
         * An unlit bar of grey panes is the empty state rather than five blank slots: a meter that only
         * appears once you have spent something is a meter nobody learns to read.
         *
         * Every segment carries the exact numbers, which is why there is no separate budget icon any
         * more: the column is five slots a cursor crosses constantly, so the readout is never more than
         * a hover away and a sixth button competing for the control row was not earning its slot.
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
                            // §2.13: there is nothing to do with leftover points, and a player who does
                            // not know that will keep looking for the shop that spends them.
                            "§8Points left over are not carried into the run.",
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
            // Between the price and the verdict: the price is why you are looking, the sheet is what
            // you look at, and "click to add" is the last line either way so it never moves.
            sheetFor(option.species)?.let { lore += StarterStatLines.render(it) }
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
            val lore = mutableListOf("§7${cost ?: "?"} point(s)")
            // The sheet again, so a player comparing their three picks against each other does not have
            // to go back to the grid and find them.
            sheetFor(species)?.let { lore += StarterStatLines.render(it) }
            // Said here rather than in a tooltip nobody reads: the draft order is the party order, so
            // slot 1 is the Pokémon that leads the first wave.
            lore += if (index == 0) "§7Leads the first wave." else "§7Party slot ${index + 1}."
            lore += "§cClick to remove"
            return label(speciesIcon(species), "§a${index + 1}. ${nameOf(species)}", lore, glint = true)
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
