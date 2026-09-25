package ui.tv.player

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import model.Kind
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import player.CONTROLS_LINGER_MS
import kotlin.test.assertTrue

/**
 * [TvPlayerScreen] on a lesson, whose notes open by themselves as on the
 * phone — without pulling the remote off the controls it opened on.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvPlayerLessonNotesTest : TvPlayerScreenHarness() {
    override fun makeFixture() = notesFixture(Kind.TUTORIAL)

    @Test
    fun aLessonsNotesOpenBesideItWithTheRemoteLeftOnPlayPause() {
        compose.onNodeWithTag(TvNotesTag).assertExists()
        compose.onNodeWithContentDescription("Pause").assertIsFocused()
    }

    @Test
    fun onceTheControlsGoTheNotesTakeTheRemote() {
        compose.mainClock.advanceTimeBy(CONTROLS_LINGER_MS + 500)
        compose.waitForIdle()
        compose.onNodeWithTag(TvNotesTag).assertIsFocused()
    }

    @Test
    fun backClosesTheNotesBeforeThePlayerCanBeLeft() {
        compose.mainClock.advanceTimeBy(CONTROLS_LINGER_MS + 500)
        compose.waitForIdle()
        back()
        compose.onNodeWithTag(TvNotesTag).assertDoesNotExist()
        compose.onNodeWithTag(TvPlayerScreenTag).assertIsFocused()
    }

    @Test
    fun oneBackKeyClosesTheNotesWhileTheyHoldTheRemote() {
        compose.mainClock.advanceTimeBy(CONTROLS_LINGER_MS + 500)
        compose.waitForIdle()
        compose.onNodeWithTag(TvNotesTag).assertIsFocused()
        pressBackKey()
        compose.onNodeWithTag(TvNotesTag).assertDoesNotExist()
        compose.onNodeWithTag(TvPlayerScreenTag).assertIsFocused()
        pressBackKey()
        compose.onNodeWithText("Library").assertExists()
    }

    /** The narrowest the stage gets: every control still has width, inside it, and the remote reaches each. */
    @Test
    fun withTheNotesOpenEveryControlFitsBesideThemAndIsReached() {
        val notesLeft = compose.onNodeWithTag(TvNotesTag).getBoundsInRoot().left
        val controls =
            listOf("Skip back 10 seconds", "Pause", "Skip forward 10 seconds", "Playback settings", "Show playback statistics")
                .map { hasContentDescription(it) } + (hasText("Notes") and hasClickAction())
        for (control in controls) {
            val bounds = compose.onNode(control).getBoundsInRoot()
            assertTrue(bounds.width > 0.dp && bounds.right <= notesLeft, "$control spans ${bounds.left}..${bounds.right}, the notes start at $notesLeft")
        }
        press(Key.DirectionRight)
        compose.onNodeWithContentDescription("Skip forward 10 seconds").assertIsFocused()
        press(Key.DirectionLeft)
        toTool(hasText("Notes") and hasClickAction())
        press(Key.DirectionRight)
        compose.onNodeWithContentDescription("Playback settings").assertIsFocused()
        press(Key.DirectionRight)
        compose.onNodeWithContentDescription("Show playback statistics").assertIsFocused()
    }

    @Test
    fun aBackKeyInTheNotesOfAFailedTitleClosesThemOntoRetry() {
        compose.runOnUiThread { fixture.fail() }
        compose.waitForIdle()
        compose.onNodeWithText("Retry").assertIsFocused()
        press(Key.DirectionRight)
        compose.onNodeWithTag(TvNotesTag).assertIsFocused()
        pressBackKey()
        compose.onNodeWithTag(TvNotesTag).assertDoesNotExist()
        compose.onNodeWithText("Retry").assertIsFocused()
    }
}
