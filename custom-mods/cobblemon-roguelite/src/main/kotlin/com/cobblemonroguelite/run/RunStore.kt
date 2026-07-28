package com.cobblemonroguelite.run

import net.minecraft.core.HolderLookup
import net.minecraft.core.RegistryAccess
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.saveddata.SavedData
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private val log = LoggerFactory.getLogger("cobblemon_roguelite/store")

/**
 * Every active PokéRogue-mode run on the server, keyed by player.
 *
 * ### Why world [SavedData] and not `player.persistentData`
 *
 * The battle tower (`TowerGauntletHook.persist()` in cobblemon-bridge) keeps its resume snapshot
 * in raw `player.persistentData`, which is correct there: the snapshot is a `Set<UUID>` pointing
 * at Pokémon that live in the player's real party, so losing it costs a few minutes of progress.
 *
 * We cannot do that. The run party lives **only** here (design decision 1 — nothing persists past
 * a run, so we never touch the player's real storage), which makes this store the sole copy of
 * six Pokémon. And raw `persistentData` does not survive a death-respawn: `ServerPlayer.restoreFrom`
 * copies only the `PlayerPersisted` subtag across the clone, dropping everything else. A player
 * killed by a creeper between waves would come back with their run party deleted.
 *
 * World-level [SavedData] sidesteps that entirely: it is written on the world autosave tick and at
 * shutdown, is untouched by player clone semantics, and keeps every active run in one file that an
 * op can inspect or hand-repair. Concurrent runs (phase 2) also enumerate for free — see
 * [activeRuns].
 *
 * ### Dirty marking
 *
 * [RunState] is mutable and handed out by reference, so mutations made by callers cannot mark this
 * store dirty on their own. Callers must call [checkpoint] at run-progress boundaries — the same
 * contract as `TowerGauntletHook.persist()`. [checkpoint] also flushes to disk, so a crash costs at
 * most the wave in progress rather than everything since the last autosave.
 */
class RunStore private constructor(
    private val registryAccess: RegistryAccess,
) : SavedData() {

    private val runs = ConcurrentHashMap<UUID, RunState>()

    fun get(player: UUID): RunState? = runs[player]

    fun hasRun(player: UUID): Boolean = runs.containsKey(player)

    /** Snapshot of every active run, for op tooling and shutdown accounting. */
    fun activeRuns(): Map<UUID, RunState> = runs.toMap()

    /**
     * Begin (or replace) a run. Replacing is deliberate: `/roguelite start` while a run is live is
     * an abandon-and-restart, and the caller is responsible for having confirmed that with the
     * player — the party it discards is unrecoverable.
     */
    fun start(player: UUID, run: RunState): RunState {
        runs[player] = run
        setDirty()
        return run
    }

    /**
     * End a run and return it so the caller can pay out. The run party dies with it (decision 1);
     * there is no archive, because a retained party would be a legendary faucet the moment anyone
     * found a way to read it back out.
     */
    fun end(player: UUID): RunState? {
        val run = runs.remove(player)
        if (run != null) setDirty()
        return run
    }

    /**
     * Persist the current state of [player]'s run. Call at wave boundaries, after shop purchases,
     * and on logout — anywhere losing the delta would be felt. Not per-turn: this serializes the
     * whole party and writes the file.
     */
    fun checkpoint(server: MinecraftServer, player: UUID) {
        if (!runs.containsKey(player)) return
        setDirty()
        // SavedData alone only reaches disk on the autosave tick. Flush now so a crash mid-run
        // rewinds by one wave at worst. DimensionDataStorage.save() writes only dirty entries.
        runCatching { server.overworld().dataStorage.save() }
            .onFailure { log.warn("roguelite: checkpoint flush failed for {}", player, it) }
    }

    override fun save(tag: CompoundTag, registries: HolderLookup.Provider): CompoundTag {
        val all = CompoundTag()
        runs.forEach { (uuid, run) ->
            runCatching { all.put(uuid.toString(), run.toNbt(registryAccess)) }
                .onFailure { log.error("roguelite: failed to serialize run for {} — it will be lost", uuid, it) }
        }
        tag.put(RUNS_KEY, all)
        return tag
    }

    companion object {
        /** File name under `<world>/data/`. */
        const val DATA_NAME = "cobblemon_bridge_roguelite_runs"
        private const val RUNS_KEY = "runs"

        /**
         * The store for this server, created on first use. [DimensionDataStorage.computeIfAbsent]
         * caches, so callers may treat this as a cheap accessor rather than holding a reference —
         * which also keeps us clear of any init-ordering question about when levels are ready.
         */
        fun of(server: MinecraftServer): RunStore {
            val registryAccess = server.registryAccess()
            val factory = SavedData.Factory(
                { RunStore(registryAccess) },
                { tag, _ -> load(registryAccess, tag) },
            )
            return server.overworld().dataStorage.computeIfAbsent(factory, DATA_NAME)
        }

        private fun load(registryAccess: RegistryAccess, tag: CompoundTag): RunStore {
            val store = RunStore(registryAccess)
            val all = tag.getCompound(RUNS_KEY)
            for (key in all.allKeys) {
                val uuid = runCatching { UUID.fromString(key) }.getOrNull()
                if (uuid == null) {
                    log.warn("roguelite: skipping run under non-UUID key '{}'", key)
                    continue
                }
                // A null here means the snapshot was unusable — see RunState.fromNbt. Dropping the
                // entry presents to the player as "no run", which they can restart from, rather
                // than as an empty party that would read as an instant wipe.
                val run = runCatching { RunState.fromNbt(registryAccess, all.getCompound(key)) }
                    .onFailure { log.warn("roguelite: run for {} failed to load — discarding", uuid, it) }
                    .getOrNull()
                if (run != null) store.runs[uuid] = run
            }
            log.info("roguelite: loaded {} active run(s)", store.runs.size)
            return store
        }
    }
}
