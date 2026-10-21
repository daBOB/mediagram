package playback

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val SET_BYTES = 4L * CHUNK_BYTES

/** Answers a chunk only once the test lets it, and records what it was asked for. */
private class HeldUpstream : SetChunkSource {
    val asked = mutableListOf<Long>()
    val release = CompletableDeferred<Unit>()

    override suspend fun chunk(
        setId: String,
        index: Long,
        totalSize: Long,
    ): ByteArray {
        asked += index
        release.await()
        return ByteArray(expectedChunkLength(index, totalSize))
    }
}

private val instant = SetChunkSource { _, index, totalSize -> ByteArray(expectedChunkLength(index, totalSize)) }

class UpstreamTrafficTest {
    @Test
    fun `a preload fetch waits while the player is fetching and starts once it is done`() =
        runTest {
            val traffic = UpstreamTraffic(clock = { currentTime })
            val playing = HeldUpstream()
            val player = async { traffic.foreground(playing).chunk("film", 0, SET_BYTES) }
            runCurrent()

            val preloadAsked = mutableListOf<Long>()
            val preload =
                async {
                    traffic.background { setId, index, total ->
                        preloadAsked += index
                        instant.chunk(setId, index, total)
                    }.chunk("next", 0, SET_BYTES)
                }
            runCurrent()
            assertEquals(emptyList(), preloadAsked, "the preload must not fetch while the player is")
            assertTrue(traffic.snapshot().backgroundWaiting)
            assertEquals(1, traffic.snapshot().foregroundInFlight)

            playing.release.complete(Unit)
            player.await()
            runCurrent()
            assertEquals(emptyList(), preloadAsked, "not until the player has been quiet for a second")
            advanceTimeBy(QUIET_MS + 1)
            preload.await()
            assertEquals(listOf(0L), preloadAsked)
            assertFalse(traffic.snapshot().backgroundWaiting)
            assertEquals(0, traffic.snapshot().foregroundInFlight)
        }

    /** A player reading one chunk at a time has nothing in flight between two fetches; that gap is not a pause. */
    @Test
    fun `a preload does not slip in between two player fetches`() =
        runTest {
            val traffic = UpstreamTraffic(clock = { currentTime })
            val player = traffic.foreground(instant)
            player.chunk("film", 0, SET_BYTES)

            val preloadAsked = mutableListOf<Long>()
            val preload =
                async {
                    traffic.background { setId, index, total ->
                        preloadAsked += index
                        instant.chunk(setId, index, total)
                    }.chunk("next", 0, SET_BYTES)
                }
            repeat(5) {
                advanceTimeBy(QUIET_MS / 2)
                player.chunk("film", 1, SET_BYTES)
            }
            assertEquals(emptyList(), preloadAsked)

            advanceTimeBy(QUIET_MS + 1)
            preload.await()
            assertEquals(listOf(0L), preloadAsked)
        }

    @Test
    fun `the player never waits for a preload fetch already running`() =
        runTest {
            val traffic = UpstreamTraffic(clock = { currentTime })
            val preloading = HeldUpstream()
            launch { traffic.background(preloading).chunk("next", 0, SET_BYTES) }
            runCurrent()
            assertEquals(listOf(0L), preloading.asked)

            val bytes = traffic.foreground(instant).chunk("film", 0, SET_BYTES)
            assertEquals(CHUNK_BYTES, bytes.size)
            preloading.release.complete(Unit)
        }

    @Test
    fun `a failed player fetch still lets the preload go`() =
        runTest {
            val traffic = UpstreamTraffic(clock = { currentTime })
            val failing = SetChunkSource { _, _, _ -> throw IOException("dropped") }
            runCatching { traffic.foreground(failing).chunk("film", 0, SET_BYTES) }
            assertEquals(0, traffic.snapshot().foregroundInFlight)
            assertEquals(CHUNK_BYTES, traffic.background(instant).chunk("next", 0, SET_BYTES).size)
        }

    @Test
    fun `the rate is what arrived over the last whole seconds`() {
        var now = 10_000L
        val traffic = UpstreamTraffic(clock = { now })
        val player = traffic.foreground(instant)
        runBlocking {
            repeat(4) {
                player.chunk("film", 0, SET_BYTES)
                now += 1000
            }
        }
        assertEquals(CHUNK_BYTES * 8L, traffic.snapshot().foregroundBitsPerSecond)
        assertEquals(0, traffic.snapshot().backgroundBitsPerSecond)

        now += 5000
        assertEquals(0, traffic.snapshot().foregroundBitsPerSecond, "nothing arriving reads as nothing")
    }
}
