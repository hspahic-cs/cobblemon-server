package com.cobblemonbridge.commands

import com.cobblemonbridge.CobblemonBridge
import com.cobblemonbridge.gymtp.WarpPos
import com.cobblemonbridge.tags.BridgeTags
import com.cobblemonbridge.tower.TowerManager
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.npc.Villager
import net.minecraft.world.entity.npc.VillagerData
import net.minecraft.world.entity.npc.VillagerProfession
import net.minecraft.world.entity.npc.VillagerType
import net.minecraft.world.phys.AABB

/**
 * Op tooling for the daily battle tower. All subcommands are op level 2.
 *
 *   /tower setfloor <1-3> [hard|normal] — capture the sender's position as that floor's leader
 *                           spot for the given difficulty (default hard). Each floor has BOTH a
 *                           hard (challenge, L50) and normal (regular, uncapped) leader; set both.
 *   /tower setreturn      — capture the run-end return spot (optional; unset → floor 1).
 *   /tower setentry       — spawn the entry-NPC greeter at the sender's position. Right-
 *                           clicking it warps a player past the beat_gym_10 gate to floor 1
 *                           and arms a run ([com.cobblemonbridge.battle.TowerEntryHook]).
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
                        // No difficulty given → hard (back-compat with the old single-leader tower).
                        .executes { ctx -> setFloor(ctx.source, IntegerArgumentType.getInteger(ctx, "floor"), "hard"); 1 }
                        .then(Commands.literal("hard")
                            .executes { ctx -> setFloor(ctx.source, IntegerArgumentType.getInteger(ctx, "floor"), "hard"); 1 })
                        .then(Commands.literal("normal")
                            .executes { ctx -> setFloor(ctx.source, IntegerArgumentType.getInteger(ctx, "floor"), "normal"); 1 })
                    )
                )
                .then(Commands.literal("setreturn")
                    .executes { ctx -> setReturn(ctx.source); 1 }
                )
                .then(Commands.literal("setentry")
                    .executes { ctx -> setEntry(ctx.source) }
                )
                .then(Commands.literal("rotate")
                    .executes { ctx -> forceRotate(ctx.source); 1 }
                )
                .then(Commands.literal("status")
                    .executes { ctx -> status(ctx.source); 1 }
                )
        )
    }

    private fun setFloor(source: CommandSourceStack, floor: Int, difficulty: String) {
        val pos = source.position
        val rot = source.rotation
        val dim = source.level.dimension().location().toString()
        TowerManager.store().setFloor(floor, difficulty, WarpPos(pos.x, pos.y, pos.z, dim, rot.y, rot.x))
        val tail = if (TowerManager.store().floorsConfigured())
            " §7— all hard floors set; set §fnormal§7 spots too, then /tower rotate." else ""
        source.sendSystemMessage(Component.literal(
            "§a[Tower] Floor $floor §e$difficulty§a → ${"%.1f".format(pos.x)}, ${"%.1f".format(pos.y)}, ${"%.1f".format(pos.z)} §8($dim)$tail"
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

    /** Capture the sender's position as the entry anchor and spawn the greeter villager there.
     *  Idempotent — replaces any existing tower-entry villager nearby. Mirrors `/gymtp spawn`. */
    private fun setEntry(source: CommandSourceStack): Int {
        val level: ServerLevel = source.level
        val pos = source.position
        val rot = source.rotation
        val dim = level.dimension().location().toString()
        TowerManager.store().setEntryPos(WarpPos(pos.x, pos.y, pos.z, dim, rot.y, rot.x))

        val killed = killTaggedNear(level, pos.x, pos.y, pos.z, radius = 4.0)
        val villager: Villager = EntityType.VILLAGER.create(level) ?: run {
            source.sendSystemMessage(Component.literal("§c[Tower] Failed to create villager entity"))
            return 0
        }
        villager.moveTo(pos.x, pos.y, pos.z, rot.y, 0f)
        villager.addTag(BridgeTags.TOWER_ENTRY)
        villager.isInvulnerable = true
        villager.setPersistenceRequired()
        villager.isSilent = true
        villager.isNoAi = true
        villager.villagerData = VillagerData(VillagerType.PLAINS, VillagerProfession.LIBRARIAN, 5)
        villager.offers.clear()
        villager.customName = Component.literal("Tower Receptionist")
            .setStyle(Style.EMPTY.withColor(0xFF55FF).withBold(true).withItalic(false))
        villager.isCustomNameVisible = true

        if (!level.addFreshEntity(villager)) {
            source.sendSystemMessage(Component.literal("§c[Tower] Failed to add villager to level"))
            return 0
        }
        val killedNote = if (killed > 0) " §7(replaced $killed existing)" else ""
        source.sendSystemMessage(Component.literal(
            "§a[Tower] Entry NPC → ${"%.1f".format(pos.x)}, ${"%.1f".format(pos.y)}, ${"%.1f".format(pos.z)} §8($dim)$killedNote"
        ))
        CobblemonBridge.logger.info("tower: entry NPC spawned at {},{},{} by {}",
            pos.x.toInt(), pos.y.toInt(), pos.z.toInt(), source.textName)
        return 1
    }

    /** Discard every tower-entry villager inside [radius] of (x,y,z). Returns count removed. */
    private fun killTaggedNear(level: ServerLevel, x: Double, y: Double, z: Double, radius: Double): Int {
        val box = AABB(x - radius, y - radius, z - radius, x + radius, y + radius, z + radius)
        val matches = level.getEntitiesOfClass(Villager::class.java, box) { v ->
            v.tags.contains(BridgeTags.TOWER_ENTRY)
        }
        for (e in matches) e.discard()
        return matches.size
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
            val h = if (s.floor(n, "hard") != null) "§aH" else "§cH"
            val nm = if (s.floor(n, "normal") != null) "§aN" else "§7N"
            "§7$n[$h§7$nm§7]"
        }
        val lineup = TowerManager.leadersForDay(TowerManager.todayEpochDay())
            .mapIndexed { i, (_, name) -> "§7${i + 1}.§f$name" }
            .joinToString(" ")
        source.sendSystemMessage(Component.literal(
            "§d[Tower] §fFloors: $floors §f| Today: $lineup §f| Last rotated epoch-day: §7${s.lastRotatedEpochDay() ?: "never"}"
        ))
    }
}
