package com.cobblemonfeedbackclient

import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Client-only companion to `cobblemon-feedback`.
 *
 * Hooks vanilla F2 (Minecraft's screenshot keybind) so the rendered frame is
 * additionally held in memory under a per-player slot for 120 seconds. When
 * `/feedback` is invoked and the server requests the screenshot, we PNG-encode
 * the held image and ship it back over the network in 32 KB chunks.
 *
 * Architecture rationale: we deliberately don't replace MC's F2 — we hook the
 * same `ScreenshotEvent`, take a copy of the `NativeImage`, and let MC's normal
 * disk write proceed. Players keep their own screenshot folder; /feedback
 * attachments are a free additional consumer of the same keypress.
 *
 * See docs/design/player-feedback-phase2.md.
 */
@Mod(value = "cobblemon_feedback_client", dist = [Dist.CLIENT])
class CobblemonFeedbackClient(modBus: IEventBus) {

    init {
        logger.info("Cobblemon Feedback Client initializing...")
        modBus.addListener(::onRegisterPayloadHandlers)
        // ScreenshotEvent + chunked-upload wiring registered in subsequent tasks.
        logger.info("Cobblemon Feedback Client initialized")
    }

    private fun onRegisterPayloadHandlers(event: RegisterPayloadHandlersEvent) {
        Payloads.register(event)
    }

    companion object {
        const val MOD_ID = "cobblemon_feedback_client"
        val logger: Logger = LoggerFactory.getLogger(MOD_ID)
    }
}
