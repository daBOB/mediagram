package player

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
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
    ) : ViewModel() {
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

        private companion object {
            const val SERVER_POLL_MS = 5_000L
        }
    }
