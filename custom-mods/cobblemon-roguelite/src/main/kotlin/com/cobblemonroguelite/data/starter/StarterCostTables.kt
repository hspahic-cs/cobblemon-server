package com.cobblemonroguelite.data.starter

import com.cobblemonroguelite.data.DataProblems
import com.cobblemonroguelite.data.JsonView
import com.cobblemonroguelite.data.RogueliteDataRegistry
import net.minecraft.resources.ResourceLocation
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("cobblemon_roguelite/data")

/**
 * Every starter cost table on the server, loaded from
 * `data/<namespace>/roguelite/starter_costs/<name>.json`.
 *
 * ### The file
 *
 * ```json
 * {
 *   "costs": [
 *     { "species": "cobblemon:bulbasaur", "cost": 3 },
 *     { "species": "cobblemon:torchic",   "cost": 4 }
 *   ]
 * }
 * ```
 *
 * A list of objects rather than a `{ "cobblemon:bulbasaur": 3 }` map, which would be half the bytes.
 * Two reasons, and the second is the real one: it matches every other table this module reads, and it
 * gives each entry a field path, so a bad line is reported as `costs[417].cost` instead of as a
 * failure somewhere inside an eight-hundred-key object.
 *
 * ### Why all files are merged, unlike every other registry here
 *
 * Reward tables, payout tables and trainer rosters are each *a* table, and a run pins one by id.
 * Prices are not like that: a species has one price on a server, and the thing a run needs is the
 * whole dictionary. Splitting by generation or by pack is then a filing decision rather than a
 * gameplay one, so files are folded together and a run never names one.
 *
 * That makes conflicts possible, which the id-per-table registries cannot have. They are resolved by
 * **sorted file id, first wins**, and every one is logged with both files. Sorted, because
 * `ResourceManager` order is not something an owner can see or predict and a price that depends on it
 * would move between restarts. First-wins rather than last-wins is arbitrary; being *stated* is not.
 *
 * A pack that means to override a price should shadow the file it came from — the standard datapack
 * rule the base registry already relies on — rather than add a second file and hope.
 *
 * ### Why no `example.json` ships here, unlike every other table
 *
 * Because merging makes an example *live*. A reward or payout example sits under an id nothing names,
 * so it is inert documentation; a cost example would be folded into the dictionary and would really
 * price the species it mentions. That is a balance statement shipped in the jar, which is precisely
 * what §2.7 keeps out of a published build — and it would silently outrank the derived default for
 * whichever handful of species the example happened to list. The schema is documented above instead.
 */
object StarterCostTables : RogueliteDataRegistry<StarterCostTable>("starter_costs") {

    /**
     * The merged dictionary, rebuilt only when the registry's contents have actually been replaced.
     *
     * A price is looked up once per eligible species per catalogue build — a few hundred lookups on a
     * veteran's account — so folding every file on each call would be real work for a result that
     * changes only on `/reload`. Keyed on the identity of the `entries` map rather than on a counter
     * because [RogueliteDataRegistry] replaces that map wholesale, so identity *is* the version.
     */
    @Volatile
    private var cache: Cached? = null

    private class Cached(val source: Map<ResourceLocation, StarterCostTable>, val costs: Map<ResourceLocation, Int>)

    /** Points [species] costs across every loaded table, or null if nothing prices it. */
    fun costOf(species: ResourceLocation): Int? = merged()[species]

    fun merged(): Map<ResourceLocation, Int> {
        val source = entries
        cache?.takeIf { it.source === source }?.let { return it.costs }
        val costs = fold(source)
        cache = Cached(source, costs)
        return costs
    }

    private fun fold(source: Map<ResourceLocation, StarterCostTable>): Map<ResourceLocation, Int> {
        val merged = HashMap<ResourceLocation, Int>()
        val from = HashMap<ResourceLocation, ResourceLocation>()
        source.entries.sortedBy { it.key.toString() }.forEach { (fileId, table) ->
            table.costs.forEach { (species, cost) ->
                val previous = from.putIfAbsent(species, fileId)
                if (previous == null) {
                    merged[species] = cost
                } else {
                    // WARN and not ERROR: the server still has a defined price and the run still
                    // starts. Silence is the thing to avoid — two packs disagreeing about what a
                    // species costs is a balance change nobody made on purpose.
                    log.warn(
                        "roguelite: '{}' is priced by both {} and {} — keeping {}'s {} point(s)",
                        species, previous, fileId, previous, merged[species],
                    )
                }
            }
        }
        return merged
    }

    public override fun parse(id: ResourceLocation, root: JsonView, problems: DataProblems): StarterCostTable? {
        val entryViews = root.requireObjectList("costs")
        root.expectNoUnknownKeys()
        if (entryViews == null) return null

        val costs = linkedMapOf<ResourceLocation, Int>()
        var dropped = 0
        for (view in entryViews) {
            val speciesText = view.requireString("species")
            val cost = view.requireInt("cost")
            view.expectNoUnknownKeys()
            if (speciesText == null || cost == null) {
                dropped++
                continue
            }
            val species = runCatching { ResourceLocation.parse(speciesText) }.getOrNull()
            if (species == null) {
                view.problem("species", "'$speciesText' is not a valid species id")
                dropped++
                continue
            }
            if (cost < 1) {
                // Zero would be a free starter, which is the budget switched off for that species
                // rather than a cheap one. Negative would pay the player points for taking it.
                view.problem("cost", "must be at least 1, was $cost — a starter cannot be free")
                dropped++
                continue
            }
            if (costs.put(species, cost) != null) {
                // Fatal for the file rather than last-wins: with one species priced twice, which
                // number the author meant is not guessable, and guessing it is a balance decision.
                view.problem("species", "'$species' is priced twice in this file")
                return null
            }
        }

        if (costs.isEmpty()) {
            problems.add("costs", "no usable entries — a table that prices nothing is not loaded")
            return null
        }
        if (dropped > 0) problems.add("costs", "$dropped entry/entries dropped; the rest of the table loaded")
        return StarterCostTable(id, costs)
    }
}
