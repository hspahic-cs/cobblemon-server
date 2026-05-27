package com.cobblemonfeedback

import com.google.gson.Gson
import com.google.gson.JsonObject
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Tiny GitHub REST API client. Uses java.net.http (JDK 11+) — no external deps.
 *
 * Endpoint: POST /repos/{owner}/{repo}/issues
 * Auth: Bearer <fine-grained PAT>
 *
 * Returns the issue's HTML URL on success, or null on failure (logged).
 */
internal object GitHubIssuesClient {
    private val log = LoggerFactory.getLogger("cobblemon-feedback/github")
    private val gson = Gson()
    private val http: HttpClient by lazy {
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
    }

    /** Synchronous HTTP. Caller should run on a worker thread. */
    fun createIssue(title: String, body: String, labels: List<String>): String? {
        val cfg = CobblemonFeedback.config
        if (cfg.githubRepo.isBlank() || cfg.githubToken.isBlank()) {
            log.warn("githubRepo/githubToken not configured — skipping issue create")
            return null
        }
        val url = "https://api.github.com/repos/${cfg.githubRepo}/issues"
        val payload = JsonObject().apply {
            addProperty("title", title)
            addProperty("body", body)
            add("labels", gson.toJsonTree(labels))
        }
        val req = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(15))
            .header("Accept", "application/vnd.github+json")
            .header("Authorization", "Bearer ${cfg.githubToken}")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "cobblemon-feedback/1.0")
            .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(payload)))
            .build()

        return try {
            val resp = http.send(req, HttpResponse.BodyHandlers.ofString())
            if (resp.statusCode() in 200..299) {
                val body = gson.fromJson(resp.body(), JsonObject::class.java)
                body.get("html_url")?.asString
            } else {
                log.warn("GitHub returned ${resp.statusCode()}: ${resp.body().take(500)}")
                null
            }
        } catch (e: Exception) {
            log.warn("HTTP error creating issue: ${e.message}", e)
            null
        }
    }
}
