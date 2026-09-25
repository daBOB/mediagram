package playback

import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals

internal val LAN_SERVER = LanServer("http://192.168.1.5:7788", "192.168.1.5:7788")

/** Records every enqueue; never actually writes anywhere. Shared with [LanFirstChunkSourceFallbackTest]. */
internal class RecordingWriter : LanChunkWriter {
    val calls = mutableListOf<Triple<String, Long, ByteArray>>()

    override fun enqueue(
        setId: String,
        index: Long,
        total: Long,
        bytes: ByteArray,
    ) {
        calls.add(Triple(setId, index, bytes))
    }
}

internal class FakeLan(
    private val onGet: suspend (String, String, Long, Int) -> ByteArray? = { _, _, _, _ -> null },
) : LanChunkProtocol {
    var getCalls = 0

    override suspend fun get(
        baseUrl: String,
        setId: String,
        index: Long,
        expectedLength: Int,
    ): ByteArray? {
        getCalls++
        return onGet(baseUrl, setId, index, expectedLength)
    }

    override suspend fun put(
        baseUrl: String,
        token: String,
        setId: String,
        index: Long,
        total: Long,
        body: ByteArray,
    ): LanPutResult = LanPutResult.Stored

    override suspend fun verify(baseUrl: String): Boolean = true

    override suspend fun status(baseUrl: String): LanServerStatus? = null
}

internal fun telegramFake(bytes: ByteArray): SetChunkSource = SetChunkSource { _, _, _ -> bytes }

internal val TELEGRAM_BYTES = ByteArray(CHUNK_BYTES) { 7 }

/** The hit/miss/down-window state machine. Failure and write-gating branches live in [LanFirstChunkSourceFallbackTest]. */
class LanFirstChunkSourceTest {
    @Test
    fun aLanHitServesTheBytesAndNeverTouchesTelegram() =
        runTest {
            var telegramCalls = 0
            val telegram = SetChunkSource { _, _, _ -> telegramCalls++; TELEGRAM_BYTES }
            val lan = FakeLan(onGet = { _, _, _, _ -> TELEGRAM_BYTES })
            val counters = PlaybackCounters()
            val source =
                LanFirstChunkSource(
                    lan = lan,
                    telegram = telegram,
                    network = UnmeteredNetworkCheck { true },
                    server = { LAN_SERVER },
                    writes = RecordingWriter(),
                    counters = counters,
                )

            val result = source.chunk("s1", 0, CHUNK_BYTES.toLong())

            assertEquals(TELEGRAM_BYTES, result)
            assertEquals(0, telegramCalls, "a hit must never fall through to Telegram")
            assertEquals(1, counters.totals().lanHits)
            assertEquals(0, counters.totals().fetches, "a hit must not be counted as a Telegram fetch")
        }

    @Test
    fun aMissGoesToTelegramAndEnqueuesAWrite() =
        runTest {
            val telegram = telegramFake(TELEGRAM_BYTES)
            val writer = RecordingWriter()
            val source =
                LanFirstChunkSource(
                    lan = FakeLan(onGet = { _, _, _, _ -> null }),
                    telegram = telegram,
                    network = UnmeteredNetworkCheck { true },
                    server = { LAN_SERVER },
                    writes = writer,
                    counters = PlaybackCounters(),
                )

            val result = source.chunk("s1", 0, CHUNK_BYTES.toLong())

            assertEquals(TELEGRAM_BYTES, result)
            assertEquals(1, writer.calls.size)
            assertEquals("s1", writer.calls.single().first)
        }

    @Test
    fun theServerIsNotRetriedWithinTheDownWindow() =
        runTest {
            var now = 0L
            var lanCalls = 0
            val lan =
                FakeLan(
                    onGet = { _, _, _, _ ->
                        lanCalls++
                        throw IOException("down")
                    },
                )
            val source =
                LanFirstChunkSource(
                    lan = lan,
                    telegram = telegramFake(TELEGRAM_BYTES),
                    network = UnmeteredNetworkCheck { true },
                    server = { LAN_SERVER },
                    writes = RecordingWriter(),
                    counters = PlaybackCounters(),
                    downWindowMs = 60_000L,
                    clock = { now },
                )

            source.chunk("s1", 0, CHUNK_BYTES.toLong())
            assertEquals(1, lanCalls)

            now = 59_000L
            source.chunk("s1", 1, CHUNK_BYTES.toLong() * 2)
            assertEquals(1, lanCalls, "the server must not be asked again inside the down window")

            now = 60_001L
            source.chunk("s1", 2, CHUNK_BYTES.toLong() * 3)
            assertEquals(2, lanCalls, "the window has lapsed; the server is worth asking again")
        }

    @Test
    fun aMissIsCountedAndDoesNotMarkTheServerDown() =
        runTest {
            var now = 0L
            var lanCalls = 0
            val lan = FakeLan(onGet = { _, _, _, _ -> lanCalls++; null })
            val counters = PlaybackCounters()
            val source =
                LanFirstChunkSource(
                    lan = lan,
                    telegram = telegramFake(TELEGRAM_BYTES),
                    network = UnmeteredNetworkCheck { true },
                    server = { LAN_SERVER },
                    writes = RecordingWriter(),
                    counters = counters,
                    clock = { now },
                )

            source.chunk("s1", 0, CHUNK_BYTES.toLong())
            now = 1_000L
            source.chunk("s1", 1, CHUNK_BYTES.toLong() * 2)

            assertEquals(2, lanCalls, "a miss must not mark the server down")
            assertEquals(2, counters.totals().lanMisses)
        }
}
