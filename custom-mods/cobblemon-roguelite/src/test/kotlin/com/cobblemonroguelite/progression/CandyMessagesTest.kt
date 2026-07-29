package com.cobblemonroguelite.progression

import net.minecraft.resources.ResourceLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The candy strings that are mechanism rather than decoration.
 *
 * Three of them carry a rule the player can learn nowhere else: that the four refusals are four
 * different situations, that an evolved species' candy lives under its first form (§2.17), and that
 * eggs are a thing this server does not sell rather than a thing that went wrong. Each of those is a
 * sentence away from being the bug report "the button does nothing".
 */
class CandyMessagesTest {

    private val charmander = ResourceLocation.fromNamespaceAndPath("cobblemon", "charmander")
    private val charizard = ResourceLocation.fromNamespaceAndPath("cobblemon", "charizard")

    private fun view(
        progress: SpeciesProgress,
        requested: ResourceLocation = charmander,
        starterCost: Int = 4,
        prices: CandyPrices = CandyPrices(),
    ) = CandyLedger.view(requested, charmander, progress, starterCost, prices)

    @Test
    fun `the four refusals are four different sentences`() {
        val ledger = view(SpeciesProgress(candy = 3))
        val texts = listOf(
            SpendResult.NotEnoughCandy(have = 3, need = 40) to CandyPurchase.PASSIVE,
            SpendResult.AlreadyOwned to CandyPurchase.PASSIVE,
            SpendResult.SoldOut to CandyPurchase.COST_REDUCTION,
            SpendResult.NotPriced to CandyPurchase.EGG,
        ).map { (result, purchase) -> CandyMessages.refusal(ledger, purchase, result).string }

        // The point of [SpendResult] having four cases. Two identical strings here would mean the
        // split upstream bought nothing and the player is being told "no" without being told what to
        // do about it.
        assertEquals(texts.size, texts.toSet().size, texts.toString())
        assertTrue("37 short" in texts[0], texts[0])
        assertTrue("already own" in texts[1], texts[1])
        assertTrue("no more to buy" in texts[2], texts[2])
        assertTrue("not available on this server" in texts[3], texts[3])
    }

    @Test
    fun `a refusal never implies candy was taken`() {
        // A refusal that reads as a completed purchase is the one wording that would have a player
        // stop earning toward something they still do not own.
        val ledger = view(SpeciesProgress(candy = 3))
        for (refusal in listOf(SpendResult.AlreadyOwned, SpendResult.SoldOut)) {
            val text = CandyMessages.refusal(ledger, CandyPurchase.COST_REDUCTION, refusal).string
            assertTrue("nothing was taken" in text, text)
        }
    }

    @Test
    fun `an evolved species is told where its candy actually is`() {
        // §2.17's line-root rule is invisible in the numbers, so it has to be in the words: a player
        // who caught Charizards and sees "Charmander: 42" without this sentence reads it as a loss.
        val text = CandyMessages.view(view(SpeciesProgress(candy = 42), requested = charizard))
            .first().string
        assertTrue("cobblemon:charizard" in text, text)
        assertTrue("cobblemon:charmander" in text, text)
        assertTrue("first form" in text, text)

        // And the first-form case does not say it, because there is nothing to explain.
        val direct = CandyMessages.view(view(SpeciesProgress(candy = 42))).first().string
        assertTrue("first form" !in direct, direct)
    }

    @Test
    fun `the confirmation names the price, the balance and the command that acts`() {
        val ledger = view(SpeciesProgress(candy = 100), requested = charizard)
        val plan = CandyLedger.plan(ledger.offer(CandyPurchase.PASSIVE), confirmed = false)
        val text = CandyMessages.confirm(ledger, plan as CandyPurchasePlan.Confirm).string

        assertTrue("40 candy" in text, text)
        assertTrue("100" in text, text)
        // Echoed back as the player typed it, not as the credited species: the command they are being
        // told to type has to be one that parses against what is on their screen.
        assertTrue("/roguelite candy cobblemon:charizard buy passive confirm" in text, text)
    }

    @Test
    fun `a purchase that failed outright does not promise the candy is safe`() {
        // Whether the deduction landed before the throw is unknown, and "nothing was taken" is the
        // reassurance a player acts on by buying again.
        val text = CandyMessages.purchaseFailed(view(SpeciesProgress(candy = 10))).string
        assertTrue("nothing was taken" !in text, text)
        assertTrue("may or may not" in text, text)
    }

    @Test
    fun `every purchase word is a literal the command tree can carry`() {
        // These strings are printed inside commands the player is told to type, and they are the same
        // values [CandyCommands] builds its literals from. A space or a slash here would produce a
        // node nobody can reach.
        for (purchase in CandyPurchase.entries) {
            val word = CandyMessages.word(purchase)
            assertTrue(word.isNotBlank() && word.none { it.isWhitespace() }, word)
            assertEquals(word.lowercase(), word)
        }
        assertEquals(
            CandyPurchase.entries.size,
            CandyPurchase.entries.map(CandyMessages::word).toSet().size,
        )
    }

    @Test
    fun `a passive is not sold as an ability that already works`() {
        // Nothing in a run reads [SpeciesProgress.passiveUnlocked] yet. The purchase is still worth
        // making — the unlock is permanent — but a player who was told it "applies to your next run"
        // would go looking for a difference that is not there, which is the same failure as an
        // unexplained refusal approached from the other side.
        val priced = CandyMessages.view(view(SpeciesProgress(candy = 100)))[1].string
        val sold = CandyMessages.bought(charmander, CandyPurchase.PASSIVE, spent = 40, remaining = 60).string
        for (text in listOf(priced, sold)) {
            assertEquals(!CandyLedger.PASSIVES_ACTIVE, "do not affect runs yet" in text, text)
        }
        // Cost reductions do work, and must not inherit the caveat.
        val reduction = CandyMessages.bought(charmander, CandyPurchase.COST_REDUCTION, spent = 20, remaining = 80).string
        assertTrue("do not affect runs yet" !in reduction, reduction)
    }

    @Test
    fun `an unknown species is reported as unknown rather than shown as zero`() {
        val text = CandyMessages.unknownSpecies(ResourceLocation.fromNamespaceAndPath("cobblemon", "charmandr")).string
        assertTrue("cobblemon:charmandr" in text, text)
        assertTrue("No Pokémon" in text, text)
    }
}
