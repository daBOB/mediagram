package playback

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import testing.FakeCore
import uniffi.mediagram_core.CoreInterface
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

/**
 * The subtitle hold before a preload is best-effort, but a preload
 * cancelled while it runs must stop there — not go on to open the disk
 * cache and start downloading a title nobody wants any more.
 */
class CacheDataSourceWriterTest {
    @Test
    fun aPreloadCancelledDuringTheSubtitleHoldNeverOpensTheCache() =
        runTest {
            val core =
                object : CoreInterface by FakeCore() {
                    override suspend fun holdSubtitles(setId: String): Boolean = throw CancellationException("stopped")
                }
            var opened = false
            val writer =
                CacheDataSourceWriter(PlaybackCounters(), { core }) {
                    opened = true
                    error("the cache was opened after cancellation")
                }

            assertFailsWith<CancellationException> { writer.write(PreloadItem("cancelled", "Episode", 1L)) }
            assertFalse(opened)
        }
}
