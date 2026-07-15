package com.cobblemonbridge.commands

import com.mojang.brigadier.CommandDispatcher
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.readText

/**
 * `/version` — reports the modpack version currently deployed on this server.
 *
 * The deploy workflow writes the released version to `.deployed_version` in the server's working
 * directory (the topmost `## [X.Y.Z]` heading from CHANGELOG.md) after a successful atomic swap +
 * restart. Reading it here gives players/admins a way to confirm, in-game, exactly which build the
 * server is actually running — so "did the update land?" is answerable without SSH.
 *
 * If the file is missing (e.g. a local/dev run that never went through the deploy pipeline), the
 * command says so rather than guessing.
 */
object VersionCommand {

    private const val VERSION_FILE = ".deployed_version"

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("version")
                .executes { ctx -> report(ctx.source); 1 }
        )
    }

    private fun report(source: CommandSourceStack) {
        val file = Paths.get(VERSION_FILE)
        if (!file.exists()) {
            source.sendSystemMessage(Component.literal(
                "§e[Server] No deployed version on record (§7.deployed_version §emissing — " +
                    "this server wasn't updated through the deploy pipeline)."))
            return
        }
        val version = try {
            file.readText().trim()
        } catch (e: Exception) {
            source.sendSystemMessage(Component.literal("§c[Server] Could not read the deployed version."))
            return
        }
        val shown = version.ifEmpty { "(unknown)" }
        source.sendSystemMessage(Component.literal("§a[Server] Running modpack version §f$shown§a."))
    }
}
