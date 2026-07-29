package com.cobblemonroguelite.wave

/**
 * Identifies which draw a [WaveRandom] is for.
 *
 * Two draws on the same wave (which species, what level) must not move together. Reusing one
 * stream for both would correlate them — every seed that rolled a high species index would also
 * roll a high level — and worse, adding a third draw later would shift every draw after it, so a
 * run checkpointed before the change would resume with different opponents. A salt per draw makes
 * each one an independent function of `(seed, wave)`, so new draws can be added without disturbing
 * the existing ones.
 *
 * **The salts are part of the save format.** They are written out literally rather than derived
 * from `ordinal` or `name.hashCode()` for that reason: reordering the constants, renaming one, or
 * a JVM changing `String.hashCode` would re-roll every in-flight run's remaining waves. Never
 * change an existing salt; only ever append new constants.
 */
enum class WaveDrawStream(internal val salt: Long) {
    /** Which species the wild encounter is. */
    SPECIES(0x5350_4543_4945_5301L),

    /** The encounter's level, before clamping. */
    LEVEL(0x4C45_5645_4C00_0002L),

    /**
     * Reserved for the per-Pokémon rolls we do not make here — IVs, nature, gender, shiny. See
     * [WildEncounter.variantSeed] for why those need a seeded stream at all.
     */
    VARIANT(0x5641_5249_414E_5403L),

    /**
     * Which authored trainer a trainer or boss wave draws from its roster band — see
     * [com.cobblemonroguelite.data.trainer.TrainerRoster].
     *
     * Appended rather than slotted in beside [SPECIES], per this enum's own rule: a run checkpointed
     * before rosters existed resumes with the same wild species and levels it had, because nothing
     * above this line moved.
     */
    TRAINER(0x5452_4149_4E45_5204L),

    /**
     * Which biome a wave band rotates into — see [com.cobblemonroguelite.run.BiomeRotation].
     *
     * The one stream drawn per *band* rather than per wave. That is not a violation of the
     * `(seed, wave)` contract above so much as a reading of it: the band index is passed where the
     * wave goes, so every wave inside a band asks the same question and gets the same answer, which
     * is what stops a run changing scenery every wave.
     */
    BIOME(0x4249_4F4D_4500_0005L),

    /**
     * Which Pokémon a generated trainer team is made of — which filler slots it uses and which
     * alternative fills each slot. See
     * [com.cobblemonroguelite.data.trainer.TrainerTeamGenerator].
     *
     * Appended, like [TRAINER] before it: a run checkpointed before §2.30's generated teams existed
     * resumes against the same trainers at the same levels, because every stream above this line is
     * untouched. The team it now generates for a wave it has *not* reached is new content in an old
     * run, which is the same thing a roster edit does and is accepted for the same reason.
     */
    TRAINER_TEAM(0x5445_414D_0000_0006L),

    /**
     * Which held items a generated team's Pokémon carry.
     *
     * Separate from [TRAINER_TEAM] so that adding, removing or re-tuning an item tier cannot change
     * *which Pokémon* a trainer brings. Sharing one stream would make a balance edit silently re-roll
     * every species draw of every in-flight run.
     */
    TRAINER_ITEM(0x4954_454D_0000_0007L),
}

/**
 * A deterministic random stream for one `(run seed, wave, draw)` triple.
 *
 * ### Why not [java.util.Random] or `kotlin.random.Random`
 *
 * A resumed run has to roll the *same* opponents it would have rolled before the disconnect
 * (plan §2.3 — the seed exists so that pulling the plug cannot reroll a bad wave). That guarantee
 * has to hold across a server restart onto a new Minecraft build, a new Kotlin stdlib, and a
 * different JVM vendor, because those are exactly the things that happen between a player logging
 * out and logging back in. Both platform generators are specified today, but neither is a format
 * we control, and neither announces a change: a resumed run would just quietly fight different
 * Pokémon. SplitMix64 is ~15 lines, so we own the sequence outright and it cannot drift.
 *
 * Not thread-safe, and does not need to be: a stream is created for a single draw and discarded.
 */
class WaveRandom private constructor(private var state: Long) {

    fun nextLong(): Long {
        state += GAMMA
        var z = state
        z = (z xor (z ushr 30)) * MIX_A
        z = (z xor (z ushr 27)) * MIX_B
        return z xor (z ushr 31)
    }

    /** Uniform in `[0, 1)`. */
    fun nextDouble(): Double = (nextLong() ushr 11).toDouble() * DOUBLE_UNIT

    /**
     * Standard normal, by the Box–Muller transform.
     *
     * [StrictMath] rather than [Math] for the same reason this class exists: `Math.log` and
     * `Math.cos` are allowed a 1–2 ulp error and may be implemented by intrinsics that differ
     * between JVM builds and CPUs, which is enough to move a level across a rounding boundary on
     * some fraction of rolls. `StrictMath` is bit-reproducible everywhere by specification, and
     * costs nothing at the rate we call it (twice per wave).
     *
     * The non-rejecting form is deliberate: Marsaglia polar would consume a variable number of
     * draws, which is harmless here but makes the stream impossible to reason about if anyone
     * later wants to reproduce a roll by hand.
     */
    fun nextGaussian(): Double {
        // 1 - u so u1 is in (0, 1]: ln(0) is -inf and would produce NaN.
        val u1 = 1.0 - nextDouble()
        val u2 = nextDouble()
        return StrictMath.sqrt(-2.0 * StrictMath.log(u1)) * StrictMath.cos(2.0 * StrictMath.PI * u2)
    }

    companion object {
        // SplitMix64 constants (Steele/Lea/Flood). Written as unsigned literals because they do not
        // fit a signed Long; the bit patterns are what matter, not the signs.
        private val GAMMA = 0x9E3779B97F4A7C15uL.toLong()
        private val MIX_A = 0xBF58476D1CE4E5B9uL.toLong()
        private val MIX_B = 0x94D049BB133111EBuL.toLong()

        private const val DOUBLE_UNIT = 1.0 / (1L shl 53).toDouble()

        /**
         * The stream for one draw on one wave of one run.
         *
         * The three inputs are folded through the same mixer used to generate output, so that runs
         * whose seeds differ by one — which is what a naive "seed = System.currentTimeMillis()"
         * hands out to two players starting together — do not get near-identical wave sequences.
         */
        fun forDraw(seed: Long, wave: Int, stream: WaveDrawStream): WaveRandom {
            val mixer = WaveRandom(seed xor stream.salt)
            val salted = mixer.nextLong()
            return WaveRandom(WaveRandom(salted + wave.toLong() * GAMMA).nextLong())
        }
    }
}
