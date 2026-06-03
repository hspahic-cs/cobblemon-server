package com.cobblemonbridge.wild

import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.events.CobblemonEvents
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
import net.neoforged.neoforge.server.ServerLifecycleHooks
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
 * Rules:
 *  - Each pedestal is independently one-shot. Catching or fleeing permanently spends
 *    that pedestal; all others remain available.
 *  - Only one LM legendary may be alive in the world at a time.
 *  - On spend, only the single pedestal block is replaced with crying obsidian —
 *    adjacent pedestals (e.g. Dialga/Palkia at Spear Pillar) are untouched.
 *
 * Pedestal identification: scan ±4 XZ, -8..+1 Y around the legendary's spawn position
 * for the nearest `legendarymonuments:*_pedestal` block. That block's position is the
 * altar key — precise enough that pedestals 10 blocks apart are never confused.
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

    /** Non-null while the legendary is alive and blocking a second spawn. Cleared on battle-flee. */
    @Volatile private var activeLmPokemon: Pokemon? = null
    /** The pokemon we're tracking for drain — same as activeLmPokemon except after a battle-flee. */
    @Volatile private var trackedLmPokemon: Pokemon? = null
    @Volatile private var activeLmPedestal: BlockPos? = null
    @Volatile private var activeLmLevel: ServerLevel? = null

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

        // Clear the blocking slot whenever a battle involving our legendary ends —
        // covers flee, player disconnect, and player loss. In all these cases the legendary
        // entity stays alive in the world, so onEntityLeaveLevel won't fire and the slot
        // would stay blocked forever. Keep trackedLmPokemon/activeLmPedestal so the altar
        // still drains when the entity eventually leaves the world.
        val clearOnBattleEnd = { battle: com.cobblemon.mod.common.api.battles.model.PokemonBattle ->
            val active = activeLmPokemon ?: return@clearOnBattleEnd
            val isOurBattle = battle.actors.any { actor ->
                actor.pokemonList.any { it.effectedPokemon === active }
            }
            if (!isOurBattle) return@clearOnBattleEnd
            activeLmPokemon = null
            CobblemonBridge.logger.info(
                "monument-lock: battle ended for {} — active slot cleared, legendary still in world",
                active.species.name,
            )
        }
        CobblemonEvents.BATTLE_FLED.subscribe(Priority.NORMAL) { event ->
            clearOnBattleEnd(event.battle)
        }
        CobblemonEvents.BATTLE_VICTORY.subscribe(Priority.NORMAL) { event ->
            clearOnBattleEnd(event.battle)
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    fun onEntityJoinLevel(event: EntityJoinLevelEvent) {
        val entity = event.entity as? PokemonEntity ?: return
        val level = event.level as? ServerLevel ?: return
        val pokemon = entity.pokemon
        if (!pokemon.isLegendary() && !pokemon.isMythical()) return
        if (!isInsideLmStructure(entity)) return

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

        // LM spawns via PokemonProperties without going through Cobblemon's full spawn
        // pipeline, so the moveset may not be initialized — battle UI won't show moves.
        if (pokemon.moveSet.getMoves().isEmpty()) {
            pokemon.initializeMoveset()
            CobblemonBridge.logger.info(
                "monument-lock: initialized moveset for LM-spawned {}",
                pokemon.species.name,
            )
        }

        activeLmPokemon = pokemon
        trackedLmPokemon = pokemon
        activeLmPedestal = pedestal
        activeLmLevel = level
        CobblemonBridge.logger.info(
            "monument-lock: {} spawned at pedestal {} — altar is now spent",
            pokemon.species.name, pedestal,
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
        val tracked = trackedLmPokemon ?: return
        if (entity.pokemon !== tracked) return

        val server = ServerLifecycleHooks.getCurrentServer() ?: return
        server.execute {
            val pedestal = activeLmPedestal
            val level = activeLmLevel
            val caught = !tracked.isWild()
            activeLmPokemon = null
            trackedLmPokemon = null
            activeLmPedestal = null
            activeLmLevel = null
            if (pedestal != null) {
                spendAltar(pedestal)
                if (level != null) drainPedestal(level, pedestal)
            }
            if (caught) {
                CobblemonBridge.logger.info("monument-lock: {} caught — pedestal {} spent", tracked.species.name, pedestal)
                server.playerList.players.forEach {
                    it.sendSystemMessage(Component.literal(
                        "§6[Legendary Monument] §f${tracked.species.name} was caught! " +
                        "§7The monument's power is spent — this legendary will not return."
                    ))
                }
            } else {
                CobblemonBridge.logger.info("monument-lock: {} left world — pedestal {} spent", tracked.species.name, pedestal)
                server.playerList.players.forEach {
                    it.sendSystemMessage(Component.literal(
                        "§6[Legendary Monument] §7The legendary left the world... the monument's power is spent."
                    ))
                }
            }
        }
    }

    fun reset() {
        spentAltars.clear()
        activeLmPokemon = null
        trackedLmPokemon = null
        activeLmPedestal = null
        activeLmLevel = null
        try { dataFile?.let { Files.deleteIfExists(it) } } catch (_: Exception) {}
        CobblemonBridge.logger.info("monument-lock: all pedestals reset by admin")
    }

    /**
     * Scans ±16 XZ and -24..+4 Y around [spawnPos] for the nearest LM activation block.
     * Covers pedestals (*_pedestal), locks (*_lock), and other spawner blocks.
     * Large radius needed for structures like Kyurem Cave where the pokemon spawns
     * far from the activation pedestal.
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
