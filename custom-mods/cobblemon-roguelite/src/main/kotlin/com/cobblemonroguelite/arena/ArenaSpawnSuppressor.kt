package com.cobblemonroguelite.arena

import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.Priority
import com.cobblemonroguelite.run.RunSettings
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean

private val log = LoggerFactory.getLogger("cobblemon_roguelite/arena")

/**
 * Nothing wild spawns in an arena except what a wave puts there.
 *
 * ### Why this is an event cancel and not configuration
 *
 * The arena's `dimension_type` already kills vanilla hostile spawning (`monster_spawn_light_level: 0`
 * and `monster_spawn_block_light_limit: 0`), but Cobblemon's spawner does not consult dimension type
 * at all — it is player-driven, and a player standing in an arena is exactly the trigger it wants.
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
        log.debug("roguelite: arena spawn suppression active")
    }
}
