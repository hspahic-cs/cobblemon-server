package com.cobblemonroguelite.starter

import com.cobblemon.mod.common.api.pokemon.stats.Stats
import net.minecraft.resources.ResourceLocation
import java.util.UUID

/**
 * The minimum IVs a starter rolls with, per stat.
 *
 * Per-stat rather than one number because §2.17 sources the floor from *the best IVs of that species
 * caught during a run*, and a caught Pokémon has six of them. Collapsing that to a single figure
 * would have to pick between the best stat (which inflates every other) and the worst (which throws
 * away most of what the player earned), and neither is recoverable once the shape is fixed.
 */
data class StarterIvFloor(private val floors: Map<Stats, Int>) {

    /** Floor for [stat], clamped into a legal IV. Absent stats floor at 0, i.e. no floor. */
    fun floorFor(stat: Stats): Int = (floors[stat] ?: 0).coerceIn(0, MAX_IV)

    companion object {

        const val MAX_IV = 31

        /**
         * §2.17's flat starting point: every species begins at 10 in every stat, for every player,
         * before any run has been played. Not a placeholder — this is the decision.
         */
        const val BASE = 10

        fun flat(value: Int) = StarterIvFloor(CobblemonBaseStatTotal.STAT_ORDER.associateWith { value })

        /**
         * Six values in [CobblemonBaseStatTotal.STAT_ORDER] — HP, Attack, Defence, Sp. Attack,
         * Sp. Defence, Speed.
         *
         * Here so that a progression store keeping its floors as a flat record can hand one over in a
         * line, without either side having to know the other's field names. Returns null on the wrong
         * length rather than padding, because a five-element floor is a bug and a silently zeroed
         * Speed floor is a bug that plays.
         */
        fun of(values: List<Int>): StarterIvFloor? =
            if (values.size != CobblemonBaseStatTotal.STAT_ORDER.size) null
            else StarterIvFloor(CobblemonBaseStatTotal.STAT_ORDER.zip(values).toMap())

        val Base = flat(BASE)
    }
}

/**
 * What the per-species progression store contributes to starter selection — **a consumer-side
 * seam, not the store**.
 *
 * ### Why this is declared here and implemented elsewhere
 *
 * §2.15 splits progression in two: the server Pokédex decides *which* species you may start with,
 * and in-run play decides *how good* they are — candy toward cost reductions, and the IV floor from
 * §2.17. The first half is this package's business and is already wired ([CaughtSpeciesSource]). The
 * second half is a persistent per-player, per-species store that this package does not own and must
 * not grow a second copy of.
 *
 * So selection declares the two questions it needs answered, in the terms *it* thinks in, and
 * defaults them to the pre-progression answers. Until the store registers itself, a budget run is a
 * complete, playable feature at base prices and base IVs; afterwards nothing in this package changes.
 * The alternative — selection reaching into the store directly — would make the store a build
 * dependency of run start, and every test in this package would need one.
 *
 * ### What implementations may and may not do
 *
 * Both methods are called on the server thread, once per catalogue build or per starter created, and
 * must not block. [effectiveCost] is clamped by the caller into `1..baseCost`: candy may make a
 * species cheaper and may never make it dearer, because a progression system that can price a player
 * *out* of something they used to afford is a punishment for playing.
 */
interface StarterProgression {

    /**
     * What [species] costs [player] after candy-bought reductions, given its [baseCost] from
     * [StarterCostSource].
     *
     * The base cost is passed in rather than looked up, so that an implementation cannot become a
     * second pricing authority: it may only discount a number this package already computed.
     */
    fun effectiveCost(player: UUID, species: ResourceLocation, baseCost: Int): Int

    /** The IV floor [player] has earned for [species] (§2.17). Never below [StarterIvFloor.BASE]. */
    fun ivFloor(player: UUID, species: ResourceLocation): StarterIvFloor

    /**
     * Whether [player] has bought the hidden-ability unlock that covers [species] (§2.27).
     *
     * Asked in *this* package's terms — a species about to be built — and answered by whoever owns
     * the ledger. That matters more here than it does for the other two questions, because candy is
     * banked on the **evolution line's root** (§2.17): the unlock for Charizard was bought on
     * Charmander's ledger, and resolving that is the store's job. Selection asks about the species it
     * is actually building and must not try to walk a line itself, or the two walks could disagree
     * and a player would own an unlock that never fires.
     *
     * *Which* ability that unlock grants is a separate question with a separate answer
     * ([HiddenAbilityGrant]), and it is keyed on this species rather than on the root — a Charizard
     * gets Charizard's, not Charmander's.
     */
    fun hiddenAbilityUnlocked(player: UUID, species: ResourceLocation): Boolean

    companion object {

        /** No reductions, flat base IVs, no unlocks. What a server with no progression store behaves like. */
        val Base: StarterProgression = object : StarterProgression {
            override fun effectiveCost(player: UUID, species: ResourceLocation, baseCost: Int) = baseCost
            override fun ivFloor(player: UUID, species: ResourceLocation) = StarterIvFloor.Base
            override fun hiddenAbilityUnlocked(player: UUID, species: ResourceLocation) = false
        }

        /**
         * The live implementation. `@Volatile` for the reason [com.cobblemonroguelite.run.RunSettings]
         * is: this is set from another component's setup thread and read from the server thread.
         */
        @Volatile
        var current: StarterProgression = Base
            private set

        fun set(progression: StarterProgression) {
            current = progression
        }

        /** Back to base prices and base IVs. For tests, and for unloading the store. */
        fun reset() {
            current = Base
        }
    }
}
