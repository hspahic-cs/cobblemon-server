package com.cobblemonbridge.wild

import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemonbridge.CobblemonBridge
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.ChunkPos
import net.minecraft.network.chat.Component
import net.minecraft.world.level.block.Blocks
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.loading.FMLPaths
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent
import net.neoforged.neoforge.server.ServerLifecycleHooks
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.writeText

/**
 * Legendary Monuments one-shot global lock.
 *
 * Rules:
 *  - Only one LM legendary may be alive in the world at a time. A second spawn attempt
 *    while one is active is cancelled.
 *  - If the legendary leaves the world uncaught (fled from battle, despawned), the monument
 *    resets and can produce another.
 *  - If the legendary is caught, the monument is permanently locked — no further legendaries
 *    will ever spawn from any LM structure.
 *
 * Detection:
 *  - Structure namespace check (`legendarymonuments:*`) at spawn time via [startsForStructure] (2D chunk-based, Y-agnostic).
 *  - Caught vs. fled distinguished by [Pokemon.isWild] on the next server tick after entity
 *    removal: capture calls [party.add] in the same tick before our scheduled check runs,
 *    so isWild() == false means caught; true means fled/despawned.
 *
 * Persistence: `config/cobblemon-bridge/runtime/monument_lock.flag` — zero-byte sentinel.
 * Admin reset: `/monument admin reset`.
 */
object LegendaryMonumentLock {

    private const val LM_NAMESPACE = "legendarymonuments"
    private const val FLAG_NAME = "monument_lock.flag"

    @Volatile private var lockFile: Path? = null
    @Volatile private var locked: Boolean = false

    fun isLocked(): Boolean = locked

    /** The LM legendary currently live in the world. Null if none active. */
    @Volatile private var activeLmPokemon: Pokemon? = null
    @Volatile private var activeLmLevel: ServerLevel? = null
    @Volatile private var activeLmPos: BlockPos? = null

    /** XZ scan radius around the structure anchor when draining altar blocks. */
    private const val DRAIN_RADIUS_XZ = 48
    /** Full Y range to scan (structure anchor down to bedrock, up to build limit). */
    private const val DRAIN_Y_MIN = -64
    private const val DRAIN_Y_MAX = 320

    fun init() {
        val file = FMLPaths.CONFIGDIR.get()
            .resolve("cobblemon-bridge")
            .resolve("runtime")
            .resolve(FLAG_NAME)
        lockFile = file
        locked = file.exists()
        CobblemonBridge.logger.info(
            "monument-lock: status={}",
            if (locked) "LOCKED" else "open",
        )
    }

    fun registerEvents() {
        CobblemonEvents.POKEMON_ENTITY_SPAWN.subscribe(Priority.HIGH) { event ->
            val pokemon = event.entity.pokemon
            if (!pokemon.isLegendary() && !pokemon.isMythical()) return@subscribe
            if (!isInsideLmStructure(event.entity)) return@subscribe

            if (locked) {
                event.cancel()
                CobblemonBridge.logger.debug(
                    "monument-lock: cancelled {} spawn — permanently locked",
                    pokemon.species.name,
                )
                return@subscribe
            }

            if (activeLmPokemon != null) {
                event.cancel()
                CobblemonBridge.logger.debug(
                    "monument-lock: cancelled {} spawn — {} is already active",
                    pokemon.species.name,
                    activeLmPokemon!!.species.name,
                )
                return@subscribe
            }

            activeLmPokemon = pokemon
            activeLmLevel = event.entity.level() as? ServerLevel
            activeLmPos = event.entity.blockPosition()
            CobblemonBridge.logger.info(
                "monument-lock: {} spawned in LM structure — one chance to catch it",
                pokemon.species.name,
            )
            val server = event.entity.server ?: return@subscribe
            server.playerList.players.forEach {
                it.sendSystemMessage(Component.literal(
                    "§6[Legendary Monument] §fA wild ${pokemon.species.name} has appeared at a Legendary Monument! " +
                    "§7Catch it before it's gone..."
                ))
            }
        }

        // POKEMON_CAPTURED: fast-path exit when no LM legendary is active — negligible overhead.
        CobblemonEvents.POKEMON_CAPTURED.subscribe(Priority.NORMAL) { event ->
            val active = activeLmPokemon ?: return@subscribe
            if (event.pokemon !== active) return@subscribe

            val captureLevel = activeLmLevel
            val spawnPos = activeLmPos
            activeLmPokemon = null
            activeLmLevel = null
            activeLmPos = null
            writeLock()
            val catcher = event.player.gameProfile.name
            val species = event.pokemon.species.name
            CobblemonBridge.logger.info(
                "monument-lock: LOCKED — {} caught {} from an LM structure",
                catcher, species,
            )
            if (captureLevel != null && spawnPos != null) {
                val anchorPos = findStructureAnchor(captureLevel, spawnPos) ?: spawnPos
                drainAltar(captureLevel, anchorPos)
            }
            event.player.server.playerList.players.forEach {
                it.sendSystemMessage(Component.literal(
                    "§6[Legendary Monument] §f$catcher has caught $species! " +
                    "§7The monument's power is spent — no legendary will spawn there again."
                ))
            }
        }
    }

    /**
     * Fires when any entity leaves the level. We only care about the active LM legendary
     * entity. Schedules a next-tick check so [POKEMON_CAPTURED] (same-tick, sequential) can
     * fire first if this removal was part of a capture. After that tick, [Pokemon.isWild]
     * is false for a caught pokemon and true for a fled/despawned one.
     */
    @SubscribeEvent
    fun onEntityLeaveLevel(event: EntityLeaveLevelEvent) {
        val entity = event.entity as? PokemonEntity ?: return
        val active = activeLmPokemon ?: return
        if (entity.pokemon !== active) return

        val server = ServerLifecycleHooks.getCurrentServer() ?: return
        server.execute {
            if (locked || activeLmPokemon !== active) return@execute  // already handled by POKEMON_CAPTURED
            activeLmPokemon = null
            activeLmLevel = null
            activeLmPos = null
            CobblemonBridge.logger.info("monument-lock: LM legendary left without being caught — monument is open again")
            server.playerList.players.forEach {
                it.sendSystemMessage(Component.literal(
                    "§6[Legendary Monument] §7The legendary escaped... The monument can be challenged again."
                ))
            }
        }
    }

    /** Clears both the permanent lock and any in-progress active legendary. */
    fun reset() {
        lockFile?.deleteIfExists()
        locked = false
        activeLmPokemon = null
        activeLmLevel = null
        activeLmPos = null
        CobblemonBridge.logger.info("monument-lock: reset by admin")
    }

    /**
     * Returns the lowest bounding-box Y of the LM structure start whose chunk contains
     * [spawnPos], giving a ground-level anchor for the drain scan.
     * Falls back to null if no structure start is found (caller uses spawnPos).
     */
    private fun findStructureAnchor(level: ServerLevel, spawnPos: BlockPos): BlockPos? {
        val structureManager = level.structureManager()
        val registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE)
        val chunkPos = ChunkPos(spawnPos)
        for ((key, structure) in registry.entrySet()) {
            if (key.location().namespace != LM_NAMESPACE) continue
            val starts = structureManager.startsForStructure(chunkPos) { it == structure }
            if (starts.isNotEmpty()) {
                val bb = starts.first().getBoundingBox()
                return BlockPos(bb.minX() + (bb.maxX() - bb.minX()) / 2, bb.minY(), bb.minZ() + (bb.maxZ() - bb.minZ()) / 2)
            }
        }
        return null
    }

    /**
     * Replaces every `legendarymonuments:*` block within [DRAIN_RADIUS_XZ] XZ and the full
     * world Y range of [anchor] with crying obsidian, giving the monument a spent appearance.
     *
     * [anchor] should be the structure's ground-level bounding-box centre (from
     * [findStructureAnchor]), not the entity's spawn position — tall structures like Bell Tower
     * spawn legendaries at the apex (y≈242) while their altar blocks sit near y≈125.
     */
    private fun drainAltar(level: ServerLevel, anchor: BlockPos) {
        val cryingObsidian = Blocks.CRYING_OBSIDIAN.defaultBlockState()
        var count = 0
        for (x in -DRAIN_RADIUS_XZ..DRAIN_RADIUS_XZ) {
            for (y in DRAIN_Y_MIN..DRAIN_Y_MAX) {
                for (z in -DRAIN_RADIUS_XZ..DRAIN_RADIUS_XZ) {
                    val pos = BlockPos(anchor.x + x, y, anchor.z + z)
                    val state = level.getBlockState(pos)
                    if (state.block.builtInRegistryHolder().key()?.location()?.namespace == LM_NAMESPACE) {
                        level.setBlock(pos, cryingObsidian, 3)
                        count++
                    }
                }
            }
        }
        CobblemonBridge.logger.info("monument-lock: drained {} LM blocks around {}", count, anchor)
    }

    private fun isInsideLmStructure(entity: PokemonEntity): Boolean {
        val level = entity.level() as? ServerLevel ?: return false
        val structureManager = level.structureManager()
        val registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE)
        val chunkPos = ChunkPos(entity.blockPosition())
        // getAllStructuresAt does a 3D bounding-box check — entities spawned at the top of tall
        // structures (e.g. Bell Tower apex at y=242) fall outside piece boxes and get missed.
        // getStructureWithPieceAt is also 3D. Use startsForStructure per chunk instead, which
        // checks only the 2D chunk footprint and is Y-agnostic.
        return structureManager.startsForStructure(chunkPos) { structure ->
            registry.getKey(structure)?.namespace == LM_NAMESPACE
        }.isNotEmpty()
    }

    private fun writeLock() {
        val file = lockFile ?: return
        file.parent.createDirectories()
        val tmp = file.resolveSibling("$FLAG_NAME.tmp")
        tmp.writeText("")
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        locked = true
    }
}
