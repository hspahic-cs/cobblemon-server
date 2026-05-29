package com.cobblemonfeedback.commands

import com.cobblemonfeedback.CobblemonFeedback
import com.cobblemonfeedback.GitHubIssuesClient
import com.cobblemonfeedback.MetadataCollector
import com.cobblemonfeedback.Payloads
import com.cobblemonfeedback.R2Client
import com.cobblemonfeedback.ScreenshotInbox
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import org.slf4j.LoggerFactory
import java.security.SecureRandom
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

    /** Pending submissions awaiting the player's attach/skip decision. */
    private val pending: MutableMap<String, Pending> = ConcurrentHashMap()
    private val rng = SecureRandom()

    /** How long the consent prompt stays valid before auto-defaulting to text-only. */
    private const val PROMPT_TTL_SEC = 30L

    private data class Pending(
        val player: net.minecraft.server.level.ServerPlayer,
        val source: CommandSourceStack,
        val type: String,
        val text: String,
        val expiresAtEpochSec: Long,
    )

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
            // Internal subcommands fired by the clickable [Attach] /
            // [Submit without] buttons that follow a /feedback bug|suggest.
            // Players don't run these directly. Tokens are scoped to a
            // single submission and expire after PROMPT_TTL_SEC.
            .then(
                Commands.literal("confirm")
                    .then(
                        Commands.argument("token", StringArgumentType.word())
                            .executes { ctx ->
                                resolvePrompt(ctx.source, StringArgumentType.getString(ctx, "token"), withScreenshot = true)
                            }
                    )
            )
            .then(
                Commands.literal("skip")
                    .then(
                        Commands.argument("token", StringArgumentType.word())
                            .executes { ctx ->
                                resolvePrompt(ctx.source, StringArgumentType.getString(ctx, "token"), withScreenshot = false)
                            }
                    )
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

        val now = System.currentTimeMillis() / 1000
        val captureAge = ScreenshotInbox.readyAge(player.uuid, now, 120L)
        if (captureAge == null) {
            // No fresh capture — file text-only immediately, original behavior.
            runSubmit(player, source, type, text, withScreenshot = false)
            return 1
        }

        // Fresh capture exists. Public upload is consequential — get explicit
        // consent before sending to R2. Stash the request, show clickable
        // [Attach] / [Submit without] buttons, and auto-default to text-only
        // after PROMPT_TTL_SEC if the player walks away.
        val token = newToken()
        pending[token] = Pending(
            player = player,
            source = source,
            type = type,
            text = text,
            expiresAtEpochSec = now + PROMPT_TTL_SEC,
        )
        sendConsentPrompt(source, token, captureAge)
        scheduleTimeout(token)
        return 1
    }

    /** Shared "do the actual work" path used by both confirm and skip. */
    private fun runSubmit(
        player: net.minecraft.server.level.ServerPlayer,
        source: CommandSourceStack,
        type: String,
        text: String,
        withScreenshot: Boolean,
    ) {
        val cfg = CobblemonFeedback.config
        val reporterId = com.cobblemonfeedback.Anonymizer.reporterId(player.uuid, player.gameProfile.name)
        val title = buildTitle(type, text, reporterId)
        val body = MetadataCollector.build(type, text, player)
        com.cobblemonfeedback.AuditLog.append(reporterId, player.uuid, player.gameProfile.name, type)
        val labels = listOf(
            when (type) {
                "bug" -> cfg.bugLabel
                "suggest" -> cfg.suggestionLabel
                else -> "feedback"
            }
        )

        source.sendSystemMessage(
            Component.literal("§7Submitting your $type to the dev team…").withStyle(ChatFormatting.GRAY)
        )

        Thread({
            val imageUrl: String? = if (withScreenshot) {
                player.server.execute {
                    if (player.isAlive) {
                        source.sendSystemMessage(
                            Component.literal("§7📤 Uploading screenshot…").withStyle(ChatFormatting.GRAY)
                        )
                    }
                }
                uploadScreenshot(player, reporterId)
            } else null

            val finalBody = if (imageUrl != null) "$body\n\n![screenshot]($imageUrl)" else body
            val url = GitHubIssuesClient.createIssue(title, finalBody, labels)
            val response = when {
                url != null && withScreenshot && imageUrl == null ->
                    "§a✓ Submitted! §7$url §e(screenshot upload failed; submitted without it)"
                url != null -> "§a✓ Submitted! §7$url"
                else -> "§cSubmission failed. Server log has details — please ping a dev."
            }
            player.server.execute {
                if (player.isAlive) {
                    source.sendSystemMessage(Component.literal(response))
                }
            }
        }, "feedback-poster").apply { isDaemon = true }.start()

        if (cfg.cooldownSeconds > 0) {
            lastSubmit[player.uuid] = System.currentTimeMillis() / 1000
        }
    }

    private fun resolvePrompt(
        source: CommandSourceStack,
        token: String,
        withScreenshot: Boolean,
    ): Int {
        val p = pending.remove(token)
        if (p == null) {
            source.sendSystemMessage(
                Component.literal("§7That confirmation expired. Run /feedback again.")
                    .withStyle(ChatFormatting.GRAY)
            )
            return 0
        }
        // Validate the click came from the same player who started the
        // submission. Click-events are authenticated as the player's own
        // command, but defense-in-depth.
        if (source.player?.uuid != p.player.uuid) {
            source.sendSystemMessage(
                Component.literal("§cThat confirmation isn't yours.").withStyle(ChatFormatting.RED)
            )
            return 0
        }
        runSubmit(p.player, p.source, p.type, p.text, withScreenshot)
        return 1
    }

    /**
     * Send the player the consent prompt with two clickable buttons. Built
     * from `Component.literal` pieces with `withClickEvent(RUN_COMMAND, ...)`
     * — vanilla MC chat handles the rendering and the click dispatches the
     * command as if the player typed it.
     */
    private fun sendConsentPrompt(source: CommandSourceStack, token: String, captureAgeSec: Long) {
        val attach = Component.literal(" [ Attach screenshot ] ")
            .withStyle { s ->
                s.withColor(ChatFormatting.GREEN).withBold(true)
                    .withClickEvent(ClickEvent(ClickEvent.Action.RUN_COMMAND, "/feedback confirm $token"))
                    .withHoverEvent(
                        HoverEvent(
                            HoverEvent.Action.SHOW_TEXT,
                            Component.literal("Upload your most recent F2 screenshot to a public URL and embed it in the GitHub issue.")
                        )
                    )
            }
        val skip = Component.literal(" [ Submit without ] ")
            .withStyle { s ->
                s.withColor(ChatFormatting.GRAY)
                    .withClickEvent(ClickEvent(ClickEvent.Action.RUN_COMMAND, "/feedback skip $token"))
                    .withHoverEvent(
                        HoverEvent(
                            HoverEvent.Action.SHOW_TEXT,
                            Component.literal("File the issue text-only. No screenshot uploaded.")
                        )
                    )
            }
        val header = Component.literal(
            "You have a screenshot from ${captureAgeSec}s ago. Upload it publicly with this issue?"
        ).withStyle(ChatFormatting.YELLOW)
        val footer = Component.literal("(auto-cancels in ${PROMPT_TTL_SEC}s — defaults to text-only)")
            .withStyle(ChatFormatting.DARK_GRAY)

        source.sendSystemMessage(header)
        source.sendSystemMessage(Component.literal("").append(attach).append(skip))
        source.sendSystemMessage(footer)
    }

    /**
     * If the player doesn't click within PROMPT_TTL_SEC, default to
     * text-only. Runs on a daemon timer thread; the actual submit hops
     * back onto the server thread before touching player state.
     */
    private fun scheduleTimeout(token: String) {
        Thread({
            try {
                Thread.sleep(PROMPT_TTL_SEC * 1000)
            } catch (_: InterruptedException) {
                return@Thread
            }
            val p = pending.remove(token) ?: return@Thread
            p.player.server.execute {
                if (p.player.isAlive) {
                    p.source.sendSystemMessage(
                        Component.literal("§8(no response — submitted without screenshot)")
                            .withStyle(ChatFormatting.DARK_GRAY)
                    )
                    runSubmit(p.player, p.source, p.type, p.text, withScreenshot = false)
                }
            }
        }, "feedback-prompt-timeout").apply { isDaemon = true }.start()
    }

    /** 8-char hex; ample uniqueness for a 30s window. */
    private fun newToken(): String {
        val bytes = ByteArray(4)
        rng.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Coordinate with the client mod to fetch and upload the screenshot.
     * Returns the public URL or null on any failure (timeout, sentinel,
     * upload error, R2 not configured).
     *
     * Caller must already be on a worker thread — this blocks for up to 30s.
     */
    private fun uploadScreenshot(
        player: net.minecraft.server.level.ServerPlayer,
        reporterId: String,
    ): String? {
        val bytes = ScreenshotInbox.requestAndAwait(
            send = { reqId ->
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                    player, Payloads.FeedbackRequest(reqId),
                )
            },
            timeoutMs = 30_000,
            maxBytes = 8 * 1024 * 1024,
        )
        // Sentinel "no capture" / "too big" — single zero-byte chunk.
        if (bytes == null || bytes.isEmpty()) return null

        val key = "${reporterId}-${System.currentTimeMillis() / 1000}.png"
        return R2Client.putObject(key, bytes, "image/png")
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
