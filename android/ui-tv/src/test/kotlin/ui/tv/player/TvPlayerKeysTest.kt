package ui.tv.player

import androidx.compose.ui.input.key.Key
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Every row of the remote key table, in both states the table has columns
 * for, plus the one row the table singles out further: the seek bar's own
 * meaning for Left/Right once focus is on it.
 */
class TvPlayerKeysTest {
    // Controls hidden — the left-hand column.

    @Test
    fun centreTogglesPlayAndBringsControlsUpWhileHidden() {
        assertEquals(TvKeyAction.TogglePlayAndShowControls, tvKeyAction(Key.DirectionCenter, controlsShowing = false, focusInControls = false))
    }

    @Test
    fun enterTogglesPlayAndBringsControlsUpWhileHidden() {
        assertEquals(TvKeyAction.TogglePlayAndShowControls, tvKeyAction(Key.Enter, controlsShowing = false, focusInControls = false))
    }

    @Test
    fun mediaPlayPauseTogglesPlayAloneWhileHidden() {
        assertEquals(TvKeyAction.TogglePlay, tvKeyAction(Key.MediaPlayPause, controlsShowing = false, focusInControls = false))
    }

    @Test
    fun mediaPlayOnlyPlaysWhileHidden() {
        assertEquals(TvKeyAction.Play, tvKeyAction(Key.MediaPlay, controlsShowing = false, focusInControls = false))
    }

    @Test
    fun mediaPauseOnlyPausesWhileHidden() {
        assertEquals(TvKeyAction.Pause, tvKeyAction(Key.MediaPause, controlsShowing = false, focusInControls = false))
    }

    @Test
    fun directionLeftSkipsBackAndBringsControlsUpWhileHidden() {
        assertEquals(TvKeyAction.SeekByAndShowControls(-10), tvKeyAction(Key.DirectionLeft, controlsShowing = false, focusInControls = false))
    }

    @Test
    fun directionRightSkipsForwardAndBringsControlsUpWhileHidden() {
        assertEquals(TvKeyAction.SeekByAndShowControls(10), tvKeyAction(Key.DirectionRight, controlsShowing = false, focusInControls = false))
    }

    @Test
    fun mediaRewindSkipsBackAndBringsControlsUpWhileHidden() {
        assertEquals(TvKeyAction.SeekByAndShowControls(-10), tvKeyAction(Key.MediaRewind, controlsShowing = false, focusInControls = false))
    }

    @Test
    fun mediaFastForwardSkipsForwardAndBringsControlsUpWhileHidden() {
        assertEquals(TvKeyAction.SeekByAndShowControls(10), tvKeyAction(Key.MediaFastForward, controlsShowing = false, focusInControls = false))
    }

    @Test
    fun directionUpBringsControlsUpFocusedOnTheSeekBar() {
        assertEquals(TvKeyAction.ShowControlsAndFocusSeekBar, tvKeyAction(Key.DirectionUp, controlsShowing = false, focusInControls = false))
    }

    @Test
    fun directionDownBringsControlsUpFocusedOnTheSeekBarToo() {
        assertEquals(TvKeyAction.ShowControlsAndFocusSeekBar, tvKeyAction(Key.DirectionDown, controlsShowing = false, focusInControls = false))
    }

    @Test
    fun backLeavesThePlayerWhileHidden() {
        assertEquals(TvKeyAction.Leave, tvKeyAction(Key.Back, controlsShowing = false, focusInControls = false))
    }

    @Test
    fun mediaNextIsIgnoredWithNoPlayOrderWhileHidden() {
        assertEquals(TvKeyAction.Ignore, tvKeyAction(Key.MediaNext, controlsShowing = false, focusInControls = false))
    }

    @Test
    fun mediaPreviousIsIgnoredWithNoPlayOrderWhileHidden() {
        assertEquals(TvKeyAction.Ignore, tvKeyAction(Key.MediaPrevious, controlsShowing = false, focusInControls = false))
    }

    // Controls showing, focus on an ordinary control (a transport button, not the seek bar).

    @Test
    fun centreActivatesTheFocusedControlWhileShowing() {
        assertEquals(TvKeyAction.PassThrough, tvKeyAction(Key.DirectionCenter, controlsShowing = true, focusInControls = false))
    }

    @Test
    fun enterActivatesTheFocusedControlWhileShowing() {
        assertEquals(TvKeyAction.PassThrough, tvKeyAction(Key.Enter, controlsShowing = true, focusInControls = false))
    }

    @Test
    fun mediaPlayPauseStillTogglesPlayWhileShowing() {
        assertEquals(TvKeyAction.TogglePlay, tvKeyAction(Key.MediaPlayPause, controlsShowing = true, focusInControls = false))
    }

    @Test
    fun mediaPlayStillOnlyPlaysWhileShowing() {
        assertEquals(TvKeyAction.Play, tvKeyAction(Key.MediaPlay, controlsShowing = true, focusInControls = false))
    }

    @Test
    fun mediaPauseStillOnlyPausesWhileShowing() {
        assertEquals(TvKeyAction.Pause, tvKeyAction(Key.MediaPause, controlsShowing = true, focusInControls = false))
    }

    @Test
    fun directionLeftMovesFocusBetweenControlsAwayFromTheSeekBar() {
        assertEquals(TvKeyAction.PassThrough, tvKeyAction(Key.DirectionLeft, controlsShowing = true, focusInControls = false))
    }

    @Test
    fun directionRightMovesFocusBetweenControlsAwayFromTheSeekBar() {
        assertEquals(TvKeyAction.PassThrough, tvKeyAction(Key.DirectionRight, controlsShowing = true, focusInControls = false))
    }

    @Test
    fun mediaRewindStillSkipsBackWhileShowing() {
        assertEquals(TvKeyAction.SeekBy(-10), tvKeyAction(Key.MediaRewind, controlsShowing = true, focusInControls = false))
    }

    @Test
    fun mediaFastForwardStillSkipsForwardWhileShowing() {
        assertEquals(TvKeyAction.SeekBy(10), tvKeyAction(Key.MediaFastForward, controlsShowing = true, focusInControls = false))
    }

    @Test
    fun directionUpMovesBetweenSeekBarTransportAndMarksRail() {
        assertEquals(TvKeyAction.PassThrough, tvKeyAction(Key.DirectionUp, controlsShowing = true, focusInControls = false))
    }

    @Test
    fun directionDownMovesBetweenSeekBarTransportAndMarksRail() {
        assertEquals(TvKeyAction.PassThrough, tvKeyAction(Key.DirectionDown, controlsShowing = true, focusInControls = false))
    }

    @Test
    fun backHidesControlsRatherThanLeavingWhileShowing() {
        assertEquals(TvKeyAction.HideControls, tvKeyAction(Key.Back, controlsShowing = true, focusInControls = false))
    }

    @Test
    fun mediaNextIsIgnoredWithNoPlayOrderWhileShowing() {
        assertEquals(TvKeyAction.Ignore, tvKeyAction(Key.MediaNext, controlsShowing = true, focusInControls = false))
    }

    @Test
    fun mediaPreviousIsIgnoredWithNoPlayOrderWhileShowing() {
        assertEquals(TvKeyAction.Ignore, tvKeyAction(Key.MediaPrevious, controlsShowing = true, focusInControls = false))
    }

    // Controls showing, focus specifically on the seek bar: Left/Right step time rather than move focus.

    @Test
    fun directionLeftStepsTheSeekBarBackRatherThanMovingFocus() {
        assertEquals(TvKeyAction.SeekBy(-10), tvKeyAction(Key.DirectionLeft, controlsShowing = true, focusInControls = true))
    }

    @Test
    fun directionRightStepsTheSeekBarForwardRatherThanMovingFocus() {
        assertEquals(TvKeyAction.SeekBy(10), tvKeyAction(Key.DirectionRight, controlsShowing = true, focusInControls = true))
    }

    @Test
    fun backStillHidesControlsWithFocusOnTheSeekBar() {
        assertEquals(TvKeyAction.HideControls, tvKeyAction(Key.Back, controlsShowing = true, focusInControls = true))
    }

    // A key this remote can send with no row in the table at all.

    @Test
    fun anUnmappedKeyIsIgnoredWhileHidden() {
        assertEquals(TvKeyAction.Ignore, tvKeyAction(Key.A, controlsShowing = false, focusInControls = false))
    }

    @Test
    fun anUnmappedKeyIsIgnoredWhileShowing() {
        assertEquals(TvKeyAction.Ignore, tvKeyAction(Key.A, controlsShowing = true, focusInControls = false))
    }

    // Nothing to control yet, or any more: preparing, or failed.

    @Test
    fun everyKeyButBackIsIgnoredWithNothingToControl() {
        val keys = listOf(Key.DirectionCenter, Key.Enter, Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause, Key.DirectionLeft, Key.DirectionRight, Key.DirectionUp, Key.DirectionDown, Key.MediaRewind, Key.MediaFastForward)
        for (key in keys) {
            assertEquals(TvKeyAction.Ignore, tvKeyAction(key, controlsShowing = false, focusInControls = false, canControl = false), "$key")
        }
        assertEquals(TvKeyAction.Leave, tvKeyAction(Key.Back, controlsShowing = false, focusInControls = false, canControl = false))
    }
}
