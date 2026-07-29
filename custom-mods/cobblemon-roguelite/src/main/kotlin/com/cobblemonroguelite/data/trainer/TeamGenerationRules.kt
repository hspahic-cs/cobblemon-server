package com.cobblemonroguelite.data.trainer

import net.minecraft.resources.ResourceLocation

/**
 * How big a generated party is from [minWave] onward.
 *
 * §2.30 puts this at 4 early, 5 mid, 6 late, and it is a difficulty lever rather than a fidelity
 * detail: §2.19 leaves waves ~138–200 at flat level 100, so party size is one of the few dials still
 * turning up there.
 *
 * First **matching** tier wins, and tiers are sorted by [minWave] descending before matching, so the
 * order they are written in does not decide the answer. That is different from the roster's bands,
 * where written order is precedence — the difference is deliberate: bands are a schedule an author
 * reads top-down, and this is a step function where "the highest threshold I have passed" is the only
 * sane reading.
 */
data class PartySizeTier(val minWave: Int, val size: Int) {
    init {
        require(minWave >= 1) { "minWave is 1-based, was $minWave" }
        require(size in 1..6) { "party size must be 1..6, was $size" }
    }
}

/**
 * Which stage of a [SpeciesLine] a wave brings.
 *
 * PokéRogue's rule is "fully evolved from wave 80"; everything below that is partially evolved. Ours
 * is that rule plus the intermediate thresholds needed to say *how* partially:
 *
 * - below the first entry of [stageWaves] — the base form;
 * - past each entry — one stage further up, clamped to the length of the line, so a two-stage line is
 *   already final where a three-stage one is halfway;
 * - from [fullyEvolvedFrom] — the last stage, whatever the line's length.
 *
 * The last clause is not redundant with the clamp. A line longer than `stageWaves.size + 1` would
 * otherwise never reach its final stage at all, and "this trainer's Golem never appears" is the kind
 * of bug that is only visible to somebody who reads the data.
 */
data class EvolutionSchedule(
    val stageWaves: List<Int> = DEFAULT_STAGE_WAVES,
    val fullyEvolvedFrom: Int = DEFAULT_FULLY_EVOLVED_FROM,
) {
    init {
        require(stageWaves.all { it >= 1 }) { "stage waves are 1-based, got $stageWaves" }
        require(stageWaves == stageWaves.sorted()) { "stage waves must ascend, got $stageWaves" }
        require(fullyEvolvedFrom >= 1) { "fullyEvolvedFrom is 1-based, was $fullyEvolvedFrom" }
    }

    fun stageIndexFor(wave: Int): Int =
        if (wave >= fullyEvolvedFrom) Int.MAX_VALUE else stageWaves.count { it <= wave }

    companion object {
        /** One intermediate step. Below wave 20 a leader's Geodude is a Geodude. */
        val DEFAULT_STAGE_WAVES = listOf(20)

        /** PokéRogue's own number, and it lands where it does because §2.19 adopted their run length. */
        const val DEFAULT_FULLY_EVOLVED_FROM = 80
    }
}

/** One item a generated Pokémon might hold, with its share of the tier's roll. */
data class HeldItemChoice(val item: ResourceLocation, val weight: Double = 1.0)

/**
 * The held items available over a wave range, and how likely each Pokémon is to have one.
 *
 * ### Why this is data and not a table in here
 *
 * §2.30 asks for held items "scaled by band and boss status, mirroring their `genModifiers`", and
 * then says the item **choices** are content. So the mechanism ships and the list does not: a mod
 * with no tiers generates trainers that hold nothing, which is a visible, honest default. A shipped
 * item table would be a balance decision made by whoever wrote the mod rather than by whoever runs
 * the server, and — per §2.7 — the transcribed content stays out of a published build anyway.
 *
 * @property boss null matches every wave, true only boss waves, false only ordinary trainer waves.
 *   Three states rather than two because "the same items everywhere" is the common case and should
 *   not require writing the tier twice.
 * @property chance per Pokémon, 0..1. Rolled per party member rather than per team, so a boss tier at
 *   0.5 is "about half the team is holding something" — which is the shape PokéRogue's modifier
 *   stacking has, and a per-team roll is not.
 * @property count how many items to try to place on one Pokémon. Cobblemon holds one item, so this is
 *   *not* a stack: it is how many independent draws that Pokémon gets, and the last one wins. It
 *   exists so a late boss tier can be made to nearly always land something without pushing [chance]
 *   to 1.0 and losing the variance entirely.
 */
data class HeldItemTier(
    val minWave: Int,
    val maxWave: Int? = null,
    val boss: Boolean? = null,
    val chance: Double,
    val count: Int = 1,
    val items: List<HeldItemChoice>,
) {
    init {
        require(minWave >= 1) { "minWave is 1-based, was $minWave" }
        require(maxWave == null || maxWave >= minWave) { "maxWave ($maxWave) is before minWave ($minWave)" }
        require(chance in 0.0..1.0) { "chance must be 0..1, was $chance" }
        require(count >= 1) { "count must be at least 1, was $count" }
        require(items.isNotEmpty()) { "a held item tier with no items would never place anything" }
    }

    fun covers(wave: Int, isBoss: Boolean): Boolean =
        wave >= minWave && (maxWave == null || wave <= maxWave) && (boss == null || boss == isBoss)
}

/**
 * How many PokéRogue-style shields a boss wave's Pokémon carry, from [minWave] onward.
 *
 * §2.32's mechanic: the holder's HP is divided into `shields + 1` chunks, a hit may never carry it
 * past a chunk boundary, and each break boosts a random stat. It rides on the held item slot, so a
 * shielded Pokémon holds nothing else — see [TeamSpecies.propertiesString].
 *
 * ### Why this is data, and why it ships empty
 *
 * The same argument [HeldItemTier] makes. *That* bosses can be shielded is the mechanism and it
 * ships; *which* bosses, from which wave, with how many, is a difficulty curve, and a curve chosen
 * by whoever wrote the mod rather than by whoever runs the server is a balance decision made in the
 * wrong place. With no tiers declared, boss waves are ordinary trainer waves that hit harder,
 * which is a visible and honest default rather than a silent one.
 *
 * Matched like [PartySizeTier] and not like [HeldItemTier]: highest passed threshold wins,
 * regardless of written order, because "how deep am I" is the only sane reading of a difficulty
 * ramp. Boss waves only — an unshielded ordinary trainer is the point of the distinction.
 *
 * @property shields 1..[com.cobblemonroguelite.boss.BossShields.MAX_SHIELDS]. The ceiling is not a
 *   taste judgement: there is one datapack JS file per count, so a number above it produces a held
 *   item id Showdown has never heard of and the boss silently fights with no shields at all.
 * @property members how many of the team, **taken from the front**, are shielded. Front because
 *   §2.30 keeps signature slots in written order precisely so that slot one can be the ace, which
 *   is where PokéRogue puts a leader's Terastallised signature Pokémon. One by default: a whole
 *   team of shielded Pokémon is not a harder fight so much as a longer one.
 */
data class BossShieldTier(val minWave: Int, val shields: Int, val members: Int = 1) {
    init {
        require(minWave >= 1) { "minWave is 1-based, was $minWave" }
        require(shields >= 1) { "shields must be at least 1, was $shields" }
        require(members >= 1) { "members must be at least 1, was $members" }
    }
}

/**
 * Everything a roster needs to turn a [TrainerEntry] into a team, other than the seed and the wave.
 *
 * ### Why it lives on the roster and not in [com.cobblemonroguelite.run.RunConfig]
 *
 * Because a roster is written *against* it. The party sizes and the evolution schedule decide what an
 * entry's four slots and its filler actually produce, and an operator who edits one without the other
 * gets a roster whose filler is dead data or whose leaders never evolve — with nothing to tie the two
 * together. Keeping them in the same file means `ops/gen_pokerogue_roster.py` writes both, the
 * validator reads both, and a roster remains a self-contained description of "who you fight".
 *
 * The wave *curve* stays out, for the reason [TrainerBand] gives about levels: a roster that could
 * restate the level curve would give a server two places that answer how hard wave 150 is.
 *
 * The defaults are §2.30's numbers, so a roster that says nothing behaves the way the decision
 * describes — except for [heldItems], which is empty because item choices are content (see
 * [HeldItemTier]).
 */
data class TeamGenerationRules(
    val partySizes: List<PartySizeTier> = DEFAULT_PARTY_SIZES,
    val evolution: EvolutionSchedule = EvolutionSchedule(),
    val heldItems: List<HeldItemTier> = emptyList(),
    val bossShields: List<BossShieldTier> = emptyList(),
) {

    /** Sorted once here rather than on every wave — the answer must not depend on written order. */
    private val orderedSizes = partySizes.sortedByDescending { it.minWave }

    /** Same treatment, same reason. See [BossShieldTier]. */
    private val orderedShields = bossShields.sortedByDescending { it.minWave }

    /**
     * How many Pokémon a trainer met at [wave] brings.
     *
     * Falls back to the *smallest* declared size when no tier covers the wave, rather than to a
     * hardcoded 6: a rules block whose lowest tier starts at wave 10 has said nothing about wave 1,
     * and answering "six" there would make an early leader harder than any later one. Empty rules
     * answer with the shipped default's first tier.
     */
    fun partySizeFor(wave: Int): Int =
        orderedSizes.firstOrNull { wave >= it.minWave }?.size
            ?: orderedSizes.minOfOrNull { it.size }
            ?: DEFAULT_PARTY_SIZES.first().size

    /** The tier serving [wave], first match wins, or null when nothing does — i.e. no held items. */
    fun heldItemsFor(wave: Int, boss: Boolean): HeldItemTier? =
        heldItems.firstOrNull { it.covers(wave, boss) }

    /**
     * The shield tier a boss met at [wave] uses, or null for no shields.
     *
     * Null on every non-boss wave whatever the tiers say, so an operator cannot accidentally shield
     * the ordinary trainer waves by writing a low `min_wave` — the mechanic is what makes a boss
     * wave a boss wave, and §2.14's schedule is the only thing allowed to decide which waves those
     * are.
     */
    fun bossShieldsFor(wave: Int, boss: Boolean): BossShieldTier? =
        if (!boss) null else orderedShields.firstOrNull { wave >= it.minWave }

    companion object {
        /** §2.30: 4 early, 5 mid, 6 late. The edges are tuning, the shape is the decision. */
        val DEFAULT_PARTY_SIZES = listOf(
            PartySizeTier(minWave = 1, size = 4),
            PartySizeTier(minWave = 60, size = 5),
            PartySizeTier(minWave = 120, size = 6),
        )
    }
}
