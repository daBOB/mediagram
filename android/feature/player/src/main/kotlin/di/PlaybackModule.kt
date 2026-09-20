package player.di

import android.content.Context
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
import playback.buildPlayer
import player.DefaultPlayerHandle
import player.PlayerHandle
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

    // Deferred<out T>'s declaration-site variance compiles to Java's
    // Deferred<? extends ExoPlayer>, which Dagger's binding graph treats
    // as a different type from the plain Deferred<ExoPlayer> a consumer
    // asks for; @JvmSuppressWildcards drops the wildcard so the two match.
    @Provides
    @Singleton
    fun provideExoPlayerDeferred(
        @ApplicationContext context: Context,
        coreProvider: CoreProvider,
        scope: CoroutineScope,
    ): @JvmSuppressWildcards Deferred<ExoPlayer> =
        scope.async { buildPlayer(context, coreProvider.awaitCore()) }

    @Provides
    @Singleton
    fun providePlayerHandle(
        playerDeferred: @JvmSuppressWildcards Deferred<ExoPlayer>,
        scope: CoroutineScope,
    ): PlayerHandle = DefaultPlayerHandle(playerDeferred, scope)
}
