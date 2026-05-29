package com.cobblemonfeedback

import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent

/**
 * Server-side wire-format definitions for the feedback ↔ feedback-client
 * protocol. Mirrors `cobblemon-feedback-client`'s Payloads.kt — same
 * ResourceLocation IDs, same codecs. The two mods don't share code (separate
 * gradle projects) so every wire-format change must be made in BOTH files.
 *
 * See docs/design/player-feedback-phase2.md "Network protocol".
 */
internal object Payloads {

    /** Client → Server: player has captured a screenshot via F2. */
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

    /** Server → Client: ask client to send the held PNG under [requestId]. */
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

    /** Client → Server: one chunk of PNG bytes for [requestId]. */
    data class FeedbackChunk(
        val requestId: Long,
        val chunkIndex: Int,
        val totalChunks: Int,
        val bytes: ByteArray,
    ) : CustomPacketPayload {
        override fun type(): CustomPacketPayload.Type<FeedbackChunk> = TYPE

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

    fun register(event: RegisterPayloadHandlersEvent) {
        val registrar = event.registrar("1")
        registrar.playToServer(FeedbackReady.TYPE, FeedbackReady.CODEC, ServerPayloadHandlers::onFeedbackReady)
        registrar.playToClient(FeedbackRequest.TYPE, FeedbackRequest.CODEC) { _, _ -> /* client-only */ }
        registrar.playToServer(FeedbackChunk.TYPE, FeedbackChunk.CODEC, ServerPayloadHandlers::onFeedbackChunk)
    }
}

internal object ServerPayloadHandlers {
    fun onFeedbackReady(
        payload: Payloads.FeedbackReady,
        context: net.neoforged.neoforge.network.handling.IPayloadContext,
    ) {
        ScreenshotInbox.recordReady(context.player().uuid, payload.capturedAtEpochSec)
    }

    fun onFeedbackChunk(
        payload: Payloads.FeedbackChunk,
        context: net.neoforged.neoforge.network.handling.IPayloadContext,
    ) {
        ScreenshotInbox.acceptChunk(
            payload.requestId, payload.chunkIndex, payload.totalChunks, payload.bytes,
        )
    }
}
