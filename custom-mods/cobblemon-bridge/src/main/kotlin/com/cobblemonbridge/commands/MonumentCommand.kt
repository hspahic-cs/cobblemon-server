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
                                Component.literal("§a[Monument] All spent altars cleared — every monument can spawn again.")
                            )
                            1
                        }
                    )
                    .then(Commands.literal("status")
                        .executes { ctx ->
                            val count = LegendaryMonumentLock.spentCount()
                            ctx.source.sendSystemMessage(
                                Component.literal("§e[Monument] §f$count altar(s) permanently spent.")
                            )
                            1
                        }
                    )
                )
        )
    }
}
