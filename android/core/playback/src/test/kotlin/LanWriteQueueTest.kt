package playback

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val SERVER = LanServer("http://192.168.1.5:7788", "192.168.1.5:7788")

private class RecordingPuts : LanChunkProtocol {
    val puts = mutableListOf<Long>()
    var nextResult: LanPutResult = LanPutResult.Stored
    var throwOnPut: Throwable? = null

    override suspend fun get(
        baseUrl: String,
        setId: String,
        index: Long,
        expectedLength: Int,
    ): ByteArray? = null

    override suspend fun put(
        baseUrl: String,
        token: String,
        setId: String,
        index: Long,
        total: Long,
        body: ByteArray,
    ): LanPutResult {
        throwOnPut?.let { throw it }
        puts.add(index)
        return nextResult
    }

    override suspend fun verify(baseUrl: String): Boolean = true

    override suspend fun status(baseUrl: String): LanServerStatus? = null
}

class LanWriteQueueTest {
    private fun chunk(index: Long) = ByteArray(4) { index.toByte() }

    @Test
    fun aQueuedChunkIsPutOnceTheWorkerRuns() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val puts = RecordingPuts()
            val queue =
                LanWriteQueue(
                    scope = TestScope(dispatcher),
                    dispatcher = dispatcher,
                    client = puts,
                    server = { SERVER },
                    token = { "token" },
                    onUnauthorized = {},
                )

            queue.enqueue("s1", 0, 100, chunk(0))
            testScheduler.advanceUntilIdle()

            assertEquals(listOf(0L), puts.puts)
        }

    @Test
    fun aNinthEnqueueDropsTheOldestOfEightBeforeTheWorkerTakesAny() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val puts = RecordingPuts()
            val queue =
                LanWriteQueue(
                    scope = TestScope(dispatcher),
                    dispatcher = dispatcher,
                    client = puts,
                    server = { SERVER },
                    token = { "token" },
                    onUnauthorized = {},
                )

            // The worker coroutine is launched but never runs until the test
            // scheduler is advanced — every enqueue below lands in the
            // channel's own buffer, not a running consumer.
            for (index in 0L..8L) queue.enqueue("s1", index, 900, chunk(index))
            testScheduler.advanceUntilIdle()

            assertEquals((1L..8L).toList(), puts.puts, "index 0 must have been dropped for the ninth")
        }

    @Test
    fun aFourOhOneHaltsFurtherWritesAndReportsOnce() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val puts = RecordingPuts().apply { nextResult = LanPutResult.Unauthorized }
            var reported = 0
            val queue =
                LanWriteQueue(
                    scope = TestScope(dispatcher),
                    dispatcher = dispatcher,
                    client = puts,
                    server = { SERVER },
                    token = { "token" },
                    onUnauthorized = { reported++ },
                )

            queue.enqueue("s1", 0, 100, chunk(0))
            testScheduler.advanceUntilIdle()
            queue.enqueue("s1", 1, 100, chunk(1))
            testScheduler.advanceUntilIdle()

            assertEquals(1, reported)
            assertEquals(listOf(0L), puts.puts, "a write queued after the halt must never reach the client")
        }

    @Test
    fun aNetworkFailureOnWriteNeverEscapesTheWorker() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val puts = RecordingPuts().apply { throwOnPut = IOException("host unreachable") }
            val queue =
                LanWriteQueue(
                    scope = TestScope(dispatcher),
                    dispatcher = dispatcher,
                    client = puts,
                    server = { SERVER },
                    token = { "token" },
                    onUnauthorized = {},
                )

            queue.enqueue("s1", 0, 100, chunk(0))
            testScheduler.advanceUntilIdle()

            // No exception propagated out of advanceUntilIdle(); the worker survived and keeps running.
            puts.throwOnPut = null
            queue.enqueue("s1", 1, 100, chunk(1))
            testScheduler.advanceUntilIdle()
            assertEquals(listOf(1L), puts.puts)
        }

    @Test
    fun withNoTokenNothingIsPutAndNothingIsHalted() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val puts = RecordingPuts()
            val queue =
                LanWriteQueue(
                    scope = TestScope(dispatcher),
                    dispatcher = dispatcher,
                    client = puts,
                    server = { SERVER },
                    token = { null },
                    onUnauthorized = {},
                )

            queue.enqueue("s1", 0, 100, chunk(0))
            testScheduler.advanceUntilIdle()

            assertTrue(puts.puts.isEmpty())
        }
}
