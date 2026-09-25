package ui.tv.player

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import android.os.Looper
import io.mockk.verify
import model.ListOfSets
import model.Profile
import model.WatchSnapshot
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The run and the up-next card meeting whatever else the viewer has open:
 * the list dialog — a window of its own, whose media keys must still reach
 * the film — the seek bar, and an unattended switch still waiting to start.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvPlayerRunOverlaysTest : TvPlayerScreenHarness() {
    override val run = THREE_TITLE_RUN

    override fun makeFixture() =
        runFixture(
            snapshot = WatchSnapshot.Empty.copy(collections = listOf(ListOfSets("fav", "Favourites", emptyList()))),
            profile = Profile("p1", "andre"),
        )

    /** Previous from inside the dialog is the player's step back, not the session's restart. */
    @Test
    fun mediaPreviousInTheListDialogStepsBackThroughTheRun() {
        openAddToList()

        pressInDialog(Key.MediaPrevious)

        assertEquals(listOf("set-zero"), TvPlayerTestActivity.switches)
        verify(exactly = 0) { fixture.media.seekToPrevious() }
        verify(exactly = 0) { fixture.media.seekTo(0L) }
    }

    @Test
    fun mediaNextInTheListDialogStepsForward() {
        openAddToList()

        pressInDialog(Key.MediaNext)

        assertEquals(listOf("set-two"), TvPlayerTestActivity.switches)
    }

    /** The other media keys keep their meaning in the dialog, as they do over the settings panel. */
    @Test
    fun theOtherMediaKeysReachTheFilmThroughTheListDialog() {
        openAddToList()

        pressInDialog(Key.MediaPlayPause)
        assertFalse(fixture.isPlaying)
        pressInDialog(Key.MediaPlayPause)
        assertTrue(fixture.isPlaying)
        pressInDialog(Key.MediaRewind)
        assertEquals(32_000L, fixture.positionMs)
        // The dialog is still open, and its own row still holds the remote.
        compose.onNodeWithText("☐ Favourites").assertIsFocused()
    }

    @Test
    fun theCardDoesNotTakeTheRemoteFromTheListDialog() {
        openAddToList()

        nearTheEnd()

        compose.onNodeWithTag(TvUpNextCardTag).assertExists()
        compose.onNodeWithText("☐ Favourites").assertIsFocused()
        compose.onNodeWithText("Play now").assertIsNotFocused()
    }

    @Test
    fun theCardDoesNotTakeTheRemoteFromTheSeekBar() {
        press(Key.DirectionUp)
        compose.onNodeWithTag(TvSeekBarTag).assertIsFocused()

        nearTheEnd()

        compose.onNodeWithTag(TvUpNextCardTag).assertExists()
        compose.onNodeWithTag(TvSeekBarTag).assertIsFocused()
        // One press up, it is there.
        press(Key.DirectionUp)
        compose.onNodeWithText("Play now").assertIsFocused()
    }

    /**
     * The countdown ran out and the next title is paused on the autoplay
     * gate; Previous goes back to where it came from, and that title plays —
     * a key press is a viewer at the screen, not one to wait for.
     */
    @Test
    fun previousWhileTheNextTitleWaitsToStartGoesBackAndPlays() {
        nearTheEnd()
        compose.runOnUiThread { controller.get().playerViewModel.onEnded() }
        idleFor(Duration.ofSeconds(11))
        assertEquals(listOf("set-two"), TvPlayerTestActivity.switches)
        assertTrue(controller.get().playerViewModel.upNext.value.awaitingStart)
        assertFalse(fixture.isPlaying)

        keyDown(android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS, repeat = 0)
        keyUp(android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS)

        assertEquals(listOf("set-two", "set-one"), TvPlayerTestActivity.switches)
        assertFalse(controller.get().playerViewModel.upNext.value.awaitingStart)
        assertTrue(fixture.isPlaying)
    }

    /** Down to the marks rail, across to Add to list, and pressed. */
    private fun openAddToList() {
        press(Key.DirectionDown)
        press(Key.DirectionRight)
        press(Key.DirectionRight)
        compose.onNodeWithText("Add to list").assertIsFocused()
        press(Key.DirectionCenter)
        compose.onNodeWithText("☐ Favourites").assertIsFocused()
    }

    /** A key to the dialog's own window, whose focus is separate from the player's underneath it. */
    private fun pressInDialog(key: Key) {
        compose.onNode(isFocused() and hasAnyAncestor(isDialog())).performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    private fun idleFor(time: Duration) {
        compose.runOnUiThread { shadowOf(Looper.getMainLooper()).idleFor(time) }
        compose.waitForIdle()
    }
}
