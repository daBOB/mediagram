package ui.tv.player

import androidx.compose.ui.input.key.Key

/**
 * What a remote key press asks the television player to do. A sealed
 * answer rather than a set of separate booleans, so a caller can act on
 * exactly one thing and a test can assert on exactly one thing.
 */
sealed interface TvKeyAction {
    /** Toggles play/pause and nothing else — the combined media key, either state. */
    data object TogglePlay : TvKeyAction

    /** Plays, and does nothing to a film already playing — the dedicated Play key, either state. */
    data object Play : TvKeyAction

    /** Pauses, and does nothing to a film already paused — the dedicated Pause key, either state. */
    data object Pause : TvKeyAction

    /** Toggles play/pause and brings the controls up: Centre/Enter's first press, with the controls not there yet to select from instead. */
    data object TogglePlayAndShowControls : TvKeyAction

    /** Moves the position by [seconds] (negative skips back), the controls already up. */
    data class SeekBy(val seconds: Int) : TvKeyAction

    /** Moves the position by [seconds] and brings the controls up for it, briefly. */
    data class SeekByAndShowControls(val seconds: Int) : TvKeyAction

    /** Brings the controls up with focus already on the seek bar — Up/Down's first press. */
    data object ShowControlsAndFocusSeekBar : TvKeyAction

    /** Closes the settings panel, and only that: the controls stay up behind it. */
    data object ClosePanel : TvKeyAction

    /** Puts the controls away without leaving the title. */
    data object HideControls : TvKeyAction

    /** Leaves the player, saving position the way the phone does on Back. */
    data object Leave : TvKeyAction

    /** Not this table's to answer: ordinary focus movement, or a focused control's own click — Compose's own input handling already does the right thing once this steps aside. */
    data object PassThrough : TvKeyAction

    /** A key this remote can send that has no row here. */
    data object Ignore : TvKeyAction
}

/** The keys the settings panel takes for itself while it is open: moving between its rows, and choosing one. */
private val PANEL_KEYS =
    setOf(Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight, Key.DirectionCenter, Key.Enter)

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
 *
 * [canControl] is whether there is a film to control at all — false while
 * it is still preparing or has failed, when the phone shows no controls and
 * so offers nothing to press. Then every key but Back is ignored: a skip or
 * a pause aimed at a player with nothing loaded would be a command nobody
 * could see land.
 *
 * [panelOpen] is the settings panel, which answers before anything else:
 * Back closes it, and the D-pad and Centre are ordinary focus movement and
 * selection inside it — a Left meant for the next row of choices must not
 * skip the film ten seconds. The dedicated media keys keep their meaning,
 * as they do everywhere: a viewer can pause to look at a subtitle size
 * without closing the panel first.
 */
fun tvKeyAction(
    key: Key,
    controlsShowing: Boolean,
    focusInControls: Boolean,
    canControl: Boolean = true,
    panelOpen: Boolean = false,
): TvKeyAction {
    if (panelOpen) {
        return when (key) {
            Key.Back -> TvKeyAction.ClosePanel
            in PANEL_KEYS -> TvKeyAction.PassThrough
            else -> tvKeyAction(key, controlsShowing = true, focusInControls = false, canControl = canControl)
        }
    }
    if (!canControl) return if (key == Key.Back) TvKeyAction.Leave else TvKeyAction.Ignore
    if (!controlsShowing) {
        return when (key) {
            Key.DirectionCenter, Key.Enter -> TvKeyAction.TogglePlayAndShowControls
            Key.MediaPlayPause -> TvKeyAction.TogglePlay
            Key.MediaPlay -> TvKeyAction.Play
            Key.MediaPause -> TvKeyAction.Pause
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
        Key.MediaPlayPause -> TvKeyAction.TogglePlay
        Key.MediaPlay -> TvKeyAction.Play
        Key.MediaPause -> TvKeyAction.Pause
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
