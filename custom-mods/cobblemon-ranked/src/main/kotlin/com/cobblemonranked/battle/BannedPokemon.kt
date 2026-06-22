package com.cobblemonranked.battle

import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation

/**
 * Ranked PvP banlist. Most entries are **forms/items**, not base species — base Kyogre is fine, but
 * Primal Kyogre (holding a Blue Orb) is banned; base Rayquaza is fine, Mega Rayquaza (knows Dragon
 * Ascent) is not. So detection keys off held item / aspect / move at team-select time, never the
 * bare species (except Miraidon, which is banned outright).
 *
 * Which entries are active is driven by [com.cobblemonranked.config.RankedConfig.bannedForms] (a list
 * of the keys below); detection logic for each key lives here. Adding a brand-new ban category needs
 * code here, but toggling the existing ones is config-only.
 */
object BannedPokemon {

    const val MEGA_LEGENDARY = "mega_legendary"
    const val MEGA_RAYQUAZA = "mega_rayquaza"
    const val PRIMAL_KYOGRE = "primal_kyogre"
    const val PRIMAL_GROUDON = "primal_groudon"
    const val ULTRA_NECROZMA = "ultra_necrozma"
    const val ZACIAN_CROWNED = "zacian_crowned"
    const val ZAMAZENTA_CROWNED = "zamazenta_crowned"
    const val CALYREX_SHADOW = "calyrex_shadow"
    const val MIRAIDON = "miraidon"

    /** The default banlist (all keys). Mirrored as the [RankedConfig.bannedForms] default. */
    val DEFAULT: List<String> = listOf(
        MEGA_LEGENDARY, MEGA_RAYQUAZA, PRIMAL_KYOGRE, PRIMAL_GROUDON, ULTRA_NECROZMA,
        ZACIAN_CROWNED, ZAMAZENTA_CROWNED, CALYREX_SHADOW, MIRAIDON,
    )

    private const val MS = "mega_showdown"

    /** Legendaries explicitly ALLOWED to Mega Evolve despite [MEGA_LEGENDARY] (they're balanced
     *  enough to permit). Mega Latios + Mega Latias. */
    private val MEGA_LEGENDARY_ALLOWED = setOf("latios", "latias")

    /**
     * Human-readable ban reason if [pokemon] is illegal under the active [enabled] keys, else null.
     * Detection is at team-select time, when the form-enabling held item is already attached.
     */
    fun banReason(pokemon: Pokemon, enabled: Set<String>): String? {
        if (enabled.isEmpty()) return null
        val species = pokemon.species.resourceIdentifier.path.lowercase()
        val held = heldItemId(pokemon)

        if (MIRAIDON in enabled && species == "miraidon") return "Miraidon"
        if (MEGA_RAYQUAZA in enabled && species == "rayquaza" && knowsMove(pokemon, "dragonascent"))
            return "Mega Rayquaza"
        if (MEGA_LEGENDARY in enabled && species !in MEGA_LEGENDARY_ALLOWED &&
            (pokemon.isLegendary() || pokemon.isMythical()) && isMegaStone(held))
            return "Mega ${pokemon.species.name}"
        if (PRIMAL_KYOGRE in enabled && species == "kyogre" && isItem(held, "blue_orb")) return "Primal Kyogre"
        if (PRIMAL_GROUDON in enabled && species == "groudon" && isItem(held, "red_orb")) return "Primal Groudon"
        if (ULTRA_NECROZMA in enabled && species == "necrozma" && isItem(held, "ultranecrozium_z"))
            return "Ultra Necrozma"
        if (ZACIAN_CROWNED in enabled && species == "zacian" && isItem(held, "rusted_sword")) return "Zacian-Crowned"
        if (ZAMAZENTA_CROWNED in enabled && species == "zamazenta" && isItem(held, "rusted_shield"))
            return "Zamazenta-Crowned"
        if (CALYREX_SHADOW in enabled && species == "calyrex" && "shadow-rider" in pokemon.aspects)
            return "Calyrex-Shadow"
        return null
    }

    /** The first ban reason across [team], or null if the whole team is legal. */
    fun firstBanned(team: List<Pokemon>, enabled: Set<String>): String? =
        team.firstNotNullOfOrNull { banReason(it, enabled) }

    private fun heldItemId(pokemon: Pokemon): ResourceLocation? {
        val stack = pokemon.heldItem()
        if (stack.isEmpty) return null
        return BuiltInRegistries.ITEM.getKey(stack.item)
    }

    private fun isItem(id: ResourceLocation?, path: String): Boolean =
        id != null && id.namespace == MS && id.path == path

    /** Any mega_showdown mega stone (the `*ite` / `*ite_x` / `*ite_y` items, plus the generic ones). */
    private fun isMegaStone(id: ResourceLocation?): Boolean {
        if (id == null || id.namespace != MS) return false
        val p = id.path
        return p == "mega_stone" || p == "mega_stone_crystal" ||
            p.endsWith("ite") || p.endsWith("ite_x") || p.endsWith("ite_y")
    }

    private fun knowsMove(pokemon: Pokemon, moveId: String): Boolean =
        pokemon.moveSet.getMoves().any { it.name.equals(moveId, ignoreCase = true) }
}
