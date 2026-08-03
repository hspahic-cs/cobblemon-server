package com.cobblemonroguelite.run

import com.cobblemonroguelite.integration.RunChargeResult
import com.cobblemonroguelite.starter.StarterCatalogue
import com.cobblemonroguelite.starter.StarterOption
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The order of run start (§2.16), which is the decision the sequence exists to encode.
 *
 * Every failure these cover is an ordering and every one of them is silent in play: a player charged
 * for a run the gate would have refused, a seed minted by a *quote* and therefore re-rollable by
 * asking the price twice, a fee taken for a server that has no starters to sell. None of that shows
 * up as an exception or a wrong number — it shows up weeks later as players who have learned the
 * trick.
 *
 * One position changed with §2.13's budget: the catalogue is no longer derived from the seed, so it
 * moved from last to **before the charge**. What that buys is asserted below, and it is not
 * cosmetic — under the superseded offer design the "this server has no starters" refusal could only
 * land on somebody who had already paid.
 */
class RunStartOrderTest {

    private val bulbasaur = ResourceLocation.fromNamespaceAndPath("cobblemon", "bulbasaur")

    private fun catalogue(budget: Int = 10, vararg options: StarterOption) =
        StarterCatalogue(budget, options.toList())

    private val defaultCatalogue = catalogue(10, StarterOption(bulbasaur, 3))

    /**
     * Records the calls rather than performing them. The point of the fake is the [calls] list: what
     * is being asserted is the sequence, so the sequence has to be the thing the test can see.
     */
    private class RecordingContext(
        private val gate: DepthGateResult = DepthGateResult.Allowed(null),
        private val arenaFree: Boolean = true,
        private val quoteResult: RunChargeResult = RunChargeResult.Paid(),
        private val chargeResult: RunChargeResult = RunChargeResult.Paid(),
        private val catalogue: StarterCatalogue,
        private val seed: Long = 4242L,
    ) : RunStartContext {

        val calls = mutableListOf<String>()
        var persistedSeed: Long? = null

        override fun depthGate(): DepthGateResult {
            calls += "gate"
            return gate
        }

        override fun arenaAvailable(): Boolean {
            calls += "arena"
            return arenaFree
        }

        override fun charge(quoteOnly: Boolean): RunChargeResult {
            calls += if (quoteOnly) "quote" else "charge"
            return if (quoteOnly) quoteResult else chargeResult
        }

        override fun starterCatalogue(): StarterCatalogue {
            calls += "catalogue"
            return catalogue
        }

        override fun mintSeed(): Long {
            calls += "mint"
            return seed
        }

        override fun persistSeed(seed: Long) {
            calls += "persist"
            persistedSeed = seed
        }
    }

    private fun context(
        gate: DepthGateResult = DepthGateResult.Allowed(null),
        arenaFree: Boolean = true,
        chargeResult: RunChargeResult = RunChargeResult.Paid(),
        catalogue: StarterCatalogue = defaultCatalogue,
    ) = RecordingContext(gate = gate, arenaFree = arenaFree, chargeResult = chargeResult, catalogue = catalogue)

    @Test
    fun `begin runs gate, arena, catalogue, charge, mint, persist in that order`() {
        val ctx = context()
        val result = RunStart.begin(ctx)
        assertEquals(listOf("gate", "arena", "catalogue", "charge", "mint", "persist"), ctx.calls)
        val ready = assertIs<RunStartResult.CatalogueReady>(result)
        assertEquals(4242L, ready.seed)
        assertEquals(defaultCatalogue, ready.catalogue)
    }

    @Test
    fun `a locked gate is refused before anything is charged`() {
        val ctx = context(gate = DepthGateResult.Denied(listOf(bulbasaur)))
        val result = RunStart.begin(ctx)
        assertEquals(listOf("gate"), ctx.calls)
        assertIs<RunStartRefusal.DepthLocked>(assertIs<RunStartResult.Refused>(result).refusal)
    }

    @Test
    fun `a refused charge mints no seed`() {
        val ctx = context(chargeResult = RunChargeResult.Refused(Component.literal("broke")))
        val result = RunStart.begin(ctx)
        assertEquals(listOf("gate", "arena", "catalogue", "charge"), ctx.calls)
        assertEquals(null, ctx.persistedSeed)
        assertIs<RunStartResult.Refused>(result)
    }

    @Test
    fun `the seed is persisted before begin returns`() {
        // §2.16, and the reason PendingStart exists: the seed decides the team's IVs, so it has to be
        // on disk before the player can act on the catalogue it will be spent against.
        val ctx = context()
        RunStart.begin(ctx)
        assertTrue(ctx.calls.indexOf("mint") < ctx.calls.indexOf("persist"))
        assertEquals(4242L, ctx.persistedSeed)
    }

    @Test
    fun `a quote charges nothing and mints nothing`() {
        val ctx = context()
        val quote = RunStart.quote(ctx)
        // "charge" absent is the free-allowance guarantee; "mint" absent is the anti-reroll one.
        assertEquals(listOf("gate", "arena", "catalogue", "quote"), ctx.calls)
        assertEquals(null, ctx.persistedSeed)
        assertIs<RunStartQuote.Priced>(quote)
    }

    @Test
    fun `a quote refused by the gate never reaches the charge provider`() {
        val ctx = context(gate = DepthGateResult.Denied(listOf(bulbasaur)))
        assertIs<RunStartQuote.Refused>(RunStart.quote(ctx))
        assertEquals(listOf("gate"), ctx.calls)
    }

    @Test
    fun `a full arena grid is refused before anything is charged`() {
        // The refusal the server causes rather than the player: it must land before the fee, because
        // there is no refund seam and "we were busy" is not something to bill someone for.
        val ctx = context(arenaFree = false)
        val result = RunStart.begin(ctx)
        assertEquals(listOf("gate", "arena"), ctx.calls)
        assertEquals(RunStartRefusal.NoArenaAvailable, assertIs<RunStartResult.Refused>(result).refusal)
    }

    @Test
    fun `a quote on a full grid names the arena, not the price`() {
        val quote = RunStart.quote(context(arenaFree = false))
        assertEquals(RunStartRefusal.NoArenaAvailable, assertIs<RunStartQuote.Refused>(quote).refusal)
    }

    @Test
    fun `an empty catalogue is refused before the fee, not after it`() {
        // The behaviour §2.13's budget bought. The superseded offer was drawn from the seed, so this
        // refusal could only be discovered after minting — which meant after charging. Nothing is
        // taken now, and the assertion that matters is the absence of "charge".
        val ctx = context(catalogue = catalogue(10))
        val result = RunStart.begin(ctx)
        assertEquals(listOf("gate", "arena", "catalogue"), ctx.calls)
        assertEquals(null, ctx.persistedSeed)
        assertEquals(RunStartRefusal.NoStartersAvailable, assertIs<RunStartResult.Refused>(result).refusal)
    }

    @Test
    fun `a quote on an empty catalogue says so instead of naming a price`() {
        // A player should be told the mode is unconfigured when they ask what it costs, not after
        // they have typed confirm.
        val quote = RunStart.quote(context(catalogue = catalogue(10)))
        assertEquals(RunStartRefusal.NoStartersAvailable, assertIs<RunStartQuote.Refused>(quote).refusal)
    }

    @Test
    fun `a budget below every price is its own refusal, naming both numbers`() {
        // Distinct from an empty catalogue because the fix is different: the pool is fine and someone
        // has set the budget under the cheapest thing in it. "No starters available" on a screen full
        // of starters is not a report an operator can act on.
        val ctx = context(catalogue = catalogue(2, StarterOption(bulbasaur, 3)))
        val result = RunStart.begin(ctx)
        assertEquals(listOf("gate", "arena", "catalogue"), ctx.calls)
        val refusal = assertIs<RunStartRefusal.NoAffordableStarters>(assertIs<RunStartResult.Refused>(result).refusal)
        assertEquals(3, refusal.cheapest)
        assertEquals(2, refusal.budget)
    }

    @Test
    fun `a catalogue with one affordable option among unaffordable ones still starts`() {
        // The budget is a ceiling on a team, not a filter on the catalogue: expensive species stay
        // listed so a player can see what their candy discounts are for.
        val ctx = context(
            catalogue = catalogue(5, StarterOption(bulbasaur, 3), StarterOption(ResourceLocation.fromNamespaceAndPath("cobblemon", "mewtwo"), 20)),
        )
        assertIs<RunStartResult.CatalogueReady>(RunStart.begin(ctx))
    }
}
