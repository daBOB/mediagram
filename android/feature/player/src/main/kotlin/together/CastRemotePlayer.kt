// SimpleBasePlayer is marked @UnstableApi; see core/playback's CacheProvider.kt
// for why media3's opt-in is taken at file scope.
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package player.together

import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import model.together.PlaybackChange
import model.together.Session
import model.together.expectedPosition
import playback.SKIP_MS

private const val MS = 1000.0

/** What the player card reads and presses: its transport, seek bar and speed. */
private val COMMANDS =
    Player.Commands.Builder()
        .addAll(
            Player.COMMAND_PLAY_PAUSE,
            Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
            Player.COMMAND_SEEK_BACK,
            Player.COMMAND_SEEK_FORWARD,
            Player.COMMAND_SET_SPEED_AND_PITCH,
            Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
            Player.COMMAND_GET_TIMELINE,
        ).build()

/**
 * A player that plays nothing: it shows a cast session as the TV plays it,
 * so the phone's own player card can be the remote. Its play, pause, seek,
 * skip and speed go out through [onMove] as the viewer's change to the
 * session; what it shows changes only through [show].
 */
internal class CastRemotePlayer(
    looper: Looper,
    private val now: () -> Long,
    private val onMove: (PlaybackChange) -> Unit,
) : SimpleBasePlayer(looper) {
    private var shown = State.Builder().setAvailableCommands(COMMANDS).build()

    /** Shows [session] on a title [durationMs] long, or [C.TIME_UNSET] while that is unknown. */
    fun show(
        session: Session,
        durationMs: Long,
    ) {
        val item = MediaItemData.Builder(session.set).setIsSeekable(true)
        if (durationMs != C.TIME_UNSET) item.setDurationUs(durationMs * 1000)
        shown =
            State.Builder()
                .setAvailableCommands(COMMANDS)
                .setPlaylist(listOf(item.build()))
                .setPlaybackState(Player.STATE_READY)
                .setPlayWhenReady(session.playing, Player.PLAY_WHEN_READY_CHANGE_REASON_REMOTE)
                .setPlaybackParameters(PlaybackParameters(session.rate.toFloat()))
                .setContentPositionMs { (expectedPosition(session, now()) * MS).toLong() }
                // The player's own skip, so the card's buttons read and step the same 15 s.
                .setSeekBackIncrementMs(SKIP_MS)
                .setSeekForwardIncrementMs(SKIP_MS)
                .build()
        invalidateState()
    }

    override fun getState(): State = shown

    override fun handleSetPlayWhenReady(playWhenReady: Boolean) = moved(PlaybackChange(playing = playWhenReady))

    override fun handleSeek(
        mediaItemIndex: Int,
        positionMs: Long,
        seekCommand: Int,
    ) = moved(PlaybackChange(pos = positionMs / MS))

    override fun handleSetPlaybackParameters(playbackParameters: PlaybackParameters) =
        moved(PlaybackChange(rate = playbackParameters.speed.toDouble()))

    /** The move is shown at once through [show]: nothing is left pending. */
    private fun moved(change: PlaybackChange): ListenableFuture<*> {
        onMove(change)
        return Futures.immediateVoidFuture()
    }
}
