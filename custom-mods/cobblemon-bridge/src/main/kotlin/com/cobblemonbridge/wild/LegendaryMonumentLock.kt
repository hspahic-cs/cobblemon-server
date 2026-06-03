package com.cobblemonbridge.wild

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemonbridge.CobblemonBridge
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.ChunkPos
import net.minecraft.network.chat.Component
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.levelgen.structure.BoundingBox
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
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Legendary Monuments per-altar one-shot lock.
 *
 * Rules:
 *  - Each altar is independently one-shot. Catching or fleeing a legendary permanently
 *    spends that altar; all other altars in the world remain available.
 *  - Only one LM legendary may be alive in the world at a time. A second spawn attempt
 *    while one is active is cancelled.
 *  - The altar drains (replaced with crying obsidian) within its own structure bounding
 *    box only — adjacent structures are unaffected.
 *
 * Detection:
 *  - [EntityJoinLevelEvent] fires for all entity adds regardless of origin. LM spawns
 *    legendaries by directly constructing a [PokemonEntity] via `PokemonProperties`,
 *    bypassing Cobblemon's spawn pipeline entirely.
 *  - On entity removal, [Pokemon.isWild] distinguishes caught (false) from fled (true)
 *    for the broadcast message — both outcomes spend the altar.
 *
 * Persistence: `config/cobblemon-bridge/runtime/spent_altars.json` — list of anchor positions.
 * Admin reset: `/monument admin reset`.
 */
object LegendaryMonumentLock {

    private const val LM_NAMESPACE = "legendarymonuments"
    private const val DATA_FILE = "spent_altars.json"
    private val GSON = Gson()

    private var dataFile: Path? = null

    /** Anchor positions (bounding-box centre at minY) of permanently spent altars. */
    private val spentAltars: MutableSet<BlockPos> = mutableSetOf()

    /** The LM legendary currently live in the world. Null if none active. */
    @Volatile private var activeLmPokemon: Pokemon? = null
    @Volatile private var activeLmBoundingBox: BoundingBox? = null
    @Volatile private var activeLmAnchor: BlockPos? = null

    fun spentCount(): Int = spentAltars.size

    fun init() {
        val file = FMLPaths.CONFIGDIR.get()
            .resolve("cobblemon-bridge")
            .resolve("runtime")
            .resolve(DATA_FILE)
        dataFile = file
        if (file.exists()) {
            try {
                val type = object : TypeToken<List<Map<String, Int>>>() {}.type
                val list: List<Map<String, Int>> = GSON.fromJson(file.readText(), type)
                list.forEach { spentAltars.add(BlockPos(it["x"]!!, it["y"]!!, it["z"]!!)) }
            } catch (_: Exception) {}
        }
        CobblemonBridge.logger.info("monument-lock: {} spent altar(s) loaded", spentAltars.size)
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    fun onEntityJoinLevel(event: EntityJoinLevelEvent) {
        val entity = event.entity as? PokemonEntity ?: return
        val level = event.level as? ServerLevel ?: return
        val pokemon = entity.pokemon
        if (!pokemon.isLegendary() && !pokemon.isMythical()) return

        val (anchor, bbox) = findStructureInfo(level, entity.blockPosition()) ?: return

        if (anchor in spentAltars) {
            event.isCanceled = true
            CobblemonBridge.logger.debug(
                "monument-lock: cancelled {} join — altar at {} is spent",
                pokemon.species.name, anchor,
            )
            level.server.playerList.players.forEach {
                it.sendSystemMessage(Component.literal(
                    "§6[Legendary Monument] §7This altar has already been spent — its legendary will not return."
                ))
            }
            return
        }

        if (activeLmPokemon != null) {
            event.isCanceled = true
            CobblemonBridge.logger.debug(
                "monument-lock: cancelled {} join — {} is already active elsewhere",
                pokemon.species.name, activeLmPokemon!!.species.name,
            )
            level.server.playerList.players.forEach {
                it.sendSystemMessage(Component.literal(
                    "§6[Legendary Monument] §7Another legendary is already active — wait for it to be caught or to flee."
                ))
            }
            return
        }

        activeLmPokemon = pokemon
        activeLmBoundingBox = bbox
        activeLmAnchor = anchor
        CobblemonBridge.logger.info(
            "monument-lock: {} joined world in LM structure at {} — altar is now spent",
            pokemon.species.name, anchor,
        )
        level.server.playerList.players.forEach {
            it.sendSystemMessage(Component.literal(
                "§6[Legendary Monument] §fA wild ${pokemon.species.name} has appeared at a Legendary Monument! " +
                "§7This is your only chance..."
            ))
        }
    }

    @SubscribeEvent
    fun onEntityLeaveLevel(event: EntityLeaveLevelEvent) {
        val entity = event.entity as? PokemonEntity ?: return
        val active = activeLmPokemon ?: return
        if (entity.pokemon !== active) return

        val server = ServerLifecycleHooks.getCurrentServer() ?: return
        server.execute {
            val bbox = activeLmBoundingBox
            val anchor = activeLmAnchor ?: return@execute
            val caught = !active.isWild()
            activeLmPokemon = null
            activeLmBoundingBox = null
            activeLmAnchor = null
            spendAltar(anchor)
            if (bbox != null) drainAltar(server.overworld(), bbox)
            if (caught) {
                CobblemonBridge.logger.info("monument-lock: {} caught — altar at {} spent", active.species.name, anchor)
                server.playerList.players.forEach {
                    it.sendSystemMessage(Component.literal(
                        "§6[Legendary Monument] §f${active.species.name} was caught! " +
                        "§7The monument's power is spent — this legendary will not return."
                    ))
                }
            } else {
                CobblemonBridge.logger.info("monument-lock: {} fled — altar at {} spent", active.species.name, anchor)
                server.playerList.players.forEach {
                    it.sendSystemMessage(Component.literal(
                        "§6[Legendary Monument] §7The legendary escaped... but the monument's power is spent."
                    ))
                }
            }
        }
    }

    /** Resets all spent altars and any active legendary. Admin use only. */
    fun reset() {
        spentAltars.clear()
        activeLmPokemon = null
        activeLmBoundingBox = null
        activeLmAnchor = null
        try { dataFile?.let { Files.deleteIfExists(it) } } catch (_: Exception) {}
        CobblemonBridge.logger.info("monument-lock: all altars reset by admin")
    }

    private data class StructureInfo(val anchor: BlockPos, val bbox: BoundingBox)

    private fun findStructureInfo(level: ServerLevel, pos: BlockPos): StructureInfo? {
        val structureManager = level.structureManager()
        val registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE)
        val chunkPos = ChunkPos(pos)
        for ((key, structure) in registry.entrySet()) {
            if (key.location().namespace != LM_NAMESPACE) continue
            val starts = structureManager.startsForStructure(chunkPos) { it == structure }
            if (starts.isNotEmpty()) {
                val bb = starts.first().getBoundingBox()
                val anchor = BlockPos(bb.minX() + (bb.maxX() - bb.minX()) / 2, bb.minY(), bb.minZ() + (bb.maxZ() - bb.minZ()) / 2)
                return StructureInfo(anchor, bb)
            }
        }
        return null
    }

    private fun drainAltar(level: ServerLevel, bbox: BoundingBox) {
        val cryingObsidian = Blocks.CRYING_OBSIDIAN.defaultBlockState()
        var count = 0
        for (x in bbox.minX()..bbox.maxX()) {
            for (y in bbox.minY()..bbox.maxY()) {
                for (z in bbox.minZ()..bbox.maxZ()) {
                    val pos = BlockPos(x, y, z)
                    val state = level.getBlockState(pos)
                    if (state.block.builtInRegistryHolder().key()?.location()?.namespace == LM_NAMESPACE) {
                        level.setBlock(pos, cryingObsidian, 3)
                        count++
                    }
                }
            }
        }
        CobblemonBridge.logger.info("monument-lock: drained {} LM blocks in bbox {}", count, bbox)
    }

    private fun spendAltar(anchor: BlockPos) {
        spentAltars.add(anchor)
        val file = dataFile ?: return
        try {
            file.parent.createDirectories()
            val list = spentAltars.map { mapOf("x" to it.x, "y" to it.y, "z" to it.z) }
            val tmp = file.resolveSibling("$DATA_FILE.tmp")
            tmp.writeText(GSON.toJson(list))
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: Exception) {
            CobblemonBridge.logger.warn("monument-lock: failed to persist spent altars", e)
        }
    }
}
