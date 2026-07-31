package com.cobblemonroguelite.run

/**
 * The run passives: team-wide buffs a run accumulates and keeps until it ends.
 *
 * ### What a passive is, and what it replaced
 *
 * §2.43 (end): PokéRogue's EXP items are not held items — they are *team-wide permanent run buffs*,
 * stacking each time the reward is taken. The first playtest ruled the held-item stand-ins out (an
 * "EXP Charm" on one Pokémon is neither team-wide nor visible), so the mechanism is run-scoped
 * stacks on [RunState.passiveStacks], read by an EXP-event multiplier
 * ([com.cobblemonroguelite.battle.RunExpPassives]). Nothing here touches a Pokémon or an item.
 *
 * ### The numbers are PokéRogue's, verbatim
 *
 * From `pagefaultgames/pokerogue` (`modifier.ts` / `modifier-type.ts` / `battle-scene.ts`,
 * fetched 2026-07-31):
 *
 * - `EXP_CHARM` is an `ExpBoosterModifierType` with `boostPercent = 25`; `SUPER_EXP_CHARM` is one
 *   with `boostPercent = 60`. The modifier applies
 *   `boost.value = floor(boost.value * (1 + stackCount * boostMultiplier))`, and its stack cap is
 *   `boostMultiplier < 1 ? (boostMultiplier < 0.6 ? 99 : 30) : 10` — so 99 for the charm and 30 for
 *   the super charm. 99 is effectively "uncapped" and is kept anyway: inventing a tighter cap is a
 *   balance decision this module does not own (§2.43 keeps their semantics; re-tuning is the reward
 *   *tables'* job, which control how often the stack can be taken at all).
 * - `EXP_SHARE` has `getMaxStackCount() = 5`, and `applyPartyExp` gives a non-participant
 *   `expValue * (stackCount * 0.2) / participantCount` — 20% of the earned EXP per stack, split by
 *   how many Pokémon actually fought.
 *
 * `EXP_BALANCE` (their fourth EXP item) is deliberately not here: it *redistributes* the party's
 * EXP toward its lowest-levelled members via a lerp, which is a per-award global rebalance and not
 * expressible as a per-gain multiplier. If it is ever wanted, it is a new passive kind plus a new
 * hook shape, not a row in this enum.
 *
 * ### Why an enum and not a datapack registry
 *
 * The same argument that makes [com.cobblemonroguelite.data.reward.RunReward] sealed: every passive
 * needs code that reads it — a stack with no reader is a purchase that does nothing. A datapack can
 * decide *where* a passive appears (tables, tiers, weights); it cannot invent a new kind.
 *
 * [id] is the wire name — what reward JSON and the NBT checkpoint both use — so renaming an enum
 * constant is free and renaming an [id] is a save-format change.
 */
enum class RunPassive(
    val id: String,
    val displayName: String,
    val maxStacks: Int,
    /** Percent added to every battle EXP gain, per stack. PokéRogue's `boostPercent`. */
    val expBoostPctPerStack: Int = 0,
    /** Percent of a participant's earned EXP granted to each non-participant, per stack. */
    val sharePctPerStack: Int = 0,
) {
    EXP_CHARM("exp_charm", "EXP Charm", maxStacks = 99, expBoostPctPerStack = 25),
    SUPER_EXP_CHARM("super_exp_charm", "Super EXP Charm", maxStacks = 30, expBoostPctPerStack = 60),
    EXP_SHARE("exp_share", "EXP Share", maxStacks = 5, sharePctPerStack = 20),
    ;

    companion object {

        fun byId(id: String): RunPassive? = entries.firstOrNull { it.id == id }

        /** The valid wire names, for rejection messages that name the whole set. */
        val ids: List<String> get() = entries.map { it.id }

        /**
         * The stack count after taking one more of [passive], or null when [current] is already at
         * the cap — null rather than the clamped value, because the caller has to answer the player
         * differently ("rank 3/5" vs "already maxed") and a clamped return hides which happened.
         */
        fun stackAfterGrant(current: Int, passive: RunPassive): Int? =
            if (current >= passive.maxStacks) null else current + 1

        /**
         * The battle-EXP multiplier for a run holding [stacks] — PokéRogue's
         * `1 + Σ(stackCount × boostMultiplier)` with both charm kinds summed, since owning both is
         * two modifiers each applying their own `(1 + stacks × pct)` there; summing is within a
         * rounding error of their sequential floors and is what one multiplier can express.
         *
         * Stacks are clamped to each kind's cap on the way in, so a hand-edited checkpoint cannot
         * exceed what the reward path could have granted.
         */
        fun expMultiplier(stacks: Map<String, Int>): Double =
            1.0 + entries.sumOf { passive ->
                passive.expBoostPctPerStack * stacksOf(stacks, passive)
            } / 100.0

        /** [base] battle EXP after the charm multiplier, floored like PokéRogue floors it. */
        fun boostedExp(base: Int, stacks: Map<String, Int>): Int =
            Math.floorDiv(
                base * (100 + entries.sumOf { it.expBoostPctPerStack * stacksOf(stacks, it) }),
                100,
            )

        /**
         * What one non-participant receives when a participant earned [earnedExp], PokéRogue's
         * formula: `earned × (shareStacks × 20%) / participantCount`. Zero when the run holds no
         * EXP Share, and [participantCount] is floored at 1 — a grant with no recorded participants
         * is Cobblemon telling us something odd, not a divide-by-zero.
         */
        fun sharedExp(earnedExp: Int, stacks: Map<String, Int>, participantCount: Int): Int {
            val shareStacks = stacksOf(stacks, EXP_SHARE)
            if (shareStacks == 0 || earnedExp <= 0) return 0
            val pct = EXP_SHARE.sharePctPerStack * shareStacks
            return Math.floorDiv(earnedExp * pct, 100 * participantCount.coerceAtLeast(1))
        }

        private fun stacksOf(stacks: Map<String, Int>, passive: RunPassive): Int =
            (stacks[passive.id] ?: 0).coerceIn(0, passive.maxStacks)
    }
}
