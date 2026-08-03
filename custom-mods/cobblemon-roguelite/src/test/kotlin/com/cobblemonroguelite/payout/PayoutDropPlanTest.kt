package com.cobblemonroguelite.payout

import com.cobblemonroguelite.data.payout.PayoutGrant
import net.minecraft.resources.ResourceLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Turning grants into stacks — the arithmetic that would otherwise ship never having executed.
 *
 * The planner is the one piece of the payout that fails against the *world* rather than against its
 * own logic: an id from a mod that has been uninstalled, a count a datapack author typed an extra
 * zero into, a save file somebody edited. All of it is welded to `BuiltInRegistries` at the call
 * sites, which is why the lookup is a lambda here — every case below is one that has to be right the
 * first time it happens on a live server, because the first time it happens is a player being paid.
 */
class PayoutDropPlanTest {

    private val diamond = ResourceLocation.fromNamespaceAndPath("minecraft", "diamond")
    private val bucket = ResourceLocation.fromNamespaceAndPath("minecraft", "bucket")
    private val ghost = ResourceLocation.fromNamespaceAndPath("somemod", "no_longer_installed")

    /** Vanilla-ish sizes, and null for anything this "server" does not have installed. */
    private val registry: (ResourceLocation) -> Int? = { id ->
        when (id) {
            diamond -> 64
            bucket -> 16
            else -> null
        }
    }

    @Test
    fun `a grant inside one stack is one stack`() {
        val plan = PayoutDropPlanner.plan(listOf(PayoutGrant.Item(diamond, 12)), registry)

        assertEquals(listOf(12), plan.planned.single().counts)
        assertTrue(plan.complete)
    }

    @Test
    fun `an over-stack grant is split, never handed over as one oversized stack`() {
        // An ItemStack over its max size survives being created and then vanishes on the next
        // inventory write, so this is not cosmetic: the un-split version pays 300 diamonds that
        // become 64 the moment the player relogs.
        val plan = PayoutDropPlanner.plan(listOf(PayoutGrant.Item(diamond, 300)), registry)

        val counts = plan.planned.single().counts
        assertEquals(listOf(64, 64, 64, 64, 44), counts)
        assertEquals(300, counts.sum())
    }

    @Test
    fun `stack size comes from the item, not from a guess of sixty-four`() {
        val plan = PayoutDropPlanner.plan(listOf(PayoutGrant.Item(bucket, 40)), registry)

        assertEquals(listOf(16, 16, 8), plan.planned.single().counts)
    }

    @Test
    fun `an item this server does not have is unresolved and the rest of the payout still pays`() {
        // The failure this is aimed at: a table naming an optional mod's item, on a server that had
        // it last week. One missing mod must not cost the player the other grants — and the id has to
        // survive into [unresolved] so the caller can name it.
        val plan = PayoutDropPlanner.plan(
            listOf(PayoutGrant.Item(ghost, 1), PayoutGrant.Item(diamond, 3)),
            registry,
        )

        assertEquals(listOf(PayoutGrant.Item(ghost, 1)), plan.unresolved)
        assertEquals(listOf(PayoutGrant.Item(diamond, 3)), plan.grants)
        assertEquals(false, plan.complete)
    }

    @Test
    fun `a nonsensical count is refused rather than guessed at`() {
        // Unreachable from a datapack (the parser enforces count >= 1) and entirely reachable from a
        // hand-edited save file, which is the input this planner also serves.
        val plan = PayoutDropPlanner.plan(listOf(PayoutGrant.Item(diamond, 0)), registry)

        assertEquals(listOf(PayoutGrant.Item(diamond, 0)), plan.unresolved)
        assertTrue(plan.planned.isEmpty())
    }

    @Test
    fun `a lookup answering zero does not hang the server`() {
        // The loop divides a count by the stack size. A third-party item reporting 0 would spin
        // forever holding the server thread — a hang, not a missing payout, and the sort of thing
        // that is only ever found this way.
        val plan = PayoutDropPlanner.plan(listOf(PayoutGrant.Item(diamond, 5))) { 0 }

        assertEquals(listOf(5), plan.planned.single().counts)
    }

    @Test
    fun `an absurd count is clamped instead of spawning a million item entities`() {
        val plan = PayoutDropPlanner.plan(listOf(PayoutGrant.Item(diamond, Int.MAX_VALUE)), registry)

        assertEquals(PayoutDropPlanner.MAX_STACKS_PER_GRANT, plan.planned.single().counts.size)
        // Clamped, not refused: the player gets what fits and the log names the remainder. Refusing
        // outright would pay nothing for a payout that was mostly legitimate.
        assertTrue(plan.grants.isNotEmpty())
    }

    @Test
    fun `nothing in means nothing out, with no lookup performed`() {
        val plan = PayoutDropPlanner.plan(emptyList()) { error("must not resolve anything") }

        assertEquals(PayoutDropPlan.NOTHING, plan)
    }
}
