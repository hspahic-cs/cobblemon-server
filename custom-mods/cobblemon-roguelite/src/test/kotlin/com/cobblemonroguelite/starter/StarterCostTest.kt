package com.cobblemonroguelite.starter

import net.minecraft.resources.ResourceLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The price a species gets when nobody has priced it, and how the layers stack.
 *
 * The bands themselves are a stand-in and are not asserted number by number — pinning them would
 * make replacing them a test edit, which is exactly the friction a documented placeholder should not
 * have. What *is* pinned is the shape: monotone, bounded, total, and never zero. Those are the
 * properties the budget depends on, and all four survive whatever the numbers become.
 */
class StarterCostTest {

    private fun id(name: String) = ResourceLocation.fromNamespaceAndPath("cobblemon", name)

    // --- the derived default -----------------------------------------------------------------------

    @Test
    fun `every base stat total gets a price, including absurd ones`() {
        // Total, with no hole to fall through. A base stat total this code cannot answer for would
        // become an unpriced species, and an unpriced species is a run somebody cannot start.
        listOf(0, 1, 175, 318, 400, 530, 600, 720, 1_000, Int.MAX_VALUE).forEach {
            val cost = DerivedStarterCost.fromBaseStatTotal(it)
            assertTrue(cost in DerivedStarterCost.MIN..DerivedStarterCost.MAX, "BST $it priced $cost, out of range")
        }
    }

    @Test
    fun `a stronger species never costs less than a weaker one`() {
        // Monotone. Not a balance claim — it is the minimum a base-stat proxy has to satisfy to be
        // worth anything at all, and a mis-ordered band table would otherwise load silently.
        var previous = 0
        (0..800 step 5).forEach {
            val cost = DerivedStarterCost.fromBaseStatTotal(it)
            assertTrue(cost >= previous, "BST $it priced $cost after $previous — the bands are out of order")
            previous = cost
        }
    }

    @Test
    fun `the cheapest derived price still costs a real share of the budget`() {
        // §2.13 puts costs in the 3-6 range and a 10-point budget at two or three Pokémon. A floor of
        // 1 would turn the shipped default into "buy a full party", which is a different mode.
        assertTrue(DerivedStarterCost.MIN >= 3, "a derived price of ${DerivedStarterCost.MIN} buys too many starters")
        assertTrue(10 / DerivedStarterCost.MIN <= 3, "the budget buys more than three of the cheapest species")
    }

    @Test
    fun `a species with no base stats is unpriced rather than free`() {
        assertNull(DerivedStarterCostSource { null }.costOf(id("nothing")))
    }

    // --- layering ----------------------------------------------------------------------------------

    @Test
    fun `the first layer with an opinion wins`() {
        // §2.7's licensing split as code: the server's transcribed table sits in front of the derived
        // default, and neither build branches on which one it is.
        val table = FixedStarterCostSource(mapOf(id("torchic") to 4))
        val layered = LayeredStarterCostSource(table, DerivedStarterCostSource { 800 })
        assertEquals(4, layered.costOf(id("torchic")))
    }

    @Test
    fun `a species the table does not mention falls through to the derived default`() {
        val table = FixedStarterCostSource(mapOf(id("torchic") to 4))
        val layered = LayeredStarterCostSource(table, DerivedStarterCostSource { 318 })
        assertEquals(DerivedStarterCost.fromBaseStatTotal(318), layered.costOf(id("bulbasaur")))
    }

    @Test
    fun `a species no layer prices stays unpriced`() {
        // The whole point of the null: a published build with no table and an unknown species must
        // report a gap, not hand out a free starter.
        val layered = LayeredStarterCostSource(FixedStarterCostSource(emptyMap()), DerivedStarterCostSource { null })
        assertNull(layered.costOf(id("ghost")))
    }
}
