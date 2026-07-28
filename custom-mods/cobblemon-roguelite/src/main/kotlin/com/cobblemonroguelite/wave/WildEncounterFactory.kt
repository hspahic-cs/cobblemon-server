package com.cobblemonroguelite.wave

import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.pokemon.Pokemon
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("cobblemon_roguelite/wave")

/**
 * Turns a resolved [WildEncounter] into the Pokémon the player actually fights.
 *
 * Split from [WildWaveGenerator] rather than folded into it because everything below needs a booted
 * server — species, moves, and abilities resolve out of registries that only exist at runtime —
 * while everything above it is pure and unit-testable. Keeping the boundary here is what lets the
 * determinism guarantee be tested at all.
 *
 * The result is an ordinary wild Cobblemon Pokémon, and that is the whole point of §2.14: wild
 * waves go through Cobblemon's own battle and capture flow, so they are catchable with no work from
 * us, and the party can grow (§2.13). Nothing here marks the Pokémon as run-owned — that belongs to
 * whoever hands it to the battle.
 */
object WildEncounterFactory {

    /**
     * Build the Pokémon for [encounter], or null if its species does not resolve.
     *
     * A null is a data fault, not a gameplay outcome: it means the pool named a species this server
     * does not have (a typo, or an addon datapack that is no longer installed). Returned rather than
     * thrown because this runs inside a wave transition, where an exception costs the player their
     * run, and because [PokemonProperties.parse] does not fail on an unknown name — it silently
     * produces the default species, which would show up as an inexplicable Bulbasaur rather than as
     * an error anyone could act on.
     */
    fun create(encounter: WildEncounter): Pokemon? {
        // getByIdentifier, not getByName: the latter forces the `cobblemon` namespace onto whatever
        // it is handed, so an addon species would report as missing here and then resolve fine in
        // the properties string — a contradiction that would be very hard to read from a log.
        if (PokemonSpecies.getByIdentifier(encounter.species.id) == null) {
            log.warn(
                "roguelite: wave {} pool names unknown species '{}' — skipping encounter",
                encounter.wave, encounter.species.id,
            )
            return null
        }

        val pokemon = runCatching { PokemonProperties.parse(encounter.propertiesString()).create() }
            .onFailure {
                log.warn("roguelite: failed to create wild encounter '{}'", encounter.propertiesString(), it)
            }
            .getOrNull() ?: return null

        // `create()` leaves the moveset empty on some paths, and a Pokémon with no moves takes the
        // battle to a state where neither side can act — the player sees "not your turn" and the run
        // is stuck. The monument respawn path in cobblemon-bridge hit exactly this; same guard.
        if (pokemon.moveSet.getMoves().isEmpty()) pokemon.initializeMoveset()

        return pokemon
    }
}
