package com.cobblemonroguelite.payout

import com.cobblemonroguelite.data.payout.PayoutGrant
import net.minecraft.resources.ResourceLocation
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("cobblemon_roguelite/payout")

/**
 * One grant, resolved into the stacks that will actually be handed over.
 *
 * @property counts one entry per [net.minecraft.world.item.ItemStack] to create, in order. A list of
 *   sizes rather than the stacks themselves because an `ItemStack` cannot be built without the game's
 *   registries, and everything worth getting wrong here — the chunking, the clamps, the refusals — is
 *   arithmetic that must be testable outside a booted server.
 */
data class PlannedGrant(
    val grant: PayoutGrant.Item,
    val counts: List<Int>,
) {
    val total: Int get() = counts.sum()
}

/**
 * What can be handed over, and what cannot.
 *
 * The split is the point. §1.1 makes the payout the one metered channel out of a sealed run, so
 * "eleven grants resolved, nine handed over" has to be a thing the code can say rather than something
 * inferred from a silence — and [unresolved] is what makes the difference sayable.
 */
data class PayoutDropPlan(
    val planned: List<PlannedGrant>,
    val unresolved: List<PayoutGrant>,
) {
    val stacks: Int get() = planned.sumOf { it.counts.size }
    val grants: List<PayoutGrant> get() = planned.map { it.grant }
    val complete: Boolean get() = unresolved.isEmpty()

    companion object {
        val NOTHING = PayoutDropPlan(emptyList(), emptyList())
    }
}

/**
 * Turns grants into stack sizes.
 *
 * ### Why the item registry is a lambda
 *
 * Because this is the code that must not first execute in production. Resolution is the step that
 * fails in the field — an id from a datapack that has since changed, a mod that was uninstalled
 * between a run ending and the player returning — and the arithmetic around it (splitting 300 of a
 * 64-stack item into five stacks; refusing a count that a hand-edited save has set to zero) is
 * exactly the sort of thing that ships never having run if it is welded to `BuiltInRegistries`.
 * Passing the lookup in costs one lambda at the two call sites and buys tests for all of it.
 *
 * The lambda answers **null for an item this server does not have**, and the max stack size
 * otherwise, because those are the same question asked once.
 */
object PayoutDropPlanner {

    /**
     * The most stacks a single grant may produce, after which the remainder is refused and logged.
     *
     * Not a design limit — [PayoutGrant] already asks table authors to split large payouts across
     * entries, and no sane table gets near this. It is a guard against a number this module does not
     * control: counts arrive from a datapack and from a save file, and `count = 2_000_000_000` turns
     * a payout into millions of item entities spawned inside one tick, which is not a payout failure
     * but a server that has stopped. Refusing the remainder loudly is survivable; the alternative is
     * not.
     */
    const val MAX_STACKS_PER_GRANT = 128

    /** Used when the lookup answers a nonsensical stack size, so that the loop below always ends. */
    const val FALLBACK_STACK_SIZE = 64

    fun plan(grants: List<PayoutGrant>, maxStackSize: (ResourceLocation) -> Int?): PayoutDropPlan {
        if (grants.isEmpty()) return PayoutDropPlan.NOTHING
        val planned = mutableListOf<PlannedGrant>()
        val unresolved = mutableListOf<PayoutGrant>()
        for (grant in grants) {
            when (grant) {
                is PayoutGrant.Item -> {
                    val size = maxStackSize(grant.item)
                    if (size == null) {
                        // Loud, and by id. The id was only syntax-checked at load (see [PayoutGrant]),
                        // because an id belonging to an optional mod is legitimate on a server that
                        // has it — so this is the first point at which "installed" can be answered,
                        // and it is the last point at which anybody can be told. The rest of the
                        // payout still pays: one missing mod must not cost the other ten grants.
                        log.error(
                            "roguelite: payout names unknown item '{}' — {} of it cannot be handed over",
                            grant.item, grant.count,
                        )
                        unresolved += grant
                        continue
                    }
                    if (grant.count < 1) {
                        log.error(
                            "roguelite: payout of '{}' has count {} — refusing it rather than guessing",
                            grant.item, grant.count,
                        )
                        unresolved += grant
                        continue
                    }
                    // A registry answering zero or negative is not a thing vanilla does, but this
                    // lookup crosses into third-party items and a `0` here would be an infinite loop
                    // holding the server thread — a hang, not a missing payout.
                    val per = if (size >= 1) size else {
                        log.error(
                            "roguelite: item '{}' reports a max stack size of {} — falling back to {}",
                            grant.item, size, FALLBACK_STACK_SIZE,
                        )
                        FALLBACK_STACK_SIZE
                    }
                    var remaining = grant.count
                    val counts = mutableListOf<Int>()
                    while (remaining > 0 && counts.size < MAX_STACKS_PER_GRANT) {
                        val chunk = minOf(remaining, per)
                        counts += chunk
                        remaining -= chunk
                    }
                    if (remaining > 0) {
                        log.error(
                            "roguelite: payout of {}x '{}' exceeds {} stacks — {} were NOT handed over; " +
                                "split large payouts across table entries",
                            grant.count, grant.item, MAX_STACKS_PER_GRANT, remaining,
                        )
                    }
                    planned += PlannedGrant(grant, counts)
                }
            }
        }
        return PayoutDropPlan(planned, unresolved)
    }
}
