package com.cobblemonranked.rental

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.CompletableFuture

/**
 * Fetches a team export from pokepast.es — the paste site the Showdown teambuilder ecosystem
 * uses — so `/ranked draft create <name> <url>` can skip the book & quill (Minecraft truncates a
 * clipboard paste to one book page, which made the book flow miserable for a full team).
 *
 * Deliberately host-locked to pokepast.es: the server sits on a home network, so a free-form URL
 * fetch from a player command would be an SSRF hole. [normalize] rejects anything else before a
 * request is ever built, and redirects are not followed.
 */
object PokePasteFetcher {

    private const val HOST = "pokepast.es"
    private val PASTE_ID = Regex("^[0-9a-fA-F]{4,32}$")

    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()

    /**
     * Turns any accepted pokepast.es link (`https://pokepast.es/<id>`, with or without `/raw`,
     * http or https, stray whitespace) into the canonical raw URL — or null if it isn't a
     * pokepast.es paste link at all.
     */
    fun normalize(raw: String): String? {
        val uri = runCatching { URI(raw.trim()) }.getOrNull() ?: return null
        if (uri.host != HOST) return null
        if (uri.scheme != "https" && uri.scheme != "http") return null
        val id = uri.path.orEmpty().trim('/').removeSuffix("/raw").trim('/')
        if (!PASTE_ID.matches(id)) return null
        return "https://$HOST/$id/raw"
    }

    /** Async fetch of a [normalize]d URL's paste text. Completes exceptionally on any failure;
     *  callers hop back onto the server thread before touching game state. */
    fun fetch(normalizedUrl: String): CompletableFuture<String> {
        val request = HttpRequest.newBuilder(URI(normalizedUrl))
            .timeout(Duration.ofSeconds(8))
            .header("User-Agent", "cobblemon-ranked-drafts")
            .GET()
            .build()
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply { response ->
                if (response.statusCode() != 200)
                    throw IllegalStateException("pokepast.es answered HTTP ${response.statusCode()}")
                response.body().takeIf { it.isNotBlank() }
                    ?: throw IllegalStateException("pokepast.es returned an empty paste")
            }
    }
}
