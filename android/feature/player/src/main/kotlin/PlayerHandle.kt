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

    /**
     * @param startAtMs where to seek once the set is loaded; 0 for the top.
     * @param playWhenReady whether to start on its own once ready — false
     * for an up-next switch waiting on the autoplay gate; true (the
     * default) everywhere else, so every existing caller is unchanged.
     */
    fun open(setId: String, startAtMs: Long, playWhenReady: Boolean = true)
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

    /** As [positionMs], for how far playback is buffered ahead — the autoplay gate's own reading of the same trust rule. */
    fun bufferedPositionMs(): Long?

    /** Starts (or resumes) playback on whatever is currently open — the autoplay gate's "now", once it is satisfied. */
    fun play()

    /**
     * Whether the loader is still fetching — `false` once it has stopped,
     * whether because there is nothing left to fetch or because the load
     * control has decided it is holding enough. The autoplay gate reads
     * this beside [bufferedPositionMs]: media3's default load control caps
     * how far it will ever buffer ahead well under the web's own 60s
     * threshold, so a loader that has stopped with anything at all held is
     * as ready as it is ever going to get.
     */
    fun isLoading(): Boolean

    /**
     * Sets the transport's playback rate. Queued rather than dropped when
     * there is no player yet — [DefaultPlayerHandle] applies it the moment
     * one exists, the same as a queued [open].
     */
    fun setPlaybackSpeed(rate: Float)

    /** Playback facts; [PlayerViewModel] maps these onto [PlayerUiState]. */
    interface Listener {
        fun onPlayingChanged(isPlaying: Boolean)
        fun onError(message: String)

        /** The open title ran out, or was seeked past its last frame — a no-op default, since only the up-next controller acts on it. */
        fun onEnded() {}

        /** A seek landed — a no-op default; only the up-next controller acts on it, to update the card while paused (`timeupdate` fires on a seek too). */
        fun onSeeked() {}
    }
}
