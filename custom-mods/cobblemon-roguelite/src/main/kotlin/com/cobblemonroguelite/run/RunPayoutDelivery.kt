package com.cobblemonroguelite.run

import com.cobblemonroguelite.data.payout.PayoutGrant
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
 */
data class PayoutDelivery(
    val delivered: List<PayoutGrant>,
    val undelivered: List<PayoutGrant>,
) {
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
 * ### The offline case is not handled, and is not pretended to be
 *
 * [com.cobblemonroguelite.integration.RunPayoutProvider] already warns that a run can end with the
 * player offline. Items cannot be given to a player who is not there, and the two ways of coping —
 * dropping the stacks into the world where they left, or a claim queue they collect later — are both
 * real decisions with real failure modes (one loses the payout to despawn or to another player, the
 * other is new persistent state and a new command). Neither is invented here. What happens instead
 * is that the grants come back as [PayoutDelivery.undelivered] and the miss is logged at ERROR with
 * the player and the table, so nothing is lost quietly and the case is visible the first time it
 * happens rather than in a report from the player who lost a 200-wave payout.
 */
object RunPayoutDelivery {

    fun deliver(server: MinecraftServer, player: UUID, grants: List<PayoutGrant>): PayoutDelivery {
        if (grants.isEmpty()) return PayoutDelivery.NOTHING

        val target = server.playerList.getPlayer(player)
        if (target == null) {
            log.error(
                "roguelite: {} is offline at payout — {} grant(s) NOT delivered and there is no claim " +
                    "queue to hold them; see RunPayoutDelivery",
                player, grants.size,
            )
            return PayoutDelivery(emptyList(), grants)
        }

        val delivered = mutableListOf<PayoutGrant>()
        val undelivered = mutableListOf<PayoutGrant>()
        for (grant in grants) {
            when (grant) {
                is PayoutGrant.Item -> {
                    val item = BuiltInRegistries.ITEM.getOptional(grant.item).orElse(null)
                    if (item == null) {
                        // Not fatal to the rest of the payout. The id was only syntax-checked at load
                        // (see PayoutGrant), because an id belonging to an optional mod is legitimate
                        // on a server that has it — so this is the first point at which "installed"
                        // can be answered, and the other entries still pay.
                        log.error("roguelite: payout names unknown item '{}' — skipping that grant", grant.item)
                        undelivered += grant
                        continue
                    }
                    var remaining = grant.count
                    while (remaining > 0) {
                        // Chunked even though PayoutGrant asks authors to split large payouts across
                        // entries: an over-stack ItemStack is the kind of thing that survives being
                        // handed over and then vanishes on the next inventory write.
                        val stack = ItemStack(item, 1)
                        val size = minOf(remaining, stack.maxStackSize)
                        stack.count = size
                        remaining -= size
                        // Dropped at the player rather than voided when the inventory is full. A
                        // payout the player has to pick up off the floor is recoverable; one that was
                        // silently discarded because slot 36 had a rock in it is not.
                        if (!target.addItem(stack)) target.drop(stack, false)
                    }
                    delivered += grant
                }
            }
        }
        return PayoutDelivery(delivered, undelivered)
    }
}
