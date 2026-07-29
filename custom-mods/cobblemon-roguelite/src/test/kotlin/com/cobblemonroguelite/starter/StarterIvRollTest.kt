package com.cobblemonroguelite.starter

import com.cobblemon.mod.common.api.pokemon.stats.Stats
import net.minecraft.resources.ResourceLocation
import java.util.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The one place randomness survived the move from an offer to a budget, and therefore the one place
 * §2.3's determinism still has to be enforced.
 *
 * A catalogue is not rolled, so a player can no longer reroll it by disconnecting — but their
 * starters' IVs are rolled, and §2.16 requires everything in a run to be derivable from the seed or
 * persisted. A run is persisted the instant it is created, so the exposure is narrow: a crash between
 * the charge and the creation. Narrow is not none, and "pull the plug on a bad roll" is exactly the
 * kind of thing that gets found.
 */
class StarterIvRollTest {

    private fun id(name: String) = ResourceLocation.fromNamespaceAndPath("cobblemon", name)

    private val bulbasaur = id("bulbasaur")
    private val charmander = id("charmander")

    // --- determinism ------------------------------------------------------------------------------

    @Test
    fun `the same seed, species and slot give the same IVs`() {
        repeat(20) { i ->
            val seed = 1_000L + i
            assertEquals(
                StarterIvRoll.roll(seed, bulbasaur, 0, StarterIvFloor.Base),
                StarterIvRoll.roll(seed, bulbasaur, 0, StarterIvFloor.Base),
            )
        }
    }

    @Test
    fun `different seeds give different IVs`() {
        val rolls = (0 until 50).map { StarterIvRoll.roll(it.toLong(), bulbasaur, 0, StarterIvFloor.Base) }.toSet()
        assertTrue(rolls.size > 1, "every seed produced the same IVs — the seed is not reaching the roll")
    }

    @Test
    fun `consecutive run seeds do not produce the same first roll`() {
        // Run seeds may well come from a counter or a clock. `Random(n)` and `Random(n+1)` open with
        // near-identical draws, which would show up as every run in a minute having the same HP IV;
        // the splitmix finaliser in starterSeed is what prevents that.
        val firsts = (0 until 16).map { StarterIvRoll.roll(it.toLong(), bulbasaur, 0, StarterIvFloor.Base)[Stats.HP] }
        assertTrue(firsts.toSet().size > 1, "consecutive seeds collapsed to one HP roll")
    }

    @Test
    fun `the IV roll does not share a stream with the raw run seed`() {
        // wave/ derives its own streams from the same run seed. Were the starter roll unsalted, the
        // starter's IVs would be a readable function of the wave-1 draw.
        val seed = 12345L
        assertNotEquals(
            Random(seed).nextLong(),
            Random(StarterIvRoll.starterSeed(seed, bulbasaur, 0)).nextLong(),
        )
    }

    @Test
    fun `two members of one team do not roll identical IVs`() {
        // Same seed, same run. Without species and slot in the mix both members would share a stream
        // and come out with the same six numbers, which reads as a bug and is one.
        val first = StarterIvRoll.roll(77L, bulbasaur, 0, StarterIvFloor.Base)
        val second = StarterIvRoll.roll(77L, charmander, 1, StarterIvFloor.Base)
        assertNotEquals(first, second)
    }

    @Test
    fun `the same species in a different slot rolls differently`() {
        assertNotEquals(
            StarterIvRoll.roll(77L, bulbasaur, 0, StarterIvFloor.Base),
            StarterIvRoll.roll(77L, bulbasaur, 1, StarterIvFloor.Base),
        )
    }

    // --- the floor ---------------------------------------------------------------------------------

    @Test
    fun `every stat rolls within the floor and the maximum`() {
        repeat(200) { i ->
            StarterIvRoll.roll(i.toLong(), bulbasaur, 0, StarterIvFloor.Base).forEach { (stat, value) ->
                assertTrue(
                    value in StarterIvFloor.BASE..StarterIvFloor.MAX_IV,
                    "$stat rolled $value, outside ${StarterIvFloor.BASE}..${StarterIvFloor.MAX_IV}",
                )
            }
        }
    }

    @Test
    fun `all six permanent stats are rolled`() {
        // A missing stat would leave whatever Cobblemon's own unseeded roll produced, which is both
        // undeterministic and unfloored — and invisible unless someone checks that exact stat.
        assertEquals(
            CobblemonBaseStatTotal.STAT_ORDER.toSet(),
            StarterIvRoll.roll(1L, bulbasaur, 0, StarterIvFloor.Base).keys,
        )
    }

    @Test
    fun `a per-stat floor is honoured per stat`() {
        // §2.17 sources the floor from the best IVs of that species caught in a run, which are six
        // separate numbers. A single collapsed figure would either inflate five stats or throw away
        // most of what the player earned.
        val floor = StarterIvFloor(mapOf(Stats.HP to 31, Stats.SPEED to 20))
        repeat(50) { i ->
            val rolled = StarterIvRoll.roll(i.toLong(), bulbasaur, 0, floor)
            assertEquals(31, rolled[Stats.HP])
            assertTrue(rolled[Stats.SPEED]!! >= 20)
            assertTrue(rolled[Stats.ATTACK]!! >= 0, "an unfloored stat must still roll, from 0")
        }
    }

    @Test
    fun `a maxed floor produces a maxed roll rather than an empty range`() {
        // The boundary the roll's arithmetic can get wrong: `nextInt(31 - 31 + 1)` is the only call
        // here that would throw if the +1 were dropped, and it would throw on the luckiest player.
        val rolled = StarterIvRoll.roll(9L, bulbasaur, 0, StarterIvFloor.flat(31))
        assertTrue(rolled.values.all { it == 31 })
    }

    @Test
    fun `the floor rolls within the range rather than taking the maximum of a free roll`() {
        // The difference between a floor and a bonus. `max(roll, floor)` would give a player whose
        // mark is 25 a 25 more than three times in four; rolling within 25..31 spreads them evenly,
        // and only the second is what "floor" means.
        val floor = StarterIvFloor.flat(25)
        val hp = (0 until 400).map { StarterIvRoll.roll(it.toLong(), bulbasaur, 0, floor)[Stats.HP]!! }
        assertTrue(hp.all { it >= 25 })
        assertTrue(hp.toSet().size >= 6, "the roll is not spread across the range above the floor: ${hp.toSet()}")
        assertTrue(hp.count { it == 25 } < hp.size / 2, "the floor value dominates — this is max(), not a range")
    }

    @Test
    fun `an out-of-range floor is clamped rather than trusted`() {
        // The floor comes from another component's store (§2.17). A 40 there would otherwise become
        // `nextInt(-8)` and take out run start.
        val rolled = StarterIvRoll.roll(3L, bulbasaur, 0, StarterIvFloor.flat(40))
        assertTrue(rolled.values.all { it == 31 })
    }
}
