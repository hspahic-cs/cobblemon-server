package com.cobblemonfeedback

import com.cobblemonfeedback.internal.ConfigPaths
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.security.SecureRandom

/**
 * Configuration for the GitHub Issues integration.
 *
 *   githubRepo        e.g. "hspahic-cs/cobblemon-server"
 *   githubToken       fine-grained PAT with issues:write on the repo above
 *   bugLabel          label applied to bug reports
 *   suggestionLabel   label applied to suggestions
 *   cooldownSeconds   per-player rate limit (0 disables)
 *   chatBufferSize    how many recent chat lines to include in each report
 *   logTailLines      how many recent server-log lines to include
 *   anonHmacSecret    per-instance secret for the HMAC that produces public
 *                     reporter IDs (see [Anonymizer]). Auto-generated on
 *                     first boot if blank. Resetting it rotates all anon-IDs.
 *
 * Both the token and the HMAC secret are sensitive — keep config.json out of
 * public-readable directories and never commit it to the repo. The repo's
 * modpack/server-overrides/config/ convention won't ship config.json (it's
 * runtime, not authored — see docs/design/mod-state-vs-config.md).
 */
data class FeedbackConfig(
    val githubRepo: String = "",
    val githubToken: String = "",
    val bugLabel: String = "bug",
    val suggestionLabel: String = "suggestion",
    val cooldownSeconds: Int = 60,
    val chatBufferSize: Int = 20,
    val logTailLines: Int = 30,
    val anonHmacSecret: String = "",
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
                val defaults = FeedbackConfig(anonHmacSecret = generateHmacSecret())
                Files.writeString(file, gson.toJson(defaults))
                log.warn(
                    "Wrote default config to {} — set githubRepo + githubToken before /feedback will work. " +
                        "anonHmacSecret was auto-generated.",
                    file,
                )
                return defaults
            }
            val parsed = try {
                gson.fromJson(Files.readString(file), FeedbackConfig::class.java) ?: FeedbackConfig()
            } catch (e: Exception) {
                log.error("Failed to parse $file; using empty defaults", e)
                FeedbackConfig()
            }
            // Backfill anonHmacSecret if missing (config predating PII anonymization).
            // Persist the freshly-generated secret so later restarts produce stable IDs.
            return if (parsed.anonHmacSecret.isBlank()) {
                val withSecret = parsed.copy(anonHmacSecret = generateHmacSecret())
                try {
                    Files.writeString(file, gson.toJson(withSecret))
                    log.info("Generated and persisted anonHmacSecret in {}", file)
                } catch (e: Exception) {
                    log.warn(
                        "Generated anonHmacSecret but couldn't persist it to {}; " +
                            "anon-IDs will rotate every restart until the file is writable: {}",
                        file, e.message,
                    )
                }
                withSecret
            } else {
                parsed
            }
        }

        /** 32 random bytes, hex-encoded — 256 bits of entropy. */
        private fun generateHmacSecret(): String {
            val bytes = ByteArray(32)
            SecureRandom().nextBytes(bytes)
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}
