package com.cobblemonfeedback

import java.time.Instant
import java.util.ArrayDeque
import java.util.Deque
import java.util.UUID

/**
 * Bounded ring buffer of recent server chat lines, included in every feedback
 * report so devs see what was happening when a player hit a bug. Thread-safe
 * via synchronized access; size bound is capped to whatever
 * `FeedbackConfig.chatBufferSize` is at boot.
 *
 * Stores both player display name and UUID per line so [MetadataCollector]
 * can substitute display names with their anonymized reporter IDs before
 * embedding the chat block in a public issue body.
 */
internal object RecentChatBuffer {
    data class ChatLine(
        val timestamp: Instant,
        val playerUuid: UUID,
        val playerName: String,
        val message: String,
    )

    private val buffer: Deque<ChatLine> = ArrayDeque()
    private val maxSize: Int get() = (CobblemonFeedback.config.chatBufferSize).coerceAtLeast(0)

    @Synchronized
    fun add(playerUuid: UUID, playerName: String, message: String) {
        if (maxSize == 0) return
        buffer.addLast(ChatLine(Instant.now(), playerUuid, playerName, message))
        while (buffer.size > maxSize) buffer.pollFirst()
    }

    @Synchronized
    fun snapshot(): List<ChatLine> = buffer.toList()
}
