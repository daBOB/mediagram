package ui.tv.player

import android.view.KeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import io.mockk.verify
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import player.PlayerUiState
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The remote's keys where a single tap is not the whole story: a key held
 * down, the dedicated Play and Pause keys, and keys pressed while there is
 * no film to control.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvPlayerHeldKeysTest : TvPlayerScreenHarness() {
    @Test
    fun aHeldRightSkipsTenAtATimeThenFasterOnTheSeekBar() {
        back()

        keyDown(KeyEvent.KEYCODE_DPAD_RIGHT, repeat = 0)
        assertEquals(52_000L, fixture.positionMs)
        compose.onNodeWithTag(TvSeekBarTag).assertIsFocused()

        for (repeat in 1 until 20) keyDown(KeyEvent.KEYCODE_DPAD_RIGHT, repeat)
        assertEquals(242_000L, fixture.positionMs)

        keyDown(KeyEvent.KEYCODE_DPAD_RIGHT, repeat = 20)
        assertEquals(272_000L, fixture.positionMs)
        keyDown(KeyEvent.KEYCODE_DPAD_RIGHT, repeat = 21)
        assertEquals(302_000L, fixture.positionMs)

        keyUp(KeyEvent.KEYCODE_DPAD_RIGHT)
        compose.onNodeWithTag(TvSeekBarTag).assertIsFocused()
    }

    @Test
    fun aHeldCentreThatBroughtTheControlsUpNeverPressesWhatItLandedOn() {
        back()

        keyDown(KeyEvent.KEYCODE_DPAD_CENTER, repeat = 0)
        assertFalse(fixture.isPlaying)
        compose.onNodeWithContentDescription("Play").assertIsFocused()

        keyDown(KeyEvent.KEYCODE_DPAD_CENTER, repeat = 1)
        keyDown(KeyEvent.KEYCODE_DPAD_CENTER, repeat = 2)
        keyUp(KeyEvent.KEYCODE_DPAD_CENTER)

        assertFalse(fixture.isPlaying)
        compose.onNodeWithContentDescription("Play").assertIsFocused()
    }

    @Test
    fun theRestOfATakenPressIsTakenEvenWhereTheTableWouldPassItOn() {
        val remote = TvPlayerRemote {}
        fun send(event: KeyEvent) =
            remote.onKey(androidx.compose.ui.input.key.KeyEvent(event), null, controlsShowing = true, onSeekBar = false, canControl = true)

        assertTrue(remote.onKey(androidx.compose.ui.input.key.KeyEvent(centre(repeat = 0)), null, controlsShowing = false, onSeekBar = false, canControl = true))
        // The controls are up now, where the table passes Centre to the focused button.
        assertTrue(send(centre(repeat = 1)))
        assertTrue(send(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER)))
        // A new press is the button's again.
        assertFalse(send(centre(repeat = 0)))
    }

    @Test
    fun playOnlyPlaysAndPauseOnlyPauses() {
        press(Key.MediaPlay)
        assertTrue(fixture.isPlaying)

        press(Key.MediaPause)
        assertFalse(fixture.isPlaying)
        press(Key.MediaPause)
        assertFalse(fixture.isPlaying)

        press(Key.MediaPlay)
        assertTrue(fixture.isPlaying)
    }

    @Test
    fun withNothingToControlTheTransportKeysDoNothingAndBackStillLeaves() {
        compose.runOnUiThread { controller.get().playerViewModel.onError("Could not play this title.") }
        compose.waitForIdle()
        assertEquals(PlayerUiState.Failed("Could not play this title."), controller.get().playerViewModel.state.value)
        compose.onNodeWithTag(TvSeekBarTag).assertDoesNotExist()

        for (key in listOf(Key.DirectionCenter, Key.DirectionRight, Key.DirectionLeft, Key.DirectionUp, Key.MediaPlayPause, Key.MediaPause)) {
            press(key)
        }

        assertEquals(42_000L, fixture.positionMs)
        verify(exactly = 0) { fixture.media.pause() }
        compose.onNodeWithTag(TvSeekBarTag).assertDoesNotExist()

        back()
        compose.onNodeWithText("Library").assertExists()
    }

    private fun centre(repeat: Int) = KeyEvent(0L, 0L, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, repeat)
}
