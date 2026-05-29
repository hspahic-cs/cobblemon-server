package com.cobblemonbridge.commands

import com.cobblemonbridge.CobblemonBridge
import com.cobblemonbridge.gymtp.WarpPos
import com.cobblemonbridge.spawn.SpawnStore
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.DimensionArgument
import net.minecraft.commands.arguments.coordinates.RotationArgument
import net.minecraft.commands.arguments.coordinates.Vec3Argument
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.neoforged.fml.loading.FMLPaths

/**
 * Global `/spawn` (any player) + `/setspawn` (op level 2). Overrides neoessentials' per-world
 * spawn behavior with a single world-agnostic teleport target persisted in
 * `config/cobblemon-bridge/runtime/spawn.json`.
 *
 * `/spawn` registers as a top-level command literal. If neoessentials also registers `/spawn`,
 * Brigadier merges the dispatcher trees — last-registered `.executes` at a given node wins.
 * Bridge's `RegisterCommandsEvent` listener runs after neoessentials' (modid alphabetical:
 * `cobblemon_bridge` < `neoessentials`, but NeoForge fires the event in mod-init order, not
 * alphabetical). If neoessentials still beats us, escalate to a higher-priority subscriber.
 */
object SpawnCommands {

    private const val OP_LEVEL = 2

    @Volatile
    private var store: SpawnStore? = null

    fun init() {
        val file = FMLPaths.CONFIGDIR.get()
            .resolve("cobblemon-bridge")
            .resolve("runtime")
            .resolve("spawn.json")
        store = SpawnStore.load(file)
        CobblemonBridge.logger.info(
            "spawn: loaded {} (file: {})",
            store?.get()?.let { "${it.x.toInt()}, ${it.y.toInt()}, ${it.z.toInt()} in ${it.world}" } ?: "no spawn set",
            file,
        )
    }

    private fun store(): SpawnStore = store
        ?: error("SpawnCommands not initialized — CobblemonBridge should call SpawnCommands.init()")

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        // /spawn — any player.
        dispatcher.register(
            Commands.literal("spawn")
                .executes { ctx -> teleportToSpawn(ctx.source); 1 }
        )

        // /setspawn — op level 2.
        dispatcher.register(
            Commands.literal("setspawn")
                .requires { it.hasPermission(OP_LEVEL) }
                .executes { ctx -> captureSpawnFromSender(ctx.source); 1 }
                .then(Commands.argument("pos", Vec3Argument.vec3(true))
                    .executes { ctx -> captureSpawnExplicit(ctx, includeRotation = false, includeDim = false); 1 }
                    .then(Commands.argument("rot", RotationArgument.rotation())
                        .executes { ctx -> captureSpawnExplicit(ctx, includeRotation = true, includeDim = false); 1 }
                        .then(Commands.argument("dimension", DimensionArgument.dimension())
                            .executes { ctx -> captureSpawnExplicit(ctx, includeRotation = true, includeDim = true); 1 }
                        )
                    )
                )
        )

        // /clearspawn — op level 2. Reverts to no global spawn (any /spawn call after this
        // tells the player it's unset).
        dispatcher.register(
            Commands.literal("clearspawn")
                .requires { it.hasPermission(OP_LEVEL) }
                .executes { ctx -> clearSpawn(ctx.source); 1 }
        )
    }

    // ─── handlers ──────────────────────────────────────────────────────────

    private fun teleportToSpawn(source: CommandSourceStack) {
        val player = source.player ?: run {
            source.sendSystemMessage(Component.literal("§c/spawn must be run by a player."))
            return
        }
        val target = store().get() ?: run {
            player.sendSystemMessage(Component.literal("§c[Spawn] No global spawn set. Ask an op to run /setspawn."))
            return
        }
        val rl = ResourceLocation.tryParse(target.world)
        if (rl == null) {
            player.sendSystemMessage(Component.literal("§c[Spawn] Invalid world id: ${target.world}"))
            return
        }
        val key = ResourceKey.create(Registries.DIMENSION, rl)
        val level: ServerLevel? = player.server.getLevel(key)
        if (level == null) {
            player.sendSystemMessage(Component.literal("§c[Spawn] Dimension not loaded: ${target.world}"))
            return
        }
        player.teleportTo(level, target.x, target.y, target.z, target.yaw, target.pitch)
        player.sendSystemMessage(Component.literal("§a[Spawn] Welcome back."))
    }

    private fun captureSpawnFromSender(source: CommandSourceStack) {
        val pos = source.position
        val rot = source.rotation
        val dim = source.level.dimension().location().toString()
        applySpawn(source, WarpPos(pos.x, pos.y, pos.z, dim, rot.y, rot.x), captured = true)
    }

    private fun captureSpawnExplicit(
        ctx: CommandContext<CommandSourceStack>,
        includeRotation: Boolean,
        includeDim: Boolean,
    ) {
        val source = ctx.source
        val coord = Vec3Argument.getVec3(ctx, "pos")
        val (yaw, pitch) = if (includeRotation) {
            val r = RotationArgument.getRotation(ctx, "rot").getRotation(source)
            r.y to r.x
        } else 0f to 0f
        val dim = if (includeDim) {
            DimensionArgument.getDimension(ctx, "dimension").dimension().location().toString()
        } else source.level.dimension().location().toString()
        applySpawn(source, WarpPos(coord.x, coord.y, coord.z, dim, yaw, pitch), captured = false)
    }

    private fun applySpawn(source: CommandSourceStack, pos: WarpPos, captured: Boolean) {
        store().set(pos)
        val verb = if (captured) "Captured" else "Set"
        source.sendSystemMessage(Component.literal(
            "§a[Spawn] $verb global spawn → ${"%.1f".format(pos.x)}, ${"%.1f".format(pos.y)}, ${"%.1f".format(pos.z)} §8(${pos.world})"
        ))
    }

    private fun clearSpawn(source: CommandSourceStack) {
        store().clear()
        source.sendSystemMessage(Component.literal("§a[Spawn] Cleared global spawn."))
    }
}
