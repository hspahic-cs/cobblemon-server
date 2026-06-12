package com.cobblemonbridge.commands

import com.cobblemonbridge.battle.E4GauntletHook
import com.mojang.brigadier.CommandDispatcher
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component

/**
 * `/e4 skip on|off` — op-only (level 2) Elite Four test bypass for the command sender.
 *
 * With the bypass on, the sender may right-click and challenge any E4 member (gyms 20-24) out of
 * order, and the party/dimension leashes never trip for them — handy for testing a single E4 fight
 * without grinding through the whole gauntlet. In-memory only: it clears on server restart and
 * affects no other player. See [E4GauntletHook.canChallenge].
 */
object E4Command {

    private const val OP_LEVEL = 2

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("e4")
                .requires { it.hasPermission(OP_LEVEL) }
                .then(Commands.literal("skip")
                    .executes { ctx -> status(ctx.source); 1 }
                    .then(Commands.literal("on").executes { ctx -> setSkip(ctx.source, true); 1 })
                    .then(Commands.literal("off").executes { ctx -> setSkip(ctx.source, false); 1 })
                )
        )
    }

    private fun setSkip(source: CommandSourceStack, on: Boolean) {
        val player = source.player ?: run {
            source.sendSystemMessage(Component.literal("§c/e4 skip must be run by a player."))
            return
        }
        E4GauntletHook.setBypass(player.uuid, on)
        player.sendSystemMessage(Component.literal(
            if (on) "§a[Elite Four] §fTest bypass §aON§f — challenge any E4 member in any order. Party/area leashes off."
            else "§e[Elite Four] §fTest bypass §cOFF§f — normal gauntlet rules restored."
        ))
    }

    private fun status(source: CommandSourceStack) {
        val player = source.player ?: run {
            source.sendSystemMessage(Component.literal("§c/e4 skip must be run by a player."))
            return
        }
        val on = E4GauntletHook.isBypassed(player.uuid)
        player.sendSystemMessage(Component.literal(
            "§6[Elite Four] §fTest bypass is ${if (on) "§aON" else "§cOFF"}§f. Use §e/e4 skip on§f or §e/e4 skip off§f."
        ))
    }
}
