package com.cobblemonroguelite.run

import com.cobblemonroguelite.integration.RunChargeResult
import com.cobblemonroguelite.starter.StarterOffer
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
 * asking the price twice, an offer shown before the seed reached disk. None of that shows up as an
 * exception or a wrong number — it shows up weeks later as players who have learned the trick.
 */
class RunStartOrderTest {

    private val bulbasaur = ResourceLocation.fromNamespaceAndPath("cobblemon", "bulbasaur")

    /**
     * Records the calls rather than performing them. The point of the fake is the [calls] list: what
     * is being asserted is the sequence, so the sequence has to be the thing the test can see.
     */
    private class RecordingContext(
        private val gate: DepthGateResult = DepthGateResult.Allowed(null),
        private val quoteResult: RunChargeResult = RunChargeResult.Paid(),
        private val chargeResult: RunChargeResult = RunChargeResult.Paid(),
        private val offer: StarterOffer = StarterOffer(listOf(ResourceLocation.fromNamespaceAndPath("cobblemon", "bulbasaur"))),
        private val seed: Long = 4242L,
    ) : RunStartContext {

        val calls = mutableListOf<String>()
        var persistedSeed: Long? = null

        override fun depthGate(): DepthGateResult {
            calls += "gate"
            return gate
        }

        override fun charge(quoteOnly: Boolean): RunChargeResult {
            calls += if (quoteOnly) "quote" else "charge"
            return if (quoteOnly) quoteResult else chargeResult
        }

        override fun mintSeed(): Long {
            calls += "mint"
            return seed
        }

        override fun persistSeed(seed: Long) {
            calls += "persist"
            persistedSeed = seed
        }

        override fun starterOffer(seed: Long): StarterOffer {
            calls += "offer"
            // Asserted rather than ignored: an offer built from anything but the persisted seed is
            // the bug the persistence step exists to prevent, and it would still produce three
            // plausible species.
            assertEquals(persistedSeed, seed, "the offer must be built from the seed that was persisted")
            return offer
        }
    }

    @Test
    fun `begin runs gate, charge, mint, persist, offer in that order`() {
        val ctx = RecordingContext()
        val result = RunStart.begin(ctx)
        assertEquals(listOf("gate", "charge", "mint", "persist", "offer"), ctx.calls)
        assertIs<RunStartResult.OfferReady>(result)
        assertEquals(4242L, result.seed)
    }

    @Test
    fun `a locked gate is refused before anything is charged`() {
        val ctx = RecordingContext(gate = DepthGateResult.Denied(listOf(bulbasaur)))
        val result = RunStart.begin(ctx)
        assertEquals(listOf("gate"), ctx.calls)
        assertIs<RunStartResult.Refused>(result)
        assertIs<RunStartRefusal.DepthLocked>(result.refusal)
    }

    @Test
    fun `a refused charge mints no seed`() {
        val ctx = RecordingContext(chargeResult = RunChargeResult.Refused(Component.literal("broke")))
        val result = RunStart.begin(ctx)
        assertEquals(listOf("gate", "charge"), ctx.calls)
        assertEquals(null, ctx.persistedSeed)
        assertIs<RunStartResult.Refused>(result)
    }

    @Test
    fun `the seed is persisted before the offer is built`() {
        val ctx = RecordingContext()
        RunStart.begin(ctx)
        assertTrue(ctx.calls.indexOf("persist") < ctx.calls.indexOf("offer"))
        assertEquals(4242L, ctx.persistedSeed)
    }

    @Test
    fun `a quote charges nothing and mints nothing`() {
        val ctx = RecordingContext()
        val quote = RunStart.quote(ctx)
        // "charge" absent is the free-allowance guarantee; "mint" absent is the anti-reroll one.
        assertEquals(listOf("gate", "quote"), ctx.calls)
        assertEquals(null, ctx.persistedSeed)
        assertIs<RunStartQuote.Priced>(quote)
    }

    @Test
    fun `a quote refused by the gate never reaches the charge provider`() {
        val ctx = RecordingContext(gate = DepthGateResult.Denied(listOf(bulbasaur)))
        assertIs<RunStartQuote.Refused>(RunStart.quote(ctx))
        assertEquals(listOf("gate"), ctx.calls)
    }

    @Test
    fun `an empty starter pool refuses after the charge, keeping the seed`() {
        val ctx = RecordingContext(offer = StarterOffer(emptyList()))
        val result = RunStart.begin(ctx)
        assertIs<RunStartResult.Refused>(result)
        assertEquals(RunStartRefusal.NoStartersAvailable, result.refusal)
        // The player paid, so the seed stays on disk and the pending start survives to be resolved.
        assertEquals(4242L, ctx.persistedSeed)
    }
}
