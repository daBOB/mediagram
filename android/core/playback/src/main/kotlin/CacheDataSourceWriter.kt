// media3 marks its extension surface @UnstableApi and may change it in any
// minor release; see CacheProvider for why the version is pinned rather
// than floored, and why this is androidx's opt-in and not Kotlin's.
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package playback

import android.content.Context
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import uniffi.mediagram_core.CoreInterface
import java.util.concurrent.Executors

/**
 * The real [PreloadWriter]: a media3 `CacheWriter` over the strict
 * `cacheDataSourceFactory` beneath the player's own, so a preload
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
 * that is never asked to take anything should never have done that. Both
 * `SeriesPreloader` and `FilmPreloader` share this one instance and reach
 * [write] only through their shared `DownloadLane`, so the lazy build needs
 * no lock of its own: the lane already guarantees only one call is ever
 * inside [write] at a time.
 *
 * [writeDispatcher] is [write]'s own dedicated thread, apart from whichever
 * dispatcher the calling preloader's worker uses: `CacheWriter.cache()` is
 * a blocking call (real disk and network I/O in a loop, not a suspend
 * function), and running it on a shared single-thread worker dispatcher
 * would starve everything else queued there — the pause-on-play watchdog,
 * `pauseForTimeLimit`, a `remove` — for as long as the write takes, which
 * for a whole film is not a moment. Its own thread means those keep
 * running on the caller's own dispatcher while this one blocks.
 */
class CacheDataSourceWriter internal constructor(
    private val counters: PlaybackCounters,
    private val currentCore: () -> CoreInterface?,
    private val lan: LanCacheRuntime? = null,
    private val writeDispatcher: CoroutineDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher(),
    private val openCache: suspend () -> Cache,
) : PreloadWriter, FilmPreloadWriter {
    // openCache stays the last constructor parameter (writeDispatcher
    // sits before it, defaulted) so every existing caller's trailing-
    // lambda call — `CacheDataSourceWriter(counters, currentCore) { cache }` —
    // keeps binding to it rather than to the new parameter.
    constructor(
        context: Context,
        counters: PlaybackCounters,
        lan: LanCacheRuntime? = null,
        currentCore: () -> CoreInterface?,
    ) : this(counters, currentCore, lan, openCache = { CacheProvider.get(context) })

    private var factory: CacheDataSource.Factory? = null

    /** [SeriesPreloader]'s own [PreloadWriter.write] — no progress. */
    override suspend fun write(item: PreloadItem) = write(item) {}

    /**
     * As [write], reporting [CacheWriter]'s own raw progress — every
     * callback it makes, unthrottled; `FilmPreloader` is the one that
     * paces those into state updates.
     *
     * Cancelling the calling coroutine (the caller's `Job`, not a method
     * on this class) is what stops the write — there is no separate
     * cancel method, and so no shared "which write is active" slot either
     * preloader could reach into and cancel the other's write by mistake.
     * `writer.cache()` runs in its own child coroutine on [writeDispatcher];
     * `Job.join()` on it is a plain, public, cancellable suspend point —
     * when *this* coroutine is cancelled while waiting there, `join()`
     * throws, and the catch is what actually reaches [CacheWriter.cancel]:
     * a plain coroutine cancellation on its own cannot interrupt a call
     * already blocking [writeDispatcher]'s thread, only that volatile flag,
     * checked between [CacheWriter]'s own reads, can. The plain
     * `coroutineScope` still waits for the write's child coroutine to
     * actually finish unwinding before this function returns or rethrows,
     * and a genuine failure in it — not caused by cancelling it — still
     * propagates out normally, as the same exception it threw.
     *
     * The strict [cacheDataSourceFactory], never the player's forgiving
     * one: a cache error here has to fail the preload rather than let it
     * keep downloading into a cache that stores nothing. Sharing it with
     * playback ([lan], when given) is what fills the LAN server for free —
     * a preload is not a separate path that happens to agree with
     * playback's, it is the same one.
     */
    override suspend fun write(item: PreloadItem, onProgress: (bytesCached: Long) -> Unit) {
        // Best-effort and never awaited-through-a-failure: a bundle this
        // title has no route to, or none at all, must not fail the byte
        // preload this call otherwise exists for.
        runCatching { currentCore()?.holdSubtitles(item.setId) }
        val built = factory ?: cacheDataSourceFactory(openCache(), counters, lan, currentCore).also { factory = it }
        val writer =
            CacheWriter(built.createDataSource(), DataSpec(setUri(item.setId)), null) { _, bytesCached, _ ->
                onProgress(bytesCached)
            }
        coroutineScope {
            val job = launch(writeDispatcher) { writer.cache() }
            try {
                job.join()
            } catch (e: CancellationException) {
                writer.cancel()
                throw e
            }
        }
    }
}
