package com.cobblemonranked.battle

import com.cobblemon.mod.common.pokemon.Pokemon

/**
 * Whether a Pokémon counts toward the ranked `maxLegendaries` cap.
 *
 * Cobblemon's [Pokemon.isLegendary] only flags species with the `legendary` label (mythical and
 * ultra_beast are separate booleans). Paradox Pokémon carry the `paradox` label and NO `legendary`
 * label, so `isLegendary()` returns false for them — yet many (Flutter Mane, Iron Valiant, Roaring
 * Moon, Iron Bundle, …) are Smogon Ubers-tier. For ranked team legality we treat Paradox as
 * legendary so they count against the same cap.
 */
fun Pokemon.countsAsLegendary(): Boolean =
    isLegendary() || isParadox()

/** True if the species carries Cobblemon's `paradox` label. */
fun Pokemon.isParadox(): Boolean =
    species.labels.any { it.equals("paradox", ignoreCase = true) }
