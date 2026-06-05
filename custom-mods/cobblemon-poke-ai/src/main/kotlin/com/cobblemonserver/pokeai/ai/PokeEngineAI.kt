package com.cobblemonserver.pokeai.ai

import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.api.battles.model.ai.BattleAI
import com.cobblemon.mod.common.battles.ActiveBattlePokemon
import com.cobblemon.mod.common.battles.BattleRegistry.packTeam
import com.cobblemon.mod.common.battles.BattleSide
import com.cobblemon.mod.common.battles.MoveActionResponse
import com.cobblemon.mod.common.battles.PassActionResponse
import com.cobblemon.mod.common.battles.ShowdownActionResponse
import com.cobblemon.mod.common.battles.ShowdownMoveset
import com.cobblemon.mod.common.battles.SwitchActionResponse
import com.cobblemon.mod.common.battles.ai.StrongBattleAI
import com.cobblemonserver.pokeai.bridge.BridgeClient
import com.cobblemonserver.pokeai.bridge.BridgeUnavailable
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.slf4j.LoggerFactory

/**
 * BattleAI implementation that delegates move selection to the local
 * poke-engine-bridge service. On any bridge error, falls back to
 * StrongBattleAI so the battle can continue.
 *
 * Registered as `"pe"` via [PokeEngineAIConfig].
 *
 * Stateless: each `choose()` call sends the full Showdown event log to
 * date. The bridge rebuilds its `Battle` object from scratch, so no
 * coordination of "what have we sent" is needed here.
 */
class PokeEngineAI(
    private val client: BridgeClient = BridgeClient(),
    private val fallback: BattleAI = StrongBattleAI(5),
) : BattleAI {

    override fun choose(
        activeBattlePokemon: ActiveBattlePokemon,
        battle: PokemonBattle,
        aiSide: BattleSide,
        moveset: ShowdownMoveset?,
        forceSwitch: Boolean,
    ): ShowdownActionResponse {
        val battleId = battle.battleId.toString()

        val requestJson = latestRequestJson(battle)
            ?: return delegateToFallback(activeBattlePokemon, battle, aiSide, moveset, forceSwitch,
                "no |request| line in showdownMessages")

        val gymSide = aiSideTag(aiSide, battle)
            ?: return delegateToFallback(activeBattlePokemon, battle, aiSide, moveset, forceSwitch,
                "could not determine gym side (p1/p2)")

        val logLines = battle.showdownMessages.toList()

        // Perfect information: the player's full team, in Cobblemon's packed
        // format. The bridge uses it instead of guessing sets from Smogon
        // usage stats — random/casual teams confuse that inference badly.
        // Best-effort: on any serialization hiccup, send nothing and let the
        // bridge fall back to set sampling.
        val opponentTeamPacked = runCatching {
            aiSide.getOppositeSide().actors.flatMap { it.pokemonList }.packTeam()
        }.onFailure {
            log.warn("could not pack opposing team for battle={}: {}", battleId, it.message)
        }.getOrNull()

        val response = try {
            client.pick(battleId, requestJson, logLines, gymSide, opponentTeamPacked)
        } catch (e: BridgeUnavailable) {
            log.warn("bridge unavailable for battle={} — falling back to StrongBattleAI: {}", battleId, e.message)
            return delegateToFallback(activeBattlePokemon, battle, aiSide, moveset, forceSwitch, e.message ?: "bridge error")
        }

        return parseChoice(response.moveChoice, activeBattlePokemon, moveset)
            ?: delegateToFallback(activeBattlePokemon, battle, aiSide, moveset, forceSwitch,
                "could not parse move_choice='${response.moveChoice}'")
    }

    private fun delegateToFallback(
        activeBattlePokemon: ActiveBattlePokemon,
        battle: PokemonBattle,
        aiSide: BattleSide,
        moveset: ShowdownMoveset?,
        forceSwitch: Boolean,
        reason: String,
    ): ShowdownActionResponse {
        log.debug("falling back to StrongBattleAI: {}", reason)
        return fallback.choose(activeBattlePokemon, battle, aiSide, moveset, forceSwitch)
    }

    private fun latestRequestJson(battle: PokemonBattle): JsonObject? {
        val raw = battle.showdownMessages.lastOrNull { it.contains("|request|") } ?: return null
        val payload = raw.substringAfter("|request|").trim().trim('\'').takeIf { it.isNotEmpty() }
            ?: return null
        return runCatching { JsonParser.parseString(payload).asJsonObject }.getOrNull()
    }

    /**
     * Resolve which Showdown side ("p1" / "p2") the AI is on.
     *
     * Falls back to looking at the first |player| line in showdownMessages
     * to map the [BattleSide] to a side tag. Returns null if we can't tell —
     * caller will fall back to StrongBattleAI.
     */
    private fun aiSideTag(aiSide: BattleSide, battle: PokemonBattle): String? {
        // BattleSide.actors[].showdownId is a UUID; PS messages give us the p1/p2 tag.
        // The first |request| we sent had a `side.id` field — use that as the source of truth.
        val req = latestRequestJson(battle) ?: return null
        return req.getAsJsonObject("side")?.get("id")?.asString
    }

    private fun parseChoice(
        choice: String,
        activeBattlePokemon: ActiveBattlePokemon,
        moveset: ShowdownMoveset?,
    ): ShowdownActionResponse? {
        val trimmed = choice.trim()
        if (trimmed.startsWith("switch ")) {
            val name = trimmed.removePrefix("switch ").trim()
            val target = activeBattlePokemon.actor.pokemonList
                .firstOrNull { it.canBeSentOut() && normalize(it.effectedPokemon.species.name) == normalize(name) }
                ?: return null
            target.willBeSwitchedIn = true
            return SwitchActionResponse(target.uuid)
        }
        if (trimmed == "pass") return PassActionResponse

        var moveId = trimmed
        var gimmick: String? = null
        when {
            moveId.endsWith("-tera") -> {
                moveId = moveId.removeSuffix("-tera")
                gimmick = ShowdownMoveset.Gimmick.TERASTALLIZATION.id
            }
            moveId.endsWith("-mega") -> {
                moveId = moveId.removeSuffix("-mega")
                gimmick = ShowdownMoveset.Gimmick.MEGA_EVOLUTION.id
            }
        }
        // Sanity-check the chosen move exists in the available moveset; if not, return null
        // so we fall back rather than send something Showdown will reject.
        val move = moveset?.moves?.firstOrNull { normalize(it.id) == normalize(moveId) }
            ?: return null
        if (!move.canBeUsed()) return null
        // MoveActionResponse.isValid requires a targetPnx whenever the move's
        // target list is non-empty — even in singles. Mirror RandomBattleAI:
        // prefer a non-allied target, else any. (Sending null gets the response
        // rejected with IllegalActionChoiceException and the turn is passed.)
        val targets = if (move.mustBeUsed()) null
            else move.target.targetList(activeBattlePokemon)?.takeIf { it.isNotEmpty() }
        if (targets == null) return MoveActionResponse(move.id, null, gimmick)
        val chosenTarget = targets.filter { !it.isAllied(activeBattlePokemon) }.randomOrNull() ?: targets.random()
        return MoveActionResponse(move.id, (chosenTarget as ActiveBattlePokemon).getPNX(), gimmick)
    }

    private fun normalize(s: String): String =
        s.lowercase().filter { it.isLetterOrDigit() }

    companion object {
        private val log = LoggerFactory.getLogger("cobblemon_poke_ai/ai")
    }
}
