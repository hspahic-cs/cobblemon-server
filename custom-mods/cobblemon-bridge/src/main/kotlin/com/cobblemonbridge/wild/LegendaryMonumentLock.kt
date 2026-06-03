package com.cobblemonbridge.wild

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemonbridge.CobblemonBridge
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.ChunkPos
import net.minecraft.network.chat.Component
import net.minecraft.world.level.block.Blocks
import net.neoforged.bus.api.EventPriority
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.loading.FMLPaths
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent
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
 *  - The monument is one-shot regardless of outcome — the altar is permanently spent and
 *    drained whether the legendary is caught or flees. Players can't exploit a reset by
 *    luring the legendary away and fleeing the battle.
 *
 * Detection:
 *  - [EntityJoinLevelEvent] fires for all entity adds regardless of origin. LM spawns
 *    legendaries by directly constructing a [PokemonEntity] via `PokemonProperties`,
 *    bypassing Cobblemon's spawn pipeline entirely.
 *  - On entity removal, a next-tick check reads [Pokemon.isWild] to pick the right
 *    broadcast message (caught vs fled) — both paths lock and drain.
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

    /**
     * Fires when any entity joins the level — including LM-spawned legendaries, which are
     * created directly via PokemonProperties and never pass through POKEMON_ENTITY_SPAWN.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    fun onEntityJoinLevel(event: EntityJoinLevelEvent) {
        val entity = event.entity as? PokemonEntity ?: return
        val level = event.level as? ServerLevel ?: return
        val pokemon = entity.pokemon
        if (!pokemon.isLegendary() && !pokemon.isMythical()) return
        if (!isInsideLmStructure(entity)) return

        if (locked) {
            event.isCanceled = true
            CobblemonBridge.logger.debug(
                "monument-lock: cancelled {} join — permanently locked",
                pokemon.species.name,
            )
            return
        }

        if (activeLmPokemon != null) {
            event.isCanceled = true
            CobblemonBridge.logger.debug(
                "monument-lock: cancelled {} join — {} is already active",
                pokemon.species.name,
                activeLmPokemon!!.species.name,
            )
            return
        }

        activeLmPokemon = pokemon
        activeLmLevel = level
        activeLmPos = entity.blockPosition()
        CobblemonBridge.logger.info(
            "monument-lock: {} joined world in LM structure — altar is now spent",
            pokemon.species.name,
        )
        level.server.playerList.players.forEach {
            it.sendSystemMessage(Component.literal(
                "§6[Legendary Monument] §fA wild ${pokemon.species.name} has appeared at a Legendary Monument! " +
                "§7This is your only chance..."
            ))
        }
    }

    /**
     * Fires when any entity leaves the level. Schedules a next-tick check so Cobblemon's
     * capture logic (same tick as entity removal) can complete first. On the next tick,
     * [Pokemon.isWild] distinguishes caught (false) from fled (true) for the broadcast
     * message — both outcomes lock and drain the altar.
     */
    @SubscribeEvent
    fun onEntityLeaveLevel(event: EntityLeaveLevelEvent) {
        val entity = event.entity as? PokemonEntity ?: return
        val active = activeLmPokemon ?: return
        if (entity.pokemon !== active) return

        val server = ServerLifecycleHooks.getCurrentServer() ?: return
        server.execute {
            if (locked) return@execute
            val leaveLevel = activeLmLevel
            val leavePos = activeLmPos
            val caught = !active.isWild()
            activeLmPokemon = null
            activeLmLevel = null
            activeLmPos = null
            writeLock()
            if (leaveLevel != null && leavePos != null) {
                val anchorPos = findStructureAnchor(leaveLevel, leavePos) ?: leavePos
                drainAltar(leaveLevel, anchorPos)
            }
            if (caught) {
                CobblemonBridge.logger.info("monument-lock: LOCKED — {} caught from LM structure", active.species.name)
                server.playerList.players.forEach {
                    it.sendSystemMessage(Component.literal(
                        "§6[Legendary Monument] §f${active.species.name} was caught! " +
                        "§7The monument's power is spent — no legendary will spawn there again."
                    ))
                }
            } else {
                CobblemonBridge.logger.info("monument-lock: LOCKED — {} fled from LM structure, monument is spent", active.species.name)
                server.playerList.players.forEach {
                    it.sendSystemMessage(Component.literal(
                        "§6[Legendary Monument] §7The legendary escaped... but the monument's power is spent."
                    ))
                }
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
     * Returns the ground-level bounding-box centre of the LM structure whose chunk contains
     * [spawnPos]. Falls back to null (caller uses spawnPos).
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
     * world Y range around [anchor] with crying obsidian.
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
