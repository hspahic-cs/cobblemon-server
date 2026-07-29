package com.cobblemonroguelite.arena

import com.cobblemonroguelite.data.biome.RunBiome
import com.cobblemonroguelite.data.biome.RunBiomes
import com.cobblemonroguelite.run.BiomeRotation
import com.cobblemonroguelite.run.RunEntryPoint
import com.cobblemonroguelite.run.RunSettings
import com.cobblemonroguelite.run.RunState
import com.cobblemonroguelite.run.RunStore
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("cobblemon_roguelite/arena")

/** Why a player is not going into an arena. Named per case because each needs a different fix. */
sealed interface ArenaFailure {

    /** Every slot is taken. The only one of these that is a normal, temporary condition. */
    data object NoFreeSlot : ArenaFailure

    /** The slot index does not resolve — a grid re-index under a live run, or a shortened list. */
    data class NoSuchSlot(val slot: Int) : ArenaFailure

    /** The stamp failed. [result] says how; all of them are operator-facing. */
    data class NotStamped(val result: StampResult) : ArenaFailure
}

/**
 * The arena half of the run lifecycle: assign a slot, keep the right build standing in it, put the
 * player in, and put them back.
 *
 * ### What this owns and what it refuses to own
 *
 * Same split as [com.cobblemonroguelite.run.RunController]: it owns the *order* and the persistence
 * boundary, and everything that decides something lives elsewhere — [ArenaLayout] for where a slot
 * is, [ArenaSlots] for which one, [ArenaStamper] for what goes in it, [ArenaChunks] for keeping it
 * loaded. That is what leaves the interesting parts unit-testable while this stays a wiring layer
 * that needs a server.
 *
 * It does **not** put anything in the arena except the player. Opponents belong to
 * [com.cobblemonroguelite.run.RunWaveHandler], which does not exist yet; what this owes that seam is
 * an arena that is loaded and stamped by the time it is asked, which is [prepare].
 *
 * ### Threading
 *
 * Server thread, all of it — block edits, entity discards and teleports are all unsafe from a battle
 * callback thread. Callers that arrive off-thread hop through `server.execute` first, the same rule
 * [com.cobblemonroguelite.run.RunController] states.
 */
object RunArenas {

    /**
     * Whether a run could be given an arena, for the start gate.
     *
     * Asked *before* the fee is charged, which is the point: refusing after the charge would need the
     * refund seam §2.16 deliberately refused to add. Pending starts count as occupancy — see
     * [ArenaSlots.hasFreeSlot].
     */
    fun hasCapacity(server: MinecraftServer): Boolean {
        val store = RunStore.of(server)
        return ArenaSlots.hasFreeSlot(
            occupied = occupiedSlots(store),
            reserved = store.pendingStarts().size,
            capacity = layout().capacity,
        )
    }

    /**
     * Give [run] an arena and stamp it. Idempotent: a run that already holds a slot keeps it.
     *
     * **A fresh assignment always stamps**, even when the template that is going in is the one that
     * was already there. That is the crash-proofing: the slot was last used by somebody else's run,
     * and whether their cleanup ran is not something we get to assume. Comparing
     * [RunState.stampedTemplate] would skip the stamp exactly in the case that most needs it — same
     * template, different run, unknown wreckage.
     *
     * Does not persist. The caller checkpoints, because it is the caller that knows whether this is
     * part of a larger transaction (run start writes the whole run once, not twice).
     */
    fun assign(server: MinecraftServer, run: RunState): ArenaResult<ArenaPlacement, ArenaFailure> {
        run.arenaSlot?.let { return prepare(server, run) }

        val layout = layout()
        val store = RunStore.of(server)
        val slot = ArenaSlots.firstFree(occupiedSlots(store), layout.capacity)
            ?: return ArenaResult.Failure(ArenaFailure.NoFreeSlot)

        run.arenaSlot = slot
        // Cleared so the stamp below cannot be skipped by a value inherited from... anything. A run
        // reaching here has no arena by definition, so whatever it thinks is standing in one is wrong.
        run.stampedTemplate = null
        // Same reasoning, one field over: the biome painted into the slot belongs to whoever had it
        // last. [RunState.biome] is deliberately *not* cleared — that is where the run is, not what
        // the world looks like, and clearing it would discard a transition the player was told about
        // (and, once §2.24's branch exists, one they chose).
        run.paintedBiome = null
        return prepare(server, run)
    }

    /**
     * Make [run]'s arena correct for the wave it is on, and hold it loaded.
     *
     * This is the §2.19 band transition and the resume path in one call, because they want the same
     * thing: the build the current wave calls for, standing, in loaded chunks. It re-stamps only when
     * the template actually differs from [RunState.stampedTemplate] — a stamp is ~130k block writes
     * and doing it per wave would be a visible hitch every battle — but it takes the chunk ticket
     * every time, since the arena may have gone cold since the last call.
     *
     * The wave handler must call this before summoning anything, and must then let
     * [ArenaConfig.settleTicks] pass. See [ArenaChunks] for what happens if it does not.
     *
     * ### Where §2.24's biome enters
     *
     * The band transition this call implements *is* the biome transition. The run's biome is settled
     * first, because it decides which template is wanted; the repaint then follows the stamp, since
     * repainting a box we are about to demolish and rebuild is work that shows for one tick.
     */
    fun prepare(server: MinecraftServer, run: RunState): ArenaResult<ArenaPlacement, ArenaFailure> {
        val config = config()
        val slot = run.arenaSlot ?: return assign(server, run)
        val placement = layout().placementOf(slot)
            ?: return ArenaResult.Failure(ArenaFailure.NoSuchSlot(slot)).also {
                log.error(
                    "roguelite: run holds arena slot {} which the current layout does not have — was " +
                        "gridWidth, maxConcurrentRuns or fixedArenas changed with runs in flight?",
                    slot,
                )
            }
        val level = levelOf(server, placement.dimension)
            ?: return ArenaResult.Failure(ArenaFailure.NotStamped(StampResult.NoSuchDimension(placement.dimension)))

        val biome = biomeFor(run, config)
        val wanted = biome?.arenaTemplate ?: config.templates.templateFor(run.wave)
        if (run.stampedTemplate == wanted) {
            // Already correct. Still needs the ticket: "stamped" is a fact about blocks on disk, not
            // about chunks being loaded, and the two come apart the moment the last player leaves.
            if (!ArenaChunks.hold(level, placement.box)) {
                return ArenaResult.Failure(ArenaFailure.NotStamped(StampResult.NotLoaded))
            }
            repaint(level, placement, run, biome)
            return ArenaResult.Success(placement)
        }

        return when (val result = ArenaStamper.stamp(level, placement, wanted)) {
            StampResult.Stamped -> {
                run.stampedTemplate = wanted
                log.info("roguelite: stamped '{}' into arena slot {} for wave {}", wanted, slot, run.wave)
                repaint(level, placement, run, biome)
                ArenaResult.Success(placement)
            }
            // stampedTemplate is left alone on failure, so the next attempt tries again rather than
            // believing a build that is not there.
            else -> ArenaResult.Failure(ArenaFailure.NotStamped(result))
        }
    }

    /**
     * Settle which biome [run] is in for the wave it is on, writing it onto the run.
     *
     * Written here rather than returned, because every caller of [prepare] would otherwise have to
     * remember to store it — and a rotation that decided a transition and did not record it would
     * re-decide, and could re-decide *differently* once §2.24's player choice exists.
     *
     * Null means no biome: either nothing is configured (the shipped state), or the run holds a biome
     * id whose file has since been deleted. The second case keeps the run's [RunState.biome] as it is
     * and only loses the arena build, because forgetting where a run is because of somebody's datapack
     * edit is a larger consequence than falling back to the configured template for a band.
     */
    private fun biomeFor(run: RunState, config: ArenaConfig): RunBiome? {
        val bandLength = RunSettings.current.biomeBandLength
        run.biome = BiomeRotation.next(
            current = run.biome,
            wave = run.wave,
            bandLength = bandLength,
            seed = run.seed,
            eligible = RunBiomes.eligibleAt(run.wave),
        )
        val visit = run.biome ?: return null
        val definition = RunBiomes[visit.biome]
        if (definition == null) {
            log.warn(
                "roguelite: run is in biome '{}' which is no longer loaded — falling back to the " +
                    "configured arena template ({}) and leaving the painted biome alone",
                visit.biome, config.templates.templateFor(run.wave),
            )
        }
        return definition
    }

    /**
     * §2.24's repaint, skipped when the box already carries this biome.
     *
     * Never fatal. A failed repaint leaves a stamped, playable arena that looks like the wrong place;
     * refusing the wave over it would turn a cosmetic fault into a run the player cannot continue.
     * [RunState.paintedBiome] is written only on success, so a failure retries on the next wave rather
     * than being remembered as done.
     */
    private fun repaint(level: ServerLevel, placement: ArenaPlacement, run: RunState, biome: RunBiome?) {
        if (biome == null || run.paintedBiome == biome.minecraftBiome) return
        when (val result = ArenaBiomePainter.paint(level, placement.box, biome.minecraftBiome)) {
            is BiomePaintResult.Painted -> {
                run.paintedBiome = biome.minecraftBiome
                log.info(
                    "roguelite: arena slot {} repainted to {} for biome '{}' at wave {}",
                    placement.slot, biome.minecraftBiome, biome.id, run.wave,
                )
            }

            is BiomePaintResult.NoSuchBiome -> log.error(
                "roguelite: biome '{}' names Minecraft biome '{}', which no datapack registers — arena " +
                    "slot {} keeps the dimension's own biome. The run is unaffected.",
                biome.id, result.biome, placement.slot,
            )

            is BiomePaintResult.Failed -> log.error(
                "roguelite: could not repaint arena slot {} to '{}': {}. The run is unaffected.",
                placement.slot, result.biome, result.detail,
            )
        }
    }

    /**
     * Put [player] into their arena, recording where they came from first.
     *
     * The entry point is captured only when the player is **outside** every arena. A player already
     * standing in one is being moved between arena states — a band transition, a resume after a
     * disconnect that left them there — and overwriting the entry point with their arena position
     * would make the exit teleport put them back into the void they were trying to leave. That is the
     * bug this ordering exists to prevent, and it is invisible until a run ends.
     */
    fun enter(server: MinecraftServer, player: ServerPlayer, run: RunState): ArenaResult<ArenaPlacement, ArenaFailure> {
        val prepared = prepare(server, run)
        val placement = when (prepared) {
            is ArenaResult.Failure -> return prepared
            is ArenaResult.Success -> prepared.value
        }
        if (!isInArena(player)) run.entry = RunEntryPoint.of(player)

        val level = levelOf(server, placement.dimension) ?: return ArenaResult.Failure(
            ArenaFailure.NotStamped(StampResult.NoSuchDimension(placement.dimension)),
        )
        val at = placement.entry(config().entryOffset)
        player.teleportTo(level, at.x + 0.5, at.y.toDouble(), at.z + 0.5, config().entryYaw, 0f)
        return ArenaResult.Success(placement)
    }

    /**
     * Send [player] back where they came from and free their slot.
     *
     * Deliberately does **not** clean the arena. See [ArenaStamper]: cleanup on the way out is
     * cleanup a crash skips, so the next assignment does it instead. All this owes is that the slot
     * stops being occupied, which is one field, and that the player stops being in the void.
     *
     * Called with the run that just ended — [RunStore.end] hands it back for exactly this — so the
     * slot is released from a value the store no longer holds. The release is therefore automatic
     * either way: a run that is gone from the store occupies nothing.
     *
     * The chunk ticket is dropped here as an optimisation and not as a correctness step: it expires
     * on its own ([ArenaChunks]), so this only shortens the window in which a finished run's arena is
     * still ticking. Order matters slightly — the ticket goes after the teleport, since a player
     * standing in chunks we just stopped holding keeps them loaded by being there anyway.
     */
    fun exit(server: MinecraftServer, player: ServerPlayer, run: RunState?) {
        val slot = run?.arenaSlot
        run?.arenaSlot = null
        if (isInArena(player)) eject(server, player, run?.entry)
        val placement = slot?.let { layout().placementOf(it) } ?: return
        levelOf(server, placement.dimension)?.let { ArenaChunks.release(it, placement.box) }
    }

    /**
     * Put a player who is standing in an arena somewhere they are allowed to be.
     *
     * The fallback chain is entry point, then the overworld spawn, and the fallback is not a
     * formality: [RunEntryPoint] says a run can outlive the dimension it was started from. A player
     * left in a void dimension with no run is stuck there permanently — there is no bed, no portal
     * and nothing to fall onto — so "somewhere wrong" beats "here" every time.
     */
    fun eject(server: MinecraftServer, player: ServerPlayer, entry: RunEntryPoint?) {
        val target = entry?.let { point ->
            levelOf(server, point.dimension)?.let { level -> level to point }
        }
        if (target != null) {
            val (level, point) = target
            player.teleportTo(level, point.x, point.y, point.z, point.yaw, point.pitch)
            return
        }
        val overworld = server.overworld()
        val spawn = overworld.sharedSpawnPos
        log.warn(
            "roguelite: ejecting {} to world spawn — entry point was {}",
            player.gameProfile.name, entry?.dimension ?: "never recorded",
        )
        player.teleportTo(overworld, spawn.x + 0.5, spawn.y.toDouble(), spawn.z + 0.5, overworld.sharedSpawnAngle, 0f)
    }

    /** True when [player] is standing in arena space — see [ArenaLayout.isArenaSpace] for the two senses. */
    fun isInArena(player: ServerPlayer): Boolean =
        layout().isArenaSpace(player.level().dimension().location(), player.blockPosition())

    /** Slots held by active runs. Recomputed per call; [ArenaSlots] says why there is no cached set. */
    private fun occupiedSlots(store: RunStore): Set<Int> =
        store.activeRuns().values.mapNotNullTo(mutableSetOf()) { it.arenaSlot }

    private fun levelOf(server: MinecraftServer, dimension: ResourceLocation): ServerLevel? =
        server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension))

    private fun config(): ArenaConfig = RunSettings.current.arena

    /** Cached and rebuilt on config change — see [RunSettings.arenaLayout]. */
    private fun layout(): ArenaLayout = RunSettings.arenaLayout
}

/**
 * A success-or-named-failure return.
 *
 * Kotlin's own `Result` carries a `Throwable`, and none of the ways an arena fails is an exception —
 * a full grid and a missing template are both ordinary answers that a caller has to tell the player
 * apart. Throwing to signal them would put the interesting information in a stack trace.
 */
sealed interface ArenaResult<out T, out E> {
    data class Success<T>(val value: T) : ArenaResult<T, Nothing>
    data class Failure<E>(val error: E) : ArenaResult<Nothing, E>
}
