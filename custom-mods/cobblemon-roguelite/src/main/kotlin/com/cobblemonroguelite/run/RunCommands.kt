package com.cobblemonroguelite.run

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.ResourceLocationArgument
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer

/**
 * `/roguelite` — the player's whole interface to the mode until there is a GUI.
 *
 * ### Confirmation is an explicit sub-literal, not a stored token
 *
 * `/roguelite abandon` prints a warning and does nothing; `/roguelite abandon confirm` does it.
 * The obvious alternative — remember "this player is about to abandon" for thirty seconds and let a
 * second bare `/roguelite abandon` go through — is state that can be stale, and the failure mode is
 * a player who typed the command twice for an unrelated reason destroying a run they meant to keep.
 * A literal cannot mis-fire: the only way to reach the destructive branch is to have typed the word
 * `confirm`. The same shape guards `start`, where the first call is also what prints the price.
 *
 * ### Every command is player-only
 *
 * A run belongs to a player and everything here reads or writes theirs. Console and command blocks
 * are refused by [Commands.requires] rather than by a null check inside each handler, so an operator
 * gets "unknown command" instead of a silent no-op.
 */
object RunCommands {

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("roguelite")
                .requires { it.entity is ServerPlayer }
                .then(
                    Commands.literal("start")
                        .executes { player(it)?.let(::quoteStart) ?: 0 }
                        .then(Commands.literal("confirm").executes { player(it)?.let(::start) ?: 0 }),
                )
                .then(
                    Commands.literal("starter")
                        .then(
                            Commands.argument("species", ResourceLocationArgument.id())
                                // Suggested from the player's own offer rather than from every
                                // species on the server: the offer is three ids and typing one of
                                // them exactly is otherwise the hardest part of starting a run.
                                .suggests { ctx, builder ->
                                    val offer = (status(ctx) as? RunStatus.AwaitingStarter)?.offer
                                    SharedSuggestionProvider.suggestResource(offer?.species.orEmpty(), builder)
                                }
                                .executes { ctx ->
                                    player(ctx)?.let { chooseStarter(it, ResourceLocationArgument.getId(ctx, "species")) } ?: 0
                                },
                        ),
                )
                .then(Commands.literal("status").executes { player(it)?.let(::status) ?: 0 })
                .then(Commands.literal("resume").executes { player(it)?.let(::resume) ?: 0 })
                .then(
                    Commands.literal("abandon")
                        .executes { player(it)?.let(::warnAbandon) ?: 0 }
                        .then(Commands.literal("confirm").executes { player(it)?.let(::abandon) ?: 0 }),
                ),
        )
    }

    private fun player(ctx: CommandContext<CommandSourceStack>): ServerPlayer? = ctx.source.player

    private fun status(ctx: CommandContext<CommandSourceStack>): RunStatus? =
        ctx.source.player?.let { RunController.status(it.server, it) }

    private fun quoteStart(player: ServerPlayer): Int {
        val current = RunController.status(player.server, player)
        if (current !is RunStatus.None) {
            // A pending start is answered with the offer rather than with "already running": the
            // player is one command away from being in a run and telling them they are busy would
            // hide that.
            player.sendSystemMessage(
                if (current is RunStatus.AwaitingStarter) RunMessages.offer(current.offer) else RunMessages.alreadyRunning(),
            )
            return 0
        }
        when (val quote = RunController.quoteStart(player.server, player)) {
            is RunStartQuote.Priced -> player.sendSystemMessage(RunMessages.confirmStart(quote.detail))
            is RunStartQuote.Refused -> player.sendSystemMessage(refusal(quote.refusal))
        }
        return 1
    }

    private fun start(player: ServerPlayer): Int {
        val result = RunController.start(player.server, player)
        if (result == null) {
            player.sendSystemMessage(RunMessages.alreadyRunning())
            return 0
        }
        return when (result) {
            is RunStartResult.Refused -> {
                player.sendSystemMessage(refusal(result.refusal))
                0
            }

            is RunStartResult.OfferReady -> {
                player.sendSystemMessage(RunMessages.offer(result.offer))
                1
            }
        }
    }

    private fun chooseStarter(player: ServerPlayer, species: ResourceLocation): Int =
        when (RunController.chooseStarter(player.server, player, species)) {
            is StarterChoiceResult.Started -> {
                player.sendSystemMessage(RunMessages.started(species.toString()))
                1
            }

            StarterChoiceResult.NoPendingStart -> {
                player.sendSystemMessage(RunMessages.noRun())
                0
            }

            StarterChoiceResult.NotOffered -> {
                player.sendSystemMessage(RunMessages.notOffered())
                0
            }

            StarterChoiceResult.SpeciesUnavailable -> {
                player.sendSystemMessage(RunMessages.speciesUnavailable())
                0
            }
        }

    private fun status(player: ServerPlayer): Int {
        when (val status = RunController.status(player.server, player)) {
            RunStatus.None -> player.sendSystemMessage(RunMessages.noRun())
            is RunStatus.AwaitingStarter -> player.sendSystemMessage(RunMessages.offer(status.offer))
            is RunStatus.InProgress -> player.sendSystemMessage(
                RunMessages.atWave(status.run.wave, status.run.partySnapshot().size, status.depthCap),
            )
        }
        return 1
    }

    private fun resume(player: ServerPlayer): Int {
        when (val result = RunController.resume(player.server, player)) {
            ResumeResult.NoRun -> player.sendSystemMessage(RunMessages.noRun())
            is ResumeResult.AwaitingStarter -> player.sendSystemMessage(RunMessages.offer(result.offer))
            is ResumeResult.WaveStarted -> Unit // the battle itself is the feedback
            is ResumeResult.WaveUnavailable -> player.sendSystemMessage(RunMessages.waveUnavailable(result.plan.wave))
            is ResumeResult.Ended -> player.sendSystemMessage(RunMessages.ended(result.report))
        }
        return 1
    }

    private fun warnAbandon(player: ServerPlayer): Int {
        when (val status = RunController.status(player.server, player)) {
            RunStatus.None -> player.sendSystemMessage(RunMessages.noRun())
            is RunStatus.AwaitingStarter -> player.sendSystemMessage(RunMessages.confirmAbandon(1, 0))
            is RunStatus.InProgress ->
                player.sendSystemMessage(RunMessages.confirmAbandon(status.run.wave, status.run.partySnapshot().size))
        }
        return 1
    }

    private fun abandon(player: ServerPlayer): Int {
        val report = RunController.abandon(player.server, player)
        player.sendSystemMessage(report?.let(RunMessages::ended) ?: RunMessages.noRun())
        return if (report == null) 0 else 1
    }

    private fun refusal(refusal: RunStartRefusal) = when (refusal) {
        is RunStartRefusal.DepthLocked -> RunMessages.depthLocked(refusal)
        // Passed through verbatim: the provider built it because only the provider can say what the
        // price is in.
        is RunStartRefusal.Charge -> refusal.reason
        RunStartRefusal.NoStartersAvailable -> RunMessages.noStarters()
    }
}
