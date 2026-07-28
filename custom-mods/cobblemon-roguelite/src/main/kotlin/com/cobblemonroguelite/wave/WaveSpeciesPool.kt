package com.cobblemonroguelite.wave

import net.minecraft.resources.ResourceLocation

/**
 * One candidate for a wild wave.
 *
 * @property id the species id. A raw [ResourceLocation] and not a resolved `Species`, so the pool
 *   can be built, ordered, and unit-tested without a booted server holding the species registry —
 *   resolution happens once, at the moment the Pokémon is created (see [WildEncounterFactory]).
 *   Same representation the starter pool uses, so a species id means the same thing everywhere in
 *   this module.
 * @property weight relative likelihood within its wave's pool. Entries at zero or below are
 *   dropped, which gives the data layer a way to disable a line without deleting it.
 * @property properties an optional extra `PokemonProperties` fragment appended verbatim after the
 *   species and level — forms, aspects, held items, `shiny=true`. Same convention as the gacha
 *   egg pools, so anyone who has written one of those already knows this format.
 */
data class WaveSpecies(
    val id: ResourceLocation,
    val weight: Double = 1.0,
    val properties: String? = null,
)

/**
 * Answers "what may appear on wave N".
 *
 * Deliberately the whole surface between wave generation and wherever the pool actually comes
 * from. The datapack layer is owned elsewhere and its format is still moving; the generator only
 * needs an ordered bag of weighted species, so that is all it asks for. Anything the data layer
 * wants to do with tiers, biomes, evolution stages, or per-segment gating collapses into this one
 * call, and the generator never has to learn about any of it.
 *
 * **The returned list is treated as a set, not a sequence.** The generator imposes its own ordering
 * before drawing (see [WildWaveGenerator]), so an implementation is free to return entries in
 * whatever order is convenient without changing what any seed rolls.
 */
fun interface WaveSpeciesPool {

    /**
     * Candidates for [wave], 1-based. Returning an empty list is legal and means "no wild encounter
     * is possible here" — the generator reports that rather than substituting anything, because a
     * silent substitution would hide a broken data file behind a run that merely feels wrong.
     */
    fun eligibleAt(wave: Int): List<WaveSpecies>
}

/**
 * A pool whose contents do not vary by wave.
 *
 * Exists as the trivial implementation for tests and for bringing the generator up before the data
 * layer lands — **not** as a shipping default. It carries no species of its own on purpose: which
 * Pokémon appear, and at which waves, is a design decision, and a hardcoded list here would be the
 * kind of placeholder that quietly becomes permanent.
 */
class StaticWaveSpeciesPool(private val entries: List<WaveSpecies>) : WaveSpeciesPool {
    override fun eligibleAt(wave: Int): List<WaveSpecies> = entries
}
