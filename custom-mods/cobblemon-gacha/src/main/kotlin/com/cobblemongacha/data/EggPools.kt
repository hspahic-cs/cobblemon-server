package com.cobblemongacha.data

import kotlin.random.Random

/**
 * One species entry in an egg pool. `id` is the lowercase Cobblemon species id passed to
 * `givepokemonegg` (e.g. "pikachu", "jangmo-o"). `hasHiddenAbility` is the flag from the
 * pool CSV — when a species flagged `true` is rolled into an egg it is granted with its
 * Hidden Ability (`ha=yes`). There is no separate "HA-only" egg type; the flag is per-species.
 *
 * `notes` carries any annotation from the CSV (e.g. "Own Tempo", "Yes (both forms)") so the
 * data isn't lost; the code doesn't act on it for v1.
 *
 * `properties` is an optional extra `PokemonProperties` fragment appended verbatim to the
 * `givepokemonegg` command (e.g. `"battle_bond=bond"` to hatch a Battle Bond Greninja). Nullable
 * because most pool entries omit it and the plain Gson loader doesn't honour Kotlin defaults for
 * absent JSON fields.
 */
data class EggSpecies(
    val id: String,
    val hasHiddenAbility: Boolean,
    val notes: String = "",
    val properties: String? = null,
)

/**
 * Four-tier pool of Pokémon species available for gacha eggs. Keys are normalised lowercase
 * with underscores: "common", "uncommon", "rare", "ultra_rare".
 */
data class EggPools(val byTier: Map<String, List<EggSpecies>>) {

    /**
     * Pick a species id for the given tier, optionally requiring it has a Hidden Ability slot.
     * Returns null if the pool is unknown or filtering leaves no candidates.
     */
    fun pick(tier: String, requireHiddenAbility: Boolean = false, random: Random = Random.Default): String? {
        val key = normaliseTier(tier)
        val pool = byTier[key] ?: return null
        val candidates = if (requireHiddenAbility) pool.filter { it.hasHiddenAbility } else pool
        if (candidates.isEmpty()) return null
        return candidates.random(random).id
    }

    /**
     * Pick a full species entry (id + its `hasHiddenAbility` flag) uniformly from the whole
     * tier pool. Used by the egg-grant path: Hidden Ability is decided per-rolled-species from
     * the returned [EggSpecies.hasHiddenAbility], so this does no filtering.
     * Returns null if the pool is unknown or empty.
     */
    fun pickSpecies(tier: String, random: Random = Random.Default): EggSpecies? {
        // A pool key may be a single tier ("rare") or a union of tiers ("rare|uncommon"),
        // letting e.g. the shiny egg draw from both the rare and uncommon species pools.
        val pool = tier.split('|', '+', ',')
            .flatMap { byTier[normaliseTier(it)] ?: emptyList() }
        if (pool.isEmpty()) return null
        return pool.random(random)
    }

    companion object {
        /** Lower-snake-case normaliser: "Ultra Rare" → "ultra_rare", "common" → "common". */
        fun normaliseTier(s: String): String =
            s.trim().lowercase().replace(' ', '_').replace('-', '_')
    }
}
