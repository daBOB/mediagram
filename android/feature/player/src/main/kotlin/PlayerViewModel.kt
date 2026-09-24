package player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import dagger.hilt.android.lifecycle.HiltViewModel
import data.ProgressPoint
import data.ResumePoint
import data.WatchStateRepository
import data.WatchSync
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import model.KidsVerdict
import model.ageLabelOf
import model.kidsVerdictOf
import playback.PlaybackCounters
import playback.PlaybackTotals
import javax.inject.Inject

/** Tracks what the player is doing for whichever set is currently open. */
@HiltViewModel
class PlayerViewModel
    @Inject
    constructor(
        private val handle: PlayerHandle,
        counters: PlaybackCounters,
        private val repository: WatchStateRepository,
        private val recorder: ProgressRecorder,
        private val watchSync: WatchSync,
    ) : ViewModel(),
        PlayerHandle.Listener {
        private val _state = MutableStateFlow<PlayerUiState>(PlayerUiState.Preparing)
        val state: StateFlow<PlayerUiState> = _state.asStateFlow()

        private val _actionNotice = MutableStateFlow<String?>(null)
        val actionNotice: StateFlow<String?> = _actionNotice.asStateFlow()
        private var openGeneration = 0L

        fun dismissActionNotice() {
            _actionNotice.value = null
        }

        /**
         * What the byte path has done, read on demand rather than copied into
         * [state]: these numbers move on ExoPlayer's loader thread between one
         * frame and the next, and a copy held in state could only be the same
         * number later, or a different one wrongly. One instance, so a caller
         * that passes it down does not hand a fresh object over on every
         * recomposition.
         */
        val totals: () -> PlaybackTotals = counters::totals

        /**
         * Handed straight to `PlayerSurface` by the UI layer once non-null;
         * not part of [state] — [state] stays `Preparing` on its own account
         * while this is still null.
         */
        val player: StateFlow<Player?> = handle.player

        /** Which set [save] and the ticker below write against; `null` between titles. */
        private val openSetId = MutableStateFlow<String?>(null)

        /** The open title's age rating, as the catalog listed it; see [open]. */
        private val openFsk = MutableStateFlow<String?>(null)

        /**
         * The Watchlist, Kids and Add-to-list controls' own state for whichever
         * set is open — `null` between titles, the same gate `player.js` puts
         * in front of its own three buttons. Joined from [openSetId] rather
         * than read once, so a write from this screen or a sync round pulled in
         * behind it updates a pressed toggle's label without the screen having
         * to ask again.
         */
        val marks: StateFlow<PlayerMarksState?> =
            combine(openSetId, openFsk, repository.snapshot) { setId, fsk, snapshot ->
                setId?.let {
                    PlayerMarksState(
                        watchlisted = it in snapshot.watchlist,
                        kids = it in snapshot.kids,
                        lists = snapshot.collections,
                        memberOf = snapshot.collections.filter { list -> it in list.items }.mapTo(HashSet()) { list -> list.id },
                        kidsVerdict = kidsVerdictOf(fsk),
                        ageLabel = ageLabelOf(fsk),
                    )
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

        /** Runs every ten seconds while playing, same cadence as the web's own timer. */
        private var tickerJob: Job? = null

        init {
            handle.setListener(this)
        }

        /**
         * [fsk] is the title's age rating as the catalog listed it, handed in
         * rather than looked up: the player holds no catalog of its own, and the
         * screen that opened it already has the set in hand.
         */
        fun open(
            setId: String,
            fsk: String? = null,
        ) {
            openGeneration++
            _actionNotice.value = null
            openFsk.value = fsk
            openSetId.value = setId
            _state.value = PlayerUiState.Preparing
            val progress =
                repository.snapshot.value.progress
                    .find { it.setId == setId }
            val resumeSeconds = ResumePoint.resumeAt(progress?.let { ProgressPoint(it.at, it.duration) })
            val startAtMs = ((resumeSeconds ?: 0.0) * 1000).toLong()
            handle.open(setId, startAtMs)
        }

        /** Called when the player screen leaves composition, so codecs and audio focus aren't held idle. */
        fun stop() {
            // Read before handle.stop(), which drops the player to STATE_IDLE —
            // positionMs()/durationMs() would already answer null afterwards.
            val setId = openSetId.value
            val atMs = handle.positionMs()
            val durationMs = handle.durationMs()
            handle.stop()
            openGeneration++
            _actionNotice.value = null
            openFsk.value = null
            openSetId.value = null
            viewModelScope.launch {
                if (setId != null && atMs != null) {
                    recorder.save(setId, atMs / 1000.0, durationMs?.let { it / 1000.0 })
                }
                // Only once the save above has landed, so the round this starts
                // has this position to sync rather than the one before it —
                // the moment a viewer is most likely to have moved on, and the
                // moment the web's Continue shelf most wants to hear about it.
                watchSync.soon()
            }
        }

        /**
         * Writes wherever the player currently is, against whichever set is
         * open. Called by the ten-second ticker, on pause, on leaving the
         * player, and by the screen's own `ON_STOP` observer — every one of the
         * web's save points except `pagehide`, which Android has no equivalent
         * of stopping to spare. A no-op with nothing open or nothing trustworthy
         * yet ([PlayerHandle.positionMs] is `null` before the player is ready or
         * after an error).
         */
        fun save() {
            val setId = openSetId.value ?: return
            val atMs = handle.positionMs() ?: return
            val durationMs = handle.durationMs()
            viewModelScope.launch {
                recorder.save(setId, atMs / 1000.0, durationMs?.let { it / 1000.0 })
            }
        }

        /**
         * The three writes [marks] feeds a label to — ported from `player.js`'s
         * `watchlistButton`/`kidsButton`/`addToButton` click handlers. Each is a
         * no-op with nothing open, the same guard those handlers open with.
         */
        fun toggleWatchlist() {
            val setId = openSetId.value ?: return
            val listed = setId in repository.snapshot.value.watchlist
            writeMark("Watchlist update") {
                repository.setWatchlisted(setId, !listed)
                true
            }
        }

        /** Marked here rather than on a shelf: "this is where a viewer is when they find out what a film actually is." */
        fun toggleKids() {
            val setId = openSetId.value ?: return
            // A rated title is not marked: its rating already decided.
            if (kidsVerdictOf(openFsk.value) != KidsVerdict.UNRATED) return
            val marked = setId in repository.snapshot.value.kids
            writeMark("Kids update") {
                repository.setKids(setId, !marked)
                true
            }
        }

        /** Files the open title into an existing list, or takes it back off one. */
        fun setInList(
            listId: String,
            included: Boolean,
        ) {
            val setId = openSetId.value ?: return
            writeMark("list update") { repository.setInList(listId, setId, included) }
        }

        /**
         * Makes a new list and puts the open title straight on it — one action
         * where the web's `addToButton` needs two, since its list of lists lives
         * on the Collections shelf and this dialog does not want to send a
         * viewer mid-film away from the player to reach it. See `AddToListDialog`.
         */
        fun createListAndAdd(name: String) {
            val setId = openSetId.value ?: return
            writeMark("list update") {
                val created = repository.createList(name) ?: return@writeMark false
                repository.setInList(created.id, setId, true)
            }
        }

        /** A write can commit before its snapshot reload fails; only repository snapshots acknowledge marks. */
        private fun writeMark(
            action: String,
            write: suspend () -> Boolean,
        ) {
            val generation = openGeneration
            viewModelScope.launch {
                val confirmed =
                    try {
                        write()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (
                        @Suppress("TooGenericExceptionCaught") e: Exception,
                    ) {
                        false
                    }
                if (generation != openGeneration) return@launch
                val failure = "Could not confirm the $action. Check it and try again."
                if (!confirmed) {
                    _actionNotice.value = failure
                } else if (_actionNotice.value == failure) {
                    _actionNotice.value = null
                }
            }
        }

        override fun onPlayingChanged(isPlaying: Boolean) {
            _state.value = if (isPlaying) PlayerUiState.Playing else PlayerUiState.Paused
            if (isPlaying) {
                startTicking()
            } else {
                stopTicking()
                save()
            }
        }

        override fun onError(message: String) {
            stopTicking()
            _state.value = PlayerUiState.Failed(message)
        }

        override fun onCleared() {
            stopTicking()
            handle.release()
        }

        private fun startTicking() {
            if (tickerJob?.isActive == true) return
            tickerJob =
                viewModelScope.launch {
                    while (true) {
                        delay(TICK_MS)
                        save()
                    }
                }
        }

        private fun stopTicking() {
            tickerJob?.cancel()
            tickerJob = null
        }

        private companion object {
            const val TICK_MS = 10_000L
        }
    }
