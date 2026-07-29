package com.cobblemonroguelite.progression

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemonroguelite.starter.DefaultStarterCosts
import com.cobblemonroguelite.starter.HiddenAbilityGrant
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.ResourceLocationArgument
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("cobblemon_roguelite/progression")

/**
 * `/roguelite candy` — the only way to spend what §2.15 lets a player earn.
 *
 * Until this existed candy accumulated and could not be spent: [ProgressionStore.buy] was complete
 * and tested with nothing calling it, which from a player's side is indistinguishable from the shop
 * not existing.
 *
 * ### Why this registers its own tree instead of being added to `RunCommands`
 *
 * Brigadier merges: `CommandNode.addChild` folds a second `roguelite` literal's children into the
 * one already registered rather than replacing it, so registering
 * `literal("roguelite").then(candy())` here puts `/roguelite candy` on the same tree the run commands
 * built, with no edit to that file. The player sees one command; the two features stay in separate
 * files.
 *
 * The merge keeps the **first** registered node's requirement, so the `roguelite` root's permissions
 * are whatever `RunCommands` set (nothing) either way — which is why the player-only requirement here
 * sits on `candy`, exactly as `RunCommands` puts its own on each branch rather than on the root.
 *
 * ### Confirmation is a trailing literal, and this file inherits the rule rather than restating it
 *
 * `/roguelite candy <species> buy hiddenability` names the price and does nothing; adding `confirm` buys.
 * A purchase is irreversible — candy is earned one per catch and there is no selling it back — so it
 * gets the same guard `abandon` has, and for the same reason `RunCommands` documents: a remembered
 * "they are about to buy something" token can go stale and fire on a command typed for an unrelated
 * reason, and a stale token on an irreversible action is the failure worth designing out. A literal
 * cannot mis-fire.
 *
 * ### Why spending is not gated on being between runs
 *
 * Nothing here reads or writes a run, so a purchase mid-run is harmless: cost reductions are read
 * when a catalogue is priced and the hidden ability when a starter is built, both of which happened
 * before the run began. Gating it would need this file to know about run state, and would leave a player
 * who is mid-run unable to look at their own ledger.
 */
object CandyCommands {

    private const val SPECIES = "species"

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(Commands.literal("roguelite").then(candy()))
    }

    private fun candy(): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal("candy")
            .requires { it.entity is ServerPlayer }
            .executes { ctx -> player(ctx)?.let(::ledger) ?: 0 }
            .then(
                Commands.argument(SPECIES, ResourceLocationArgument.id())
                    // Suggested from the player's own ledger, which is the set of species that have a
                    // number to look at. Deliberately not every species on the server: the argument
                    // still accepts any of them, and a thousand-entry completion list would bury the
                    // handful the player has actually earned candy for.
                    .suggests { ctx, builder ->
                        val earned = ctx.source.player?.let { p ->
                            runCatching { ProgressionStore.of(p.server).of(p.uuid).all().keys }.getOrNull()
                        }.orEmpty()
                        SharedSuggestionProvider.suggestResource(earned, builder)
                    }
                    .executes { ctx -> player(ctx)?.let { show(it, species(ctx)) } ?: 0 }
                    .then(
                        Commands.literal("buy")
                            .then(purchase(CandyPurchase.HIDDEN_ABILITY))
                            .then(purchase(CandyPurchase.COST_REDUCTION))
                            // Present even though it is refused on every server today. A player who
                            // has read that candy buys eggs (§2.15) will type this, and "unknown
                            // command" reads as a broken build where "not available on this server"
                            // reads as the truth — see [CandyLedger.EGGS_GRANTABLE].
                            .then(purchase(CandyPurchase.EGG)),
                    ),
            )

    /**
     * One purchase branch: bare warns, `confirm` acts.
     *
     * The literal comes from [CandyMessages.word] rather than being spelled here, so the command a
     * message tells the player to type is by construction the command this tree parses.
     */
    private fun purchase(purchase: CandyPurchase): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal(CandyMessages.word(purchase))
            .executes { ctx -> player(ctx)?.let { buy(it, species(ctx), purchase, confirmed = false) } ?: 0 }
            .then(
                Commands.literal("confirm")
                    .executes { ctx -> player(ctx)?.let { buy(it, species(ctx), purchase, confirmed = true) } ?: 0 },
            )

    private fun player(ctx: CommandContext<CommandSourceStack>): ServerPlayer? = ctx.source.player

    private fun species(ctx: CommandContext<CommandSourceStack>): ResourceLocation =
        ResourceLocationArgument.getId(ctx, SPECIES)

    /** Every species this player has a balance for. */
    private fun ledger(player: ServerPlayer): Int {
        val rows = runCatching { CandyLedger.summary(ProgressionStore.of(player.server).of(player.uuid).all()) }
            .onFailure { log.warn("roguelite: could not read a candy ledger for {}", player.uuid, it) }
            .getOrDefault(emptyList())
        player.sendSystemMessage(if (rows.isEmpty()) CandyMessages.noCandy() else CandyMessages.ledger(rows))
        return rows.size
    }

    private fun show(player: ServerPlayer, typed: ResourceLocation): Int {
        val view = viewFor(player, typed) ?: return unknown(player, typed)
        CandyMessages.view(view).forEach(player::sendSystemMessage)
        return 1
    }

    private fun buy(
        player: ServerPlayer,
        typed: ResourceLocation,
        purchase: CandyPurchase,
        confirmed: Boolean,
    ): Int {
        val view = viewFor(player, typed) ?: return unknown(player, typed)
        return when (val plan = CandyLedger.plan(view.offer(purchase), confirmed)) {
            is CandyPurchasePlan.Confirm -> {
                player.sendSystemMessage(CandyMessages.confirm(view, plan))
                1
            }

            is CandyPurchasePlan.Refuse -> {
                player.sendSystemMessage(CandyMessages.refusal(view, purchase, plan.refusal))
                0
            }

            is CandyPurchasePlan.Commit -> commit(player, view, purchase)
        }
    }

    /**
     * The one call that spends.
     *
     * Goes through [ProgressionStore.buy] rather than reading, deciding and writing here, because
     * that path does the check and the deduction as one atomic step and flushes the result — see
     * [PlayerProgression.buy] on the double-spend two racing commands would otherwise produce. Its
     * answer, not the quote, is what the player is told: the balance can have moved between the two
     * commands, and the store's refusal is the one that is true.
     */
    private fun commit(player: ServerPlayer, view: CandyLedgerView, purchase: CandyPurchase): Int {
        val result = runCatching {
            ProgressionStore.of(player.server).buy(
                server = player.server,
                player = player.uuid,
                species = view.credited,
                purchase = purchase,
                starterCost = view.starterCost,
            )
        }.onFailure {
            log.error("roguelite: candy purchase failed for {} on {}", player.uuid, view.credited, it)
        }.getOrNull()

        if (result == null) {
            // The store threw, so whether anything was deducted is unknown — which is exactly what the
            // player is told. Claiming either outcome would be a guess, and the wrong guess is one
            // they would act on.
            player.sendSystemMessage(CandyMessages.purchaseFailed(view))
            return 0
        }
        return when (result) {
            is SpendResult.Ok -> {
                // Logged at INFO because it is the only irreversible spend of permanent progression in
                // the mode, and "my candy is gone and I did not buy anything" is a report an operator
                // can only answer from a log line.
                log.info(
                    "roguelite: {} bought {} for {} on {} — {} candy left",
                    player.uuid, purchase, result.spent, view.credited, result.progress.candy,
                )
                player.sendSystemMessage(
                    CandyMessages.bought(view.credited, purchase, result.spent, result.progress.candy),
                )
                1
            }

            else -> {
                player.sendSystemMessage(CandyMessages.refusal(view, purchase, result))
                0
            }
        }
    }

    private fun unknown(player: ServerPlayer, typed: ResourceLocation): Int {
        player.sendSystemMessage(CandyMessages.unknownSpecies(typed))
        return 0
    }

    /**
     * The impure half: a typed id becomes a real species, the species becomes the ledger it credits,
     * and the ledger is priced.
     *
     * Null means no such species — reported as such rather than shown as an empty ledger, because a
     * plausible page of zeroes for a mistyped id reads as candy that has gone missing.
     */
    private fun viewFor(player: ServerPlayer, typed: ResourceLocation): CandyLedgerView? {
        val resolved = CandySpeciesArgument.resolve(typed) { id ->
            runCatching { PokemonSpecies.getByIdentifier(id) }.getOrNull() != null
        } ?: return null
        val species = runCatching { PokemonSpecies.getByIdentifier(resolved) }.getOrNull() ?: return null

        // §2.17's rule, and the reason this file cannot just use what the player typed: candy is
        // keyed on the evolution line's root, so a player asking about Charizard has to be shown —
        // and has to spend — Charmander's ledger. Read through [RunProgression.speciesKey] rather
        // than walking the line here, so the view is keyed by whatever the *catch* path keys by; two
        // copies of that rule would be a shop that spends a ledger the catches never paid into.
        val credited = runCatching { RunProgression.speciesKey.keyFor(species) }
            .onFailure { log.warn("roguelite: could not resolve a candy ledger for '{}'", resolved, it) }
            .getOrDefault(species.resourceIdentifier)

        val progress = ProgressionStore.of(player.server).progressFor(player.uuid, credited)
        return CandyLedger.view(
            requested = typed,
            credited = credited,
            progress = progress,
            starterCost = baseStarterCost(credited),
            // §2.27, and the last impure lookup this view needs: what an unlock on the *credited*
            // species grants, or null if it has none and the purchase must be withdrawn. Read here
            // rather than inside [CandyLedger] for that file's stated reason — nothing in it touches a
            // server, a player or a Pokémon, and Cobblemon's ability pool is all three away.
            hiddenAbility = HiddenAbilityGrant.offeredName(credited),
        )
    }

    /**
     * The credited species' §2.13 base cost, for the prices that are keyed by it
     * ([CandyPrices.hiddenAbilityCandyByCost]).
     *
     * The *credited* species and not the one typed, because that is the species the candy belongs to
     * and the one a player can actually start a run with. Unknown degrades to the flat price rather
     * than refusing — see [SpeciesProgress.UNKNOWN_STARTER_COST]; a server whose cost table has a
     * hole should still be able to spend candy.
     */
    private fun baseStarterCost(species: ResourceLocation): Int =
        runCatching { DefaultStarterCosts.costOf(species) }
            .onFailure { log.warn("roguelite: could not price '{}' for candy — using the flat price", species, it) }
            .getOrNull() ?: SpeciesProgress.UNKNOWN_STARTER_COST
}
