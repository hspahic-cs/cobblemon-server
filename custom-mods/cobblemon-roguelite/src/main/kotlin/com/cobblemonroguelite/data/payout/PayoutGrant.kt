package com.cobblemonroguelite.data.payout

import com.cobblemonroguelite.data.JsonView
import net.minecraft.resources.ResourceLocation

/**
 * One thing a finished run hands the player, for keeps.
 *
 * ### Why this surface is items and nothing else
 *
 * §2.20 settled that the payout is **not currency**: the mode charges currency at the door (§2.18)
 * and has to stay a sink, so paying out in the same unit only works while the average run loses
 * money, and stops working the moment it does not. What is left is things — and things are the one
 * kind of reward this module can hand out *by itself*, with no economy and no host mod present,
 * which is what makes a published build pay out properly with nothing registered (§1.2).
 *
 * ### Why sealed, i.e. why a datapack cannot add a payout kind
 *
 * Same reason as [com.cobblemonroguelite.data.reward.RunReward]: every kind needs code that hands it
 * over, so a datapack can add an *entry* or a whole *table* but not a kind, and sealing makes the
 * grant-side `when` exhaustive so a new kind is a compile error rather than a silently skipped
 * payout. It reads as ceremony while there is one member; the point is that the one member is a
 * decision rather than an accident.
 *
 * ### What is deliberately not here
 *
 * **A currency kind.** That is the whole of §2.20 — see [com.cobblemonroguelite.integration.RunPayouts]
 * for where a server that wants one plugs it in.
 *
 * **A command kind.** `"run": "/give @p ..."` is the obvious way to make a payout table able to
 * express anything, and it is exactly what must not exist here: a table could then pay out currency,
 * grant permissions, or teleport, and the isolation contract of §1.1 would be enforced by whatever
 * the server owner happened to type. A closed set of grants is what keeps "the payout is data" from
 * meaning "the payout is arbitrary".
 *
 * **Existence of the item id.** Checked for syntax at load and resolved when the payout is handed
 * over, for the reasons given on [com.cobblemonroguelite.data.reward.RunReward]: an id belonging to
 * an optional third-party mod is legitimate on a server that has it and a false alarm on one that
 * does not.
 */
sealed interface PayoutGrant {

    /** A stack of [count] × [item]. Split a large payout across entries rather than exceeding a stack. */
    data class Item(val item: ResourceLocation, val count: Int) : PayoutGrant

    companion object {

        /**
         * Read the `grant` object of a payout entry. Returns null with problems recorded if it is
         * unusable.
         *
         * Type ids are bare strings for the same reason reward types are: the set is closed, so a
         * namespace would be ceremony on every hand-written table with nothing that could collide.
         */
        fun parse(view: JsonView): PayoutGrant? {
            val type = view.requireString("type")
            val grant = when (type) {
                null -> null
                "item" -> parseItem(view)
                else -> {
                    view.problem("type", "unknown grant type '$type' (expected one of: ${TYPES.joinToString(", ")})")
                    null
                }
            }
            // Runs even on the failure paths above, so a file with both a bad type and a stray field
            // reports both and is fixed in one sitting.
            view.expectNoUnknownKeys()
            return grant
        }

        private fun parseItem(view: JsonView): PayoutGrant? {
            val raw = view.requireString("item")
            val count = view.optionalInt("count") ?: 1
            if (count < 1) {
                view.problem("count", "must be at least 1, was $count")
                return null
            }
            if (raw == null) return null
            // Unqualified means `minecraft:`, which is what every other datapack in the game means
            // by it. Cobblemon items therefore have to be written out in full, which is the right way
            // round: guessing our own namespace would make a vanilla item id fail confusingly.
            val qualified = if (raw.contains(':')) raw else "$MINECRAFT:$raw"
            val id = ResourceLocation.tryParse(qualified)
            if (id == null) {
                view.problem("item", "'$raw' is not a valid id (letters, digits, '_', '-', '.', '/' only, optionally 'namespace:path')")
                return null
            }
            return Item(id, count)
        }

        private const val MINECRAFT = "minecraft"

        private val TYPES = listOf("item")
    }
}
