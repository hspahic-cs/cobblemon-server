package com.cobblemonroguelite.run

import com.cobblemonroguelite.data.biome.RunBiome
import com.cobblemonroguelite.wave.WaveDrawStream
import com.cobblemonroguelite.wave.WaveRandom
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation

/**
 * Where a run is, and which band it got there in (§2.24).
 *
 * ### Why the band is stored next to the biome
 *
 * The biome alone cannot say whether it is still current: bands are ten waves long, so a run holding
 * "volcano" at wave 41 might be in its first wave of a new band or its last wave of an old one, and
 * those want opposite things. Storing the band the biome was entered for makes the comparison a
 * single equality against the band the wave is now in — no history, no boundary arithmetic that has
 * to agree with itself in two places.
 *
 * @property band the [BiomeRotation.bandOf] index this biome was entered for, **not** a wave. Derived
 *   from the wave and the configured band length at the moment of entry, so a server that re-tunes the
 *   band length mid-run moves the boundary for waves ahead and leaves this one where it is — which is
 *   the same behaviour a re-tuned trainer roster has, and the alternative is a run that changes biome
 *   in the middle of a band for reasons the player cannot see.
 * @property biome the [com.cobblemonroguelite.data.biome.RunBiome] id, which may name a file that has
 *   since been deleted. Held as an id rather than as the definition for [RunState.payoutTable]'s
 *   reason: what is pinned is which biome, never what that biome contains.
 */
data class BiomeVisit(val band: Int, val biome: ResourceLocation) {

    fun toNbt(): CompoundTag = CompoundTag().apply {
        putInt("band", band)
        putString("biome", biome.toString())
    }

    companion object {

        /**
         * Null when the tag cannot be read, which restores as "no biome yet".
         *
         * The safe failure direction, and unlike [RunBattleMarker] it is safe because nothing is
         * destroyed either way: the rotation re-picks for the current band on the next arena prepare,
         * the arena is re-stamped and repainted, and the player is told they have arrived somewhere.
         * The worst outcome is a run that changes scenery once after a damaged checkpoint.
         */
        fun fromNbt(tag: CompoundTag): BiomeVisit? {
            if (!tag.contains("band")) return null
            val biome = ResourceLocation.tryParse(tag.getString("biome")) ?: return null
            return BiomeVisit(tag.getInt("band"), biome)
        }
    }
}

/**
 * Which biome a run is in at a given wave — §2.24's rotation.
 *
 * ### Why this decides nothing about the arena
 *
 * It answers "which biome", and the arena layer turns that into a build and a repaint. That split is
 * what keeps the interesting half testable: the boundary arithmetic and the seeded draw are exactly
 * the sort of thing that is silent in play (a band that transitions one wave late looks like nothing
 * at all) and immediate in a test, while everything it feeds needs a booted server.
 *
 * ### Why the pick is seeded and why that is not the final word
 *
 * §2.24 leaves open whether a transition is **chosen** by the player or seeded, and this is the
 * seeded half. It is written so the other half can replace exactly one line: [next] returns the
 * stored [BiomeVisit] untouched whenever the band has not moved, so a player-chosen transition only
 * has to write a visit for the new band and this will preserve it. That is also why the visit is
 * persisted at all rather than recomputed from `(seed, band)` on demand — recomputing works today and
 * would have to be unpicked, with a schema bump, the moment a choice exists.
 *
 * The draw is a pure function of `(seed, band)` for [com.cobblemonroguelite.wave.WildWaveGenerator]'s
 * reason: a resumed run must not find itself somewhere else. Note it is keyed on the band and not the
 * wave, so every wave inside a band asks the same question and gets the same answer even if the
 * stored visit is lost.
 */
object BiomeRotation {

    /**
     * Which band [wave] falls in, 0-based.
     *
     * Zero-based so that band 0 is waves 1–10 and the arithmetic is one expression. The band index
     * is never shown to a player — it exists to be compared against [BiomeVisit.band] and to salt the
     * draw — so there is nothing to be gained by making it read like a chapter number.
     */
    fun bandOf(wave: Int, bandLength: Int): Int {
        require(wave >= 1) { "wave is 1-based, got $wave" }
        require(bandLength >= 1) { "bandLength must be at least 1, was $bandLength" }
        return (wave - 1) / bandLength
    }

    /**
     * The visit that should be in force at [wave], given what the run already holds.
     *
     * Returns [current] unchanged while the band has not moved — see the class docs for why that is
     * the seam a player-chosen transition plugs into rather than an optimisation.
     *
     * Returns [current] **also** when the band has moved and [eligible] is empty, which is the case
     * worth stating: a datapack edited mid-run can leave a band with no biome, and the choice is
     * between leaving the run in the last biome it legitimately entered or dropping it back to the
     * configured default build mid-run. Keeping the last one costs the player nothing and repairs
     * itself at the next band; dropping it would demolish and re-stamp their arena because somebody
     * else's file has a typo in it.
     *
     * Null is only ever returned when the run has no biome and none is eligible, i.e. on a server
     * with no biomes configured — which is the shipped state, and the arena layer reads it as "leave
     * everything as it is".
     */
    fun next(
        current: BiomeVisit?,
        wave: Int,
        bandLength: Int,
        seed: Long,
        eligible: List<RunBiome>,
    ): BiomeVisit? {
        val band = bandOf(wave, bandLength)
        if (current != null && current.band == band) return current
        if (eligible.isEmpty()) return current
        return BiomeVisit(band, pick(eligible, WaveRandom.forDraw(seed, band, WaveDrawStream.BIOME)).id)
    }

    /**
     * A weighted walk, identical in shape to the wild species draw.
     *
     * The caller supplies the order ([com.cobblemonroguelite.data.biome.RunBiomes.eligibleAt] sorts),
     * because a walk is order-sensitive and the layer that owns the data is the one that can promise
     * a stable one.
     */
    private fun pick(candidates: List<RunBiome>, rng: WaveRandom): RunBiome {
        val total = candidates.sumOf { it.weight }
        var roll = rng.nextDouble() * total
        for (candidate in candidates) {
            roll -= candidate.weight
            if (roll < 0.0) return candidate
        }
        // Floating-point drift only — the roll is strictly below the total, so this is reached when
        // the running subtraction loses the last fraction of an ulp. The last candidate is the one
        // the walk was inside of.
        return candidates.last()
    }
}
