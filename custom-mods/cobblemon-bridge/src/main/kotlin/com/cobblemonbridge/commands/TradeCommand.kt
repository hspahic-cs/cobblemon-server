package com.cobblemonbridge.commands

import com.cobblemonbridge.trade.TradeManager
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer

/**
 * `/trade <player>` — send a trade request to another online player.
 * `/trade accept` — accept the most recent incoming request (opens the trade GUI).
 * `/trade decline` — decline the most recent incoming request.
 * `/trade cancel` — cancel the trade you're currently in (refunds escrowed items + money).
 * `/trade money <amount>` — set the money portion of your offer (clamped to 0..balance).
 *
 * The GUI handles staging Pokémon, dragging items, and per-side confirm — these subcommands
 * are just the chat-side entry points.
 */
object TradeCommand {

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("trade")
                .executes { ctx -> usage(ctx); 1 }
                .then(Commands.literal("accept").executes { ctx -> handleAccept(ctx); 1 })
                .then(Commands.literal("decline").executes { ctx -> handleDecline(ctx); 1 })
                .then(Commands.literal("cancel").executes { ctx -> handleCancel(ctx); 1 })
                .then(Commands.literal("money")
                    .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                        .executes { ctx ->
                            val amount = IntegerArgumentType.getInteger(ctx, "amount")
                            handleMoney(ctx, amount); 1
                        }))
                .then(Commands.argument("player", EntityArgument.player())
                    .executes { ctx ->
                        val target = EntityArgument.getPlayer(ctx, "player")
                        handleRequest(ctx, target); 1
                    })
        )
    }

    private fun usage(ctx: CommandContext<CommandSourceStack>) {
        ctx.source.sendSystemMessage(Component.literal("§e/trade <player>§7 — send a trade request"))
        ctx.source.sendSystemMessage(Component.literal("§e/trade accept|decline|cancel§7 — manage a trade"))
        ctx.source.sendSystemMessage(Component.literal("§e/trade money <amount>§7 — set the money in your offer"))
    }

    private fun handleRequest(ctx: CommandContext<CommandSourceStack>, target: ServerPlayer) {
        val from = ctx.source.player ?: run {
            ctx.source.sendSystemMessage(Component.literal("§c[Trade] Players only.")); return
        }
        TradeManager.request(from, target)
    }

    private fun handleAccept(ctx: CommandContext<CommandSourceStack>) {
        val player = ctx.source.player ?: return
        TradeManager.accept(player)
    }

    private fun handleDecline(ctx: CommandContext<CommandSourceStack>) {
        val player = ctx.source.player ?: return
        TradeManager.decline(player)
    }

    private fun handleCancel(ctx: CommandContext<CommandSourceStack>) {
        val player = ctx.source.player ?: return
        TradeManager.cancel(player)
    }

    private fun handleMoney(ctx: CommandContext<CommandSourceStack>, amount: Int) {
        val player = ctx.source.player ?: return
        TradeManager.setMoney(player, amount)
    }
}
