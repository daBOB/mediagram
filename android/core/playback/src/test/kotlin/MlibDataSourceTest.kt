package playback

import androidx.media3.common.C
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import data.CoreClient
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** byte at index i has value (i % 251).toByte() - a pattern that catches both a wrong offset and a wrong length. */
internal fun coreWithBytes(n: Int) =
    FakeCore(
        bytesOf = { off, len -> ByteArray(len) { ((off + it) % 251).toByte() } },
        totalSize = n.toLong(),
    )

/** [MlibDataSource] reading straight through [TelegramChunkSource], with no memo in front of it. */
internal fun dataSource(core: CoreClient) = MlibDataSource(core, TelegramChunkSource(core, PlaybackCounters()))

@RunWith(RobolectricTestRunner::class)
class MlibDataSourceTest {
    @Test
    fun openReportsTheWholeSetWhenNoLengthIsAsked() {
        val source = dataSource(coreWithBytes(1_000))
        val available = source.open(DataSpec(setUri("s1")))
        assertEquals(1_000L, available)
    }

    @Test
    fun aSeekStartsReadingAtTheRequestedOffset() {
        val source = dataSource(coreWithBytes(1_000))
        source.open(
            DataSpec
                .Builder()
                .setUri(setUri("s1"))
                .setPosition(400)
                .build(),
        )
        val buf = ByteArray(4)
        source.read(buf, 0, 4)
        assertEquals((400 % 251).toByte(), buf[0])
    }

    @Test
    fun openAtANonZeroPositionReturnsOnlyWhatIsLeft() {
        val source = dataSource(coreWithBytes(1_000))
        val available =
            source.open(
                DataSpec
                    .Builder()
                    .setUri(setUri("s1"))
                    .setPosition(400)
                    .build(),
            )
        assertEquals(600L, available)
    }

    @Test
    fun openPastTheEndOfTheSetThrowsRatherThanReturningANegativeLength() {
        val source = dataSource(coreWithBytes(1_000))
        assertFailsWith<DataSourceException> {
            source.open(
                DataSpec
                    .Builder()
                    .setUri(setUri("s1"))
                    .setPosition(1_001)
                    .build(),
            )
        }
    }

    @Test
    fun openHonoursAnExplicitLengthShorterThanTheSet() {
        val source = dataSource(coreWithBytes(1_000))
        val available =
            source.open(
                DataSpec
                    .Builder()
                    .setUri(setUri("s1"))
                    .setLength(50)
                    .build(),
            )
        assertEquals(50L, available)
    }

    @Test
    fun aBoundedReadNeverReturnsMoreThanTheDataSpecLength() {
        val source = dataSource(coreWithBytes(1_000))
        source.open(
            DataSpec
                .Builder()
                .setUri(setUri("s1"))
                .setLength(10)
                .build(),
        )
        val read = source.read(ByteArray(64), 0, 64)
        assertEquals(10, read)
    }

    @Test
    fun aBoundedLengthPastTheRealEndFailsInsteadOfFabricatingBytes() {
        val source = dataSource(coreWithBytes(1_000))
        source.open(
            DataSpec
                .Builder()
                .setUri(setUri("s1"))
                .setPosition(990)
                .setLength(100)
                .build(),
        )
        // The real 10 bytes between 990 and the set's end arrive normally.
        assertEquals(10, source.read(ByteArray(100), 0, 100))
        // The DataSpec claimed 90 more exist past the set's real end; the
        // core refuses rather than the source fabricating them.
        assertFailsWith<IOException> { source.read(ByteArray(90), 0, 90) }
    }

    @Test
    fun readingPastTheEndReportsEndOfInput() {
        val source = dataSource(coreWithBytes(8))
        source.open(DataSpec(setUri("s1")))
        source.read(ByteArray(8), 0, 8)
        assertEquals(C.RESULT_END_OF_INPUT, source.read(ByteArray(1), 0, 1))
    }

    @Test
    fun aZeroLengthReadReturnsZeroRatherThanEndOfInput() {
        val source = dataSource(coreWithBytes(1_000))
        source.open(DataSpec(setUri("s1")))
        assertEquals(0, source.read(ByteArray(0), 0, 0))
    }

    /**
     * The reason this class holds anything at all. A Matroska parser reads
     * its header a few bytes at a time, and every fetch costs a round trip
     * to resolve the part plus a whole chunk from Telegram — so asking per
     * read spends minutes where it should spend one request, and a film
     * never starts.
     */
    @Test
    fun aParserReadingAFewBytesAtATimeCostsOneFetch() {
        val core = coreWithBytes(1_000_000)
        val source = dataSource(core)
        source.open(DataSpec(setUri("s1")))

        repeat(200) { source.read(ByteArray(8), 0, 8) }

        assertEquals(1, core.reads, "200 small reads must not be 200 round trips")
    }

    /** What was held belongs to the old position and must not outlive a seek. */
    @Test
    fun aSeekIsNotServedBytesHeldForWhereItCameFrom() {
        val source = dataSource(coreWithBytes(1_000_000))
        source.open(DataSpec(setUri("s1")))
        source.read(ByteArray(8), 0, 8)

        source.open(
            DataSpec
                .Builder()
                .setUri(setUri("s1"))
                .setPosition(500_000)
                .build(),
        )

        val buf = ByteArray(1)
        source.read(buf, 0, 1)
        assertEquals((500_000 % 251).toByte(), buf[0])
    }

    @Test
    fun readAfterCloseReportsEndOfInputRatherThanCrashing() {
        val source = dataSource(coreWithBytes(1_000))
        source.open(DataSpec(setUri("s1")))
        source.close()
        assertEquals(C.RESULT_END_OF_INPUT, source.read(ByteArray(4), 0, 4))
    }

    @Test
    fun closeClearsTheReportedUri() {
        val source = dataSource(coreWithBytes(1_000))
        source.open(DataSpec(setUri("s1")))
        source.close()
        assertEquals(null, source.getUri())
    }
}
