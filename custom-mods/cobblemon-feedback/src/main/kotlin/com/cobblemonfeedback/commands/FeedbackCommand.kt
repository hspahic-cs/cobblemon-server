package com.cobblemonfeedback.commands

import com.cobblemonfeedback.CobblemonFeedback
import com.cobblemonfeedback.GitHubIssuesClient
import com.cobblemonfeedback.MetadataCollector
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * `/feedback bug <text>` and `/feedback suggest <text>`.
 *
 * Usable by all players (no permission level). Per-player cooldown enforced
 * via `FeedbackConfig.cooldownSeconds`.
 *
 * The actual GitHub POST runs on a background thread so the player isn't
 * blocked by network latency.
 */
internal object FeedbackCommand {
    private val log = LoggerFactory.getLogger("cobblemon-feedback/command")

    /** UUID → epoch-seconds of last submission. */
    private val lastSubmit: MutableMap<UUID, Long> = ConcurrentHashMap()

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        val root = Commands.literal("feedback")
            .then(
                Commands.literal("bug")
                    .then(textArg { ctx ->
                        submit(ctx.source, "bug", StringArgumentType.getString(ctx, "text"))
                    })
                    .executes { ctx ->
                        usage(ctx.source, "bug")
                        1
                    }
            )
            .then(
                Commands.literal("suggest")
                    .then(textArg { ctx ->
                        submit(ctx.source, "suggest", StringArgumentType.getString(ctx, "text"))
                    })
                    .executes { ctx ->
                        usage(ctx.source, "suggest")
                        1
                    }
            )
            .then(
                // Op-only reverse lookup. Maintainer triages a public issue,
                // sees `Reporter: anon-7f3e2c1b`, runs this to recover the
                // player's name + UUID. In-memory cache only — for older
                // submissions, grep config/cobblemon-feedback/runtime/audit.log.
                Commands.literal("whois")
                    .requires { it.hasPermission(2) }
                    .then(
                        Commands.argument("id", StringArgumentType.word())
                            .executes { ctx ->
                                whois(ctx.source, StringArgumentType.getString(ctx, "id"))
                            }
                    )
                    .executes { ctx ->
                        ctx.source.sendSystemMessage(
                            Component.literal("Usage: /feedback whois <anon-id>")
                                .withStyle(ChatFormatting.GRAY)
                        )
                        1
                    }
            )
            .executes { ctx ->
                usage(ctx.source, null)
                1
            }
        dispatcher.register(root)
    }

    private fun whois(source: CommandSourceStack, reporterId: String): Int {
        val match = com.cobblemonfeedback.Anonymizer.lookup(reporterId)
        if (match != null) {
            val (uuid, name) = match
            source.sendSystemMessage(
                Component.literal("$reporterId → $name ($uuid)").withStyle(ChatFormatting.AQUA)
            )
        } else {
            source.sendSystemMessage(
                Component.literal(
                    "No active mapping for $reporterId — grep config/cobblemon-feedback/runtime/audit.log."
                ).withStyle(ChatFormatting.GRAY)
            )
        }
        return 1
    }

    private fun textArg(handler: (com.mojang.brigadier.context.CommandContext<CommandSourceStack>) -> Int) =
        Commands.argument("text", StringArgumentType.greedyString()).executes { ctx -> handler(ctx) }

    private fun usage(source: CommandSourceStack, type: String?) {
        val msg = when (type) {
            "bug" -> "Usage: §e/feedback bug <description>§r — file a bug report"
            "suggest" -> "Usage: §e/feedback suggest <description>§r — file a suggestion"
            else -> "Usage:\n  §e/feedback bug <description>§r\n  §e/feedback suggest <description>§r"
        }
        source.sendSystemMessage(Component.literal(msg.replace("§e", "")).withStyle(ChatFormatting.GRAY))
    }

    private fun submit(source: CommandSourceStack, type: String, text: String): Int {
        val player = source.player
        if (player == null) {
            source.sendSystemMessage(Component.literal("§c/feedback must be run by a player.")
                .withStyle(ChatFormatting.RED))
            return 0
        }
        if (text.isBlank()) {
            usage(source, type)
            return 0
        }

        val cfg = CobblemonFeedback.config
        if (cfg.cooldownSeconds > 0) {
            val last = lastSubmit[player.uuid] ?: 0L
            val elapsed = System.currentTimeMillis() / 1000 - last
            if (elapsed < cfg.cooldownSeconds) {
                val waitFor = cfg.cooldownSeconds - elapsed
                source.sendSystemMessage(
                    Component.literal("§ePlease wait ${waitFor}s before submitting another report.")
                        .withStyle(ChatFormatting.YELLOW)
                )
                return 0
            }
        }

        if (cfg.githubToken.isBlank() || cfg.githubRepo.isBlank()) {
            source.sendSystemMessage(
                Component.literal("§cFeedback isn't configured on this server (missing GitHub token).")
                    .withStyle(ChatFormatting.RED)
            )
            log.warn("/feedback called but GitHub config is missing")
            return 0
        }

        // Build metadata synchronously on the server thread (we're touching server
        // state — party, level, log file). Then dispatch the HTTP call to a worker
        // thread so we don't block the tick.
        val reporterId = com.cobblemonfeedback.Anonymizer.reporterId(player.uuid, player.gameProfile.name)
        val title = buildTitle(type, text, reporterId)
        val body = MetadataCollector.build(type, text, player)
        // Append to the audit log so maintainers can recover the (anon-id → uuid +
        // name) mapping after a server restart, when the in-memory cache is empty.
        com.cobblemonfeedback.AuditLog.append(reporterId, player.uuid, player.gameProfile.name, type)
        val labels = listOf(
            when (type) {
                "bug" -> cfg.bugLabel
                "suggest" -> cfg.suggestionLabel
                else -> "feedback"
            }
        )

        // Optimistic feedback to the player while the HTTP call is in flight.
        source.sendSystemMessage(
            Component.literal("§7Submitting your $type to the dev team…").withStyle(ChatFormatting.GRAY)
        )

        Thread({
            val url = GitHubIssuesClient.createIssue(title, body, labels)
            val response = if (url != null) {
                "§a✓ Submitted! §7$url"
            } else {
                "§cSubmission failed. Server log has details — please ping a dev."
            }
            // Send the response back on the server thread for chat-system safety.
            player.server.execute {
                if (player.isAlive) {
                    source.sendSystemMessage(Component.literal(response))
                }
            }
        }, "feedback-poster").apply { isDaemon = true }.start()

        if (cfg.cooldownSeconds > 0) {
            lastSubmit[player.uuid] = System.currentTimeMillis() / 1000
        }
        return 1
    }

    private fun buildTitle(type: String, text: String, reporterId: String): String {
        val prefix = if (type == "bug") "[bug]" else "[suggest]"
        // Trim the body to a reasonable issue title — first line, max 80 chars.
        val firstLine = text.lineSequence().firstOrNull().orEmpty().take(80)
        // Reporter ID instead of username — title is publicly visible and we
        // don't want raw player names in it.
        return "$prefix $firstLine — by $reporterId"
    }
}
