package com.cobblemonbridge.commands

import com.cobblemonbridge.CobblemonBridge
import com.cobblemonbridge.quests.QuestAdvancements
import com.cobblemonbridge.wild.WildStore
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.Level
import net.minecraft.world.level.levelgen.Heightmap
import net.neoforged.fml.loading.FMLPaths
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * `/wild` — teleport to a random surface location in the overworld (wilderness).
 *
 * Picks a random X/Z within [radius] blocks of ([centerX], [centerZ]), finds the
 * surface height, and teleports the player there. Retries up to 5 times to avoid
 * landing in water/lava. Per-player cooldown resets on server restart.
 *
 * Admin variant: `/wild <player>` (permission level 2+) can target any online
 * player. Same per-player cooldown applies. Useful for command blocks / portals.
 */
object WildCommand {

    /**
     * Defaults — only used when `config/cobblemon-bridge/runtime/wild.json` is missing or
     * malformed. Once [init] runs, [store] is the source of truth and these vars track its
     * current values for hot reads (no IO on the teleport hot path).
     */
    var centerX: Int = 350
    var centerZ: Int = -700
    var radius: Int = 250
    var cooldownSeconds: Int = 60

    @Volatile
    private var store: WildStore? = null

    private val cooldowns = ConcurrentHashMap<UUID, Long>()

    /** Called from CobblemonBridge.onServerStarting. Loads runtime/wild.json and applies it. */
    fun init() {
        val file = FMLPaths.CONFIGDIR.get()
            .resolve("cobblemon-bridge")
            .resolve("runtime")
            .resolve("wild.json")
        val s = WildStore.load(file)
        store = s
        val cfg = s.get()
        centerX = cfg.centerX
        centerZ = cfg.centerZ
        radius = cfg.radius
        cooldownSeconds = cfg.cooldownSeconds
        CobblemonBridge.logger.info(
            "wild: loaded center=({}, {}) radius={} cooldown={}s (file: {})",
            cfg.centerX, cfg.centerZ, cfg.radius, cfg.cooldownSeconds, file,
        )
    }

    private fun store(): WildStore = store
        ?: error("WildCommand not initialized — CobblemonBridge should call WildCommand.init()")

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("wild")
                .executes { ctx ->
                    val player = ctx.source.player ?: return@executes 0
                    handleWild(player, enforceCooldown = true)
                    1
                }
                .then(Commands.argument("player", EntityArgument.player())
                    .requires { it.hasPermission(2) }
                    .executes { ctx ->
                        val target = EntityArgument.getPlayer(ctx, "player")
                        handleWild(target, enforceCooldown = true)
                        ctx.source.sendSystemMessage(
                            Component.literal("§a[Wild] §fSent ${target.gameProfile.name} to the wilderness.")
                        )
                        1
                    }
                )
                .then(Commands.literal("admin")
                    .requires { it.hasPermission(2) }
                    .then(Commands.literal("show")
                        .executes { ctx -> adminShow(ctx.source); 1 }
                    )
                    .then(Commands.literal("setcenter")
                        // /wild admin setcenter → uses sender's X/Z (ignores Y; surface-find on teleport)
                        .executes { ctx -> adminSetCenterFromSender(ctx.source); 1 }
                        // /wild admin setcenter <x> <z> → explicit
                        .then(Commands.argument("x", IntegerArgumentType.integer())
                            .then(Commands.argument("z", IntegerArgumentType.integer())
                                .executes { ctx ->
                                    adminSetCenter(
                                        ctx.source,
                                        IntegerArgumentType.getInteger(ctx, "x"),
                                        IntegerArgumentType.getInteger(ctx, "z"),
                                    ); 1
                                }
                            )
                        )
                    )
                    .then(Commands.literal("setradius")
                        .then(Commands.argument("blocks", IntegerArgumentType.integer(16, 50000))
                            .executes { ctx ->
                                adminSetRadius(ctx.source, IntegerArgumentType.getInteger(ctx, "blocks")); 1
                            }
                        )
                    )
                    .then(Commands.literal("setcooldown")
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(0, 3600))
                            .executes { ctx ->
                                adminSetCooldown(ctx.source, IntegerArgumentType.getInteger(ctx, "seconds")); 1
                            }
                        )
                    )
                )
        )
    }

    private fun adminShow(source: CommandSourceStack) {
        source.sendSystemMessage(Component.literal("§e[Wild] §fCurrent config:"))
        source.sendSystemMessage(Component.literal("§7  center: §f($centerX, $centerZ)"))
        source.sendSystemMessage(Component.literal("§7  radius: §f$radius §8blocks (${radius * 2}×${radius * 2} area)"))
        source.sendSystemMessage(Component.literal("§7  cooldown: §f${cooldownSeconds}s"))
    }

    private fun adminSetCenterFromSender(source: CommandSourceStack) {
        val pos = source.position
        adminSetCenter(source, pos.x.toInt(), pos.z.toInt())
    }

    private fun adminSetCenter(source: CommandSourceStack, x: Int, z: Int) {
        store().update { it.copy(centerX = x, centerZ = z) }
        centerX = x; centerZ = z
        source.sendSystemMessage(Component.literal("§a[Wild] Center → §f($x, $z)"))
    }

    private fun adminSetRadius(source: CommandSourceStack, r: Int) {
        store().update { it.copy(radius = r) }
        radius = r
        source.sendSystemMessage(Component.literal("§a[Wild] Radius → §f$r §8(${r * 2}×${r * 2} area)"))
    }

    private fun adminSetCooldown(source: CommandSourceStack, secs: Int) {
        store().update { it.copy(cooldownSeconds = secs) }
        cooldownSeconds = secs
        source.sendSystemMessage(Component.literal("§a[Wild] Cooldown → §f${secs}s"))
    }

    private fun handleWild(player: ServerPlayer, enforceCooldown: Boolean) {
        // ── Cooldown check (skipped for admin usage) ────────────────────────
        if (enforceCooldown) {
            val now = System.currentTimeMillis()
            val lastUsed = cooldowns[player.uuid]
            if (lastUsed != null) {
                val remaining = cooldownSeconds - ((now - lastUsed) / 1000).toInt()
                if (remaining > 0) {
                    player.sendSystemMessage(Component.literal("§c[Wild] Cooldown: ${remaining}s remaining."))
                    return
                }
            }
        }

        val overworld = player.server.getLevel(Level.OVERWORLD) ?: run {
            player.sendSystemMessage(Component.literal("§c[Wild] Wilderness world not available."))
            return
        }

        // ── Find a safe landing spot (up to 5 attempts) ────────────────────
        // Force-load the chunk before reading the heightmap so ungenerated chunks
        // don't return Y=0 (which buries the player underground).
        var bestX = 0; var bestZ = 0; var bestY = 0; var safe = false
        for (attempt in 1..5) {
            val x = centerX + Random.nextInt(-radius, radius + 1)
            val z = centerZ + Random.nextInt(-radius, radius + 1)
            overworld.getChunk(x shr 4, z shr 4)
            val y = overworld.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z)
            bestX = x; bestZ = z; bestY = y

            @Suppress("DEPRECATION")
            val below = overworld.getBlockState(BlockPos(x, y - 1, z))
            if (!below.liquid()) {
                safe = true
                break
            }
        }

        // ── Teleport ────────────────────────────────────────────────────────
        player.teleportTo(overworld, bestX + 0.5, bestY.toDouble(), bestZ + 0.5, player.yRot, player.xRot)
        cooldowns[player.uuid] = System.currentTimeMillis()

        val warn = if (!safe) " §7(landed in water)" else ""
        player.sendSystemMessage(
            Component.literal("§a[Wild] §fTeleported to the wilderness! §7($bestX, $bestY, $bestZ)$warn")
        )
        QuestAdvancements.award(player, "server:use_wild")
        CobblemonBridge.logger.info("/wild: {} → ({}, {}, {})", player.gameProfile.name, bestX, bestY, bestZ)
    }
}
