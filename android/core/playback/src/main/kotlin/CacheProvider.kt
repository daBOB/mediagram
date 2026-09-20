package playback

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val CACHE_DIR_NAME = "mlib"
private const val CACHE_MAX_BYTES = 2L * 1024 * 1024 * 1024 // 2 GiB

/**
 * The one [SimpleCache] for the whole process, over `context.cacheDir/mlib`
 * with a 2 GiB LRU ceiling. `SimpleCache` throws at construction if a
 * second instance opens the same directory concurrently, so [get] is the
 * single choke point that guarantees only one is ever built.
 *
 * `SimpleCache`'s constructor blocks the calling thread while it opens its
 * index, so [get] is `suspend` and does that work on [dispatcher] (real
 * disk/database I/O, `Dispatchers.IO` by default) rather than whichever
 * thread first asks for the cache — which, through
 * `PlaybackModule.provideExoPlayerDeferred`, is the coroutine that
 * `hiltViewModel()` starts during composition on main. Once built, the
 * instance is reused without a dispatch.
 *
 * Backed by [StandaloneDatabaseProvider] rather than the legacy, file-only
 * index: the legacy index has no `CacheFileMetadataIndex`, so opening it
 * means statting every span file in the cache directory to rebuild
 * itself. The database-backed index persists its own metadata, so a warm
 * open is a handful of small reads rather than a directory walk — a
 * smaller amount of work to do off-thread, not a reason by itself to move
 * it off-thread. Opening a legacy-indexed directory this way migrates it
 * in place (media3 loads the legacy index once and rewrites it), so
 * nothing already cached is lost by the switch.
 */
object CacheProvider {

    @Volatile
    private var instance: SimpleCache? = null

    suspend fun get(context: Context, dispatcher: CoroutineDispatcher = Dispatchers.IO): SimpleCache {
        instance?.let { return it }
        return withContext(dispatcher) {
            synchronized(this@CacheProvider) {
                instance ?: buildCache(context).also { instance = it }
            }
        }
    }

    /** Test-only: clears the cached instance so a test can observe a fresh construction. */
    internal fun resetForTest() {
        instance = null
    }

    private fun buildCache(context: Context): SimpleCache = SimpleCache(
        File(context.cacheDir, CACHE_DIR_NAME),
        LeastRecentlyUsedCacheEvictor(CACHE_MAX_BYTES),
        StandaloneDatabaseProvider(context),
    )
}
