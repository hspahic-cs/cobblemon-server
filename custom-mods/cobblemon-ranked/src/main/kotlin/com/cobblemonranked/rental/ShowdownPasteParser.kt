package com.cobblemonranked.rental

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.pokemon.FormData
import com.cobblemon.mod.common.pokemon.Species
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation

/**
 * Parses Showdown-teambuilder export text (the format `docs/rental-drafts-plan.md` commits to)
 * into [RentalTeams.RentalMon] specs carrying the player's RAW EV spread — the rental de-tune is
 * applied later at build time, so exports can round-trip the player's true target build.
 *
 * Input arrives as the joined pages of a book & quill. Sets may be separated by blank lines OR by
 * page boundaries (no blank line), so a new set starts at any line that isn't a recognised
 * attribute/move line rather than only after a blank.
 */
object ShowdownPasteParser {

    class ParseException(message: String) : Exception(message)

    private val NATURES = setOf(
        "adamant", "bashful", "bold", "brave", "calm", "careful", "docile", "gentle", "hardy",
        "hasty", "impish", "jolly", "lax", "lonely", "mild", "modest", "naive", "naughty",
        "quiet", "quirky", "rash", "relaxed", "sassy", "serious", "timid",
    )

    /** Namespaces tried, in order, when resolving a held-item name like "Rocky Helmet". */
    private val ITEM_NAMESPACES = listOf("cobblemon", "mega_showdown", "minecraft", "legendarymonuments")

    /** "Rough Skin" / "Chi-Yu" / "U-turn" → "roughskin" / "chiyu" / "uturn". */
    fun showdownId(raw: String): String = raw.lowercase().replace(Regex("[^a-z0-9]"), "")

    fun parse(text: String): List<RentalTeams.RentalMon> {
        val blocks = splitIntoSets(text)
        if (blocks.isEmpty()) throw ParseException("The book doesn't contain any Pokémon sets.")
        return blocks.map { parseSet(it) }
    }

    // ---- set splitting ----------------------------------------------------------------------

    private fun isAttributeLine(line: String): Boolean {
        val l = line.trim()
        return l.startsWith("-") ||
            l.endsWith(" Nature", ignoreCase = true) ||
            ATTRIBUTE_PREFIXES.any { l.startsWith(it, ignoreCase = true) }
    }

    private val ATTRIBUTE_PREFIXES = listOf(
        "Ability:", "Level:", "EVs:", "IVs:", "Shiny:", "Tera Type:", "Happiness:",
        "Gigantamax:", "Dynamax Level:", "Hidden Power:",
    )

    private fun splitIntoSets(text: String): List<List<String>> {
        val sets = mutableListOf<MutableList<String>>()
        for (rawLine in text.lines()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            if (!isAttributeLine(line)) sets.add(mutableListOf(line))
            else sets.lastOrNull()?.add(line)
                ?: throw ParseException("The paste starts mid-set (\"$line\") — copy the full Showdown export.")
        }
        return sets
    }

    // ---- one set ----------------------------------------------------------------------------

    private fun parseSet(lines: List<String>): RentalTeams.RentalMon {
        val header = lines.first()
        val (species, form, gender) = parseHeaderSpecies(header)
        val item = parseHeaderItem(header, speciesLabel = header)

        var ability: String? = null
        var nature: String? = null
        var evs = RentalTeams.RentalEVs()
        val moves = mutableListOf<String>()

        for (line in lines.drop(1)) {
            when {
                line.startsWith("Ability:", ignoreCase = true) ->
                    ability = showdownId(line.substringAfter(":"))
                line.endsWith(" Nature", ignoreCase = true) ->
                    nature = showdownId(line.removeSuffix("Nature").removeSuffix("nature"))
                line.startsWith("EVs:", ignoreCase = true) ->
                    evs = parseEvs(line.substringAfter(":"), speciesLabel = header)
                line.startsWith("-") -> {
                    val move = showdownId(line.removePrefix("-"))
                    if (move.isNotEmpty()) moves.add(move)
                }
                // Level / IVs / Shiny / Tera Type etc. are ignored: the de-tune and server rules
                // decide those, not the paste.
            }
        }

        if (ability == null) throw ParseException("$header: missing \"Ability:\" line.")
        if (nature == null) throw ParseException("$header: missing nature line (e.g. \"Adamant Nature\").")
        if (nature !in NATURES) throw ParseException("$header: unknown nature \"$nature\".")
        if (moves.isEmpty()) throw ParseException("$header: no moves listed.")
        if (moves.size > 4) throw ParseException("$header: more than 4 moves.")

        return RentalTeams.RentalMon(
            species = species.resourceIdentifier.path,
            form = form?.name?.lowercase(),
            ability = ability,
            nature = nature,
            item = item,
            moves = moves,
            evs = evs,
            gender = gender,
        )
    }

    /**
     * Header shapes: `Garchomp @ Rocky Helmet`, `Nickname (Slowking-Galar) (F) @ Leftovers`,
     * `Chi-Yu @ Choice Specs`. Returns the resolved species (+ form if the name carried one) and
     * gender letter.
     */
    private fun parseHeaderSpecies(header: String): Triple<Species, FormData?, String?> {
        var name = header.substringBefore("@").trim()
        var gender: String? = null
        // Strip trailing (M)/(F), then prefer the last parenthesised group as the species name
        // (nickname form). Anything left is the species itself.
        Regex("\\((M|F)\\)\\s*$").find(name)?.let {
            gender = it.groupValues[1]
            name = name.removeRange(it.range).trim()
        }
        Regex("\\(([^)]+)\\)\\s*$").find(name)?.let { name = it.groupValues[1].trim() }

        val wanted = showdownId(name)
        if (wanted.isEmpty()) throw ParseException("Couldn't read a species from \"$header\".")

        // Exact species match first (handles hyphenated species like Chi-Yu / Ho-Oh), then
        // progressively peel "-Form" suffixes off the display name and match base + form.
        findSpecies(wanted)?.let { return Triple(it, null, gender) }

        val parts = name.split("-")
        for (cut in parts.size - 1 downTo 1) {
            val base = findSpecies(showdownId(parts.take(cut).joinToString(""))) ?: continue
            val formName = parts.drop(cut).joinToString("-")
            val form = base.forms.find { showdownId(it.name) == showdownId(formName) }
                ?: throw ParseException("$name: ${base.name} has no form \"$formName\".")
            return Triple(base, form, gender)
        }
        throw ParseException("Unknown species \"$name\".")
    }

    private fun findSpecies(id: String): Species? =
        PokemonSpecies.species.find { showdownId(it.resourceIdentifier.path) == id || showdownId(it.name) == id }

    private fun parseHeaderItem(header: String, speciesLabel: String): String {
        val raw = header.substringAfter("@", "").trim()
        if (raw.isEmpty()) throw ParseException("$speciesLabel: no held item (every rental mon needs one — \"@ Item\").")
        val path = raw.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
        for (ns in ITEM_NAMESPACES) {
            val id = ResourceLocation.fromNamespaceAndPath(ns, path)
            if (BuiltInRegistries.ITEM.containsKey(id)) return id.toString()
        }
        throw ParseException("$speciesLabel: unknown held item \"$raw\".")
    }

    private fun parseEvs(spec: String, speciesLabel: String): RentalTeams.RentalEVs {
        var hp = 0; var atk = 0; var def = 0; var spa = 0; var spd = 0; var spe = 0
        for (part in spec.split("/")) {
            val p = part.trim()
            if (p.isEmpty()) continue
            val m = Regex("(\\d+)\\s+([A-Za-z]+)").find(p)
                ?: throw ParseException("$speciesLabel: unreadable EV entry \"$p\".")
            val value = m.groupValues[1].toInt()
            if (value > 252) throw ParseException("$speciesLabel: EV value $value exceeds 252.")
            when (m.groupValues[2].lowercase()) {
                "hp" -> hp = value
                "atk" -> atk = value
                "def" -> def = value
                "spa" -> spa = value
                "spd" -> spd = value
                "spe" -> spe = value
                else -> throw ParseException("$speciesLabel: unknown EV stat \"${m.groupValues[2]}\".")
            }
        }
        val total = hp + atk + def + spa + spd + spe
        if (total > 510) throw ParseException("$speciesLabel: EV total $total exceeds 510.")
        return RentalTeams.RentalEVs(hp, atk, def, spa, spd, spe)
    }
}
