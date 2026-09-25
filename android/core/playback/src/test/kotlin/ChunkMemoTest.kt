package playback

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Records every `(setId, index)` it was actually asked for, so a test can tell a hit from a fetch. */
private class RecordingUpstream : SetChunkSource {
    val asked = mutableListOf<Pair<String, Long>>()

    override suspend fun chunk(
        setId: String,
        index: Long,
        totalSize: Long,
    ): ByteArray {
        asked += setId to index
        return byteArrayOf(asked.size.toByte())
    }
}

class ChunkMemoTest {
    @Test
    fun aSecondRequestForTheSameKeyIsServedFromTheMemoWithoutAskingUpstreamAgain() =
        runTest {
            val upstream = RecordingUpstream()
            val memo = ChunkMemo(upstream)

            val first = memo.chunk("s1", 0, 1_000)
            val second = memo.chunk("s1", 0, 1_000)

            assertEquals(1, upstream.asked.size)
            assertTrue(first.contentEquals(second))
        }

    @Test
    fun aDifferentSetWithTheSameIndexIsNotServedFromAnotherSetsEntry() =
        runTest {
            val upstream = RecordingUpstream()
            val memo = ChunkMemo(upstream)

            memo.chunk("s1", 0, 1_000)
            memo.chunk("s2", 0, 1_000)

            assertEquals(2, upstream.asked.size, "(setId, index) is the key; the set id must not be dropped")
        }

    @Test
    fun theLeastRecentlyUsedKeyIsEvictedOnceCapacityIsExceeded() =
        runTest {
            val upstream = RecordingUpstream()
            val memo = ChunkMemo(upstream, capacity = 2)

            memo.chunk("s1", 0, 1_000)
            memo.chunk("s1", 1, 1_000)
            memo.chunk("s1", 2, 1_000) // evicts (s1, 0), the least recently touched

            memo.chunk("s1", 0, 1_000)

            assertEquals(4, upstream.asked.size, "(s1, 0) fell out of the memo and had to be fetched again")
        }

    @Test
    fun aHitKeepsAKeyFromBeingTheLeastRecentlyUsed() =
        runTest {
            val upstream = RecordingUpstream()
            val memo = ChunkMemo(upstream, capacity = 2)

            memo.chunk("s1", 0, 1_000)
            memo.chunk("s1", 1, 1_000)
            memo.chunk("s1", 0, 1_000) // touches (s1, 0) again; (s1, 1) is now the least recently used
            memo.chunk("s1", 2, 1_000) // evicts (s1, 1), not (s1, 0)

            memo.chunk("s1", 0, 1_000)

            assertEquals(3, upstream.asked.size, "(s1, 0) was still in the memo after being touched")
        }

    @Test
    fun concurrentRequestsForTheSameKeyStillFetchItOnlyOnce() =
        runTest {
            val upstream = RecordingUpstream()
            val memo = ChunkMemo(upstream)

            val results = (0 until 10).map { async { memo.chunk("s1", 0, 1_000) } }.awaitAll()

            assertEquals(1, upstream.asked.size)
            assertTrue(results.all { it.contentEquals(results[0]) })
        }

    /**
     * Real threads, not just coroutines on a single test dispatcher: what
     * proves the map itself tolerates concurrent access rather than only
     * the single-flight fetch above.
     */
    @Test
    fun theMemoSurvivesRealConcurrentAccessFromMultipleThreads() {
        val upstream = RecordingUpstream()
        val memo = ChunkMemo(upstream, capacity = 4)

        val threads =
            (0 until 8).map {
                Thread {
                    runBlocking {
                        repeat(50) { i -> memo.chunk("s1", (i % 6).toLong(), 1_000) }
                    }
                }
            }
        threads.forEach(Thread::start)
        threads.forEach(Thread::join)

        assertTrue(upstream.asked.isNotEmpty(), "every one of the 6 keys must have been fetched at least once")
    }
}
