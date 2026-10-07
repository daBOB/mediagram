// See core/playback's CacheProvider.kt for why @UnstableApi is opted into
// at file scope: CacheProvider.get/removeResource are both marked with it.
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package player.di

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.exoplayer.ExoPlayer
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import data.CatalogRepository
import data.CoreProvider
import data.di.MainThreadScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import playback.CacheBudgetQuery
import playback.CacheDataSourceWriter
import playback.CacheProvider
import playback.DownloadLane
import playback.FilmPreloader
import playback.FilmPreloading
import playback.HeldSetsQuery
import playback.LanCacheRuntime
import playback.PlaybackCounters
import playback.SeriesPreloader
import playback.SeriesPreloading
import playback.SystemUnmeteredNetworkCheck
import playback.fitsFilmPreloadBudget
import playback.fitsInPreloadBudget
import playback.setUri
import player.ActivePlayback
import player.PreloadService
import java.util.concurrent.Executors
import javax.inject.Singleton

/**
 * The series and film preloaders, and everything they share. They stay in
 * feature:player though the catalogue injects them too: the film preloader
 * is built on [ActivePlayback] and starts [PreloadService], both this
 * module's own.
 */
@Module
@InstallIn(SingletonComponent::class)
object PreloadModule {
    @Provides
    @Singleton
    fun provideDownloadLane(): DownloadLane = DownloadLane()

    /** Wraps [CacheProvider.occupancy] — the same live figure [provideFilmPreloader]'s own `fits` lambda already reads — so a NeedsSpace label can name it without a test needing a real disk cache behind it. */
    @Provides
    @Singleton
    fun provideCacheBudgetQuery(
        @ApplicationContext context: Context,
    ): CacheBudgetQuery = CacheBudgetQuery { CacheProvider.occupancy(context).budgetBytes }

    /**
     * The one strict writer the series and film preloaders both take into
     * the cache through — see [CacheDataSourceWriter]'s own doc for why
     * sharing it (rather than each building its own) is what makes
     * [DownloadLane] alone enough to keep their writes from overlapping.
     * Its own [PlaybackCounters], not the playing title's, the same
     * reasoning [provideSeriesPreloader] already documents.
     */
    @Provides
    @Singleton
    fun provideCacheDataSourceWriter(
        @ApplicationContext context: Context,
        coreProvider: CoreProvider,
        lan: LanCacheRuntime,
    ): CacheDataSourceWriter = CacheDataSourceWriter(context, PlaybackCounters(), lan) { coreProvider.core.value }

    /**
     * Built here rather than constructor-injected into [SeriesPreloader]
     * itself: `:core:playback` carries no Hilt plugin of its own, so every
     * real dependency — the dedicated thread, the network check, the
     * budget read — is assembled once, in this module.
     */
    @Provides
    @Singleton
    fun provideSeriesPreloader(
        @ApplicationContext context: Context,
        heldSets: HeldSetsQuery,
        writer: CacheDataSourceWriter,
        lane: DownloadLane,
        @MainThreadScope scope: CoroutineScope,
    ): SeriesPreloading {
        // Its own dedicated thread, apart from Dispatchers.IO's shared
        // pool: MlibDataSource's own reads block with `runBlocking`, and a
        // preload must never be what starves the pool the rest of the
        // app's IO shares.
        val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val network = SystemUnmeteredNetworkCheck(context)
        return SeriesPreloader(
            scope = scope,
            dispatcher = dispatcher,
            writer = writer,
            isHeld = { item -> heldSets.isHeld(item.setId, item.totalBytes) },
            fits = { candidateBytes, currentBytes ->
                network.isUnmetered() &&
                    CacheProvider.occupancy(context).let { occupancy ->
                        fitsInPreloadBudget(occupancy.heldBytes, currentBytes, candidateBytes, occupancy.budgetBytes)
                    }
            },
            log = { line, e -> if (e == null) Log.d(PRELOAD_LOG_TAG, line) else Log.w(PRELOAD_LOG_TAG, line, e) },
            lane = lane,
        )
    }

    /** See [ActivePlayback]'s own doc — [Lazy] is what keeps asking for this from forcing the player to build. */
    @Provides
    @Singleton
    fun provideActivePlayback(
        playerDeferred: Lazy<@JvmSuppressWildcards Deferred<ExoPlayer>>,
        catalogRepository: CatalogRepository,
        @MainThreadScope scope: CoroutineScope,
    ): ActivePlayback = ActivePlayback(playerDeferred, catalogRepository, scope)

    /**
     * See [FilmPreloader]'s own doc for the engine; this only assembles its
     * real dependencies, the same shape [provideSeriesPreloader] already
     * takes. The `hasWork` subscription below is what actually starts
     * [PreloadService] the first time anything is enqueued — the service
     * itself only ever stops what it started, and a failed start (Android
     * may refuse one from the background — `ForegroundServiceStartNotAllowedException`,
     * API 31+) just leaves the item queued for the next trigger rather
     * than crashing the app.
     */
    @Provides
    @Singleton
    fun provideFilmPreloader(
        @ApplicationContext context: Context,
        writer: CacheDataSourceWriter,
        lane: DownloadLane,
        heldSets: HeldSetsQuery,
        activePlayback: ActivePlayback,
        @MainThreadScope scope: CoroutineScope,
    ): FilmPreloading {
        val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val preloader =
            FilmPreloader(
                scope = scope,
                dispatcher = dispatcher,
                writer = writer,
                lane = lane,
                heldSets = heldSets,
                removeFromCache = { setId ->
                    val cache = CacheProvider.get(context)
                    withContext(Dispatchers.IO) { cache.removeResource(setUri(setId).toString()) }
                },
                openTitleSource = activePlayback,
                network = SystemUnmeteredNetworkCheck(context),
                fits = { totalBytes, reservedBytes ->
                    fitsFilmPreloadBudget(totalBytes, reservedBytes, CacheProvider.occupancy(context).budgetBytes)
                },
                log = { line, e -> if (e == null) Log.d(FILM_PRELOAD_LOG_TAG, line) else Log.w(FILM_PRELOAD_LOG_TAG, line, e) },
            )
        scope.launch {
            preloader.hasWork.filter { it }.collect {
                startPreloadServiceSafely(context)
            }
        }
        return preloader
    }
}

@Suppress("TooGenericExceptionCaught") // Android may refuse a foreground service start from the background (ForegroundServiceStartNotAllowedException, API 31+) — a queued preload just waits for the next trigger rather than crashing the app.
private fun startPreloadServiceSafely(context: Context) {
    try {
        ContextCompat.startForegroundService(context, Intent(context, PreloadService::class.java))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(FILM_PRELOAD_LOG_TAG, "could not start the preload service", e)
    }
}

/** What `adb logcat` filters on to watch a preload run — `preload: <title> held`/`skipped`/`stopped`. */
private const val PRELOAD_LOG_TAG = "SeriesPreload"

/** As [PRELOAD_LOG_TAG], for a film preload — `film preload: <title> held`/`write failed`. */
private const val FILM_PRELOAD_LOG_TAG = "FilmPreload"
