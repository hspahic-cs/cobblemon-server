package com.cobblemonbridge.commands

import com.cobblemonbridge.battle.GymReturnHook
import com.cobblemonbridge.gymtp.WarpPos
import com.cobblemonbridge.permissions.StaffPermissions
import com.mojang.brigadier.CommandDispatcher
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component

/**
 * Staff tooling for the gym-victory return point (op level 2, or the `moderator` group via
 * [PERMISSION_NODE]). The teleport-after-win for gyms 1-10 is opt-in: it only fires while a
 * return point is set.
 *
 *   /gymreturn set    — capture the sender's position+rotation as the return point.
 *   /gymreturn clear  — disable the teleport.
 *   /gymreturn status — show the configured point.
 */
object GymReturnCommands {

    private const val OP_LEVEL = 2

    /** NeoEssentials node that opens this to non-op staff. See [StaffPermissions]. */
    const val PERMISSION_NODE = "cobblemon.staff.gymreturn"

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("gymreturn")
                .requires { StaffPermissions.check(it, PERMISSION_NODE, OP_LEVEL) }
                .then(Commands.literal("set")
                    .executes { ctx -> set(ctx.source); 1 }
                )
                .then(Commands.literal("clear")
                    .executes { ctx -> clear(ctx.source); 1 }
                )
                .then(Commands.literal("status")
                    .executes { ctx -> status(ctx.source); 1 }
                )
        )
    }

    private fun set(source: CommandSourceStack) {
        val pos = source.position
        val rot = source.rotation
        val dim = source.level.dimension().location().toString()
        GymReturnHook.store().set(WarpPos(pos.x, pos.y, pos.z, dim, rot.y, rot.x))
        source.sendSystemMessage(Component.literal(
            "§a[GymReturn] Gym-win teleport → ${"%.1f".format(pos.x)}, ${"%.1f".format(pos.y)}, ${"%.1f".format(pos.z)} §8($dim)"
        ))
    }

    private fun clear(source: CommandSourceStack) {
        GymReturnHook.store().clear()
        source.sendSystemMessage(Component.literal("§a[GymReturn] Cleared — gym wins no longer teleport."))
    }

    private fun status(source: CommandSourceStack) {
        val p = GymReturnHook.store().get()
        source.sendSystemMessage(Component.literal(
            if (p == null) "§7[GymReturn] Unset — gym wins don't teleport."
            else "§d[GymReturn] §f${p.x.toInt()}, ${p.y.toInt()}, ${p.z.toInt()} §8(${p.world})"
        ))
    }
}
