package ui.tv.player

import android.view.KeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import io.mockk.verify
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The player opened on the middle title of a three-title run: the remote's
 * Next and Previous step through it and are never left for the playback
 * session, and the up-next card near the end puts the remote on Play now —
 * except over the settings panel, which keeps it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvPlayerUpNextTest : TvPlayerScreenHarness() {
    override val run = THREE_TITLE_RUN

    override fun makeFixture() = runFixture()

    @Test
    fun mediaNextMovesToTheNextTitleOfTheRun() {
        assertTrue(dispatch(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT))
        assertTrue(dispatch(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_NEXT))

        assertEquals(listOf("set-two"), TvPlayerTestActivity.switches)
    }

    /**
     * Taken, press and release both, so the window's fallback never hands
     * it on to the playback session — whose own Previous is a restart of
     * this title, the second answer this press used to get.
     */
    @Test
    fun mediaPreviousMovesBackThroughTheRunAndIsNotLeftForTheSession() {
        assertTrue(dispatch(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS))
        assertTrue(dispatch(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PREVIOUS))

        assertEquals(listOf("set-zero"), TvPlayerTestActivity.switches)
        verify(exactly = 0) { fixture.media.seekToPrevious() }
        verify(exactly = 0) { fixture.media.seekTo(0L) }
    }

    @Test
    fun aHeldNextIsOneStepNotMany() {
        keyDown(KeyEvent.KEYCODE_MEDIA_NEXT, repeat = 0)
        keyDown(KeyEvent.KEYCODE_MEDIA_NEXT, repeat = 1)
        keyDown(KeyEvent.KEYCODE_MEDIA_NEXT, repeat = 2)
        keyUp(KeyEvent.KEYCODE_MEDIA_NEXT)

        assertEquals(listOf("set-two"), TvPlayerTestActivity.switches)
    }

    @Test
    fun theCardComesUpNearTheEndWithTheRemoteOnPlayNow() {
        nearTheEnd()

        compose.onNodeWithTag(TvUpNextCardTag).assertExists()
        compose.onNodeWithText("After", substring = true).assertExists()
        compose.onNodeWithText("Play now").assertIsFocused()
    }

    @Test
    fun playNowStartsTheNextTitle() {
        nearTheEnd()

        press(Key.DirectionCenter)

        assertEquals(listOf("set-two"), TvPlayerTestActivity.switches)
    }

    @Test
    fun backCancelsTheCardAndLeavesTheControlsUp() {
        nearTheEnd()

        back()

        compose.onNodeWithTag(TvUpNextCardTag).assertDoesNotExist()
        compose.onNodeWithTag(TvSeekBarTag).assertExists()
        assertEquals(emptyList(), TvPlayerTestActivity.switches)
        // The standing button stays after a cancel, as on the phone.
        compose.onNodeWithContentDescription("Play next", substring = true).assertExists()
    }

    @Test
    fun theCardDoesNotTakeTheRemoteFromTheSettingsPanel() {
        openSettings()
        compose.onNodeWithTag(TvSettingsPanelTag).assertExists()

        nearTheEnd()

        compose.onNodeWithTag(TvUpNextCardTag).assertExists()
        compose.onNodeWithText("Play now").assertIsNotFocused()

        back()

        compose.onNodeWithTag(TvSettingsPanelTag).assertDoesNotExist()
        compose.onNodeWithText("Play now").assertIsFocused()
    }

    private fun dispatch(
        action: Int,
        keyCode: Int,
    ): Boolean {
        var handled = false
        compose.runOnUiThread { handled = controller.get().dispatchKeyEvent(KeyEvent(action, keyCode)) }
        compose.waitForIdle()
        return handled
    }
}
