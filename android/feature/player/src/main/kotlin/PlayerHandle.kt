package player

import androidx.media3.common.Player
import kotlinx.coroutines.flow.StateFlow

/**
 * Abstracts a real ExoPlayer so [PlayerViewModel] is testable on the JVM
 * without one. [DefaultPlayerHandle] is the production implementation,
 * backed by the app's single ExoPlayer; a fake stands in for it under
 * test.
 */
interface PlayerHandle {
    /**
     * The underlying player, handed straight to `PlayerSurface` by the UI
     * layer once it's non-null. Null until the player has finished
     * building on a background thread — the UI layer is expected to
     * render nothing (the ViewModel's own `Preparing` state already
     * covers this) until it arrives.
     */
    val player: StateFlow<Player?>

    fun open(setId: String)
    fun setListener(listener: Listener?)

    /** Stops playback and releases the decoder/audio focus the player is holding. */
    fun stop()

    /** Detaches whichever listener is currently subscribed. Safe to call more than once. */
    fun release()

    /** Playback facts; [PlayerViewModel] maps these onto [PlayerUiState]. */
    interface Listener {
        fun onPlayingChanged(isPlaying: Boolean)
        fun onError(message: String)
    }
}
