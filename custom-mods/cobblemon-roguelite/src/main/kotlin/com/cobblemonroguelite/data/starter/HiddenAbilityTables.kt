package com.cobblemonroguelite.data.starter

import com.cobblemonroguelite.data.DataProblems
import com.cobblemonroguelite.data.JsonView
import com.cobblemonroguelite.data.RogueliteDataRegistry
import net.minecraft.resources.ResourceLocation
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("cobblemon_roguelite/data")

/**
 * Every hand-assigned unlock ability on the server, loaded from
 * `data/<namespace>/roguelite/hidden_abilities/<name>.json`.
 *
 * ### The file
 *
 * ```json
 * {
 *   "abilities": [
 *     { "species": "cobblemon:slaking", "ability": "hugepower" },
 *     { "species": "cobblemon:torchic", "ability": "speedboost" }
 *   ]
 * }
 * ```
 *
 * `ability` is an ability's registry name. It is matched case- and separator-insensitively, so
 * `"Speed Boost"` finds `speedboost` — see [com.cobblemonroguelite.starter.HiddenAbilityUnlock.normalise].
 * An ability no installed mod defines is **not** rejected at load time, because this file is read
 * before nothing in particular: it is reported when the species is next quoted or granted, where the
 * log line can name the species the operator actually has to fix.
 *
 * A list of objects rather than a `{ "cobblemon:slaking": "hugepower" }` map, for
 * [StarterCostTables]' reason: it matches every other table here, and it gives each entry a field
 * path, so a bad line is reported as `abilities[12].ability` rather than as a failure somewhere
 * inside one large object.
 *
 * ### Merged across files, like starter costs and unlike everything else
 *
 * A species has one granted ability on a server, and the thing a starter build needs is the whole
 * dictionary — so files are folded together and nothing ever names one by id. Conflicts are resolved
 * by **sorted file id, first wins**, and every one is logged with both files: `ResourceManager` order
 * is not something an owner can see or predict, so a grant that depended on it would move between
 * restarts. First-wins is arbitrary; being stated is not. A pack that means to override an assignment
 * should shadow the file it came from, which is the standard datapack rule the base registry relies
 * on already.
 *
 * ### Why no `example.json` ships here
 *
 * Same reason [StarterCostTables] ships none: merging makes an example **live**. A reward or payout
 * example sits under an id nothing names and is inert documentation, whereas an example here would
 * really re-assign whichever species it mentioned — a balance statement shipped in the jar, which is
 * what §2.7 keeps out of a published build. The schema is documented above instead, and an empty
 * registry is the intended out-of-the-box state: every species falls through to its own hidden
 * ability, which is §2.27's default.
 */
object HiddenAbilityTables : RogueliteDataRegistry<HiddenAbilityTable>("hidden_abilities") {

    /**
     * The merged dictionary, rebuilt only when the registry's contents have actually been replaced.
     *
     * [StarterCostTables]' cache, for [StarterCostTables]' reason: this is read once per species per
     * starter built and once per candy quote, and folding every file on each call would be real work
     * for a result that changes only on `/reload`. Keyed on the identity of the `entries` map because
     * [RogueliteDataRegistry] replaces it wholesale, so identity *is* the version.
     */
    @Volatile
    private var cache: Cached? = null

    private class Cached(val source: Map<ResourceLocation, HiddenAbilityTable>, val abilities: Map<ResourceLocation, String>)

    /** The hand-assigned ability for [species], or null to use its own hidden ability. */
    fun abilityFor(species: ResourceLocation): String? = merged()[species]

    fun merged(): Map<ResourceLocation, String> {
        val source = entries
        cache?.takeIf { it.source === source }?.let { return it.abilities }
        val abilities = fold(source)
        cache = Cached(source, abilities)
        return abilities
    }

    private fun fold(source: Map<ResourceLocation, HiddenAbilityTable>): Map<ResourceLocation, String> {
        val merged = HashMap<ResourceLocation, String>()
        val from = HashMap<ResourceLocation, ResourceLocation>()
        source.entries.sortedBy { it.key.toString() }.forEach { (fileId, table) ->
            table.abilities.forEach { (species, ability) ->
                val previous = from.putIfAbsent(species, fileId)
                if (previous == null) {
                    merged[species] = ability
                } else {
                    // WARN and not ERROR: the server still has a defined answer and the run still
                    // starts. Silence is the thing to avoid — two packs disagreeing about what an
                    // unlock grants is a balance change nobody made on purpose.
                    log.warn(
                        "roguelite: '{}' is assigned an unlock ability by both {} and {} — keeping {}'s '{}'",
                        species, previous, fileId, previous, merged[species],
                    )
                }
            }
        }
        return merged
    }

    public override fun parse(id: ResourceLocation, root: JsonView, problems: DataProblems): HiddenAbilityTable? {
        val entryViews = root.requireObjectList("abilities")
        root.expectNoUnknownKeys()
        if (entryViews == null) return null

        val abilities = linkedMapOf<ResourceLocation, String>()
        var dropped = 0
        for (view in entryViews) {
            val speciesText = view.requireString("species")
            val ability = view.requireString("ability")
            view.expectNoUnknownKeys()
            if (speciesText == null || ability == null) {
                dropped++
                continue
            }
            val species = runCatching { ResourceLocation.parse(speciesText) }.getOrNull()
            if (species == null) {
                view.problem("species", "'$speciesText' is not a valid species id")
                dropped++
                continue
            }
            if (ability.isBlank()) {
                // A blank is not "no override" — it is an entry the author meant to fill in. Treating
                // it as absent would silently hand back the hidden ability they were replacing.
                view.problem("ability", "must name an ability; a blank entry assigns nothing")
                dropped++
                continue
            }
            if (abilities.put(species, ability) != null) {
                // Fatal for the file rather than last-wins: with one species assigned twice, which
                // ability the author meant is not guessable, and guessing it is a balance decision.
                view.problem("species", "'$species' is assigned an unlock ability twice in this file")
                return null
            }
        }

        if (abilities.isEmpty()) {
            problems.add("abilities", "no usable entries — a table that assigns nothing is not loaded")
            return null
        }
        if (dropped > 0) problems.add("abilities", "$dropped entry/entries dropped; the rest of the table loaded")
        return HiddenAbilityTable(id, abilities)
    }
}
