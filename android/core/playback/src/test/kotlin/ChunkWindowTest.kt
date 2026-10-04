package playback

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** A hundred whole chunks: room for any window these tests open. */
private const val SET_BYTES = 100L * CHUNK_BYTES

/** One fetch's round trip as measured on the TV box: ~0.5 s per 1 MiB chunk. */
private const val ROUND_TRIP_MS = 500L

/**
 * Telegram as the box sees it: every chunk arrives [ROUND_TRIP_MS] after it
 * was asked for, however many are in flight — or whatever [latencyOf] says
 * for that chunk, and a [failing] one fails after its latency instead.
 * Records what was asked, what was cancelled, and the most it was ever
 * serving at once. Runs on the test's one thread, so plain collections are
 * enough.
 */
private class SlowChunks(
    private val failing: Set<Long> = emptySet(),
    private val latencyOf: (Long) -> Long = { ROUND_TRIP_MS },
) : SetChunkSource {
    val asked = mutableListOf<Long>()
    val cancelled = mutableListOf<Long>()
    var inFlight = 0
    var mostInFlight = 0

    override suspend fun chunk(
        setId: String,
        index: Long,
        totalSize: Long,
    ): ByteArray {
        asked += index
        inFlight++
        mostInFlight = maxOf(mostInFlight, inFlight)
        try {
            delay(latencyOf(index))
            if (index in failing) throw IOException("FLOOD_WAIT on chunk $index")
            return ByteArray(expectedChunkLength(index, totalSize)) { index.toByte() }
        } catch (e: CancellationException) {
            cancelled += index
            throw e
        } finally {
            inFlight--
        }
    }
}

private fun TestScope.window(
    chunks: SetChunkSource,
    width: suspend () -> Int,
    lastIndex: Long = SET_BYTES / CHUNK_BYTES - 1,
    failed: () -> Unit = {},
) = ChunkWindow("s1", SET_BYTES, lastIndex, chunks, width, failed, StandardTestDispatcher(testScheduler))

class ChunkWindowTest {
    @Test
    fun theFirstTakeReturnsAfterOneRoundTripAndOpensTheWindowOnlyThen() =
        runTest {
            val chunks = SlowChunks()
            val window = window(chunks, width = { 4 })

            val bytes = window.take(0)

            assertEquals(ROUND_TRIP_MS, currentTime, "a first frame waits for one fetch, never for the window")
            assertEquals(0, bytes[0].toInt())
            assertEquals(1, chunks.mostInFlight, "nothing is fetched ahead until the first chunk has arrived")
            runCurrent()
            assertEquals(listOf(0L, 1L, 2L, 3L), chunks.asked, "once it has, the window opens to its width")
            window.cancel()
        }

    @Test
    fun theWindowKeepsItsWidthInFlightAndNeverMore() =
        runTest {
            val chunks = SlowChunks()
            val window = window(chunks, width = { 4 })

            for (index in 0L until 20) assertEquals(index.toByte(), window.take(index)[0])

            assertEquals(4, chunks.mostInFlight)
            window.cancel()
        }

    @Test
    fun aChunkTheWindowAlreadyAskedForIsNeverAskedForAgain() =
        runTest {
            val chunks = SlowChunks()
            val window = window(chunks, width = { 4 })

            // The reader catches up with fetches still in flight on every take.
            for (index in 0L until 20) window.take(index)
            window.cancel()

            val wanted = chunks.asked.filter { it < 20 }
            assertEquals((0L until 20).toList(), wanted, "every chunk the reader took was asked for exactly once, in order")
        }

    @Test
    fun cancelStopsEveryFetchStillInFlight() =
        runTest {
            val chunks = SlowChunks()
            val window = window(chunks, width = { 4 })
            window.take(0)
            runCurrent() // 1, 2 and 3 are now on their way

            window.cancel()
            advanceUntilIdle()

            assertEquals(listOf(1L, 2L, 3L), chunks.cancelled.sorted())
            assertEquals(0, chunks.inFlight)
        }

    @Test
    fun theWindowStopsAtTheLastChunkOfItsRange() =
        runTest {
            val chunks = SlowChunks()
            // A gap of three chunks between two spans already on disk.
            val window = window(chunks, width = { 4 }, lastIndex = 12)

            for (index in 10L..12) window.take(index)
            advanceUntilIdle()

            assertEquals(listOf(10L, 11L, 12L), chunks.asked, "a chunk past the gap is on disk already")
        }

    @Test
    fun aWidthOfOneReadsOneChunkAtATimeAsBeforeThereWasAWindow() =
        runTest {
            val chunks = SlowChunks()
            val window = window(chunks, width = { 1 })

            for (index in 0L until 10) window.take(index)
            advanceUntilIdle()

            assertEquals(1, chunks.mostInFlight)
            assertEquals((0L until 10).toList(), chunks.asked)
            assertEquals(10 * ROUND_TRIP_MS, currentTime)
        }

    /**
     * Chunk 2 is refused — a flood wait comes back fast — while 3 and 4 are
     * still on their way. Every fetch after that goes one at a time: what
     * was in flight past the reader is cancelled rather than left to pile
     * onto an account Telegram is pushing back on, and the window opens
     * again only once a whole backoff passes with no failure.
     */
    @Test
    fun aFailedFetchCancelsTheRestOfTheWindowAndNarrowsItToOneUntilTheBackoffPasses() =
        runTest {
            var now = 0L
            val readAhead = ReadAhead(durationSecs = { 1L }, backoffMs = 60_000, clock = { now })
            val width: suspend () -> Int = { readAhead.width("s1", SET_BYTES) }
            val latency = mapOf(2L to 100L, 3L to 2_000L, 4L to 2_000L)
            val chunks = SlowChunks(failing = setOf(2L), latencyOf = { latency[it] ?: ROUND_TRIP_MS })
            val window = window(chunks, width, failed = readAhead::failed)

            window.take(0)
            window.take(1) // chunk 2 failed while this one waited
            assertFailsWith<IOException> { window.take(2) }
            window.cancel() // ExoPlayer closes the session to retry it
            advanceUntilIdle()

            assertEquals(listOf(3L, 4L), chunks.cancelled.sorted())
            assertEquals(listOf(0L, 1L, 2L, 3L, 4L), chunks.asked, "nothing new was asked for after the failure")

            val retried = SlowChunks()
            val retry = window(retried, width, failed = readAhead::failed)
            for (index in 2L until 8) retry.take(index)
            retry.cancel()
            assertEquals(1, retried.mostInFlight, "inside the backoff a high-bitrate set reads one chunk at a time")

            now += 60_000
            assertEquals(MAX_READ_AHEAD, readAhead.width("s1", SET_BYTES), "a whole backoff without a failure opens it again")
        }

    /**
     * What the window is for. At the box's ~0.5 s round trip one fetch at a
     * time is ~16 Mbit/s; a 4K film averages 27–31 and peaks at 40–65.
     */
    @Test
    fun fourInFlightMultiplyThroughputPastWhatA4kFilmNeeds() =
        runTest {
            val chunkCount = 40L

            suspend fun mbitPerSecond(width: Int): Double {
                val started = currentTime
                val window = window(SlowChunks(), width = { width })
                for (index in 0L until chunkCount) window.take(index)
                window.cancel()
                val seconds = (currentTime - started) / 1000.0
                return chunkCount * CHUNK_BYTES * 8 / seconds / 1_000_000
            }

            val sequential = mbitPerSecond(1)
            val windowed = mbitPerSecond(MAX_READ_AHEAD)

            assertTrue(sequential in 16.0..17.5, "one at a time is the box's measured ~16 Mbit/s, was $sequential")
            assertTrue(windowed >= 3.5 * sequential, "four in flight must nearly quadruple it: $sequential -> $windowed")
            assertTrue(windowed >= 40.0, "four in flight must reach 40 Mbit/s, was $windowed")
        }
}
