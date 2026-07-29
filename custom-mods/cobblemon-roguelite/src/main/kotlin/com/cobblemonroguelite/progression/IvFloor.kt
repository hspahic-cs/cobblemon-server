package com.cobblemonroguelite.progression

import net.minecraft.nbt.CompoundTag

/**
 * The IVs a species is *guaranteed* to start a run with, per §2.17.
 *
 * Six plain [Int]s and not Cobblemon's `IVs`/`Stats` types, deliberately. This is the value that gets
 * persisted, compared and tested, and Cobblemon's stat classes cannot be touched outside a booted
 * game — `IVs` extends `PokemonStats`, which builds MoLang structs and translatable display names on
 * construction. Keeping the stored form primitive is what lets the whole of §2.17's arithmetic run in
 * a plain JUnit test instead of shipping never having executed. The conversion from a real Pokémon's
 * IVs lives in [RunProgression], which is the only place that needs a booted server anyway.
 *
 * ### Why per-stat maxima rather than "the best spread"
 *
 * A "best spread" needs a total order over spreads, and there isn't one — 31/0/31/0/31/0 and
 * 20/20/20/20/20/20 are each better than the other depending on the Pokémon. Taking the max per stat
 * (which is what PokéRogue does) sidesteps the question entirely and is monotone: catching something
 * can only ever raise a floor, never lower one, so a player is never punished for a catch. That
 * matters more than it sounds — the alternative, "the last good one wins", makes a mediocre catch of
 * a species you already have *cost* you, and nothing about the mechanic would explain why.
 *
 * The consequence is stated rather than hidden: the floor is a composite of many catches and is
 * therefore better than any single Pokémon that produced it. That is the mechanic, not a leak — it
 * mints no Pokémon, and it only ever applies to a run-scoped starter that dies with the run (§1.1).
 */
data class IvFloor(
    val hp: Int,
    val attack: Int,
    val defence: Int,
    val specialAttack: Int,
    val specialDefence: Int,
    val speed: Int,
) {

    /** In the order [FROM_NBT_ORDER] documents. Used for serialization and for stat-wise folds. */
    fun asList(): List<Int> = listOf(hp, attack, defence, specialAttack, specialDefence, speed)

    /**
     * This floor raised to whatever [other] is better at, stat by stat. Never lowers anything — see
     * the class docs for why that property is the whole point.
     */
    fun raisedBy(other: IvFloor): IvFloor = IvFloor(
        hp = maxOf(hp, other.hp),
        attack = maxOf(attack, other.attack),
        defence = maxOf(defence, other.defence),
        specialAttack = maxOf(specialAttack, other.specialAttack),
        specialDefence = maxOf(specialDefence, other.specialDefence),
        speed = maxOf(speed, other.speed),
    )

    /** True when nothing has been earned yet, i.e. this is still [BASE]. Lets storage skip writing it. */
    fun isBase(): Boolean = this == BASE

    fun toNbt(): CompoundTag = CompoundTag().apply { putIntArray(VALUES_KEY, asList().toIntArray()) }

    companion object {

        /**
         * §2.17: "every species starts at a flat base 10 IVs". A constant here rather than a config
         * value on purpose — it is the *definition* of the unearned state, and a server that lowered
         * it would silently make every already-stored floor look earned when it was not.
         */
        const val BASE_IV = 10

        /** What a species the player has never caught in a run is worth. */
        val BASE = flat(BASE_IV)

        /** The order [asList] and the NBT int array are in. Reordering this rewrites everyone's floors. */
        const val FROM_NBT_ORDER = "hp, attack, defence, specialAttack, specialDefence, speed"

        private const val VALUES_KEY = "ivs"

        fun flat(value: Int): IvFloor = IvFloor(value, value, value, value, value, value)

        /** Six values in [FROM_NBT_ORDER], or null if that is not what was given. */
        fun of(values: List<Int>): IvFloor? {
            if (values.size != 6) return null
            return IvFloor(values[0], values[1], values[2], values[3], values[4], values[5])
        }

        /**
         * Read a floor back, or null if the tag does not hold one.
         *
         * Null rather than [BASE] on a malformed read, so the caller decides. Silently substituting
         * the base value here would turn a truncated file into "you never earned anything", which is
         * exactly the failure a player cannot detect and cannot argue with.
         */
        fun fromNbt(tag: CompoundTag): IvFloor? {
            if (!tag.contains(VALUES_KEY)) return null
            return of(tag.getIntArray(VALUES_KEY).toList())
        }
    }
}
