package playback

import androidx.media3.datasource.DataSpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import testing.FakeCore
import kotlin.test.Test
import kotlin.test.assertEquals

/** Fifty whole chunks. */
private const val SET_BYTES = 50L * CHUNK_BYTES

/**
 * Answers the chunks in [served] at once and holds every other one until it
 * is cancelled, recording both — so what a session asked for ahead, and
 * what closing it cancelled, can be read straight off.
 */
private class HeldChunks(
    private val served: Set<Long>,
) : SetChunkSource {
    val asked = mutableSetOf<Long>()
    val cancelled = mutableSetOf<Long>()

    override suspend fun chunk(
        setId: String,
        index: Long,
        totalSize: Long,
    ): ByteArray {
        asked += index
        if (index in served) return ByteArray(expectedChunkLength(index, totalSize)) { index.toByte() }
        try {
            awaitCancellation()
        } catch (e: CancellationException) {
            cancelled += index
            throw e
        }
    }
}

/**
 * The window as the player reaches it, through [MlibDataSource]'s own
 * open, read and close. Fetches run [Dispatchers.Unconfined], so each
 * starts — and records itself — before the call that launched it returns,
 * and these tests need no waiting.
 */
@RunWith(RobolectricTestRunner::class)
class MlibDataSourceReadAheadTest {
    private fun source(
        chunks: SetChunkSource,
        durationSecs: Long,
    ) = MlibDataSource(
        FakeCore(totalSize = SET_BYTES),
        chunks,
        ReadAhead(durationSecs = { durationSecs }),
        Dispatchers.Unconfined,
    )

    private fun MlibDataSource.openAt(position: Long) =
        open(DataSpec.Builder().setUri(setUri("s1")).setPosition(position).build())

    private fun MlibDataSource.readByte(): Byte = ByteArray(1).also { read(it, 0, 1) }[0]

    @Test
    fun aHighBitrateSetFetchesAheadOfTheReaderAndClosingCancelsWhatIsInFlight() {
        val chunks = HeldChunks(served = setOf(0L))
        val source = source(chunks, durationSecs = 1)

        source.openAt(0)
        assertEquals(0, source.readByte().toInt())
        assertEquals(setOf(0L, 1L, 2L, 3L), chunks.asked)

        source.close()
        assertEquals(setOf(1L, 2L, 3L), chunks.cancelled)
    }

    @Test
    fun aSeekCancelsTheWindowItLeftAndOpensOneWhereItLands() {
        val chunks = HeldChunks(served = setOf(0L, 20L))
        val source = source(chunks, durationSecs = 1)
        source.openAt(0)
        source.readByte()

        // How ExoPlayer seeks: the session closes, and a new one opens.
        source.close()
        source.openAt(20L * CHUNK_BYTES)

        assertEquals(20, source.readByte().toInt())
        assertEquals(setOf(1L, 2L, 3L), chunks.cancelled)
        assertEquals(setOf(0L, 1L, 2L, 3L, 20L, 21L, 22L, 23L), chunks.asked)
        source.close()
    }

    @Test
    fun aLowBitrateSetFetchesNothingAhead() {
        val chunks = HeldChunks(served = setOf(0L))
        // 50 MiB over a day: well under a megabit a second.
        val source = source(chunks, durationSecs = 86_400)

        source.openAt(0)
        source.readByte()

        assertEquals(setOf(0L), chunks.asked)
        source.close()
    }
}
