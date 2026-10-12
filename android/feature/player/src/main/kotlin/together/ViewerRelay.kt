package player.together

import model.together.PlaybackChange
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Carries the viewer's moves from the one player's controls, the screen's
 * and the media session's [ViewerPlayer]s, to the controller of the player
 * on screen. One per process, as the player is.
 */
@Singleton
class ViewerRelay @Inject constructor() {
    /** Who hears the moves. The main thread's alone, as the player's calls are. */
    internal var controller: TogetherController? = null

    fun report(change: PlaybackChange) {
        controller?.viewer(change)
    }
}
