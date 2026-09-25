package ui.tv.player

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import player.PlayerUiState
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * [TvPlayerScreen] once its title has failed: the phone's message and
 * Retry, with the remote already on Retry, Centre pressing it, and every
 * key that would move a film doing nothing to one that is not there.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvPlayerRetryTest : TvPlayerScreenHarness() {
    private fun failNow() {
        compose.runOnUiThread { fixture.fail() }
        compose.waitForIdle()
        assertIs<PlayerUiState.Failed>(controller.get().playerViewModel.state.value)
    }

    @Test
    fun aFailureShowsItsMessageWithTheRemoteOnRetry() {
        failNow()
        compose.onNodeWithText("Playback failed").assertExists()
        compose.onNodeWithText("Retry").assertIsFocused()
        compose.onNodeWithTag(TvSeekBarTag).assertDoesNotExist()
    }

    @Test
    fun centreOnRetryOpensTheTitleAgain() {
        failNow()
        press(Key.DirectionCenter)
        assertEquals(PlayerUiState.Playing, controller.get().playerViewModel.state.value)
        compose.onNodeWithText("Retry").assertDoesNotExist()
    }

    @Test
    fun theTransportKeysDoNothingToAFailedTitle() {
        failNow()
        val before = fixture.positionMs
        for (key in listOf(Key.DirectionLeft, Key.DirectionRight, Key.MediaRewind, Key.MediaFastForward, Key.MediaPlayPause, Key.DirectionUp, Key.DirectionDown)) {
            press(key)
        }
        assertEquals(before, fixture.positionMs)
        assertIs<PlayerUiState.Failed>(controller.get().playerViewModel.state.value)
        compose.onNodeWithTag(TvSeekBarTag).assertDoesNotExist()
        compose.onNodeWithText("Retry").assertIsFocused()
    }

    @Test
    fun backStillLeavesAFailedTitle() {
        failNow()
        back()
        compose.onNodeWithText("Library").assertExists()
    }

    @Test
    fun aBackKeyOnRetryLeavesAFailedTitle() {
        failNow()
        compose.onNodeWithText("Retry").assertIsFocused()
        pressBackKey()
        compose.onNodeWithText("Library").assertExists()
    }
}
