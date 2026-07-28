package com.cobblemonroguelite.starter

import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.resources.ResourceLocation
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("cobblemon_roguelite/starter")

/**
 * Turns the species a player picked into the Pokémon their run begins with.
 *
 * The split from [StarterOfferFactory] is the same one
 * [com.cobblemonroguelite.wave.WildEncounterFactory] makes from
 * [com.cobblemonroguelite.wave.WildWaveGenerator], for the same reason: choosing *what* is pure and
 * testable, building it needs a booted server's registries. Keeping the line here is what lets the
 * offer's determinism be tested at all.
 *
 * ### What is not rolled from the seed yet, and why that is consistent rather than an oversight
 *
 * §2.16 requires everything in a run to be derivable from the seed or persisted, and names IVs,
 * nature, gender, shiny and ability as the values Cobblemon rolls unseeded inside `create()`. Those
 * are not written into the properties string here — exactly as they are not in the wild encounter
 * path — because §2.17 puts the IV floor on a per-player high-water mark that does not exist yet,
 * and half of that decision (seeded shiny, unseeded IVs) would have to be undone to add the other.
 * The starter Pokémon is persisted the moment the run is created, so the *run* is still stable
 * across a disconnect; what is open is the narrower question of whether two identical seeds produce
 * identical starters, which they do not yet.
 */
object StarterFactory {

    /**
     * Build the run's first party member, or null if [species] does not resolve on this server.
     *
     * Null is a data fault rather than a gameplay outcome and the caller must not turn it into a
     * failed run start: the player has already been charged. `PokemonProperties.parse` does not fail
     * on an unknown name — it silently yields the default species — so the existence check has to
     * happen here or a typo in the starter pool hands somebody a Bulbasaur they did not pick.
     */
    fun create(species: ResourceLocation, level: Int): Pokemon? {
        // getByIdentifier, not getByName: the latter forces the `cobblemon` namespace, so an addon
        // species would report missing here and then resolve fine in the properties string.
        if (PokemonSpecies.getByIdentifier(species) == null) {
            log.warn("roguelite: starter pool names unknown species '{}'", species)
            return null
        }
        val properties = "species=$species level=$level"
        val pokemon = runCatching { PokemonProperties.parse(properties).create() }
            .onFailure { log.warn("roguelite: failed to create starter '{}'", properties, it) }
            .getOrNull() ?: return null

        // `create()` leaves the moveset empty on some paths, and a Pokémon with no moves takes the
        // battle to a state where neither side can act. Same guard as the wild encounter path.
        if (pokemon.moveSet.getMoves().isEmpty()) pokemon.initializeMoveset()
        return pokemon
    }
}
