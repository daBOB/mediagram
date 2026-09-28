package player

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import playback.CacheBudgetQuery
import playback.FilmPreloadRow
import playback.FilmPreloadState
import playback.FilmPreloading
import playback.LanCacheSettings
import playback.LanChunkProtocol
import playback.LanServerSource
import javax.inject.Inject

/**
 * What a film page — phone, tablet or TV — drives its own Preload control
 * from: the engine's state, an enqueue/cancel/remove that reads the right
 * one off [preloadTapAction] rather than making the composable decide, and
 * the paired home server's own line. Android-only by decision: the web
 * player has no film preload.
 *
 * Not keyed to one film — like [catalog.BrowseViewModel] beside it, every
 * method takes the film's own id, since this app has no navigation graph to
 * scope a per-screen instance to; the same reasoning is why [serverLine]'s
 * polling lives in a plain cold [Flow] rather than a `StateFlow` cached
 * here; collected only while a page asks for it, it starts and stops with
 * that page's own composition instead of running on for a film nobody is
 * looking at.
 */
@HiltViewModel
class TitlePreloadViewModel
    @Inject
    constructor(
        private val preloading: FilmPreloading,
        private val lanCacheSettings: LanCacheSettings,
        private val lanServerSource: LanServerSource,
        private val lanChunkProtocol: LanChunkProtocol,
        private val cacheBudget: CacheBudgetQuery = CacheBudgetQuery.Noop,
    ) : ViewModel() {
        /**
         * The engine's own ordered queue, plus every film paused by
         * Android's own background time limit (which the engine reports
         * apart from the queue itself, since pausing for it empties the
         * queue). A `val`, not a function: unlike [stateOf]/[serverLine]
         * (per-film, called fresh for whichever film a page is open on),
         * every caller shares this one instance, so a Preloads page and the
         * menu's own count can both collect it with no `remember` keying of
         * their own — a fresh `Flow` on every call is what would need that.
         */
        val queueRows: Flow<List<FilmPreloadRow>> =
            combine(preloading.queueOverview, preloading.timeLimitPaused) { queue, paused -> queue + paused }

        /** How many films are running or queued right now — the menu's own "Preloads · n" badge, and the gate for showing it at all. */
        val queueCount: Flow<Int> = queueRows.map { it.size }

        fun stateOf(setId: String, totalBytes: Long): Flow<FilmPreloadState> = preloading.stateOf(setId, totalBytes)

        /** What the control's own tap or OK press does, read straight off [state] rather than kept as separate ViewModel bookkeeping. */
        fun toggle(setId: String, title: String, totalBytes: Long, state: FilmPreloadState) {
            when (preloadTapAction(state)) {
                PreloadTapAction.ENQUEUE -> preloading.enqueue(setId, title, totalBytes)
                PreloadTapAction.CANCEL -> preloading.cancel(setId)
                PreloadTapAction.NONE -> Unit
            }
        }

        fun remove(setId: String) = preloading.remove(setId)

        /** Cancels [setId] outright — the Preloads page's own Cancel action, which (unlike [toggle]) already knows it is asking for exactly this without reading a state first. */
        fun cancel(setId: String) = preloading.cancel(setId)

        /**
         * "Home server: x of y GB", polled every 5s while [setId] is
         * actually [FilmPreloadState.Running] and once for every other
         * state — a page that only glanced at an idle film has no reason to
         * keep asking the server about it. [flatMapLatest] keys on whether
         * the film is running, not the state itself: a running film's own
         * progress changes several times a second (`ProgressThrottle`), and
         * keying on the whole state would restart the 5s wait on every one
         * of them, never actually reaching it.
         */
        fun serverLine(setId: String, totalBytes: Long): Flow<String?> =
            stateOf(setId, totalBytes)
                .map { it is FilmPreloadState.Running }
                .distinctUntilChanged()
                .flatMapLatest { running ->
                    flow {
                        emit(fetchServerLine(setId, totalBytes))
                        if (running) {
                            while (true) {
                                delay(SERVER_POLL_MS)
                                emit(fetchServerLine(setId, totalBytes))
                            }
                        }
                    }
                }

        private suspend fun fetchServerLine(setId: String, totalBytes: Long): String? {
            if (!lanCacheSettings.enabled()) return null
            val server = lanServerSource.server.value ?: return null
            val status = lanChunkProtocol.setStatus(server.baseUrl, setId) ?: return null
            return preloadServerLine(status.bytesHeld, totalBytes)
        }

        /**
         * What [setId]'s own "Queued" label adds beyond the bare word —
         * gated the same way [serverLine] gates its own poll, so a film
         * that never queues never subscribes to [rows] at all. [rows]
         * defaults to [queueRows] itself; a kids profile's own wiring
         * passes one already filtered to titles its catalogue can resolve,
         * so "after <title>" never names a film a kid could not open.
         */
        fun queuedAhead(setId: String, totalBytes: Long, rows: Flow<List<FilmPreloadRow>> = queueRows): Flow<String?> =
            stateOf(setId, totalBytes)
                .map { it is FilmPreloadState.Queued }
                .distinctUntilChanged()
                .flatMapLatest { queued -> if (!queued) flowOf(null) else rows.map { queuedAheadLabel(it, setId) } }

        /** The Preloads page's own "Resume" action for a film Android's time limit paused — re-enqueues it, the same as a tap on its own film page would. */
        fun resume(setId: String, title: String, totalBytes: Long) = preloading.enqueue(setId, title, totalBytes)

        /**
         * The live cache budget, read once whenever [setId] becomes
         * [FilmPreloadState.NeedsSpace] — what its own label names so the
         * reason is visible without opening Settings.
         */
        fun needsSpaceBudget(setId: String, totalBytes: Long): Flow<Long?> =
            stateOf(setId, totalBytes)
                .map { it is FilmPreloadState.NeedsSpace }
                .distinctUntilChanged()
                .flatMapLatest { needsSpace -> if (!needsSpace) flowOf(null) else flow { emit(cacheBudget.currentBudgetBytes()) } }

        private companion object {
            const val SERVER_POLL_MS = 5_000L
        }
    }
