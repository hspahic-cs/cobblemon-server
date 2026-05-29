package com.cobblemonranked.commands

import com.cobblemonranked.CobblemonRanked
import com.cobblemonranked.battle.RankedBattleManager
import com.cobblemonranked.config.ArenaPos
import com.cobblemonranked.config.RankedConfig
import com.cobblemonranked.decay.DecayManager
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.DimensionArgument
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.commands.arguments.coordinates.RotationArgument
import net.minecraft.commands.arguments.coordinates.Vec3Argument
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.neoforged.fml.loading.FMLPaths

object RankedCommands {

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("ranked")
                .executes { ctx ->
                    showHelp(ctx.source, ctx.source.hasPermission(4))
                    1
                }
                .then(Commands.literal("help")
                    .executes { ctx ->
                        showHelp(ctx.source, ctx.source.hasPermission(4))
                        1
                    }
                )
                .then(Commands.literal("challenge")
                    .then(Commands.argument("player", EntityArgument.player())
                        .executes { ctx ->
                            val source = ctx.source.playerOrException
                            val target = EntityArgument.getPlayer(ctx, "player")
                            handleChallenge(source, target)
                            1
                        }
                    )
                )
                .then(Commands.literal("accept")
                    .executes { ctx ->
                        handleAccept(ctx.source.playerOrException)
                        1
                    }
                )
                .then(Commands.literal("decline")
                    .executes { ctx ->
                        handleDecline(ctx.source.playerOrException)
                        1
                    }
                )
                .then(Commands.literal("stats")
                    .executes { ctx ->
                        showStats(ctx.source.playerOrException, ctx.source.playerOrException)
                        1
                    }
                    .then(Commands.argument("player", StringArgumentType.string())
                        .executes { ctx ->
                            // Accept either an online player name or any name with an EloStore entry,
                            // so the console can inspect offline players' stats too.
                            showStatsByName(ctx.source, StringArgumentType.getString(ctx, "player"))
                            1
                        }
                    )
                )
                .then(Commands.literal("leaderboard")
                    .executes { ctx ->
                        showLeaderboard(ctx.source)
                        1
                    }
                )
                .then(Commands.literal("admin")
                    .requires { it.hasPermission(4) }
                    .then(Commands.literal("setelo")
                        .then(Commands.argument("player", EntityArgument.player())
                            .then(Commands.argument("value", IntegerArgumentType.integer(0))
                                .executes { ctx ->
                                    val target = EntityArgument.getPlayer(ctx, "player")
                                    val value = IntegerArgumentType.getInteger(ctx, "value")
                                    adminSetElo(ctx.source, target, value)
                                    1
                                }
                            )
                        )
                    )
                    .then(Commands.literal("decay")
                        .executes { ctx ->
                            DecayManager.forceDecay(ctx.source.server)
                            ctx.source.sendSystemMessage(Component.literal("[Ranked] Decay manually triggered."))
                            1
                        }
                    )
                    .then(Commands.literal("force")
                        .then(Commands.argument("player1", EntityArgument.player())
                            .then(Commands.argument("player2", EntityArgument.player())
                                .executes { ctx ->
                                    val p1 = EntityArgument.getPlayer(ctx, "player1")
                                    val p2 = EntityArgument.getPlayer(ctx, "player2")
                                    adminForce(ctx.source, p1, p2)
                                    1
                                }
                            )
                        )
                    )
                    .then(Commands.literal("reload")
                        .executes { ctx ->
                            CobblemonRanked.config = RankedConfig.load(FMLPaths.CONFIGDIR.get())
                            val arena = if (CobblemonRanked.config.isArenaConfigured()) "configured" else "disabled"
                            ctx.source.sendSystemMessage(Component.literal("[Ranked] Config reloaded. Arena: $arena."))
                            1
                        }
                    )
                    .then(Commands.literal("forfeit")
                        .then(Commands.argument("player", EntityArgument.player())
                            .executes { ctx ->
                                val target = EntityArgument.getPlayer(ctx, "player")
                                val forfeited = RankedBattleManager.forfeitMatch(target, reason = "was forfeited by admin")
                                if (forfeited) {
                                    ctx.source.sendSystemMessage(Component.literal(
                                        "§a[Ranked] Forfeited ${target.name.string}'s active match (opponent gets the win)."))
                                } else {
                                    ctx.source.sendSystemMessage(Component.literal(
                                        "§c[Ranked] ${target.name.string} isn't in an active match."))
                                }
                                1
                            }
                        )
                    )
                    .then(Commands.literal("simulate")
                        .then(Commands.argument("name1", StringArgumentType.string())
                            .then(Commands.argument("name2", StringArgumentType.string())
                                .executes { ctx ->
                                    val n1 = StringArgumentType.getString(ctx, "name1")
                                    val n2 = StringArgumentType.getString(ctx, "name2")
                                    if (n1.equals(n2, ignoreCase = true)) {
                                        ctx.source.sendSystemMessage(Component.literal(
                                            "§c[Ranked] Both names are the same — pick two different players."))
                                        return@executes 0
                                    }
                                    adminSimulate(ctx.source, n1, n2)
                                    1
                                }
                            )
                        )
                    )
                    // Arena 1 — slot 1 = player1 landing, slot 2 = player2 landing.
                    .then(Commands.literal("setarena")
                        .then(Commands.argument("slot", IntegerArgumentType.integer(1, 2))
                            .executes { ctx -> adminSetArenaFromSender(ctx.source, arenaNum = 1,
                                IntegerArgumentType.getInteger(ctx, "slot")) }
                            .then(Commands.argument("pos", Vec3Argument.vec3(true))
                                .executes { ctx -> adminSetArenaExplicit(ctx, arenaNum = 1, includeRotation = false, includeDim = false) }
                                .then(Commands.argument("rot", RotationArgument.rotation())
                                    .executes { ctx -> adminSetArenaExplicit(ctx, arenaNum = 1, includeRotation = true, includeDim = false) }
                                    .then(Commands.argument("dimension", DimensionArgument.dimension())
                                        .executes { ctx -> adminSetArenaExplicit(ctx, arenaNum = 1, includeRotation = true, includeDim = true) }
                                    )
                                )
                            )
                        )
                    )
                    // Arena 2 — slot 1 = player1 landing, slot 2 = player2 landing.
                    .then(Commands.literal("setarena2")
                        .then(Commands.argument("slot", IntegerArgumentType.integer(1, 2))
                            .executes { ctx -> adminSetArenaFromSender(ctx.source, arenaNum = 2,
                                IntegerArgumentType.getInteger(ctx, "slot")) }
                            .then(Commands.argument("pos", Vec3Argument.vec3(true))
                                .executes { ctx -> adminSetArenaExplicit(ctx, arenaNum = 2, includeRotation = false, includeDim = false) }
                                .then(Commands.argument("rot", RotationArgument.rotation())
                                    .executes { ctx -> adminSetArenaExplicit(ctx, arenaNum = 2, includeRotation = true, includeDim = false) }
                                    .then(Commands.argument("dimension", DimensionArgument.dimension())
                                        .executes { ctx -> adminSetArenaExplicit(ctx, arenaNum = 2, includeRotation = true, includeDim = true) }
                                    )
                                )
                            )
                        )
                    )
                    // Overflow spawn — shared landing point when both arenas are in use. One
                    // position; both players land there.
                    .then(Commands.literal("setoverflow")
                        .executes { ctx -> adminSetOverflowFromSender(ctx.source); 1 }
                        .then(Commands.argument("pos", Vec3Argument.vec3(true))
                            .executes { ctx -> adminSetOverflowExplicit(ctx, includeRotation = false, includeDim = false) }
                            .then(Commands.argument("rot", RotationArgument.rotation())
                                .executes { ctx -> adminSetOverflowExplicit(ctx, includeRotation = true, includeDim = false) }
                                .then(Commands.argument("dimension", DimensionArgument.dimension())
                                    .executes { ctx -> adminSetOverflowExplicit(ctx, includeRotation = true, includeDim = true) }
                                )
                            )
                        )
                    )
                    .then(Commands.literal("clearpos")
                        .then(Commands.argument("slot", IntegerArgumentType.integer(1, 2))
                            .executes { ctx -> adminClearArena(ctx.source, arenaNum = 1,
                                IntegerArgumentType.getInteger(ctx, "slot")); 1 }
                        )
                    )
                    .then(Commands.literal("clearpos2")
                        .then(Commands.argument("slot", IntegerArgumentType.integer(1, 2))
                            .executes { ctx -> adminClearArena(ctx.source, arenaNum = 2,
                                IntegerArgumentType.getInteger(ctx, "slot")); 1 }
                        )
                    )
                    .then(Commands.literal("clearoverflow")
                        .executes { ctx -> adminClearOverflow(ctx.source); 1 }
                    )
                    .then(Commands.literal("showarena")
                        .executes { ctx -> adminShowArena(ctx.source); 1 }
                    )
                )
        )
    }

    private fun showHelp(source: CommandSourceStack, includeAdmin: Boolean) {
        val lines = mutableListOf(
            "§e[Ranked] §fCommands:",
            "§7  /ranked challenge <player> §f— challenge a player to a ranked match",
            "§7  /ranked accept §f— accept a pending challenge",
            "§7  /ranked decline §f— decline a pending challenge",
            "§7  /ranked stats [player] §f— view ELO, wins, losses",
            "§7  /ranked leaderboard §f— top players by ELO",
        )
        if (includeAdmin) {
            lines += listOf(
                "§e[Ranked] §fAdmin (op level 4):",
                "§7  /ranked admin setelo <player> <value> §f— override a player's ELO",
                "§7  /ranked admin decay §f— manually trigger daily decay",
                "§7  /ranked admin force <player1> <player2> §f— force a match (bypasses daily limit)",
                "§7  /ranked admin forfeit <player> §f— end <player>'s active match as a forfeit (opponent wins, both TP back)",
                "§7  /ranked admin reload §f— reload config.json from disk",
                "§7  /ranked admin simulate <name1> <name2> §f— simulate a match (winner picked by ELO odds; offline-friendly)",
                "§7  /ranked admin setarena 1|2 [<x y z> [yaw pitch] [dim]] §f— set arena teleport point",
                "§7  /ranked admin clearpos 1|2 §f— clear an arena teleport point",
                "§7  /ranked admin showarena §f— print current arena positions",
            )
        }
        lines.forEach { source.sendSystemMessage(Component.literal(it)) }
    }

    /**
     * Drives RankedBattleManager.simulateMatch and prints a transparent breakdown to the
     * source: pre-match ELOs, win probability, the random roll, and the applied delta.
     */
    private fun adminSimulate(source: CommandSourceStack, name1: String, name2: String) {
        val outcome = RankedBattleManager.simulateMatch(source.server, name1, name2)
        val pct1 = (outcome.expected1 * 100).toInt()
        val pct2 = 100 - pct1
        val winner = if (outcome.player1Wins) name1 else name2
        source.sendSystemMessage(Component.literal(
            "§e[Ranked] §fSimulating §a$name1 §7(${outcome.elo1})§f vs §a$name2 §7(${outcome.elo2})"))
        source.sendSystemMessage(Component.literal(
            "§7  Win probability: §f$name1 $pct1%§7 / §f$name2 $pct2%"))
        source.sendSystemMessage(Component.literal(
            "§7  Roll: §f${"%.3f".format(outcome.roll)}§7 → §a$winner wins"))
        // applyMatchResult already broadcasts the ELO update lines, so no need to repeat here.
    }

    private fun handleChallenge(challenger: ServerPlayer, target: ServerPlayer) {
        val challengeManager = CobblemonRanked.challengeManager
        val error = challengeManager.challenge(challenger, target)
        if (error != null) {
            challenger.sendSystemMessage(Component.literal("[Ranked] $error"))
            return
        }

        val forced = challengeManager.getPendingForced(target.uuid)
        if (forced != null) {
            RankedBattleManager.startTeamSelection(challenger, target)
        }
    }

    private fun handleAccept(player: ServerPlayer) {
        val challenge = CobblemonRanked.challengeManager.accept(player)
        if (challenge == null) {
            player.sendSystemMessage(Component.literal("[Ranked] No pending challenge to accept."))
            return
        }
        val challenger = player.server.playerList.getPlayer(challenge.challengerUuid)
        if (challenger == null) {
            player.sendSystemMessage(Component.literal("[Ranked] Challenger is no longer online."))
            return
        }
        RankedBattleManager.startTeamSelection(challenger, player)
    }

    private fun handleDecline(player: ServerPlayer) {
        if (!CobblemonRanked.challengeManager.decline(player)) {
            player.sendSystemMessage(Component.literal("[Ranked] No pending challenge to decline."))
        } else {
            player.sendSystemMessage(Component.literal("[Ranked] Challenge declined."))
        }
    }

    private fun showStats(viewer: ServerPlayer, target: ServerPlayer) {
        val data = CobblemonRanked.eloStore.getOrCreate(target.uuid, target.name.string)
        viewer.sendSystemMessage(Component.literal(
            "[Ranked] ${target.name.string}: ELO ${data.elo} | ${data.wins}W / ${data.losses}L | Last battle: ${data.lastBattleDate ?: "never"}"
        ))
    }

    /**
     * Stats lookup that works from any source (player or console). Resolves the target by:
     *   1) An online player matching the name (case-insensitive), preferring their server UUID.
     *   2) An existing EloStore entry whose stored name matches (case-insensitive).
     *   3) Returns "no stats" if no record found and the target isn't online.
     */
    private fun showStatsByName(source: CommandSourceStack, name: String) {
        val online = source.server.playerList.players.firstOrNull { it.name.string.equals(name, ignoreCase = true) }
        if (online != null) {
            val data = CobblemonRanked.eloStore.getOrCreate(online.uuid, online.name.string)
            source.sendSystemMessage(Component.literal(
                "[Ranked] ${online.name.string}: ELO ${data.elo} | ${data.wins}W / ${data.losses}L | Last battle: ${data.lastBattleDate ?: "never"}"
            ))
            return
        }
        val existing = CobblemonRanked.eloStore.getAll().entries.firstOrNull {
            it.value.name.equals(name, ignoreCase = true)
        }
        if (existing != null) {
            val data = existing.value
            source.sendSystemMessage(Component.literal(
                "[Ranked] ${data.name}: ELO ${data.elo} | ${data.wins}W / ${data.losses}L | Last battle: ${data.lastBattleDate ?: "never"} (offline)"
            ))
            return
        }
        source.sendSystemMessage(Component.literal("§c[Ranked] No record for '$name' (player has not battled and is not online)"))
    }

    private fun showLeaderboard(source: CommandSourceStack) {
        val config = CobblemonRanked.config
        val leaderboard = CobblemonRanked.eloStore.getLeaderboard()
        source.sendSystemMessage(Component.literal("[Ranked] === ELO Leaderboard ==="))
        if (leaderboard.isEmpty()) {
            source.sendSystemMessage(Component.literal("  No players ranked yet."))
            return
        }
        val topN = leaderboard.take(config.leaderboardSize)
        topN.forEachIndexed { i, (_, data) ->
            source.sendSystemMessage(Component.literal(
                "  ${i + 1}. ${data.name}: ${data.elo} (${data.wins}W/${data.losses}L)"
            ))
        }

        // Show caller's rank if not in top N
        val player = source.player ?: return
        val playerUuid = player.uuid.toString()
        val playerIndex = leaderboard.indexOfFirst { it.first == playerUuid }
        if (playerIndex >= config.leaderboardSize) {
            val (_, playerData) = leaderboard[playerIndex]
            source.sendSystemMessage(Component.literal("  ---"))
            source.sendSystemMessage(Component.literal(
                "  ${playerIndex + 1}. ${playerData.name}: ${playerData.elo} (${playerData.wins}W/${playerData.losses}L)"
            ))
        }
    }

    private fun adminSetElo(source: CommandSourceStack, target: ServerPlayer, value: Int) {
        CobblemonRanked.eloStore.setElo(target.uuid, target.name.string, value)
        source.sendSystemMessage(Component.literal(
            "[Ranked] Set ${target.name.string}'s ELO to ${value.coerceAtLeast(CobblemonRanked.config.minimumElo)}"
        ))
    }

    private fun adminForce(source: CommandSourceStack, p1: ServerPlayer, p2: ServerPlayer) {
        if (p1.uuid == p2.uuid) {
            source.sendSystemMessage(Component.literal("[Ranked] Can't force a player to fight themselves."))
            return
        }
        source.sendSystemMessage(Component.literal(
            "[Ranked] Forcing match: ${p1.name.string} vs ${p2.name.string}"
        ))
        RankedBattleManager.startTeamSelection(p1, p2)
    }

    /**
     * Capture sender's current position + facing + dimension into `arenaPos<slot>` and persist.
     * `slot` is validated as 1..2 by the Brigadier IntegerArgumentType range.
     */
    private fun adminSetArenaFromSender(source: CommandSourceStack, arenaNum: Int, slot: Int): Int {
        val pos = source.position
        val rot = source.rotation
        val dim = source.level.dimension().location().toString()
        val arena = ArenaPos(pos.x, pos.y, pos.z, dim, rot.y, rot.x)
        applyArena(source, arenaNum, slot, arena, captured = true)
        return 1
    }

    private fun adminSetArenaExplicit(
        ctx: CommandContext<CommandSourceStack>,
        arenaNum: Int,
        includeRotation: Boolean,
        includeDim: Boolean,
    ): Int {
        val source = ctx.source
        val slot = IntegerArgumentType.getInteger(ctx, "slot")
        val arena = readArenaPos(ctx, includeRotation, includeDim)
        applyArena(source, arenaNum, slot, arena, captured = false)
        return 1
    }

    private fun adminSetOverflowFromSender(source: CommandSourceStack) {
        val pos = source.position
        val rot = source.rotation
        val dim = source.level.dimension().location().toString()
        applyOverflow(source, ArenaPos(pos.x, pos.y, pos.z, dim, rot.y, rot.x), captured = true)
    }

    private fun adminSetOverflowExplicit(
        ctx: CommandContext<CommandSourceStack>,
        includeRotation: Boolean,
        includeDim: Boolean,
    ): Int {
        applyOverflow(ctx.source, readArenaPos(ctx, includeRotation, includeDim), captured = false)
        return 1
    }

    private fun readArenaPos(
        ctx: CommandContext<CommandSourceStack>,
        includeRotation: Boolean,
        includeDim: Boolean,
    ): ArenaPos {
        val source = ctx.source
        val coord = Vec3Argument.getVec3(ctx, "pos")
        val (yaw, pitch) = if (includeRotation) {
            val r = RotationArgument.getRotation(ctx, "rot").getRotation(source)
            r.y to r.x
        } else 0f to 0f
        val dim = if (includeDim) {
            DimensionArgument.getDimension(ctx, "dimension").dimension().location().toString()
        } else source.level.dimension().location().toString()
        return ArenaPos(coord.x, coord.y, coord.z, dim, yaw, pitch)
    }

    private fun applyArena(source: CommandSourceStack, arenaNum: Int, slot: Int, arena: ArenaPos, captured: Boolean) {
        val updated = when (arenaNum to slot) {
            1 to 1 -> CobblemonRanked.config.copy(arenaPos1 = arena)
            1 to 2 -> CobblemonRanked.config.copy(arenaPos2 = arena)
            2 to 1 -> CobblemonRanked.config.copy(arena2Pos1 = arena)
            2 to 2 -> CobblemonRanked.config.copy(arena2Pos2 = arena)
            else -> return  // Brigadier guards this, but be defensive.
        }
        CobblemonRanked.config = updated
        RankedConfig.save(FMLPaths.CONFIGDIR.get(), updated)
        val verb = if (captured) "Captured" else "Set"
        source.sendSystemMessage(Component.literal(
            "§a[Ranked] $verb arena $arenaNum slot $slot → ${formatArena(arena)}"
        ))
        reportArenaState(source, updated)
    }

    private fun applyOverflow(source: CommandSourceStack, arena: ArenaPos, captured: Boolean) {
        val updated = CobblemonRanked.config.copy(spawnPos = arena)
        CobblemonRanked.config = updated
        RankedConfig.save(FMLPaths.CONFIGDIR.get(), updated)
        val verb = if (captured) "Captured" else "Set"
        source.sendSystemMessage(Component.literal(
            "§a[Ranked] $verb overflow spawn → ${formatArena(arena)}"
        ))
        reportArenaState(source, updated)
    }

    private fun adminClearArena(source: CommandSourceStack, arenaNum: Int, slot: Int) {
        val updated = when (arenaNum to slot) {
            1 to 1 -> CobblemonRanked.config.copy(arenaPos1 = null)
            1 to 2 -> CobblemonRanked.config.copy(arenaPos2 = null)
            2 to 1 -> CobblemonRanked.config.copy(arena2Pos1 = null)
            2 to 2 -> CobblemonRanked.config.copy(arena2Pos2 = null)
            else -> return
        }
        CobblemonRanked.config = updated
        RankedConfig.save(FMLPaths.CONFIGDIR.get(), updated)
        source.sendSystemMessage(Component.literal("§a[Ranked] Cleared arena $arenaNum slot $slot"))
    }

    private fun adminClearOverflow(source: CommandSourceStack) {
        val updated = CobblemonRanked.config.copy(spawnPos = null)
        CobblemonRanked.config = updated
        RankedConfig.save(FMLPaths.CONFIGDIR.get(), updated)
        source.sendSystemMessage(Component.literal("§a[Ranked] Cleared overflow spawn"))
    }

    private fun reportArenaState(source: CommandSourceStack, cfg: RankedConfig) {
        val a1 = if (cfg.isArenaConfigured()) "§a✓" else "§7—"
        val a2 = if (cfg.isArena2Configured()) "§a✓" else "§7—"
        val sp = if (cfg.isSpawnConfigured()) "§a✓" else "§7—"
        source.sendSystemMessage(Component.literal(
            "§7[Ranked] Allocation order — arena1 $a1§7 · arena2 $a2§7 · overflow $sp"
        ))
    }

    private fun adminShowArena(source: CommandSourceStack) {
        val cfg = CobblemonRanked.config
        source.sendSystemMessage(Component.literal("§e[Ranked] §fTeleport configuration:"))
        source.sendSystemMessage(Component.literal("§e  Arena 1 §7(/warp arena1)"))
        source.sendSystemMessage(Component.literal("§7    slot 1: §f${cfg.arenaPos1?.let { formatArena(it) } ?: "§8(unset)"}"))
        source.sendSystemMessage(Component.literal("§7    slot 2: §f${cfg.arenaPos2?.let { formatArena(it) } ?: "§8(unset)"}"))
        source.sendSystemMessage(Component.literal("§e  Arena 2 §7(/warp arena2)"))
        source.sendSystemMessage(Component.literal("§7    slot 1: §f${cfg.arena2Pos1?.let { formatArena(it) } ?: "§8(unset)"}"))
        source.sendSystemMessage(Component.literal("§7    slot 2: §f${cfg.arena2Pos2?.let { formatArena(it) } ?: "§8(unset)"}"))
        source.sendSystemMessage(Component.literal("§e  Overflow §7(shared)"))
        source.sendSystemMessage(Component.literal("§7    spawn:  §f${cfg.spawnPos?.let { formatArena(it) } ?: "§8(unset)"}"))
        reportArenaState(source, cfg)
    }

    private fun formatArena(a: ArenaPos): String =
        "${"%.1f".format(a.x)}, ${"%.1f".format(a.y)}, ${"%.1f".format(a.z)} §8(${a.world}, yaw ${"%.1f".format(a.yaw)}, pitch ${"%.1f".format(a.pitch)})"
}
