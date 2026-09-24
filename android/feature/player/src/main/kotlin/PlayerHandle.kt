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

    /** @param startAtMs where to seek once the set is loaded; 0 for the top. */
    fun open(
        setId: String,
        startAtMs: Long,
    )

    fun setListener(listener: Listener?)

    /** Stops playback and releases the decoder/audio focus the player is holding. */
    fun stop()

    /** Detaches whichever listener is currently subscribed. Safe to call more than once. */
    fun release()

    /**
     * The playhead, or `null` when there is nothing to trust it against — no
     * player yet, or one sitting in `STATE_IDLE` (never prepared, stopped,
     * or just failed). A recorder that saw `null` here has nothing to save,
     * which is the point: a position from before the first frame or after
     * an error is not a place to resume to.
     */
    fun positionMs(): Long?

    /** As [positionMs], for the set's length; `null` on the same terms, or while media3 hasn't measured it yet. */
    fun durationMs(): Long?

    /** Playback facts; [PlayerViewModel] maps these onto [PlayerUiState]. */
    interface Listener {
        fun onPlayingChanged(isPlaying: Boolean)

        fun onError(message: String)
    }
}
