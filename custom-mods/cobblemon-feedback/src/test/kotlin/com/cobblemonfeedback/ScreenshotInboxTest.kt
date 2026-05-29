package com.cobblemonfeedback

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Tests for the chunk reassembly path in [ScreenshotInbox]. The static-object
 * shape means we test through the public API rather than constructing
 * Pending directly. Each test acquires its own requestId via requestAndAwait
 * — they're allocated monotonically and never reused, so tests don't
 * interfere with each other.
 */
class ScreenshotInboxTest {

    /** Run [feedChunks] inside requestAndAwait, return (requestId, assembledBytesOrNull). */
    private fun awaitOnWorker(feedChunks: (Long) -> Unit): Pair<Long, ByteArray?> {
        val capturedId = AtomicReference<Long>()
        val result = AtomicReference<ByteArray?>()
        val done = CountDownLatch(1)
        Thread {
            result.set(
                ScreenshotInbox.requestAndAwait(
                    send = { id -> capturedId.set(id); feedChunks(id) },
                    timeoutMs = 2_000,
                    maxBytes = 1024 * 1024,
                )
            )
            done.countDown()
        }.apply { isDaemon = true }.start()
        check(done.await(5, TimeUnit.SECONDS)) { "worker didn't finish" }
        return capturedId.get() to result.get()
    }

    @Test
    fun `reassembles in-order chunks`() {
        val (id, bytes) = awaitOnWorker { id ->
            // Send 3 chunks of "ABC", "DEF", "GH" in order.
            ScreenshotInbox.acceptChunk(id, 0, 3, "ABC".toByteArray())
            ScreenshotInbox.acceptChunk(id, 1, 3, "DEF".toByteArray())
            ScreenshotInbox.acceptChunk(id, 2, 3, "GH".toByteArray())
        }
        assertArrayEquals("ABCDEFGH".toByteArray(), bytes)
        assert(id > 0)
    }

    @Test
    fun `reassembles out-of-order chunks`() {
        val (_, bytes) = awaitOnWorker { id ->
            ScreenshotInbox.acceptChunk(id, 2, 3, "GH".toByteArray())
            ScreenshotInbox.acceptChunk(id, 0, 3, "ABC".toByteArray())
            ScreenshotInbox.acceptChunk(id, 1, 3, "DEF".toByteArray())
        }
        assertArrayEquals("ABCDEFGH".toByteArray(), bytes)
    }

    @Test
    fun `times out when chunks never arrive`() {
        val result = AtomicReference<ByteArray?>()
        val done = CountDownLatch(1)
        Thread {
            result.set(
                ScreenshotInbox.requestAndAwait(
                    send = { /* drop request on the floor */ },
                    timeoutMs = 100,
                    maxBytes = 1024,
                )
            )
            done.countDown()
        }.apply { isDaemon = true }.start()
        check(done.await(2, TimeUnit.SECONDS))
        assertNull(result.get())
    }

    @Test
    fun `rejects payload exceeding max bytes`() {
        val result = AtomicReference<ByteArray?>()
        val done = CountDownLatch(1)
        Thread {
            result.set(
                ScreenshotInbox.requestAndAwait(
                    send = { id ->
                        // 2 chunks of 600 bytes each — total 1200 > maxBytes=1000.
                        ScreenshotInbox.acceptChunk(id, 0, 2, ByteArray(600))
                        ScreenshotInbox.acceptChunk(id, 1, 2, ByteArray(600))
                    },
                    timeoutMs = 1_000,
                    maxBytes = 1000,
                )
            )
            done.countDown()
        }.apply { isDaemon = true }.start()
        check(done.await(2, TimeUnit.SECONDS))
        assertNull(result.get())
    }

    @Test
    fun `fails on mismatched totalChunks`() {
        val result = AtomicReference<ByteArray?>()
        val done = CountDownLatch(1)
        Thread {
            result.set(
                ScreenshotInbox.requestAndAwait(
                    send = { id ->
                        // First chunk says total=3, second contradicts with total=5.
                        ScreenshotInbox.acceptChunk(id, 0, 3, "AAA".toByteArray())
                        ScreenshotInbox.acceptChunk(id, 1, 5, "BBB".toByteArray())
                    },
                    timeoutMs = 1_000,
                    maxBytes = 1024,
                )
            )
            done.countDown()
        }.apply { isDaemon = true }.start()
        check(done.await(2, TimeUnit.SECONDS))
        assertNull(result.get())
    }

    @Test
    fun `late chunks for unknown requestId are dropped silently`() {
        // Issue a request and let it time out; then deliver chunks for that id.
        // The chunks should be ignored (no exception, no state leak).
        val result = AtomicReference<ByteArray?>()
        val capturedId = AtomicReference<Long>()
        val done = CountDownLatch(1)
        Thread {
            result.set(
                ScreenshotInbox.requestAndAwait(
                    send = { id -> capturedId.set(id) },
                    timeoutMs = 50,
                    maxBytes = 1024,
                )
            )
            done.countDown()
        }.apply { isDaemon = true }.start()
        check(done.await(2, TimeUnit.SECONDS))
        assertNull(result.get())

        // Now the request is gone; this should not throw or do anything.
        ScreenshotInbox.acceptChunk(capturedId.get(), 0, 1, "late".toByteArray())
    }

    @Test
    fun `concurrent chunk delivery from multiple threads`() {
        // Stress: send 16 chunks from 4 threads simultaneously.
        val executor = Executors.newFixedThreadPool(4)
        val (_, bytes) = awaitOnWorker { id ->
            val total = 16
            val barrier = CountDownLatch(total)
            for (i in 0 until total) {
                executor.submit {
                    ScreenshotInbox.acceptChunk(id, i, total, byteArrayOf(i.toByte()))
                    barrier.countDown()
                }
            }
            check(barrier.await(2, TimeUnit.SECONDS))
        }
        executor.shutdown()
        assert(bytes != null && bytes.size == 16) { "got ${bytes?.size} bytes" }
        // Each chunk is its index byte; reassembly must preserve ordering.
        for (i in 0 until 16) {
            assert(bytes!![i] == i.toByte()) { "byte $i = ${bytes[i]}" }
        }
    }

    @Test
    fun `readyAge returns null for unknown player and stale captures`() {
        val unknown = java.util.UUID.randomUUID()
        assertNull(ScreenshotInbox.readyAge(unknown, 1000L, 120L))

        val player = java.util.UUID.randomUUID()
        ScreenshotInbox.recordReady(player, 1000L)
        // 130 sec old — over the 120 sec cap.
        assertNull(ScreenshotInbox.readyAge(player, 1130L, 120L))
        // 60 sec old — fresh.
        assert(ScreenshotInbox.readyAge(player, 1060L, 120L) == 60L)
    }

    @Test
    fun `hasClientMod becomes true after first feedback_ready`() {
        val player = java.util.UUID.randomUUID()
        assert(!ScreenshotInbox.hasClientMod(player))
        ScreenshotInbox.recordReady(player, 1000L)
        assert(ScreenshotInbox.hasClientMod(player))
    }
}
