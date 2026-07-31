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
    /** §2.17's floor, in [CobblemonBaseStatTotal.STAT_ORDER]. Null only when it could not be read. */
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

    /**
     * Short labels — and **upper case for a reason that is not style**.
     *
     * Minecraft's default font is proportional, so `Atk` and `Def` are not the same width: `t` is 4px
     * against 6px for almost everything else, and `HP` is a whole character short of the three-letter
     * labels. That is what made the six rows look ragged even though every bar is exactly the same
     * number of pipes. Every upper-case glyph is 6px, so `ATK`/`DEF`/`SPA`/`SPD`/`SPE` all measure 18px
     * and the columns after them line up.
     *
     * `HP ` is the one residue: a space is 4px, so that row starts its number 2px early. There is no
     * 6px blank in the vanilla font to pad with, and 2px is a third of a character — it is the closest
     * this gets without a resource pack.
     */
    val STAT_LABELS = listOf("HP ", "ATK", "DEF", "SPA", "SPD", "SPE")

    /**
     * A horizontal rule, the way the rest of this repo draws one: strikethrough spaces.
     *
     * The unicode box-drawing characters would be sharper and come from the fallback font, which is the
     * same trap the bars avoid. Twenty-five spaces is about the width of a stat row, so the rule ends
     * where the block it is separating ends.
     */
    val RULE = "§8§m" + " ".repeat(25)

    /** Between a base stat and its IV. Not a pipe: the bars are pipes, and a pipe would join them. */
    const val COLUMN_DIVIDER = " §8: "

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
     * The IV bar, deliberately half the width of the base-stat bar beside it.
     *
     * Two full-width bars on one row read as equally important and they are not: the base stat is what
     * the species *is* and the IV is the roll on top of it. Narrower also keeps the row shorter than
     * the single wide "IVs …" line this replaced, which was the widest thing on the screen.
     */
    const val IV_BAR_WIDTH = 5

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

    /**
     * The whole panel, in the order PokéRogue's own left column reads: what it is, then how good it is.
     *
     * ### Three blocks, two rules
     *
     * Identity, numbers, abilities. Every line used to sit in one undifferentiated stack of thirteen,
     * which is what made a tooltip carrying good information read as clutter — nothing told the eye
     * where one kind of fact stopped and the next began. The rules do that, and they cost two lines to
     * save the reader from scanning eleven.
     *
     * Growth rate moved up onto the identity line for the same reason: alone at the bottom it was a
     * whole row spent on a minor fact, and it belongs with "what is this species" anyway.
     */
    fun render(sheet: StarterStatSheet): List<String> {
        val lines = mutableListOf<String>()

        // Types and growth read as one fact — what this species is — so they share a line.
        val identity = buildList {
            if (sheet.types.isNotEmpty()) add(sheet.types.joinToString(" / "))
            sheet.growthRate?.let { add(it) }
        }
        if (identity.isNotEmpty()) lines += "§7" + identity.joinToString(" §8· §7")
        if (sheet.baseStats.isNotEmpty() && identity.isNotEmpty()) lines += RULE

        // The IV rides on the stat row it belongs to, behind a divider, rather than in a row of its own.
        //
        // It was one wide line — "IVs HP10 Atk10 Def10 SpA10 SpD10 Spe10" — which in play was the widest
        // thing on the screen and asked a player to match six numbers back to six rows by counting. Per
        // stat is right; six SEPARATE per-stat rows would be right too, except the tooltip already runs
        // most of the screen's height, so it shares the row that already exists for that stat.
        //
        // The word "IV" used to be repeated on all six rows. The divider says the same thing once per
        // row without any words at all, and the trailer names the column — six repetitions of a
        // two-letter label is exactly the kind of thing that adds up to "cluttered".
        sheet.baseStats.forEachIndexed { index, (label, value) ->
            // Padded to three so the bars start in the same column on every row; a ragged left edge
            // makes six bars unreadable as a group, which is the only reason to draw them together.
            val name = STAT_LABELS.getOrElse(index) { label }
            val iv = sheet.ivFloor?.getOrNull(index)?.let { floor ->
                COLUMN_DIVIDER + "§f${floor.toString().padStart(2)} ${bar(floor, StarterIvFloor.MAX_IV, IV_BAR_WIDTH)}"
            }.orEmpty()
            lines += "§7$name §f${value.toString().padStart(3)} ${bar(value)}$iv"
        }
        if (sheet.baseStats.isNotEmpty()) lines += "§7BST §f${sheet.baseStatTotal}"

        // Abilities and the IV note are the third block: what is true about this species beyond its
        // numbers. Assembled first so the rule is only drawn when there is something under it.
        val trailer = mutableListOf<String>()
        if (sheet.abilities.isNotEmpty()) trailer += "§7Ability: §f${sheet.abilities.joinToString(", ")}"
        sheet.hiddenAbility?.let {
            // Shown locked rather than hidden, the way PokéRogue shows a padlocked passive: knowing a
            // species HAS one you have not unlocked is the part that makes the unlock worth wanting.
            trailer += if (sheet.hiddenAbilityUnlocked) "§7Hidden: §f$it" else "§7Hidden: §8$it §7(locked)"
        }
        // Names the right-hand column, which is now the only thing that does — and says which floor it
        // is, a fixed starting point or a high-water mark you have raised, which is why §2.17 exists.
        // Kept short deliberately. The long version — "Right of the divider: IVs. Every run starts
        // here." — named the column perfectly and was half again as wide as a stat row, which would have
        // made it the widest line on the panel and undone the point of splitting the IVs out of one.
        sheet.ivFloor?.let { floor ->
            trailer += if (floor.all { it <= StarterIvFloor.BASE }) {
                "§8IVs · every run starts here"
            } else {
                "§8IVs · your best so far"
            }
        }
        if (trailer.isNotEmpty()) {
            if (lines.isNotEmpty()) lines += RULE
            lines += trailer
        }

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

        // Null only when the progression store could not answer. The base floor is shown like any
        // other: it is what the starter's IVs will be, which is a fact about the purchase and not a
        // reward notification.
        val floor = runCatching {
            progression?.ivFloor(player, species)?.let { earned ->
                CobblemonBaseStatTotal.STAT_ORDER.map(earned::floorFor)
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
