package player

import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import playback.setUri
import javax.inject.Inject

/**
 * Wraps the app's single [ExoPlayer], translating its events into
 * [PlayerHandle.Listener] calls. [open] never touches the network or the
 * core directly: it only sets a `mlib://` media item and lets the cached
 * `DataSource` chain built in [buildPlayer][playback.buildPlayer] do the
 * rest.
 */
class DefaultPlayerHandle @Inject constructor(
    override val player: ExoPlayer,
) : PlayerHandle {

    private var listener: PlayerHandle.Listener? = null

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) = notifyPosition(isPlaying)

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) notifyPosition(player.isPlaying)
        }

        override fun onPlayerError(error: PlaybackException) {
            listener?.onError(error.message ?: "Playback failed")
        }
    }

    init {
        player.addListener(playerListener)
    }

    override fun open(setId: String) {
        player.setMediaItem(MediaItem.fromUri(setUri(setId)))
        player.prepare()
        player.playWhenReady = true
    }

    override fun setListener(listener: PlayerHandle.Listener?) {
        this.listener = listener
    }

    override fun release() {
        player.removeListener(playerListener)
    }

    private fun notifyPosition(isPlaying: Boolean) {
        listener?.onPositionChanged(
            positionMs = player.currentPosition,
            durationMs = player.duration.coerceAtLeast(0),
            isPlaying = isPlaying,
        )
    }
}
