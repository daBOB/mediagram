package com.mediagram.android.di

import android.content.Context
import android.os.Build
import com.mediagram.android.R
import com.mediagram.android.isTelevision
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import player.PlayerHandle
import update.PlaybackActivity
import update.UpdateConfig
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object UpdateModule {
    /**
     * A release build on a television: `self_update` is true only in the
     * release build type (`app/build.gradle.kts`), and phones and tablets
     * never update themselves — Google Play Protect blocks an app-driven
     * update from a signing key it has never seen.
     */
    @Provides
    @Singleton
    fun config(
        @ApplicationContext context: Context,
    ): UpdateConfig {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)

        @Suppress("DEPRECATION")
        val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        return UpdateConfig(
            enabled = context.resources.getBoolean(R.bool.self_update) && isTelevision(context),
            installedVersionCode = code,
            updatesDir = File(context.cacheDir, "updates"),
        )
    }

    /**
     * ExoPlayer is read on the main thread only. The handle is [Lazy] so a
     * disabled updater, which never asks, does not build the player at launch.
     */
    @Provides
    fun playback(handle: Lazy<PlayerHandle>): PlaybackActivity =
        PlaybackActivity { withContext(Dispatchers.Main.immediate) { handle.get().player.value?.isPlaying == true } }
}
