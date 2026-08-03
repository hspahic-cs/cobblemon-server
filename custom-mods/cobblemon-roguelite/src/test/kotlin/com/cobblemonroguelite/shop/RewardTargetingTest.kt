package com.cobblemonroguelite.shop

import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemonroguelite.data.reward.RunReward
import net.minecraft.resources.ResourceLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Who a reward lands on.
 *
 * The failures this guards are all silent ones. A per-Pokémon reward applied party-wide is a Rare Candy
 * that levels the whole team; a party-wide reward applied to one member is a Revive that only one
 * Pokémon can use; and an off-by-one targets the wrong Pokémon without throwing. None of the three
 * looks like a bug from the outside, which is why they are pinned here rather than left to review.
 */
class RewardTargetingTest {

    private val evs = RunReward.Evs(Stats.ATTACK, 10)
    private val bagItem = RunReward.BagItem(ResourceLocation.fromNamespaceAndPath("cobblemon", "revive"), 1)

    @Test
    fun `a bag item is party-wide because a run's bag belongs to the run`() {
        // §2.11: the run bag is the only bag a run has, so an evolution item and a Revive are the same
        // mechanism. If this flips to needing a member, every evolution item becomes un-grantable.
        assertEquals(RewardTarget.WholeParty, RewardTargeting.resolve(bagItem, chosenSlot = null, partySize = 3))
    }

    @Test
    fun `a credits reward is party-wide because the balance belongs to the run`() {
        // If this flips to needing a member, every Nugget pick starts demanding a party slot that
        // changes nothing — and a solo pick in the GUI would open a pointless picker screen.
        val credits = RunReward.Credits(2.5)
        assertTrue(!RewardTargeting.needsMember(credits), "credits should not need a slot")
        assertEquals(RewardTarget.WholeParty, RewardTargeting.resolve(credits, chosenSlot = null, partySize = 3))
    }

    @Test
    fun `every per-Pokemon reward type needs a member`() {
        val perPokemon = listOf(
            evs,
            RunReward.Levels(1),
            RunReward.Mint(ResourceLocation.fromNamespaceAndPath("cobblemon", "adamant")),
            RunReward.AbilityPatch(null),
            RunReward.HeldItem(ResourceLocation.fromNamespaceAndPath("cobblemon", "leftovers")),
            RunReward.TechnicalMachine("earthquake"),
        )
        perPokemon.forEach { reward ->
            assertTrue(RewardTargeting.needsMember(reward), "$reward should need a party slot")
        }
        assertTrue(!RewardTargeting.needsMember(bagItem), "a bag item should not need a slot")
    }

    @Test
    fun `a passive is party-wide because §2_43's buffs are team-wide by definition`() {
        val passive = RunReward.Passive(com.cobblemonroguelite.run.RunPassive.EXP_CHARM)
        assertTrue(!RewardTargeting.needsMember(passive), "a passive should not need a slot")
        // A chosen slot on a passive is ignored, not refused — the player asked for something
        // coherent and named a member that does not change the outcome (same rule as bag items).
        assertEquals(RewardTarget.WholeParty, RewardTargeting.resolve(passive, chosenSlot = 2, partySize = 3))
    }

    @Test
    fun `a per-Pokemon reward with no slot chosen is refused, not applied to the lead`() {
        // Defaulting to the lead is the tempting shortcut: a player who meant to patch their sweeper
        // would silently patch whatever was first, and would only find out much later.
        val unresolved = assertIs<RewardTarget.Unresolved>(RewardTargeting.resolve(evs, null, partySize = 4))
        assertTrue("1-4" in unresolved.reason, unresolved.reason)
    }

    @Test
    fun `with a party of one there is no choice to make, so the member is inferred`() {
        assertEquals(RewardTarget.Member(0), RewardTargeting.resolve(evs, null, partySize = 1))
    }

    @Test
    fun `a chosen slot is converted from 1-based to 0-based exactly once`() {
        // The off-by-one that targets the wrong Pokemon without throwing.
        assertEquals(RewardTarget.Member(0), RewardTargeting.resolve(evs, chosenSlot = 1, partySize = 6))
        assertEquals(RewardTarget.Member(5), RewardTargeting.resolve(evs, chosenSlot = 6, partySize = 6))
    }

    @Test
    fun `a slot outside the party is refused, including zero and negatives`() {
        listOf(0, -1, 7).forEach { slot ->
            val result = RewardTargeting.resolve(evs, slot, partySize = 6)
            assertIs<RewardTarget.Unresolved>(result, "slot $slot should not resolve")
        }
    }

    @Test
    fun `an empty party refuses rather than reporting a slot range of one to zero`() {
        val unresolved = assertIs<RewardTarget.Unresolved>(RewardTargeting.resolve(evs, null, partySize = 0))
        assertTrue("empty" in unresolved.reason, unresolved.reason)
    }

    @Test
    fun `a slot named on a party-wide reward is ignored rather than refused`() {
        // The player asked for something coherent; naming a member simply does not change the outcome.
        assertEquals(RewardTarget.WholeParty, RewardTargeting.resolve(bagItem, chosenSlot = 3, partySize = 6))
    }
}
