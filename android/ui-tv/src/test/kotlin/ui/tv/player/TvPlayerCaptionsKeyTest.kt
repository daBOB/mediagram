package ui.tv.player

import androidx.compose.ui.input.key.Key
import org.junit.Test
import kotlin.test.assertEquals

/** The remote's captions key toggles subtitles in every state the player can be in, as Next does. */
class TvPlayerCaptionsKeyTest {
    private fun press(
        controlsShowing: Boolean = false,
        focusInControls: Boolean = false,
        canControl: Boolean = true,
        panelOpen: Boolean = false,
        upNextShown: Boolean = false,
        notesOpen: Boolean = false,
    ) = tvKeyAction(Key.Captions, controlsShowing, focusInControls, canControl, panelOpen, upNextShown, notesOpen)

    @Test
    fun withTheControlsHidden() = assertEquals(TvKeyAction.ToggleSubtitles, press())

    @Test
    fun withTheControlsUp() = assertEquals(TvKeyAction.ToggleSubtitles, press(controlsShowing = true))

    @Test
    fun withFocusOnTheSeekBar() = assertEquals(TvKeyAction.ToggleSubtitles, press(controlsShowing = true, focusInControls = true))

    @Test
    fun withTheSettingsPanelOpen() = assertEquals(TvKeyAction.ToggleSubtitles, press(controlsShowing = true, panelOpen = true))

    @Test
    fun withTheNotesOpen() = assertEquals(TvKeyAction.ToggleSubtitles, press(notesOpen = true))

    @Test
    fun withTheUpNextCardShown() = assertEquals(TvKeyAction.ToggleSubtitles, press(controlsShowing = true, upNextShown = true))

    @Test
    fun whileNothingCanBeControlled() = assertEquals(TvKeyAction.ToggleSubtitles, press(canControl = false))
}
