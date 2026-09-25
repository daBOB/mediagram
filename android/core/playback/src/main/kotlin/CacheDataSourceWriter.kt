// media3 marks its extension surface @UnstableApi and may change it in any
// minor release; see CacheProvider for why the version is pinned rather
// than floored, and why this is androidx's opt-in and not Kotlin's.
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package playback

import android.content.Context
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import data.CoreClient

/**
 * The real [PreloadWriter]: a media3 `CacheWriter` over the same
 * `cacheDataSourceFactory` playback itself reads through, so a preload
 * fills exactly the spans playback would read, under the same key
 * ([setUri]).
 *
 * [counters] is a [PlaybackCounters] of this writer's own rather than the
 * one the playing title reports through — "what has this app fetched" and
 * "what did this preload cost" are different questions, and the System
 * screen's own numbers should not move just because a series is preloading
 * quietly in the background.
 *
 * The factory is built once, lazily, on the first [write] rather than at
 * construction — building it opens the shared disk cache, and a preloader
 * that is never asked to take anything should never have done that.
 * [write] always runs on this class's own dedicated dispatcher (see
 * [SeriesPreloader]'s single worker), so the lazy build needs no lock.
 */
class CacheDataSourceWriter(
    private val context: Context,
    private val counters: PlaybackCounters,
    private val currentCore: () -> CoreClient?,
) : PreloadWriter {

    private var factory: CacheDataSource.Factory? = null

    override suspend fun write(item: PreloadItem) {
        val built = factory ?: cacheDataSourceFactory(context, counters, currentCore).also { factory = it }
        CacheWriter(built.createDataSource(), DataSpec(setUri(item.setId)), null, null).cache()
    }
}
