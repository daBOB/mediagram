package com.mediagram.android.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import data.di.MainThreadScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import playback.HeldSets
import playback.HeldSetsQuery
import playback.LanCacheRuntime
import playback.LanCacheSettings
import playback.LanCacheTokenStatus
import playback.LanChunkClient
import playback.LanChunkProtocol
import playback.LanServerLocator
import playback.LanServerSource
import playback.LanWriteQueue
import playback.PlainLanCacheSettings
import playback.PlaybackCounters
import playback.SystemUnmeteredNetworkCheck
import settings.LanCacheTokenSettings
import java.util.concurrent.Executors
import javax.inject.Singleton

/**
 * The core:playback singletons more than one feature injects: the LAN
 * cache (feature:system's LanCacheViewModel reads its settings, status and
 * server; the player and the preloaders read and share chunks through it),
 * the playback counters (feature:system's SystemViewModel and the player),
 * and which sets are already held (feature:catalog's CatalogViewModel, the
 * player and the preloaders). Bound here, at the composition root, rather
 * than in any one feature — core:playback carries no Hilt plugin of its own.
 */
@Module
@InstallIn(SingletonComponent::class)
object SharedPlaybackModule {
    @Provides
    @Singleton
    fun playbackCounters(): PlaybackCounters = PlaybackCounters()

    @Provides
    @Singleton
    fun provideHeldSets(
        @ApplicationContext context: Context,
    ): HeldSetsQuery = HeldSets(context)

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
        @MainThreadScope scope: CoroutineScope,
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
     * Shared by the player and the preloader (`PreloadModule.provideSeriesPreloader`)
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
        @MainThreadScope scope: CoroutineScope,
    ): LanCacheRuntime {
        // Its own dedicated thread, apart from Dispatchers.IO's shared pool —
        // the same reasoning PreloadModule gives the preloader's own worker.
        val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val writes =
            LanWriteQueue(
                scope = scope,
                dispatcher = dispatcher,
                client = client,
                // Gated on the sharing switch as reads are: chunks queued just
                // before a viewer switched sharing off are not sent after it.
                server = { if (settings.enabled()) locator.server.value else null },
                token = { tokenSettings.read() },
                tokenStatus = tokenStatus,
            )
        return LanCacheRuntime(client, locator, settings, tokenStatus, SystemUnmeteredNetworkCheck(context), writes)
    }
}
