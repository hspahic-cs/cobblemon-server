package com.cobblemonserver.pokeai.bridge

import com.cobblemonserver.pokeai.BridgeConfig
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class BridgeUnavailable(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

data class PickResponse(val moveChoice: String, val searchMs: Int)

class BridgeClient(
    // Force HTTP/1.1 — Java HttpClient defaults to HTTP/2, which sends an
    // h2c upgrade preamble that uvicorn doesn't support. Result: uvicorn
    // ignores the upgrade and the body never makes it through (422 with
    // empty body on every POST).
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .connectTimeout(Duration.ofMillis(2000))
        .build(),
) {
    private val gson = Gson()

    fun pick(
        battleId: String,
        requestJson: JsonObject,
        logLines: List<String>,
        gymSide: String,
        opponentTeamPacked: String? = null,
        forceSwitch: Boolean = false,
        temperature: Double = 0.0,
    ): PickResponse {
        val body = JsonObject().apply {
            add("request_json", requestJson)
            add("log_lines", gson.toJsonTree(logLines))
            addProperty("gym_side", gymSide)
            // authoritative pivot/faint signal — the |request| in the log can
            // be stale on Volt Switch/U-turn/Teleport KO turns
            addProperty("force_switch", forceSwitch)
            addProperty("pokemon_format", BridgeConfig.pokemonFormat)
            addProperty("generation", BridgeConfig.generation)
            addProperty("smogon_stats_format", BridgeConfig.smogonStatsFormat)
            addProperty("search_time_ms", BridgeConfig.searchTimeMs)
            // Per-gym opponent-fallibility temperature (0 = perfect opponent).
            // Higher = the search assumes the player misplays and punishes greed.
            addProperty("temperature", temperature)
            // Player's full team — lets the bridge search with perfect
            // information instead of guessing sets from Smogon stats.
            opponentTeamPacked?.let { addProperty("opponent_team_packed", it) }
        }
        val httpReq = HttpRequest.newBuilder(URI.create("${BridgeConfig.url}/battles/$battleId/pick"))
            .timeout(Duration.ofMillis(BridgeConfig.timeoutMs))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
            .build()

        val httpResp = try {
            httpClient.send(httpReq, HttpResponse.BodyHandlers.ofString())
        } catch (e: Exception) {
            throw BridgeUnavailable("bridge request failed: ${e.message}", e)
        }
        if (httpResp.statusCode() != 200) {
            throw BridgeUnavailable("bridge returned ${httpResp.statusCode()}: ${httpResp.body()}")
        }
        val json = gson.fromJson(httpResp.body(), JsonObject::class.java)
        return PickResponse(
            moveChoice = json.get("move_choice").asString,
            searchMs = json.get("search_ms")?.asInt ?: 0,
        )
    }
}
