package com.cobblemonranked.battle

import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.core.registries.BuiltInRegistries

/**
 * Whether a Pokémon counts toward the ranked `maxLegendaries` cap.
 *
 * Cobblemon's [Pokemon.isLegendary] only flags species with the `legendary` label. Mythicals carry
 * the `mythical` label (Mew, Celebi, Darkrai, and crucially **Arceus** — including every plate form
 * like Arceus-Ghost) and Paradox Pokémon carry the `paradox` label; NEITHER sets `legendary`, so
 * `isLegendary()` returns false for both — yet they're all Smogon restricted/Ubers tier. For ranked
 * team legality we count Mythical and Paradox as legendary too, against the same cap. (Without the
 * Mythical clause an Arceus slipped past the 1-legendary limit alongside a true legendary.)
 */
fun Pokemon.countsAsLegendary(): Boolean =
    isLegendary() || isMythical() || isParadox()

/** True if the species carries Cobblemon's `paradox` label. */
fun Pokemon.isParadox(): Boolean =
    species.labels.any { it.equals("paradox", ignoreCase = true) }

/** True if the species carries Cobblemon's `ultra_beast` label (e.g. Nihilego, Buzzwole). */
fun Pokemon.isUltraBeast(): Boolean =
    species.labels.any { it.equals("ultra_beast", ignoreCase = true) || it.equals("ultrabeast", ignoreCase = true) }

/**
 * Whether a Pokémon counts toward the **tournament** special cap. Tournaments treat Legendary,
 * Mythical, Paradox, AND Ultra Beast as "special" (roster cap is 4 of these; a battle subset caps
 * at 1). Broader than [countsAsLegendary] only by Ultra Beasts.
 */
fun Pokemon.countsAsSpecial(): Boolean =
    isLegendary() || isMythical() || isParadox() || isUltraBeast()

/** Human-readable special category for menu/messaging, or null if the Pokémon isn't special. */
fun Pokemon.specialCategory(): String? = when {
    isLegendary() -> "Legendary"
    isMythical() -> "Mythical"
    isParadox() -> "Paradox"
    isUltraBeast() -> "Ultra-Beast"
    else -> null
}

/**
 * The server PvP banlist. If this Pokémon would be (or become) a banned power-form, returns a short
 * reason; otherwise null. The bans target the **form trigger** (held item / move / form aspect), not
 * the base species — so base Kyogre, Dusk-Mane Necrozma, Hero-form Zacian, Calyrex Ice Rider, etc.
 * all stay legal; only the listed power-forms are blocked:
 *
 *   Mega Mewtwo (X/Y) · Mega Rayquaza · Primal Kyogre/Groudon · Ultra Necrozma ·
 *   Zacian/Zamazenta Crowned · Calyrex Shadow Rider · Miraidon
 *
 * Intentionally NOT banned: the other Mega legendaries (Latias / Latios / Diancie) and Koraidon.
 * Edit the `when` below to adjust the list (trigger item IDs are Mega Showdown's).
 */
fun Pokemon.rankedBanReason(): String? {
    val sp = species.resourceIdentifier.path.lowercase()
    val held = heldItemIdOrNull()
    fun holds(vararg ids: String) = held != null && ids.any { it == held }
    fun knows(move: String) = moveSet.getMoves().any { it.name.equals(move, ignoreCase = true) }
    return when {
        sp == "miraidon" -> "Miraidon"
        sp == "calyrex" && aspects.contains("shadow-rider") -> "Calyrex (Shadow Rider)"
        sp == "mewtwo" && holds("mega_showdown:mewtwonite_x", "mega_showdown:mewtwonite_y") -> "Mega Mewtwo"
        sp == "rayquaza" && knows("dragonascent") -> "Mega Rayquaza"
        sp == "kyogre" && holds("mega_showdown:blue_orb") -> "Primal Kyogre"
        sp == "groudon" && holds("mega_showdown:red_orb") -> "Primal Groudon"
        sp == "necrozma" && holds("mega_showdown:ultranecrozium_z") -> "Ultra Necrozma"
        sp == "zacian" && holds("mega_showdown:rusted_sword") -> "Zacian-Crowned"
        sp == "zamazenta" && holds("mega_showdown:rusted_shield") -> "Zamazenta-Crowned"
        else -> null
    }
}

/** Registry id of the held item (e.g. "mega_showdown:blue_orb"), or null if empty-handed. */
private fun Pokemon.heldItemIdOrNull(): String? {
    val stack = heldItem()
    return if (stack.isEmpty) null else BuiltInRegistries.ITEM.getKey(stack.item).toString()
}
