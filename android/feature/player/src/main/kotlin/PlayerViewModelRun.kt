package player

/*
 * The card's own steps through the run and back to the top of the title —
 * split out of [PlayerViewModel] to keep that file under the project's line
 * guideline, as `PlayerViewModelDelegates.kt` is.
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
 * they were; a countdown already running after the credits stops, since
 * the seek has left the end (`UpNextController.onSeeked`).
 */
fun PlayerViewModel.restart() {
    handle.player.value?.seekTo(0L)
}
