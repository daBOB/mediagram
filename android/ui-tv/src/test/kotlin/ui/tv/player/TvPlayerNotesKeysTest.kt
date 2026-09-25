package ui.tv.player

import androidx.compose.ui.input.key.Key
import kotlin.test.Test
import kotlin.test.assertEquals

/** The key table's rows for the notes column beside the picture. */
class TvPlayerNotesKeysTest {
    @Test
    fun backClosesTheNotesBeforeTheControlsGoOrTheTitleIsLeft() {
        assertEquals(TvKeyAction.CloseNotes, tvKeyAction(Key.Back, controlsShowing = true, focusInControls = false, notesOpen = true))
        assertEquals(TvKeyAction.CloseNotes, tvKeyAction(Key.Back, controlsShowing = false, focusInControls = false, notesOpen = true))
        assertEquals(TvKeyAction.CloseNotes, tvKeyAction(Key.Back, controlsShowing = false, focusInControls = false, canControl = false, notesOpen = true))
    }

    @Test
    fun theSettingsPanelAndTheUpNextCardStillAnswerBackFirst() {
        assertEquals(TvKeyAction.ClosePanel, tvKeyAction(Key.Back, controlsShowing = true, focusInControls = false, panelOpen = true, notesOpen = true))
        assertEquals(TvKeyAction.CancelUpNext, tvKeyAction(Key.Back, controlsShowing = true, focusInControls = false, upNextShown = true, notesOpen = true))
    }

    @Test
    fun withTheControlsAwayUpAndDownPageTheNotesInsteadOfRaisingTheSeekBar() {
        assertEquals(TvKeyAction.PassThrough, tvKeyAction(Key.DirectionUp, controlsShowing = false, focusInControls = false, notesOpen = true))
        assertEquals(TvKeyAction.PassThrough, tvKeyAction(Key.DirectionDown, controlsShowing = false, focusInControls = false, notesOpen = true))
    }

    @Test
    fun withTheNotesOpenTheOtherKeysStillSkipAndPause() {
        assertEquals(TvKeyAction.SeekByAndShowControls(-10), tvKeyAction(Key.DirectionLeft, controlsShowing = false, focusInControls = false, notesOpen = true))
        assertEquals(TvKeyAction.SeekByAndShowControls(10), tvKeyAction(Key.DirectionRight, controlsShowing = false, focusInControls = false, notesOpen = true))
        assertEquals(TvKeyAction.TogglePlayAndShowControls, tvKeyAction(Key.DirectionCenter, controlsShowing = false, focusInControls = false, notesOpen = true))
        assertEquals(TvKeyAction.TogglePlay, tvKeyAction(Key.MediaPlayPause, controlsShowing = false, focusInControls = false, notesOpen = true))
    }
}
