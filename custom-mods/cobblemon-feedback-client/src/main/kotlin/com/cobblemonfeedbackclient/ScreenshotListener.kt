package com.cobblemonfeedbackclient

import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.ScreenshotEvent
import net.neoforged.neoforge.network.PacketDistributor

/**
 * Subscribes to NeoForge's `ScreenshotEvent` (fired on F2 by vanilla MC, after
 * the framebuffer has been read into a `NativeImage` but before the PNG is
 * written to disk).
 *
 * On capture we:
 *  1. Take a copy of the image (the original is owned by MC and will be
 *     closed after the disk write — we can't hold a reference past the event).
 *  2. Stash the copy in [ScreenshotSlot] with the current timestamp.
 *  3. Tell the server "I have one ready" via the `feedback_ready` packet.
 *  4. Print a chat ack so the player knows the capture is queued for /feedback.
 *
 * We do NOT cancel the event or otherwise disturb MC's normal screenshot
 * write — players keep their on-disk file as usual.
 */
@EventBusSubscriber(modid = CobblemonFeedbackClient.MOD_ID, value = [net.neoforged.api.distmarker.Dist.CLIENT])
internal object ScreenshotListener {

    @SubscribeEvent
    fun onScreenshot(event: ScreenshotEvent) {
        val img = event.image
        // Copy the image — the original is owned by Minecraft's screenshot path
        // and will be closed after the disk write. NativeImage doesn't expose a
        // public copy(), but we can rebuild from getPixelRGBA reads.
        val copy = copyOf(img)
        val now = System.currentTimeMillis() / 1000
        ScreenshotSlot.hold(copy, now)

        // Tell the server we've got one. The server uses this to decide
        // whether /feedback should ask for the screenshot.
        try {
            PacketDistributor.sendToServer(Payloads.FeedbackReady(now))
        } catch (e: Exception) {
            // Connection drops, single-player without a server, etc. — fine.
            CobblemonFeedbackClient.logger.debug("FeedbackReady send skipped: ${e.message}")
        }

        // Be explicit about consequences: /feedback will offer to upload this
        // image to a PUBLIC URL embedded in the GitHub issue. Players who
        // don't want that just don't run /feedback (or pick "Submit without"
        // when prompted).
        Minecraft.getInstance().gui.chat.addMessage(
            Component.literal(
                "📸 Captured. If you /feedback in the next ${ScreenshotSlot.HOLD_TTL_SEC}s, " +
                    "you'll be asked whether to upload this screenshot to a public URL on the bug report."
            ).withStyle(ChatFormatting.GRAY)
        )
    }

    private fun copyOf(src: NativeImage): NativeImage {
        val copy = NativeImage(src.format(), src.width, src.height, false)
        for (y in 0 until src.height) {
            for (x in 0 until src.width) {
                copy.setPixelRGBA(x, y, src.getPixelRGBA(x, y))
            }
        }
        return copy
    }
}
