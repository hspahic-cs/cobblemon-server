package com.cobblemonroguelite.shop

import com.cobblemonroguelite.data.reward.RewardTable
import com.cobblemonroguelite.data.reward.RewardTables
import com.cobblemonroguelite.data.shop.ShopTable
import com.cobblemonroguelite.data.shop.ShopTables
import com.cobblemonroguelite.run.RunState
import com.cobblemonroguelite.run.RunStore
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.server.level.ServerPlayer
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("cobblemon_roguelite/shop")

/**
 * `/roguelite shop` and `/roguelite reward` — the between-wave step, made reachable.
 *
 * Until this existed, credits accumulated with nothing to spend them on and the whole decision layer
 * was complete, tested and unreachable — which from a player's side is indistinguishable from the
 * feature not existing. Exactly the gap [com.cobblemonroguelite.progression.CandyCommands] was written
 * to close for candy, and this file follows it deliberately, including registering its own `roguelite`
 * literal (Brigadier merges children rather than replacing, so the trees combine).
 *
 * ### Two commands because there are two halves
 *
 * `/roguelite reward` is the **free** pick-one-of-three, and takes no price anywhere in its tree.
 * `/roguelite shop` is the **paid** consumable row. Keeping them as separate verbs is the same decision
 * [RewardOffer] documents: a single `/roguelite buy` covering both would put the free options and the
 * priced ones in one list, and the distinction between them is the mechanic.
 *
 * ### Why every handler re-reads the run
 *
 * A command can arrive at any time — mid-battle, after a run ended under the player, before a starter is
 * chosen. So each handler starts from [RunStore] rather than trusting a cached view, and refuses on
 * anything unexpected. The between-wave step has one further gate of its own: it is only open when no
 * battle is in progress, because a shop that could be used mid-fight would let a player buy their way
 * out of a losing turn.
 */
object ShopCommands {

    private const val ENTRY = "entry"
    private const val SLOT = "slot"

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(Commands.literal("roguelite").then(shop()))
        dispatcher.register(Commands.literal("roguelite").then(reward()))
    }

    private fun shop(): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal("shop")
            .requires { it.entity is ServerPlayer }
            // Opens the GUI. The text listing stays reachable as `/roguelite shop list` for players who
            // prefer it, for screen readers, and for debugging a server where the menu misbehaves.
            .executes { ctx -> player(ctx)?.let(::openMenu) ?: 0 }
            .then(Commands.literal("list").executes { ctx -> player(ctx)?.let(::showShop) ?: 0 })
            .then(
                Commands.literal("buy")
                    .then(
                        Commands.argument(ENTRY, StringArgumentType.word())
                            .suggests { ctx, builder ->
                                // Suggested from what is actually stocked at this wave, so a player is
                                // never offered an id the purchase would refuse.
                                val stocked = player(ctx)?.let { p ->
                                    runFor(p)?.let { run -> shopTable()?.let { t -> ShopStock.stockAt(t, run.wave) } }
                                }.orEmpty()
                                SharedSuggestionProvider.suggest(stocked.map { it.id }, builder)
                            }
                            .executes { ctx -> player(ctx)?.let { buy(it, entryArg(ctx), slot = null) } ?: 0 }
                            .then(
                                Commands.argument(SLOT, IntegerArgumentType.integer(1, RunState.MAX_PARTY))
                                    .executes { ctx ->
                                        player(ctx)?.let { buy(it, entryArg(ctx), slotArg(ctx)) } ?: 0
                                    },
                            ),
                    ),
            )

    private fun reward(): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal("reward")
            .requires { it.entity is ServerPlayer }
            .executes { ctx -> player(ctx)?.let(::showReward) ?: 0 }
            .then(
                Commands.literal("take")
                    .then(
                        Commands.argument(ENTRY, StringArgumentType.word())
                            .suggests { ctx, builder ->
                                val offered = player(ctx)?.let { p ->
                                    runFor(p)?.let { run ->
                                        rewardTable()?.let { t ->
                                            RewardOffer.offerFor(t, run.wave, run.seed, run.rerollsThisWave)
                                        }
                                    }
                                }.orEmpty()
                                SharedSuggestionProvider.suggest(offered.map { it.id }, builder)
                            }
                            .executes { ctx -> player(ctx)?.let { take(it, entryArg(ctx), slot = null) } ?: 0 }
                            .then(
                                Commands.argument(SLOT, IntegerArgumentType.integer(1, RunState.MAX_PARTY))
                                    .executes { ctx ->
                                        player(ctx)?.let { take(it, entryArg(ctx), slotArg(ctx)) } ?: 0
                                    },
                            ),
                    ),
            )
            .then(Commands.literal("reroll").executes { ctx -> player(ctx)?.let(::reroll) ?: 0 })

    // ------------------------------------------------------------------ reads

    /** The GUI, with the text listing as the fallback when there is no run to paint. */
    private fun openMenu(player: ServerPlayer): Int {
        if (BetweenWaveMenu.openFor(player)) return 1
        return showShop(player)
    }

    private fun showShop(player: ServerPlayer): Int {
        val run = runFor(player) ?: return refuse(player, ShopMessages.noRun())
        val table = shopTable() ?: return refuse(player, ShopMessages.noShopTable())
        val stock = ShopStock.stockAt(table, run.wave)
        ShopMessages.shop(stock, run.wave, run.credits).forEach(player::sendSystemMessage)
        return stock.size
    }

    private fun showReward(player: ServerPlayer): Int {
        val run = runFor(player) ?: return refuse(player, ShopMessages.noRun())
        val table = rewardTable() ?: return refuse(player, ShopMessages.noRewardTable())
        val offer = RewardOffer.offerFor(table, run.wave, run.seed, run.rerollsThisWave)
        val rerollPrice = ShopSettings.shop.rerollPrice(run.rerollsThisWave, run.wave)
        ShopMessages.reward(offer, run.credits, run.rewardTakenThisWave, rerollPrice)
            .forEach(player::sendSystemMessage)
        return offer.size
    }

    // ------------------------------------------------------------------ writes

    /**
     * Buy one consumable.
     *
     * Charges **before** granting, which is the uncomfortable ordering [RewardGrant.apply] documents: a
     * failed grant costs the credits. Granting first would make a crash between the two a free item, and
     * a grant that cannot succeed is an operator's broken table rather than something to refund around.
     */
    private fun buy(player: ServerPlayer, entryId: String, slot: Int?): Int {
        val run = runFor(player) ?: return refuse(player, ShopMessages.noRun())
        if (run.battle != null) return refuse(player, ShopMessages.midBattle())
        val table = shopTable() ?: return refuse(player, ShopMessages.noShopTable())

        return when (val result = ShopStock.buy(table, run.wave, run.credits, entryId)) {
            is PurchaseResult.NotEnoughCredits -> refuse(player, ShopMessages.tooPoor(result.have, result.need))
            is PurchaseResult.NoSuchEntry -> refuse(player, ShopMessages.noSuchEntry(result.id))
            is PurchaseResult.NotStocked -> refuse(player, ShopMessages.notStocked(result.id))
            is PurchaseResult.Ok -> {
                val party = run.partySnapshot()
                val target = RewardTargeting.resolve(result.entry.reward, slot, party.size)
                if (target is RewardTarget.Unresolved) {
                    // Refused before charging. Unlike a broken table this is the player's own missing
                    // argument, and it is fully recoverable by typing the slot — so it must not cost.
                    return refuse(player, ShopMessages.needsSlot(target.reason))
                }
                run.credits = result.remaining
                val granted = RewardGrant.apply(result.entry.reward, target, party, run.seed, player)
                RunStore.of(player.server).checkpoint(player.server, player.uuid)
                player.sendSystemMessage(ShopMessages.bought(result.entry.id, result.price, run.credits, granted))
                if (granted is GrantResult.Failed) {
                    log.warn(
                        "roguelite: {} paid {} for '{}' and the grant failed: {}",
                        player.uuid, result.price, result.entry.id, granted.reason,
                    )
                }
                1
            }
        }
    }

    /** Take the one free option. */
    private fun take(player: ServerPlayer, entryId: String, slot: Int?): Int {
        val run = runFor(player) ?: return refuse(player, ShopMessages.noRun())
        if (run.battle != null) return refuse(player, ShopMessages.midBattle())
        if (run.rewardTakenThisWave) return refuse(player, ShopMessages.alreadyTaken())
        val table = rewardTable() ?: return refuse(player, ShopMessages.noRewardTable())

        return when (val result = RewardOffer.take(table, run.wave, run.seed, run.rerollsThisWave, entryId)) {
            is TakeResult.NoSuchEntry -> refuse(player, ShopMessages.noSuchEntry(result.id))
            is TakeResult.NotOffered -> refuse(player, ShopMessages.notOffered(result.id))
            is TakeResult.Ok -> {
                val party = run.partySnapshot()
                val target = RewardTargeting.resolve(result.entry.reward, slot, party.size)
                if (target is RewardTarget.Unresolved) {
                    return refuse(player, ShopMessages.needsSlot(target.reason))
                }
                // The flag is set even when the grant fails, and that asymmetry with `buy` is on purpose:
                // there is nothing to refund, and a failed grant that left the option takeable would let
                // a player retry the same broken entry forever instead of taking one of the other two.
                // A Failed result names the entry, so an operator can see which one to fix.
                val granted = RewardGrant.apply(result.entry.reward, target, party, run.seed, player)
                run.rewardTakenThisWave = true
                RunStore.of(player.server).checkpoint(player.server, player.uuid)
                player.sendSystemMessage(ShopMessages.taken(result.entry.id, granted))
                1
            }
        }
    }

    private fun reroll(player: ServerPlayer): Int {
        val run = runFor(player) ?: return refuse(player, ShopMessages.noRun())
        if (run.battle != null) return refuse(player, ShopMessages.midBattle())
        if (run.rewardTakenThisWave) return refuse(player, ShopMessages.alreadyTaken())

        return when (val result = RewardOffer.reroll(run.credits, run.rerollsThisWave, run.wave)) {
            RerollResult.Disabled -> refuse(player, ShopMessages.rerollDisabled())
            is RerollResult.NotEnoughCredits -> refuse(player, ShopMessages.tooPoor(result.have, result.need))
            is RerollResult.Ok -> {
                run.credits = result.remaining
                run.rerollsThisWave++
                RunStore.of(player.server).checkpoint(player.server, player.uuid)
                showReward(player)
                1
            }
        }
    }

    // ------------------------------------------------------------------ plumbing

    private fun player(ctx: CommandContext<CommandSourceStack>): ServerPlayer? = ctx.source.player

    private fun entryArg(ctx: CommandContext<CommandSourceStack>): String = StringArgumentType.getString(ctx, ENTRY)

    private fun slotArg(ctx: CommandContext<CommandSourceStack>): Int = IntegerArgumentType.getInteger(ctx, SLOT)

    private fun runFor(player: ServerPlayer): RunState? = RunStore.of(player.server).get(player.uuid)

    /**
     * The tables the step reads.
     *
     * **Not pinned to the run**, unlike the payout and trainer tables. Those are pinned because a run's
     * *outcome* must not change under it; the between-wave step is read fresh every wave, so pinning it
     * would only mean a price fix never reaching a run in progress. That is the trade the shop table's
     * registration in `RogueliteData` documents, and it is why editing a shop table reaches live runs.
     *
     * A **named default id** rather than the first table loaded, matching `PayoutTables`: with several
     * tables installed, first-loaded depends on pack order, so the shop's contents would change for
     * reasons an operator cannot see. Until the file exists, that half of the step is closed and the
     * command says so.
     */
    private fun shopTable(): ShopTable? = ShopTables.default()

    private fun rewardTable(): RewardTable? = RewardTables.default()

    private fun refuse(player: ServerPlayer, message: net.minecraft.network.chat.Component): Int {
        player.sendSystemMessage(message)
        return 0
    }
}
