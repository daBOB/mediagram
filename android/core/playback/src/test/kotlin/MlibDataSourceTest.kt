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

private fun coreWithBytes(n: Int) = FakeCore(
    // byte at index i has value (i % 251).toByte() - a pattern that catches
    // both a wrong offset and a wrong length.
    bytesOf = { off, len -> ByteArray(len) { ((off + it) % 251).toByte() } },
    totalSize = n.toLong(),
)

/** Half of whatever is asked for arrives, forcing a caller to notice a short read. */
private fun coreWithShortReads() = FakeCore(
    totalSize = 1_000L,
    bytesOf = { off, len ->
        val short = maxOf(1, len / 2)
        ByteArray(short) { ((off + it) % 251).toByte() }
    },
)

@RunWith(RobolectricTestRunner::class)
class MlibDataSourceTest {

    @Test
    fun openReportsTheWholeSetWhenNoLengthIsAsked() {
        val source = MlibDataSource(coreWithBytes(1_000))
        val available = source.open(DataSpec(setUri("s1")))
        assertEquals(1_000L, available)
    }

    @Test
    fun aSeekStartsReadingAtTheRequestedOffset() {
        val source = MlibDataSource(coreWithBytes(1_000))
        source.open(DataSpec.Builder().setUri(setUri("s1")).setPosition(400).build())
        val buf = ByteArray(4)
        source.read(buf, 0, 4)
        assertEquals((400 % 251).toByte(), buf[0])
    }

    @Test
    fun openAtANonZeroPositionReturnsOnlyWhatIsLeft() {
        val source = MlibDataSource(coreWithBytes(1_000))
        val available = source.open(DataSpec.Builder().setUri(setUri("s1")).setPosition(400).build())
        assertEquals(600L, available)
    }

    @Test
    fun openPastTheEndOfTheSetThrowsRatherThanReturningANegativeLength() {
        val source = MlibDataSource(coreWithBytes(1_000))
        assertFailsWith<DataSourceException> {
            source.open(DataSpec.Builder().setUri(setUri("s1")).setPosition(1_001).build())
        }
    }

    @Test
    fun openHonoursAnExplicitLengthShorterThanTheSet() {
        val source = MlibDataSource(coreWithBytes(1_000))
        val available = source.open(DataSpec.Builder().setUri(setUri("s1")).setLength(50).build())
        assertEquals(50L, available)
    }

    @Test
    fun aBoundedReadNeverReturnsMoreThanTheDataSpecLength() {
        val source = MlibDataSource(coreWithBytes(1_000))
        source.open(DataSpec.Builder().setUri(setUri("s1")).setLength(10).build())
        val read = source.read(ByteArray(64), 0, 64)
        assertEquals(10, read)
    }

    @Test
    fun aBoundedLengthPastTheRealEndFailsInsteadOfFabricatingBytes() {
        val source = MlibDataSource(coreWithBytes(1_000))
        source.open(DataSpec.Builder().setUri(setUri("s1")).setPosition(990).setLength(100).build())
        // The real 10 bytes between 990 and the set's end arrive normally.
        assertEquals(10, source.read(ByteArray(100), 0, 100))
        // The DataSpec claimed 90 more exist past the set's real end; the
        // core refuses rather than the source fabricating them.
        assertFailsWith<IOException> { source.read(ByteArray(90), 0, 90) }
    }

    @Test
    fun readingPastTheEndReportsEndOfInput() {
        val source = MlibDataSource(coreWithBytes(8))
        source.open(DataSpec(setUri("s1")))
        source.read(ByteArray(8), 0, 8)
        assertEquals(C.RESULT_END_OF_INPUT, source.read(ByteArray(1), 0, 1))
    }

    @Test
    fun aZeroLengthReadReturnsZeroRatherThanEndOfInput() {
        val source = MlibDataSource(coreWithBytes(1_000))
        source.open(DataSpec(setUri("s1")))
        assertEquals(0, source.read(ByteArray(0), 0, 0))
    }

    /**
     * A fetch that comes back short is handed out in full and the next one
     * resumes where it ended. Counting bytes that never arrived would serve
     * the wrong part of the file from then on, silently.
     */
    @Test
    fun bytesStayContiguousAcrossAFetchThatCameBackShort() {
        val source = MlibDataSource(coreWithShortReads())
        source.open(DataSpec(setUri("s1")))

        // Past 500, which is all the first fetch of this 1,000-byte set
        // returns, so the run crosses a short fetch's boundary.
        val seen = ByteArray(600)
        var got = 0
        while (got < seen.size) {
            got += source.read(seen, got, seen.size - got)
        }

        for (i in seen.indices) {
            assertEquals((i % 251).toByte(), seen[i], "byte at $i")
        }
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
        val source = MlibDataSource(core)
        source.open(DataSpec(setUri("s1")))

        repeat(200) { source.read(ByteArray(8), 0, 8) }

        assertEquals(1, core.reads, "200 small reads must not be 200 round trips")
    }

    /** What was held belongs to the old position and must not outlive a seek. */
    @Test
    fun aSeekIsNotServedBytesHeldForWhereItCameFrom() {
        val source = MlibDataSource(coreWithBytes(1_000_000))
        source.open(DataSpec(setUri("s1")))
        source.read(ByteArray(8), 0, 8)

        source.open(DataSpec.Builder().setUri(setUri("s1")).setPosition(500_000).build())

        val buf = ByteArray(1)
        source.read(buf, 0, 1)
        assertEquals((500_000 % 251).toByte(), buf[0])
    }

    @Test
    fun readAfterCloseReportsEndOfInputRatherThanCrashing() {
        val source = MlibDataSource(coreWithBytes(1_000))
        source.open(DataSpec(setUri("s1")))
        source.close()
        assertEquals(C.RESULT_END_OF_INPUT, source.read(ByteArray(4), 0, 4))
    }

    @Test
    fun closeClearsTheReportedUri() {
        val source = MlibDataSource(coreWithBytes(1_000))
        source.open(DataSpec(setUri("s1")))
        source.close()
        assertEquals(null, source.getUri())
    }

    /**
     * The player is built once per process and outlives signing this device
     * out. A factory that captured its core would keep the previous
     * account's open, still-authorised connection reachable — and since the
     * catalog resolves by path, it would look up the *new* library's sets
     * and fetch them as the *old* account.
     */
    @Test
    fun eachReadSessionIsBoundToWhicheverCoreIsCurrentThen() {
        var current: CoreClient? = coreWithBytes(1_000)
        val factory = MlibDataSourceFactory { current }

        val before = factory.createDataSource().open(DataSpec(setUri("s1")))
        current = coreWithBytes(4_000)
        val after = factory.createDataSource().open(DataSpec(setUri("s1")))

        assertEquals(1_000L, before)
        assertEquals(4_000L, after, "a replaced core must be the one the next read session reads through")
    }

    @Test
    fun aReadSessionOpenedWithNoCoreFailsAsIoRatherThanReadingThroughAnOldOne() {
        val factory = MlibDataSourceFactory { null }

        assertFailsWith<IOException> { factory.createDataSource().open(DataSpec(setUri("s1"))) }
    }
}
