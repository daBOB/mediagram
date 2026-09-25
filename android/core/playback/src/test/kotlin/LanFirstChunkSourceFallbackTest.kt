package playback

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Every branch that must fall back to Telegram without ever asking
 * [LanChunkWriter] to mirror a chunk back — a skip (metered, no server), a
 * failure (an exception on the LAN path), or a read this device is not
 * even allowed to make (mobile data) are none of them a real miss, and
 * only a real miss ([LanFirstChunkSourceTest.aMissGoesToTelegramAndEnqueuesAWrite])
 * is worth sharing.
 */
class LanFirstChunkSourceFallbackTest {
    @Test
    fun anIoExceptionMarksTheServerDownAndFallsThroughToTelegramWithoutEnqueuing() =
        runTest {
            var now = 0L
            val lan = FakeLan(onGet = { _, _, _, _ -> throw IOException("connect timed out") })
            val writer = RecordingWriter()
            val source =
                LanFirstChunkSource(
                    lan = lan,
                    telegram = telegramFake(TELEGRAM_BYTES),
                    network = UnmeteredNetworkCheck { true },
                    server = { LAN_SERVER },
                    writes = writer,
                    counters = PlaybackCounters(),
                    clock = { now },
                )

            val result = source.chunk("s1", 0, CHUNK_BYTES.toLong())

            assertEquals(TELEGRAM_BYTES, result)
            assertTrue(writer.calls.isEmpty(), "a failure was never asked to share back — there is nothing real to mirror")
        }

    /** [LanFirstChunkSource] must never itself decide a reader's cancellation should instead fall back to Telegram. */
    @Test
    fun aCancellationDuringTheLanReadIsNeverSwallowed() =
        runTest {
            val lan = FakeLan(onGet = { _, _, _, _ -> throw CancellationException("closed") })
            val source =
                LanFirstChunkSource(
                    lan = lan,
                    telegram = telegramFake(TELEGRAM_BYTES),
                    network = UnmeteredNetworkCheck { true },
                    server = { LAN_SERVER },
                    writes = RecordingWriter(),
                    counters = PlaybackCounters(),
                )

            assertFailsWith<CancellationException> { source.chunk("s1", 0, CHUNK_BYTES.toLong()) }
        }

    /**
     * `catch (e: Exception)`, not only `IOException` — defence in depth for
     * whatever a LAN implementation might throw that is not cleanly a
     * network failure. It must cost the reader nothing but the fallback.
     */
    @Test
    fun anUnexpectedExceptionOnTheLanPathStillFallsThroughToTelegramWithoutEnqueuing() =
        runTest {
            val lan = FakeLan(onGet = { _, _, _, _ -> throw IllegalStateException("unexpected") })
            val writer = RecordingWriter()
            val source =
                LanFirstChunkSource(
                    lan = lan,
                    telegram = telegramFake(TELEGRAM_BYTES),
                    network = UnmeteredNetworkCheck { true },
                    server = { LAN_SERVER },
                    writes = writer,
                    counters = PlaybackCounters(),
                )

            val result = source.chunk("s1", 0, CHUNK_BYTES.toLong())

            assertEquals(TELEGRAM_BYTES, result)
            assertTrue(writer.calls.isEmpty())
        }

    @Test
    fun aMeteredNetworkNeverTouchesTheServerOrEnqueuesAWrite() =
        runTest {
            val lan = FakeLan()
            val writer = RecordingWriter()
            val source =
                LanFirstChunkSource(
                    lan = lan,
                    telegram = telegramFake(TELEGRAM_BYTES),
                    network = UnmeteredNetworkCheck { false },
                    server = { LAN_SERVER },
                    writes = writer,
                    counters = PlaybackCounters(),
                )

            source.chunk("s1", 0, CHUNK_BYTES.toLong())

            assertEquals(0, lan.getCalls)
            assertTrue(writer.calls.isEmpty(), "a chunk read over mobile data must never be mirrored back over it")
        }

    @Test
    fun noKnownServerNeverTouchesTheLanClientOrEnqueuesAWrite() =
        runTest {
            val lan = FakeLan()
            val writer = RecordingWriter()
            val source =
                LanFirstChunkSource(
                    lan = lan,
                    telegram = telegramFake(TELEGRAM_BYTES),
                    network = UnmeteredNetworkCheck { true },
                    server = { null },
                    writes = writer,
                    counters = PlaybackCounters(),
                )

            source.chunk("s1", 0, CHUNK_BYTES.toLong())

            assertEquals(0, lan.getCalls)
            assertTrue(writer.calls.isEmpty())
        }

    @Test
    fun aServerSkippedInsideTheDownWindowNeverEnqueuesAWriteEither() =
        runTest {
            var now = 0L
            val lan = FakeLan(onGet = { _, _, _, _ -> throw IOException("down") })
            val writer = RecordingWriter()
            val source =
                LanFirstChunkSource(
                    lan = lan,
                    telegram = telegramFake(TELEGRAM_BYTES),
                    network = UnmeteredNetworkCheck { true },
                    server = { LAN_SERVER },
                    writes = writer,
                    counters = PlaybackCounters(),
                    downWindowMs = 60_000L,
                    clock = { now },
                )
            source.chunk("s1", 0, CHUNK_BYTES.toLong())
            writer.calls.clear()

            now = 1_000L
            source.chunk("s1", 1, CHUNK_BYTES.toLong() * 2)

            assertTrue(writer.calls.isEmpty(), "a chunk fetched while the server is down-flagged must never be mirrored back to it")
        }
}
