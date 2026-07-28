package com.cobblemonbridge.commands

import com.cobblemonbridge.battle.BattleSpeed
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.FloatArgumentType
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component

/**
 * `/battlespeed` — server-wide battle pacing, op-only (permission 2).
 *
 *   /battlespeed              show the current multiplier and whether the hooks are live
 *   /battlespeed <0.25-5.0>   set it; takes effect on the next pause, persists across restarts
 *
 * Changes apply mid-battle — the multiplier is read per pause, not captured at battle start.
 */
object BattleSpeedCommand {

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("battlespeed")
                .requires { it.hasPermission(2) }
                .executes { ctx -> show(ctx.source); 1 }
                .then(
                    Commands.argument(
                        "multiplier",
                        FloatArgumentType.floatArg(BattleSpeed.MIN_MULTIPLIER, BattleSpeed.MAX_MULTIPLIER),
                    ).executes { ctx ->
                        val applied = BattleSpeed.set(FloatArgumentType.getFloat(ctx, "multiplier"))
                        ctx.source.sendSystemMessage(
                            Component.literal("§a[BattleSpeed] §fNow §e${fmt(applied)}x§f${suffix(applied)}.")
                        )
                        if (applied > 2.0f) {
                            ctx.source.sendSystemMessage(
                                Component.literal(
                                    "§7  Above 2x the client's move animations can't keep up — " +
                                        "expect visible clipping."
                                )
                            )
                        }
                        1
                    }
                )
        )
    }

    private fun show(source: CommandSourceStack) {
        val m = BattleSpeed.multiplier
        source.sendSystemMessage(
            Component.literal("§e[BattleSpeed] §fCurrent: §e${fmt(m)}x§f${suffix(m)}")
        )
        if (!BattleSpeed.isApplied()) {
            source.sendSystemMessage(
                Component.literal(
                    "§7  No battle has run since restart, or the Cobblemon hooks did not apply. " +
                        "Run a battle and check again."
                )
            )
        }
    }

    private fun fmt(value: Float): String = "%.2f".format(value).trimEnd('0').trimEnd('.')

    private fun suffix(value: Float): String = when {
        value > 1.0f -> " §7(faster than stock)"
        value < 1.0f -> " §7(slower than stock)"
        else -> " §7(stock Cobblemon pacing)"
    }
}
