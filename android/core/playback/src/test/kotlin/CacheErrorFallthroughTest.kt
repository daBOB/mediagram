@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package playback

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceUtil
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheSpan
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

/**
 * A disk cache that fails — a full disk, a card pulled mid-play — must cost
 * the player its cache, never its playback. Preload is the opposite: a
 * preload into a cache that cannot hold anything would download whole
 * episodes for nothing, on every trigger, so it has to keep failing loudly.
 *
 * The preload test guards that property, not which factory the writer
 * uses: media3's `CacheWriter` never retries, so even the forgiving
 * factory would throw there. That `buildPlayer` uses the forgiving one is
 * not tested here either — `DefaultMediaSourceFactory` does not hand its
 * data-source factory back out.
 */
@RunWith(RobolectricTestRunner::class)
class CacheErrorFallthroughTest {
    @get:Rule
    val folder = TemporaryFolder()

    /**
     * Past media3's 5 MiB sink fragment, so a span is committed in the
     * middle of the read — where a full disk strikes a real multi-GB set —
     * and not only when the source is closed.
     */
    private val totalSize = 6L * 1024 * 1024 + 12_345
    private val core = FakeCore(totalSize) { offset, len -> ByteArray(len) { ((offset + it) % 251).toByte() } }
    private val expected = ByteArray(totalSize.toInt()) { (it % 251).toByte() }
    private lateinit var cache: SimpleCache

    @Before
    fun openARealCache() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        cache = SimpleCache(folder.newFolder("mlib"), NoOpCacheEvictor(), StandaloneDatabaseProvider(context))
    }

    @After
    fun releaseIt() = cache.release()

    @Test
    fun aHealthyCacheServesTheWholeSet() =
        runTest {
            val factory = playbackDataSourceFactory(cache, PlaybackCounters()) { core }

            assertContentEquals(expected, readWholeSet(factory.createDataSource()))
        }

    @Test
    fun theWholeSetStillPlaysWhenTheCacheThrowsOnRead() =
        runTest {
            val broken = BrokenCache(cache, failReads = true)
            val factory = playbackDataSourceFactory(broken, PlaybackCounters()) { core }

            assertContentEquals(expected, readWholeSet(factory.createDataSource()))
        }

    @Test
    fun theWholeSetStillPlaysWhenTheCacheCannotStoreWhatItWrote() =
        runTest {
            val broken = BrokenCache(cache, failCommits = true)
            val factory = playbackDataSourceFactory(broken, PlaybackCounters()) { core }

            assertContentEquals(expected, readWholeSet(factory.createDataSource()))
        }

    @Test
    fun aPreloadIntoACacheThatCannotStoreAnythingFails() =
        runTest {
            val broken = BrokenCache(cache, failCommits = true)
            val writer = CacheDataSourceWriter(PlaybackCounters(), { core }) { broken }

            assertFailsWith<IOException> { writer.write(PreloadItem("preload-into-nothing", "Episode", totalSize)) }
        }

    /**
     * Reads the set to its end, re-opening once on failure the way
     * ExoPlayer's loader retries a load that threw, and closing quietly the
     * way it does. One retry, not a loop: a source that fails twice is not
     * falling through, it is broken.
     */
    private fun readWholeSet(source: DataSource): ByteArray {
        // What ExoPlayer's progressive loader asks for: fragmentation on, so the
        // sink commits a span every 5 MiB rather than once at close.
        val request =
            DataSpec
                .Builder()
                .setUri(setUri("fall-through"))
                .setFlags(DataSpec.FLAG_ALLOW_CACHE_FRAGMENTATION)
                .build()
        return try {
            source.open(request)
            DataSourceUtil.readToEnd(source)
        } catch (_: IOException) {
            DataSourceUtil.closeQuietly(source)
            source.open(request)
            DataSourceUtil.readToEnd(source)
        } finally {
            DataSourceUtil.closeQuietly(source)
        }
    }
}

/**
 * A real [SimpleCache] with one failure switched on: [failReads] throws
 * where a read or write first reaches the cache, [failCommits] where a
 * finished span is committed — which is where a full disk actually
 * surfaces, after the bytes were written, not before.
 */
private class BrokenCache(
    private val real: Cache,
    private val failReads: Boolean = false,
    private val failCommits: Boolean = false,
) : Cache by real {
    override fun startReadWrite(key: String, position: Long, length: Long): CacheSpan {
        if (failReads) throw Cache.CacheException("the cache is unreadable")
        return real.startReadWrite(key, position, length)
    }

    override fun startReadWriteNonBlocking(key: String, position: Long, length: Long): CacheSpan? {
        if (failReads) throw Cache.CacheException("the cache is unreadable")
        return real.startReadWriteNonBlocking(key, position, length)
    }

    override fun commitFile(file: File, length: Long) {
        if (failCommits) throw Cache.CacheException("no space left on device")
        real.commitFile(file, length)
    }
}
