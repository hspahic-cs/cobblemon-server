package com.cobblemonroguelite.wave

import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("cobblemon_roguelite/wave")

/**
 * What a wild wave resolved to, before any Cobblemon object exists.
 *
 * Kept free of game types so the whole generator can be unit-tested without a server: everything
 * that decides *what* the player fights is decided here, and [WildEncounterFactory] only turns the
 * decision into a Pokémon.
 *
 * @property variantSeed a stable sub-seed for the rolls this class does **not** make — IVs, nature,
 *   gender, shiny. Cobblemon's `PokemonProperties.create()` rolls those from its own unseeded RNG,
 *   which leaves the §2.3 reroll hole half-open: species and level survive a disconnect unchanged,
 *   but a player who dislikes the wild Pikachu's nature can still reconnect until it changes, and
 *   shiny hunting by reconnect is the same trick. Closing that means writing those values into the
 *   properties string from this seed, which needs a shiny rate and an IV policy that nobody has
 *   decided yet. The seed is derived and carried now so that closing it later does not disturb the
 *   species and level draws of runs already in flight.
 */
data class WildEncounter(
    val wave: Int,
    val level: Int,
    val boss: Boolean,
    val species: WaveSpecies,
    val variantSeed: Long,
) {
    /**
     * The Cobblemon `PokemonProperties` string for this encounter.
     *
     * `level=` is written by us and not left to the species' own spawn data, which is what makes
     * wild-wave scaling independent of the runtime NPC level mutation that trainer waves need
     * (plan §2.14) — if that spike fails, this path is unaffected.
     *
     * The species goes in as `species=<namespace:path>` rather than as a bare leading token:
     * Cobblemon's keyed form runs the value through `asIdentifierDefaultingNamespace`, so an addon
     * species keeps its namespace instead of being looked up under `cobblemon:` and coming back
     * unresolved.
     */
    fun propertiesString(): String = buildString {
        append("species=").append(species.id)
        append(" level=").append(level)
        species.properties?.takeIf { it.isNotBlank() }?.let { append(' ').append(it.trim()) }
    }
}

/**
 * Picks the species and level for a wild wave from a run's seed.
 *
 * ### Determinism is the point, not a convenience
 *
 * Runs are checkpointable (plan §2.3), so without seeded generation a player who does not like
 * what wave 7 turned out to be can quit before it resolves and reconnect for a different one.
 * Every draw here is therefore a pure function of `(seed, wave)`: the same pair produces the same
 * encounter on any server, on any build, any number of times. Nothing in this class reads a clock,
 * a player, world state, or a shared RNG, and it holds no mutable state between calls — which is
 * also why one generator instance can serve every concurrent run.
 *
 * The one input that is not `(seed, wave)` is the pool itself. Editing the data layer's species
 * list *will* change what an in-flight run rolls for waves it has not reached yet; that is
 * unavoidable short of snapshotting the pool into every run, and it is an operator action rather
 * than something a player can trigger.
 */
class WildWaveGenerator(
    private val pool: WaveSpeciesPool,
    val curve: WaveLevelCurve = WaveLevelCurve(),
) {

    /**
     * Resolve the wild encounter for [wave] of the run seeded with [seed].
     *
     * Returns null when the pool offers nothing for this wave. The caller must treat that as a
     * configuration fault — skip the wave, or refuse to start the run — rather than papering over
     * it, since the alternative is a run that stalls with no explanation.
     *
     * [boss] exists because the curve is shared with trainer and boss waves (§2.14 builds those as
     * authored trainers, so in the shipping composition this is false for every call). It is a
     * parameter and not an interval check on [wave] because which waves are boss waves is the
     * composition layer's decision, not this one's.
     */
    fun generate(seed: Long, wave: Int, boss: Boolean = false): WildEncounter? {
        require(wave >= 1) { "wave is 1-based, got $wave" }

        val candidates = orderedCandidates(wave)
        if (candidates.isEmpty()) {
            log.warn("roguelite: no eligible wild species for wave {} — pool is empty or all weights are <= 0", wave)
            return null
        }

        val species = pick(candidates, WaveRandom.forDraw(seed, wave, WaveDrawStream.SPECIES))
        val level = curve.levelFor(wave, boss, WaveRandom.forDraw(seed, wave, WaveDrawStream.LEVEL))
        val variantSeed = WaveRandom.forDraw(seed, wave, WaveDrawStream.VARIANT).nextLong()

        return WildEncounter(wave = wave, level = level, boss = boss, species = species, variantSeed = variantSeed)
    }

    /**
     * The pool's entries in an order we control.
     *
     * A weighted pick walks the list, so the same species set delivered in a different order picks
     * a different winner for the same seed. Nothing guarantees the data layer's order is stable —
     * datapack file iteration, map values, and merge order across packs all vary between loads and
     * between servers — so a run resumed after a restart would silently roll different opponents.
     * Sorting by the entry's own content makes the draw depend on *what* is in the pool and not on
     * how it arrived. The secondary keys only exist to keep the order total when two entries name
     * the same species.
     */
    private fun orderedCandidates(wave: Int): List<WaveSpecies> =
        pool.eligibleAt(wave)
            .filter { it.weight > 0.0 }
            .sortedWith(compareBy({ it.id.toString() }, { it.properties ?: "" }, { it.weight }))

    private fun pick(candidates: List<WaveSpecies>, rng: WaveRandom): WaveSpecies {
        val total = candidates.sumOf { it.weight }
        var roll = rng.nextDouble() * total
        for (candidate in candidates) {
            roll -= candidate.weight
            if (roll < 0.0) return candidate
        }
        // Only reachable through floating-point summation error at the very top of the range.
        return candidates.last()
    }
}
