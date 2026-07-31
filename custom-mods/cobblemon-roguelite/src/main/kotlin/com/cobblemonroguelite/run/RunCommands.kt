package com.cobblemonroguelite.run

import com.cobblemonroguelite.starter.StarterDraftMenu
import com.cobblemonroguelite.starter.StarterSelection
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.commands.arguments.ResourceLocationArgument
import net.minecraft.network.chat.Component
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
 * `confirm`. The same shape guards `start`, where the first call is also what prints the price, and
 * `pause` — which is the odd one out in that its confirm branch destroys nothing and changes nothing.
 * It uses the shape anyway so that "the second line is the one that counts" holds everywhere, rather
 * than being a rule with an exception the player has to learn.
 *
 * ### Every *player* command is player-only, and one command deliberately is not
 *
 * A run belongs to a player and almost everything here reads or writes theirs. Console and command
 * blocks are refused by [Commands.requires] rather than by a null check inside each handler, so an
 * operator gets "unknown command" instead of a silent no-op.
 *
 * The requirement sits on each branch rather than on the `roguelite` root, which it used to. §2.25's
 * `override` is an operator command about *another* player, and a dev server's operator is typically
 * typing into a server console with no entity attached — a root-level player check would have made
 * the one command that exists for the console unreachable from it.
 */
object RunCommands {

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("roguelite")
                .then(
                    playerOnly(Commands.literal("start"))
                        .executes { player(it)?.let(::quoteStart) ?: 0 }
                        .then(Commands.literal("confirm").executes { player(it)?.let(::start) ?: 0 }),
                )
                .then(
                    // Bare `starter` reopens the draft; the species arguments stay for scripts, for
                    // tests, and for a player who closed the window and would rather type.
                    playerOnly(Commands.literal("starter"))
                        .executes { player(it)?.let(::openDraft) ?: 0 }
                        .then(starterArgument(1)),
                )
                .then(playerOnly(Commands.literal("status")).executes { player(it)?.let(::status) ?: 0 })
                .then(playerOnly(Commands.literal("resume")).executes { player(it)?.let(::resume) ?: 0 })
                .then(
                    // §2.22. The confirm branch is the same shape as `abandon`'s and does none of the
                    // same work: it acknowledges a price, it does not pay one. See [RunPause].
                    playerOnly(Commands.literal("pause"))
                        .executes { player(it)?.let { p -> pause(p, confirmed = false) } ?: 0 }
                        .then(Commands.literal("confirm").executes { player(it)?.let { p -> pause(p, confirmed = true) } ?: 0 }),
                )
                .then(
                    // §2.13's swap-or-release. Bare `catch` asks, `swap <slot>` and `release` warn,
                    // and only the trailing `confirm` destroys anything — the same three steps as
                    // `abandon`, because it is the same kind of act: a Pokémon stops existing and
                    // there is nowhere to get it back from.
                    playerOnly(Commands.literal("catch"))
                        .executes { player(it)?.let(::catchPrompt) ?: 0 }
                        .then(
                            Commands.literal("swap")
                                .then(
                                    // Bounded at the source, so an out-of-range number is a parse
                                    // error the player sees before any confirmation exists to type.
                                    // [RunState.resolveCatch] still range-checks, because the party
                                    // can be shorter than six.
                                    Commands.argument("slot", IntegerArgumentType.integer(1, RunState.MAX_PARTY))
                                        .executes { ctx ->
                                            player(ctx)?.let { warnSwap(it, IntegerArgumentType.getInteger(ctx, "slot")) } ?: 0
                                        }
                                        .then(
                                            Commands.literal("confirm").executes { ctx ->
                                                player(ctx)?.let {
                                                    resolveCatch(it, CatchDecision.Swap(IntegerArgumentType.getInteger(ctx, "slot")))
                                                } ?: 0
                                            },
                                        ),
                                ),
                        )
                        .then(
                            Commands.literal("release")
                                .executes { player(it)?.let(::warnRelease) ?: 0 }
                                .then(
                                    Commands.literal("confirm")
                                        .executes { player(it)?.let { p -> resolveCatch(p, CatchDecision.Release) } ?: 0 },
                                ),
                        ),
                )
                .then(
                    playerOnly(Commands.literal("abandon"))
                        .executes { player(it)?.let(::warnAbandon) ?: 0 }
                        .then(Commands.literal("confirm").executes { player(it)?.let(::abandon) ?: 0 }),
                )
                // Last, and the only branch above that is not player-only.
                .then(overrideCommand()),
        )
    }

    /** The player-only requirement, per branch. See the class docs for why it is not on the root. */
    private fun playerOnly(node: LiteralArgumentBuilder<CommandSourceStack>) =
        node.requires { it.entity is ServerPlayer }

    /**
     * §2.25: `/roguelite override <players> on|off`, and `/roguelite override list`.
     *
     * ### Why level 2 and not level 4
     *
     * Level 2 is the vanilla bar for commands that change the world (`/fillbiome` itself is level 2),
     * and it is the level a dev server's testers actually have. Level 4 would mean only the console
     * and the owner, which on the box this exists for is the same as nobody.
     *
     * ### Why there is no `off` for everybody
     *
     * A blanket clear is the command an operator reaches for when they have lost track, and losing
     * track is precisely when it would silently end somebody's in-flight testing. `list` answers the
     * question that leads to it, and the restart clears the rest.
     */
    private fun overrideCommand(): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal("override")
            .requires { it.hasPermission(2) }
            .then(Commands.literal("list").executes { listOverrides(it.source) })
            .then(
                Commands.argument("players", EntityArgument.players())
                    .then(Commands.literal("on").executes { setOverride(it, on = true) })
                    .then(Commands.literal("off").executes { setOverride(it, on = false) }),
            )

    /**
     * Feedback goes through `sendSuccess(.., true)`, which broadcasts to every other operator.
     *
     * That is §2.25's "never quietly": an override granted in a private message is one the next
     * operator to look at a leaderboard has no reason to suspect. The log line beside it
     * ([RunDepthOverrides.set]) is the durable half; this is the half somebody sees at the time.
     */
    private fun setOverride(ctx: CommandContext<CommandSourceStack>, on: Boolean): Int {
        val targets = EntityArgument.getPlayers(ctx, "players")
        val by = ctx.source.textName
        var changed = 0
        for (target in targets) {
            if (RunDepthOverrides.set(target.uuid, target.gameProfile.name, on, by)) changed++
            // Told to the player as well, and not only to the operator: it changes what their runs
            // mean, and a run marked as unearned without its owner ever being told is the sort of
            // thing they find out from a leaderboard.
            target.sendSystemMessage(
                if (on) {
                    Component.literal(
                        "An operator has lifted the badge depth gate for you. Runs you start from now " +
                            "on are recorded as started under an override.",
                    ).withStyle(ChatFormatting.YELLOW)
                } else {
                    Component.literal("The badge depth gate applies to you again.").withStyle(ChatFormatting.YELLOW)
                },
            )
        }
        val verb = if (on) "lifted for" else "restored for"
        ctx.source.sendSuccess(
            {
                Component.literal(
                    "Roguelite depth gate $verb ${targets.size} player(s) ($changed changed). " +
                        "Overrides are not saved and clear on restart.",
                ).withStyle(ChatFormatting.YELLOW)
            },
            true,
        )
        return targets.size
    }

    /** Who is overridden right now. By UUID, because the set is keyed that way and may hold offline players. */
    private fun listOverrides(source: CommandSourceStack): Int {
        val active = RunDepthOverrides.active()
        val text = if (active.isEmpty()) {
            "No roguelite depth overrides are in force."
        } else {
            "Roguelite depth overrides in force for ${active.size} player(s): " +
                active.joinToString(", ") { uuid ->
                    source.server.playerList.getPlayer(uuid)?.gameProfile?.name ?: uuid.toString()
                }
        }
        source.sendSuccess({ Component.literal(text) }, false)
        return active.size
    }

    /**
     * `/roguelite starter <a> [b] [c] ...`, built as nested optional arguments rather than one
     * greedy string.
     *
     * A greedy string would be a third of the code and would give suggestions on the first species
     * only. Under §2.13 a team is two or three ids typed exactly, in a chat box, with no GUI — so
     * per-slot completion is not a nicety, it is the difference between the feature being usable
     * from a command and not. Each level also `executes`, which is what makes every arity legal
     * without declaring six overloads.
     *
     * Bounded at [StarterSelection.MAX_STARTERS], the DRAFT cap, not [StarterSelection.MAX_TEAM].
     * A fourth argument is refused by the parser, before there is anything to validate. Using the
     * party ceiling here would offer completions for slots the validator then rejects, which reads as
     * the command being broken rather than the team being too big.
     */
    private fun starterArgument(depth: Int): RequiredArgumentBuilder<CommandSourceStack, ResourceLocation> {
        val argument = Commands.argument(speciesArg(depth), ResourceLocationArgument.id())
            // Suggested from the player's own catalogue rather than from every species on the
            // server. Deliberately does not hide what they have already typed: filtering by the
            // earlier arguments would need this to re-parse a half-typed command, and a duplicate is
            // caught with a clear message a moment later.
            .suggests { ctx, builder ->
                val catalogue = (status(ctx) as? RunStatus.AwaitingStarter)?.catalogue
                SharedSuggestionProvider.suggestResource(catalogue?.options.orEmpty().map { it.species }, builder)
            }
            .executes { ctx -> player(ctx)?.let { chooseStarters(it, speciesArgs(ctx)) } ?: 0 }
        if (depth < StarterSelection.MAX_STARTERS) argument.then(starterArgument(depth + 1))
        return argument
    }

    private fun speciesArg(depth: Int) = "species$depth"

    /**
     * The species actually typed, in order.
     *
     * Brigadier has no "was this argument supplied" query, so absence is read off the exception its
     * getter throws. Stopping at the first gap rather than collecting what is present keeps the
     * order the player typed, which becomes their party order and therefore their lead.
     */
    private fun speciesArgs(ctx: CommandContext<CommandSourceStack>): List<ResourceLocation> {
        val species = mutableListOf<ResourceLocation>()
        for (depth in 1..StarterSelection.MAX_STARTERS) {
            val id = runCatching { ResourceLocationArgument.getId(ctx, speciesArg(depth)) }.getOrNull() ?: break
            species += id
        }
        return species
    }

    private fun player(ctx: CommandContext<CommandSourceStack>): ServerPlayer? = ctx.source.player

    private fun status(ctx: CommandContext<CommandSourceStack>): RunStatus? =
        ctx.source.player?.let { RunController.status(it.server, it) }

    private fun quoteStart(player: ServerPlayer): Int {
        val current = RunController.status(player.server, player)
        if (current !is RunStatus.None) {
            // A pending start is answered with the catalogue rather than with "already running": the
            // player is one command away from being in a run and telling them they are busy would
            // hide that.
            player.sendSystemMessage(
                if (current is RunStatus.AwaitingStarter) RunMessages.catalogue(current.catalogue) else RunMessages.alreadyRunning(),
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

            is RunStartResult.CatalogueReady -> {
                // Chat first, then the screen. The message is not redundant: it survives the window
                // being closed, and it is the only thing a player has if the draft cannot open (an
                // empty catalogue, which is an operator fault the GUI has no good way to show).
                player.sendSystemMessage(RunMessages.catalogue(result.catalogue))
                // Wrapped for the same reason RunController wraps the between-wave screen: a menu that
                // throws must not take the started run down with it, since the run is already paid for
                // and `/roguelite starter` is still a way through.
                runCatching { StarterDraftMenu.openFor(player) }
                1
            }
        }
    }

    /**
     * `/roguelite starter` with nothing after it.
     *
     * Falls back to the chat catalogue rather than reporting "no screen": the reasons the draft will
     * not open are "you have no pending start" and "your catalogue is empty", and both of those are
     * things [RunMessages] already says properly.
     */
    private fun openDraft(player: ServerPlayer): Int {
        if (runCatching { StarterDraftMenu.openFor(player) }.getOrDefault(false)) return 1
        return status(player)
    }

    /**
     * Internal rather than private because [com.cobblemonroguelite.starter.StarterDraftMenu] confirms
     * through it. The mapping from a [StarterChoiceResult] to the line a player reads exists once, so
     * the GUI and the command cannot come to say different things about the same refusal.
     *
     * Returns 1 on a started run, which is what the menu reads to decide whether to close itself.
     */
    internal fun chooseStarters(player: ServerPlayer, species: List<ResourceLocation>): Int =
        when (val result = RunController.chooseStarters(player.server, player, species)) {
            is StarterChoiceResult.Started -> {
                player.sendSystemMessage(
                    RunMessages.started(species.map { it.toString() }, result.spent, result.remaining),
                )
                1
            }

            StarterChoiceResult.NoPendingStart -> {
                player.sendSystemMessage(RunMessages.noRun())
                0
            }

            is StarterChoiceResult.Rejected -> {
                player.sendSystemMessage(RunMessages.starterRejected(result.reason))
                0
            }

            is StarterChoiceResult.SpeciesUnavailable -> {
                player.sendSystemMessage(RunMessages.speciesUnavailable(result.species))
                0
            }
        }

    private fun status(player: ServerPlayer): Int {
        when (val status = RunController.status(player.server, player)) {
            RunStatus.None -> player.sendSystemMessage(RunMessages.noRun())
            is RunStatus.AwaitingStarter -> player.sendSystemMessage(RunMessages.catalogue(status.catalogue))
            is RunStatus.InProgress -> {
                player.sendSystemMessage(
                    RunMessages.atWave(status.run.wave, status.run.partySnapshot().size, status.depthCap),
                )
                // Appended rather than folded in: "wave 13, 6 Pokémon alive" is true and complete as
                // a status line, and it is also the line a player would read as "nothing is stopping
                // me" — which, with a decision outstanding, is exactly wrong.
                status.run.pendingCatch?.let { player.sendSystemMessage(RunMessages.catchPending(it)) }
                // §2.25: the one place a player can find out their run is not comparable with anyone
                // else's. Appended for the same reason — the depth on the line above is the number
                // this qualifies.
                if (status.run.startedUnderOverride) player.sendSystemMessage(RunMessages.depthOverridden())
            }
        }
        return 1
    }

    private fun resume(player: ServerPlayer): Int {
        when (val result = RunController.resume(player.server, player)) {
            ResumeResult.NoRun -> player.sendSystemMessage(RunMessages.noRun())
            is ResumeResult.AwaitingStarter -> player.sendSystemMessage(RunMessages.catalogue(result.catalogue))
            is ResumeResult.WaveStarted -> Unit // the battle itself is the feedback
            is ResumeResult.WaveUnavailable ->
                player.sendSystemMessage(RunMessages.waveUnavailable(result.plan.wave, result.plan.kind))
            is ResumeResult.ArenaUnavailable -> player.sendSystemMessage(RunMessages.arenaUnavailable())
            is ResumeResult.RosterUnavailable -> player.sendSystemMessage(RunMessages.rosterUnavailable())
            // Both lines: what is blocking the run, then the decision itself. A player who typed
            // `resume` wants the next thing to type, and sending them to another command to find out
            // what they are choosing between is a step this prompt cannot afford.
            is ResumeResult.CatchPending -> {
                player.sendSystemMessage(RunMessages.catchPending(result.pokemon))
                player.sendSystemMessage(RunMessages.catchPrompt(result.pokemon, result.party))
            }

            is ResumeResult.Ended -> player.sendSystemMessage(RunMessages.ended(result.report))
        }
        return 1
    }

    /**
     * Always succeeds, including with no run at all. It reports a fact rather than performing an
     * action, and a failure code on a question the player is entitled to ask would make the command
     * look broken in exactly the case where the answer is the reassuring one.
     */
    private fun pause(player: ServerPlayer, confirmed: Boolean): Int {
        player.sendSystemMessage(RunMessages.pause(RunController.pause(player.server, player, confirmed)))
        return 1
    }

    /**
     * §2.13's prompt. Always succeeds, for [pause]'s reason: it reports a fact, and a failure code on
     * "am I holding anything" would make the command look broken in the reassuring case.
     *
     * The one branch with a side effect is [CatchPrompt.Joined], and the controller says why it is
     * allowed to have one.
     */
    private fun catchPrompt(player: ServerPlayer): Int {
        when (val prompt = RunController.catchPrompt(player.server, player)) {
            CatchPrompt.NoRun -> player.sendSystemMessage(RunMessages.noRun())
            CatchPrompt.NothingHeld -> player.sendSystemMessage(RunMessages.nothingHeld())
            is CatchPrompt.Held -> player.sendSystemMessage(RunMessages.catchPrompt(prompt.pokemon, prompt.party))
            is CatchPrompt.Joined -> player.sendSystemMessage(RunMessages.catchJoined(prompt.pokemon, prompt.slot))
        }
        return 1
    }

    /**
     * Name what `swap <slot> confirm` would destroy, without destroying it.
     *
     * Reads the party through the prompt rather than trusting the slot number, because the whole
     * point of the warning is to say *which* Pokémon dies — a warning that only echoed the number
     * back would confirm nothing the player did not already type.
     */
    private fun warnSwap(player: ServerPlayer, slot: Int): Int {
        val prompt = RunController.catchPrompt(player.server, player)
        if (prompt !is CatchPrompt.Held) return catchPromptFallback(player, prompt)
        val discarded = prompt.party.getOrNull(slot - 1)
        if (discarded == null) {
            player.sendSystemMessage(RunMessages.catchResolved(CatchResolution.NoSuchSlot(prompt.party.size)))
            return 0
        }
        player.sendSystemMessage(RunMessages.confirmSwap(slot, discarded, prompt.pokemon))
        return 1
    }

    private fun warnRelease(player: ServerPlayer): Int {
        val prompt = RunController.catchPrompt(player.server, player)
        if (prompt !is CatchPrompt.Held) return catchPromptFallback(player, prompt)
        player.sendSystemMessage(RunMessages.confirmRelease(prompt.pokemon))
        return 1
    }

    /** The two non-decision answers, so both warnings say the same thing about them. */
    private fun catchPromptFallback(player: ServerPlayer, prompt: CatchPrompt): Int {
        when (prompt) {
            CatchPrompt.NoRun -> player.sendSystemMessage(RunMessages.noRun())
            is CatchPrompt.Joined -> player.sendSystemMessage(RunMessages.catchJoined(prompt.pokemon, prompt.slot))
            else -> player.sendSystemMessage(RunMessages.nothingHeld())
        }
        return 0
    }

    private fun resolveCatch(player: ServerPlayer, decision: CatchDecision): Int {
        val resolution = RunController.resolveCatch(player.server, player, decision)
        if (resolution == null) {
            // Nothing was held — a second `confirm`, or a catch that let itself in while they typed.
            // Said plainly rather than treated as an error: nothing was destroyed either time, and
            // that is the reassurance a player re-typing a destructive command needs.
            player.sendSystemMessage(RunMessages.nothingHeld())
            return 0
        }
        player.sendSystemMessage(RunMessages.catchResolved(resolution))
        return if (resolution is CatchResolution.NoSuchSlot) 0 else 1
    }

    private fun warnAbandon(player: ServerPlayer): Int {
        when (val status = RunController.status(player.server, player)) {
            RunStatus.None -> player.sendSystemMessage(RunMessages.noRun())
            is RunStatus.AwaitingStarter -> player.sendSystemMessage(RunMessages.confirmAbandon(1, 0))
            // A held catch counts. It is a run Pokémon that abandoning destroys along with the party,
            // and a confirmation that undercounted the casualties by one is the exact failure the
            // confirmation exists to prevent.
            is RunStatus.InProgress -> player.sendSystemMessage(
                RunMessages.confirmAbandon(
                    status.run.wave,
                    status.run.partySnapshot().size + (if (status.run.pendingCatch != null) 1 else 0),
                ),
            )
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
        RunStartRefusal.NoArenaAvailable -> RunMessages.noArenaAvailable()
        RunStartRefusal.NoStartersAvailable -> RunMessages.noStarters()
        is RunStartRefusal.NoAffordableStarters -> RunMessages.noAffordableStarters(refusal)
    }
}
