package ui.tv.player

import android.view.KeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import data.CatalogRepository
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import model.Kind
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ui.tv.catalog.set
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
    override val run = listOf("set-zero", "set-one", "set-two")

    override fun makeFixture(): TvPlayerFixture {
        val catalog = mockk<CatalogRepository>(relaxed = true)
        coEvery { catalog.mediaSet("set-zero") } returns set("set-zero", Kind.EPISODE, "Before", show = "A Show", addedAt = 1, episode = 3, durationSecs = 600)
        coEvery { catalog.mediaSet("set-one") } returns set("set-one", Kind.EPISODE, "Pilot", show = "A Show", addedAt = 1, episode = 4, durationSecs = 600)
        coEvery { catalog.mediaSet("set-two") } returns set("set-two", Kind.EPISODE, "After", show = "A Show", addedAt = 1, episode = 5, durationSecs = 600)
        return TvPlayerFixture(catalog = catalog)
    }

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
        // Play/pause, skip forward, Play next, and then the gear.
        press(Key.DirectionRight)
        press(Key.DirectionRight)
        press(Key.DirectionRight)
        press(Key.DirectionCenter)
        compose.onNodeWithTag(TvSettingsPanelTag).assertExists()

        nearTheEnd()

        compose.onNodeWithTag(TvUpNextCardTag).assertExists()
        compose.onNodeWithText("Play now").assertIsNotFocused()

        back()

        compose.onNodeWithTag(TvSettingsPanelTag).assertDoesNotExist()
        compose.onNodeWithText("Play now").assertIsFocused()
    }

    /** Into the last half-minute, as a seek there lands: the card's own cue. */
    private fun nearTheEnd() {
        compose.runOnUiThread {
            fixture.positionMs = 590_000L
            controller.get().playerViewModel.onSeeked()
        }
        compose.waitForIdle()
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
