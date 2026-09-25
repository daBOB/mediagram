package playback

import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SetChunkSourceTest {
    @Test
    fun theFinalChunkIsShortByExactlyWhatIsLeftOfTheSet() =
        runTest {
            val total = 2L * CHUNK_BYTES + 777
            val core =
                FakeCore(totalSize = total) { offset, len -> ByteArray(len) { ((offset + it) % 251).toByte() } }
            val source = TelegramChunkSource(core, PlaybackCounters())

            val chunk = source.chunk("s1", index = 2, totalSize = total)

            assertEquals(777, chunk.size)
        }

    @Test
    fun aFullChunkAsksForExactlyChunkBytes() =
        runTest {
            val total = 3L * CHUNK_BYTES
            val core =
                FakeCore(totalSize = total) { offset, len -> ByteArray(len) { ((offset + it) % 251).toByte() } }
            val source = TelegramChunkSource(core, PlaybackCounters())

            val chunk = source.chunk("s1", index = 1, totalSize = total)

            assertEquals(CHUNK_BYTES, chunk.size)
        }

    @Test
    fun aCoreFailureIsWrappedAsIoExceptionAndRecordedAsAReadFailure() =
        runTest {
            // offset 0 is at or past this core's own totalSize (0), so read() throws.
            val core = FakeCore(totalSize = 0)
            val counters = PlaybackCounters()
            val source = TelegramChunkSource(core, counters)

            assertFailsWith<IOException> { source.chunk("s1", index = 0, totalSize = 100) }

            assertEquals(1, counters.totals().failedReads)
            assertEquals(0, counters.totals().fetches, "a failed read must not also count as a fetch")
        }
}
