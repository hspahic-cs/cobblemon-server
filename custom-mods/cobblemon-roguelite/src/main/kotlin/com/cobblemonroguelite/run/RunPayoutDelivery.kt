package com.cobblemonroguelite.run

import com.cobblemonroguelite.data.payout.PayoutGrant
import com.cobblemonroguelite.payout.PayoutDropPlanner
import com.cobblemonroguelite.payout.PendingPayoutHooks
import com.cobblemonroguelite.payout.PendingPayoutStore
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.MinecraftServer
import net.minecraft.world.item.ItemStack
import org.slf4j.LoggerFactory
import java.util.UUID

private val log = LoggerFactory.getLogger("cobblemon_roguelite/payout")

/**
 * What actually reached the player, as opposed to what the table said.
 *
 * The two are separate because they come apart in ways that matter: an item id from a mod that has
 * since been removed, a full inventory, an offline player. §1.1 makes the payout the one metered
 * channel out of a sealed run, so "we resolved eleven grants and handed over nine" is the sort of
 * thing that has to be sayable rather than inferred from a silence.
 *
 * @property held grants that were **not** handed over and are **not** lost: the player was offline,
 *   so they are on disk in [PendingPayoutStore] waiting for their next login. Kept apart from
 *   [undelivered] because the two want opposite reactions — an undelivered grant is a fault an
 *   operator should look at, a held one is the system working.
 */
data class PayoutDelivery(
    val delivered: List<PayoutGrant>,
    val undelivered: List<PayoutGrant>,
    val held: List<PayoutGrant> = emptyList(),
) {
    /**
     * Whether anything failed. [held] is deliberately not counted: it is in flight, not lost.
     *
     * That reading is safe because of an invariant [RunPayoutDelivery.deliver] maintains — a payout
     * is either entirely handed over or entirely held, never split — and because the only reader of
     * this flag is a message shown to a player who is *online*, which by construction is never the
     * case for a payout that was held.
     */
    val complete: Boolean get() = undelivered.isEmpty()

    companion object {
        val NOTHING = PayoutDelivery(emptyList(), emptyList())
    }
}

/**
 * Hands a resolved payout over.
 *
 * ### Why this is a plain object and not a seam
 *
 * §2.20 turned the payout into items the module grants by itself, so a published build with nothing
 * registered pays out fully (§1.2). Putting delivery behind an interface would put that back where
 * it was — a payout that only works when somebody else implements it.
 *
 * ### The offline case, which is now handled
 *
 * A run can end with nobody there to be paid: §2.10's disconnect penalty can wipe a party, and an
 * operator can clear a run. This used to return those grants as [PayoutDelivery.undelivered] and log
 * at ERROR — nothing was lost, but nothing arrived either.
 *
 * They are now **held** and dropped at the player's feet on their next login
 * ([PendingPayoutHooks]). Two things were rejected to get there, and both for the same reason:
 *
 * - **Dropping the stacks in the world where the run ended.** Item entities despawn in five minutes,
 *   and the run ended in an arena the player is not standing in — so the payout would be gone in
 *   exactly the case it exists for, while the log claimed it was paid. A delivery that logs success
 *   and loses the goods is worse than the failure it replaced.
 * - **Doing it at the moment the run ends, in any form.** The player is not there. Whatever is done
 *   has to survive until they are, and only a file does that.
 *
 * ### Held is all-or-nothing
 *
 * An offline payout is held whole, without resolving a single id. Resolution is deferred to delivery
 * on purpose: a datapack reload between the run ending and the player returning can restore an id
 * that is missing today, and resolving early would bake a temporary absence into a permanent loss.
 */
object RunPayoutDelivery {

    fun deliver(server: MinecraftServer, player: UUID, grants: List<PayoutGrant>): PayoutDelivery {
        if (grants.isEmpty()) return PayoutDelivery.NOTHING

        val target = server.playerList.getPlayer(player)
        if (target == null) {
            // Not an error any more — this is the designed path for a run that ends on the disconnect
            // penalty. The store logs what it took on, at INFO, naming every grant.
            PendingPayoutStore.of(server).hold(server, player, grants)
            return PayoutDelivery(emptyList(), emptyList(), held = grants)
        }

        // Same planner the held path uses, so the chunking and the refusals are one piece of code
        // with one set of tests rather than two that drift. See [PayoutDropPlanner] on why the item
        // lookup is passed in.
        val plan = PayoutDropPlanner.plan(grants) { id ->
            val item = BuiltInRegistries.ITEM.getOptional(id).orElse(null)
            if (item == null) null else ItemStack(item, 1).maxStackSize
        }

        for (planned in plan.planned) {
            val item = BuiltInRegistries.ITEM.getOptional(planned.grant.item).orElse(null)
            if (item == null) {
                log.error("roguelite: '{}' resolved during planning and not during delivery", planned.grant.item)
                continue
            }
            for (count in planned.counts) {
                val stack = ItemStack(item, count)
                // Dropped at the player rather than voided when the inventory is full. A payout the
                // player has to pick up off the floor is recoverable; one that was silently discarded
                // because slot 36 had a rock in it is not.
                if (!target.addItem(stack)) target.drop(stack, false)
            }
        }
        return PayoutDelivery(plan.grants, plan.unresolved)
    }
}
