package player.together

import androidx.media3.common.Player
import model.together.ScreenState
import model.together.Session
import model.together.Step
import model.together.decide
import player.PlayerHandle
import kotlin.math.roundToInt

private const val MS = 1000.0

/**
 * A media3 speed as a session holds it. media3's is a float, and 1.1f is
 * 1.100000023841858 as a double: unrounded, no screen's speed would ever
 * match a session's, and every check would set it again.
 */
internal fun Float.rate(): Double = (this * 100).roundToInt() / 100.0

/**
 * Moving the player to where a session is, by `decide` (`model.together`),
 * the twin of the web player's `together-steps.js`. Nothing here is sent
 * anywhere: these are the changes a screen makes because it is following,
 * and they go to the raw player, under the viewer's [ViewerPlayer].
 */
internal class TogetherSteps(
    private val handle: PlayerHandle,
    private val now: () -> Long,
) {
    /** What the player is doing on [setId]. At a title's end it is not playing, as an ended element is paused on the web. */
    fun own(setId: String?): ScreenState? {
        val player = handle.player.value ?: return null
        val playing = player.playWhenReady && player.playbackState != Player.STATE_ENDED
        return ScreenState(setId, playing, (handle.positionMs() ?: 0L) / MS, player.playbackParameters.speed.rate())
    }

    /**
     * Brings the player, on [setId], to [session]; or names the title to
     * open first. A drift check passes over a player not ready to play,
     * loading, buffering or at its end: nobody waits for it, and the next
     * check catches it up. At its end the player waits for the next title,
     * which Up next or the viewer opens. A session still playing reads on
     * past that end, and going there, or playing, would start this title
     * again from nothing; only a seek back moves it.
     */
    fun bring(
        session: Session,
        setId: String?,
        drifting: Boolean,
    ): String? {
        val player = handle.player.value ?: return null
        if (drifting && player.playbackState != Player.STATE_READY) return null
        val own = own(setId) ?: return null
        var atEnd = player.playbackState == Player.STATE_ENDED
        for (step in decide(own, session, now())) {
            when (step) {
                is Step.Open -> return step.set
                is Step.Rate -> handle.setPlaybackSpeed(step.rate.toFloat())
                is Step.Seek ->
                    if (!atEnd || step.pos < own.pos) {
                        player.seekTo((step.pos * MS).toLong())
                        atEnd = false
                    }
                Step.Play -> if (!atEnd) handle.play()
                Step.Pause -> handle.pause()
            }
        }
        return null
    }
}
