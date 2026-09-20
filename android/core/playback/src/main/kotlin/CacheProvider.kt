// Every media3 symbol this module touches — SimpleCache, BaseDataSource,
// CacheDataSource, DefaultMediaSourceFactory — is marked @UnstableApi.
// That is media3's whole extension surface: writing a custom DataSource or
// running a disk cache is impossible without it, so opting in is the only
// way to have this module at all, not a shortcut around a warning.
//
// What the annotation actually promises is that these signatures may change
// in any minor release, which makes the media3 version in
// gradle/libs.versions.toml a deliberate pin rather than a floor. Raising
// it changes this module's source compatibility and belongs in a commit
// that rebuilds and reruns the playback tests, never in a routine
// dependency sweep.
//
// androidx.annotation.OptIn, not kotlin.OptIn: @UnstableApi is marked with
// androidx.annotation.RequiresOptIn, which Kotlin's own opt-in machinery
// does not recognise, so the Kotlin annotation compiles and silences
// nothing.
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

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

/** What the disk cache is actually holding, against what it may hold — the System screen's Held row. */
data class CacheOccupancy(val heldBytes: Long, val budgetBytes: Long)

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

    /**
     * The one number [CACHE_MAX_BYTES] is for, read back against what
     * [SimpleCache] is actually holding right now. `cacheSpace` is a plain
     * getter over the index's own running total, not disk I/O, so this
     * needs no dispatch beyond whatever [get] itself needs to open the
     * cache the first time.
     */
    suspend fun occupancy(context: Context, dispatcher: CoroutineDispatcher = Dispatchers.IO): CacheOccupancy =
        CacheOccupancy(heldBytes = get(context, dispatcher).cacheSpace, budgetBytes = CACHE_MAX_BYTES)

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
