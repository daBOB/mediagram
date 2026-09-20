package playback

import androidx.media3.common.C
import androidx.media3.datasource.DataSpec
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals

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
    fun readingPastTheEndReportsEndOfInput() {
        val source = MlibDataSource(coreWithBytes(8))
        source.open(DataSpec(setUri("s1")))
        source.read(ByteArray(8), 0, 8)
        assertEquals(C.RESULT_END_OF_INPUT, source.read(ByteArray(1), 0, 1))
    }

    @Test
    fun aShortReadAdvancesOnlyByWhatArrived() {
        val source = MlibDataSource(coreWithShortReads())
        source.open(DataSpec(setUri("s1")))
        val first = source.read(ByteArray(64), 0, 64)
        val buf = ByteArray(4)
        source.read(buf, 0, 4)
        assertEquals((first % 251).toByte(), buf[0])
    }
}
