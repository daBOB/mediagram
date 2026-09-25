package player.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import playback.LanCacheRuntime
import playback.LanCacheSettings
import playback.LanCacheTokenStatus
import playback.LanChunkClient
import playback.LanChunkProtocol
import playback.LanServerLocator
import playback.LanServerSource
import playback.LanWriteQueue
import playback.PlainLanCacheSettings
import playback.SystemUnmeteredNetworkCheck
import settings.LanCacheTokenSettings
import java.util.concurrent.Executors
import javax.inject.Singleton

/**
 * Wiring for reading and sharing chunks through a home LAN cache server —
 * kept apart from [PlaybackModule] only so neither file grows past what
 * fits on one screen; both install into the same [SingletonComponent].
 */
@Module
@InstallIn(SingletonComponent::class)
object LanCacheModule {
    @Provides
    @Singleton
    fun provideLanCacheSettings(
        @ApplicationContext context: Context,
    ): LanCacheSettings = PlainLanCacheSettings(context)

    @Provides
    @Singleton
    fun provideLanChunkProtocol(): LanChunkProtocol = LanChunkClient()

    @Provides
    @Singleton
    fun provideLanCacheTokenStatus(): LanCacheTokenStatus = LanCacheTokenStatus()

    @Provides
    @Singleton
    fun provideLanServerLocator(
        @ApplicationContext context: Context,
        client: LanChunkProtocol,
        settings: LanCacheSettings,
        scope: CoroutineScope,
    ): LanServerLocator =
        // One pass starts as soon as this singleton is first resolved —
        // effectively app start, since the player and Settings both pull
        // it in early — plus whatever further passes Settings triggers
        // while it is open (LanCacheViewModel.refresh()).
        LanServerLocator(context, scope, client) { settings.manualAddress() }.apply { discover() }

    // Upcasts to the narrow interface a Settings view model actually
    // depends on, so it can be tested against a fake without a real
    // NsdManager — this module is a plain `object`, so `@Binds` (which
    // needs an abstract class) is not an option here.
    @Provides
    @Singleton
    fun provideLanServerSource(locator: LanServerLocator): LanServerSource = locator

    /**
     * Shared by the player and the preloader (`PlaybackModule.provideSeriesPreloader`)
     * — one discovery, one write queue, so a preload filling the LAN server
     * and playback reading from it never race two independent instances of
     * either.
     */
    @Provides
    @Singleton
    fun provideLanCacheRuntime(
        @ApplicationContext context: Context,
        client: LanChunkProtocol,
        locator: LanServerLocator,
        settings: LanCacheSettings,
        tokenSettings: LanCacheTokenSettings,
        tokenStatus: LanCacheTokenStatus,
        scope: CoroutineScope,
    ): LanCacheRuntime {
        // Its own dedicated thread, apart from Dispatchers.IO's shared pool —
        // the same reasoning PlaybackModule gives the preloader's own worker.
        val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val writes =
            LanWriteQueue(
                scope = scope,
                dispatcher = dispatcher,
                client = client,
                server = { locator.server.value },
                token = { tokenSettings.read() },
                onUnauthorized = tokenStatus::markRejected,
            )
        return LanCacheRuntime(client, locator, settings, tokenStatus, SystemUnmeteredNetworkCheck(context), writes)
    }
}
