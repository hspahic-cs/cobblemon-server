package com.cobblemonfeedback

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Maps Minecraft UUIDs to short opaque "reporter" IDs for use in public issue
 * bodies. Now that the repo is public, putting raw player UUIDs and usernames
 * into issues would expose more than necessary — both are looked up via
 * Mojang's API, and a username + UUID pair tied to a public bug report is
 * cumulatively more identifying than either alone.
 *
 * Strategy:
 *   anon-id = first 8 hex chars of HMAC-SHA256(secret, uuid)
 *
 * The secret lives in `runtime/config.json` (per-instance, not shipped via
 * deploy, never in repo) so dev and prod produce different anon-IDs for the
 * same player. Resetting the secret rotates all IDs.
 *
 * Reverse lookup: an in-memory `Map<anonId, (uuid, name)>` is populated as
 * /feedback is invoked. Survives until server restart. The append-only audit
 * log at `runtime/audit.log` is the durable backing store — maintainers SSH
 * in and grep when the in-memory map is cold.
 */
internal object Anonymizer {
    /** anon-id (lowercase, no prefix) → most-recent (uuid, displayName) */
    private val cache: MutableMap<String, Pair<UUID, String>> = ConcurrentHashMap()

    /**
     * Compute the public reporter ID for a UUID. Records the (uuid, name)
     * mapping in the in-memory cache so /feedback whois works without
     * touching the audit log.
     */
    fun reporterId(uuid: UUID, displayName: String): String {
        val secret = CobblemonFeedback.config.anonHmacSecret
        require(secret.isNotBlank()) {
            "anonHmacSecret is empty — config should auto-generate one on first boot"
        }
        val mac = Mac.getInstance("HmacSHA256").apply {
            init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        }
        val hex = mac.doFinal(uuid.toString().toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .substring(0, 8)
        cache[hex] = uuid to displayName
        return "anon-$hex"
    }

    /**
     * Reverse lookup. Accepts either "anon-7f3e2c1b" or just "7f3e2c1b".
     * Only finds players who have submitted /feedback since the server last
     * started (or since their hash was computed). For older entries, fall
     * back to the audit log.
     */
    fun lookup(reporterId: String): Pair<UUID, String>? {
        val key = reporterId.removePrefix("anon-").lowercase()
        return cache[key]
    }
}
