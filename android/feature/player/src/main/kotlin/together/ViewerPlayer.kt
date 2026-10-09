// ForwardingPlayer is marked @UnstableApi; see core/playback's CacheProvider.kt
// for why media3's opt-in is taken at file scope.
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package player.together

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import model.together.PlaybackChange

private const val MS = 1000.0

/**
 * The player the viewer's controls hold: the card's and the TV's buttons,
 * gestures and remote keys, the picture-in-picture window, and the media
 * session a headset and the lock screen reach. Every play, pause and seek
 * through it is the viewer's own, and is reported once made. What watch
 * together does to follow a session goes to the raw player under it, which
 * reports nothing, so following never echoes back out.
 *
 * Playing a title again from its end (`Util.handlePlayButtonAction` seeks to
 * the default position first) reports the play alone, as the web player
 * does: the drift check then brings the screen back to the session.
 */
internal class ViewerPlayer(
    player: Player,
    private val report: (PlaybackChange) -> Unit,
) : ForwardingPlayer(player) {
    override fun play() {
        super.play()
        report(PlaybackChange(playing = true))
    }

    override fun pause() {
        super.pause()
        report(PlaybackChange(playing = false))
    }

    override fun setPlayWhenReady(playWhenReady: Boolean) {
        super.setPlayWhenReady(playWhenReady)
        report(PlaybackChange(playing = playWhenReady))
    }

    override fun seekTo(positionMs: Long) {
        super.seekTo(positionMs)
        report(PlaybackChange(pos = positionMs / MS))
    }

    override fun seekTo(
        mediaItemIndex: Int,
        positionMs: Long,
    ) {
        super.seekTo(mediaItemIndex, positionMs)
        report(PlaybackChange(pos = positionMs / MS))
    }

    override fun seekBack() {
        super.seekBack()
        report(PlaybackChange(pos = currentPosition / MS))
    }

    override fun seekForward() {
        super.seekForward()
        report(PlaybackChange(pos = currentPosition / MS))
    }
}
