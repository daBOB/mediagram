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

    @Test
    fun aShortReadAdvancesOnlyByWhatArrived() {
        val source = MlibDataSource(coreWithShortReads())
        source.open(DataSpec(setUri("s1")))
        val first = source.read(ByteArray(64), 0, 64)
        // Absolute expectation, not derived from the value under test:
        // coreWithShortReads() always returns exactly half of what a
        // 64-byte request asks for.
        assertEquals(32, first)
        val buf = ByteArray(4)
        source.read(buf, 0, 4)
        assertEquals((first % 251).toByte(), buf[0])
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
