package ui.tv.player

import androidx.compose.ui.input.key.Key
import org.junit.Test
import kotlin.test.assertEquals

/**
 * The remote key table's rows for the run: Next and Previous wherever they
 * are pressed, and Back with the up-next card on screen — and where
 * Previous goes in the run.
 */
class TvPlayerRunKeysTest {
    // Next and Previous are never left for the playback session to answer.

    @Test
    fun nextAndPreviousStepThroughTheRunWithTheSettingsPanelOpen() {
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
    fun backClosesTheSettingsPanelBeforeCancellingTheUpNextCard() {
        assertEquals(TvKeyAction.ClosePanel, tvKeyAction(Key.Back, controlsShowing = true, focusInControls = false, panelOpen = true, upNextShown = true))
    }

    @Test
    fun centrePressesTheFocusedCardButtonWhileTheCardIsUp() {
        assertEquals(TvKeyAction.PassThrough, tvKeyAction(Key.DirectionCenter, controlsShowing = true, focusInControls = false, upNextShown = true))
    }

    @Test
    fun previousIsTheTitleBeforeInTheRun() {
        assertEquals("a", previousInRun(listOf("a", "b", "c"), "b"))
    }

    @Test
    fun thereIsNothingBeforeTheFirstTitleOrOneTheRunDoesNotHold() {
        assertEquals(null, previousInRun(listOf("a", "b"), "a"))
        assertEquals(null, previousInRun(listOf("a", "b"), "z"))
        assertEquals(null, previousInRun(emptyList(), "a"))
    }
}
