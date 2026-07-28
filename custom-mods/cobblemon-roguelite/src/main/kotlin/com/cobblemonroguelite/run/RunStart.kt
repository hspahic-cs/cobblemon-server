package com.cobblemonroguelite.run

import com.cobblemonroguelite.integration.RunChargeResult
import com.cobblemonroguelite.starter.StarterOffer
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation

/**
 * The five things starting a run does, in the order it must do them.
 *
 * ### Why the steps are an interface instead of direct calls
 *
 * The order is the decision (§2.16), not the implementations. Every step here needs a booted server
 * — advancements, an economy provider, a save file, a Pokédex — so a start sequence written as
 * direct calls could only ever be verified by playing it, and the failures it is guarding against
 * (a player shown an offer before being charged; a seed re-rolled on reconnect; a free allowance
 * consumed on a quote) are all *orderings*, which is exactly the kind of thing a test can pin and a
 * play-through can miss. Behind this interface the order is checkable in milliseconds;
 * [ServerRunStartContext] then has nothing left in it but the five calls themselves.
 *
 * Implementations are called on the server thread, once per attempt, in the order [RunStart.begin]
 * calls them.
 */
interface RunStartContext {

    /** §2.18's badge gate. Read live rather than cached; see [RunDepthGate]. */
    fun depthGate(): DepthGateResult

    /**
     * Whether there is an arena free — §4's concurrency bound, through
     * [com.cobblemonroguelite.arena.RunArenas.hasCapacity].
     *
     * A gate and not a later check, because the alternative is discovering the grid is full *after*
     * the fee has been taken, and there is deliberately no refund seam (§2.16). It is also the only
     * refusal here that is temporary and not the player's fault, which is why the wording it gets is
     * "try again shortly" rather than "you cannot".
     */
    fun arenaAvailable(): Boolean

    /**
     * §2.18's entry fee, through [com.cobblemonroguelite.integration.RunCharges].
     *
     * @param quoteOnly true to price the run without taking anything. **The non-quote call is where
     *   a free allowance is consumed** (§2.16) — at the door, not on completion, or abandoning and
     *   restarting is an unlimited free reroll. That consumption happens inside the provider; what
     *   this module owes is to make the real call at *start*, which is this step being here at all.
     */
    fun charge(quoteOnly: Boolean): RunChargeResult

    /**
     * A fresh seed. §2.16: every start mints a new one, including a start straight after an abandon
     * — a player who dislikes their draft is meant to be able to walk away and get a different run,
     * and the fee is what prices that.
     */
    fun mintSeed(): Long

    /**
     * Write the seed down before anything derived from it is shown. See [PendingStart]: the offer
     * screen is precisely where a player might pull the plug, and an unpersisted seed makes that a
     * reroll of a run they have already paid for.
     */
    fun persistSeed(seed: Long)

    /** The starter offer for this seed (§2.13). Derived, so it comes last. */
    fun starterOffer(seed: Long): StarterOffer
}

/** Why a run did not start. Structured rather than pre-rendered so the command layer owns wording. */
sealed interface RunStartRefusal {

    /**
     * §2.18's gate is closed. [requires] is every advancement that would open it — any one is enough.
     */
    data class DepthLocked(val requires: List<ResourceLocation>) : RunStartRefusal

    /**
     * Every arena is in use. The only refusal here that resolves on its own, and the only one that is
     * about the server rather than about the player.
     */
    data object NoArenaAvailable : RunStartRefusal

    /** The charge provider said no. [reason] is the provider's own message and is shown verbatim. */
    data class Charge(val reason: Component) : RunStartRefusal

    /**
     * The offer came back empty, i.e. the server has no starter pool. A configuration fault rather
     * than a gameplay outcome, and one that lands **after the player has been charged** — which is
     * why [StarterOffer] and its pool source both treat an empty baseline as an error rather than
     * quietly narrowing the offer.
     */
    data object NoStartersAvailable : RunStartRefusal
}

/** What a quote came to. Nothing has been taken either way. */
sealed interface RunStartQuote {

    /** The run may start. [detail] is the provider's price line, or null when there is nothing to say. */
    data class Priced(val detail: Component?) : RunStartQuote

    data class Refused(val refusal: RunStartRefusal) : RunStartQuote
}

/** What a start attempt came to. */
sealed interface RunStartResult {

    /**
     * The fee is taken, the seed is on disk, and the player has an offer to pick from. No
     * [RunState] exists yet — it is built from the chosen starter, at level 1 (§2.21).
     */
    data class OfferReady(
        val seed: Long,
        val offer: StarterOffer,
        val charged: Component?,
        val maxWave: Int?,
    ) : RunStartResult

    data class Refused(val refusal: RunStartRefusal) : RunStartResult
}

/**
 * Run start, as an order.
 *
 * ### The order, and what each position is protecting
 *
 * 1. **Gate before fee.** A player who cannot start a run must not be charged for finding out. This
 *    is also the cheapest check, which is a coincidence and not the reason.
 * 2. **Arena before fee**, for the same reason and a sharper one: a full grid is a refusal the
 *    *server* caused, and charging for it would be charging a player for our concurrency limit.
 * 3. **Fee before seed.** [com.cobblemonroguelite.integration.RunChargeProvider] states this as its
 *    own contract: a player must never see a starter offer they are then refused for.
 * 4. **Seed persisted before the offer is built.** §2.16, and the reason [PendingStart] exists.
 * 5. **Offer last**, because it is derived from the seed and nothing else derives from it.
 *
 * The quote path ([quote]) runs steps 1 to 3 with `quoteOnly`, and stops. It exists so a confirm
 * prompt can name the price — a prompt that cannot is a prompt the player cannot decide from — and
 * it must not touch steps 3 to 5, since a quote that mints a seed would let a player re-quote until
 * they liked the draft.
 */
object RunStart {

    /** Price a run without starting it. Takes nothing and consumes no allowance. */
    fun quote(ctx: RunStartContext): RunStartQuote {
        when (val gate = ctx.depthGate()) {
            is DepthGateResult.Denied -> return RunStartQuote.Refused(RunStartRefusal.DepthLocked(gate.requires))
            is DepthGateResult.Allowed -> Unit
        }
        if (!ctx.arenaAvailable()) return RunStartQuote.Refused(RunStartRefusal.NoArenaAvailable)
        return when (val charge = ctx.charge(quoteOnly = true)) {
            is RunChargeResult.Refused -> RunStartQuote.Refused(RunStartRefusal.Charge(charge.reason))
            is RunChargeResult.Paid -> RunStartQuote.Priced(charge.detail)
        }
    }

    /**
     * Start a run: gate, charge, mint, persist, offer.
     *
     * The gate is re-evaluated here rather than carried over from [quote]. The two calls are separate
     * player actions with a confirmation between them, and although a badge cannot be *lost*, the
     * configured gate can change in between — and re-reading costs one map lookup against a state
     * that is otherwise assumed rather than known.
     */
    fun begin(ctx: RunStartContext): RunStartResult {
        val maxWave = when (val gate = ctx.depthGate()) {
            is DepthGateResult.Denied -> return RunStartResult.Refused(RunStartRefusal.DepthLocked(gate.requires))
            is DepthGateResult.Allowed -> gate.maxWave
        }

        // Re-asked rather than carried from the quote, and for a stronger reason than the gate is:
        // between the price prompt and the confirm, another player may have taken the last slot.
        if (!ctx.arenaAvailable()) return RunStartResult.Refused(RunStartRefusal.NoArenaAvailable)

        val charge = ctx.charge(quoteOnly = false)
        if (charge is RunChargeResult.Refused) {
            return RunStartResult.Refused(RunStartRefusal.Charge(charge.reason))
        }
        val detail = (charge as RunChargeResult.Paid).detail

        val seed = ctx.mintSeed()
        ctx.persistSeed(seed)

        val offer = ctx.starterOffer(seed)
        // Deliberately *after* the charge, and deliberately not rolled back. The fee is taken and the
        // seed is on disk, so the player still owns a paid, seeded run — the pending start survives
        // and the same offer resolves as soon as an op fixes the pool. Refunding here would need the
        // refund seam §2.16 refused to add, and voiding the pending start would throw away the one
        // record that they paid.
        if (offer.isEmpty) return RunStartResult.Refused(RunStartRefusal.NoStartersAvailable)

        return RunStartResult.OfferReady(seed = seed, offer = offer, charged = detail, maxWave = maxWave)
    }
}
