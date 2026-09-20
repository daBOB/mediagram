package playback

import android.content.Context
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import data.CoreClient

/**
 * Wraps [MlibDataSourceFactory] in the process-wide disk cache: a cache hit
 * never reaches the core, a miss falls through to [MlibDataSource]. `suspend`
 * because building the cache does real disk/database I/O — see
 * [CacheProvider.get].
 */
suspend fun cacheDataSourceFactory(context: Context, core: CoreClient): DataSource.Factory =
    CacheDataSource.Factory()
        .setCache(CacheProvider.get(context))
        .setUpstreamDataSourceFactory(MlibDataSourceFactory(core))

/**
 * An [ExoPlayer] that reads every set through the cache. No format hints
 * are given to [DefaultMediaSourceFactory] — its default
 * `DefaultExtractorsFactory` sniffs the container, so Matroska, MP4 and
 * whatever else the uploader wrote all work without per-title
 * configuration, and nothing here transcodes anything.
 *
 * `suspend`, not because building an `ExoPlayer` itself is slow, but
 * because [cacheDataSourceFactory] is: a caller that awaits this from a
 * main-dispatched coroutine resumes the cheap `ExoPlayer.Builder().build()`
 * call back on its own (main) thread once the cache's I/O — the only real
 * work here — has finished on whatever dispatcher [CacheProvider.get] used.
 */
suspend fun buildPlayer(context: Context, core: CoreClient): ExoPlayer =
    ExoPlayer.Builder(context)
        .setMediaSourceFactory(
            DefaultMediaSourceFactory(context).setDataSourceFactory(cacheDataSourceFactory(context, core)),
        )
        .build()
