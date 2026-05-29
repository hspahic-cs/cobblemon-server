package com.cobblemonbridge.worldrules

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemonbridge.CobblemonBridge
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.Mob
import net.minecraft.world.level.GameType
import net.minecraft.world.level.Level
import net.neoforged.bus.api.EventPriority
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent
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
 * Two dimension classifications:
 *  - [ALLOWED_DIMS]: progression worlds. No restrictions. Hardcoded to vanilla
 *    overworld/nether/end. Tweak here if the server adds another progression dim.
 *  - "Locked" (anything outside [ALLOWED_DIMS]): block edits, force Adventure,
 *    block Pokemon/trainer natural spawns.
 *  - [NO_MOB_NAMESPACES]: a stricter subset of locked. Worlds matching one of
 *    these dimension namespaces additionally block ALL natural mob spawns
 *    (hostile, passive, ambient — anything that's a [Mob]). The point is to
 *    remove every exploration incentive from the static showcase worlds (spawn,
 *    elite4 arenas) so players don't wander them looking for resources or fights.
 *    Matched by namespace prefix so adding new arenas (e.g. multiworld:arena3)
 *    requires no code change.
 */
object WorldRulesHook {

    private val ALLOWED_DIMS: Set<String> = setOf(
        "minecraft:overworld",
        "minecraft:the_nether",
        "minecraft:the_end",
    )

    /** Dim namespaces that get the stricter "no mobs at all" treatment. */
    private val NO_MOB_NAMESPACES: Set<String> = setOf(
        "multiworld",
    )

    private val RCT_TRAINER_ID: ResourceLocation =
        ResourceLocation.fromNamespaceAndPath("rctmod", "trainer")

    private const val OP_LEVEL: Int = 2

    /** Saved pre-entry gamemode per player. Cleared when restored. */
    private val savedGameType: ConcurrentHashMap<UUID, GameType> = ConcurrentHashMap()

    private fun isLocked(level: Level): Boolean =
        level.dimension().location().toString() !in ALLOWED_DIMS

    private fun isLocked(dimLocation: String): Boolean = dimLocation !in ALLOWED_DIMS

    private fun isNoMobDim(level: Level): Boolean =
        level.dimension().location().namespace in NO_MOB_NAMESPACES

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
     * True if [entity] is something this hook should veto from the given [level].
     * In a locked dim we veto Pokemon + RCT trainers; in a NO_MOB dim we additionally
     * veto every other [Mob] (hostile, passive, ambient — anything that's a Mob
     * in the MC class hierarchy). Non-Mob entities like item frames, paintings,
     * boats, XP orbs, etc. are never targeted.
     */
    private fun shouldVetoSpawn(entity: net.minecraft.world.entity.Entity, level: Level): Boolean {
        if (entity is PokemonEntity) return true
        if (EntityType.getKey(entity.type) == RCT_TRAINER_ID) return true
        if (isNoMobDim(level) && entity is Mob) return true
        return false
    }

    /**
     * Cancel natural spawns of vetoed entities in locked worlds. Run at HIGH (not
     * HIGHEST) so other mods can still influence pre-spawn checks; we just want to be the last
     * vetoer before vanilla allows the spawn.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    fun onFinalizeSpawn(event: FinalizeSpawnEvent) {
        val level = event.level.level
        if (!isLocked(level)) return
        if (!shouldVetoSpawn(event.entity, level)) return
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

    /**
     * Belt-and-suspenders: some Pokemon/trainer code paths bypass FinalizeSpawn (e.g. direct
     * `level.addFreshEntity(...)` without going through `MobSpawnType` finalisation). Veto at
     * EntityJoinLevel for entities matching our criteria — but ONLY when the level is locked
     * AND the entity has no NBT marker indicating a command spawn (we use the
     * `cobblemon_bridge.gym_id.*` / `gym_tp_npc` tag presence as a hint that an op put it there).
     */
    // ─── Tagged-entity invulnerability ───────────────────────────────────────

    /**
     * Make admin-tagged entities (gym leaders, gym-TP villagers, anything
     * stamped with a `cobblemon_bridge.*` tag) invulnerable to non-op players
     * in [NO_MOB_NAMESPACES] dims. Ops can still damage them for cleanup —
     * same out we have everywhere else in this hook.
     *
     * Pokemon battles aren't affected: they're driven by Showdown, not the
     * vanilla damage path.
     */
    @SubscribeEvent
    fun onIncomingDamage(event: LivingIncomingDamageEvent) {
        val target = event.entity
        if (!isNoMobDim(target.level())) return
        if (target.tags.none { it.startsWith("cobblemon_bridge.") }) return
        // Only block damage that originates from a non-op player. Lava/fall/
        // suffocation/etc. still apply (rare in arenas, but cleaner contract).
        val attacker = event.source.entity as? ServerPlayer ?: return
        if (isOp(attacker)) return
        event.isCanceled = true
    }

    @SubscribeEvent
    fun onEntityJoin(event: EntityJoinLevelEvent) {
        val level = event.level
        if (!isLocked(level)) return
        val entity = event.entity
        if (!shouldVetoSpawn(entity, level)) return
        // If the entity has any cobblemon_bridge.* tag (the ones our admin commands stamp at
        // spawn time), it's an op-summoned entity — let it through.
        if (entity.tags.any { it.startsWith("cobblemon_bridge.") }) return
        event.isCanceled = true
        CobblemonBridge.logger.debug(
            "worldrules: blocked {} from {} (no admin tag)",
            entity.type, level.dimension().location(),
        )
    }
}
