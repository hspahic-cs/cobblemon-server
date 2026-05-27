package com.cobblemonfeedback

import java.time.Instant
import java.util.ArrayDeque
import java.util.Deque

/**
 * Bounded ring buffer of recent server chat lines, included in every feedback
 * report so devs see what was happening when a player hit a bug. Thread-safe
 * via synchronized access; size bound is capped to whatever
 * `FeedbackConfig.chatBufferSize` is at boot.
 */
internal object RecentChatBuffer {
    data class ChatLine(val timestamp: Instant, val player: String, val message: String)

    private val buffer: Deque<ChatLine> = ArrayDeque()
    private val maxSize: Int get() = (CobblemonFeedback.config.chatBufferSize).coerceAtLeast(0)

    @Synchronized
    fun add(player: String, message: String) {
        if (maxSize == 0) return
        buffer.addLast(ChatLine(Instant.now(), player, message))
        while (buffer.size > maxSize) buffer.pollFirst()
    }

    @Synchronized
    fun snapshot(): List<ChatLine> = buffer.toList()
}
