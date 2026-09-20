package player

import androidx.media3.common.Player

/**
 * Abstracts a real ExoPlayer so [PlayerViewModel] is testable on the JVM
 * without one. [DefaultPlayerHandle] is the production implementation,
 * backed by the app's single ExoPlayer; a fake stands in for it under
 * test.
 */
interface PlayerHandle {
    /** The underlying player, handed straight to `PlayerSurface` by the UI layer. */
    val player: Player

    fun open(setId: String)
    fun setListener(listener: Listener?)

    /** Stops playback and releases the decoder/audio focus the player is holding. */
    fun stop()

    /** Detaches whichever listener is currently subscribed. Safe to call more than once. */
    fun release()

    /** Playback facts; [PlayerViewModel] maps these onto [PlayerUiState]. */
    interface Listener {
        fun onPositionChanged(positionMs: Long, durationMs: Long, isPlaying: Boolean)
        fun onError(message: String)
    }
}
