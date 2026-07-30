package com.cobblemonroguelite.shop

import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemonroguelite.data.reward.RewardEntry
import com.cobblemonroguelite.data.reward.RewardTables
import com.cobblemonroguelite.data.reward.RunReward
import com.cobblemonroguelite.data.shop.ShopEntry
import com.cobblemonroguelite.data.shop.ShopTables
import com.cobblemonroguelite.run.RunState
import com.cobblemonroguelite.run.RunStore
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.Container
import net.minecraft.world.SimpleContainer
import net.minecraft.world.SimpleMenuProvider
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.inventory.ClickType
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.ItemLore
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("cobblemon_roguelite/shop")

/**
 * The between-wave screen: a server-side chest GUI that **opens itself** when a wave is cleared.
 *
 * ### Why this exists, and why commands were never going to be enough
 *
 * The step was reachable only through `/roguelite reward take <id> [slot]` and `/roguelite shop buy`.
 * That is fine for one wave and unusable for two hundred — a player would be typing item ids between
 * every fight, which is the sort of friction that gets a feature abandoned rather than criticised.
 *
 * ### Why a chest menu rather than a client mod
 *
 * `ChestMenu` + `player.openMenu` is entirely server-side: it renders on a **vanilla client**, needs no
 * resource pack, and cannot desync with a client that has the wrong version. `cobblemon-market` and
 * `cobblemon-auction` already do this here, so it is a proven path in this repo rather than a new bet.
 *
 * It also keeps `cobblemon-roguelite` publishable and self-contained (§1.2): a companion client mod
 * would make the mode a two-jar install for everyone who wanted it, and would put the run's rules on a
 * machine the server does not control.
 *
 * ### This is a front end and nothing more
 *
 * Every rule still lives where it lived: [ShopStock] decides what is stocked, [RewardOffer] what is on
 * offer, [RewardTargeting] who it lands on, [RewardGrant] what it does. This file translates clicks
 * into those calls and paints the result. That separation is load-bearing — two copies of "can this be
 * afforded" would eventually disagree, and the GUI's copy would be the one nothing tests.
 *
 * ### The slot problem the GUI solves for free
 *
 * A per-Pokémon reward needs a party member, and on the command path that is an argument a player has
 * to know to type — [RewardTargeting] refuses without it rather than guessing at the lead. Here the
 * same refusal becomes a second screen showing the party, and picking is a click. The refusal path is
 * still what runs underneath; it just stops being something the player has to read about.
 */
object BetweenWaveMenu {

    private const val ROWS = 6
    private const val SLOTS = ROWS * 9

    /** Row 0 is the paid row, left-aligned; slot 8 is the credit counter. */
    private const val CREDITS_SLOT = 8
    private const val SHOP_FIRST = 0
    private const val SHOP_LAST = 6

    /** Row 2, centred: the three free options. A fourth would need this list extending. */
    private val OFFER_SLOTS = listOf(20, 22, 24)

    /** Row 5: the two actions. */
    private const val REROLL_SLOT = 45
    private const val CONTINUE_SLOT = 53

    /** Party picker occupies row 3 while a target is pending. */
    private val PARTY_SLOTS = (27..32).toList()

    /**
     * Open the screen for [player], or do nothing if they have no run.
     *
     * Called both by the command (`/roguelite shop`) and automatically on a cleared wave, which is the
     * point of it — see [com.cobblemonroguelite.run.RunController.waveCleared].
     */
    fun openFor(player: ServerPlayer): Boolean {
        val run = RunStore.of(player.server).get(player.uuid) ?: return false
        // Refused mid-battle for the reason the commands are: a shop usable during a fight lets a
        // player buy their way out of a losing turn.
        if (run.battle != null) return false
        val container = SimpleContainer(SLOTS)
        player.openMenu(
            SimpleMenuProvider(
                { syncId, inv, _ -> Impl(syncId, inv, container, player, run) },
                Component.literal("Wave ${run.wave} — choose one"),
            ),
        )
        return true
    }

    private class Impl(
        syncId: Int,
        inv: Inventory,
        private val container: Container,
        private val viewer: ServerPlayer,
        private val run: RunState,
    ) : ChestMenu(MenuType.GENERIC_9x6, syncId, inv, container, ROWS) {

        /**
         * The purchase or pick waiting on a party member, or null when the grid is showing normally.
         *
         * Held on the menu rather than on the run: it is a half-finished click, not run state, and
         * persisting it would mean a player who closed the window mid-pick came back to a run that
         * thought it owed them something.
         */
        private var pending: Pending? = null

        private data class Pending(val paid: Boolean, val entryId: String, val label: String)

        init {
            paint()
        }

        override fun clicked(slotId: Int, button: Int, clickType: ClickType, player: Player) {
            // The dupe vectors, blocked exactly as MarketMenu blocks them: drag, number-key swap and
            // double-click-collect would all pull the display copies into a real inventory.
            if (clickType == ClickType.QUICK_CRAFT || clickType == ClickType.SWAP ||
                clickType == ClickType.PICKUP_ALL
            ) {
                return
            }
            // Outside the chest is the player's own inventory, which behaves normally.
            if (slotId !in 0 until SLOTS) {
                super.clicked(slotId, button, clickType, player)
                return
            }
            // Deliberately no super call for any chest slot: every one of them is a button, and letting
            // the default handler run would hand the icon to the cursor.
            val sp = player as? ServerPlayer ?: return
            if (button != 0 || (clickType != ClickType.PICKUP && clickType != ClickType.QUICK_MOVE)) return

            when {
                pending != null -> resolvePending(sp, slotId)
                slotId == CONTINUE_SLOT -> sp.closeContainer()
                slotId == REROLL_SLOT -> reroll(sp)
                slotId in SHOP_FIRST..SHOP_LAST -> beginShop(sp, slotId)
                slotId in OFFER_SLOTS -> beginOffer(sp, slotId)
            }
        }

        // ------------------------------------------------------------------ actions

        private fun beginShop(player: ServerPlayer, slotId: Int) {
            val entry = stock().getOrNull(slotId - SHOP_FIRST) ?: return
            // Affordability is checked by ShopStock.buy, not here. Checking twice is how the GUI and the
            // command start disagreeing, and this copy is the one no test covers.
            val needsMember = RewardTargeting.needsMember(entry.reward)
            if (needsMember && run.partySnapshot().size > 1) {
                pending = Pending(paid = true, entryId = entry.id, label = entry.id)
                paint()
                return
            }
            finishShop(player, entry.id, slot = null)
        }

        private fun beginOffer(player: ServerPlayer, slotId: Int) {
            if (run.rewardTakenThisWave) return
            val entry = offer().getOrNull(OFFER_SLOTS.indexOf(slotId)) ?: return
            if (RewardTargeting.needsMember(entry.reward) && run.partySnapshot().size > 1) {
                pending = Pending(paid = false, entryId = entry.id, label = entry.id)
                paint()
                return
            }
            finishOffer(player, entry.id, slot = null)
        }

        private fun resolvePending(player: ServerPlayer, slotId: Int) {
            val waiting = pending ?: return
            val index = PARTY_SLOTS.indexOf(slotId)
            if (index < 0) {
                // Anything outside the party row cancels, including the continue button. A pick that
                // could only be escaped by closing the window would trap a player who mis-clicked.
                pending = null
                paint()
                return
            }
            val slot = index + 1
            pending = null
            if (waiting.paid) finishShop(player, waiting.entryId, slot) else finishOffer(player, waiting.entryId, slot)
        }

        private fun finishShop(player: ServerPlayer, entryId: String, slot: Int?) {
            val table = ShopTables.default() ?: return
            when (val result = ShopStock.buy(table, run.wave, run.credits, entryId)) {
                is PurchaseResult.Ok -> {
                    val party = run.partySnapshot()
                    val target = RewardTargeting.resolve(result.entry.reward, slot, party.size)
                    if (target is RewardTarget.Unresolved) {
                        player.sendSystemMessage(ShopMessages.needsSlot(target.reason))
                        return
                    }
                    run.credits = result.remaining
                    val granted = RewardGrant.apply(result.entry.reward, target, party)
                    checkpoint(player)
                    player.sendSystemMessage(ShopMessages.bought(entryId, result.price, run.credits, granted))
                }

                is PurchaseResult.NotEnoughCredits ->
                    player.sendSystemMessage(ShopMessages.tooPoor(result.have, result.need))

                is PurchaseResult.NoSuchEntry -> player.sendSystemMessage(ShopMessages.noSuchEntry(result.id))
                is PurchaseResult.NotStocked -> player.sendSystemMessage(ShopMessages.notStocked(result.id))
            }
            paint()
        }

        private fun finishOffer(player: ServerPlayer, entryId: String, slot: Int?) {
            val table = RewardTables.default() ?: return
            when (val result = RewardOffer.take(table, run.wave, run.seed, run.rerollsThisWave, entryId)) {
                is TakeResult.Ok -> {
                    val party = run.partySnapshot()
                    val target = RewardTargeting.resolve(result.entry.reward, slot, party.size)
                    if (target is RewardTarget.Unresolved) {
                        player.sendSystemMessage(ShopMessages.needsSlot(target.reason))
                        return
                    }
                    val granted = RewardGrant.apply(result.entry.reward, target, party)
                    run.rewardTakenThisWave = true
                    checkpoint(player)
                    player.sendSystemMessage(ShopMessages.taken(entryId, granted))
                }

                is TakeResult.NoSuchEntry -> player.sendSystemMessage(ShopMessages.noSuchEntry(result.id))
                is TakeResult.NotOffered -> player.sendSystemMessage(ShopMessages.notOffered(result.id))
            }
            paint()
        }

        private fun reroll(player: ServerPlayer) {
            if (run.rewardTakenThisWave) return
            when (val result = RewardOffer.reroll(run.credits, run.rerollsThisWave, run.wave)) {
                is RerollResult.Ok -> {
                    run.credits = result.remaining
                    run.rerollsThisWave++
                    checkpoint(player)
                }

                is RerollResult.NotEnoughCredits ->
                    player.sendSystemMessage(ShopMessages.tooPoor(result.have, result.need))

                RerollResult.Disabled -> player.sendSystemMessage(ShopMessages.rerollDisabled())
            }
            paint()
        }

        private fun checkpoint(player: ServerPlayer) =
            RunStore.of(player.server).checkpoint(player.server, player.uuid)

        // ------------------------------------------------------------------ painting

        private fun stock(): List<ShopEntry> =
            ShopTables.default()?.let { ShopStock.stockAt(it, run.wave) }.orEmpty()

        private fun offer(): List<RewardEntry> =
            RewardTables.default()?.let { RewardOffer.offerFor(it, run.wave, run.seed, run.rerollsThisWave) }
                .orEmpty()

        /** Repaint in place. Cheaper than reopening, and it keeps the window from flickering shut. */
        private fun paint() {
            for (slot in 0 until SLOTS) container.setItem(slot, ItemStack.EMPTY)

            container.setItem(CREDITS_SLOT, creditsIcon())

            stock().take(SHOP_LAST - SHOP_FIRST + 1).forEachIndexed { index, entry ->
                container.setItem(SHOP_FIRST + index, shopIcon(entry))
            }

            val waiting = pending
            if (waiting != null) {
                container.setItem(REROLL_SLOT, label(Items.BARRIER, "§cCancel", listOf("§7Click anywhere else too.")))
                run.partySnapshot().forEachIndexed { index, pokemon ->
                    PARTY_SLOTS.getOrNull(index)?.let { container.setItem(it, partyIcon(pokemon, index + 1, waiting)) }
                }
            } else {
                offer().forEachIndexed { index, entry ->
                    OFFER_SLOTS.getOrNull(index)?.let { container.setItem(it, offerIcon(entry)) }
                }
                container.setItem(REROLL_SLOT, rerollIcon())
                container.setItem(CONTINUE_SLOT, continueIcon())
            }
            broadcastChanges()
        }

        private fun creditsIcon() = label(
            Items.GOLD_NUGGET,
            "§e${run.credits} credit(s)",
            listOf("§7Wave §f${run.wave}", "§8Earned by clearing waves."),
        )

        private fun shopIcon(entry: ShopEntry): ItemStack {
            val price = entry.priceAt(run.wave)
            val affordable = price <= run.credits
            val colour = if (affordable) "§f" else "§8"
            val lore = mutableListOf("§7$price credit(s)", describe(entry.reward))
            lore += if (affordable) "§aClick to buy" else "§cNot enough credits"
            return label(iconFor(entry.reward), "$colour${entry.id}", lore)
        }

        private fun offerIcon(entry: RewardEntry): ItemStack {
            // No price, ever. If one appears here the two halves have been merged again — see RewardOffer.
            val taken = run.rewardTakenThisWave
            val lore = mutableListOf(describe(entry.reward), "§8tier: ${entry.tier}")
            lore += if (taken) "§8Already taken this wave" else "§aClick to take — the other two are gone"
            return label(iconFor(entry.reward), if (taken) "§8${entry.id}" else "§b${entry.id}", lore)
        }

        private fun rerollIcon(): ItemStack {
            val price = ShopSettings.shop.rerollPrice(run.rerollsThisWave, run.wave)
                ?: return label(Items.GRAY_DYE, "§8Reroll unavailable", listOf("§8Not enabled on this server."))
            val affordable = price <= run.credits && !run.rewardTakenThisWave
            return label(
                if (affordable) Items.ENDER_PEARL else Items.GRAY_DYE,
                if (affordable) "§dReroll — $price" else "§8Reroll — $price",
                listOf("§7Rerolls the three options.", "§8Rerolled ${run.rerollsThisWave} time(s) this wave."),
            )
        }

        private fun continueIcon() = label(
            Items.LIME_DYE,
            "§aContinue",
            listOf("§7Closes this screen.", "§8Then /roguelite resume for the next wave."),
        )

        private fun partyIcon(pokemon: Pokemon, slot: Int, waiting: Pending): ItemStack = label(
            Items.PAPER,
            "§f$slot. ${runCatching { pokemon.species.name }.getOrDefault("?")}",
            listOf(
                "§7Level ${runCatching { pokemon.level }.getOrDefault(0)}",
                "§aClick to apply §f${waiting.label}",
            ),
        )

        /**
         * A vanilla icon per reward kind.
         *
         * Vanilla on purpose: a `cobblemon:` or third-party id can be absent on a given server, and an
         * icon that resolves to air would leave an invisible, unclickable-looking button. A held item is
         * the one case where the real item is tried first, because seeing the actual Leftovers is worth
         * more than consistency — with a paper fallback when it is not installed.
         */
        private fun iconFor(reward: RunReward): Item = when (reward) {
            is RunReward.Evs -> Items.POTION
            is RunReward.Levels -> Items.EXPERIENCE_BOTTLE
            is RunReward.Mint -> Items.SUGAR
            is RunReward.AbilityPatch -> Items.NETHER_STAR
            is RunReward.BagItem -> Items.CHEST
            is RunReward.TechnicalMachine -> Items.ENCHANTED_BOOK
            is RunReward.HeldItem ->
                BuiltInRegistries.ITEM.getOptional(reward.item).orElse(Items.PAPER)
        }

        private fun describe(reward: RunReward): String = when (reward) {
            is RunReward.Evs -> "§7${signed(reward.amount)} ${reward.stat.identifier.path} EVs"
            is RunReward.Levels -> "§7${signed(reward.amount)} level(s)"
            is RunReward.Mint -> "§7Nature -> ${reward.nature.path}"
            is RunReward.AbilityPatch -> "§7Ability -> ${reward.ability ?: "hidden ability"}"
            is RunReward.BagItem -> "§7${reward.count}x ${reward.item.path} (bag)"
            is RunReward.HeldItem -> "§7Held item -> ${reward.item.path}"
            is RunReward.TechnicalMachine -> "§7Teaches ${reward.move}"
        }

        private fun signed(amount: Int) = if (amount >= 0) "+$amount" else "$amount"

        private fun label(item: Item, name: String, lore: List<String>): ItemStack {
            val stack = ItemStack(item)
            stack.set(DataComponents.CUSTOM_NAME, line(name))
            stack.set(DataComponents.LORE, ItemLore(lore.map { line(it) as Component }))
            return stack
        }

        /** Italics off, the way every other menu in this repo builds a label. */
        private fun line(text: String): MutableComponent =
            Component.literal(text).setStyle(Style.EMPTY.withItalic(false))
    }
}
