package playback

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
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
 * Backed by [StandaloneDatabaseProvider] rather than the legacy, file-only
 * index: `SimpleCache`'s constructor blocks the calling thread while it
 * reads its index, and `get` is first called from `hiltViewModel`'s
 * injection on the main thread. The legacy index has no
 * `CacheFileMetadataIndex`, so it must stat every span file in the cache
 * directory to rebuild itself — on a warm, close-to-full 2 GiB cache made
 * of many small chunks, that scan is real work to do on main. The
 * database-backed index persists its own metadata, so a warm open is a
 * handful of small reads rather than a directory walk, and opening a
 * legacy-indexed directory this way migrates it in place (media3 loads
 * the legacy index once and rewrites it), so nothing already cached is
 * lost by the switch.
 */
object CacheProvider {

    @Volatile
    private var instance: SimpleCache? = null

    fun get(context: Context): SimpleCache =
        instance ?: synchronized(this) {
            instance ?: buildCache(context).also { instance = it }
        }

    private fun buildCache(context: Context): SimpleCache = SimpleCache(
        File(context.cacheDir, CACHE_DIR_NAME),
        LeastRecentlyUsedCacheEvictor(CACHE_MAX_BYTES),
        StandaloneDatabaseProvider(context),
    )
}
