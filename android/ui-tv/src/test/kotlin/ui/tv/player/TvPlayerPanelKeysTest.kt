package ui.tv.player

import androidx.compose.ui.input.key.Key
import org.junit.Test
import kotlin.test.assertEquals

/**
 * The remote key table while the settings panel is open: Back closes it
 * before anything else, the D-pad and Centre belong to its rows — never a
 * skip, even from a focus the table would otherwise read as the seek bar —
 * and the dedicated media keys keep the meaning they have everywhere.
 */
class TvPlayerPanelKeysTest {
    @Test
    fun backClosesThePanelBeforeTheControlsOrThePlayer() {
        assertEquals(TvKeyAction.ClosePanel, panel(Key.Back))
        assertEquals(TvKeyAction.ClosePanel, panel(Key.Back, controlsShowing = false))
        assertEquals(TvKeyAction.ClosePanel, panel(Key.Back, canControl = false))
    }

    @Test
    fun theDpadAndCentreMoveAndChooseInsideThePanel() {
        val keys = listOf(Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight, Key.DirectionCenter, Key.Enter)
        for (key in keys) {
            assertEquals(TvKeyAction.PassThrough, panel(key), "$key")
            assertEquals(TvKeyAction.PassThrough, panel(key, focusInControls = true), "$key on a stale seek-bar focus")
            assertEquals(TvKeyAction.PassThrough, panel(key, controlsShowing = false), "$key with the controls away")
        }
    }

    @Test
    fun theMediaKeysKeepTheirMeaningWithThePanelOpen() {
        assertEquals(TvKeyAction.TogglePlay, panel(Key.MediaPlayPause))
        assertEquals(TvKeyAction.Play, panel(Key.MediaPlay))
        assertEquals(TvKeyAction.Pause, panel(Key.MediaPause))
        assertEquals(TvKeyAction.SeekBy(-10), panel(Key.MediaRewind))
        assertEquals(TvKeyAction.SeekBy(10), panel(Key.MediaFastForward))
    }

    @Test
    fun withNothingToControlOnlyTheDpadAndBackDoAnything() {
        assertEquals(TvKeyAction.Ignore, panel(Key.MediaPlayPause, canControl = false))
        assertEquals(TvKeyAction.PassThrough, panel(Key.DirectionDown, canControl = false))
    }

    @Test
    fun anUnmappedKeyIsStillIgnored() {
        assertEquals(TvKeyAction.Ignore, panel(Key.A))
    }

    private fun panel(
        key: Key,
        controlsShowing: Boolean = true,
        focusInControls: Boolean = false,
        canControl: Boolean = true,
    ) = tvKeyAction(key, controlsShowing, focusInControls, canControl, panelOpen = true)
}
