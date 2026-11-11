package com.mediagram.android.di

import android.content.Context
import androidx.media3.common.Player
import com.mediagram.android.R
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import data.di.AppScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import player.PlayerHandle
import update.ApkInstaller
import update.AppUpdater
import update.PackageApkInstaller
import update.PlaybackActivity
import update.UpdateConfig
import update.updateConfig
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object UpdateModule {
    /**
     * Every release build, on any device: `self_update` is true only in the
     * release build type (`app/build.gradle.kts`). On a phone or tablet with
     * Google Play Protect, "Scan apps with Play Protect" must be off, or it
     * blocks an app-driven update from a signing key it has never seen.
     */
    @Provides
    @Singleton
    fun config(
        @ApplicationContext context: Context,
    ): UpdateConfig = updateConfig(context, enabled = context.resources.getBoolean(R.bool.self_update))

    @Provides
    @Singleton
    fun installer(
        @ApplicationContext context: Context,
    ): ApkInstaller = PackageApkInstaller(context)

    @Provides
    @Singleton
    fun updater(
        config: UpdateConfig,
        source: CoreReleaseSource,
        playback: PlaybackActivity,
        installer: ApkInstaller,
        @AppScope scope: CoroutineScope,
    ): AppUpdater = AppUpdater(config, source, playback, installer, scope)

    /**
     * ExoPlayer is read on the main thread only. The handle is [Lazy] so a
     * disabled updater, which never asks, does not build the player at launch.
     */
    @Provides
    fun playback(handle: Lazy<PlayerHandle>): PlaybackActivity =
        PlaybackActivity {
            withContext(Dispatchers.Main.immediate) {
                // Buffering a title that is about to play counts: isPlaying is false then.
                handle.get().player.value?.let {
                    it.isPlaying || (it.playWhenReady && it.playbackState == Player.STATE_BUFFERING)
                } == true
            }
        }
}
