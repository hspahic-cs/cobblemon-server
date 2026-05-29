package com.cobblemonfeedback

import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Server-side state for the screenshot protocol.
 *
 * Two stores:
 *  1. **Ready map** — per-player `(UUID → epoch-seconds)` of the most recent
 *     F2 capture. Updated when the client sends `feedback_ready`. Read by
 *     `/feedback` to decide whether to ask for a screenshot at all.
 *  2. **Pending requests** — per-`requestId` reassembly buffer. When
 *     `/feedback` decides "yes, fetch the screenshot," we generate a
 *     requestId, send the client a `feedback_request`, and create a
 *     [Pending] entry. Subsequent `feedback_chunk` packets accumulate into
 *     the entry; the originating thread blocks on `awaitBytes`.
 *
 * All operations are thread-safe — payload handlers run on the network
 * thread; `/feedback` upload coordination runs on a worker thread.
 */
internal object ScreenshotInbox {
    private val log = LoggerFactory.getLogger("cobblemon-feedback/inbox")
    private val nextRequestId = AtomicLong(1L)

    /** UUID → most recent capture timestamp (epoch seconds). */
    private val ready: MutableMap<UUID, Long> = ConcurrentHashMap()

    /**
     * UUIDs that have ever sent us a `feedback_ready` — i.e. they have the
     * cobblemon-feedback-client mod loaded. Used to decide whether to show
     * the "(no screenshot attached)" note. Server-restart-scoped: if a
     * client mod player rejoins after restart and runs /feedback before
     * pressing F2, we won't know yet, and they get no note. That's fine —
     * the note is a hint, not a contract.
     */
    private val knownClientMod: MutableSet<UUID> =
        java.util.concurrent.ConcurrentHashMap.newKeySet()

    /** requestId → pending reassembly buffer. */
    private val pending: MutableMap<Long, Pending> = ConcurrentHashMap()

    /** Mark this player as having a fresh capture available. */
    fun recordReady(player: UUID, capturedAtEpochSec: Long) {
        ready[player] = capturedAtEpochSec
        knownClientMod.add(player)
    }

    /** True iff we've ever seen a `feedback_ready` from this player. */
    fun hasClientMod(player: UUID): Boolean = player in knownClientMod

    /**
     * @return capture timestamp if it's < [maxAgeSec] old, else null. Does
     * not consume the entry — same capture can satisfy multiple /feedback
     * runs (rare, but legal).
     */
    fun readyAge(player: UUID, nowEpochSec: Long, maxAgeSec: Long): Long? {
        val captured = ready[player] ?: return null
        val age = nowEpochSec - captured
        return if (age in 0..maxAgeSec) age else null
    }

    /**
     * Allocate a new request, send via [send], and block the caller until
     * the chunks reassemble — or [timeoutMs] elapses, or the upload exceeds
     * a sanity cap. Returns the assembled bytes or null on timeout/failure.
     *
     * The caller is expected to be on a worker thread.
     */
    fun requestAndAwait(send: (Long) -> Unit, timeoutMs: Long, maxBytes: Int): ByteArray? {
        val id = nextRequestId.getAndIncrement()
        val entry = Pending(maxBytes)
        pending[id] = entry
        try {
            send(id)
            return entry.awaitBytes(timeoutMs)
        } finally {
            pending.remove(id)
        }
    }

    /** Called by the chunk handler. Buffers the chunk; signals when complete. */
    fun acceptChunk(requestId: Long, chunkIndex: Int, totalChunks: Int, bytes: ByteArray) {
        val entry = pending[requestId]
        if (entry == null) {
            // Late or unknown request — request was canceled / timed out.
            log.debug("dropping chunk for unknown requestId={}", requestId)
            return
        }
        entry.acceptChunk(chunkIndex, totalChunks, bytes)
    }

    /** Internal reassembly buffer; one per in-flight request. */
    private class Pending(private val maxBytes: Int) {
        private val lock = java.util.concurrent.locks.ReentrantLock()
        private val cond = lock.newCondition()
        private val chunks = HashMap<Int, ByteArray>()
        private var totalChunks = -1
        private var totalBytes = 0
        private var failed = false

        fun acceptChunk(chunkIndex: Int, total: Int, bytes: ByteArray) {
            lock.lock()
            try {
                if (failed) return
                if (totalChunks < 0) totalChunks = total
                else if (totalChunks != total) {
                    failed = true; cond.signalAll(); return
                }
                if (chunks.containsKey(chunkIndex)) return
                totalBytes += bytes.size
                if (totalBytes > maxBytes) {
                    failed = true; cond.signalAll(); return
                }
                chunks[chunkIndex] = bytes
                if (chunks.size == totalChunks) cond.signalAll()
            } finally {
                lock.unlock()
            }
        }

        fun awaitBytes(timeoutMs: Long): ByteArray? {
            val deadline = System.nanoTime() + timeoutMs * 1_000_000
            lock.lock()
            try {
                while (!failed && (totalChunks < 0 || chunks.size < totalChunks)) {
                    val remaining = deadline - System.nanoTime()
                    if (remaining <= 0) return null
                    cond.awaitNanos(remaining)
                }
                if (failed) return null
                val out = java.io.ByteArrayOutputStream(totalBytes)
                for (i in 0 until totalChunks) {
                    out.write(chunks[i] ?: return null)
                }
                return out.toByteArray()
            } finally {
                lock.unlock()
            }
        }
    }
}
