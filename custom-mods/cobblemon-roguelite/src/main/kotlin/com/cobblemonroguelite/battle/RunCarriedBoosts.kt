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
    /**
     * Read the field's surviving stages the moment the victory event arrives — NOT a tick later.
     *
     * The first wiring deferred the whole capture into `server.execute`, and it read empty every
     * single time (playtest, 2026-07-31: the carry "not happening at all"): by the next tick
     * Cobblemon's end-of-battle sequence has recalled the active positions, so `activePokemon` is
     * a list of nobody. This snapshot runs synchronously at event receipt, while the field still
     * exists; it is a pure read of the mirror maps, safe off-thread, and the caller stores the
     * result into the run on the server thread as before.
     *
     * [lastField] — the §2.10 field tracker's per-tick record — is the belt-and-braces: if the
     * active list has already been emptied even at event time, whoever it last reported as on the
     * field is who the stages belong to, read from the full battle team instead.
     */
    /**
     * Third wiring, and this one reads a thing that is actually written. `BattlePokemon.statChanges`
     * is a CLIENT-DISPLAY mirror — nothing on the server ever writes it, so both earlier capture
     * attempts read an empty map with perfect reliability. The server-side truth is the battle's own
     * protocol stream ([PokemonBattle.showdownMessages], public and complete), so the stages are
     * reconstructed the way the client itself learns them: replay `|-boost|`/`|-unboost|` for slot
     * `p1a`, resetting on switch/drag/faint — Showdown's own reset points.
     *
     * Keyed to the §2.10 field tracker's last-reported lead: with lead continuity, the Pokémon that
     * ends the wave on the field is the one the next wave sends out, which is the only member
     * [applyTo] would ever apply to anyway.
     */
    fun snapshot(battle: PokemonBattle, playerId: UUID, lastField: List<UUID>): Map<UUID, Map<String, Int>> {
        // battleLog, not showdownMessages: a REAL saved production log (battle_logs/98126a41…)
        // proves battleLog accumulates the full per-line history and that idents carry the
        // Pokémon's UUID (`|switch|p1a: <uuid>|…`) — both facts this parser depends on, both now
        // pinned by [replay]'s unit test against those exact lines.
        val lines = battle.battleLog.ifEmpty { battle.showdownMessages }
        val (occupantUuid, stages) = replay(lines)
        if (stages.isEmpty()) return emptyMap()
        val owner = occupantUuid ?: lastField.firstOrNull() ?: return emptyMap()
        return mapOf(owner to stages)
    }

    /**
     * Replay the protocol history for slot `p1a`: who ended up in it, at what stages. Pure and
     * `internal` so the parser is testable against real captured log lines with no battle object.
     */
    internal fun replay(lines: List<String>): Pair<UUID?, Map<String, Int>> {
        var stages = mutableMapOf<String, Int>()
        var occupant: UUID? = null
        for (raw in lines) {
            val parts = raw.trim().split('|')
            if (parts.size < 2) continue
            val ident = parts.getOrNull(2) ?: ""
            val p1a = ident.startsWith("p1a")
            when (parts[1]) {
                // A new occupant starts clean; the ident's payload after "p1a: " is the UUID.
                "switch", "drag" -> if (p1a) {
                    stages = mutableMapOf()
                    occupant = runCatching { UUID.fromString(ident.substringAfter(": ").trim()) }.getOrNull()
                }
                "faint" -> if (p1a) {
                    stages = mutableMapOf()
                    occupant = null
                }
                "-boost" -> if (p1a) bump(stages, parts, +1)
                "-unboost" -> if (p1a) bump(stages, parts, -1)
                "-clearboost" -> if (p1a) stages.clear()
                "-clearallboost" -> stages.clear()
                "-clearnegativeboost" -> if (p1a) stages.entries.removeIf { it.value < 0 }
                // -copyboost/-swapboost/-setboost are rare enough (Heart Swap, Topsy-Turvy) that a
                // wrong carry from ignoring them is a curiosity, not a loop; left unhandled by name.
            }
        }
        return occupant to stages.filterValues { it != 0 }.mapValues { it.value.coerceIn(-6, 6) }
    }

    private fun bump(stages: MutableMap<String, Int>, parts: List<String>, sign: Int) {
        val stat = parts.getOrNull(3) ?: return
        val amount = parts.getOrNull(4)?.trim()?.toIntOrNull() ?: return
        stages[stat] = (stages[stat] ?: 0) + sign * amount
    }

    /** Store a [snapshot] into [run], on the server thread. Split from the read — see [snapshot]. */
    fun store(run: RunState, carried: Map<UUID, Map<String, Int>>) {
        run.carriedBoosts.clear()
        run.carriedBoosts.putAll(carried)
        if (carried.isNotEmpty()) {
            log.info("roguelite: carried {} boosted member(s) out of wave {}", carried.size, run.wave)
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
