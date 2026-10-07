package player

import androidx.media3.common.Player

/*
 * The card's own steps through the run and back to the top of the title.
 */

/**
 * Opens the title before the open one in its run, through the switch Next
 * takes — so it saves where this one stood and moves the library to match.
 * Nothing at the start of a run or without one, and never a restart:
 * [restart] is that, a button of its own.
 */
fun PlayerViewModel.previous() {
    val open = session.openSetId ?: return
    previousInQueue(upNext.value.run, open)?.let(upNextController::playFromRun)
}

/** A row picked in the episode sidebar, opened the same way; the open row, or one the run does not hold, does nothing. */
fun PlayerViewModel.playFromRun(setId: String) = upNextController.playFromRun(setId)

/**
 * Back to 0:00 of the open title. A seek, so it leaves play and pause as
 * they were — except past the credits, where the picture is stopped but
 * media3 still holds play-when-ready: that is paused first, so the viewer
 * who pressed ↺ on a stopped picture gets the stopped first frame, as on
 * the web. A countdown already running after the credits stops, since the
 * seek has left the end (`UpNextController.onSeeked`).
 */
fun PlayerViewModel.restart() {
    val player = handle.player.value ?: return
    if (player.playbackState == Player.STATE_ENDED) player.pause()
    player.seekTo(0L)
}
