package com.cobblemonroguelite.shop

import com.cobblemon.mod.common.api.moves.MoveTemplate
import com.cobblemon.mod.common.pokemon.Pokemon

/**
 * Whether a TM can land on a given Pokémon — decided *before* anything is taken or charged.
 *
 * ### Why the learnset is checked at all
 *
 * [RewardGrant]'s move path teaches through `MoveSet.add`/`setMove`, and Cobblemon's `MoveSet`
 * enforces **nothing** — it is the storage, not the rules. The rules live in the TM *item* flow this
 * mod does not use, so without this file a `TechnicalMachine` reward would happily put Earthquake on
 * a Magikarp. The playtest ruling (2026-07-31) is that it must not: a TM only applies to a Pokémon
 * whose species can actually learn the move, and an ineligible pick is refused so the player can aim
 * at a different party member instead.
 *
 * ### The pure/impure split, again
 *
 * Same split as [RewardTargeting]/[RewardGrant] and `HiddenAbilityUnlock`/`HiddenAbilityGrant`, for
 * the same reason: a `Pokemon` and a `Learnset` cannot be built outside a booted game, so the
 * decision is made over plain strings where a unit test can reach it, and the [MoveTemplate]
 * overload is the thin adapter that reads a live Pokémon. Both the GUI's pre-take intercept and the
 * grant's backstop call through here — one copy of the rule, so they cannot drift.
 *
 * ### What counts as learnable
 *
 * `Learnset.getAllLegalMoves()`: level-up + egg + tutor + TM + evolution + form-change moves, which
 * is Cobblemon's own union (legacy and special event-only moves are excluded by Cobblemon itself).
 * Deliberately NOT `tmMoves` alone — our "TechnicalMachine" reward is a roguelite pickup, not a
 * literal mainline TM, and refusing to teach a Pokémon its own egg move because mainline never
 * printed that TM would be pedantry the player experiences as a bug. Read via `pokemon.form.moves`
 * rather than `species.moves`, because a form can override its learnset and `FormData.moves` falls
 * back to the species learnset when it does not (verified against Cobblemon 1.7.3).
 */
object TmEligibility {

    /**
     * Why [moveName] cannot be taught to this Pokémon, or null when it can be.
     *
     * The reason is written to be shown to the player as-is, with call sites appending their own
     * consequence ("— nothing was taken", "— pick another Pokémon"): the sentence about the Pokémon
     * is the shared fact, the sentence about the click is not.
     *
     * Already-knows is checked **first**, and the ordering is not cosmetic: a run Pokémon can know a
     * move that is not in its legal learnset (a starter template, a future event move), and telling
     * the player their Blaziken "can't learn" the Flamethrower it is currently using would be
     * obviously wrong. If it knows the move, teaching is pointless regardless of the learnset.
     *
     * Case-insensitive throughout, matching every other move-name comparison in this module —
     * `Moves.getByName` normalises but reward tables are hand-typed.
     */
    fun blockReason(
        moveName: String,
        moveDisplay: String,
        speciesName: String,
        knownMoveNames: List<String>,
        learnableMoveNames: Collection<String>,
    ): String? {
        if (knownMoveNames.any { it.equals(moveName, ignoreCase = true) }) {
            return "$speciesName already knows $moveDisplay"
        }
        if (learnableMoveNames.none { it.equals(moveName, ignoreCase = true) }) {
            return "$speciesName can't learn $moveDisplay"
        }
        return null
    }

    /** The impure adapter: reads the live Pokémon and delegates every decision to the pure overload. */
    fun blockReason(template: MoveTemplate, pokemon: Pokemon): String? = blockReason(
        moveName = template.name,
        moveDisplay = template.displayName.string,
        speciesName = pokemon.species.name,
        knownMoveNames = pokemon.moveSet.getMoves().map { it.name },
        learnableMoveNames = pokemon.form.moves.getAllLegalMoves().map { it.name },
    )
}
