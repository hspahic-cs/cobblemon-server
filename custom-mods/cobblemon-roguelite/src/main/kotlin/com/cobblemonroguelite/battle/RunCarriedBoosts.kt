package com.cobblemonroguelite.battle

import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon
import com.cobblemonroguelite.integration.RunOpponent
import com.cobblemonroguelite.run.RunState
import org.slf4j.LoggerFactory
import java.util.UUID

private val log = LoggerFactory.getLogger("cobblemon_roguelite/battle")

/**
 * PokéRogue's stat-stage carryover (user decision 2026-07-31): stages — buffs AND debuffs — persist
 * across **wild** waves and reset at any **trainer** battle.
 *
 * Three parts, one rule each: [captureFrom] reads the ACTIVE Pokémon's `statChanges` at wild-wave
 * victory (Cobblemon's mirror of the `|boost|` stream); [resetForWave] clears on any non-wild wave,
 * PokéRogue's own reset point; and [applyTo] re-applies at the next battle's start through the sim's
 * `>eval` input — the one write path where the SIMULATOR is the thing that changes, with the client
 * and Cobblemon's mirror fed by the ordinary protocol stream that falls out. An earlier revision
 * stubbed the injection while that mechanism was unverified; the dev jar's battle-stream.js keeps
 * Showdown's eval chunk, so the stub died the day it was born.
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
        // The ACTIVE Pokémon only — PokéRogue's own semantics: stages live on the Pokémon that is on
        // the field, and a switch already reset them mid-battle. Reading the whole party would carry
        // stale mirror values for benched members whose |unboost| stream stopped at their switch-out.
        battle.actors.filter { it.uuid == playerId }.forEach { actor ->
            actor.activePokemon.mapNotNull { it.battlePokemon }.forEach { member: BattlePokemon ->
                val stages = member.statChanges
                    .filterValues { it != 0 }
                    .entries
                    .associate { (stat, stage) -> stat.showdownId to stage.coerceIn(-6, 6) }
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
     * The injection, no longer a stub — through the sim's own `>eval` input.
     *
     * ### Why this is safe where writing `statChanges` was not
     *
     * The deployed battle-stream.js keeps Showdown's `case "eval"` chunk (verified on the dev jar,
     * lines 172–207), which evaluates in battle scope with `p1active` pre-bound. Calling
     * **`battle.boost(...)`** there runs the simulator's own boost pathway: the stages change in the
     * AUTHORITY, the `|-boost|` protocol lines stream back out, Cobblemon's interpreter mirrors them
     * into `statChanges`, and the client draws its arrows — one source of truth, everything
     * downstream of it fed the normal way. The string is built from `showdownId`s and clamped ints,
     * so nothing player-controlled can reach the eval.
     *
     * ### Ordering
     *
     * [PokemonBattle.writeShowdownAction] appends to the same input queue the battle start went
     * through, so the boost lands after the sim has started and before any turn-1 choice — the
     * "start of battle" a player would describe. Only the LEAD is applied: stages belong to the
     * Pokémon on the field, and if the party was reordered between waves the carried entry simply
     * finds nobody and expires, which is also what PokéRogue does to a mon that left the field.
     */
    fun applyTo(battle: PokemonBattle, playerId: UUID, run: RunState) {
        if (run.carriedBoosts.isEmpty()) return
        val lead = battle.actors.firstOrNull { it.uuid == playerId }
            ?.activePokemon?.firstOrNull()?.battlePokemon ?: return
        val stages = run.carriedBoosts[lead.effectedPokemon.uuid] ?: return
        val table = stages.entries
            .filter { (stat, stage) -> stat.matches(Regex("[a-z]+")) && stage != 0 }
            .joinToString(",") { (stat, stage) -> "$stat:${stage.coerceIn(-6, 6)}" }
        if (table.isEmpty()) return
        battle.writeShowdownAction(">eval battle.boost({$table}, p1active)")
        log.info(
            "roguelite: re-applied carried stages {{{}}} to {}'s lead at wave {}",
            table, playerId, run.wave,
        )
    }
}
