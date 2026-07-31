package com.cobblemonroguelite.arena

import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.Priority
import com.cobblemonroguelite.run.RunSettings
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.MobSpawnType
import net.neoforged.bus.api.EventPriority
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean

private val log = LoggerFactory.getLogger("cobblemon_roguelite/arena")

/**
 * Nothing wild spawns in an arena except what a wave puts there.
 *
 * ### Why this is an event cancel and not configuration
 *
 * The arena's `dimension_type` kills vanilla *hostile* spawning (`monster_spawn_light_level: 0`
 * and `monster_spawn_block_light_limit: 0`) — but that is only the monster half of the vanilla
 * spawner, and it turned out not to be enough. The dev playtest found a sheep in the arena, and
 * ender pearls, string, feathers and raw chicken in the run-exit stash quarantine: passive and
 * ambient mobs ignore the light rules entirely, and structure/chunk-gen placed mobs never consult
 * them. So this object carries **two** vetoes, one per spawn pipeline:
 *
 * 1. `CobblemonEvents.ENTITY_SPAWN` for Cobblemon's player-driven spawner, which does not consult
 *    dimension type at all — a player standing in an arena is exactly the trigger it wants.
 * 2. NeoForge's [FinalizeSpawnEvent] for everything vanilla-and-modded that spawns *implicitly* —
 *    see [suppressFinalizeSpawn] for exactly which categories that covers and which it cannot.
 *
 * The one lever deliberately **not** pulled is `doMobSpawning`: gamerules are per-server, not
 * per-dimension, so flipping it to protect the arena would sterilise the overworld too.
 *
 * Cobblemon has **no global dimension blacklist**. `SpawningCondition` carries a per-spawn-detail
 * `dimensions` list, which is an *allowlist* on each individual spawn entry — the wrong direction
 * entirely: using it would mean editing every spawn file Cobblemon and every addon ship, and being
 * wrong again the next time one is added. `CobblemonEvents.ENTITY_SPAWN` is a `CancelableObservable`,
 * so one subscription covers every spawn from every source, including ones that do not exist yet.
 *
 * ### It reads the level from the spawn position, not the entity
 *
 * `SpawnablePosition.getWorld()` is a `ServerLevel` and is the place the spawn was *computed for*.
 * The entity's own level is the same thing in practice, but only after construction, and this event
 * is the point at which the spawn can still be refused.
 *
 * ### Whole-dimension or per-box, decided by the layout
 *
 * [ArenaLayout.isArenaSpace] draws the line, and the two layouts draw it differently on purpose. On
 * the generated grid the whole dimension is ours and nothing should ever spawn in it. With
 * [ArenaConfig.fixedArenas] the arenas sit in a world that has other things in it, so a
 * dimension-wide cancel would blank the Cobblemon spawner for that entire world — which an owner
 * pointing at a hand-built arena in their overworld would experience as "the mod broke spawning".
 */
object ArenaSpawnSuppressor {

    private val registered = AtomicBoolean(false)

    /**
     * Subscribe once. Guarded because the observable has no unsubscribe-by-owner and a second
     * subscription would double every cancel decision — harmless in effect, but it would also double
     * the per-spawn cost on every spawn on the server, which is the hottest path this mod touches.
     */
    fun register() {
        if (!registered.compareAndSet(false, true)) return
        // LOWEST so anything that wants to inspect or modify a spawn still sees it. We are the veto,
        // and a veto that runs first hides the spawn from every other subscriber on the server.
        CobblemonEvents.ENTITY_SPAWN.subscribe(Priority.LOWEST) { event ->
            val level = event.spawnablePosition.world
            // Read, not built: RunSettings caches the layout and rebuilds it on config change, and
            // this runs on every spawn on the server.
            val layout = RunSettings.arenaLayout
            if (layout.isArenaSpace(level.dimension().location(), event.spawnablePosition.position)) {
                event.cancel()
            }
        }
        // LOWEST for the same reason as above, and `receiveCanceled = true` for a sneakier one: some
        // other mod cancelling this event only suppresses the mob's *initialization* — the entity
        // still spawns (that is FinalizeSpawnEvent's documented split between setCanceled and
        // setSpawnCancelled). If we skipped canceled events, a mob whose finalize another mod vetoed
        // would sail into the arena untouched.
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, ::suppressFinalizeSpawn)
        NeoForge.EVENT_BUS.addListener(::purgeDiskLoaded)
        NeoForge.EVENT_BUS.addListener(::suppressDeathLoot)
        log.debug("roguelite: arena spawn suppression active")
    }

    /**
     * No death loot in arena space: a KO'd wave Pokémon drops nothing.
     *
     * Cobblemon species carry vanilla-style drop tables (the playtest's exit quarantine was full of
     * them — feathers, string, raw chicken, a Dragon Fang), and PokéRogue KOs drop nothing: the
     * wave's payout is the reward pick and the credits, not a floor of part-loot. The stacks were
     * already being voided at exit by the stash quarantine, so this closes clutter rather than a
     * leak — but it closes it at the source, where the player never sees items the run was always
     * going to confiscate.
     *
     * Experience orbs are left alone: Cobblemon grants battle EXP directly (§2.21), and any stray
     * vanilla orb is harmless — collecting it touches the player's real XP bar, which the run does
     * not manage. What this does NOT cover: mods that grant server money on Pokémon KO/pickup
     * ("found money" features) — that is an economy-config question, not an entity event.
     */
    private fun suppressDeathLoot(event: net.neoforged.neoforge.event.entity.living.LivingDropsEvent) {
        val entity = event.entity
        val level = entity.level()
        if (level.isClientSide) return
        val layout = RunSettings.arenaLayout
        if (layout.isArenaSpace(level.dimension().location(), entity.blockPosition())) {
            event.isCanceled = true
        }
    }

    /**
     * Discard any non-player entity that loads back in **from disk** inside arena space.
     *
     * The vetoes above are prevention; this is the amnesty for what got in before they shipped, and
     * for the one gap prevention cannot close: entities already serialised into arena chunk data.
     * The playtest's sheep survived a whole restart that way, and [ArenaStamper]'s box sweep cannot
     * reach it — entities load a beat *behind* their chunks in 1.21, so a sweep at stamp time runs
     * over a box whose leftovers do not exist yet (the same race the bridge's wave-trainer cleanup
     * hit, fixed the same way).
     *
     * Disk-loaded only, and that filter is load-bearing: everything a live wave adds — the wild
     * opponent, the player's own sent-out Pokémon, thrown balls, the trainer NPC — joins the level
     * *fresh* and must not be touched. Nothing legitimate is ever meant to persist in arena chunk
     * data across an unload; an arena is stamped scenery plus a battle in progress.
     */
    private fun purgeDiskLoaded(event: net.neoforged.neoforge.event.entity.EntityJoinLevelEvent) {
        if (event.level.isClientSide || !event.loadedFromDisk()) return
        val entity = event.entity
        if (entity is net.minecraft.world.entity.player.Player) return
        val layout = RunSettings.arenaLayout
        if (layout.isArenaSpace(event.level.dimension().location(), entity.blockPosition())) {
            entity.discard()
            log.info(
                "roguelite: discarded {} that loaded from disk inside an arena at {} — arena chunks " +
                    "should never hold saved entities",
                entity.type.description.string, entity.blockPosition().toShortString(),
            )
        }
    }

    /**
     * The spawn types a mob may arrive in an arena with. Everything else is refused.
     *
     * This is an allowlist of *deliberate hands-on acts* — `/summon` from an operator debugging a
     * wave, a spawn egg, a bucket of fish, a dispenser someone is experimenting with — because an
     * explicit act has a person attached who can see what they did. It is **not** how the mode's own
     * opponents get in: wave Pokémon go through `Pokemon.sendOut` ([com.cobblemonroguelite.battle.RunWildBattle]),
     * which calls the deprecated `Mob.finalizeSpawn` directly and therefore never fires this event at
     * all — NeoForge only posts it from `EventHooks.finalizeMobSpawn`, which is patched into the
     * *implicit* spawn paths. Our spawns are structurally unreachable by this veto, which is exactly
     * why a dimension-wide cancel is safe.
     */
    private val EXPLICIT_SPAWN_TYPES = setOf(
        MobSpawnType.COMMAND,
        MobSpawnType.SPAWN_EGG,
        MobSpawnType.BUCKET,
        MobSpawnType.DISPENSER,
    )

    /**
     * Refuses every implicit mob spawn in arena space. Covered, because their spawners all route
     * through `EventHooks.finalizeMobSpawn`: natural surface spawns (`NATURAL` — the playtest sheep,
     * and phantoms, whose custom spawner also stamps `NATURAL`), chunk-gen pre-placed mobs
     * (`CHUNK_GENERATION`), monster and trial spawner blocks (`SPAWNER`, `TRIAL_SPAWNER`), structure
     * piece mobs (`STRUCTURE`), illager patrols (`PATROL`), wandering traders / raids and other
     * event spawns (`EVENT`), zombie reinforcements (`REINFORCEMENT`), jockey riders (`JOCKEY`),
     * evoker vexes and similar summons (`MOB_SUMMONED`), sculk-shrieker wardens (`TRIGGERED`),
     * breeding (`BREEDING`) and conversions that finalize (`CONVERSION`). *Not* covered: third-party
     * code that adds a mob via `addFreshEntity` without finalizing — the same hole our own spawns
     * intentionally use, and one no spawn event can close.
     *
     * Both flags are set on a veto. [FinalizeSpawnEvent.setSpawnCancelled] is the one that actually
     * discards the entity (enforced by NeoForge's builtin spawn blocker and a `WorldGenRegion`
     * patch); cancelling the event on top of it skips `finalizeSpawn` itself, so a refused spawn
     * cannot manufacture side entities first — jockey mounts are created *inside* finalize.
     *
     * This is prevention only. A mob that got in before this shipped is [ArenaStamper]'s problem:
     * its sweep already clears the box between runs.
     */
    private fun suppressFinalizeSpawn(event: FinalizeSpawnEvent) {
        if (event.spawnType in EXPLICIT_SPAWN_TYPES) return
        // `event.level` is a ServerLevelAccessor — during chunk generation it is a WorldGenRegion,
        // not the level itself — so the dimension has to come from the backing ServerLevel.
        val dimension = event.level.level.dimension().location()
        // Same layout contract as the Cobblemon veto above: whole-dimension on the generated grid,
        // per-box for fixedArenas so we do not blank spawning across somebody's inhabited world.
        val layout = RunSettings.arenaLayout
        if (layout.isArenaSpace(dimension, BlockPos.containing(event.x, event.y, event.z))) {
            event.setSpawnCancelled(true)
            event.isCanceled = true
        }
    }
}
