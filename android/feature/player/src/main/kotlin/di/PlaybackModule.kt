package player.di

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import data.CoreClient
import player.DefaultPlayerHandle
import player.PlayerHandle
import playback.buildPlayer
import javax.inject.Singleton

/**
 * One [ExoPlayer] for the whole process: this is a single-player app, so
 * there is nothing to gain from a per-screen instance, and a shared one
 * means only one decoder and one cache reader are ever alive at once.
 */
@Module
@InstallIn(SingletonComponent::class)
object PlaybackModule {

    @Provides
    @Singleton
    fun provideExoPlayer(@ApplicationContext context: Context, core: CoreClient): ExoPlayer =
        buildPlayer(context, core)

    @Provides
    @Singleton
    fun providePlayerHandle(player: ExoPlayer): PlayerHandle = DefaultPlayerHandle(player)
}
