package com.cobblemonbridge.wild

import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemonbridge.CobblemonBridge
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.server.level.ServerLevel
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
 *  - Structure namespace check (`legendarymonuments:*`) at spawn time via [getAllStructuresAt].
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

    /** Scan radius (each axis) around the spawn position when draining altar blocks. */
    private const val DRAIN_RADIUS = 8

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
            val capturePos = activeLmPos
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
            if (captureLevel != null && capturePos != null) {
                drainAltar(captureLevel, capturePos)
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
     * Replaces every `legendarymonuments:*` block within [DRAIN_RADIUS] of [origin] with
     * crying obsidian, giving the monument a visually "spent" appearance.
     */
    private fun drainAltar(level: ServerLevel, origin: BlockPos) {
        val cryingObsidian = Blocks.CRYING_OBSIDIAN.defaultBlockState()
        var count = 0
        for (x in -DRAIN_RADIUS..DRAIN_RADIUS) {
            for (y in -DRAIN_RADIUS..DRAIN_RADIUS) {
                for (z in -DRAIN_RADIUS..DRAIN_RADIUS) {
                    val pos = origin.offset(x, y, z)
                    val state = level.getBlockState(pos)
                    if (state.block.builtInRegistryHolder().key()?.location()?.namespace == LM_NAMESPACE) {
                        level.setBlock(pos, cryingObsidian, 3)
                        count++
                    }
                }
            }
        }
        CobblemonBridge.logger.info("monument-lock: drained {} LM blocks around {}", count, origin)
    }

    private fun isInsideLmStructure(entity: PokemonEntity): Boolean {
        val level = entity.level() as? ServerLevel ?: return false
        val structureManager = level.structureManager()
        val registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE)
        return structureManager.getAllStructuresAt(entity.blockPosition()).keys
            .any { structure -> registry.getKey(structure)?.namespace == LM_NAMESPACE }
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
