package com.cobblemonroguelite.progression

import net.minecraft.resources.ResourceLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * What candy buys, and — more importantly — what it must refuse to buy.
 *
 * Every refusal here is a case that would otherwise present as a button that does nothing: an
 * unaffordable purchase, a passive bought twice, a cost reduction past the last one, and a purchase
 * the operator has not priced. They are distinct outcomes because they need distinct things said.
 */
class CandySpendTest {

    private val prices = CandyPrices()
    private val species = ResourceLocation.fromNamespaceAndPath("cobblemon", "torchic")

    @Test
    fun `a passive costs the configured price and deducts it`() {
        val rich = SpeciesProgress(candy = 100)
        val result = rich.buy(CandyPurchase.HIDDEN_ABILITY, prices = prices)
        assertIs<SpendResult.Ok>(result)
        assertEquals(40, result.spent)
        assertEquals(60, result.progress.candy)
        assertTrue(result.progress.hiddenAbilityUnlocked)
    }

    @Test
    fun `a passive priced by starter cost overrides the flat price`() {
        // §2.13 keeps the cost table server-side, so the per-cost map is the seam that lets an
        // operator supply PokéRogue's curve without this module carrying their data.
        val byCost = CandyPrices(hiddenAbilityCandy = 40, hiddenAbilityCandyByCost = mapOf(6 to 80))
        val result = SpeciesProgress(candy = 100).buy(CandyPurchase.HIDDEN_ABILITY, starterCost = 6, prices = byCost)
        assertIs<SpendResult.Ok>(result)
        assertEquals(80, result.spent)
    }

    @Test
    fun `an unaffordable purchase reports the gap rather than failing silently`() {
        val result = SpeciesProgress(candy = 28).buy(CandyPurchase.HIDDEN_ABILITY, prices = prices)
        assertIs<SpendResult.NotEnoughCandy>(result)
        assertEquals(28, result.have)
        assertEquals(40, result.need)
    }

    @Test
    fun `a passive already owned is refused, not resold`() {
        val owner = SpeciesProgress(candy = 100, hiddenAbilityUnlocked = true)
        assertIs<SpendResult.AlreadyOwned>(owner.buy(CandyPurchase.HIDDEN_ABILITY, prices = prices))
    }

    @Test
    fun `cost reductions are priced in order and run out`() {
        var progress = SpeciesProgress(candy = 100)
        val first = progress.buy(CandyPurchase.COST_REDUCTION, prices = prices)
        assertIs<SpendResult.Ok>(first)
        assertEquals(20, first.spent)
        progress = first.progress

        val second = progress.buy(CandyPurchase.COST_REDUCTION, prices = prices)
        assertIs<SpendResult.Ok>(second)
        assertEquals(50, second.spent)
        progress = second.progress
        assertEquals(30, progress.candy)
        assertEquals(2, progress.costReductions)

        // The price list length *is* the cap, so the two cannot drift apart.
        assertIs<SpendResult.SoldOut>(progress.buy(CandyPurchase.COST_REDUCTION, prices = prices))
    }

    @Test
    fun `cost reductions lower the starter cost and stop at the minimum`() {
        val base = 4
        assertEquals(4, SpeciesProgress().effectiveStarterCost(base, prices))
        assertEquals(3, SpeciesProgress(costReductions = 1).effectiveStarterCost(base, prices))
        assertEquals(2, SpeciesProgress(costReductions = 2).effectiveStarterCost(base, prices))
        // A 1-cost species with reductions cannot become free — a zero cost would make §2.13's
        // 10-point budget infinite.
        assertEquals(1, SpeciesProgress(costReductions = 2).effectiveStarterCost(1, prices))
    }

    @Test
    fun `an unpriced egg is refused rather than given a price nobody chose`() {
        assertIs<SpendResult.NotPriced>(SpeciesProgress(candy = 999).buy(CandyPurchase.EGG, prices = prices))

        val priced = CandyPrices(eggCandy = 25)
        val result = SpeciesProgress(candy = 999).buy(CandyPurchase.EGG, prices = priced)
        assertIs<SpendResult.Ok>(result)
        assertEquals(974, result.progress.candy)
        // An egg leaves no mark on the species record; whatever grants the egg owns that fact.
        assertEquals(SpeciesProgress(candy = 974), result.progress)
    }

    @Test
    fun `a refused purchase leaves the stored record untouched`() {
        // Through the store-side path, because that is the one that writes: a refusal must not be
        // able to deduct, and must not create a row for a player who bought nothing.
        val player = PlayerProgression()
        assertIs<SpendResult.NotEnoughCandy>(player.buy(species, CandyPurchase.HIDDEN_ABILITY, prices = prices))
        assertTrue(player.isEmpty())
        assertEquals(SpeciesProgress.EMPTY, player.of(species))
    }

    @Test
    fun `a stored purchase deducts exactly once`() {
        val player = PlayerProgression()
        player.update(species) { it.copy(candy = 45) }
        assertIs<SpendResult.Ok>(player.buy(species, CandyPurchase.HIDDEN_ABILITY, prices = prices))
        assertEquals(5, player.of(species).candy)
        assertTrue(player.of(species).hiddenAbilityUnlocked)
        // The second attempt is refused and, critically, does not deduct again.
        assertIs<SpendResult.AlreadyOwned>(player.buy(species, CandyPurchase.HIDDEN_ABILITY, prices = prices))
        assertEquals(5, player.of(species).candy)
    }
}
