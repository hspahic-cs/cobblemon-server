package com.cobblemonroguelite.run

import net.minecraft.core.HolderLookup
import net.minecraft.core.RegistryAccess
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.saveddata.SavedData
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

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
 * most the wave in progress rather than everything since the last autosave. [end] flushes too, and
 * for a sharper reason: see there.
 */
class RunStore private constructor(
    private val registryAccess: RegistryAccess,
) : SavedData() {

    private val runs = ConcurrentHashMap<UUID, RunState>()

    /**
     * Seeds minted for runs whose starter has not been picked yet. Kept beside [runs] rather than as
     * a half-built [RunState] because a run with no party is not a state [RunState.fromNbt] will
     * restore — it discards an empty party on purpose, since an empty party reads as an instant
     * wipe. See [PendingStart] for why this has to reach disk at all.
     */
    private val pending = ConcurrentHashMap<UUID, PendingStart>()

    /**
     * §2.23: runs deleted by the expiry sweep whose owner has not been told yet.
     *
     * Here rather than in a file of its own because it is the *residue* of an entry in [runs] and has
     * no life without one — a notice is written in the same call that removes a run and is dropped on
     * the owner's next login. See [RunExpiryNotice] for why the record has to survive at all, and why
     * this must not be allowed to become a ledger.
     */
    private val expired = ConcurrentHashMap<UUID, RunExpiryNotice>()

    fun get(player: UUID): RunState? = runs[player]

    fun hasRun(player: UUID): Boolean = runs.containsKey(player)

    fun pending(player: UUID): PendingStart? = pending[player]

    /**
     * Record a minted seed before the starter offer is shown, and flush it.
     *
     * Flushes for the same reason [end] does rather than the softer one [checkpoint] does: the
     * window this closes is measured against a player who is *looking at the offer screen*, i.e.
     * exactly the moment they might quit. Leaving the write to the next autosave leaves the seed
     * re-rollable for as long as that takes, which is the thing §2.16 wrote the seed down to prevent.
     */
    fun beginPending(server: MinecraftServer, player: UUID, start: PendingStart): PendingStart {
        pending[player] = start
        setDirty()
        flush(server, player)
        return start
    }

    /**
     * Drop a pending start — because the starter was chosen, or because the player walked away.
     *
     * Not flushed. A stale pending start that survives a crash costs the player nothing: they are
     * shown the offer again, from the same seed, which is where they already were. The flush on
     * [beginPending] is what matters; this one is only bookkeeping.
     */
    fun clearPending(player: UUID): PendingStart? = pending.remove(player)?.also { setDirty() }

    /** Snapshot of every active run, for op tooling and shutdown accounting. */
    fun activeRuns(): Map<UUID, RunState> = runs.toMap()

    /**
     * Snapshot of every paid start still waiting on a starter choice.
     *
     * Exists because arena capacity has to count them: a pending start is a run that has been paid
     * for and will want a slot the moment the player picks, so treating the grid as free until the
     * [RunState] exists is how a burst of starts all pass the capacity gate and one of them then
     * cannot be given an arena — after being charged. See
     * [com.cobblemonroguelite.arena.ArenaSlots.hasFreeSlot].
     */
    fun pendingStarts(): Map<UUID, PendingStart> = pending.toMap()

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
     *
     * Flushes for a harder reason than [checkpoint] does. The removal is the *only* record that a
     * run was completed and paid out; the payout itself lands in the economy, which has its own
     * persistence. Leaving the removal to the next autosave means a crash in that window restores
     * the run at its last checkpoint with the reward already banked — the player finishes it again
     * and gets paid twice, and repeatably, since the crash window is the same every time.
     *
     * Call this on the server thread, where the flush runs inline and is done before the payout is
     * made. Called off-thread it is only queued (see [flush]), which reopens that window a tick.
     */
    fun end(server: MinecraftServer, player: UUID): RunState? {
        val run = runs.remove(player) ?: return null
        setDirty()
        flush(server, player)
        return run
    }

    /**
     * §2.23: delete [player]'s run because nobody has played it, and leave a note saying so.
     *
     * Separate from [end] and it must stay separate, because [end] is the *payout* path — everything
     * that calls it goes on to resolve a table and hand grants over, and an expiry pays nothing. Routing
     * expiry through it would either pay a player who has not touched the run in half a year or need a
     * "do not pay" flag threaded through the whole run-end path, which is a flag somebody eventually
     * gets wrong in the direction that mints items.
     *
     * Flushes for [end]'s reason inverted: the removal must reach disk before the next autosave can
     * write the run back out, or the sweep runs again next boot on a run it already announced as gone —
     * and the player would be told twice about one deletion.
     *
     * Returns null when there was nothing to expire, so a double call is harmless.
     */
    fun expire(server: MinecraftServer, player: UUID, notice: RunExpiryNotice): RunState? {
        val run = runs.remove(player) ?: return null
        expired[player] = notice
        setDirty()
        flush(server, player)
        return run
    }

    /**
     * Take the pending expiry notice for [player], if there is one. Delivering it is the caller's job.
     *
     * Removing on read is the whole double-delivery guard: a player is told once that their run
     * expired. Not flushed — a crash between this and the message re-delivers one sentence on the next
     * login, which is a far better failure than a flush per login on the path that is empty for
     * essentially every player.
     */
    fun takeExpiryNotice(player: UUID): RunExpiryNotice? = expired.remove(player)?.also { setDirty() }

    /**
     * Persist the current state of [player]'s run. Call at wave boundaries, after shop purchases,
     * and on logout — anywhere losing the delta would be felt. Not per-turn: this serializes the
     * whole party and writes the file.
     */
    fun checkpoint(server: MinecraftServer, player: UUID) {
        if (!runs.containsKey(player)) return
        setDirty()
        flush(server, player)
    }

    /**
     * SavedData alone only reaches disk on the autosave tick; this writes it now, so a crash costs
     * a wave at worst. `DimensionDataStorage.save()` writes only dirty entries.
     *
     * On the server thread, always. Callers reach us from battle hooks, and ours answer
     * asynchronously (the AI bridge replies off-thread), so an inline `save()` can run concurrently
     * with the world autosave already walking the same `DimensionDataStorage` — two writers, one
     * file, and the loser is a truncated run file that reads back as "no run". `execute` runs the
     * block inline when we are already on the server thread, so the common path pays nothing for
     * this; off-thread callers get the write ordered against the autosave instead of racing it.
     */
    private fun flush(server: MinecraftServer, player: UUID) {
        server.execute {
            runCatching { server.overworld().dataStorage.save() }
                .onFailure { log.warn("roguelite: checkpoint flush failed for {}", player, it) }
        }
    }

    override fun save(tag: CompoundTag, registries: HolderLookup.Provider): CompoundTag {
        val all = CompoundTag()
        runs.forEach { (uuid, run) ->
            runCatching { all.put(uuid.toString(), run.toNbt(registryAccess)) }
                .onFailure { log.error("roguelite: failed to serialize run for {} — it will be lost", uuid, it) }
        }
        tag.put(RUNS_KEY, all)
        val allPending = CompoundTag()
        pending.forEach { (uuid, start) -> allPending.put(uuid.toString(), start.toNbt()) }
        tag.put(PENDING_KEY, allPending)
        val allExpired = CompoundTag()
        expired.forEach { (uuid, notice) -> allExpired.put(uuid.toString(), notice.toNbt()) }
        tag.put(EXPIRED_KEY, allExpired)
        return tag
    }

    companion object {
        /** File name under `<world>/data/`. Names this mod, not the one this code started in. */
        const val DATA_NAME = "cobblemon_roguelite_runs"
        private const val RUNS_KEY = "runs"
        private const val PENDING_KEY = "pendingStarts"
        private const val EXPIRED_KEY = "expiredNotices"

        /**
         * How long an undelivered expiry notice is kept, in days. Longer than the deepest retention
         * band, so a notice always outlives the run it describes; finite, so a player who never comes
         * back leaves three numbers behind rather than three numbers forever. It is the same argument
         * expiry itself makes, applied to expiry's own paperwork.
         */
        private const val NOTICE_RETENTION_DAYS = 365L

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
            val allPending = tag.getCompound(PENDING_KEY)
            for (key in allPending.allKeys) {
                val uuid = runCatching { UUID.fromString(key) }.getOrNull() ?: continue
                // A pending start whose run already exists is a crash between "starter chosen" and
                // "pending cleared". The run wins: it was built from this very seed, and re-offering
                // a starter to someone who has one would give them a second party member for free.
                if (store.runs.containsKey(uuid)) continue
                PendingStart.fromNbt(allPending.getCompound(key))?.let { store.pending[uuid] = it }
            }
            val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(NOTICE_RETENTION_DAYS)
            val allExpired = tag.getCompound(EXPIRED_KEY)
            for (key in allExpired.allKeys) {
                val uuid = runCatching { UUID.fromString(key) }.getOrNull() ?: continue
                val notice = RunExpiryNotice.fromNbt(allExpired.getCompound(key)) ?: continue
                // Aged out here rather than on a timer: this is the only moment the whole map is walked
                // anyway, and a notice nobody has collected in a year is for a player who is not coming
                // back for the sentence.
                if (notice.expiredAtEpochMs >= cutoff) store.expired[uuid] = notice
            }
            log.info(
                "roguelite: loaded {} active run(s), {} awaiting a starter, {} unread expiry notice(s)",
                store.runs.size, store.pending.size, store.expired.size,
            )
            return store
        }
    }
}
