package com.cobblemonroguelite.starter

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.pokedex.PokedexEntryProgress
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import org.slf4j.LoggerFactory
import java.util.UUID

private val log = LoggerFactory.getLogger("cobblemon_roguelite/starter")

/**
 * The set of species a player has unlocked for their starter catalogue (§2.15).
 *
 * Separated from [StarterPoolSource] rather than folded into it because the two answer different
 * questions and must be allowed to fail differently: a missing baseline pool is a server
 * misconfiguration, an empty unlock set is a brand-new player and completely normal.
 *
 * Kept as an interface for the same reason the pool is — determinism tests need a fixed unlock set,
 * and reaching Cobblemon's player data store requires a booted server.
 */
fun interface CaughtSpeciesSource {

    /**
     * Species [player] has caught. Empty is a valid answer and must not be treated as an error.
     *
     * Returns species ids only, with no indication of *how* each was earned. The catalogue builder
     * unions this with the baseline pool and the result is indistinguishable from that point on,
     * which is what stops unlock status from leaking into pricing (§2.15) — the same separation, moved
     * from weighting to cost when §2.13 became a budget.
     */
    fun caughtSpecies(player: UUID): Set<ResourceLocation>
}

/**
 * Reads Cobblemon's own per-player Pokédex. No tracking of our own, which is the whole reason
 * §2.15 chose the server Pokédex: it already exists, it survives publication of this mod, and it
 * works in single-player.
 *
 * ### Call this on the server thread, and prefer the [ServerPlayer] overload
 *
 * `Cobblemon.playerDataManager` hands out `PokedexManager` instances from
 * `CachedPlayerDataStoreFactory`, whose cache is a plain `mutableMapOf` with no synchronization and
 * whose eviction pass runs on Cobblemon's save scheduler. Calling from a battle thread or an async
 * command puts two writers on that map.
 *
 * Reading an *offline* player does work — the factory falls through to the file backend for any
 * UUID — but it is not free: the load inserts into that cache, the file backend materialises and
 * saves a default record when no file exists, and eviction only happens on the next save sweep. So
 * a lookup for a UUID that never played creates a Pokédex file for it. That is fine for the catalogue,
 * which is built when the player types the command and is therefore online; it is a trap for any
 * future "show me another player's unlocks" tooling, which should take a [ServerPlayer].
 */
object CobblemonPokedexUnlocks : CaughtSpeciesSource {

    fun caughtSpecies(player: ServerPlayer): Set<ResourceLocation> = caughtSpecies(player.uuid)

    override fun caughtSpecies(player: UUID): Set<ResourceLocation> {
        val dex = runCatching { Cobblemon.playerDataManager.getPokedexData(player) }
            .onFailure { log.warn("roguelite: could not read Pokédex for {} — offering baseline only", player, it) }
            .getOrNull() ?: return emptySet()

        // Snapshot the keys before filtering. `speciesRecords` is the live map Cobblemon mutates on
        // every catch, and a catch landing mid-iteration would throw out of a command that has
        // nothing to do with catching.
        val speciesIds = runCatching { dex.speciesRecords.keys.toList() }
            .onFailure { log.warn("roguelite: Pokédex for {} changed while reading — offering baseline only", player, it) }
            .getOrNull() ?: return emptySet()

        return speciesIds.filterTo(mutableSetOf()) {
            dex.getHighestKnowledgeForSpecies(it) >= PokedexEntryProgress.CAUGHT
        }
    }
}

/**
 * Fixed unlock set, for tests and for a server that wants meta-progression off entirely — passing
 * an empty set here reduces the catalogue to the baseline pool, which §2.15 requires to be playable on
 * its own anyway.
 */
class FixedCaughtSpecies(private val caught: Set<ResourceLocation>) : CaughtSpeciesSource {
    override fun caughtSpecies(player: UUID): Set<ResourceLocation> = caught
}
