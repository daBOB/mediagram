package playback

import android.content.Context
import androidx.media3.common.C
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Uses Robolectric's real application [Context], not a mock: the
 * database-backed cache needs to actually open a SQLite database, which a
 * mocked `Context` can't provide.
 */
@RunWith(RobolectricTestRunner::class)
class PlayerFactoryTest {
    @Before
    fun resetTheSharedCache() {
        CacheProvider.resetForTest()
    }

    @After
    fun releaseTheSharedCache() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            CacheProvider.get(context).release()
            CacheProvider.resetForTest()
        }

    @Test
    fun theCacheWrapsTheMlibSource() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()

            val factory = cacheDataSourceFactory(context, PlaybackCounters()) { FakeCore() }

            assertTrue(factory.createDataSource() is CacheDataSource)
        }

    @Test
    fun repeatedBoundedReadsUseCachedBytesWithoutAnotherCoreRead() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val core = FakeCore(totalSize = 32_768) { offset, len -> ByteArray(len) { ((offset + it) % 251).toByte() } }
            val counters = PlaybackCounters()
            val factory = cacheDataSourceFactory(context, counters) { core }
            val start = 73L
            val length = 8193
            val request =
                DataSpec
                    .Builder()
                    .setUri(setUri("bounded-cache-read"))
                    .setPosition(start)
                    .setLength(length.toLong())
                    .build()

            fun readRange(): ByteArray {
                val source = factory.createDataSource()
                try {
                    assertEquals(length.toLong(), source.open(request))
                    val bytes = ByteArray(length)
                    var received = 0
                    while (received < length) {
                        val count = source.read(bytes, received, minOf(777, length - received))
                        assertTrue(count > 0, "the requested range must not end early")
                        received += count
                    }
                    assertEquals(C.RESULT_END_OF_INPUT, source.read(ByteArray(1), 0, 1))
                    return bytes
                } finally {
                    source.close()
                }
            }

            val expected = ByteArray(length) { ((start + it) % 251).toByte() }
            assertContentEquals(expected, readRange())
            val initialReads = core.reads
            assertTrue(initialReads > 0)
            assertEquals(length.toLong(), counters.totals().fromUpstreamBytes)
            assertEquals(0L, counters.totals().fromCacheBytes)
            assertEquals(length.toLong(), CacheProvider.occupancy(context).heldBytes)

            assertContentEquals(expected, readRange())
            assertEquals(initialReads, core.reads)
            assertEquals(length.toLong(), counters.totals().fromUpstreamBytes)
            assertEquals(length.toLong(), counters.totals().fromCacheBytes)
            assertEquals(0, counters.totals().failedReads)
        }

    /**
     * Both directions, explicitly. media3 defaults to five seconds back and
     * fifteen forward, so a bar whose buttons both say ten would be telling
     * a viewer something the player does not do.
     */
    @Test
    fun aSkipMovesTenSecondsInEitherDirection() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()

            val player = buildPlayer(context, PlaybackCounters()) { FakeCore() }

            try {
                assertEquals(10_000L, player.seekBackIncrement)
                assertEquals(10_000L, player.seekForwardIncrement)
            } finally {
                player.release()
            }
        }
}
