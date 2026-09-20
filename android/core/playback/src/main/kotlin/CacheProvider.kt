package playback

import android.content.Context
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

private const val CACHE_DIR_NAME = "mlib"
private const val CACHE_MAX_BYTES = 2L * 1024 * 1024 * 1024 // 2 GiB

/**
 * The one [SimpleCache] for the whole process, over `context.cacheDir/mlib`
 * with a 2 GiB LRU ceiling. `SimpleCache` throws at construction if a
 * second instance opens the same directory concurrently, so [get] is the
 * single choke point that guarantees only one is ever built.
 *
 * Uses the legacy, file-only cache index rather than the newer
 * database-backed one on purpose: the database variant needs a
 * `DatabaseProvider` backed by real `android.database.sqlite`, which does
 * not exist on a plain JVM unit test without Robolectric. The legacy index
 * is pure file I/O, which is what lets a JVM test build a real
 * `SimpleCache` against a mocked `Context`.
 */
object CacheProvider {

    @Volatile
    private var instance: SimpleCache? = null

    fun get(context: Context): SimpleCache =
        instance ?: synchronized(this) {
            instance ?: buildCache(context).also { instance = it }
        }

    @Suppress("DEPRECATION")
    private fun buildCache(context: Context): SimpleCache = SimpleCache(
        File(context.cacheDir, CACHE_DIR_NAME),
        LeastRecentlyUsedCacheEvictor(CACHE_MAX_BYTES),
    )
}
