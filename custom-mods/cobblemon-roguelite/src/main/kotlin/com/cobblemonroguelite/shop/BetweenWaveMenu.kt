package com.cobblemonroguelite.shop

import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemonroguelite.data.reward.RewardEntry
import com.cobblemonroguelite.data.reward.RewardTables
import com.cobblemonroguelite.data.reward.RunReward
import com.cobblemonroguelite.data.shop.ShopEntry
import com.cobblemonroguelite.data.shop.ShopTables
import com.cobblemonroguelite.run.RunCommands
import com.cobblemonroguelite.run.RunHud
import com.cobblemonroguelite.run.RunPassive
import com.cobblemonroguelite.run.RunState
import com.cobblemonroguelite.run.RunStore
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
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

    /** Row 0 holds the paid row, centred; slot 8 is the money counter. */
    private const val CREDITS_SLOT = 8
    private const val SHOP_FIRST = 0
    private const val SHOP_LAST = 6

    /** Columns 2/4/6 first — the free row's columns — then the gaps, then the far edge. */
    private val SHOP_FILL_ORDER = listOf(2, 4, 6, 1, 3, 5, 0)

    /**
     * Where a row of [count] items sits so it reads as centred rather than as a row that ran out.
     *
     * Left-aligned, three items in a seven-slot row looked like four slots had failed to load — the eye
     * reads a ragged right edge as missing content, not as space. The free options below have always
     * been centred (20/22/24), so this also stops the two halves of §2.12 disagreeing about where the
     * middle of the screen is.
     *
     * Revised after the playtest: not merely centred, but ALIGNED with the free row below. The three
     * free options sit at columns 2/4/6, and a centred-contiguous paid row of three sat at 2/3/4 —
     * two rows both claiming to be the middle of the screen and agreeing about nothing. The paid row
     * now fills the same columns first and the gaps between them as stock grows, so the two halves of
     * §2.12 read as one grid. Never past column 6: the counter lives at slot 8.
     */
    private fun shopSlotsFor(count: Int): List<Int> =
        SHOP_FILL_ORDER.take(count.coerceIn(0, SHOP_FILL_ORDER.size)).sorted()

    /** Row 2, centred: the three free options. A fourth would need this list extending. */
    private val OFFER_SLOTS = listOf(20, 22, 24)

    /** Row 5: the two actions. */
    private const val REROLL_SLOT = 45
    private const val CONTINUE_SLOT = 53

    /** Party picker occupies row 3 while a target is pending. */
    private val PARTY_SLOTS = (27..32).toList()

    /**
     * Rows 1 and 4, drawn as solid bars.
     *
     * The screen was mostly holes — three items in a row of seven, three options adrift in the middle,
     * and two whole empty rows — which reads as a menu that failed to load rather than as a menu with
     * space in it. Filling the gaps is half the fix; the other half is that these two rows are not
     * decoration. They sit exactly on §2.12's seam: everything above the first bar is PAID and drawn
     * from your money, everything between the bars is the FREE pick, and the bottom bar separates both
     * from the actions. That split is the whole design of the step and until now nothing on screen
     * said so.
     */
    private val DIVIDER_ROWS = ((9..17) + (36..44)).toList()

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

        /**
         * A TM aimed at a Pokémon whose moveset is full: the take/buy has NOT happened yet, and does
         * not until a move is chosen — the playtest's "TMs don't work" was exactly this path
         * consuming the wave's one free pick and then refusing to overwrite. [memberSlot] is 1-based
         * like everything the player sees.
         */
        private data class PendingMove(val paid: Boolean, val entryId: String, val label: String, val memberSlot: Int)

        private var pendingMove: PendingMove? = null

        /** Row 3 again: up to four moves to forget. Same row as the party picker, same reading. */
        private val MOVE_SLOTS = listOf(27, 28, 29, 30)

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
                pendingMove != null -> resolveMoveChoice(sp, slotId)
                pending != null -> resolvePending(sp, slotId)
                slotId == CONTINUE_SLOT -> {
                    // #5 from the playtest: closing the shop dead-ended and nothing started the next
                    // wave — waveCleared deliberately does not chain (§2.12 owns the gap), so the
                    // gap's END has to. Continue IS the end of the between-wave step.
                    sp.closeContainer()
                    RunCommands.resume(sp)
                }
                slotId == REROLL_SLOT -> reroll(sp)
                slotId in SHOP_FIRST..SHOP_LAST -> beginShop(sp, slotId)
                slotId in OFFER_SLOTS -> beginOffer(sp, slotId)
            }
        }

        // ------------------------------------------------------------------ actions

        private fun beginShop(player: ServerPlayer, slotId: Int) {
            // Indexed through the same centring the paint uses, not by subtracting SHOP_FIRST. That
            // subtraction was correct only while the row was left-aligned, and would now buy whichever
            // item happened to sit that many slots from the edge.
            val stocked = stock().take(SHOP_LAST - SHOP_FIRST + 1)
            val entry = shopSlotsFor(stocked.size).indexOf(slotId).takeIf { it >= 0 }?.let(stocked::get) ?: return
            // Affordability is checked by ShopStock.buy, not here. Checking twice is how the GUI and the
            // command start disagreeing, and this copy is the one no test covers.
            val needsMember = RewardTargeting.needsMember(entry.reward)
            if (needsMember && run.partySnapshot().size > 1) {
                pending = Pending(paid = true, entryId = entry.id, label = entry.id)
                paint()
                return
            }
            if (needsMember && interceptTm(player, paid = true, entryId = entry.id, label = entry.id, memberSlot = 1)) return
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
            if (RewardTargeting.needsMember(entry.reward) &&
                interceptTm(player, paid = false, entryId = entry.id, label = entry.id, memberSlot = 1)
            ) {
                return
            }
            // The free row's consumables follow the same immediate-use ruling as the shop's: the
            // overlay opens BEFORE the take, so cancelling keeps the wave's one free pick — the same
            // nothing-irreversible-before-the-choice shape as the TM gate above.
            val bagReward = entry.reward as? RunReward.BagItem
            val selectingItem = bagReward?.let {
                BuiltInRegistries.ITEM.getOptional(it.item).orElse(null) as? com.cobblemon.mod.common.api.item.PokemonSelectingItem
            }
            if (bagReward != null && selectingItem != null) {
                val entryId = entry.id
                player.closeContainer()
                com.cobblemonroguelite.run.RunTicks.schedule(2) {
                    val online = player.server.playerList.getPlayer(player.uuid) ?: return@schedule
                    val sample = com.cobblemonroguelite.run.RunItems.mark(ItemStack(selectingItem as Item, 1), run.seed)
                    com.cobblemon.mod.common.api.callback.PartySelectCallbacks.createFromPokemon(
                        online,
                        sample.hoverName,
                        run.partySnapshot(),
                        { pokemon -> runCatching { selectingItem.canUseOnPokemon(sample, pokemon) }.getOrDefault(false) },
                        { cancelled -> BetweenWaveMenu.openFor(cancelled) },
                        { pokemon ->
                            val table = RewardTables.default()
                            val take = table?.let {
                                RewardOffer.take(
                                    it, run.wave, run.seed, run.rerollsThisWave, entryId,
                                    party = RewardOffer.partyStateOf(run.partySnapshot()),
                                )
                            }
                            if (take is TakeResult.Ok) {
                                val outcome = runCatching { selectingItem.applyToPokemon(online, sample.copy(), pokemon) }
                                if (outcome.isSuccess) {
                                    run.rewardTakenThisWave = true
                                    checkpoint(online)
                                    online.sendSystemMessage(
                                        ShopMessages.taken(entryId, GrantResult.Ok("used on ${pokemon.species.name}")),
                                    )
                                } else {
                                    log.warn("roguelite: free-pick instant-use apply failed for {}", entryId, outcome.exceptionOrNull())
                                }
                            }
                            BetweenWaveMenu.openFor(online)
                        },
                    )
                }
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

            // The learnset gate, BEFORE the pending pick is cleared: an ineligible Pokémon keeps the
            // PICKER open rather than bouncing the player back to the grid, because the whole point of
            // refusing (TmEligibility's ruling) is that they aim at a different member — and the
            // members that qualify are painted right there. Clearing pending first and refusing after
            // would make every wrong click cost two more clicks to get back to this screen.
            (rewardOf(waiting.paid, waiting.entryId) as? RunReward.TechnicalMachine)?.let { tm ->
                val blocked = run.partySnapshot().getOrNull(slot - 1)?.let { tmBlockReason(tm, it) }
                if (blocked != null) {
                    player.sendSystemMessage(Component.literal("$blocked — pick another Pokémon."))
                    paint()
                    return
                }
            }
            pending = null

            if (interceptTm(player, waiting.paid, waiting.entryId, waiting.label, slot)) return
            if (waiting.paid) finishShop(player, waiting.entryId, slot) else finishOffer(player, waiting.entryId, slot)
        }

        /** The offer/stock entry a pending click refers to, or null if the wave moved on. */
        private fun rewardOf(paid: Boolean, entryId: String): RunReward? =
            if (paid) stock().firstOrNull { it.id == entryId }?.reward
            else offer().firstOrNull { it.id == entryId }?.reward

        /**
         * The TM gate, BEFORE the take or the purchase — both are irreversible and the grant can
         * still refuse, which is how the playtest lost a free pick to a full moveset. True means the
         * click was handled here (cancelled or diverted to the forget screen) and the caller stops.
         *
         * One function for BOTH entry paths, because the first version lived only inside the
         * party-picker resolution — and a party of one skips the picker entirely, which is exactly
         * the party the playtest had. Solo parties come through [beginShop]/[beginOffer] with
         * [memberSlot] = 1; multi parties come through [resolvePending] with the picked slot.
         */
        private fun interceptTm(player: ServerPlayer, paid: Boolean, entryId: String, label: String, memberSlot: Int): Boolean {
            val reward = rewardOf(paid, entryId) as? RunReward.TechnicalMachine ?: return false
            val pokemon = run.partySnapshot().getOrNull(memberSlot - 1) ?: return false
            // Already-knows AND the learnset gate, through the same [TmEligibility] the grant's
            // backstop uses — the second playtest ruling folded into the shape the first one built.
            // A solo party reaches here without ever seeing the picker, so this is where a Magikarp
            // holding the run's only slot finds out the TM is not for it, with nothing yet spent.
            val blocked = tmBlockReason(reward, pokemon)
            if (blocked != null) {
                player.sendSystemMessage(Component.literal("$blocked — nothing was taken."))
                paint()
                return true
            }
            if (!pokemon.moveSet.hasSpace()) {
                pendingMove = PendingMove(paid, entryId, label, memberSlot)
                paint()
                return true
            }
            return false
        }

        /**
         * [TmEligibility.blockReason] over a live party member, or null when the TM may proceed.
         *
         * An **unresolvable move id** is deliberately null — "may proceed" — rather than a refusal:
         * naming a move this server does not have is [RewardGrant]'s `unresolved()` case, and letting
         * the click fall through means the operator-facing message (and the log line) comes from the
         * one place that owns it, instead of this menu inventing a second wording.
         */
        private fun tmBlockReason(reward: RunReward.TechnicalMachine, pokemon: Pokemon): String? {
            val template = runCatching { com.cobblemon.mod.common.api.moves.Moves.getByName(reward.move) }.getOrNull()
                ?: return null
            return runCatching { TmEligibility.blockReason(template, pokemon) }.getOrNull()
        }

        private fun resolveMoveChoice(player: ServerPlayer, slotId: Int) {
            val waiting = pendingMove ?: return
            val index = MOVE_SLOTS.indexOf(slotId)
            if (index < 0) {
                pendingMove = null
                paint()
                return
            }
            pendingMove = null
            if (waiting.paid) {
                finishShop(player, waiting.entryId, waiting.memberSlot, forgetMoveSlot = index)
            } else {
                finishOffer(player, waiting.entryId, waiting.memberSlot, forgetMoveSlot = index)
            }
        }

        private fun finishShop(player: ServerPlayer, entryId: String, slot: Int?, forgetMoveSlot: Int? = null) {
            val table = ShopTables.default() ?: return
            when (val result = ShopStock.buy(table, run.wave, run.credits, entryId)) {
                is PurchaseResult.Ok -> {
                    // User decision 2026-07-31, second revision: buying a consumable IS using it,
                    // and a player may never hold one. The first version parked the stack in the
                    // inventory as cancel-safety; the ruling is stricter — immediate use only — and
                    // Cobblemon's PartySelectCallbacks makes the stricter version the cleaner one,
                    // because it has an explicit CANCEL callback: nothing is charged and nothing is
                    // minted until a Pokémon is actually chosen. Cancel returns to the shop with the
                    // money unspent; selection charges, applies through Cobblemon's own
                    // applyToPokemon (its healing rules, its refusals), and returns to the shop —
                    // which is also where the still-untaken free pick lives, answering "does it go
                    // back if I haven't picked one of the three yet" with yes, always.
                    val bagReward = result.entry.reward as? RunReward.BagItem
                    val selectingItem = bagReward?.let {
                        BuiltInRegistries.ITEM.getOptional(it.item).orElse(null) as? com.cobblemon.mod.common.api.item.PokemonSelectingItem
                    }
                    if (bagReward != null && selectingItem != null) {
                        player.closeContainer()
                        val entryId = result.entry.id
                        com.cobblemonroguelite.run.RunTicks.schedule(2) {
                            val online = player.server.playerList.getPlayer(player.uuid) ?: return@schedule
                            val sample = com.cobblemonroguelite.run.RunItems.mark(
                                ItemStack(selectingItem as Item, 1), run.seed,
                            )
                            com.cobblemon.mod.common.api.callback.PartySelectCallbacks.createFromPokemon(
                                online,
                                sample.hoverName,
                                run.partySnapshot(),
                                { pokemon -> runCatching { selectingItem.canUseOnPokemon(sample, pokemon) }.getOrDefault(false) },
                                { cancelled -> BetweenWaveMenu.openFor(cancelled) },
                                { pokemon ->
                                    // Charged HERE, at selection — revalidated through the same
                                    // ShopStock.buy every other purchase uses, because the overlay
                                    // outlives this click and single-sourcing affordability is what
                                    // keeps the GUI unable to disagree with the rules.
                                    when (val charge = ShopStock.buy(table, run.wave, run.credits, entryId)) {
                                        is PurchaseResult.Ok -> {
                                            val outcome = runCatching { selectingItem.applyToPokemon(online, sample.copy(), pokemon) }
                                            if (outcome.isSuccess) {
                                                run.credits = charge.remaining
                                                checkpoint(online)
                                                // Credits moved, so the HUD's ₽ is stale until told —
                                                // the bar is synced from its write sites, not a tick.
                                                RunHud.sync(online)
                                                online.sendSystemMessage(
                                                    ShopMessages.bought(entryId, charge.price, run.credits, GrantResult.Ok("used on ${pokemon.species.name}")),
                                                )
                                            } else {
                                                log.warn("roguelite: instant-use apply failed for {}", entryId, outcome.exceptionOrNull())
                                            }
                                        }
                                        is PurchaseResult.NotEnoughCredits ->
                                            online.sendSystemMessage(ShopMessages.tooPoor(charge.have, charge.need))
                                        else -> Unit
                                    }
                                    BetweenWaveMenu.openFor(online)
                                },
                            )
                        }
                        return
                    }
                    val party = run.partySnapshot()
                    val target = RewardTargeting.resolve(result.entry.reward, slot, party.size)
                    if (target is RewardTarget.Unresolved) {
                        player.sendSystemMessage(ShopMessages.needsSlot(target.reason))
                        return
                    }
                    run.credits = result.remaining
                    val granted = RewardGrant.apply(result.entry.reward, target, party, run, player, forgetMoveSlot)
                    checkpoint(player)
                    // Same as the instant-use path above: a credits write is a HUD write.
                    RunHud.sync(player)
                    player.sendSystemMessage(ShopMessages.bought(entryId, result.price, run.credits, granted))
                }

                is PurchaseResult.NotEnoughCredits ->
                    player.sendSystemMessage(ShopMessages.tooPoor(result.have, result.need))

                is PurchaseResult.NoSuchEntry -> player.sendSystemMessage(ShopMessages.noSuchEntry(result.id))
                is PurchaseResult.NotStocked -> player.sendSystemMessage(ShopMessages.notStocked(result.id))
            }
            paint()
        }

        private fun finishOffer(player: ServerPlayer, entryId: String, slot: Int?, forgetMoveSlot: Int? = null) {
            val table = RewardTables.default() ?: return
            val party = run.partySnapshot()
            val result = RewardOffer.take(
                table, run.wave, run.seed, run.rerollsThisWave, entryId,
                party = RewardOffer.partyStateOf(party),
            )
            when (result) {
                is TakeResult.Ok -> {
                    val target = RewardTargeting.resolve(result.entry.reward, slot, party.size)
                    if (target is RewardTarget.Unresolved) {
                        player.sendSystemMessage(ShopMessages.needsSlot(target.reason))
                        return
                    }
                    val granted = RewardGrant.apply(result.entry.reward, target, party, run, player, forgetMoveSlot)
                    run.rewardTakenThisWave = true
                    checkpoint(player)
                    player.sendSystemMessage(ShopMessages.taken(entryId, granted))
                    // #2 from the playtest: the free pick is one-per-wave, so taking it IS the
                    // decision this screen exists for — the next battle follows without a second
                    // click. The paid row stays browsable by buying BEFORE picking, and Continue
                    // covers the pick-nothing case.
                    player.closeContainer()
                    RunCommands.resume(player)
                    return
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
                    // Same as the purchase paths: a credits write is a HUD write.
                    RunHud.sync(player)
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
            // The party state makes the scaled entries live: a full-health party sees no potions in
            // its three. Computed fresh per paint, so healing from the shop row immediately stops
            // the free half offering what the player just bought.
            RewardTables.default()?.let {
                RewardOffer.offerFor(
                    it, run.wave, run.seed, run.rerollsThisWave,
                    party = RewardOffer.partyStateOf(run.partySnapshot()),
                )
            }.orEmpty()

        /** Repaint in place. Cheaper than reopening, and it keeps the window from flickering shut. */
        private fun paint() {
            for (slot in 0 until SLOTS) container.setItem(slot, ItemStack.EMPTY)
            // Painted first so every real button overwrites it, which means a slot that gains a purpose
            // later cannot be silently covered by filler.
            DIVIDER_ROWS.forEach { container.setItem(it, divider()) }

            container.setItem(CREDITS_SLOT, creditsIcon())

            val stocked = stock().take(SHOP_LAST - SHOP_FIRST + 1)
            shopSlotsFor(stocked.size).forEachIndexed { index, slot ->
                container.setItem(slot, shopIcon(stocked[index]))
            }

            val forgetting = pendingMove
            if (forgetting != null) {
                for (slot in 0 until SLOTS) container.setItem(slot, background())
                DIVIDER_ROWS.forEach { container.setItem(it, divider()) }
                val pokemon = run.partySnapshot().getOrNull(forgetting.memberSlot - 1)
                container.setItem(
                    4,
                    label(
                        Items.OAK_SIGN,
                        "§bForget which move for §f${forgetting.label}§b?",
                        listOf("§7${pokemon?.species?.name ?: "?"} knows four moves.", "§8Anywhere else cancels."),
                    ),
                )
                pokemon?.moveSet?.getMoves()?.forEachIndexed { index, move ->
                    MOVE_SLOTS.getOrNull(index)?.let { slot ->
                        container.setItem(
                            slot,
                            label(
                                Items.PAPER,
                                "§f${move.displayName.string}",
                                listOf("§cClick to forget this move", "§7and learn the new one instead."),
                            ),
                        )
                    }
                }
                container.setItem(REROLL_SLOT, label(Items.BARRIER, "§cCancel", listOf("§7Keeps all four moves.")))
                broadcastChanges()
                return
            }

            val waiting = pending
            if (waiting != null) {
                // The whole screen becomes the question. The first version added two paper icons to a
                // row of an otherwise fully-painted menu, and the playtest read it as "clicking the
                // item did nothing" — a picker that has to be noticed is a picker that failed. So the
                // shop row and offers are NOT painted while a target is pending: every slot is either
                // a Pokémon, the cancel button, or an arrow pointing at the party row.
                for (slot in 0 until SLOTS) container.setItem(slot, background())
                DIVIDER_ROWS.forEach { container.setItem(it, divider()) }
                container.setItem(
                    4,
                    label(
                        Items.OAK_SIGN,
                        "§bWho gets §f${waiting.label}§b?",
                        listOf("§7Click one of your Pokémon below.", "§8Anywhere else cancels."),
                    ),
                )
                // The picker "filter" for a TM: ineligible members are painted greyed with the reason
                // rather than removed. Removing them would renumber the row (the 1-based slot the
                // player sees IS the party slot everywhere else in this mod) and would hide WHY a
                // Pokémon is not an option — "can't learn it" painted on the sprite answers the
                // question the empty slot would raise. The click side of the same rule lives in
                // resolvePending, which keeps this screen open when a greyed member is clicked anyway.
                val pendingTm = rewardOf(waiting.paid, waiting.entryId) as? RunReward.TechnicalMachine
                run.partySnapshot().forEachIndexed { index, pokemon ->
                    PARTY_SLOTS.getOrNull(index)?.let {
                        val blocked = pendingTm?.let { tm -> tmBlockReason(tm, pokemon) }
                        container.setItem(it, partyIcon(pokemon, index + 1, waiting, blocked))
                    }
                }
                container.setItem(REROLL_SLOT, label(Items.BARRIER, "§cCancel", listOf("§7Click anywhere else too.")))
                broadcastChanges()
                return
            } else {
                offer().forEachIndexed { index, entry ->
                    OFFER_SLOTS.getOrNull(index)?.let { container.setItem(it, offerIcon(entry)) }
                }
                container.setItem(REROLL_SLOT, rerollIcon())
                container.setItem(CONTINUE_SLOT, continueIcon())
            }
            // Last: anything still empty becomes background. A chest slot with nothing in it reads as
            // a slot you could put something into — which on a menu where every click is a button is
            // exactly the wrong invitation.
            for (slot in 0 until SLOTS) {
                if (container.getItem(slot).isEmpty) container.setItem(slot, background())
            }
            broadcastChanges()
        }

        /** The seam between the paid half and the free one. */
        private fun divider() = blank(Items.CYAN_STAINED_GLASS_PANE)

        /** Everything else that would otherwise be a hole. */
        private fun background() = blank(Items.GRAY_STAINED_GLASS_PANE)

        /**
         * A pane with no name and no lore.
         *
         * The name is a single space rather than empty: an empty custom name makes the client fall back
         * to the item's own name, so the screen would be captioned "Gray Stained Glass Pane" nine times
         * over on hover.
         */
        private fun blank(item: Item): ItemStack = label(item, " ", emptyList())

        private fun creditsIcon() = label(
            Items.GOLD_NUGGET,
            "§e${RunCurrency.format(run.credits)}",
            listOf("§7Wave §f${run.wave}", "§8Earned by clearing waves."),
        )

        private fun shopIcon(entry: ShopEntry): ItemStack {
            val price = entry.priceAt(run.wave)
            val affordable = price <= run.credits
            val colour = if (affordable) "§f" else "§8"
            val lore = mutableListOf("§7${RunCurrency.format(price)}", describe(entry.reward))
            lore += if (affordable) "§aClick to buy" else "§cNot enough ${RunCurrency.SYMBOL}"
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

        private fun partyIcon(pokemon: Pokemon, slot: Int, waiting: Pending, blocked: String? = null): ItemStack {
            // The real sprite, the way the starter draft and cobblemon-ranked's menus do it — a row of
            // identical paper is what made the picker invisible in the first playtest. A blocked
            // member (a TM its species cannot learn) KEEPS the sprite: swapping it for a barrier
            // would make the row unreadable as a party, and the grey name plus the red reason already
            // say everything the barrier would.
            val icon = runCatching { com.cobblemon.mod.common.item.PokemonItem.from(pokemon) }
                .getOrNull()?.takeIf { !it.isEmpty } ?: ItemStack(Items.PAPER)
            val name = runCatching { pokemon.species.name }.getOrDefault("?")
            val level = "§7Level ${runCatching { pokemon.level }.getOrDefault(0)}"
            return if (blocked != null) {
                label(icon, "§8$slot. $name", listOf(level, "§c$blocked"))
            } else {
                label(icon, "§f$slot. $name", listOf(level, "§aClick to apply §f${waiting.label}"))
            }
        }

        /**
         * A vanilla icon per reward kind.
         *
         * Vanilla on purpose: a `cobblemon:` or third-party id can be absent on a given server, and an
         * icon that resolves to air would leave an invisible, unclickable-looking button. A held item is
         * the one case where the real item is tried first, because seeing the actual Leftovers is worth
         * more than consistency — with a paper fallback when it is not installed.
         */
        private fun iconFor(reward: RunReward): Item = when (reward) {
            // The vitamin that actually raises that stat, so the icon IS the thing rather than a
            // generic potion six times over.
            is RunReward.Evs -> cobblemon(
                when (reward.stat) {
                    Stats.HP -> "hp_up"
                    Stats.ATTACK -> "protein"
                    Stats.DEFENCE -> "iron"
                    Stats.SPECIAL_ATTACK -> "calcium"
                    Stats.SPECIAL_DEFENCE -> "zinc"
                    Stats.SPEED -> "carbos"
                    else -> "hp_up"
                },
                Items.POTION,
            )

            is RunReward.Levels -> cobblemon("rare_candy", Items.EXPERIENCE_BOTTLE)
            // The mint for that exact nature — `adamant_mint`, `jolly_mint` and so on — falling back to
            // the generic sugar only if a nature has no mint on this server.
            is RunReward.Mint -> cobblemon("${reward.nature.path}_mint", Items.SUGAR)
            is RunReward.AbilityPatch -> cobblemon("ability_patch", Items.NETHER_STAR)
            is RunReward.TechnicalMachine -> tmIcon(reward.move)
            // The money counter's own icon (creditsIcon), so "this pays ₽" reads at a glance.
            is RunReward.Credits -> Items.GOLD_NUGGET
            // The EXP Share is a real Cobblemon item, so its icon can be the thing itself; the charms
            // have no item anywhere (they are PokéRogue UI art), so the closest honest stand-ins are
            // the EXP candies, sized with the boost.
            is RunReward.Passive -> when (reward.passive) {
                RunPassive.EXP_SHARE -> cobblemon("exp_share", Items.AMETHYST_SHARD)
                RunPassive.EXP_CHARM -> cobblemon("exp_candy_m", Items.EXPERIENCE_BOTTLE)
                RunPassive.SUPER_EXP_CHARM -> cobblemon("exp_candy_xl", Items.EXPERIENCE_BOTTLE)
            }
            // These two already name a real item, so they show it. Unchanged.
            is RunReward.BagItem -> BuiltInRegistries.ITEM.getOptional(reward.item).orElse(Items.CHEST)
            is RunReward.HeldItem -> BuiltInRegistries.ITEM.getOptional(reward.item).orElse(Items.PAPER)
            // The same vanilla item the mint uses, so the button shows what will actually land on
            // the Pokémon. The id is vanilla and cannot be absent; the fallback is ceremony.
            is RunReward.ModifierItem -> BuiltInRegistries.ITEM.getOptional(reward.modifier.baseItem).orElse(Items.PAPER)
        }

        /**
         * The icon for a TM reward: the ACTUAL TM disc for that move, when this server has one.
         *
         * Cobblemon 1.7.3 ships no TM items at all — the old `cobblemon:tm_case` id here resolved to
         * nothing on every server and always degraded to the enchanted-book fallback. The TM items
         * that do exist come from SimpleTMs (`simpletms:tm_<move id>`, one per move, type-coloured),
         * which this server runs but this mod deliberately does not depend on (§1.2) — so the id is
         * assembled at runtime and looked up the same optional way every `cobblemon:` icon is. The
         * `tr_` twin is tried second because SimpleTMs registers both 1:1 and a server could
         * conceivably disable one side; the enchanted book stays as the no-SimpleTMs fallback, which
         * is exactly what a standalone install painted before.
         */
        private fun tmIcon(move: String): Item {
            val id = tmItemId(move)
            return BuiltInRegistries.ITEM
                .getOptional(ResourceLocation.fromNamespaceAndPath("simpletms", id))
                .or {
                    BuiltInRegistries.ITEM
                        .getOptional(ResourceLocation.fromNamespaceAndPath("simpletms", "tr_" + id.removePrefix("tm_")))
                }
                .orElseGet { cobblemon("tm_case", Items.ENCHANTED_BOOK) }
        }

        /**
         * A Cobblemon item by path, or [fallback] when this server does not have it.
         *
         * The fallback is not defensive noise: these are ids resolved at runtime against whatever
         * Cobblemon version is installed, and an id that has been renamed would otherwise resolve to
         * air — an invisible, unclickable-looking button. Falling back to the vanilla stand-in the
         * screen used before means a renamed item degrades to the old icon rather than to nothing.
         */
        private fun cobblemon(path: String, fallback: Item): Item =
            BuiltInRegistries.ITEM
                .getOptional(ResourceLocation.fromNamespaceAndPath("cobblemon", path))
                .orElse(fallback)

        private fun describe(reward: RunReward): String = when (reward) {
            is RunReward.Evs -> "§7${signed(reward.amount)} ${reward.stat.identifier.path} EVs"
            is RunReward.Levels -> "§7${signed(reward.amount)} level(s)"
            is RunReward.Mint -> "§7Nature -> ${reward.nature.path}"
            is RunReward.AbilityPatch -> "§7Ability -> ${reward.ability ?: "hidden ability"}"
            is RunReward.BagItem -> "§7${reward.count}x ${reward.item.path} (bag)"
            is RunReward.HeldItem -> "§7Held item -> ${reward.item.path}"
            // The blurb states the effect; the parenthetical states §2.34's ladder, because "I
            // already have one of these" is exactly the moment the pick is at its best.
            is RunReward.ModifierItem ->
                "§7${reward.modifier.displayName}: ${reward.modifier.blurb} (upgrades in place if held)"
            is RunReward.TechnicalMachine -> "§7Teaches ${reward.move}"
            // The concrete number at THIS wave, not the multiplier: "×2.5" means nothing on a screen
            // where every price is already in ₽. Resolved through the same curve the grant pays from.
            is RunReward.Credits ->
                "§7+${RunCurrency.format(ShopSettings.credits.curve.amountAt(run.wave, reward.multiplier))} on the spot"
            is RunReward.Passive -> describePassive(reward.passive)
        }

        /**
         * Built from the passive's own numbers rather than hand-written per kind, so a tuning change
         * in [RunPassive] cannot leave this lore describing the old effect.
         */
        private fun describePassive(passive: RunPassive): String {
            val effect = when {
                passive.expBoostPctPerStack > 0 -> "+${passive.expBoostPctPerStack}% battle EXP per rank"
                passive.sharePctPerStack > 0 -> "party shares ${passive.sharePctPerStack}% of EXP per rank"
                else -> "run passive"
            }
            return "§7${passive.displayName}: $effect (whole team, all run)"
        }

        private fun signed(amount: Int) = if (amount >= 0) "+$amount" else "$amount"

        private fun label(icon: ItemStack, name: String, lore: List<String>): ItemStack {
            val stack = if (icon.isEmpty) ItemStack(Items.PAPER) else icon
            stack.set(DataComponents.CUSTOM_NAME, line(name))
            stack.set(DataComponents.LORE, ItemLore(lore.map { line(it) as Component }))
            return stack
        }

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

/**
 * The SimpleTMs item path for [move]: `tm_` plus the move's Showdown-style id.
 *
 * SimpleTMs keys its items by the lowercase-alphanumeric move id (`tm_uturn`, `tm_willowisp` — see
 * `ops/gen-tm-items.py`, which reads that list straight out of the SimpleTMs jar), while a reward
 * table's `move` field is hand-typed and may carry hyphens or capitals. Normalising here means a
 * table that says "U-turn" still finds the disc. Top-level and internal rather than private to the
 * menu, because it is the one piece of the icon path that is pure — the tests pin the normalisation
 * so a renamed move id fails a test instead of silently painting the fallback book.
 */
internal fun tmItemId(move: String): String =
    "tm_" + move.lowercase().filter { it in 'a'..'z' || it in '0'..'9' }
