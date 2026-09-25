package player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import dagger.hilt.android.lifecycle.HiltViewModel
import data.CatalogRepository
import data.PlayerPreferences
import data.WatchStateRepository
import data.WatchSync
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import model.MediaSet
import playback.HeldSetsQuery
import playback.PlaybackCounters
import playback.PlaybackTotals
import playback.SeriesPreloading
import playback.SubtitleTrackSource
import playback.SummarySource
import playback.TimedCue
import javax.inject.Inject

/** Tracks what the player is doing for whichever set is currently open. */
@HiltViewModel
class PlayerViewModel @Inject constructor(
    internal val handle: PlayerHandle,
    counters: PlaybackCounters,
    internal val repository: WatchStateRepository,
    private val recorder: ProgressRecorder,
    private val watchSync: WatchSync,
    catalogRepository: CatalogRepository,
    preferences: PlayerPreferences,
    subtitleTrackSource: SubtitleTrackSource,
    internal val playbackServiceController: PlaybackServiceController = PlaybackServiceController.Noop,
    seriesPreloader: SeriesPreloading,
    heldSets: HeldSetsQuery,
    summarySource: SummarySource = SummarySource.None,
) : ViewModel(), PlayerHandle.Listener {

    internal val _state = MutableStateFlow<PlayerUiState>(PlayerUiState.Preparing)
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

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

    /** The ten-second save ticker and which set it saves against. */
    internal val session = PlayerSession(viewModelScope, handle, recorder)

    internal val _openSetId = MutableStateFlow<String?>(null)

    /** The open title's age rating, as the catalog listed it; see [open]. */
    internal val openFsk = MutableStateFlow<String?>(null)

    internal val choicesController =
        PlayerChoicesController(viewModelScope, session, repository, catalogRepository, preferences, handle, subtitleTrackSource)

    /** The resolved set behind the open id, for the title line — null before it resolves, or with nothing open. */
    val openSet: StateFlow<MediaSet?> = choicesController.openSet

    /** What this viewer has chosen for the open title — speed, audio and subtitle style; framing joins it later. */
    val choices: StateFlow<PlayerChoices> = choicesController.choices

    /** The open title's subtitle cues, once its chosen language's VTT has resolved — empty for "off" or a file with none. */
    val subtitleCues: StateFlow<List<TimedCue>> = choicesController.subtitleCues

    internal val marksController = PlayerMarksController(viewModelScope, session, repository, _openSetId, openFsk)
    val marks: StateFlow<PlayerMarksState?> = marksController.marks

    /** What follows the open title, and the countdown that may start it unattended — ported from `refreshUpNext`/`startWhenReady` in `player.js`. */
    internal val upNextController = UpNextController(viewModelScope, handle, session, catalogRepository, choicesController.openSet)
    val upNext: StateFlow<UpNextUiState> = upNextController.state

    /** Takes the next two episodes of an open show into the cache while this one plays. */
    internal val preloadController = PlayerPreloadController(viewModelScope, catalogRepository, seriesPreloader)

    /** Whether the open title plays with no network at all — the stats overlay's "cached" line. */
    internal val heldController = PlayerHeldController(viewModelScope, heldSets, seriesPreloader, choicesController.openSet)
    val held: StateFlow<Boolean> = heldController.held

    /** The open title's notes, when it has any; see [PlayerNotesController]. */
    internal val notesController = PlayerNotesController(viewModelScope, summarySource, choicesController.openSet)
    val notes: StateFlow<PlayerNotes?> = notesController.notes
    fun toggleNotes() = notesController.toggle()

    /** A title to navigate to, once — the UI layer owns `LibraryPositions`, so it (not this VM) moves there and calls [switchAcknowledged]. */
    val pendingSwitch: StateFlow<PendingPlayerSwitch?> = upNextController.pendingSwitch
    fun switchAcknowledged() = upNextController.switchAcknowledged()

    /** The run changed for the still-open title (the catalog was not Ready yet when it opened). */
    fun updateRun(setId: String, run: List<String>) = upNextController.updateRun(setId, run)

    init {
        handle.setListener(this)
        syncMetadataToHandle()
    }

    /** Starts whatever follows the open title, the same as its own "Play next" button. */
    fun playNext() = upNextController.playNow()

    /** The viewer dismissed the up-next panel; the standing button stays. */
    fun cancelUpNext() = upNextController.cancel()

    /** Called when the player screen leaves composition, so codecs and audio focus aren't held idle. */
    fun stop() {
        // Read before handle.stop(), which drops the player to STATE_IDLE —
        // positionMs()/durationMs() would already answer null afterwards.
        val setId = session.openSetId
        val atMs = handle.positionMs()
        val durationMs = handle.durationMs()
        handle.stop()
        playbackServiceController.stop()
        session.clear()
        openFsk.value = null
        _openSetId.value = null
        choicesController.reset()
        upNextController.stop()
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
     * player, and by the screen's own `ON_STOP` observer.
     */
    fun save() = session.save()

    /**
     * The viewer dismissed the picture-in-picture window (the ✕, or
     * swiping it away) rather than expanding it back — a pause, not
     * [stop]: the title stays open (state, choices, up-next all
     * untouched), so reopening the app finds it exactly where it was,
     * paused, rather than back at the catalog. The service stops
     * regardless — nothing plays with the window gone, so there is
     * nothing left to keep a notification or a foreground state for
     * until the viewer returns.
     */
    fun pauseForPipDismissal() {
        handle.pause()
        session.save()
        playbackServiceController.stop()
    }

    override fun onPlayingChanged(isPlaying: Boolean) {
        _state.value = if (isPlaying) PlayerUiState.Playing else PlayerUiState.Paused
        // A picture-in-picture dismissal pauses and stops the service
        // without closing the title (`pauseForPipDismissal`); pressing play
        // again on that same still-open title never runs through `open()`,
        // so this is the only place that notices playback has resumed and
        // has to bring the session back. Safe on every ordinary play too —
        // starting an already-running service is a no-op.
        if (isPlaying) playbackServiceController.start()
        session.onPlayingChanged(isPlaying)
        upNextController.onPlayingChanged(isPlaying)
    }

    override fun onError(message: String) {
        session.stopTicking()
        _state.value = PlayerUiState.Failed(message)
    }

    override fun onEnded() = upNextController.onEnded()

    override fun onSeeked() = upNextController.onSeeked()

    override fun onCleared() {
        session.stopTicking()
        choicesController.release()
        handle.release()
    }
}
