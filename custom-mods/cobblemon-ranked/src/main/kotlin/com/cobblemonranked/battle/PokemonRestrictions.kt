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

/** True if the species carries Cobblemon's `ultra_beast` label (e.g. Nihilego, Buzzwole). */
fun Pokemon.isUltraBeast(): Boolean =
    species.labels.any { it.equals("ultra_beast", ignoreCase = true) || it.equals("ultrabeast", ignoreCase = true) }

/**
 * Whether a Pokémon counts toward the **tournament** special cap. Tournaments treat Legendary,
 * Paradox, AND Ultra Beast as "special" (roster cap is 4 of these; a battle subset caps at 1).
 * Broader than [countsAsLegendary], which omits Ultra Beasts (left as-is for the normal ranked
 * `maxLegendaries` rule).
 */
fun Pokemon.countsAsSpecial(): Boolean =
    isLegendary() || isParadox() || isUltraBeast()

/** Human-readable special category for menu/messaging, or null if the Pokémon isn't special. */
fun Pokemon.specialCategory(): String? = when {
    isLegendary() -> "Legendary"
    isParadox() -> "Paradox"
    isUltraBeast() -> "Ultra-Beast"
    else -> null
}
