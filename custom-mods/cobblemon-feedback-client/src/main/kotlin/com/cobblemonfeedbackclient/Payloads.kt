package com.cobblemonfeedbackclient

import io.netty.buffer.ByteBuf
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent

/**
 * Shared wire-format definitions for the feedback ↔ feedback-client protocol.
 *
 * The server-side mod has a parallel set of classes with identical
 * ResourceLocation IDs and identical codecs — the two mods don't share code
 * (separate gradle projects) but the wire bytes match. If you change anything
 * here, mirror it in cobblemon-feedback's Payloads.kt or the handshake breaks.
 *
 * Cap: chunked PNG payload limited to 8 MB total. Per-chunk cap is the
 * NeoForge custom-payload default (~1 MB practical, we use 32 KB for safety
 * margin against fragmentation).
 *
 * See docs/design/player-feedback-phase2.md "Network protocol".
 */
internal object Payloads {

    /** Client → Server: "I've captured a screenshot, hold it for me." */
    data class FeedbackReady(val capturedAtEpochSec: Long) : CustomPacketPayload {
        override fun type(): CustomPacketPayload.Type<FeedbackReady> = TYPE

        companion object {
            val TYPE = CustomPacketPayload.Type<FeedbackReady>(
                ResourceLocation.fromNamespaceAndPath("cobblemonfeedback", "ready"),
            )
            val CODEC: StreamCodec<ByteBuf, FeedbackReady> =
                StreamCodec.composite(
                    ByteBufCodecs.VAR_LONG, FeedbackReady::capturedAtEpochSec,
                    ::FeedbackReady,
                )
        }
    }

    /** Server → Client: "Send me your held screenshot under this request id." */
    data class FeedbackRequest(val requestId: Long) : CustomPacketPayload {
        override fun type(): CustomPacketPayload.Type<FeedbackRequest> = TYPE

        companion object {
            val TYPE = CustomPacketPayload.Type<FeedbackRequest>(
                ResourceLocation.fromNamespaceAndPath("cobblemonfeedback", "request"),
            )
            val CODEC: StreamCodec<ByteBuf, FeedbackRequest> =
                StreamCodec.composite(
                    ByteBufCodecs.VAR_LONG, FeedbackRequest::requestId,
                    ::FeedbackRequest,
                )
        }
    }

    /**
     * Client → Server: one chunk of the PNG bytes for [requestId].
     * The server reassembles by [chunkIndex] until [totalChunks] are received.
     */
    data class FeedbackChunk(
        val requestId: Long,
        val chunkIndex: Int,
        val totalChunks: Int,
        val bytes: ByteArray,
    ) : CustomPacketPayload {
        override fun type(): CustomPacketPayload.Type<FeedbackChunk> = TYPE

        // ByteArray equals/hashCode aren't useful for this case but data-class
        // generates them; suppress the lint by overriding to identity.
        override fun equals(other: Any?): Boolean = this === other
        override fun hashCode(): Int = System.identityHashCode(this)

        companion object {
            val TYPE = CustomPacketPayload.Type<FeedbackChunk>(
                ResourceLocation.fromNamespaceAndPath("cobblemonfeedback", "chunk"),
            )
            val CODEC: StreamCodec<ByteBuf, FeedbackChunk> = object : StreamCodec<ByteBuf, FeedbackChunk> {
                override fun decode(buf: ByteBuf): FeedbackChunk {
                    val requestId = ByteBufCodecs.VAR_LONG.decode(buf)
                    val chunkIndex = ByteBufCodecs.VAR_INT.decode(buf)
                    val totalChunks = ByteBufCodecs.VAR_INT.decode(buf)
                    val bytes = ByteBufCodecs.BYTE_ARRAY.decode(buf)
                    return FeedbackChunk(requestId, chunkIndex, totalChunks, bytes)
                }
                override fun encode(buf: ByteBuf, value: FeedbackChunk) {
                    ByteBufCodecs.VAR_LONG.encode(buf, value.requestId)
                    ByteBufCodecs.VAR_INT.encode(buf, value.chunkIndex)
                    ByteBufCodecs.VAR_INT.encode(buf, value.totalChunks)
                    ByteBufCodecs.BYTE_ARRAY.encode(buf, value.bytes)
                }
            }
        }
    }

    /** Per-chunk PNG byte cap. 32 KB leaves headroom under NeoForge's default limits. */
    const val CHUNK_BYTES = 32 * 1024

    /** Total PNG cap before client refuses to send. 8 MB matches the design doc. */
    const val MAX_TOTAL_BYTES = 8 * 1024 * 1024

    fun register(event: RegisterPayloadHandlersEvent) {
        // Versioning the protocol with a string lets us change wire format
        // without breaking older clients silently — they'll just fail the handshake.
        val registrar = event.registrar("1")
        registrar.playToServer(FeedbackReady.TYPE, FeedbackReady.CODEC) { _, _ -> /* server-only handler */ }
        registrar.playToClient(FeedbackRequest.TYPE, FeedbackRequest.CODEC, ClientPayloadHandlers::onFeedbackRequest)
        registrar.playToServer(FeedbackChunk.TYPE, FeedbackChunk.CODEC) { _, _ -> /* server-only handler */ }
    }
}

/**
 * Server asked for our held screenshot. Encode it to PNG and send back as
 * chunks of [Payloads.CHUNK_BYTES] bytes each. Refuses if the encoded image
 * exceeds [Payloads.MAX_TOTAL_BYTES] — server falls back to text-only.
 */
internal object ClientPayloadHandlers {
    fun onFeedbackRequest(
        payload: Payloads.FeedbackRequest,
        context: net.neoforged.neoforge.network.handling.IPayloadContext,
    ) {
        val now = System.currentTimeMillis() / 1000
        val bytes = ScreenshotSlot.takePngBytes(now)
        if (bytes == null) {
            // No fresh capture — server will time out and post text-only.
            // Still send a sentinel chunk so the server can short-circuit the wait.
            context.reply(Payloads.FeedbackChunk(payload.requestId, 0, 1, ByteArray(0)))
            return
        }
        if (bytes.size > Payloads.MAX_TOTAL_BYTES) {
            net.minecraft.client.Minecraft.getInstance().gui.chat.addMessage(
                net.minecraft.network.chat.Component.literal(
                    "Screenshot too large to attach (${bytes.size / 1024} KB). Submitted without it."
                ).withStyle(net.minecraft.ChatFormatting.YELLOW)
            )
            // Sentinel: 1 chunk, 0 bytes — same convention as "no capture".
            context.reply(Payloads.FeedbackChunk(payload.requestId, 0, 1, ByteArray(0)))
            return
        }
        val totalChunks = (bytes.size + Payloads.CHUNK_BYTES - 1) / Payloads.CHUNK_BYTES
        for (i in 0 until totalChunks) {
            val from = i * Payloads.CHUNK_BYTES
            val to = minOf(from + Payloads.CHUNK_BYTES, bytes.size)
            context.reply(
                Payloads.FeedbackChunk(payload.requestId, i, totalChunks, bytes.copyOfRange(from, to))
            )
        }
    }
}
