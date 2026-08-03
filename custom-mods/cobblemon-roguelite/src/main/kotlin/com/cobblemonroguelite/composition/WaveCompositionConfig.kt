package com.cobblemonroguelite.composition

import com.cobblemonroguelite.CobblemonRoguelite
import com.cobblemonroguelite.integration.RunOpponent
import com.cobblemonroguelite.wave.WaveLevelCurve
import net.minecraft.resources.ResourceLocation

/**
 * The shape of a run: how long it is, which waves are trainers, which are bosses, how opponent level
 * moves with depth, and which reward table each wave rolls from.
 *
 * ### Why every interval is a field and not a constant
 *
 * "Every 5th wave is a trainer, every 10th is a boss" is PokéRogue's schedule (plan §2.14), and it is
 * the thing most likely to be wrong for us: our battles take minutes rather than seconds (§2.19), so
 * the ratio of trainer battles to wild ones is a pacing decision that will be retuned against real
 * play. Hardcoding it would put a balance dial behind a rebuild, and — worse — behind a code review,
 * which is the failure §2.12 already rejected for reward contents.
 *
 * ### Where this comes from at runtime
 *
 * Nothing loads this yet; a run builds one and passes it in. When it needs to become data, it follows
 * [com.cobblemonroguelite.data.RogueliteDataRegistry] like every other table in this mod rather than
 * growing a second mechanism — but that base class is many-files-keyed-by-id and this is a singleton,
 * so what it needs first is a convention for "one document, fixed id" (read
 * `roguelite/wave_composition/<name>.json` and have the run name which one it uses, so a server can
 * ship a short campaign and a classic one side by side). That is a decision, not an implementation
 * detail, so it is left open rather than guessed at here.
 *
 * @property runLength waves in a full run. 200 is §2.19's choice, taken from PokéRogue's Classic
 *   length deliberately, so the level curve below can be theirs literally.
 * @property trainerInterval every Nth wave is an authored trainer instead of a wild encounter.
 * @property bossInterval every Nth wave is a boss. Boss beats trainer where they collide; see
 *   [WaveComposition.kindOf].
 * @property curve shared by all three wave kinds. [WaveLevelCurve]'s defaults *are* PokéRogue's
 *   Classic constants (§2.19 adopted their run length precisely so the curve could be theirs
 *   literally), so the default here is simply the default curve.
 * @property rewards which reward table a wave rolls from.
 */
data class WaveCompositionConfig(
    val runLength: Int = 200,
    val trainerInterval: Int = 5,
    val bossInterval: Int = 10,
    val curve: WaveLevelCurve = WaveLevelCurve(),
    val rewards: RewardRouting = RewardRouting(),
) {
    init {
        require(runLength >= 1) { "runLength must be at least 1, was $runLength" }
        // Zero or negative would make the modulo below throw or match every wave; either way the
        // symptom is "the whole run is boss battles" or a crash mid-run, both from a config typo
        // that nothing else would name.
        require(trainerInterval >= 1) { "trainerInterval must be at least 1, was $trainerInterval" }
        require(bossInterval >= 1) { "bossInterval must be at least 1, was $bossInterval" }
    }

    /**
     * True when boss waves are a strict subset of trainer waves, i.e. when the two intervals line up
     * the way PokéRogue's 5/10 do.
     *
     * Exposed rather than enforced. An operator is entitled to set 5/7, and the result is coherent —
     * it just means some trainer waves are skipped where a boss lands on a non-multiple, so the count
     * of trainer battles per run stops being `runLength/trainerInterval`. Enforcing alignment would
     * refuse a legitimate schedule; saying nothing would let someone tune the intervals and quietly
     * lose trainer waves they thought they were keeping.
     */
    fun intervalsAligned(): Boolean = bossInterval % trainerInterval == 0

    companion object {
        /**
         * Cobblemon's global `maxPokemonLevel`. Duplicated as a constant rather than read from their
         * config because this has to be known while *planning* a run — including in tests with no
         * server — and because a server that lowers it wants the curve clamped to the new value, which
         * is a config edit here rather than a surprise at spawn time.
         */
        const val COBBLEMON_MAX_LEVEL = 100
    }
}

/**
 * Which reward table a wave rolls from.
 *
 * ### Ids, not tables
 *
 * This resolves to a [ResourceLocation] and stops. Looking the id up in
 * [com.cobblemonroguelite.data.reward.RewardTables] is the caller's job, so composition stays a pure
 * function that a test can drive without a resource manager, and so a table that is missing at roll
 * time is reported by the layer that actually knows the registry was empty.
 *
 * ### Two mechanisms, because they answer different questions
 *
 * [byKind] is the standing rule — wild waves pay out one thing, bosses another. [bands] is the
 * exception, matched first: "waves 180+ roll from the endgame table". Collapsing them into one list
 * would mean the ordinary case could not be stated without also stating a wave range, and every
 * schedule change would have to be reflected in the reward routing too.
 *
 * A kind absent from [byKind] with no band covering it means **no reward for that wave**, which is a
 * legitimate thing to author (PokéRogue does not reward every wave identically) and is why callers
 * get a nullable id rather than a fault.
 */
data class RewardRouting(
    /**
     * PLACEHOLDER IDS. These name datapack files nobody has written; they exist so the default
     * config resolves to *something* addressable rather than to null everywhere, and so the naming
     * convention (`data/<ns>/roguelite/reward_tables/<kind>.json`) is stated once. Contents, rarity
     * and which kinds share a table are all §2.12's owner's call.
     */
    val byKind: Map<RunOpponent, ResourceLocation> = mapOf(
        RunOpponent.WILD to table("wild"),
        RunOpponent.TRAINER to table("trainer"),
        RunOpponent.BOSS to table("boss"),
        // NO RIVAL ENTRY, and its absence is the decision rather than an omission. §2.36's rival waves
        // are all *promoted* waves ([com.cobblemonroguelite.data.trainer.RivalLadder]), and a promoted
        // wave rewards as the wave the schedule called it — pinned in `RunProgressTest`, because
        // re-pointing a reward table from a roster file would be a balance change made by data, and
        // §2.12 puts routing in the operator's hands. So [tableFor] is never called with RIVAL, and a
        // key here would be config that looks live and does nothing.
        //
        // The consequence is worth stating because it is a wart rather than a virtue: a rival at wave 8
        // rolls the *wild* table, since that is what wave 8 is scheduled as. It is the same wart a
        // promoted Elite Four member has at wave 182 and is fixable today with a one-wave [RewardBand];
        // whether promotions should route by effective kind is a §2.12 question this class must not
        // answer for it.
    ),

    /** Checked in order, first match wins, so an author reads precedence off the file top-down. */
    val bands: List<RewardBand> = emptyList(),
) {
    /** The table for a wave of this kind, or null when nothing is routed there. */
    fun tableFor(wave: Int, kind: RunOpponent): ResourceLocation? =
        bands.firstOrNull { it.covers(wave, kind) }?.tableId ?: byKind[kind]

    companion object {
        private fun table(name: String): ResourceLocation =
            ResourceLocation.fromNamespaceAndPath(CobblemonRoguelite.MOD_ID, name)
    }
}

/**
 * A wave range that overrides [RewardRouting.byKind].
 *
 * @property kind null matches any kind. Present so a band can say "everything past wave 180" without
 *   having to be written out once per wave kind, which is the form it would take otherwise and would
 *   be three places to edit when the boundary moves.
 * @property maxWave inclusive, null for open-ended — deliberately the same convention as
 *   [com.cobblemonroguelite.data.reward.RewardEntry], since an author will meet both.
 */
data class RewardBand(
    val minWave: Int,
    val maxWave: Int? = null,
    val kind: RunOpponent? = null,
    val tableId: ResourceLocation,
) {
    init {
        require(minWave >= 1) { "minWave must be at least 1, was $minWave" }
        require(maxWave == null || maxWave >= minWave) {
            "maxWave ($maxWave) is before minWave ($minWave), so this band could never match"
        }
    }

    fun covers(wave: Int, waveKind: RunOpponent): Boolean =
        wave >= minWave && (maxWave == null || wave <= maxWave) && (kind == null || kind == waveKind)
}
