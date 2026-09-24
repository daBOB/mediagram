package player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import dagger.hilt.android.lifecycle.HiltViewModel
import data.CatalogRepository
import data.PlayerPreferences
import data.ProgressPoint
import data.ResumePoint
import data.WatchStateRepository
import data.WatchSync
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import model.MediaSet
import playback.AudioOption
import playback.PlaybackCounters
import playback.PlaybackTotals
import playback.SubtitleTrackSource
import playback.TimedCue
import javax.inject.Inject

/** Tracks what the player is doing for whichever set is currently open. */
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val handle: PlayerHandle,
    counters: PlaybackCounters,
    private val repository: WatchStateRepository,
    private val recorder: ProgressRecorder,
    private val watchSync: WatchSync,
    catalogRepository: CatalogRepository,
    preferences: PlayerPreferences,
    subtitleTrackSource: SubtitleTrackSource,
) : ViewModel(), PlayerHandle.Listener {

    private val _state = MutableStateFlow<PlayerUiState>(PlayerUiState.Preparing)
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
    private val session = PlayerSession(viewModelScope, handle, recorder)

    private val _openSetId = MutableStateFlow<String?>(null)

    /** The open title's age rating, as the catalog listed it; see [open]. */
    private val openFsk = MutableStateFlow<String?>(null)

    private val choicesController =
        PlayerChoicesController(viewModelScope, session, repository, catalogRepository, preferences, handle, subtitleTrackSource)

    /** The resolved set behind the open id, for the title line — null before it resolves, or with nothing open. */
    val openSet: StateFlow<MediaSet?> = choicesController.openSet

    /** What this viewer has chosen for the open title — speed, audio and subtitle style; framing joins it later. */
    val choices: StateFlow<PlayerChoices> = choicesController.choices

    /** The open title's subtitle cues, once its chosen language's VTT has resolved — empty for "off" or a file with none. */
    val subtitleCues: StateFlow<List<TimedCue>> = choicesController.subtitleCues

    private val marksController = PlayerMarksController(viewModelScope, session, repository, _openSetId, openFsk)
    val marks: StateFlow<PlayerMarksState?> = marksController.marks

    init {
        handle.setListener(this)
    }

    /**
     * [fsk] is the title's age rating as the catalog listed it, handed in
     * rather than looked up: the screen that opened this already has the
     * set in hand from whichever shelf it came from, and a lookup here
     * could still be racing the catalog on a cold start. The set itself is
     * resolved anyway, in [PlayerChoicesController.resolve], for the title
     * line and the preference scope — both can wait the moment it takes.
     *
     * A rotation destroys and recreates the whole screen, which re-runs the
     * `LaunchedEffect` that calls this with the *same* [setId] —
     * [sameTitle] is what tells that apart from a genuinely new title.
     * Retracing any of this for it is exactly what turned a rotation
     * mid-film into a flicker of the title, a reset of the chosen speed,
     * and (a leftover `setPlaybackSpeed(1f)` used to run here regardless)
     * an audible drop to 1x — none of which the handle needs help with:
     * asking it to open a set that is already loaded and playing
     * republishes rather than reloading (`DefaultPlayerHandle.open`), and
     * only a real reload floors the rate to 1x (`DefaultPlayerHandle.openOn`).
     */
    fun open(setId: String, fsk: String? = null) {
        val sameTitle = session.openSetId == setId
        session.open(setId)
        openFsk.value = fsk
        _openSetId.value = setId
        // Reset unconditionally, same as always: for a rotation reopening
        // an already-playing title this is corrected straight back by the
        // handle's own synchronous republish (below), within this same
        // call — never actually shown — and for one still buffering it is
        // exactly what has to stay put until the player's own ready event
        // ends the wait (`PlayerReopenTest`). Only the choice, title and
        // scope skip resetting for [sameTitle], since those really would
        // otherwise flicker and reset for no reload at all.
        _state.value = PlayerUiState.Preparing
        if (!sameTitle) choicesController.reset()
        val progress = repository.snapshot.value.progress.find { it.setId == setId }
        val resumeSeconds = ResumePoint.resumeAt(progress?.let { ProgressPoint(it.at, it.duration) })
        val startAtMs = ((resumeSeconds ?: 0.0) * 1000).toLong()
        handle.open(setId, startAtMs)
        if (!sameTitle) viewModelScope.launch { choicesController.resolve(setId) }
    }

    /**
     * Re-opens the title that just failed, at wherever it was last saved
     * to — the phone's touch equivalent of the web's seek-to-retry. [open]
     * takes its "same title" path here (nothing about the choice already
     * made needs re-resolving), but the failed player was left in
     * `STATE_IDLE`, so this *is* a real reload and does floor the rate to
     * 1x inside the handle; unlike a fresh title, nothing corrects that
     * back on its own, so it is corrected here.
     */
    fun retry() {
        val setId = session.openSetId ?: return
        open(setId, openFsk.value)
        handle.setPlaybackSpeed(choicesController.choices.value.speed)
    }

    fun setSpeed(rate: Float) = choicesController.setSpeed(rate)
    fun chooseAudioTrack(option: AudioOption) = choicesController.chooseAudioTrack(option)
    fun chooseSubtitleLanguage(languageOrOff: String) = choicesController.chooseSubtitleLanguage(languageOrOff)
    fun setSubtitleSize(percent: Int) = choicesController.setSubtitleSize(percent)
    fun setSubtitleBacking(stored: String) = choicesController.setSubtitleBacking(stored)
    fun nudgeSubtitleOffset(steps: Int) = choicesController.nudgeSubtitleOffset(steps)
    fun resetSubtitleOffset() = choicesController.resetSubtitleOffset()

    /** Called when the player screen leaves composition, so codecs and audio focus aren't held idle. */
    fun stop() {
        // Read before handle.stop(), which drops the player to STATE_IDLE —
        // positionMs()/durationMs() would already answer null afterwards.
        val setId = session.openSetId
        val atMs = handle.positionMs()
        val durationMs = handle.durationMs()
        handle.stop()
        session.clear()
        openFsk.value = null
        _openSetId.value = null
        choicesController.reset()
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

    fun toggleWatchlist() = marksController.toggleWatchlist()
    fun toggleKids() = marksController.toggleKids()
    fun setInList(listId: String, included: Boolean) = marksController.setInList(listId, included)
    fun createListAndAdd(name: String) = marksController.createListAndAdd(name)

    override fun onPlayingChanged(isPlaying: Boolean) {
        _state.value = if (isPlaying) PlayerUiState.Playing else PlayerUiState.Paused
        session.onPlayingChanged(isPlaying)
    }

    override fun onError(message: String) {
        session.stopTicking()
        _state.value = PlayerUiState.Failed(message)
    }

    override fun onCleared() {
        session.stopTicking()
        choicesController.release()
        handle.release()
    }
}
