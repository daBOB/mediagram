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
import androidx.media3.datasource.cache.SimpleCache
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val CACHE_DIR_NAME = "mlib"

/** What the disk cache is actually holding, against what it may hold — the System screen's Held row. */
data class CacheOccupancy(val heldBytes: Long, val budgetBytes: Long)

/**
 * The one [SimpleCache] for the whole process, over `context.cacheDir/mlib`
 * under an [AdjustableLruEvictor] seeded from [CacheBudgetSettings] — a 2
 * GiB LRU ceiling ([CACHE_MAX_BYTES]) until a viewer picks another one in
 * Settings via [setBudget]. `SimpleCache` throws at construction if a
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

    /**
     * The evictor backing [instance], kept alongside it because
     * `SimpleCache` never hands its evictor back out — [setBudget] and
     * [occupancy] both need to reach the live budget, not just the disk
     * usage.
     */
    @Volatile
    private var evictor: AdjustableLruEvictor? = null

    suspend fun get(context: Context, dispatcher: CoroutineDispatcher = Dispatchers.IO): SimpleCache {
        instance?.let { return it }
        return withContext(dispatcher) {
            // Read outside the lock: a race just repeats a cheap prefs
            // read, where reading it while holding the lock would call a
            // suspend function from the plain lambda `synchronized` takes.
            val budgetBytes = budgetSettings(context).read()
            synchronized(this@CacheProvider) {
                instance ?: buildCache(context, budgetBytes).also { instance = it }
            }
        }
    }

    /**
     * The live budget — [AdjustableLruEvictor.budgetBytes], not a constant
     * — read back against what [SimpleCache] is actually holding right
     * now. `cacheSpace` is a plain getter over the index's own running
     * total, not disk I/O, so this needs no dispatch beyond whatever [get]
     * itself needs to open the cache the first time.
     */
    suspend fun occupancy(context: Context, dispatcher: CoroutineDispatcher = Dispatchers.IO): CacheOccupancy {
        val cache = get(context, dispatcher)
        val budgetBytes = evictor?.budgetBytes ?: CACHE_MAX_BYTES
        return CacheOccupancy(heldBytes = cache.cacheSpace, budgetBytes = budgetBytes)
    }

    /**
     * Persists [bytes] as the new budget and evicts the live cache down to
     * it immediately — the pair a Settings row's size choice needs so
     * "Held" drops right away and the choice survives a restart. Builds
     * the cache first if nothing has opened it yet, same as [occupancy].
     */
    suspend fun setBudget(context: Context, bytes: Long, dispatcher: CoroutineDispatcher = Dispatchers.IO) {
        val cache = get(context, dispatcher)
        withContext(dispatcher) {
            val settings = budgetSettings(context)
            settings.write(bytes)
            val clampedBytes = settings.read()
            evictor?.setBudget(clampedBytes, cache)
        }
    }

    /** Test-only: clears the cached instance so a test can observe a fresh construction. */
    internal fun resetForTest() {
        instance = null
        evictor = null
    }

    private fun budgetSettings(context: Context): CacheBudgetSettings = PlainCacheBudgetSettings(context)

    private fun buildCache(context: Context, budgetBytes: Long): SimpleCache {
        val newEvictor = AdjustableLruEvictor(budgetBytes)
        evictor = newEvictor
        return SimpleCache(
            File(context.cacheDir, CACHE_DIR_NAME),
            newEvictor,
            StandaloneDatabaseProvider(context),
        )
    }
}
