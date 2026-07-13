package com.cobblemonranked.bp

import com.mojang.brigadier.Command
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component

object ShinyVoucherCommand {

    fun buildShinyCommand(): LiteralArgumentBuilder<CommandSourceStack> {
        return Commands.literal("shiny")
            .requires { it.hasPermission(2) }
            .then(
                Commands.argument("player", StringArgumentType.word())
                    .then(
                        Commands.argument("slot", IntegerArgumentType.integer(1, 6))
                            .executes { context ->
                                val playerName = StringArgumentType.getString(context, "player")
                                val slot = IntegerArgumentType.getInteger(context, "slot")
                                commandShinyVoucher(context.source, playerName, slot)
                            }
                    )
            )
    }

    private fun commandShinyVoucher(source: CommandSourceStack, playerName: String, slot: Int): Int {
        val player = source.server.playerList.getPlayerByName(playerName)
        if (player == null) {
            source.sendSystemMessage(Component.literal("§c[Shiny] Player '$playerName' not found."))
            return 0
        }

        // Check for shiny voucher
        if (!BpVoucher.hasVoucher(player, "shiny")) {
            source.sendSystemMessage(Component.literal("§c[Shiny] ${player.name.string} does not have a Shiny Voucher."))
            return 0
        }

        // Consume voucher
        if (!BpVoucher.consumeVoucher(player, "shiny")) {
            source.sendSystemMessage(Component.literal("§c[Shiny] Failed to consume Shiny Voucher."))
            return 0
        }

        // TODO: Integrate with actual Cobblemon shiny mechanism to make Pokémon shiny
        source.sendSystemMessage(Component.literal("§a[Shiny] Consumed Shiny Voucher for §f${player.name.string}§a (slot $slot)."))
        player.sendSystemMessage(Component.literal("§a[Shiny] Your Shiny Voucher was used by an admin."))
        return Command.SINGLE_SUCCESS
    }
}
