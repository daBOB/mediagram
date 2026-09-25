package playback

import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * [MlibDataSource]'s byte-for-byte correctness once a fetch is aligned to
 * [CHUNK_BYTES], and the two properties that alignment exists for: every
 * `core.read` offset lands on a chunk boundary, and [MlibDataSourceFactory]
 * shares one chunk across the data sources it opens.
 */
@RunWith(RobolectricTestRunner::class)
class MlibDataSourceChunkAlignmentTest {
    @Test
    fun readingFromTheStartToTheEndMatchesTheCoreByteForByte() {
        val total = 2 * ONE_MIB + 500_000
        val core = coreWithBytes(total)
        val source = dataSource(core)
        source.open(DataSpec(setUri("s1")))

        assertContentEquals(coreBytes(0, total), readAll(source, total))
    }

    @Test
    fun readingFromAnUnalignedPositionToTheEndMatchesTheCoreByteForByte() {
        val total = 3 * ONE_MIB
        val start = (ONE_MIB + 12_345).toLong()
        val core = coreWithBytes(total)
        val source = dataSource(core)
        source.open(DataSpec.Builder().setUri(setUri("s1")).setPosition(start).build())

        val want = (total - start).toInt()
        assertContentEquals(coreBytes(start, want), readAll(source, want))
    }

    @Test
    fun aBoundedReadCrossingAChunkBoundaryMatchesTheCoreByteForByte() {
        val total = 2 * ONE_MIB
        val start = (ONE_MIB - 100).toLong()
        val length = 300
        val core = coreWithBytes(total)
        val source = dataSource(core)
        source.open(
            DataSpec.Builder().setUri(setUri("s1")).setPosition(start).setLength(length.toLong()).build(),
        )

        assertContentEquals(coreBytes(start, length), readAll(source, length))
    }

    @Test
    fun theFinalShortTailMatchesTheCoreByteForByte() {
        val total = 2 * ONE_MIB + 777
        val start = (total - 2_000).toLong()
        val core = coreWithBytes(total)
        val source = dataSource(core)
        source.open(DataSpec.Builder().setUri(setUri("s1")).setPosition(start).build())

        val want = (total - start).toInt()
        assertContentEquals(coreBytes(start, want), readAll(source, want))
    }

    @Test
    fun everyCoreReadOffsetIsAMultipleOfChunkBytesEvenFromAnUnalignedOpen() {
        val total = 3 * ONE_MIB
        val start = (ONE_MIB + 12_345).toLong()
        val core = coreWithBytes(total)
        val source = dataSource(core)
        source.open(DataSpec.Builder().setUri(setUri("s1")).setPosition(start).build())

        readAll(source, (total - start).toInt())

        for (offset in core.requestedOffsets) {
            assertEquals(0L, offset % ONE_MIB, "offset $offset is not chunk-aligned")
        }
    }

    /**
     * The reason [MlibDataSourceFactory] carries a [ChunkMemo]:
     * `CacheDataSource` opens a new upstream read for every gap it fills,
     * and two opens landing in the same chunk must not pay for it twice.
     */
    @Test
    fun twoOpensOnTheSameFactoryInsideOneChunkFetchItOnce() {
        val core = coreWithBytes(2 * ONE_MIB)
        val factory = MlibDataSourceFactory(PlaybackCounters()) { core }

        val first = factory.createDataSource()
        first.open(DataSpec.Builder().setUri(setUri("s1")).setPosition(0).setLength(10).build())
        readAll(first, 10)
        first.close()

        val second = factory.createDataSource()
        second.open(DataSpec.Builder().setUri(setUri("s1")).setPosition(500_000).setLength(10).build())
        readAll(second, 10)
        second.close()

        assertEquals(1, core.reads, "both opens land inside the same chunk; the second must be served from the memo")
    }
}

/** [DataSource.read] until [want] bytes have arrived. */
private fun readAll(source: DataSource, want: Int): ByteArray {
    val out = ByteArray(want)
    var got = 0
    while (got < want) {
        val n = source.read(out, got, want - got)
        require(n > 0) { "expected $want bytes, got $got before the source stopped" }
        got += n
    }
    return out
}

/** [coreWithBytes]'s pattern at [offset] for [len] bytes, for comparison against a real read. */
private fun coreBytes(
    offset: Long,
    len: Int,
) = ByteArray(len) { ((offset + it) % 251).toByte() }

private const val ONE_MIB = 1024 * 1024
