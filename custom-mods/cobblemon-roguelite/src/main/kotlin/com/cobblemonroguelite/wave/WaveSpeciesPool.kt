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
 * ### What biome-gated encounters would need, and why they are not built here
 *
 * §2.24 puts a run in a biome, and a biome is the natural key for a wild pool — the whole point of
 * this interface being one call is that such a key costs no new plumbing downstream. It does cost
 * something here, and it is worth naming precisely so nobody assumes it is free:
 *
 * - the biome has to *reach* this call. The wave number does not imply it: §2.24's rotation is
 *   seeded per run and may become player-chosen, so two runs on wave 41 are legitimately in different
 *   biomes. Either the signature grows a parameter (`eligibleAt(wave, biome)`) or the caller binds a
 *   pool per run — and only the first keeps this a pure function of its arguments.
 * - [com.cobblemonroguelite.wave.WildWaveGenerator] would have to pass it, which means the generator
 *   takes a biome per `generate` call rather than only `(seed, wave)`. That is the one line that makes
 *   the encounter no longer reproducible from the checkpoint alone, unless the biome is read off the
 *   run — which it can be, since [com.cobblemonroguelite.run.RunState.biome] is persisted.
 * - a biome with no pool entries has to mean something. "No encounter is possible" already has a
 *   meaning below, and a run whose biome happens to have an empty pool would hit it for ten waves.
 *
 * None of that is built. It is written down because the shape of this call is what keeps it cheap,
 * and the next person to widen the signature should know what the widening is actually for.
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
