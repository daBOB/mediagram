package playback

import android.content.Context
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import data.CoreClient

/**
 * Wraps [MlibDataSourceFactory] in the process-wide disk cache: a cache hit
 * never reaches the core, a miss falls through to [MlibDataSource].
 */
fun cacheDataSourceFactory(context: Context, core: CoreClient): DataSource.Factory =
    CacheDataSource.Factory()
        .setCache(CacheProvider.get(context))
        .setUpstreamDataSourceFactory(MlibDataSourceFactory(core))

/**
 * An [ExoPlayer] that reads every set through the cache. No format hints
 * are given to [DefaultMediaSourceFactory] — its default
 * `DefaultExtractorsFactory` sniffs the container, so Matroska, MP4 and
 * whatever else the uploader wrote all work without per-title
 * configuration, and nothing here transcodes anything.
 */
fun buildPlayer(context: Context, core: CoreClient): ExoPlayer =
    ExoPlayer.Builder(context)
        .setMediaSourceFactory(
            DefaultMediaSourceFactory(context).setDataSourceFactory(cacheDataSourceFactory(context, core)),
        )
        .build()
