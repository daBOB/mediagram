package player

import androidx.media3.common.PlaybackException
import androidx.media3.common.Player

/**
 * The bridge between the real player's events and [DefaultPlayerHandle] —
 * split out to keep that file under the project's line guideline. This is
 * itself process-lifetime, attached exactly once to the app's singleton
 * player; see [DefaultPlayerHandle]'s own doc for why.
 *
 * A failed player drops back to `STATE_IDLE` and then stays silent, so
 * nothing more arrives on its own to move a subscriber off the error —
 * that is exactly the state `open()` reloads from, which is what makes
 * trying the same set again work rather than hang.
 */
internal class PlayerHandleListener(
    private val isCurrentlyPlaying: () -> Boolean,
    private val notifyPlaying: (isPlaying: Boolean) -> Unit,
    private val notifyError: (message: String) -> Unit,
) : Player.Listener {

    override fun onIsPlayingChanged(isPlaying: Boolean) = notifyPlaying(isPlaying)

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_READY) notifyPlaying(isCurrentlyPlaying())
    }

    override fun onPlayerError(error: PlaybackException) {
        notifyError(error.message ?: "Playback failed")
    }
}
