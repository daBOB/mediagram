package playback

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf

/**
 * What one film's preload is doing right now — what `FilmPreloader.stateOf`
 * hands the film page to render as its "Preload · 5.8 GB" button, its
 * progress bar, or its "Preloaded ✓" row.
 *
 * [Idle] is not only the state before a viewer ever taps preload: it is
 * also where a cancel or a remove lands, its [heldBytes] read fresh from
 * the cache rather than kept as a running total — a film half held from
 * playback alone starts here at its real percentage, and a removed one
 * settles back to zero once the cache actually forgets it, with nothing
 * here that could drift from what is really on disk.
 */
sealed interface FilmPreloadState {
    data class Idle(val heldBytes: Long, val totalBytes: Long) : FilmPreloadState

    /** Waiting for another film's write to finish — the queue is FIFO, one active at a time. */
    data object Queued : FilmPreloadState

    data class Running(val heldBytes: Long, val totalBytes: Long) : FilmPreloadState

    /** Interrupted, not stopped — [FilmPreloader]'s worker keeps retrying [reason] until it clears on its own. */
    data class Paused(val heldBytes: Long, val totalBytes: Long, val reason: PauseReason) : FilmPreloadState

    data object Done : FilmPreloadState

    /**
     * The film's own [neededBytes] — its whole size — does not fit the
     * budget even after evicting everything evictable; raising the budget
     * is the only way forward. See `fitsFilmPreloadBudget`'s own doc: this
     * is never about what else happens to be held right now, since an LRU
     * cache with no pinning can always make room for anything that itself
     * fits.
     */
    data class NeedsSpace(val neededBytes: Long) : FilmPreloadState

    /** Gave up after repeated failures with no forward progress — [reason] is a sentence, not a code. */
    data class Failed(val reason: String) : FilmPreloadState
}

/** Why a [FilmPreloadState.Paused] preload is not writing right now. */
enum class PauseReason {
    /** A title is open in the player — playing, buffering, or paused by the viewer; anything but closed counts. */
    Playing,

    /** The network turned metered mid-download; the same Wi-Fi-only rule [PreloadNetwork] gives the series preloader. */
    Metered,

    /** Android's `dataSync` foreground-service ceiling (6h continuous, 24h a day) was reached; resumable from the page. */
    TimeLimit,
}

/**
 * What [CacheDataSourceWriter] gives [FilmPreloader] beyond the plain
 * [PreloadWriter] `SeriesPreloader` uses: raw progress. There is no
 * separate cancel method — [write] is cancelled the ordinary coroutine
 * way, by cancelling whichever `Job` called it; see [CacheDataSourceWriter]'s
 * own doc for how that reaches the blocking `CacheWriter` underneath, and
 * why that is what makes cancellation per call rather than a shared slot
 * both preloaders could trip over.
 */
interface FilmPreloadWriter {
    suspend fun write(item: PreloadItem, onProgress: (bytesCached: Long) -> Unit)
}

/** What `PreloadService`'s notification shows while a film is actually writing — `null` the rest of the time (queued, paused, or nothing preloading at all). */
data class ActivePreload(val title: String, val heldBytes: Long, val totalBytes: Long)

/** What a film page drives: enqueue, cancel, remove, and observe one film's own preload. */
interface FilmPreloading {
    fun stateOf(setId: String, totalBytes: Long): Flow<FilmPreloadState>
    fun enqueue(setId: String, title: String, totalBytes: Long)
    fun cancel(setId: String)
    fun remove(setId: String)

    /** Android's `dataSync` foreground-service ceiling was reached — `PreloadService.onTimeout` calls this. */
    fun pauseForTimeLimit()

    /** One id per film this preloader has just taken into the cache in full — the catalogue's held badges collect this alongside `SeriesPreloading.heldEvents`. */
    val heldEvents: SharedFlow<String>

    /** One id per film [remove] just cleared from the cache — the badges' own "no longer held" signal. */
    val unheldEvents: SharedFlow<String>

    /** Whether the queue has anything left to do — `PreloadService` starts its foreground state while this is `true` and stops once it turns `false`. */
    val hasWork: StateFlow<Boolean>

    /** What is actually writing right now, for `PreloadService`'s notification text. */
    val active: StateFlow<ActivePreload?>

    companion object {
        /** Nothing queued, nothing held — a constructor default for a caller that does not care. */
        val Noop: FilmPreloading = object : FilmPreloading {
            override fun stateOf(setId: String, totalBytes: Long): Flow<FilmPreloadState> =
                flowOf(FilmPreloadState.Idle(0L, totalBytes))
            override fun enqueue(setId: String, title: String, totalBytes: Long) = Unit
            override fun cancel(setId: String) = Unit
            override fun remove(setId: String) = Unit
            override fun pauseForTimeLimit() = Unit
            override val heldEvents: SharedFlow<String> = MutableSharedFlow()
            override val unheldEvents: SharedFlow<String> = MutableSharedFlow()
            override val hasWork: StateFlow<Boolean> = MutableStateFlow(false)
            override val active: StateFlow<ActivePreload?> = MutableStateFlow(null)
        }
    }
}
