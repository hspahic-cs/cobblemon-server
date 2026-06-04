package com.cobblemonserver.pokeai.ai

import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.api.battles.model.ai.BattleAI
import com.cobblemon.mod.common.battles.ActiveBattlePokemon
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

        val response = try {
            client.pick(battleId, requestJson, logLines, gymSide)
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
        return MoveActionResponse(move.id, null, gimmick)
    }

    private fun normalize(s: String): String =
        s.lowercase().filter { it.isLetterOrDigit() }

    companion object {
        private val log = LoggerFactory.getLogger("cobblemon_poke_ai/ai")
    }
}
