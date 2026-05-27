package com.cobblemonfeedback

import com.cobblemonfeedback.internal.ConfigPaths
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path

/**
 * Configuration for the GitHub Issues integration.
 *
 *   githubRepo       e.g. "hspahic-cs/cobblemon-server"
 *   githubToken      fine-grained PAT with issues:write on the repo above
 *   bugLabel         label applied to bug reports
 *   suggestionLabel  label applied to suggestions
 *   cooldownSeconds  per-player rate limit (0 disables)
 *   chatBufferSize   how many recent chat lines to include in each report
 *   logTailLines     how many recent server-log lines to include
 *
 * The token is sensitive — keep config.json out of public-readable directories
 * and never commit it to the repo. The repo's modpack/server-overrides/config/
 * convention won't ship config.json (it's runtime, not authored — see below).
 */
data class FeedbackConfig(
    val githubRepo: String = "",
    val githubToken: String = "",
    val bugLabel: String = "bug",
    val suggestionLabel: String = "suggestion",
    val cooldownSeconds: Int = 60,
    val chatBufferSize: Int = 20,
    val logTailLines: Int = 30,
) {
    companion object {
        private val log = LoggerFactory.getLogger("cobblemon-feedback/config")
        private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

        // Token-bearing config is RUNTIME (per-instance secret) — never shipped via deploy.
        // See docs/design/mod-state-vs-config.md.
        private const val FILE_NAME = "config.json"

        fun load(configDir: Path): FeedbackConfig {
            val file = ConfigPaths.runtime(configDir, FILE_NAME)
            Files.createDirectories(file.parent)
            if (!Files.exists(file)) {
                val defaults = FeedbackConfig()
                Files.writeString(file, gson.toJson(defaults))
                log.warn(
                    "Wrote default config to {} — set githubRepo + githubToken before /feedback will work.",
                    file,
                )
                return defaults
            }
            return try {
                gson.fromJson(Files.readString(file), FeedbackConfig::class.java) ?: FeedbackConfig()
            } catch (e: Exception) {
                log.error("Failed to parse $file; using empty defaults", e)
                FeedbackConfig()
            }
        }
    }
}
