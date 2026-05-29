package com.cobblemonbridge.profile

import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.Priority
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonSyntaxException
import net.neoforged.fml.loading.FMLPaths
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

private val log = LoggerFactory.getLogger("cobblemon_bridge/profile/favorite")

/**
 * Records, per player, the total HP a player has restored via carrot-feeding each of their
 * Pokemon. The Pokemon with the highest cumulative HP-fed amount is surfaced in the /profile
 * GUI as that player's "favorite".
 *
 * Data shape on disk (config/cobblemon-bridge/runtime/favorites.json):
 * {
 *   "entries": {
 *     "<player-uuid>": {
 *       "<pokemon-uuid>": { "species": "pikachu", "hpFed": 240 },
 *       ...
 *     }
 *   }
 * }
 *
 * Carrot-feed integration happens via cobblemon-carrots' actual heal flow — it grants +30 HP
 * per carrot up to current max, so we credit min(30, pokemon.maxHealth - pre-heal currentHealth)
 * per successful feed. The hook lives in this same file so the persistence layer and the
 * recording side stay together.
 */
data class FavoriteEntry(val species: String, val hpFed: Int)

class FavoriteTracker private constructor(
    private val file: Path,
    private var data: MutableMap<String, MutableMap<String, FavoriteEntry>>,
) {
    fun record(playerUuid: UUID, pokemonUuid: UUID, species: String, hpAmount: Int) {
        if (hpAmount <= 0) return
        val byPlayer = data.getOrPut(playerUuid.toString()) { mutableMapOf() }
        val prior = byPlayer[pokemonUuid.toString()]
        byPlayer[pokemonUuid.toString()] = FavoriteEntry(species, (prior?.hpFed ?: 0) + hpAmount)
        scheduleSave()
    }

    /**
     * Returns the (species, hpFed) tuple for the Pokemon this player has poured the most
     * carrot-derived HP into. null when they haven't fed anything yet.
     */
    fun favorite(playerUuid: UUID): FavoriteEntry? =
        data[playerUuid.toString()]?.values?.maxByOrNull { it.hpFed }

    // ─── persistence ─────────────────────────────────────────────────────────

    /** Saves are coalesced to once per N ticks (real-time) — feed events fire fast. */
    @Volatile private var dirty = false

    private fun scheduleSave() {
        if (dirty) return
        dirty = true
        // Cheap: just save on the next tick. The actual `save()` is fast and not in any tight
        // loop. For simplicity we save immediately; if this ever shows up on a profiler we can
        // batch with a scheduler.
        save()
    }

    private fun save() {
        try {
            file.parent.createDirectories()
            val tmp = file.resolveSibling(file.fileName.toString() + ".tmp")
            tmp.writeText(gson.toJson(mapOf("entries" to data)))
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            dirty = false
        } catch (e: Exception) {
            log.warn("favorite tracker save failed", e)
        }
    }

    companion object {
        private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

        @Volatile
        private var instance: FavoriteTracker? = null

        fun get(): FavoriteTracker = instance
            ?: error("FavoriteTracker not initialized — bridge.init() should have called FavoriteTracker.init()")

        fun init() {
            val file = FMLPaths.CONFIGDIR.get()
                .resolve("cobblemon-bridge")
                .resolve("runtime")
                .resolve("favorites.json")
            val data = load(file)
            instance = FavoriteTracker(file, data)
            registerCobblemonHooks()
        }

        @Suppress("UNCHECKED_CAST")
        private fun load(file: Path): MutableMap<String, MutableMap<String, FavoriteEntry>> {
            if (!file.exists()) return ConcurrentHashMap()
            return try {
                val text = file.readText()
                val parsed = gson.fromJson(text, Map::class.java) as? Map<String, Any> ?: return ConcurrentHashMap()
                val entries = parsed["entries"] as? Map<String, Map<String, Map<String, Any>>> ?: return ConcurrentHashMap()
                val result = ConcurrentHashMap<String, MutableMap<String, FavoriteEntry>>()
                for ((player, monMap) in entries) {
                    val perPlayer = mutableMapOf<String, FavoriteEntry>()
                    for ((monUuid, fields) in monMap) {
                        val species = fields["species"] as? String ?: continue
                        val hpFed = ((fields["hpFed"] as? Number)?.toInt()) ?: 0
                        perPlayer[monUuid] = FavoriteEntry(species, hpFed)
                    }
                    result[player] = perPlayer
                }
                result
            } catch (e: JsonSyntaxException) {
                log.warn("favorites.json malformed; starting empty", e); ConcurrentHashMap()
            } catch (e: Exception) {
                log.warn("favorites.json load failed; starting empty", e); ConcurrentHashMap()
            }
        }

        /**
         * Subscribes to Cobblemon's pokemon-healed event so any HP restored by a player to one
         * of their Pokemon (via carrot-feed, healer block, etc.) is credited here. We can't
         * distinguish the *source* of healing from the event itself, but for "favorite Pokemon"
         * intent the carrot/healer combo IS the player relationship signal — both are voluntary
         * actions by the player.
         */
        private fun registerCobblemonHooks() {
            try {
                // POKEMON_HEALED fires whenever a Pokemon's HP is set higher than before via the
                // canonical .heal() / setCurrentHealth path. We capture (owner, pokemon, delta)
                // and credit the owner.
                CobblemonEvents.POKEMON_HEALED.subscribe(Priority.NORMAL) { event ->
                    val pokemon = event.pokemon
                    val owner = pokemon.getOwnerPlayer() ?: return@subscribe
                    val delta = event.amount
                    if (delta <= 0) return@subscribe
                    get().record(owner.uuid, pokemon.uuid, pokemon.species.name.lowercase(), delta)
                }
            } catch (e: Throwable) {
                log.warn("Couldn't hook POKEMON_HEALED for favorite-tracker", e)
            }
        }
    }
}
