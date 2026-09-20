package player

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/** Tracks what the player is doing for whichever set is currently open. */
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val handle: PlayerHandle,
) : ViewModel(), PlayerHandle.Listener {

    private val _state = MutableStateFlow<PlayerUiState>(PlayerUiState.Preparing)
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    /** Handed straight to `PlayerSurface` by the UI layer; not part of [state]. */
    val player get() = handle.player

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

    override fun onPositionChanged(positionMs: Long, durationMs: Long, isPlaying: Boolean) {
        _state.value = if (isPlaying) {
            PlayerUiState.Playing(positionMs, durationMs)
        } else {
            PlayerUiState.Paused(positionMs, durationMs)
        }
    }

    override fun onError(message: String) {
        _state.value = PlayerUiState.Failed(message)
    }

    override fun onCleared() {
        handle.release()
    }
}
