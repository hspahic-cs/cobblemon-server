package com.cobblemonpokerogue.bridge.presentation;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.pokemon.Species;
import java.util.Locale;
import org.jetbrains.annotations.Nullable;

/**
 * Resolves a {@link com.cobblemonpokerogue.bridge.api.RunSnapshot#leadSpecies()} value to a
 * Cobblemon species. The poller emits PokeRogue's NUMERIC SpeciesId as a decimal string; the
 * national dex number is {@code id % 2000} (the 2xxx/4xxx/6xxx/8xxx prefixes are alolan/
 * galarian/hisuian/paldean regional forms, rendered here as the base species). Non-numeric
 * strings fall back to a name lookup for forward compatibility.
 */
public final class RogueSpecies {

    private RogueSpecies() {}

    @Nullable
    public static Species resolve(String leadSpecies) {
        if (leadSpecies == null || leadSpecies.isBlank()) {
            return null;
        }
        try {
            int id = Integer.parseInt(leadSpecies.trim());
            int dex = id % 2000;
            if (dex <= 0) {
                return null;
            }
            return PokemonSpecies.INSTANCE.getByPokedexNumber(dex, "cobblemon");
        } catch (NumberFormatException notNumeric) {
            return PokemonSpecies.getByName(
                    leadSpecies.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", ""));
        }
    }
}
