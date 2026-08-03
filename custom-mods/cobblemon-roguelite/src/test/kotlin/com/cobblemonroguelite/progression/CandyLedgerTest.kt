package com.cobblemonroguelite.progression

import net.minecraft.resources.ResourceLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The shop's decisions, which is everything between "the player typed something" and "the store was
 * asked".
 *
 * None of it is reachable from a plain JUnit run through the command layer — a `ServerPlayer` and a
 * booted `PokemonSpecies` registry cannot be constructed here — so the whole of it lives in
 * [CandyLedger] and is exercised directly. What that buys is the two properties the feature actually
 * depends on: that a price quoted is the price charged, and that the five refusals stay five.
 */
class CandyLedgerTest {

    private val prices = CandyPrices()
    private val charmander = ResourceLocation.fromNamespaceAndPath("cobblemon", "charmander")
    private val charizard = ResourceLocation.fromNamespaceAndPath("cobblemon", "charizard")
    private val bulbasaur = ResourceLocation.fromNamespaceAndPath("cobblemon", "bulbasaur")

    /**
     * Charmander's, and every view here supplies one. §2.27 withdraws the unlock from sale for a
     * species with no hidden ability, so a view built without this quotes a refusal — which is the
     * behaviour, not a fixture detail, and is asserted on its own below.
     */
    private val solarPower = "solarpower"

    private fun view(
        progress: SpeciesProgress,
        requested: ResourceLocation = charmander,
        starterCost: Int = 4,
        prices: CandyPrices = this.prices,
        eggsGrantable: Boolean = CandyLedger.EGGS_GRANTABLE,
        hiddenAbility: String? = solarPower,
    ) = CandyLedger.view(requested, charmander, progress, starterCost, prices, eggsGrantable, hiddenAbility)

    @Test
    fun `a species view quotes every purchase and keeps the five refusals apart`() {
        // One player, one species, three purchases, and the reason the view exists: each answer is a
        // different sentence. If any two of these collapse to the same result the display has to
        // guess, and a guess is how a purchase ends up looking like a button that does nothing.
        val quoted = view(SpeciesProgress(candy = 25, hiddenAbilityUnlocked = true, costReductions = 2))
        assertIs<SpendResult.AlreadyOwned>(quoted.offer(CandyPurchase.HIDDEN_ABILITY).quote)
        assertIs<SpendResult.SoldOut>(quoted.offer(CandyPurchase.COST_REDUCTION).quote)
        assertIs<SpendResult.NotPriced>(quoted.offer(CandyPurchase.EGG).quote)

        val poor = view(SpeciesProgress(candy = 5))
        val short = poor.offer(CandyPurchase.HIDDEN_ABILITY).quote
        assertIs<SpendResult.NotEnoughCandy>(short)
        assertEquals(5, short.have)
        assertEquals(40, short.need)

        // The fifth: a species with no hidden ability at all. Distinct from the four above because it
        // is the only one no amount of candy and no operator action can turn into a purchase.
        val noAbility = view(SpeciesProgress(candy = 500), hiddenAbility = null)
        assertIs<SpendResult.NoHiddenAbility>(noAbility.offer(CandyPurchase.HIDDEN_ABILITY).quote)
    }

    @Test
    fun `a species with no hidden ability is refused before the price is consulted`() {
        // The failure §2.27 exists to remove: a player pays, a starter is built with an ordinary
        // ability, and nothing ever says why. Refusing has to happen even when they can plainly
        // afford it — an affordability check reached first would sell it.
        val rich = view(SpeciesProgress(candy = 10_000), hiddenAbility = null)
        val offer = rich.offer(CandyPurchase.HIDDEN_ABILITY)
        assertIs<SpendResult.NoHiddenAbility>(offer.quote)
        assertNull(offer.price)
        assertTrue(!offer.affordable)

        // And confirming does not promote it: a refusal is a refusal at both steps.
        assertIs<CandyPurchasePlan.Refuse>(CandyLedger.plan(offer, confirmed = true))

        // The other purchases on that species are untouched — it has candy and it can still spend it.
        assertIs<SpendResult.Ok>(rich.offer(CandyPurchase.COST_REDUCTION).quote)
    }

    @Test
    fun `an unlock already owned is refused even for a species that lost its ability`() {
        // Withdrawal beats ownership when the two disagree. Neither answer costs the player anything
        // — no candy can move either way — so the one that stays true is the one that says the
        // species has nothing to unlock, rather than "you own it" for an ability that is not there.
        val owner = view(SpeciesProgress(candy = 100, hiddenAbilityUnlocked = true), hiddenAbility = null)
        assertIs<SpendResult.NoHiddenAbility>(owner.offer(CandyPurchase.HIDDEN_ABILITY).quote)
    }

    @Test
    fun `the view names the ability an unlock grants`() {
        // "40 candy" for an unnamed ability is not a decision a player can make, and hidden abilities
        // are the one purchase here whose worth swings between transformative and useless (§2.27).
        assertEquals(solarPower, view(SpeciesProgress(candy = 100)).hiddenAbility)
        assertNull(view(SpeciesProgress(candy = 100), hiddenAbility = null).hiddenAbility)
    }

    @Test
    fun `the price shown when affordable and when not is the same number`() {
        // The quote is a dry run of the real purchase, so an affordable unlock and an unaffordable
        // one are priced by the same code. A display that read the price off [CandyPrices] itself
        // could quote 40 while the store charged something else, and the player would learn that at
        // the moment their candy went.
        val rich = view(SpeciesProgress(candy = 500)).offer(CandyPurchase.HIDDEN_ABILITY)
        val poor = view(SpeciesProgress(candy = 1)).offer(CandyPurchase.HIDDEN_ABILITY)
        assertEquals(40, rich.price)
        assertEquals(40, poor.price)
        assertTrue(rich.affordable)
        assertTrue(!poor.affordable)
    }

    @Test
    fun `quoting does not spend`() {
        // The view dry-runs a purchase that would succeed. Nothing may be deducted by looking.
        val before = SpeciesProgress(candy = 100)
        val quoted = view(before)
        assertIs<SpendResult.Ok>(quoted.offer(CandyPurchase.HIDDEN_ABILITY).quote)
        assertEquals(before, quoted.progress)
        assertEquals(100, quoted.candy)
    }

    @Test
    fun `the cap on cost reductions is the length of the price list`() {
        // Two prices, so two reductions, so the counter the player reads and the SoldOut they hit
        // are the same number by construction — see [CandyPrices.costReductionCandy].
        val none = view(SpeciesProgress(candy = 500)).offer(CandyPurchase.COST_REDUCTION)
        assertEquals(0, none.owned)
        assertEquals(2, none.cap)
        assertEquals(20, none.price)

        val one = view(SpeciesProgress(candy = 500, costReductions = 1)).offer(CandyPurchase.COST_REDUCTION)
        assertEquals(50, one.price)

        val both = view(SpeciesProgress(candy = 500, costReductions = 2)).offer(CandyPurchase.COST_REDUCTION)
        assertIs<SpendResult.SoldOut>(both.quote)
        assertNull(both.price)
        assertEquals(2, both.owned)
        assertEquals(2, both.cap)

        // A longer price list is a higher cap, with nothing else to keep in step.
        val longer = CandyPrices(costReductionCandy = listOf(20, 50, 90))
        val third = view(SpeciesProgress(candy = 500, costReductions = 2), prices = longer)
            .offer(CandyPurchase.COST_REDUCTION)
        assertEquals(90, third.price)
        assertEquals(3, third.cap)
    }

    @Test
    fun `a cost reduction is quoted with the budget cost it would produce`() {
        val fresh = view(SpeciesProgress(candy = 500), starterCost = 4)
        assertEquals(4, fresh.effectiveStarterCost)
        assertEquals(3, fresh.nextStarterCost)

        val discounted = view(SpeciesProgress(candy = 500, costReductions = 1), starterCost = 4)
        assertEquals(3, discounted.effectiveStarterCost)
        assertEquals(2, discounted.nextStarterCost)
    }

    @Test
    fun `a reduction that the minimum would swallow is visible as such`() {
        // A 1-cost species with reductions still for sale: the purchase is legal and changes nothing,
        // because [CandyPrices.minimumStarterCost] clamps it. The display can only say so if the
        // clamped number is carried rather than subtracted at the last moment.
        val floored = view(SpeciesProgress(candy = 500), starterCost = 1)
        assertEquals(1, floored.effectiveStarterCost)
        assertEquals(1, floored.nextStarterCost)
        assertEquals(1, floored.floorStarterCost)
        assertIs<SpendResult.Ok>(floored.offer(CandyPurchase.COST_REDUCTION).quote)
    }

    @Test
    fun `an unknown starter cost falls back to the flat price rather than refusing`() {
        // §2.13 keeps the cost table server-side, so a species with no entry is normal. It must still
        // be spendable: refusing would make a hole in a datapack into "candy cannot be spent at all".
        val byCost = CandyPrices(hiddenAbilityCandy = 40, hiddenAbilityCandyByCost = mapOf(4 to 80))
        val known = view(SpeciesProgress(candy = 500), starterCost = 4, prices = byCost)
        val unknown = view(SpeciesProgress(candy = 500), starterCost = SpeciesProgress.UNKNOWN_STARTER_COST, prices = byCost)
        assertEquals(80, known.offer(CandyPurchase.HIDDEN_ABILITY).price)
        assertEquals(40, unknown.offer(CandyPurchase.HIDDEN_ABILITY).price)
        // And with no cost there is no budget number to show, rather than a made-up one.
        assertNull(unknown.effectiveStarterCost)
        assertNull(unknown.nextStarterCost)
    }

    @Test
    fun `eggs are refused as unpriced even when an operator prices them`() {
        // The refusal is not an accident of the default config. Nothing in this build grants an egg,
        // and [SpeciesProgress.buy] would happily take the candy and leave no trace — so a priced egg
        // on a build with no grantor destroys candy for nothing, which is worse than not selling it.
        val priced = CandyPrices(eggCandy = 25)
        val quoted = view(SpeciesProgress(candy = 500), prices = priced)
        assertIs<SpendResult.NotPriced>(quoted.offer(CandyPurchase.EGG).quote)
        // The purchase path takes the same refusal, so the view and the till agree.
        assertIs<SpendResult.NotPriced>(
            CandyLedger.quote(SpeciesProgress(candy = 500), CandyPurchase.EGG, prices = priced),
        )
        // And when there is a grantor, the flag is the only thing standing between here and a sale.
        assertIs<SpendResult.Ok>(
            CandyLedger.quote(SpeciesProgress(candy = 500), CandyPurchase.EGG, prices = priced, eggsGrantable = true),
        )
        // The shipped state, so that a change to the constant fails here rather than in play.
        assertTrue(!CandyLedger.EGGS_GRANTABLE)
    }

    @Test
    fun `an evolved species is shown its line root's ledger, flagged`() {
        // §2.17: a caught Charizard candies Charmander. The view carries both so the display can say
        // where the candy went — without the flag, a player who asked about Charizard sees a total
        // that looks like somebody else's and concludes theirs is missing.
        val redirected = view(SpeciesProgress(candy = 42), requested = charizard)
        assertTrue(redirected.redirected)
        assertEquals(charizard, redirected.requested)
        assertEquals(charmander, redirected.credited)
        assertEquals(42, redirected.candy)

        val direct = view(SpeciesProgress(candy = 42), requested = charmander)
        assertTrue(!direct.redirected)
    }

    @Test
    fun `the bare form warns and names the price, and only confirm commits`() {
        val offer = view(SpeciesProgress(candy = 100)).offer(CandyPurchase.HIDDEN_ABILITY)

        val warned = CandyLedger.plan(offer, confirmed = false)
        assertIs<CandyPurchasePlan.Confirm>(warned)
        assertEquals(40, warned.price)

        val committed = CandyLedger.plan(offer, confirmed = true)
        assertIs<CandyPurchasePlan.Commit>(committed)
        assertEquals(40, committed.quotedPrice)
    }

    @Test
    fun `a refusal is delivered on the bare command rather than after a confirmation`() {
        // Asking a player to confirm a purchase that is going to be refused teaches them that
        // `confirm` is a formality, which is the exact habit the confirmation exists to prevent.
        val broke = view(SpeciesProgress(candy = 1)).offer(CandyPurchase.HIDDEN_ABILITY)
        val refused = CandyLedger.plan(broke, confirmed = false)
        assertIs<CandyPurchasePlan.Refuse>(refused)
        assertIs<SpendResult.NotEnoughCandy>(refused.refusal)

        // And confirming it does not promote it to a purchase.
        assertIs<CandyPurchasePlan.Refuse>(CandyLedger.plan(broke, confirmed = true))
    }

    @Test
    fun `every refusal reaches the plan as itself`() {
        // The plan must not flatten the five into "no". Each of these is a different instruction to
        // the player: earn more, you have it, there are none, this server does not sell it, this
        // species has none.
        val owned = view(SpeciesProgress(candy = 100, hiddenAbilityUnlocked = true)).offer(CandyPurchase.HIDDEN_ABILITY)
        val exhausted = view(SpeciesProgress(candy = 100, costReductions = 2)).offer(CandyPurchase.COST_REDUCTION)
        val egg = view(SpeciesProgress(candy = 100)).offer(CandyPurchase.EGG)
        val poor = view(SpeciesProgress(candy = 0)).offer(CandyPurchase.HIDDEN_ABILITY)
        val abilityless = view(SpeciesProgress(candy = 100), hiddenAbility = null).offer(CandyPurchase.HIDDEN_ABILITY)

        val refusals = listOf(owned, exhausted, egg, poor, abilityless)
            .map { CandyLedger.plan(it, confirmed = true) }
            .map { assertIs<CandyPurchasePlan.Refuse>(it).refusal }

        assertIs<SpendResult.AlreadyOwned>(refusals[0])
        assertIs<SpendResult.SoldOut>(refusals[1])
        assertIs<SpendResult.NotPriced>(refusals[2])
        assertIs<SpendResult.NotEnoughCandy>(refusals[3])
        assertIs<SpendResult.NoHiddenAbility>(refusals[4])
        assertEquals(refusals.size, refusals.distinct().size)
    }

    @Test
    fun `the ledger lists what candy can act on and orders it by balance`() {
        val floorOnly = SpeciesProgress(floor = IvFloor(31, 10, 10, 10, 10, 10))
        val rows = CandyLedger.summary(
            mapOf(
                bulbasaur to SpeciesProgress(candy = 7),
                charmander to SpeciesProgress(candy = 42, hiddenAbilityUnlocked = true),
                // Earned an IV floor and nothing else. It has a record, but there is nothing to buy
                // with it, and listing it as "0 candy" pads the list with rows the command cannot act
                // on.
                charizard to floorOnly,
            ),
        )
        assertEquals(listOf(charmander, bulbasaur), rows.map { it.species })
        assertEquals(42, rows.first().progress.candy)
    }

    @Test
    fun `a spent-out species stays in the ledger while it still owns something`() {
        // Zero candy but an unlock bought: dropping it would make the one purchase a player has to
        // show for their runs invisible the moment they finished paying for it.
        val rows = CandyLedger.summary(mapOf(charmander to SpeciesProgress(candy = 0, hiddenAbilityUnlocked = true)))
        assertEquals(listOf(charmander), rows.map { it.species })
    }

    @Test
    fun `a bare species name is retried under cobblemon and a namespaced one is not`() {
        val real = setOf(charmander, ResourceLocation.fromNamespaceAndPath("someaddon", "charmander"))
        val exists: (ResourceLocation) -> Boolean = { it in real }

        // What Brigadier hands us for a typed `charmander`. Refusing it would report an unknown
        // Pokémon on a server that plainly has one.
        val bare = ResourceLocation.fromNamespaceAndPath("minecraft", "charmander")
        assertEquals(charmander, CandySpeciesArgument.resolve(bare, exists))

        // An id the player namespaced is taken at their word: two namespaces are two Pokémon with two
        // ledgers, and substituting one would spend the wrong species' candy.
        val addon = ResourceLocation.fromNamespaceAndPath("someaddon", "charmander")
        assertEquals(addon, CandySpeciesArgument.resolve(addon, exists))
        assertNotEquals(charmander, CandySpeciesArgument.resolve(addon, exists))

        // No such species, either way.
        assertNull(CandySpeciesArgument.resolve(ResourceLocation.fromNamespaceAndPath("minecraft", "missingno"), exists))
        assertNull(CandySpeciesArgument.resolve(ResourceLocation.fromNamespaceAndPath("someaddon", "missingno"), exists))
    }
}
