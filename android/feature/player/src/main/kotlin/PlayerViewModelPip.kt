package player

/**
 * The viewer dismissed the picture-in-picture window (the ✕, or
 * swiping it away) rather than expanding it back — a pause, not
 * [PlayerViewModel.stop]: the title stays open (state, choices, up-next all
 * untouched), so reopening the app finds it exactly where it was,
 * paused, rather than back at the catalog. The service stops
 * regardless — nothing plays with the window gone, so there is
 * nothing left to keep a notification or a foreground state for
 * until the viewer returns. It leaves watch together first, and pauses
 * the raw player, so the pause stops this screen alone.
 */
fun PlayerViewModel.pauseForPipDismissal() {
    togetherController.leave()
    handle.pause()
    session.save()
    playbackServiceController.stop()
}
