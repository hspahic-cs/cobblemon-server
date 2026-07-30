package com.cobblemonranked.bp

import com.cobblemonranked.permissions.StaffPermissions
import com.mojang.brigadier.Command
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component

object BpCommands {

    /** NeoEssentials node that opens `/bp` to non-op staff. See [StaffPermissions]. */
    const val PERMISSION_NODE = "cobblemon.staff.bp"

    fun buildBpCommand(): LiteralArgumentBuilder<CommandSourceStack> {
        return Commands.literal("bp")
            .requires { StaffPermissions.check(it, PERMISSION_NODE, 2) }
            .then(
                Commands.literal("add")
                    .then(
                        Commands.argument("player", StringArgumentType.word())
                            .then(
                                Commands.argument("amount", IntegerArgumentType.integer(1))
                                    .executes { context ->
                                        val playerName = StringArgumentType.getString(context, "player")
                                        val amount = IntegerArgumentType.getInteger(context, "amount")
                                        commandBpAdd(context.source, playerName, amount)
                                    }
                            )
                    )
            )
            .then(
                Commands.literal("set")
                    .then(
                        Commands.argument("player", StringArgumentType.word())
                            .then(
                                Commands.argument("amount", IntegerArgumentType.integer(0))
                                    .executes { context ->
                                        val playerName = StringArgumentType.getString(context, "player")
                                        val amount = IntegerArgumentType.getInteger(context, "amount")
                                        commandBpSet(context.source, playerName, amount)
                                    }
                            )
                    )
            )
    }

    private fun commandBpAdd(source: CommandSourceStack, playerName: String, amount: Int): Int {
        val player = source.server.playerList.getPlayerByName(playerName)
        if (player == null) {
            source.sendSystemMessage(Component.literal("§c[BP] Player '$playerName' not found."))
            return 0
        }

        val newBalance = BpManager.addBalance(player.uuid, amount)
        source.sendSystemMessage(Component.literal("§a[BP] Added §f$amount §aBP to §f${player.name.string}§a. New balance: §f$newBalance"))
        player.sendSystemMessage(Component.literal("§a[BP] You received §f$amount §aBP! Total: §f$newBalance"))
        return Command.SINGLE_SUCCESS
    }

    private fun commandBpSet(source: CommandSourceStack, playerName: String, amount: Int): Int {
        val player = source.server.playerList.getPlayerByName(playerName)
        if (player == null) {
            source.sendSystemMessage(Component.literal("§c[BP] Player '$playerName' not found."))
            return 0
        }

        val oldBalance = BpManager.getBalance(player.uuid)
        BpManager.setBalance(player.uuid, amount)
        source.sendSystemMessage(Component.literal("§a[BP] Set §f${player.name.string}§a's BP to §f$amount§a (was §f$oldBalance§a)."))
        player.sendSystemMessage(Component.literal("§a[BP] Your BP balance has been set to §f$amount"))
        return Command.SINGLE_SUCCESS
    }
}
