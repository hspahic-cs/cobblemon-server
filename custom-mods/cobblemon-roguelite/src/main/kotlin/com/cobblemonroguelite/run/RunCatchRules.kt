package com.cobblemonroguelite.run

/**
 * §2.13's arithmetic, decided without a Pokémon in sight.
 *
 * ### Why this is separated from [RunState] at all
 *
 * Everything the catch decision actually *decides* is a question about counts: is there room, is a
 * decision already outstanding, is that slot one of theirs. Everything else — which object goes
 * where, what gets destroyed — is bookkeeping that follows from the answer. Splitting them the same
 * way [RunProgress] and [DisconnectAttribution] are split buys the only thing this module can have
 * without a dev VM: these branches are unit-testable, and the Pokémon-shaped half is not.
 *
 * That is not a stylistic preference here. A `Pokemon` cannot be constructed in a plain JUnit run at
 * all — Cobblemon's entity classes fail to link outside a booted game — so a rule left inline in
 * [RunState] is a rule that ships having never been executed. The branch that decides whether a run
 * Pokémon is destroyed is not one to ship untested.
 */
object RunCatchRules {

    /** What offering a catch to a party of [partySize] does. */
    enum class Route {
        /** There is room. The catch joins and nothing is asked. */
        JOIN,

        /** The party is full, so the catch waits on a swap-or-release decision. */
        HOLD,

        /**
         * A decision is already outstanding, so the new catch is lost.
         *
         * Not "replace the held one": the player is looking at a prompt that names the held Pokémon,
         * and changing what that prompt refers to would make them confirm the destruction of
         * something they were never shown.
         */
        REFUSE,
    }

    fun route(partySize: Int, holdingCatch: Boolean): Route = when {
        holdingCatch -> Route.REFUSE
        partySize < RunState.MAX_PARTY -> Route.JOIN
        else -> Route.HOLD
    }

    /**
     * Whether a held catch should simply be taken in rather than decided about.
     *
     * True exactly when the party has room again, because the prompt exists *because* it did not —
     * see [RunState.claimPendingCatch] for the §2.10 route that makes this reachable.
     */
    fun claims(partySize: Int, holdingCatch: Boolean): Boolean =
        holdingCatch && partySize < RunState.MAX_PARTY

    /**
     * The 0-based party index a 1-based [slot] names, or null when it names nothing.
     *
     * Bounded by the party's actual size and not by [RunState.MAX_PARTY]: a run whose party has been
     * thinned by permadeath has fewer slots than six, and "swap slot 6" against a party of four has
     * to be refused rather than rounded to the last one — the rounding would destroy a Pokémon the
     * player did not name.
     */
    fun swapIndex(slot: Int, partySize: Int): Int? = (slot - 1).takeIf { it in 0 until partySize }
}
