package com.cobblemonfeedback

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore
import net.minecraft.server.level.ServerPlayer
import net.neoforged.fml.ModList
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Builds the markdown body for a feedback issue. Captures:
 *   - player username + UUID
 *   - submitted timestamp + server version
 *   - dimension + biome + coords
 *   - server TPS (best-effort)
 *   - Cobblemon party (species + level)
 *   - recent chat buffer
 *   - tail of the server log
 *   - loaded mod count + a short summary
 */
internal object MetadataCollector {
    private val log = LoggerFactory.getLogger("cobblemon-feedback/metadata")

    fun build(
        type: String,
        text: String,
        player: ServerPlayer,
    ): String {
        val now = ZonedDateTime.now()
        val server = player.server
        val level = player.level()
        val coords = player.position()
        val biome = level.getBiome(player.blockPosition()).unwrapKey()
            .map { it.location().toString() }.orElse("unknown")
        val party = runCatching { partyForPlayer(player) }.getOrElse { e ->
            log.debug("party fetch failed: {}", e.message)
            "<unable to read party: ${e.message}>"
        }
        val tps = runCatching { tpsSummary(server) }.getOrElse { "<unable to read tps>" }
        val recentChat = RecentChatBuffer.snapshot().joinToString("\n") { line ->
            "[${line.timestamp}] <${line.player}> ${line.message}"
        }.ifBlank { "(no recent chat)" }
        val logTail = runCatching { tailServerLog(CobblemonFeedback.config.logTailLines) }.getOrElse {
            "<unable to read server log: ${it.message}>"
        }
        val modCount = ModList.get().mods.size

        return buildString {
            appendLine("**Type:** ${type.replaceFirstChar { it.uppercase() }}")
            appendLine("**Player:** ${player.gameProfile.name} (`${player.uuid}`)")
            appendLine("**Submitted:** ${DATE_FMT.format(now)}")
            appendLine("**Server version:** ${runCatching { Files.readString(Path.of("/opt/cobblemon-prod/.deployed_version")).trim() }.getOrElse { Files.exists(Paths.get("/opt/cobblemon-dev/.deployed_version")).let { onDev -> if (onDev) Files.readString(Path.of("/opt/cobblemon-dev/.deployed_version")).trim() else "unknown" } }}")
            appendLine("**Mods loaded:** $modCount")
            appendLine("**Dimension:** ${level.dimension().location()}")
            appendLine("**Biome:** $biome")
            appendLine("**Coords:** ${"%.1f".format(coords.x)}, ${"%.1f".format(coords.y)}, ${"%.1f".format(coords.z)}")
            appendLine("**TPS:**")
            appendLine("```")
            appendLine(tps)
            appendLine("```")
            appendLine()
            appendLine("## Description")
            appendLine()
            appendLine(text)
            appendLine()
            appendLine("## Pokémon party")
            appendLine("```")
            appendLine(party)
            appendLine("```")
            appendLine()
            appendLine("## Recent chat (last ${CobblemonFeedback.config.chatBufferSize} lines)")
            appendLine("```")
            appendLine(recentChat)
            appendLine("```")
            appendLine()
            appendLine("## Server log (tail ${CobblemonFeedback.config.logTailLines} lines)")
            appendLine("```")
            appendLine(logTail)
            appendLine("```")
            appendLine()
            appendLine("---")
            appendLine("*Submitted via in-game `/feedback $type` by ${player.gameProfile.name}*")
        }
    }

    private val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")

    private fun partyForPlayer(player: ServerPlayer): String {
        val party: PlayerPartyStore = Cobblemon.storage.getParty(player)
        if (party.size() == 0) return "(empty)"
        return party.toGappyList().mapIndexedNotNull { i, mon ->
            mon?.let { "${i + 1}. ${it.species.translatedName.string} L${it.level}" }
        }.joinToString("\n")
    }

    /**
     * Best-effort TPS summary. NeoForge exposes recent tick samples on the server;
     * we average the last ~100 ticks (≈5 seconds at 20 TPS).
     */
    private fun tpsSummary(server: net.minecraft.server.MinecraftServer): String {
        val tickTimes = server.tickTimesNanos
        if (tickTimes == null || tickTimes.isEmpty()) return "(unavailable)"
        val avgNanos = tickTimes.average()
        val avgMs = avgNanos / 1_000_000.0
        val tps = (1_000.0 / avgMs).coerceAtMost(20.0)
        return "Overall: %.1f TPS (%.2f ms/tick avg, last %d ticks)".format(tps, avgMs, tickTimes.size)
    }

    /**
     * Reads the last [n] lines of the running server's log. Looks for the standard
     * NeoForge log location relative to the working directory.
     */
    private fun tailServerLog(n: Int): String {
        val candidates = listOf(
            Paths.get("logs/latest.log"),
            Paths.get("logs", "latest.log"),
        )
        val log = candidates.firstOrNull { Files.exists(it) } ?: return "(server log not found)"
        return Files.readAllLines(log).takeLast(n).joinToString("\n")
    }
}
