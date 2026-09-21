package player

import androidx.lifecycle.ViewModel
import androidx.media3.common.Player
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import playback.PlaybackCounters
import playback.PlaybackTotals
import javax.inject.Inject

/** Tracks what the player is doing for whichever set is currently open. */
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val handle: PlayerHandle,
    counters: PlaybackCounters,
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

    init {
        handle.setListener(this)
    }

    fun open(setId: String) {
        _state.value = PlayerUiState.Preparing
        handle.open(setId)
    }

    /** Called when the player screen leaves composition, so codecs and audio focus aren't held idle. */
    fun stop() {
        handle.stop()
    }

    override fun onPlayingChanged(isPlaying: Boolean) {
        _state.value = if (isPlaying) PlayerUiState.Playing else PlayerUiState.Paused
    }

    override fun onError(message: String) {
        _state.value = PlayerUiState.Failed(message)
    }

    override fun onCleared() {
        handle.release()
    }
}
