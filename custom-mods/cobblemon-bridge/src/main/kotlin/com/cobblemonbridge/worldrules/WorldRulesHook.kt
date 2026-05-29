package com.cobblemonbridge.worldrules

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemonbridge.CobblemonBridge
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.GameType
import net.minecraft.world.level.Level
import net.neoforged.bus.api.EventPriority
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.level.BlockEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Restricts non-progression worlds to read-only sightseeing:
 *
 *   - Pokemon + RCT trainer natural spawns are blocked outside [ALLOWED_DIMS]. Command-summoned
 *     entities still go through (ops setting up arenas, `rctmod trainer summon_persistent`, etc.).
 *   - Non-op players entering a non-allowed dimension are forced into [GameType.ADVENTURE] (their
 *     prior gamemode is captured and restored when they exit to an allowed dim or log out). Block
 *     break/place events are also cancelled defensively, in case a mod re-promotes them to
 *     survival mid-visit.
 *   - Ops (permission level 2+) bypass all restrictions. They can build, fight, summon, and use
 *     any gamemode in any dimension.
 *
 * [ALLOWED_DIMS] is hardcoded to the three vanilla overworld/nether/end. Tweak there if the
 * server adds an additional progression dimension.
 */
object WorldRulesHook {

    private val ALLOWED_DIMS: Set<String> = setOf(
        "minecraft:overworld",
        "minecraft:the_nether",
        "minecraft:the_end",
    )

    private val RCT_TRAINER_ID: ResourceLocation =
        ResourceLocation.fromNamespaceAndPath("rctmod", "trainer")

    private const val OP_LEVEL: Int = 2

    /** Saved pre-entry gamemode per player. Cleared when restored. */
    private val savedGameType: ConcurrentHashMap<UUID, GameType> = ConcurrentHashMap()

    private fun isLocked(level: Level): Boolean =
        level.dimension().location().toString() !in ALLOWED_DIMS

    private fun isLocked(dimLocation: String): Boolean = dimLocation !in ALLOWED_DIMS

    private fun isOp(player: ServerPlayer): Boolean = player.hasPermissions(OP_LEVEL)

    // ─── Player dimension changes ────────────────────────────────────────────

    @SubscribeEvent
    fun onPlayerLoggedIn(event: PlayerEvent.PlayerLoggedInEvent) {
        val player = event.entity as? ServerPlayer ?: return
        if (isOp(player)) return
        if (isLocked(player.level())) applyLock(player)
    }

    @SubscribeEvent
    fun onPlayerLoggedOut(event: PlayerEvent.PlayerLoggedOutEvent) {
        val uuid = (event.entity as? ServerPlayer)?.uuid ?: return
        // Don't restore on logout — just drop the saved entry. Next login is handled by
        // onPlayerLoggedIn, which checks the dim they actually rejoin in.
        savedGameType.remove(uuid)
    }

    @SubscribeEvent
    fun onChangedDimension(event: PlayerEvent.PlayerChangedDimensionEvent) {
        val player = event.entity as? ServerPlayer ?: return
        if (isOp(player)) return
        val toLocked = isLocked(event.to.location().toString())
        val fromLocked = isLocked(event.from.location().toString())
        when {
            !fromLocked && toLocked -> applyLock(player)
            fromLocked && !toLocked -> restoreLock(player)
            else -> Unit
        }
    }

    private fun applyLock(player: ServerPlayer) {
        if (savedGameType.containsKey(player.uuid)) return  // already locked, don't re-stack
        savedGameType[player.uuid] = player.gameMode.gameModeForPlayer
        player.setGameMode(GameType.ADVENTURE)
        player.sendSystemMessage(Component.literal(
            "§7[Server] This world is read-only. Block edits and natural spawns are disabled."
        ))
    }

    private fun restoreLock(player: ServerPlayer) {
        val prior = savedGameType.remove(player.uuid) ?: return
        player.setGameMode(prior)
    }

    // ─── Block edits ─────────────────────────────────────────────────────────

    @SubscribeEvent
    fun onBlockBreak(event: BlockEvent.BreakEvent) {
        val player = event.player as? ServerPlayer ?: return
        if (isOp(player)) return
        if (!isLocked(event.level as Level)) return
        event.isCanceled = true
        player.sendSystemMessage(Component.literal("§c[Server] You can't edit this world."))
    }

    @SubscribeEvent
    fun onBlockPlace(event: BlockEvent.EntityPlaceEvent) {
        val player = event.entity as? ServerPlayer ?: return
        if (isOp(player)) return
        if (!isLocked(event.level as Level)) return
        event.isCanceled = true
        player.sendSystemMessage(Component.literal("§c[Server] You can't edit this world."))
    }

    // ─── Spawn restrictions ──────────────────────────────────────────────────

    /**
     * Cancel natural spawns of Pokemon + RCT trainers in non-allowed worlds. Run at HIGH (not
     * HIGHEST) so other mods can still influence pre-spawn checks; we just want to be the last
     * vetoer before vanilla allows the spawn.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    fun onFinalizeSpawn(event: FinalizeSpawnEvent) {
        val level = event.level.level
        if (!isLocked(level)) return
        val entity = event.entity
        val isTarget = entity is PokemonEntity || EntityType.getKey(entity.type) == RCT_TRAINER_ID
        if (!isTarget) return
        // MobSpawnType.NATURAL, CHUNK_GENERATION, STRUCTURE, JOCKEY, REINFORCEMENT, BREEDING,
        // EVENT, PATROL — anything that isn't explicit command/spawn-egg/summoned. We only allow
        // op-initiated spawns (COMMAND, SPAWN_EGG, MOB_SUMMONED).
        when (event.spawnType) {
            net.minecraft.world.entity.MobSpawnType.COMMAND,
            net.minecraft.world.entity.MobSpawnType.SPAWN_EGG,
            net.minecraft.world.entity.MobSpawnType.MOB_SUMMONED,
            net.minecraft.world.entity.MobSpawnType.DISPENSER -> return
            else -> {
                event.isSpawnCancelled = true
            }
        }
    }

    // Note: a previous belt-and-suspenders `onEntityJoin` hook that vetoed at
    // EntityJoinLevelEvent was removed — it over-blocked because the
    // `/function server:gym/spawn_<N>` mcfunctions tag the trainer AFTER `rctmod trainer
    // summon_persistent`, so at EntityJoin time the `cobblemon_bridge.*` tag isn't on yet and
    // the function's spawn was being canceled before its own tag command could run. The
    // FinalizeSpawnEvent above already distinguishes COMMAND/SPAWN_EGG/MOB_SUMMONED from
    // natural spawn types and is the correct gate. If a future natural spawn path bypasses
    // FinalizeSpawn entirely, re-introduce a more targeted check here.
}
