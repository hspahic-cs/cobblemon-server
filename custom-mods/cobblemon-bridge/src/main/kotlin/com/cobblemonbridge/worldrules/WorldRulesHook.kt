package com.cobblemonbridge.worldrules

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.Mob
import net.minecraft.world.level.GameType
import net.minecraft.world.level.Level
import net.neoforged.bus.api.EventPriority
import net.neoforged.bus.api.SubscribeEvent
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
     * Cancel natural spawns of vetoed entities. Run at HIGH (not HIGHEST) so other mods can
     * still influence pre-spawn checks; we just want to be the last vetoer before vanilla
     * allows the spawn.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    fun onFinalizeSpawn(event: FinalizeSpawnEvent) {
        // Op-initiated spawns (commands, spawn eggs, /summon, dispensers) always pass through —
        // regardless of dimension or mob category. This is checked once at the top so all rules
        // below honour it.
        when (event.spawnType) {
            net.minecraft.world.entity.MobSpawnType.COMMAND,
            net.minecraft.world.entity.MobSpawnType.SPAWN_EGG,
            net.minecraft.world.entity.MobSpawnType.MOB_SUMMONED,
            net.minecraft.world.entity.MobSpawnType.DISPENSER -> return
            else -> Unit
        }

        val level = event.level.level
        val entity = event.entity

        // Rule 1 — non-progression dimensions: no Pokémon or RCT trainer natural spawns.
        if (isLocked(level)) {
            val isPokeOrTrainer = entity is PokemonEntity || EntityType.getKey(entity.type) == RCT_TRAINER_ID
            if (isPokeOrTrainer) {
                event.isSpawnCancelled = true
                return
            }
        }

        // Rule 2 — NO_MOB dims (multiworld:*): block every natural Mob spawn (hostile,
        // passive, ambient). Removes any incentive to wander the static showcase worlds
        // for resources or fights.
        if (isNoMobDim(level) && entity is Mob) {
            event.isSpawnCancelled = true
            return
        }

        // Rule 3 — globally: no vanilla hostile-mob natural spawns. We play on Easy difficulty
        // but with no hostile spawning so the gameplay loop centres on Pokémon. Peaceful mobs
        // (CREATURE / AMBIENT / WATER_* categories) still spawn normally. Op-initiated spawns
        // were already let through above.
        if (entity.type.category == net.minecraft.world.entity.MobCategory.MONSTER) {
            event.isSpawnCancelled = true
        }
    }

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

    // Note: a previous belt-and-suspenders `onEntityJoin` hook that vetoed at
    // EntityJoinLevelEvent was removed — it over-blocked because the
    // `/function server:gym/spawn_<N>` mcfunctions tag the trainer AFTER `rctmod trainer
    // summon_persistent`, so at EntityJoin time the `cobblemon_bridge.*` tag isn't on yet and
    // the function's spawn was being canceled before its own tag command could run. The
    // FinalizeSpawnEvent above already distinguishes COMMAND/SPAWN_EGG/MOB_SUMMONED from
    // natural spawn types and is the correct gate. If a future natural spawn path bypasses
    // FinalizeSpawn entirely, re-introduce a more targeted check here.
}
