package player

/*
 * When the control bar may be on screen, and when it goes away on its own.
 *
 * Pure, and kept apart from the composable, because this module has no Compose
 * test rule — `PlayerScreenTest` says so. A decision that lives in a function
 * can be proved; the same decision inside a `LaunchedEffect` cannot, here,
 * today.
 */

/** How long the bar stays after a tap, while the film is running. */
const val CONTROLS_LINGER_MS = 4_000L

/**
 * How often the readout catches up with the playhead. Twice a second: a clock
 * printing whole seconds needs no more, and a tick is a recomposition.
 *
 * Shared between the transport bar and the statistics overlay rather than
 * copied, so the two read the player on one interval instead of drifting
 * apart on two.
 */
const val TICK_MS = 500L

/**
 * Whether there is anything to control.
 *
 * A set that is preparing has no playhead to move and nothing to pause, and a
 * bar drawn over an error invites a press that cannot do anything — worse than
 * no bar, because it looks like the error might be dismissible.
 */
fun controlsMayShow(state: PlayerUiState): Boolean = state == PlayerUiState.Playing || state == PlayerUiState.Paused

/**
 * Whether the bar should take itself away.
 *
 * A running film gets its picture back; a paused one keeps its controls,
 * because nothing else on screen offers a way to start again and the tap that
 * would bring them back is invisible. A drag in progress keeps them too — the
 * slider cannot be pulled out from under the thumb holding it.
 */
fun controlsShouldFade(
    isPlaying: Boolean,
    isScrubbing: Boolean,
): Boolean = isPlaying && !isScrubbing
