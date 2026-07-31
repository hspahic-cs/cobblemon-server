package com.cobblemonroguelite.battle

import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon
import com.cobblemonroguelite.integration.RunOpponent
import com.cobblemonroguelite.run.RunState
import org.slf4j.LoggerFactory
import java.util.UUID

private val log = LoggerFactory.getLogger("cobblemon_roguelite/battle")

/**
 * PokéRogue's stat-stage carryover, half-built on purpose.
 *
 * ### The rule being mirrored (user decision 2026-07-31)
 *
 * Stat stages (and eventually forms) persist across **wild** waves and reset at **trainer** battles.
 * Ours resets everything every wave, because each wave is its own Cobblemon/Showdown battle and
 * battle state dies with the battle by construction — so carrying it means capturing the player
 * side's stages when a wild wave ends and re-applying them when the next battle starts.
 *
 * ### Which half this is, and why the other half is a stub
 *
 * **Capture, persistence and the reset rule are real** — [captureFrom] reads
 * `BattlePokemon.statChanges` (Cobblemon's own bookkeeping of the `|boost|` stream) for every
 * surviving party member, [RunState.carriedBoosts] rides the ordinary checkpoints, and
 * [resetForWave] clears on any non-wild wave, which is exactly PokéRogue's reset point.
 *
 * **Injection is deliberately inert** ([applyTo] logs and does nothing), because `statChanges` is a
 * *mirror* of Showdown state, not an input to it — writing the map back would change what the client
 * displays while the simulator disagrees, which corrupts battles rather than merely missing. The
 * real injection has to reach the Showdown side (the candidate mechanisms are a synthetic bag-item
 * instruction per stage, or a mega_showdown-style script hook) and gets its own researched pass; a
 * wrong guess here costs every battle after wave 1, which is why this file refuses to guess.
 */
object RunCarriedBoosts {

    /**
     * Read the player side's surviving stages out of a finished wild-wave battle into [run].
     *
     * Keyed by Pokémon UUID so a boost follows its owner and dies with it — a fainted member's entry
     * is simply never read again, and permadeath removes the Pokémon the key points at.
     */
    fun captureFrom(battle: PokemonBattle, playerId: UUID, run: RunState) {
        val carried = mutableMapOf<UUID, Map<String, Int>>()
        battle.actors.filter { it.uuid == playerId }.forEach { actor ->
            actor.pokemonList.forEach { member: BattlePokemon ->
                val stages = member.statChanges
                    .filterValues { it != 0 }
                    .entries
                    .associate { (stat, stage) -> stat.showdownId to stage }
                if (stages.isNotEmpty() && member.health > 0) {
                    carried[member.effectedPokemon.uuid] = stages
                }
            }
        }
        run.carriedBoosts.clear()
        run.carriedBoosts.putAll(carried)
        if (carried.isNotEmpty()) {
            log.debug("roguelite: carried {} boosted member(s) out of wave {}", carried.size, run.wave)
        }
    }

    /**
     * PokéRogue's reset point: any non-wild wave clears the slate before it begins. Called from the
     * controller where the plan's kind is known, so the rule lives beside the schedule that defines
     * "trainer wave" rather than being re-derived here.
     */
    fun resetForWave(run: RunState, kind: RunOpponent) {
        if (kind != RunOpponent.WILD && run.carriedBoosts.isNotEmpty()) {
            log.debug("roguelite: {} wave resets {} carried boost entr(ies)", kind, run.carriedBoosts.size)
            run.carriedBoosts.clear()
        }
    }

    /**
     * THE STUB. Logs what a real injection would apply and touches nothing — see the class docs for
     * why guessing here is worse than waiting.
     */
    fun applyTo(battle: PokemonBattle, playerId: UUID, run: RunState) {
        if (run.carriedBoosts.isEmpty()) return
        log.debug(
            "roguelite: {} carried boost entr(ies) NOT applied to the new battle — injection is not " +
                "implemented yet (mirror-only statChanges; needs a Showdown-side mechanism)",
            run.carriedBoosts.size,
        )
    }
}
