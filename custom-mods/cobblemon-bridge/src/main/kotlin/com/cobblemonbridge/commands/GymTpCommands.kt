package com.cobblemonbridge.commands

import com.cobblemonbridge.CobblemonBridge
import com.cobblemonbridge.gymtp.GymEntry
import com.cobblemonbridge.gymtp.GymTpRegistry
import com.cobblemonbridge.gymtp.WarpPos
import com.cobblemonbridge.tags.BridgeTags
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.suggestion.SuggestionProvider
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.DimensionArgument
import net.minecraft.commands.arguments.coordinates.RotationArgument
import net.minecraft.commands.arguments.coordinates.Vec3Argument
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.npc.Villager
import net.minecraft.world.entity.npc.VillagerData
import net.minecraft.world.entity.npc.VillagerProfession
import net.minecraft.world.entity.npc.VillagerType

/**
 * Op-only admin commands for the gym-warp villager. See spec doc:
 *   docs/superpowers/specs/2026-05-28-gym-tp-villager-design.md
 *
 * Subcommands:
 *   /gymtp set <id>                                       — capture sender pos+rot+dim
 *   /gymtp set <id> at <x> <y> <z> [yaw pitch] [dim]      — explicit form
 *   /gymtp set <id> unlock <advancement>                  — set unlock advancement
 *   /gymtp set <id> label <text...>                       — set display label
 *   /gymtp clear <id>                                     — remove entry
 *   /gymtp list                                           — print all entries
 *   /gymtp spawn                                          — summon tagged villager
 *   /gymtp delete                                         — kill tagged villagers nearby
 */
object GymTpCommands {

    private const val PERMISSION_LEVEL = 2  // ops

    /** Tab-completes `<id>` from the live store. */
    private val ID_SUGGESTIONS: SuggestionProvider<CommandSourceStack> =
        SuggestionProvider { _: CommandContext<CommandSourceStack>, builder ->
            GymTpRegistry.store().entries().keys.forEach { builder.suggest(it) }
            builder.buildFuture()
        }

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("gymtp")
                .requires { it.hasPermission(PERMISSION_LEVEL) }
                .executes { ctx -> printHelp(ctx.source); 1 }
                .then(Commands.literal("help").executes { ctx -> printHelp(ctx.source); 1 })
                .then(Commands.literal("list").executes { ctx -> listEntries(ctx.source); 1 })
                .then(Commands.literal("spawn").executes { ctx -> spawnVillager(ctx.source) })
                .then(Commands.literal("delete").executes { ctx -> deleteVillagers(ctx.source) })
                .then(Commands.literal("clear")
                    .then(Commands.argument("id", StringArgumentType.word())
                        .suggests(ID_SUGGESTIONS)
                        .executes { ctx -> clearEntry(ctx.source, StringArgumentType.getString(ctx, "id")) }
                    )
                )
                .then(Commands.literal("set")
                    .then(Commands.argument("id", StringArgumentType.word())
                        .suggests(ID_SUGGESTIONS)
                        // /gymtp set <id> — capture sender position
                        .executes { ctx ->
                            setFromSender(
                                ctx.source,
                                StringArgumentType.getString(ctx, "id"),
                            )
                        }
                        // /gymtp set <id> at <x y z>
                        .then(Commands.literal("at")
                            .then(Commands.argument("pos", Vec3Argument.vec3(true))
                                .executes { ctx -> setExplicit(ctx, includeRotation = false, includeDim = false) }
                                .then(Commands.argument("rot", RotationArgument.rotation())
                                    .executes { ctx -> setExplicit(ctx, includeRotation = true, includeDim = false) }
                                    .then(Commands.argument("dimension", DimensionArgument.dimension())
                                        .executes { ctx -> setExplicit(ctx, includeRotation = true, includeDim = true) }
                                    )
                                )
                            )
                        )
                        // /gymtp set <id> unlock <advancement>
                        .then(Commands.literal("unlock")
                            .then(Commands.argument("advancement", StringArgumentType.greedyString())
                                .executes { ctx ->
                                    setUnlock(
                                        ctx.source,
                                        StringArgumentType.getString(ctx, "id"),
                                        StringArgumentType.getString(ctx, "advancement"),
                                    )
                                }
                            )
                        )
                        // /gymtp set <id> label <text>
                        .then(Commands.literal("label")
                            .then(Commands.argument("text", StringArgumentType.greedyString())
                                .executes { ctx ->
                                    setLabel(
                                        ctx.source,
                                        StringArgumentType.getString(ctx, "id"),
                                        StringArgumentType.getString(ctx, "text"),
                                    )
                                }
                            )
                        )
                    )
                )
        )
    }

    // ─── handlers ──────────────────────────────────────────────────────────

    private fun printHelp(source: CommandSourceStack) {
        listOf(
            "§e[GymTP] §fAdmin (op level $PERMISSION_LEVEL):",
            "§7  /gymtp set <id> §f— capture your current pos+facing+dim",
            "§7  /gymtp set <id> at <x y z> [yaw pitch] [dim] §f— explicit",
            "§7  /gymtp set <id> unlock <advancement> §f— gate visibility",
            "§7  /gymtp set <id> label <text> §f— override display label",
            "§7  /gymtp clear <id> §f— remove an entry",
            "§7  /gymtp list §f— print all entries",
            "§7  /gymtp spawn §f— summon villager at your position",
            "§7  /gymtp delete §f— kill the tagged villager(s) near you",
        ).forEach { source.sendSystemMessage(Component.literal(it)) }
    }

    private fun setFromSender(source: CommandSourceStack, id: String): Int {
        val pos = source.position
        val rot = source.rotation
        val dim = source.level.dimension().location().toString()
        val existing = GymTpRegistry.store().get(id)
        val entry = GymEntry(
            position = WarpPos(pos.x, pos.y, pos.z, dim, rot.y, rot.x),
            unlockAdvancement = existing?.unlockAdvancement,
            label = existing?.label,
        )
        GymTpRegistry.store().set(id, entry)
        source.sendSystemMessage(Component.literal(
            "§a[GymTP] Set '$id' → ${fmt(pos.x, pos.y, pos.z)} ($dim, yaw ${"%.1f".format(rot.y)})"
        ))
        return 1
    }

    private fun setExplicit(
        ctx: CommandContext<CommandSourceStack>,
        includeRotation: Boolean,
        includeDim: Boolean,
    ): Int {
        val source = ctx.source
        val id = StringArgumentType.getString(ctx, "id")
        val coord = Vec3Argument.getVec3(ctx, "pos")
        val (yaw, pitch) = if (includeRotation) {
            val rot = RotationArgument.getRotation(ctx, "rot").getRotation(source)
            rot.y to rot.x
        } else 0f to 0f
        val dim = if (includeDim) {
            DimensionArgument.getDimension(ctx, "dimension").dimension().location().toString()
        } else source.level.dimension().location().toString()

        val existing = GymTpRegistry.store().get(id)
        val entry = GymEntry(
            position = WarpPos(coord.x, coord.y, coord.z, dim, yaw, pitch),
            unlockAdvancement = existing?.unlockAdvancement,
            label = existing?.label,
        )
        GymTpRegistry.store().set(id, entry)
        source.sendSystemMessage(Component.literal(
            "§a[GymTP] Set '$id' → ${fmt(coord.x, coord.y, coord.z)} ($dim)"
        ))
        return 1
    }

    private fun setUnlock(source: CommandSourceStack, id: String, advancement: String): Int {
        val existing = GymTpRegistry.store().get(id)
        if (existing == null) {
            source.sendSystemMessage(Component.literal(
                "§c[GymTP] '$id' has no position yet. Use §f/gymtp set $id§c first."
            ))
            return 0
        }
        GymTpRegistry.store().set(id, existing.copy(unlockAdvancement = advancement))
        source.sendSystemMessage(Component.literal(
            "§a[GymTP] '$id' unlock advancement → §f$advancement"
        ))
        return 1
    }

    private fun setLabel(source: CommandSourceStack, id: String, label: String): Int {
        val existing = GymTpRegistry.store().get(id)
        if (existing == null) {
            source.sendSystemMessage(Component.literal(
                "§c[GymTP] '$id' has no position yet. Use §f/gymtp set $id§c first."
            ))
            return 0
        }
        GymTpRegistry.store().set(id, existing.copy(label = label))
        source.sendSystemMessage(Component.literal("§a[GymTP] '$id' label → §f$label"))
        return 1
    }

    private fun clearEntry(source: CommandSourceStack, id: String): Int {
        return if (GymTpRegistry.store().remove(id)) {
            source.sendSystemMessage(Component.literal("§a[GymTP] Removed '$id'"))
            1
        } else {
            source.sendSystemMessage(Component.literal("§c[GymTP] No entry '$id'"))
            0
        }
    }

    private fun listEntries(source: CommandSourceStack) {
        val entries = GymTpRegistry.store().entries()
        source.sendSystemMessage(Component.literal("§e[GymTP] §f${entries.size} entr${if (entries.size == 1) "y" else "ies"}:"))
        if (entries.isEmpty()) {
            source.sendSystemMessage(Component.literal("§7  (none — use /gymtp set <id> to add one)"))
            return
        }
        for ((id, entry) in entries) {
            val p = entry.position
            val unlock = entry.unlockAdvancement?.let { " §8unlock=§f$it" } ?: ""
            val label = entry.label?.let { " §8label=§f\"$it\"" } ?: ""
            source.sendSystemMessage(Component.literal(
                "§7  §e$id§7 → §f${fmt(p.x, p.y, p.z)} §8(${p.world})$unlock$label"
            ))
        }
    }

    // ─── villager spawn / delete ──────────────────────────────────────────

    private fun spawnVillager(source: CommandSourceStack): Int {
        val level: ServerLevel = source.level
        val pos = source.position
        // Kill any nearby existing tagged villager so re-spawn is idempotent.
        val killed = killTaggedNear(level, pos.x, pos.y, pos.z, radius = 4.0)

        val villager: Villager = EntityType.VILLAGER.create(level) ?: run {
            source.sendSystemMessage(Component.literal("§c[GymTP] Failed to create villager entity"))
            return 0
        }
        villager.moveTo(pos.x, pos.y, pos.z, source.rotation.y, 0f)
        villager.addTag(BridgeTags.GYM_TP_NPC)
        villager.isInvulnerable = true
        villager.setPersistenceRequired()
        villager.isSilent = true
        villager.isNoAi = true
        villager.villagerData = VillagerData(VillagerType.PLAINS, VillagerProfession.LIBRARIAN, 5)
        villager.offers.clear()
        villager.customName = Component.literal("Gym Guide")
            .setStyle(Style.EMPTY.withColor(0x55FF55).withBold(true).withItalic(false))
        villager.isCustomNameVisible = true

        if (!level.addFreshEntity(villager)) {
            source.sendSystemMessage(Component.literal("§c[GymTP] Failed to add villager to level"))
            return 0
        }

        val killedNote = if (killed > 0) " §7(replaced $killed existing)" else ""
        source.sendSystemMessage(Component.literal(
            "§a[GymTP] Spawned Gym Guide at ${fmt(pos.x, pos.y, pos.z)}$killedNote"
        ))
        CobblemonBridge.logger.info(
            "gym-tp: spawned villager at {} by {}", fmt(pos.x, pos.y, pos.z),
            source.textName,
        )
        return 1
    }

    private fun deleteVillagers(source: CommandSourceStack): Int {
        val level = source.level
        val pos = source.position
        val killed = killTaggedNear(level, pos.x, pos.y, pos.z, radius = 32.0)
        source.sendSystemMessage(Component.literal("§a[GymTP] Removed $killed tagged villager(s)"))
        return killed
    }

    /** Discard every tagged villager inside [radius] of (x,y,z). Returns count removed. */
    private fun killTaggedNear(level: ServerLevel, x: Double, y: Double, z: Double, radius: Double): Int {
        val box = net.minecraft.world.phys.AABB(
            x - radius, y - radius, z - radius,
            x + radius, y + radius, z + radius,
        )
        val matches = level.getEntitiesOfClass(net.minecraft.world.entity.npc.Villager::class.java, box) { v ->
            v.tags.contains(BridgeTags.GYM_TP_NPC)
        }
        for (e in matches) e.discard()
        return matches.size
    }

    private fun fmt(x: Double, y: Double, z: Double): String =
        "${"%.1f".format(x)}, ${"%.1f".format(y)}, ${"%.1f".format(z)}"
}
