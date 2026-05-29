package com.cobblemonfeedbackclient

import com.mojang.blaze3d.platform.NativeImage
import org.slf4j.LoggerFactory
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Holds at most ONE recent screenshot in memory, with a wall-clock expiry.
 *
 * Two-phase usage:
 *  1. F2 listener calls [hold] with a copy of MC's `NativeImage`. Replaces
 *     any prior held image; closes the old one.
 *  2. When server sends `feedback_request`, we [takePngBytes] which encodes
 *     the held image to PNG, returns the bytes, and clears the slot.
 *
 * If 120 seconds elapse without a take, the next access discovers the
 * staleness and clears the slot. We don't need a timer thread — checks at
 * read time are enough for our cadence.
 *
 * `NativeImage` owns a native pointer that must be `close()`d to free off-heap
 * memory; we always close any image we replace or discard.
 */
internal object ScreenshotSlot {
    private val log = LoggerFactory.getLogger("cobblemon-feedback-client/slot")

    /** TTL for a held capture before /feedback can claim it. */
    const val HOLD_TTL_SEC = 120L

    private val lock = ReentrantLock()
    private var image: NativeImage? = null
    private var capturedAtEpochSec: Long = 0L

    /** Replace any held image with [copy]. Caller must pass a COPY (we close it later). */
    fun hold(copy: NativeImage, capturedAt: Long) {
        lock.withLock {
            image?.close()
            image = copy
            capturedAtEpochSec = capturedAt
        }
    }

    /** @return capture timestamp if a fresh image is held, else null (also clears stale). */
    fun freshCaptureAt(nowEpochSec: Long): Long? {
        lock.withLock {
            val img = image ?: return null
            return if (nowEpochSec - capturedAtEpochSec in 0..HOLD_TTL_SEC) {
                capturedAtEpochSec
            } else {
                img.close()
                image = null
                capturedAtEpochSec = 0L
                null
            }
        }
    }

    /**
     * Encode the held image to PNG bytes and clear the slot. Returns null if
     * the slot is empty or stale, or encoding fails.
     */
    fun takePngBytes(nowEpochSec: Long): ByteArray? {
        lock.withLock {
            val img = image ?: return null
            try {
                if (nowEpochSec - capturedAtEpochSec !in 0..HOLD_TTL_SEC) {
                    return null
                }
                return img.asByteArray()
            } catch (e: Exception) {
                log.warn("PNG encode failed: ${e.message}", e)
                return null
            } finally {
                img.close()
                image = null
                capturedAtEpochSec = 0L
            }
        }
    }
}
