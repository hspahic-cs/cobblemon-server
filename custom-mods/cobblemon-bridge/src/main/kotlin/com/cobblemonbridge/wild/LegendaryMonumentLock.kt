package com.cobblemonbridge.wild

import com.cobblemon.mod.common.api.pokemon.PokemonProperties
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
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent

/**
 * Legendary Monuments one-shot lock.
 *
 * World state is the source of truth: every activation drains the pedestal block
 * to crying obsidian, permanently removing it. There's no per-altar set to track —
 * if the pedestal block exists, it can spawn; if it's crying obsidian, it can't.
 *
 * Flow on legendary spawn inside an LM structure:
 *  - Cancel LM's entity (its spawn pipeline is incomplete: no moveset init, no client sync).
 *  - Drain the activation block.
 *  - Re-spawn the same species/level via Cobblemon's spawn API.
 * The re-spawned entity is a normal wild Pokemon. Outcome (catch / flee / loss /
 * disconnect) doesn't matter — the altar is already gone.
 *
 * Only one LM legendary may be alive at a time. The slot clears when the
 * re-spawned entity leaves the world.
 *
 * Admin unstick: `/monument admin reset` clears the active-legendary slot if the
 * entity ref ever gets stuck. To restore a drained altar, `/setblock` the crying
 * obsidian back to the original pedestal block.
 */
object LegendaryMonumentLock {

    private const val LM_NAMESPACE = "legendarymonuments"

    // Deploy-skip canary: this comment exists to verify code-only PRs don't trigger the dev deploy.

    /** The legendary we re-spawned and are tracking. Cleared when it leaves the world. */
    @Volatile private var activeLmPokemon: Pokemon? = null

    fun isActive(): Boolean = activeLmPokemon != null

    fun init() {
        CobblemonBridge.logger.info("monument-lock: drain-on-activation mode (no persistence)")
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

        // Player-owned legendaries (sent out from a party in battle) spawn entities
        // too — don't intercept them. Only wild LM spawns matter here.
        if (!pokemon.isWild()) return

        // No pedestal in range means this isn't a fresh LM activation. Most common
        // cause: chunk reload of a re-spawned legendary whose altar is already
        // drained (crying obsidian, different namespace, scan returns null). Leave
        // the entity alone — it's already a normal wild Pokemon from a prior run.
        val pedestal = findPedestal(level, entity.blockPosition())
        if (pedestal == null) return

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
        val pokemonLevel = pokemon.level
        val shiny = pokemon.shiny
        val spawnPos = entity.position()

        val server = level.server
        server.execute {
            drainPedestal(level, pedestal)

            val propsString = buildString {
                append(species)
                append(" level=").append(pokemonLevel)
                if (shiny) append(" shiny")
            }
            val props = PokemonProperties.parse(propsString)
            val newEntity = props.createEntity(level)
            // PokemonProperties.createEntity doesn't initialize the moveset — same gap
            // as LM's spawn path. Without this the battle UI renders without moves
            // and the player can't act ("not your turn"). See 0.7.55 history.
            if (newEntity.pokemon.moveSet.getMoves().isEmpty()) {
                newEntity.pokemon.initializeMoveset()
            }
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
                pedestal, species, pokemonLevel, if (shiny) ", shiny" else "", spawnPos,
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

    /** Admin unstick: clears the active-legendary slot. */
    fun reset() {
        activeLmPokemon = null
        CobblemonBridge.logger.info("monument-lock: active slot cleared by admin")
    }

    /**
     * Scans ±16 XZ and -24..+4 Y around [spawnPos] for the nearest LM activation block.
     * Drained pedestals are crying obsidian (minecraft namespace) and skipped automatically.
     * Large radius needed for structures like Kyurem Cave where the pokemon spawns far
     * from the pedestal.
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
        path == "hoopa_boss_summon" ||
        // Summon blocks that don't fit the suffix patterns. eternatus_cocoon is the
        // important one: unlike meltan_box (spawnMeltan + destroyBlock) and regi_statue
        // (spawnRegiAndBreak), the cocoon does NOT remove itself on use, so without this
        // it stays placed and Eternatus is re-summonable at the same spot. Draining it
        // makes the monument one-shot and routes the spawn through our re-spawn path
        // (proper moveset/sync) instead of LM's incomplete pipeline. meltan_box/regi_statue
        // are included for completeness; if the mod has already removed them by the time the
        // entity joins, findPedestal simply won't match and we leave them alone.
        path == "eternatus_cocoon" ||
        path == "meltan_box" ||
        path == "regi_statue"

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
}
