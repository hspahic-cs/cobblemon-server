package com.cobblemonroguelite.data.wild

import com.cobblemonroguelite.data.DataProblems
import com.cobblemonroguelite.data.JsonView
import com.cobblemonroguelite.data.RogueliteDataRegistry
import com.cobblemonroguelite.wave.WaveSpecies
import com.cobblemonroguelite.wave.WaveSpeciesPool
import net.minecraft.resources.ResourceLocation

/**
 * A datapack file of wild species — what a run may meet on a wave that is not a trainer's.
 *
 * ### Why this exists, and why its absence was invisible
 *
 * [com.cobblemonroguelite.wave.WildPools] is a code seam with a default that refuses, and until this
 * registry there was **no way to fill it**: every other table in the mode is a datapack registry, and
 * this one was a `register()` call nobody could reach from data. The effect in play was a run that
 * started, teleported into a correctly stamped arena, and then refused wave 1 — with the player told
 * "run battles are not implemented on this server yet", which was not true. Battles were implemented;
 * nothing had told them what to fight.
 *
 * ### One pool per file, unioned
 *
 * The registry loads every file under `data/<ns>/roguelite/wild_pools/` and takes the union, the same
 * way [com.cobblemonroguelite.data.biome.RunBiomes] treats biomes. That is what lets an addon
 * datapack add its own species without editing — or shadowing — the server's file, which a single
 * `default.json` would force. Weights are relative across the whole union, not within a file.
 *
 * ### Wave windows, not tiers
 *
 * `min_wave`/`max_wave` on each entry, matching every other windowed thing in the mode
 * ([com.cobblemonroguelite.data.trainer.TrainerRoster]'s bands, [RunBiome]'s range). §2.19's level
 * curve already scales whatever is drawn, so a window is about *what belongs* at a depth — Caterpie
 * stopping and Garchomp starting — rather than about how strong it will be when it arrives.
 */
data class WildPool(val id: ResourceLocation, val entries: List<WildPoolEntry>)

/**
 * One species in a pool.
 *
 * @property weight relative likelihood among everything eligible for the wave. Zero or less disables
 *   the entry without deleting it — the same "disabled" [com.cobblemonroguelite.data.biome.RunBiomes]
 *   uses, so an author who has learned one has learned both.
 * @property properties extra Cobblemon `PokemonProperties` tokens, appended after the species and
 *   level the generator writes. This is where a shiny rate, a form or an aspect goes; it is passed
 *   through unparsed, because the properties grammar is Cobblemon's and duplicating its validation
 *   here would mean rejecting anything Cobblemon learned to accept after this file was written.
 */
data class WildPoolEntry(
    val species: ResourceLocation,
    val weight: Double = 1.0,
    val minWave: Int = 1,
    val maxWave: Int? = null,
    val properties: String? = null,
) {
    fun covers(wave: Int): Boolean = wave >= minWave && (maxWave == null || wave <= maxWave)
}

object WildSpeciesPools : RogueliteDataRegistry<WildPool>("wild_pools") {

    /**
     * The whole union as a [WaveSpeciesPool], for [com.cobblemonroguelite.wave.WildPools].
     *
     * A function returning a live view rather than a snapshot taken at boot: the registry reloads on
     * `/reload`, and a pool captured once would keep drawing from the file that was on disk when the
     * server started. The generator asks per wave, so there is nothing to cache.
     */
    fun asWavePool(): WaveSpeciesPool = WaveSpeciesPool { wave -> eligibleAt(wave) }

    /**
     * Every entry whose window covers [wave] and whose weight is positive.
     *
     * Ordering is not imposed here on purpose. [com.cobblemonroguelite.wave.WildWaveGenerator] sorts
     * what it is given before drawing, precisely so that a data layer returning entries in map order
     * cannot change what a seed rolls — so sorting here as well would be duplicated work with a
     * duplicated reason to drift.
     */
    fun eligibleAt(wave: Int): List<WaveSpecies> =
        entries.values
            .flatMap { it.entries }
            .filter { it.weight > 0.0 && it.covers(wave) }
            .map { WaveSpecies(id = it.species, weight = it.weight, properties = it.properties) }

    public override fun parse(id: ResourceLocation, root: JsonView, problems: DataProblems): WildPool? {
        val before = problems.count

        val entries = root.requireObjectList("entries").orEmpty().mapNotNull(::parseEntry)

        root.expectNoUnknownKeys()

        if (entries.isEmpty() && problems.count == before) {
            // Not silently accepted. An empty pool file is either a mistake or a leftover, and both
            // present in play as "wild waves refuse to start" — the exact failure this registry was
            // added to stop being mysterious.
            root.problem("entries", "a wild pool with no entries cannot produce an encounter")
        }

        return if (problems.count == before) WildPool(id, entries) else null
    }

    private fun parseEntry(view: JsonView): WildPoolEntry? {
        val species = view.requireString("species")?.let { text ->
            // tryParse, then reported by name: an id that does not parse is a typo in a file, and the
            // species registry is not consulted here at all — a datapack is read during the same
            // reload that builds it, so "unknown species" is [WildEncounterFactory]'s call to make
            // when the encounter is created, not this layer's.
            ResourceLocation.tryParse(text) ?: run {
                view.problem("species", "'$text' is not a valid id")
                null
            }
        }
        val weight = view.optionalDouble("weight") ?: 1.0
        val minWave = view.optionalInt("min_wave") ?: 1
        val maxWave = view.optionalInt("max_wave")
        val properties = view.optionalString("properties")
        view.expectNoUnknownKeys()

        if (minWave < 1) view.problem("min_wave", "waves are 1-based, was $minWave")
        if (maxWave != null && maxWave < minWave) {
            view.problem("max_wave", "$maxWave is before min_wave $minWave, so this species could never appear")
        }

        return species?.let {
            WildPoolEntry(
                species = it,
                weight = weight,
                minWave = minWave,
                maxWave = maxWave,
                properties = properties?.takeIf(String::isNotBlank),
            )
        }
    }
}
