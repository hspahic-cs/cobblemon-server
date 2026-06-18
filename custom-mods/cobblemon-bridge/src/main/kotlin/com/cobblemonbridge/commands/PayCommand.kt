package com.cobblemonbridge.commands

import com.cobblemonbridge.CobblemonBridge
import com.cobblemonbridge.economy.EconomyBridge
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer

/**
 * `/pay <player> <amount>` — transfer coins to another online player.
 *
 * Replaces NeoEssentials' `/pay`, which was unreliable on this server (disabled via
 * `config/neoessentials/commands.json` → `"pay": false`). This implementation uses the *same*
 * NeoEssentials economy balances under the hood (via [EconomyBridge]), so `/balance`, `/baltop`
 * and income all stay consistent — only the transfer command itself is ours.
 *
 * Transfer is debit-then-credit: we only credit the recipient once the sender's debit succeeds, and
 * if the credit is somehow rejected we refund the sender so coins can't vanish.
 */
object PayCommand {

    private const val SYMBOL = "$"

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("pay")
                .executes { ctx -> usage(ctx.source); 1 }
                .then(
                    Commands.argument("player", EntityArgument.player())
                        .then(
                            Commands.argument("amount", IntegerArgumentType.integer(1))
                                .executes { ctx ->
                                    handlePay(
                                        ctx,
                                        EntityArgument.getPlayer(ctx, "player"),
                                        IntegerArgumentType.getInteger(ctx, "amount"),
                                    )
                                },
                        ),
                ),
        )
    }

    private fun usage(source: CommandSourceStack) {
        source.sendSystemMessage(Component.literal("§eUsage: §f/pay <player> <amount>"))
    }

    private fun handlePay(ctx: CommandContext<CommandSourceStack>, target: ServerPlayer, amount: Int): Int {
        val sender = ctx.source.player ?: run {
            ctx.source.sendSystemMessage(Component.literal("§c[Pay] Only players can use /pay."))
            return 0
        }
        if (target.uuid == sender.uuid) {
            sender.sendSystemMessage(Component.literal("§c[Pay] You can't pay yourself."))
            return 0
        }
        if (amount <= 0) { // belt-and-suspenders; the arg type already enforces >= 1
            sender.sendSystemMessage(Component.literal("§c[Pay] Amount must be positive."))
            return 0
        }

        val balance = EconomyBridge.getBalance(sender.uuid)
        if (balance < amount) {
            sender.sendSystemMessage(Component.literal("§c[Pay] Not enough coins — you have $SYMBOL$balance."))
            return 0
        }

        // Debit the sender first; the boolean is the authoritative funds check.
        if (!EconomyBridge.withdraw(sender.uuid, amount)) {
            sender.sendSystemMessage(Component.literal("§c[Pay] Payment failed — could not debit your balance."))
            return 0
        }
        // Credit the recipient; refund the sender if the economy rejects the credit.
        if (!EconomyBridge.deposit(target.uuid, amount)) {
            EconomyBridge.deposit(sender.uuid, amount) // best-effort refund
            sender.sendSystemMessage(Component.literal("§c[Pay] Payment failed — ${target.gameProfile.name} couldn't receive it. You were refunded."))
            CobblemonBridge.logger.warn(
                "/pay credit rejected: {} -> {} amount={} (refunded sender)",
                sender.gameProfile.name, target.gameProfile.name, amount,
            )
            return 0
        }

        sender.sendSystemMessage(Component.literal("§aYou paid $SYMBOL$amount to §f${target.gameProfile.name}§a."))
        target.sendSystemMessage(Component.literal("§a§f${sender.gameProfile.name}§a paid you $SYMBOL$amount."))
        CobblemonBridge.logger.info(
            "/pay: {} -> {} amount={}", sender.gameProfile.name, target.gameProfile.name, amount,
        )
        return 1
    }
}
