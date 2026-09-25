package ui.tv.player

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import model.Kind
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import player.CONTROLS_LINGER_MS

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
