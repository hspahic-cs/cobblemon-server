package com.cobblemonroguelite.starter

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.pokemon.abilities.HiddenAbility
import net.minecraft.resources.ResourceLocation
import java.util.UUID

/**
 * What the draft screen can tell a player about a species before they buy it.
 *
 * ### Why this exists
 *
 * PokéRogue's starter select is half grid and half **dossier** — the left panel carries types, the
 * ability, the passive, the growth rate and a hexagon of the best IVs that player has ever had of
 * that species. Picking without it is picking by name and price, which is the one thing a budget
 * screen must not be: §2.13 poses a trade ("three cheap ones or one good one") that a player cannot
 * evaluate if the only visible difference between two rows is a number of points.
 *
 * ### Everything here is already ours
 *
 * Types, base stats, abilities and growth rate come out of Cobblemon's `Species`. The IV floor is
 * §2.17's per-species high-water mark, which [StarterProgression] already keeps because
 * [StarterFactory] needs it to create the Pokémon — so the "best IVs seen" hexagon is a *read of
 * state that exists*, not a new system. Nothing here is a new source of truth, which is why the
 * panel cannot disagree with the starter a player ends up with.
 *
 * ### And it is all a tooltip, on purpose
 *
 * A custom screen would draw the hexagon properly and would cost a client mod — the standalone story
 * (§1.2) and a two-jar install for everyone. §2.32 already weighed exactly that trade for the
 * segmented HP bar and deferred it, on the grounds that the state lives server-side either way so a
 * client mod later *reads what already exists* and is additive rather than a rewrite. The same
 * applies to this sheet, so the same answer does: build the data, render it as lore, and let a real
 * screen be a later decision that costs nothing to keep open.
 */
data class StarterStatSheet(
    val types: List<String>,
    /** Label to value, in [CobblemonBaseStatTotal.STAT_ORDER] — the order every stat display here uses. */
    val baseStats: List<Pair<String, Int>>,
    val abilities: List<String>,
    val hiddenAbility: String? = null,
    val hiddenAbilityUnlocked: Boolean = false,
    val growthRate: String? = null,
    /** §2.17's floor, in [CobblemonBaseStatTotal.STAT_ORDER]. Null when the player has earned nothing yet. */
    val ivFloor: List<Int>? = null,
) {
    val baseStatTotal: Int get() = baseStats.sumOf { it.second }
}

/**
 * The sheet as lore lines.
 *
 * Pure text so it is testable without a server, and so the one thing that is easy to get wrong — a
 * bar that overflows its width on an unusual species — is covered by a test rather than by looking
 * at it in game.
 */
object StarterStatLines {

    /** Short labels, six characters of value and bar. Long enough to read, short enough not to wrap. */
    val STAT_LABELS = listOf("HP ", "Atk", "Def", "SpA", "SpD", "Spe")

    const val BAR_WIDTH = 10

    /**
     * The base stat a full bar means.
     *
     * 160, not 255. Almost nothing has a 255 in anything, so scaling to the theoretical maximum would
     * leave every bar in the game short and make the *differences* — which is the entire point of a
     * bar — happen in the first third of the width. 160 puts a genuinely excellent stat at full and
     * costs only that the handful above it also read as full, which is not a distinction a player
     * choosing a starter has to make.
     */
    const val BASE_STAT_FULL = 160

    /**
     * A bar of pipes, coloured by how full it is.
     *
     * Pipes rather than block-drawing characters: `█` and `░` come from the unicode fallback font,
     * which a resource pack is free to replace and which renders at a different width to the default
     * font. `|` is in the default font on every client, so the bars line up everywhere.
     */
    fun bar(value: Int, full: Int = BASE_STAT_FULL, width: Int = BAR_WIDTH): String {
        if (width <= 0) return ""
        val fraction = if (full <= 0) 1.0 else (value.toDouble() / full)
        val filled = Math.round(fraction * width).toInt().coerceIn(0, width)
        val colour = when {
            fraction >= 0.66 -> "§a"
            fraction >= 0.33 -> "§e"
            else -> "§c"
        }
        return "$colour${"|".repeat(filled)}§8${"|".repeat(width - filled)}"
    }

    /** The whole panel, in the order PokéRogue's own left column reads: what it is, then how good it is. */
    fun render(sheet: StarterStatSheet): List<String> {
        val lines = mutableListOf<String>()

        if (sheet.types.isNotEmpty()) lines += "§7${sheet.types.joinToString(" / ")}"

        sheet.baseStats.forEachIndexed { index, (label, value) ->
            // Padded to three so the bars start in the same column on every row; a ragged left edge
            // makes six bars unreadable as a group, which is the only reason to draw them together.
            val name = STAT_LABELS.getOrElse(index) { label }
            lines += "§7$name §f${value.toString().padStart(3)} ${bar(value)}"
        }
        if (sheet.baseStats.isNotEmpty()) lines += "§7BST §f${sheet.baseStatTotal}"

        sheet.ivFloor?.let { floor ->
            // One line, not six bars. This is a floor a player has earned, so what they need is the
            // number; the hexagon is the part a real screen would do better and this deliberately does
            // not try to imitate.
            lines += "§7IVs at least §f${floor.joinToString(" ")}"
        }

        if (sheet.abilities.isNotEmpty()) lines += "§7Ability: §f${sheet.abilities.joinToString(", ")}"
        sheet.hiddenAbility?.let {
            // Shown locked rather than hidden, the way PokéRogue shows a padlocked passive: knowing a
            // species HAS one you have not unlocked is the part that makes the unlock worth wanting.
            lines += if (sheet.hiddenAbilityUnlocked) "§7Hidden: §f$it" else "§7Hidden: §8$it §7(locked)"
        }
        sheet.growthRate?.let { lines += "§8Growth: $it" }

        return lines
    }
}

/**
 * Builds a sheet out of Cobblemon and [StarterProgression].
 *
 * Every read is wrapped. A species the registry cannot resolve, an ability pool a datapack has left
 * in a strange state, a progression store that throws — none of those are reasons to lose the whole
 * draft screen, and a sheet missing a line is obviously degraded in a way a closed window is not.
 */
object StarterStatSheets {

    fun of(player: UUID, species: ResourceLocation): StarterStatSheet? {
        val found = runCatching { PokemonSpecies.getByIdentifier(species) }.getOrNull() ?: return null

        val types = runCatching {
            listOfNotNull(found.primaryType, found.secondaryType).map { it.name.capitalise() }
        }.getOrDefault(emptyList())

        val baseStats = runCatching {
            CobblemonBaseStatTotal.STAT_ORDER.map { it.showdownId to (found.baseStats[it] ?: 0) }
        }.getOrDefault(emptyList())

        // Hidden abilities are separated by type rather than by priority: priority is an ordering
        // within the pool and a datapack is free to set it to anything, while the HiddenAbility type
        // is what actually decides whether §2.15's unlock applies to it.
        val abilities = runCatching {
            found.abilities.filterNot { it is HiddenAbility }.map { it.template.name.capitalise() }.distinct()
        }.getOrDefault(emptyList())
        val hidden = runCatching {
            found.abilities.filterIsInstance<HiddenAbility>().firstOrNull()?.template?.name?.capitalise()
        }.getOrNull()

        val progression = runCatching { StarterProgression.current }.getOrNull()
        val unlocked = hidden != null &&
            runCatching { progression?.hiddenAbilityUnlocked(player, species) }.getOrNull() == true

        // Null unless the player is actually above the floor everybody starts at: a line reading "IVs
        // at least 10 10 10 10 10 10" on every species is six numbers of noise on the screen where a
        // player is comparing thirty-six of them.
        val floor = runCatching {
            progression?.ivFloor(player, species)?.let { earned ->
                CobblemonBaseStatTotal.STAT_ORDER.map(earned::floorFor)
                    .takeIf { values -> values.any { it > StarterIvFloor.BASE } }
            }
        }.getOrNull()

        return StarterStatSheet(
            types = types,
            baseStats = baseStats,
            abilities = abilities,
            hiddenAbility = hidden,
            hiddenAbilityUnlocked = unlocked,
            growthRate = runCatching { found.experienceGroup.name.capitalise() }.getOrNull(),
            ivFloor = floor,
        )
    }

    private fun String.capitalise(): String =
        replace('_', ' ').replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}
