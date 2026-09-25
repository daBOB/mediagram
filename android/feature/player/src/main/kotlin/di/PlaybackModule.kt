package player.di

import android.content.Context
import android.util.Log
import androidx.media3.exoplayer.ExoPlayer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import data.CoreProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.asCoroutineDispatcher
import playback.CacheDataSourceWriter
import playback.DefaultSubtitleTrackSource
import playback.HeldSets
import playback.HeldSetsQuery
import playback.PlaybackCounters
import playback.PreloadWriter
import playback.SeriesPreloader
import playback.SeriesPreloading
import playback.SubtitleTrackSource
import playback.SystemUnmeteredNetworkCheck
import playback.buildPlayer
import playback.CacheProvider
import playback.fitsInPreloadBudget
import player.AndroidPlaybackServiceController
import player.DefaultPlayerHandle
import player.PlaybackServiceController
import player.PlayerHandle
import java.util.concurrent.Executors
import javax.inject.Singleton

/**
 * `buildPlayer` does real disk/database I/O building the cache. Starting
 * it here with `async`, rather than calling it directly from a plain
 * `@Provides` function, means Hilt's synchronous resolution of
 * [PlayerHandle] — triggered by `hiltViewModel()` during composition on
 * main — only ever starts the work and hands back a handle to it; nothing
 * in this module blocks waiting for the result. [DefaultPlayerHandle]
 * awaits the [Deferred] itself, off main, before touching the player.
 *
 * The same `async` now also absorbs a second wait: the core it reads
 * through does not exist until the device has been set up, so on a first
 * run this deferred stays pending rather than failing. Nothing can reach a
 * player before then, and the handle already renders a pending player as
 * "preparing".
 */
@Module
@InstallIn(SingletonComponent::class)
object PlaybackModule {

    @Provides
    @Singleton
    fun providePlaybackScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Provides
    @Singleton
    fun playbackCounters(): PlaybackCounters = PlaybackCounters()

    // Deferred<out T>'s declaration-site variance compiles to Java's
    // Deferred<? extends ExoPlayer>, which Dagger's binding graph treats
    // as a different type from the plain Deferred<ExoPlayer> a consumer
    // asks for; @JvmSuppressWildcards drops the wildcard so the two match.
    @Provides
    @Singleton
    fun provideExoPlayerDeferred(
        @ApplicationContext context: Context,
        coreProvider: CoreProvider,
        counters: PlaybackCounters,
        scope: CoroutineScope,
    ): @JvmSuppressWildcards Deferred<ExoPlayer> = scope.async {
        // Awaited once so the cache is not built on a device that has never
        // been set up, then read per data source rather than captured: the
        // player outlives a start-over, the core it reads through does not.
        coreProvider.awaitCore()
        buildPlayer(context, counters) { coreProvider.core.value }
    }

    @Provides
    @Singleton
    fun providePlayerHandle(
        playerDeferred: @JvmSuppressWildcards Deferred<ExoPlayer>,
        scope: CoroutineScope,
    ): PlayerHandle = DefaultPlayerHandle(playerDeferred, scope)

    @Provides
    @Singleton
    fun provideSubtitleTrackSource(coreProvider: CoreProvider): SubtitleTrackSource =
        DefaultSubtitleTrackSource(coreProvider)

    // Upcasts a constructor-injected concrete type to the interface
    // PlayerViewModel actually depends on — this module is a plain
    // `object`, so `@Binds` (which needs an abstract class) is not an
    // option here.
    @Provides
    @Singleton
    fun providePlaybackServiceController(controller: AndroidPlaybackServiceController): PlaybackServiceController = controller

    @Provides
    @Singleton
    fun provideHeldSets(@ApplicationContext context: Context): HeldSetsQuery = HeldSets(context)

    /**
     * Built here rather than constructor-injected into [SeriesPreloader]
     * itself: `:core:playback` carries no Hilt plugin of its own (see
     * [HeldSets]), so every real dependency — the dedicated thread, the
     * network check, the budget read — is assembled once, in this module,
     * the same as [provideExoPlayerDeferred] builds the player's own cache.
     */
    @Provides
    @Singleton
    fun provideSeriesPreloader(
        @ApplicationContext context: Context,
        coreProvider: CoreProvider,
        heldSets: HeldSetsQuery,
        scope: CoroutineScope,
    ): SeriesPreloading {
        // Its own dedicated thread, apart from Dispatchers.IO's shared
        // pool: MlibDataSource's own reads block with `runBlocking`, and a
        // preload must never be what starves the pool the rest of the
        // app's IO shares.
        val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        // Its own counters, not the playing title's: the System screen's
        // numbers should not move just because a series is quietly
        // preloading in the background.
        val writer: PreloadWriter = CacheDataSourceWriter(context, PlaybackCounters()) { coreProvider.core.value }
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
            log = { line -> Log.d(PRELOAD_LOG_TAG, line) },
        )
    }
}

/** What `adb logcat` filters on to watch a preload run — `preload: <title> held`/`skipped`/`stopped`. */
private const val PRELOAD_LOG_TAG = "SeriesPreload"
