package com.cobblemonroguelite.data.reward

import net.minecraft.resources.ResourceLocation
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What the schema's numbers actually *mean*.
 *
 * Weights and a rarity curve are inert data until something draws from them, so these tests are what
 * pins the semantics an author is writing against: bands decide existence, curves decide likelihood,
 * and a table is allowed to have nothing to give at a particular depth.
 *
 * The draws use a fixed seed. That is not just test hygiene — run generation is seeded (§2.3), and a
 * draw that did not take its randomness as an argument would reroll rewards on resume, which is the
 * exploit the seed exists to close.
 */
class RewardTableRollTest {

    private val id = ResourceLocation.fromNamespaceAndPath("test", "rolls")

    private fun entry(name: String, tier: String, weight: Double = 1.0, min: Int = 1, max: Int? = null) =
        RewardEntry(name, tier, weight, min, max, RunReward.Levels(1))

    @Test
    fun `a curve holds flat outside its breakpoints and interpolates between them`() {
        val curve = WeightCurve(listOf(CurvePoint(10, 0.0), CurvePoint(20, 100.0)))
        // Held flat below the first point and above the last, so a run that goes deeper than the
        // author planned does not fall off a cliff into weight zero.
        assertEquals(0.0, curve.weightAt(1))
        assertEquals(0.0, curve.weightAt(10))
        assertEquals(50.0, curve.weightAt(15))
        assertEquals(100.0, curve.weightAt(20))
        assertEquals(100.0, curve.weightAt(9999))
    }

    @Test
    fun `wave bands decide whether an entry exists at all`() {
        val table = RewardTable(
            id,
            tiers = listOf(RewardTier("t", WeightCurve.flat())),
            entries = listOf(
                entry("early", "t", max = 5),
                entry("late", "t", min = 6),
            ),
        )
        val random = Random(1)
        assertTrue((1..50).map { table.roll(3, random)!!.id }.all { it == "early" })
        assertTrue((1..50).map { table.roll(9, random)!!.id }.all { it == "late" })
    }

    @Test
    fun `a tier at zero weight cannot be drawn, however heavy its entries are`() {
        val table = RewardTable(
            id,
            tiers = listOf(
                RewardTier("common", WeightCurve(listOf(CurvePoint(1, 1.0)))),
                RewardTier("rare", WeightCurve(listOf(CurvePoint(1, 0.0), CurvePoint(10, 5.0)))),
            ),
            entries = listOf(
                entry("plain", "common"),
                entry("shiny", "rare", weight = 1000.0),
            ),
        )
        val random = Random(7)
        // The rarity ramp is the tier curve, not the entry weight — this is the property that lets an
        // author retune depth-scaling without touching a hundred entries.
        assertTrue((1..200).map { table.roll(1, random)!!.id }.all { it == "plain" })
        assertTrue((1..200).map { table.roll(10, random)!!.id }.any { it == "shiny" })
    }

    @Test
    fun `a table with nothing eligible rolls null rather than something wrong`() {
        val table = RewardTable(
            id,
            tiers = listOf(RewardTier("t", WeightCurve.flat())),
            entries = listOf(entry("late", "t", min = 30)),
        )
        assertNull(table.roll(1, Random(1)))
    }

    @Test
    fun `an offer never repeats an entry and stops when the table runs dry`() {
        val table = RewardTable(
            id,
            tiers = listOf(RewardTier("t", WeightCurve.flat())),
            entries = listOf(entry("a", "t"), entry("b", "t"), entry("c", "t")),
        )
        val offer = table.rollOffer(wave = 1, count = 3, random = Random(3))
        assertEquals(3, offer.size)
        assertEquals(3, offer.map { it.id }.toSet().size)

        // Fewer than asked for is a legitimate answer, which is why callers get a list back.
        assertEquals(3, table.rollOffer(wave = 1, count = 10, random = Random(3)).size)
    }

    @Test
    fun `the same seed and wave draw the same reward`() {
        val table = RewardTable(
            id,
            tiers = listOf(RewardTier("t", WeightCurve.flat())),
            entries = listOf(entry("a", "t"), entry("b", "t"), entry("c", "t")),
        )
        // A resumed run must not be able to reroll its rewards by reconnecting.
        assertEquals(
            table.rollOffer(wave = 4, count = 2, random = Random(99)).map { it.id },
            table.rollOffer(wave = 4, count = 2, random = Random(99)).map { it.id },
        )
    }

    // ─── party-scaled weights (PokéRogue's healing-item weight functions, reshaped) ───

    private fun scaled(name: String, tier: String, condition: PartyCondition, weight: Double = 1.0) =
        RewardEntry(name, tier, weight, 1, null, RunReward.Levels(1), scaledBy = condition)

    private val whole = PartyState(missingHealth = 0.0, fainted = 0)
    private val battered = PartyState(missingHealth = 2.5, fainted = 2)

    @Test
    fun `a scaled entry does not exist for a party that does not need it`() {
        val table = RewardTable(
            id,
            tiers = listOf(RewardTier("t", WeightCurve.flat())),
            entries = listOf(
                scaled("potion", "t", PartyCondition.INJURED),
                scaled("revive", "t", PartyCondition.FAINTED),
                entry("candy", "t"),
            ),
        )
        // The PokéRogue property this whole mechanism exists for: a full-health party is never
        // offered healing, so its picks all land on the one thing it can use.
        val random = Random(7)
        assertTrue((1..60).map { table.roll(5, random, party = whole)!!.id }.all { it == "candy" })
        // A battered party sees all three.
        val drawn = (1..200).map { table.roll(5, random, party = battered)!!.id }.toSet()
        assertEquals(setOf("potion", "revive", "candy"), drawn)
    }

    @Test
    fun `a null party keeps every written weight`() {
        val table = RewardTable(
            id,
            tiers = listOf(RewardTier("t", WeightCurve.flat())),
            entries = listOf(scaled("revive", "t", PartyCondition.FAINTED), entry("candy", "t")),
        )
        // Null means "no scaling", not "empty party": every conditional entry stays at its written
        // weight, which is what a caller with no party in scope gets and must know it is getting.
        val random = Random(11)
        val drawn = (1..100).map { table.roll(5, random, party = null)!!.id }.toSet()
        assertEquals(setOf("revive", "candy"), drawn)
    }

    @Test
    fun `a tier whose entries all scale to zero loses the tier, not the option`() {
        val table = RewardTable(
            id,
            tiers = listOf(
                RewardTier("healing", WeightCurve(listOf(CurvePoint(1, 1000.0)))),
                RewardTier("rare", WeightCurve(listOf(CurvePoint(1, 1.0)))),
            ),
            entries = listOf(
                scaled("revive", "healing", PartyCondition.FAINTED),
                entry("candy", "rare"),
            ),
        )
        // The eligibility filter runs before the tier pick. If it ran after, the heavily-weighted
        // healing tier would win the tier roll, find nothing drawable inside, and the player's
        // three-option offer would silently arrive with two.
        repeat(50) { assertEquals("candy", table.roll(5, Random(it), party = whole)!!.id) }
        assertEquals(1, table.rollOffer(5, 3, Random(3), party = whole).size)
    }
}
