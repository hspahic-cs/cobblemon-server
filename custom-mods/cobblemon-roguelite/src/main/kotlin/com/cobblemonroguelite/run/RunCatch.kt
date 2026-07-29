package com.cobblemonroguelite.run

import com.cobblemon.mod.common.pokemon.Pokemon

/**
 * §2.13's catch decision, as a set of answers rather than a set of side effects.
 *
 * ### Why every one of these carries the Pokémon it is talking about
 *
 * The command layer has to *name* what is about to be destroyed, and by the time it is asked the
 * run has already changed: a swap has taken one member out and put another in, a release has let go
 * of something [RunState] no longer holds a reference to. Handing the Pokémon back with the answer
 * is what lets the message be written after the fact and still be true. Re-reading the run to build
 * the message is how a player gets told they released the wrong thing.
 *
 * ### Why these are answers and not exceptions
 *
 * Every failure here is a player typing a command — a slot number that is not in their party, a
 * second `confirm` after the first one already went through. Those are not faults, and a throw would
 * turn each of them into a stack trace in the log of a mode whose defining risk is destroying
 * somebody's party by accident.
 */

/** Where a run catch ended up when it was offered to the run — see [RunState.offer]. */
sealed interface CatchRouting {

    /** Straight into the party, at 1-based [slot] in party order. Nothing was asked and nothing lost. */
    data class Joined(val slot: Int) : CatchRouting

    /**
     * The party was full, so the catch is held for the swap-or-release decision. [party] is the
     * party as it stood, in the order the prompt numbers it.
     */
    data class HeldForDecision(val pokemon: Pokemon, val party: List<Pokemon>) : CatchRouting

    /**
     * A decision was already outstanding, so this catch is gone.
     *
     * Should be unreachable — the run refuses to start a wave while a catch is held, and a wild wave
     * ends the moment its one opponent is caught — which is exactly why it is a state rather than a
     * silent overwrite: if it ever happens, something has let a run fight with a decision pending,
     * and that is worth a loud line in the log rather than a Pokémon quietly disappearing.
     *
     * [held] is the catch that was kept, so the caller can say which one survived.
     */
    data class AlreadyDeciding(val held: Pokemon) : CatchRouting
}

/** What the player chose to do about a held catch. */
sealed interface CatchDecision {

    /** Destroy the party member in 1-based [slot] and put the held catch in its place. */
    data class Swap(val slot: Int) : CatchDecision

    /** Destroy the held catch and keep the party as it is. */
    data object Release : CatchDecision
}

/** What [RunState.resolveCatch] did. */
sealed interface CatchResolution {

    /** [discarded] is gone for good; [kept] is now the run's, in 1-based [slot]. */
    data class Swapped(val slot: Int, val discarded: Pokemon, val kept: Pokemon) : CatchResolution

    data class Released(val released: Pokemon) : CatchResolution

    /** The slot number is not one of theirs. Nothing was touched — [partySize] is what it may be. */
    data class NoSuchSlot(val partySize: Int) : CatchResolution
}

/**
 * What `/roguelite catch` found. Distinct from [CatchResolution] because asking is not deciding, and
 * the one thing this mode must never do is let a question have a side effect that destroys something.
 */
sealed interface CatchPrompt {

    data object NoRun : CatchPrompt

    /** Nothing is held. The ordinary answer, and not an error — see [RunCommands]. */
    data object NothingHeld : CatchPrompt

    /** The decision, with the party numbered as the player will be shown it. */
    data class Held(val pokemon: Pokemon, val party: List<Pokemon>) : CatchPrompt

    /**
     * The party had room again by the time they asked, so the catch simply joined at 1-based [slot].
     *
     * The one side effect a question is allowed here, and it is allowed because it cannot lose
     * anything: see [RunState.claimPendingCatch] for why a party with a free slot has already
     * answered the question.
     */
    data class Joined(val pokemon: Pokemon, val slot: Int) : CatchPrompt
}
