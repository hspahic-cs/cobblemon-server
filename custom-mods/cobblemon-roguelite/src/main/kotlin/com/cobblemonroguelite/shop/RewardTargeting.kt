package com.cobblemonroguelite.shop

import com.cobblemonroguelite.data.reward.RunReward

/**
 * Who a reward applies to, decided before anything is mutated.
 *
 * ### Why targeting is its own decision
 *
 * [com.cobblemonroguelite.data.reward.RunReward]'s docs say it deliberately carries no targeting:
 * *"Nothing says which party member gets the EVs. That is a run-time choice."* This is that choice,
 * and it is separated from applying so the interesting part is testable — a `Pokemon` cannot be built
 * outside a booted game, so anything that takes one is code that ships having never run. Same split
 * [com.cobblemonroguelite.starter.HiddenAbilityUnlock] makes from `HiddenAbilityGrant`.
 *
 * ### The rule, and the trap it avoids
 *
 * Some rewards are **party-wide** and some need **one member**, and getting that wrong is not a crash:
 * it is a Rare Candy that levels the whole team, or a Metal Coat that six Pokémon each receive. So the
 * requirement is derived from the reward *type* rather than left to each call site to remember.
 *
 * A reward that needs a member and was given none is [Unresolved] rather than silently applied to the
 * lead. Defaulting to the lead is the tempting shortcut and it is wrong twice over: a player who meant
 * to patch their sweeper would silently patch whatever happened to be first, and the failure would only
 * surface as a confused bug report. Refusing means the caller has to ask, which is the actual mechanic.
 */
sealed interface RewardTarget {

    /** Applies to every member of the run party — the reward does not name one. */
    data object WholeParty : RewardTarget

    /** Applies to party slot [index], zero-based, as the player chose. */
    data class Member(val index: Int) : RewardTarget

    /** The reward needs a member and none was chosen, or the chosen slot is not in the party. */
    data class Unresolved(val reason: String) : RewardTarget
}

/** Decides [RewardTarget] from a reward and the player's choice. Pure; no Cobblemon types. */
object RewardTargeting {

    /**
     * Whether [reward] has to be pointed at one party member.
     *
     * The ones that do all write to a *Pokémon*: EVs, levels, a nature, an ability, a held item and a
     * move are all per-Pokémon facts. [RunReward.BagItem] does not, because a run's bag belongs to the
     * run rather than to a member (§2.11) — which is also why evolution items are bag items and not
     * held items. [RunReward.Credits] does not either: it writes to the run's balance, which no
     * Pokémon owns. Nor does [RunReward.Passive], and more strongly: §2.43's passives are
     * *team-wide by definition*, so a slot on one would be a question with no answer.
     */
    fun needsMember(reward: RunReward): Boolean = when (reward) {
        is RunReward.BagItem,
        is RunReward.Credits,
        is RunReward.Passive,
        -> false
        is RunReward.Evs,
        is RunReward.Levels,
        is RunReward.Mint,
        is RunReward.AbilityPatch,
        is RunReward.HeldItem,
        is RunReward.ModifierItem,
        is RunReward.TechnicalMachine,
        -> true
    }

    /**
     * Resolve the target for [reward] given [chosenSlot] (a 1-based slot as a player would type it,
     * or null) against a party of [partySize].
     *
     * 1-based on the way in because that is what the player sees and types; zero-based on the way out
     * because that is what indexes a list. Converting once, here, is what stops an off-by-one being
     * duplicated across every call site — and an off-by-one here targets the wrong Pokémon rather than
     * throwing, which is the sort of bug that survives a long time.
     */
    fun resolve(reward: RunReward, chosenSlot: Int?, partySize: Int): RewardTarget {
        if (!needsMember(reward)) {
            // A chosen slot on a party-wide reward is ignored rather than refused: the player asked for
            // something coherent and named a member that simply does not change the outcome.
            return RewardTarget.WholeParty
        }
        if (partySize <= 0) return RewardTarget.Unresolved("the run party is empty")
        if (chosenSlot == null) {
            return if (partySize == 1) {
                // With one Pokémon there is no choice to make, so requiring one would be pedantry
                // rather than a decision. This is the only place a member is inferred.
                RewardTarget.Member(0)
            } else {
                RewardTarget.Unresolved("this reward needs a party slot (1-$partySize)")
            }
        }
        if (chosenSlot !in 1..partySize) {
            return RewardTarget.Unresolved("slot $chosenSlot is not in the party (1-$partySize)")
        }
        return RewardTarget.Member(chosenSlot - 1)
    }
}
