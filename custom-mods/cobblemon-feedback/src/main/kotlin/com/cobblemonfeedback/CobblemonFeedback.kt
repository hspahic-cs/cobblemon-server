package com.cobblemonfeedback

import com.cobblemonfeedback.commands.FeedbackCommand
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.common.Mod
import net.neoforged.fml.loading.FMLPaths
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.event.ServerChatEvent
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Server-side `/feedback` command for in-game bug reports and suggestions.
 *
 * Players run `/feedback bug <text>` or `/feedback suggest <text>`. The mod
 * captures rich server-side metadata (coords, dimension, party, TPS, recent
 * chat, log tail) and POSTs a new GitHub Issue.
 *
 * See docs/working-with-mods.md and docs/design/player-feedback.md.
 */
@Mod("cobblemon_feedback")
class CobblemonFeedback(modBus: IEventBus) {

    init {
        logger.info("Cobblemon Feedback initializing...")

        config = FeedbackConfig.load(FMLPaths.CONFIGDIR.get())
        if (config.githubToken.isBlank() || config.githubRepo.isBlank()) {
            logger.warn(
                "cobblemon-feedback: githubToken/githubRepo not set in config — " +
                    "/feedback will fail until configured. See config/cobblemon-feedback/authored/config.json."
            )
        } else {
            logger.info("cobblemon-feedback: posting to {}", config.githubRepo)
        }

        // Register on the GAME bus (commands + chat events), not the mod-load bus.
        NeoForge.EVENT_BUS.addListener(::onRegisterCommands)
        NeoForge.EVENT_BUS.addListener(::onServerChat)

        logger.info("Cobblemon Feedback initialized")
    }

    private fun onRegisterCommands(event: RegisterCommandsEvent) {
        FeedbackCommand.register(event.dispatcher)
    }

    private fun onServerChat(event: ServerChatEvent) {
        // Buffer the last N chat lines so reports include nearby conversation
        // context. Store UUID alongside the name so MetadataCollector can
        // anonymize names when rendering the issue body.
        RecentChatBuffer.add(
            event.player.uuid,
            event.player.gameProfile.name,
            event.message.string,
        )
    }

    companion object {
        const val MOD_ID = "cobblemon_feedback"
        val logger: Logger = LoggerFactory.getLogger(MOD_ID)
        lateinit var config: FeedbackConfig
            private set
    }
}
