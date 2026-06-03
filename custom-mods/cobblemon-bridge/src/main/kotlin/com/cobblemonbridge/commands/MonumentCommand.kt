package com.cobblemonbridge.commands

import com.cobblemonbridge.wild.LegendaryMonumentLock
import com.mojang.brigadier.CommandDispatcher
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component

object MonumentCommand {

    private const val OP_LEVEL = 2

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("monument")
                .then(Commands.literal("admin")
                    .requires { it.hasPermission(OP_LEVEL) }
                    .then(Commands.literal("reset")
                        .executes { ctx ->
                            LegendaryMonumentLock.reset()
                            ctx.source.sendSystemMessage(
                                Component.literal("§a[Monument] Active-legendary slot cleared. To restore a drained altar, /setblock the crying obsidian back to the original pedestal.")
                            )
                            1
                        }
                    )
                    .then(Commands.literal("status")
                        .executes { ctx ->
                            val msg = if (LegendaryMonumentLock.isActive())
                                "§e[Monument] §fA legendary is currently active."
                            else
                                "§e[Monument] §fNo legendary active."
                            ctx.source.sendSystemMessage(Component.literal(msg))
                            1
                        }
                    )
                )
        )
    }
}
