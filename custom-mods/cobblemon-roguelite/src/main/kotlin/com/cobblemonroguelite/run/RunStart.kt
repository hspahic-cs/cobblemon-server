package com.cobblemonroguelite.run

import com.cobblemonroguelite.integration.RunChargeResult
import com.cobblemonroguelite.starter.StarterCatalogue
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation

/**
 * The six things starting a run does, in the order it must do them.
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
     * What this player may buy their starting team from (§2.13).
     *
     * **Takes no seed, and is asked before the fee.** Under the superseded random-offer design the
     * offer was derived from the run seed, so it could only be built after the seed existed — which
     * put the "this server has no starters" refusal *after* the charge, with a comment explaining why
     * that was tolerable. A budget catalogue is a pure function of the player's unlocks and the price
     * table, so the question can be asked while nothing has been taken, and the whole hazard goes
     * away rather than being managed.
     *
     * Still a step on this interface rather than a call inside [RunStart], because it is the one
     * refusal here that reads the datapack and it needs a booted server to answer.
     */
    fun starterCatalogue(): StarterCatalogue

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
     * The catalogue came back empty: the server has no starter pool, or everything in it is
     * unpriced. A configuration fault rather than a gameplay outcome, and — unlike under the
     * superseded offer design — one that now lands **before** the player is charged.
     */
    data object NoStartersAvailable : RunStartRefusal

    /**
     * The catalogue has species in it and none of them fits the budget.
     *
     * Separate from [NoStartersAvailable] because it is a different mistake with a different fix: the
     * pool is fine and the prices are fine relative to each other, and somebody has set a budget below
     * the cheapest thing anyone can buy. Both numbers are carried so the message can name them, since
     * "no starters available" on a screen full of starters is not a report anyone can act on.
     */
    data class NoAffordableStarters(val cheapest: Int, val budget: Int) : RunStartRefusal
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
     * The fee is taken, the seed is on disk, and the player has a catalogue to spend their budget on.
     * No [RunState] exists yet — it is built from the team they buy, at level 1 (§2.21).
     */
    data class CatalogueReady(
        val seed: Long,
        val catalogue: StarterCatalogue,
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
 * 3. **Catalogue before fee**, which is new and is the one order this rework changed. §2.13's budget
 *    catalogue is not derived from the seed, so "this server has no startable species" can be asked
 *    while nothing has been taken. Under the superseded offer design it could not be, and the
 *    refusal landed on a player who had already paid.
 * 4. **Fee before seed.** [com.cobblemonroguelite.integration.RunChargeProvider] states this as its
 *    own contract: a player must never see a starter screen they are then refused for.
 * 5. **Seed persisted before the run can be built from it.** §2.16, and the reason [PendingStart]
 *    exists.
 *
 * The quote path ([quote]) runs steps 1 to 3 plus a `quoteOnly` charge, and stops. It exists so a
 * confirm prompt can name the price — a prompt that cannot is a prompt the player cannot decide from
 * — and it must not mint or persist a seed, since a quote that minted one would let a player re-quote
 * until they liked the run.
 *
 * The catalogue is in the quote for the player's sake rather than the server's: being told the server
 * has no starters *before* typing `confirm` is strictly better than being told after, and the check
 * is a map lookup either way.
 */
object RunStart {

    /** Price a run without starting it. Takes nothing and consumes no allowance. */
    fun quote(ctx: RunStartContext): RunStartQuote {
        when (val gate = ctx.depthGate()) {
            is DepthGateResult.Denied -> return RunStartQuote.Refused(RunStartRefusal.DepthLocked(gate.requires))
            is DepthGateResult.Allowed -> Unit
        }
        if (!ctx.arenaAvailable()) return RunStartQuote.Refused(RunStartRefusal.NoArenaAvailable)
        catalogueRefusal(ctx)?.let { return RunStartQuote.Refused(it) }
        return when (val charge = ctx.charge(quoteOnly = true)) {
            is RunChargeResult.Refused -> RunStartQuote.Refused(RunStartRefusal.Charge(charge.reason))
            is RunChargeResult.Paid -> RunStartQuote.Priced(charge.detail)
        }
    }

    /**
     * Start a run: gate, arena, catalogue, charge, mint, persist.
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

        // Re-asked here as well, for the same reason as the gate and the arena: a `/reload` between
        // the price prompt and the confirm can empty the price table, and the catalogue is cheap.
        val catalogue = ctx.starterCatalogue()
        catalogueRefusal(catalogue)?.let { return RunStartResult.Refused(it) }

        val charge = ctx.charge(quoteOnly = false)
        if (charge is RunChargeResult.Refused) {
            return RunStartResult.Refused(RunStartRefusal.Charge(charge.reason))
        }
        val detail = (charge as RunChargeResult.Paid).detail

        val seed = ctx.mintSeed()
        ctx.persistSeed(seed)

        return RunStartResult.CatalogueReady(seed = seed, catalogue = catalogue, charged = detail, maxWave = maxWave)
    }

    private fun catalogueRefusal(ctx: RunStartContext): RunStartRefusal? = catalogueRefusal(ctx.starterCatalogue())

    /**
     * Whether this catalogue can start a run at all.
     *
     * Two failures, deliberately distinguished — see [RunStartRefusal.NoAffordableStarters]. Neither
     * is a gameplay outcome; both are the server misconfigured, and both are worth refusing rather
     * than showing a screen that cannot be acted on.
     */
    private fun catalogueRefusal(catalogue: StarterCatalogue): RunStartRefusal? = when {
        catalogue.isEmpty -> RunStartRefusal.NoStartersAvailable
        catalogue.affordable().isEmpty() ->
            RunStartRefusal.NoAffordableStarters(catalogue.cheapest ?: 0, catalogue.budget)
        else -> null
    }
}
