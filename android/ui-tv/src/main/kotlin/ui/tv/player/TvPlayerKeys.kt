package ui.tv.player

import androidx.compose.ui.input.key.Key

/**
 * What a remote key press asks the television player to do. A sealed
 * answer rather than a set of separate booleans, so a caller can act on
 * exactly one thing and a test can assert on exactly one thing.
 */
sealed interface TvKeyAction {
    /** Toggles play/pause and nothing else — the dedicated media key, either state. */
    data object TogglePlay : TvKeyAction

    /** Toggles play/pause and brings the controls up: Centre/Enter's first press, with the controls not there yet to select from instead. */
    data object TogglePlayAndShowControls : TvKeyAction

    /** Moves the position by [seconds] (negative skips back), the controls already up. */
    data class SeekBy(val seconds: Int) : TvKeyAction

    /** Moves the position by [seconds] and brings the controls up for it, briefly. */
    data class SeekByAndShowControls(val seconds: Int) : TvKeyAction

    /** Brings the controls up with focus already on the seek bar — Up/Down's first press. */
    data object ShowControlsAndFocusSeekBar : TvKeyAction

    /** Puts the controls away without leaving the title. */
    data object HideControls : TvKeyAction

    /** Leaves the player, saving position the way the phone does on Back. */
    data object Leave : TvKeyAction

    /** Not this table's to answer: ordinary focus movement, or a focused control's own click — Compose's own input handling already does the right thing once this steps aside. */
    data object PassThrough : TvKeyAction

    /** A key this remote can send that has no row here. */
    data object Ignore : TvKeyAction
}

/** Ten seconds, the same skip the phone and the web both use. */
private const val SKIP_SECONDS = 10

/**
 * The remote key table as a pure function — no element, no playback, no
 * deciding here whether the caller consumes the event. Mirrors the shape of
 * the web player's own `keyAction`: a table that can be proved without a
 * screen behind it.
 *
 * [controlsShowing] is the transport overlay's own visibility.
 * [focusInControls] is narrower: whether the focused node is the seek bar,
 * the one control the table gives Left/Right a meaning of its own — moving
 * the position rather than moving focus. Everywhere else the controls are
 * showing, Left/Right is ordinary focus movement between them and this
 * function steps aside.
 */
fun tvKeyAction(
    key: Key,
    controlsShowing: Boolean,
    focusInControls: Boolean,
): TvKeyAction {
    if (!controlsShowing) {
        return when (key) {
            Key.DirectionCenter, Key.Enter -> TvKeyAction.TogglePlayAndShowControls
            Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> TvKeyAction.TogglePlay
            Key.DirectionLeft, Key.MediaRewind -> TvKeyAction.SeekByAndShowControls(-SKIP_SECONDS)
            Key.DirectionRight, Key.MediaFastForward -> TvKeyAction.SeekByAndShowControls(SKIP_SECONDS)
            Key.DirectionUp, Key.DirectionDown -> TvKeyAction.ShowControlsAndFocusSeekBar
            Key.Back -> TvKeyAction.Leave
            // Next/Previous would move through a play order; the player has none, so there is nothing to do.
            Key.MediaNext, Key.MediaPrevious -> TvKeyAction.Ignore
            else -> TvKeyAction.Ignore
        }
    }

    return when (key) {
        Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> TvKeyAction.TogglePlay
        Key.MediaRewind -> TvKeyAction.SeekBy(-SKIP_SECONDS)
        Key.MediaFastForward -> TvKeyAction.SeekBy(SKIP_SECONDS)
        Key.DirectionLeft -> if (focusInControls) TvKeyAction.SeekBy(-SKIP_SECONDS) else TvKeyAction.PassThrough
        Key.DirectionRight -> if (focusInControls) TvKeyAction.SeekBy(SKIP_SECONDS) else TvKeyAction.PassThrough
        Key.DirectionCenter, Key.Enter, Key.DirectionUp, Key.DirectionDown -> TvKeyAction.PassThrough
        Key.Back -> TvKeyAction.HideControls
        Key.MediaNext, Key.MediaPrevious -> TvKeyAction.Ignore
        else -> TvKeyAction.Ignore
    }
}
