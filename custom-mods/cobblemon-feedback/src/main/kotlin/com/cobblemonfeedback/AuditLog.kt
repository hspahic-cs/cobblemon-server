package com.cobblemonfeedback

import com.cobblemonfeedback.internal.ConfigPaths
import net.neoforged.fml.loading.FMLPaths
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Append-only log of every /feedback invocation, mapping the public
 * reporter ID back to the player's UUID and display name. Lives on the
 * server filesystem at:
 *
 *   config/cobblemon-feedback/runtime/audit.log
 *
 * Maintainers SSH in and grep when [Anonymizer]'s in-memory cache is cold
 * (after a server restart, or when looking up a player who hasn't reported
 * since startup):
 *
 *   $ grep "anon-7f3e2c1b" /opt/cobblemon-{dev,prod}/config/cobblemon-feedback/runtime/audit.log
 *
 * The log is **runtime** (per-instance, not shipped via deploy, never
 * committed). It contains player UUIDs and usernames — the same data we
 * deliberately keep out of public issue bodies — so it must not leak.
 */
internal object AuditLog {
    private val log = LoggerFactory.getLogger("cobblemon-feedback/audit")
    private val ts = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX")

    fun append(reporterId: String, uuid: UUID, displayName: String, type: String) {
        val configDir = FMLPaths.CONFIGDIR.get()
        val path = ConfigPaths.runtime(configDir, "audit.log")
        try {
            Files.createDirectories(path.parent)
            val line = "${ts.format(ZonedDateTime.now())}\t$reporterId\t$uuid\t$displayName\t$type\n"
            Files.writeString(
                path,
                line,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND,
            )
        } catch (e: Exception) {
            // Failing to write the audit log shouldn't block /feedback. Worst
            // case the maintainer can't reverse-look-up this submission.
            log.warn("Failed to append audit-log entry for {}: {}", reporterId, e.message)
        }
    }
}
