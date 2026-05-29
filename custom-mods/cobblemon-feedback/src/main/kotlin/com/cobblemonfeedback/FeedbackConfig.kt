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
 *   r2Endpoint        Cloudflare R2 S3-API endpoint, e.g.
 *                     "https://<account-id>.r2.cloudflarestorage.com". Blank
 *                     disables screenshot upload (issues are still filed
 *                     text-only).
 *   r2Bucket          R2 bucket name, e.g. "cobblemon-bugs".
 *   r2AccessKeyId     R2 API token's access key ID.
 *   r2SecretAccessKey R2 API token's secret. Never log this.
 *   r2PublicUrlBase   Prefix prepended to the object key to form the URL we
 *                     paste into the issue body. For the managed dev domain:
 *                     "https://pub-<hash>.r2.dev". For a custom domain:
 *                     "https://screenshots.example.com". No trailing slash.
 *
 * The GitHub token, HMAC secret, and R2 secret are sensitive — keep config.json
 * out of public-readable directories and never commit it to the repo. The
 * repo's modpack/server-overrides/config/ convention won't ship config.json
 * (it's runtime, not authored — see docs/design/mod-state-vs-config.md).
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
    val r2Endpoint: String = "",
    val r2Bucket: String = "",
    val r2AccessKeyId: String = "",
    val r2SecretAccessKey: String = "",
    val r2PublicUrlBase: String = "",
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
