package com.cobblemonbridge.commands

import com.cobblemonbridge.gymtp.WarpPos
import com.cobblemonbridge.tower.TowerManager
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component

/**
 * Op tooling for the daily battle tower. All subcommands are op level 2.
 *
 *   /tower setfloor <1-3> — capture the sender's position+rotation as that floor's
 *                           leader spot. Stand where the NPC should anchor and run it.
 *   /tower rotate         — force a rotation now (kills + resummons today's lineup).
 *                           Used for initial setup and testing; the midnight poll
 *                           handles normal days.
 *   /tower status         — floors configured, today's three leaders, last rotation day.
 */
object TowerCommands {

    private const val OP_LEVEL = 2

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("tower")
                .requires { it.hasPermission(OP_LEVEL) }
                .then(Commands.literal("setfloor")
                    .then(Commands.argument("floor", IntegerArgumentType.integer(1, 3))
                        .executes { ctx ->
                            setFloor(ctx.source, IntegerArgumentType.getInteger(ctx, "floor")); 1
                        }
                    )
                )
                .then(Commands.literal("setreturn")
                    .executes { ctx -> setReturn(ctx.source); 1 }
                )
                .then(Commands.literal("rotate")
                    .executes { ctx -> forceRotate(ctx.source); 1 }
                )
                .then(Commands.literal("status")
                    .executes { ctx -> status(ctx.source); 1 }
                )
        )
    }

    private fun setFloor(source: CommandSourceStack, floor: Int) {
        val pos = source.position
        val rot = source.rotation
        val dim = source.level.dimension().location().toString()
        TowerManager.store().setFloor(floor, WarpPos(pos.x, pos.y, pos.z, dim, rot.y, rot.x))
        source.sendSystemMessage(Component.literal(
            "§a[Tower] Floor $floor → ${"%.1f".format(pos.x)}, ${"%.1f".format(pos.y)}, ${"%.1f".format(pos.z)} §8($dim)" +
            if (TowerManager.store().floorsConfigured()) " §7— all floors set; run /tower rotate to summon." else ""
        ))
    }

    /** Capture the sender's position as the run-end return spot (clear or loss). Optional —
     *  unset falls back to floor 1. */
    private fun setReturn(source: CommandSourceStack) {
        val pos = source.position
        val rot = source.rotation
        val dim = source.level.dimension().location().toString()
        TowerManager.store().setReturnPos(WarpPos(pos.x, pos.y, pos.z, dim, rot.y, rot.x))
        source.sendSystemMessage(Component.literal(
            "§a[Tower] Return spot → ${"%.1f".format(pos.x)}, ${"%.1f".format(pos.y)}, ${"%.1f".format(pos.z)} §8($dim)"
        ))
    }

    private fun forceRotate(source: CommandSourceStack) {
        if (!TowerManager.store().floorsConfigured()) {
            source.sendSystemMessage(Component.literal("§c[Tower] Set all three floors first (/tower setfloor 1..3)."))
            return
        }
        TowerManager.rotate(source.server, force = true)
        source.sendSystemMessage(Component.literal("§a[Tower] Rotated — today's lineup summoned."))
    }

    private fun status(source: CommandSourceStack) {
        val s = TowerManager.store()
        val floors = (1..3).joinToString(" ") { n ->
            val p = s.floor(n)
            if (p == null) "§c$n:unset" else "§a$n:${p.x.toInt()},${p.y.toInt()},${p.z.toInt()}"
        }
        val lineup = TowerManager.leadersForDay(TowerManager.todayEpochDay())
            .mapIndexed { i, (_, name) -> "§7${i + 1}.§f$name" }
            .joinToString(" ")
        source.sendSystemMessage(Component.literal(
            "§d[Tower] §fFloors: $floors §f| Today: $lineup §f| Last rotated epoch-day: §7${s.lastRotatedEpochDay() ?: "never"}"
        ))
    }
}
