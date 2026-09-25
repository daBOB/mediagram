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
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext

/** What the disk cache is actually holding and where — the System screen's Held and Where rows. */
data class CacheOccupancy(
    val heldBytes: Long,
    val budgetBytes: Long,
    val volumeLabel: String,
    val fellBack: Boolean,
    val capBytes: Long,
)

/**
 * The one [SimpleCache] for the whole process, over the chosen volume's
 * `mlib` directory ([cacheVolumes]) under an [AdjustableLruEvictor] seeded
 * from [CacheBudgetSettings] and clamped to what that volume can hold
 * ([budgetCap]) — the opening itself is [openCache]'s. `SimpleCache` throws
 * at construction if a second instance opens the same directory
 * concurrently, so [get] is the single choke point that guarantees only
 * one is ever built.
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

    /** Which volume [instance] actually landed on, and the cap it was opened against. Fixed for the process, like [instance] itself: moving the cache needs a restart. */
    @Volatile
    private var opened: OpenedCache? = null

    @Volatile
    private var cleanupJob: Job? = null

    /**
     * Test seam: which volumes are on offer. Real callers read them off the
     * platform through [cacheVolumes]; Robolectric has no removable storage
     * to fake through Android's own APIs, so a test swaps this instead,
     * the same way [CacheDataSourceWriter]'s internal constructor swaps
     * `openCache`.
     */
    @Volatile
    internal var volumesFor: (Context) -> List<CacheVolume> = ::cacheVolumes

    suspend fun get(
        context: Context,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
    ): SimpleCache {
        instance?.let { return it }
        return withContext(dispatcher) {
            // Read outside the lock: a race just repeats cheap work, where
            // doing it while holding the lock would call suspend functions
            // from the plain lambda `synchronized` takes.
            val volumes = volumesFor(context)
            val chosenId = volumeSettings(context).read()
            val budgetBytes = budgetSettings(context).read()
            val databaseProvider = StandaloneDatabaseProvider(context)
            val built =
                synchronized(this@CacheProvider) {
                    if (instance != null) {
                        null
                    } else {
                        openCache(volumes, chosenId, budgetBytes, databaseProvider).also {
                            instance = it.cache
                            evictor = it.evictor
                            opened = it
                        }
                    }
                }
            // Off the open path and after it: a card full of another
            // title's leftovers never delays this cache's first frame.
            built?.let { cleanupJob = scheduleStaleVolumeCleanup(dispatcher, databaseProvider, volumes, it.volume) }
            instance!!
        }
    }

    /**
     * The live budget — [AdjustableLruEvictor.budgetBytes], not a constant
     * — read back against what [SimpleCache] is actually holding right
     * now. `cacheSpace` is a plain getter over the index's own running
     * total, not disk I/O, so this needs no dispatch beyond whatever [get]
     * itself needs to open the cache the first time.
     */
    suspend fun occupancy(
        context: Context,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
    ): CacheOccupancy {
        val cache = get(context, dispatcher)
        val budgetBytes = evictor?.budgetBytes ?: CACHE_MAX_BYTES
        val location = opened
        return CacheOccupancy(
            heldBytes = cache.cacheSpace,
            budgetBytes = budgetBytes,
            volumeLabel = location?.volume?.label ?: "Internal storage",
            fellBack = location?.fellBack ?: false,
            capBytes = location?.capBytes ?: budgetBytes,
        )
    }

    /**
     * Persists [bytes] as the new budget and evicts the live cache down to
     * it immediately — the pair a Settings row's size choice needs so
     * "Held" drops right away and the choice survives a restart. Builds
     * the cache first if nothing has opened it yet, same as [occupancy].
     */
    suspend fun setBudget(
        context: Context,
        bytes: Long,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
    ) {
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
        opened = null
        cleanupJob = null
        volumesFor = ::cacheVolumes
    }

    /** Test-only: waits for the stale-volume sweep [get] kicked off, so a test can assert on its result. */
    internal suspend fun awaitCleanupForTest() {
        cleanupJob?.join()
    }

    private fun budgetSettings(context: Context): CacheBudgetSettings = PlainCacheBudgetSettings(context)

    private fun volumeSettings(context: Context): CacheVolumeSettings = PlainCacheVolumeSettings(context)
}
