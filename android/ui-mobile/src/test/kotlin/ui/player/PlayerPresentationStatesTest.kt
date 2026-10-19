package ui.player

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.media3.common.Player
import io.mockk.every
import io.mockk.verify
import model.ReportWords
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import player.retry
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame

/**
 * The presenting player through what else happens to a title and a screen:
 * opening and failing are said on the phone, the remote in the hand; a
 * recreation for a configuration change is not the viewer leaving; and a
 * presentation the system takes down is put back while the TV is there.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w800dp-h600dp")
class PlayerPresentationStatesTest : PlayerPresentationBase() {
    @Test
    fun whilePresentingAFailureShowsRetryOnThePhone() {
        open()
        connectTv()

        compose.runOnUiThread { controller.get().playerViewModel.onError("Telegram isn't reachable") }
        compose.waitForIdle()

        compose.onNodeWithText("Telegram isn't reachable").assertExists()
        compose.onNodeWithText(ReportWords.BUTTON).assertExists()
        compose.onNode(hasContentDescription(label)).assertExists()
        // As on the player's own look, nothing is left for the card to drive.
        compose.onNodeWithTag(PlayerCardTag).assertDoesNotExist()

        compose.onNodeWithText("Retry").performClick()
        compose.onNodeWithTag(PlayerCardTag).assertExists()
    }

    @Test
    fun whilePresentingOpeningShowsTheLeaderOnThePhone() {
        open()
        connectTv()
        // Still buffering: the player's own ready event has not come yet.
        every { fixture.media.playbackState } returns Player.STATE_BUFFERING

        compose.runOnUiThread { controller.get().playerViewModel.retry() }
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Loading").assertExists()
        compose.onNode(hasContentDescription(label)).assertExists()
    }

    @Test
    fun aConfigurationChangeWhilePresentingDoesNotPause() {
        open()
        connectTv()

        compose.runOnUiThread { controller.recreate() }
        idle()

        verify(exactly = 0) { fixture.media.pause() }
        assertNotNull(presentation(), "the new activity puts the picture back on the TV")
    }

    @Test
    fun aPresentationTheSystemTakesDownIsShownAgain() {
        open()
        connectTv()
        val first = assertNotNull(presentation())

        // What a presentation does to itself when its display's metrics change;
        // Robolectric's display manager does not get it that far, so it is called here.
        compose.runOnUiThread { first.cancel() }
        idle()

        assertFalse(first.isShowing)
        assertNotSame(first, assertNotNull(presentation(), "the picture is back on the TV"))
    }
}
