// media3 marks its extension surface @UnstableApi and may change it in any
// minor release; see CacheProvider for why the version is pinned rather
// than floored, and why this is androidx's opt-in and not Kotlin's.
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package playback

import android.content.Context
import androidx.media3.common.C
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
suspend fun cacheDataSourceFactory(
    context: Context,
    counters: PlaybackCounters,
    currentCore: () -> CoreClient?,
): DataSource.Factory = CacheDataSource.Factory()
    .setCache(CacheProvider.get(context))
    .setUpstreamDataSourceFactory(MlibDataSourceFactory(counters, currentCore))
    // media3 offers this and nothing has ever attached one. Without it there
    // is no way to tell a cache that is carrying playback from one that is
    // being bypassed, which is the first thing worth knowing about a read.
    // Not a SAM conversion: CacheDataSource.EventListener has two abstract
    // methods, so it needs an explicit implementation rather than a lambda.
    .setEventListener(object : CacheDataSource.EventListener {
        override fun onCachedBytesRead(cacheSizeBytes: Long, cachedBytesRead: Long) {
            counters.servedFromCache(cachedBytesRead)
        }

        override fun onCacheIgnored(reason: Int) = Unit
    })

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
 *
 * The text renderer is disabled outright, once, here — never per open. This
 * app's subtitles are never embedded in the container (the web never
 * extracts one either; both read the index's own VTT text instead — see
 * `SubtitleTrack.kt`), so there is nothing for ExoPlayer's own text
 * selection to offer, and disabling it rules out a forced or default track
 * a container happens to carry ever flashing up uninvited.
 */
suspend fun buildPlayer(context: Context, counters: PlaybackCounters, currentCore: () -> CoreClient?): ExoPlayer =
    ExoPlayer.Builder(context)
        .setMediaSourceFactory(
            DefaultMediaSourceFactory(context)
                .setDataSourceFactory(cacheDataSourceFactory(context, counters, currentCore)),
        )
        // Set in both directions because media3's defaults are not
        // symmetrical — five seconds back, fifteen forward. A control that
        // offers the same jump each way has to say so here; the buttons read
        // their labels back off the player rather than carry their own copy.
        .setSeekBackIncrementMs(SKIP_MS)
        .setSeekForwardIncrementMs(SKIP_MS)
        .build()
        .apply {
            trackSelectionParameters = trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
        }

/**
 * How far one skip moves. Ten seconds is long enough to clear a line of
 * dialogue that was missed and short enough that two of them are not a
 * scene.
 */
private const val SKIP_MS = 10_000L
