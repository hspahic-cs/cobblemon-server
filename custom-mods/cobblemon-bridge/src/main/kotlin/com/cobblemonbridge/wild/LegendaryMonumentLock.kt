package com.cobblemonbridge.wild

import com.cobblemon.mod.common.api.pokemon.PokemonProperties
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
import net.neoforged.bus.api.EventPriority
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.loading.FMLPaths
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Legendary Monuments per-pedestal one-shot lock.
 *
 * Drain-on-activation model:
 *  - When LM spawns a legendary inside an LM structure, we cancel its entity, drain
 *    the activation block to crying obsidian (permanently spending the altar), and
 *    re-spawn the same species/level via Cobblemon's own spawn API. This sidesteps
 *    LM's incomplete spawn pipeline (no moveset init, no client sync) — the
 *    re-spawned entity is a normal wild Pokemon.
 *  - Outcome (catch / flee / loss / disconnect) doesn't matter: the altar is already
 *    drained on activation, so there's no post-battle bookkeeping.
 *  - Only one LM legendary may be alive at a time. The slot clears when the
 *    re-spawned entity leaves the world (caught, despawned, or killed).
 *
 * Persistence: `config/cobblemon-bridge/runtime/spent_altars.json`.
 * Admin reset: `/monument admin reset`.
 */
object LegendaryMonumentLock {

    private const val LM_NAMESPACE = "legendarymonuments"
    private const val DATA_FILE = "spent_altars.json"
    private val GSON = Gson()

    private var dataFile: Path? = null

    /** Pedestal block positions that have been permanently spent. */
    private val spentAltars: MutableSet<BlockPos> = mutableSetOf()

    /** The legendary we re-spawned and are tracking. Cleared when it leaves the world. */
    @Volatile private var activeLmPokemon: Pokemon? = null

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
        CobblemonBridge.logger.info("monument-lock: {} spent pedestal(s) loaded", spentAltars.size)
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    fun onEntityJoinLevel(event: EntityJoinLevelEvent) {
        val entity = event.entity as? PokemonEntity ?: return
        val level = event.level as? ServerLevel ?: return
        val pokemon = entity.pokemon
        if (!pokemon.isLegendary() && !pokemon.isMythical()) return
        if (!isInsideLmStructure(entity)) return

        // If we've already re-spawned this entity, let it through — it's our doing.
        if (pokemon === activeLmPokemon) return

        val pedestal = findPedestal(level, entity.blockPosition())

        if (pedestal != null && pedestal in spentAltars) {
            event.isCanceled = true
            CobblemonBridge.logger.debug(
                "monument-lock: cancelled {} — pedestal at {} is spent",
                pokemon.species.name, pedestal,
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
                "monument-lock: cancelled {} — {} already active",
                pokemon.species.name, activeLmPokemon!!.species.name,
            )
            level.server.playerList.players.forEach {
                it.sendSystemMessage(Component.literal(
                    "§6[Legendary Monument] §7Another legendary is already active — wait for it to be resolved first."
                ))
            }
            return
        }

        // Drain + re-spawn. Cancel LM's entity so it never enters the world; the
        // re-spawn we queue will be a normal Cobblemon-spawned wild Pokemon with
        // proper moveset, client sync, and despawn behavior.
        event.isCanceled = true

        val species = pokemon.species.name
        val level_ = pokemon.level
        val shiny = pokemon.shiny
        val spawnPos = entity.position()

        val server = level.server
        server.execute {
            // Drain first — ensures the altar is spent even if re-spawn fails for any reason.
            if (pedestal != null) {
                spendAltar(pedestal)
                drainPedestal(level, pedestal)
            }

            val propsString = buildString {
                append(species)
                append(" level=").append(level_)
                if (shiny) append(" shiny")
            }
            val props = PokemonProperties.parse(propsString)
            val newEntity = props.createEntity(level)
            newEntity.moveTo(spawnPos.x, spawnPos.y, spawnPos.z, entity.yRot, entity.xRot)
            activeLmPokemon = newEntity.pokemon
            if (!level.addFreshEntity(newEntity)) {
                CobblemonBridge.logger.warn(
                    "monument-lock: failed to add re-spawned {} at {}",
                    species, spawnPos,
                )
                activeLmPokemon = null
                return@execute
            }

            CobblemonBridge.logger.info(
                "monument-lock: drained pedestal {} and re-spawned {} (level {}{}) at {}",
                pedestal, species, level_, if (shiny) ", shiny" else "", spawnPos,
            )
            level.server.playerList.players.forEach {
                it.sendSystemMessage(Component.literal(
                    "§6[Legendary Monument] §fA wild ${species} has appeared at a Legendary Monument! " +
                    "§7This is your only chance..."
                ))
            }
        }
    }

    @SubscribeEvent
    fun onEntityLeaveLevel(event: EntityLeaveLevelEvent) {
        val entity = event.entity as? PokemonEntity ?: return
        val active = activeLmPokemon ?: return
        if (entity.pokemon !== active) return
        activeLmPokemon = null
        CobblemonBridge.logger.info(
            "monument-lock: {} left world — slot cleared",
            active.species.name,
        )
    }

    fun reset() {
        spentAltars.clear()
        activeLmPokemon = null
        try { dataFile?.let { Files.deleteIfExists(it) } } catch (_: Exception) {}
        CobblemonBridge.logger.info("monument-lock: all pedestals reset by admin")
    }

    /**
     * Scans ±16 XZ and -24..+4 Y around [spawnPos] for the nearest LM activation block.
     * Covers pedestals, locks, shrines, stakes, and other spawner blocks. Large radius
     * needed for structures like Kyurem Cave where the pokemon spawns far from the pedestal.
     */
    private fun findPedestal(level: ServerLevel, spawnPos: BlockPos): BlockPos? {
        var nearest: BlockPos? = null
        var nearestDist = Int.MAX_VALUE
        for (dx in -16..16) {
            for (dy in -24..4) {
                for (dz in -16..16) {
                    val pos = spawnPos.offset(dx, dy, dz)
                    val id = level.getBlockState(pos).block
                        .builtInRegistryHolder().key()?.location() ?: continue
                    if (id.namespace == LM_NAMESPACE && isActivationBlock(id.path)) {
                        val dist = dx * dx + dy * dy + dz * dz
                        if (dist < nearestDist) {
                            nearestDist = dist
                            nearest = pos
                        }
                    }
                }
            }
        }
        return nearest
    }

    private fun isActivationBlock(path: String): Boolean =
        path.endsWith("_pedestal") ||
        path.endsWith("_lock") ||
        path.endsWith("_shrine") ||
        path.endsWith("_stake") ||
        path == "pokemon_trial_spawner" ||
        path == "sanctuary_block" ||
        path == "hoopa_boss_summon"

    /** Replaces only the single pedestal block with crying obsidian. */
    private fun drainPedestal(level: ServerLevel, pedestal: BlockPos) {
        level.setBlock(pedestal, Blocks.CRYING_OBSIDIAN.defaultBlockState(), 3)
        CobblemonBridge.logger.info("monument-lock: drained pedestal at {}", pedestal)
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

    private fun spendAltar(pedestal: BlockPos) {
        spentAltars.add(pedestal)
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
