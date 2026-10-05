package ui.tv.player

import androidx.compose.ui.input.key.Key
import org.junit.Test
import kotlin.test.assertEquals

/**
 * The remote key table's rows for the run: Next and Previous wherever they
 * are pressed, and Back with the up-next card on screen.
 */
class TvPlayerRunKeysTest {
    // Next and Previous are never left for the playback session to answer.

    @Test
    fun nextAndPreviousStepThroughTheRunWithAMenuOpen() {
        assertEquals(TvKeyAction.Next, tvKeyAction(Key.MediaNext, controlsShowing = true, focusInControls = false, panelOpen = true))
        assertEquals(TvKeyAction.Previous, tvKeyAction(Key.MediaPrevious, controlsShowing = true, focusInControls = false, panelOpen = true))
    }

    @Test
    fun nextAndPreviousStepThroughTheRunWithNothingToControl() {
        assertEquals(TvKeyAction.Next, tvKeyAction(Key.MediaNext, controlsShowing = false, focusInControls = false, canControl = false))
        assertEquals(TvKeyAction.Previous, tvKeyAction(Key.MediaPrevious, controlsShowing = false, focusInControls = false, canControl = false))
    }

    // The up-next card on screen.

    @Test
    fun backCancelsTheUpNextCardBeforeHidingTheControls() {
        assertEquals(TvKeyAction.CancelUpNext, tvKeyAction(Key.Back, controlsShowing = true, focusInControls = false, upNextShown = true))
    }

    @Test
    fun backClosesAMenuBeforeCancellingTheUpNextCard() {
        assertEquals(TvKeyAction.ClosePanel, tvKeyAction(Key.Back, controlsShowing = true, focusInControls = false, panelOpen = true, upNextShown = true))
    }

    @Test
    fun centrePressesTheFocusedCardButtonWhileTheCardIsUp() {
        assertEquals(TvKeyAction.PassThrough, tvKeyAction(Key.DirectionCenter, controlsShowing = true, focusInControls = false, upNextShown = true))
    }

    /** Where Previous goes is the run's own answer, shared with the phone; here it is only ever the remote's, never the session's. */
    @Test
    fun previousIsTakenWithTheNotesOpenAndTheUpNextCardUp() {
        assertEquals(TvKeyAction.Previous, tvKeyAction(Key.MediaPrevious, controlsShowing = true, focusInControls = false, upNextShown = true, notesOpen = true))
    }

    @Test
    fun backCancelsTheUpNextCardBeforePuttingTheStatisticsAway() {
        assertEquals(TvKeyAction.CancelUpNext, tvKeyAction(Key.Back, controlsShowing = true, focusInControls = false, upNextShown = true, statsShown = true))
    }
}
