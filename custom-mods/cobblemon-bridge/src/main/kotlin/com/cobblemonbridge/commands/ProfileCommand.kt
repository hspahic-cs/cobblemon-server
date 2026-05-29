package com.cobblemonbridge.commands

import com.cobblemonbridge.profile.ProfileBuilder
import com.cobblemonbridge.profile.ProfileMenu
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component

/**
 * `/profile` opens your own profile chest GUI.
 * `/profile <name>` opens the named player's profile (online or offline — uses cached records).
 */
object ProfileCommand {

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("profile")
                .executes { ctx -> openSelf(ctx); 1 }
                .then(Commands.argument("name", StringArgumentType.string())
                    .executes { ctx -> openByName(ctx, StringArgumentType.getString(ctx, "name")); 1 }
                )
        )
    }

    private fun openSelf(ctx: CommandContext<CommandSourceStack>) {
        val viewer = ctx.source.playerOrException
        val snapshot = ProfileBuilder.build(viewer.server, viewer.uuid, viewer.name.string, online = viewer)
        ProfileMenu.open(viewer, snapshot)
    }

    private fun openByName(ctx: CommandContext<CommandSourceStack>, name: String) {
        val viewer = ctx.source.playerOrException
        val server = viewer.server
        val online = server.playerList.players.firstOrNull { it.name.string.equals(name, ignoreCase = true) }
        val targetUuid = online?.uuid ?: server.profileCache?.get(name)?.orElse(null)?.id ?: run {
            viewer.sendSystemMessage(Component.literal("§c[Profile] Unknown player: $name"))
            return
        }
        val resolvedName = online?.name?.string ?: name
        val snapshot = ProfileBuilder.build(server, targetUuid, resolvedName, online = online)
        ProfileMenu.open(viewer, snapshot)
    }
}
