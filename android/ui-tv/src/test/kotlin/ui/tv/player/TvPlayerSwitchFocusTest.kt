package ui.tv.player

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertFalse

private fun audioGroup(language: String, selected: Boolean) =
    Tracks.Group(
        TrackGroup(Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC).setLanguage(language).setChannelCount(2).build()),
        false,
        intArrayOf(C.FORMAT_HANDLED),
        booleanArrayOf(selected),
    )

/**
 * A title switch after a menu: the next title is prepared for a while, so
 * the controls go and come back, and the remote must come back on
 * play/pause — never on the tool the last menu was opened from, which may
 * not exist for the new title.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvPlayerSwitchFocusTest : TvPlayerScreenHarness() {
    override val run = THREE_TITLE_RUN

    override fun makeFixture() = runFixture()

    private fun playPauseFocused() = compose.onNode(isFocused() and (hasContentDescription("Pause") or hasContentDescription("Play")))

    private fun nextAndWaitForTheNewTitle() {
        compose.runOnUiThread { fixture.holdPrepare = true }
        toTransport(hasContentDescription("Next"))
        press(Key.DirectionCenter)
        compose.runOnUiThread { fixture.becomeReady() }
        compose.waitForIdle()
        compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodesWithContentDescription("Pause").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun afterAMenuANextTitleOpensWithTheRemoteOnPlayPause() {
        openMenu("Speed")
        back()
        compose.onNodeWithContentDescription("Speed").assertIsFocused()

        press(Key.DirectionDown)
        nextAndWaitForTheNewTitle()
        playPauseFocused().assertExists()
    }

    @Test
    fun afterAnAudioMenuTheRemoteIsOnPlayPauseWhenTheNewTitleHasNoAudioTool() {
        compose.runOnUiThread { fixture.reportTracks(Tracks(listOf(audioGroup("en", true), audioGroup("de", false)))) }
        compose.waitUntil(timeoutMillis = 5_000) { controller.get().playerViewModel.choices.value.audioOptions.isNotEmpty() }
        openMenu("Audio")
        back()
        compose.onNodeWithContentDescription("Audio").assertIsFocused()

        press(Key.DirectionDown)
        nextAndWaitForTheNewTitle()

        compose.onNodeWithContentDescription("Audio").assertDoesNotExist()
        playPauseFocused().assertExists()
        press(Key.DirectionCenter)
        assertFalse(fixture.isPlaying)
    }

    @Test
    fun anOpenMenuIsClosedByASwitch() {
        compose.runOnUiThread { fixture.holdPrepare = true }
        openMenu("Speed")
        compose.runOnUiThread { controller.get().playerViewModel.playNext() }
        compose.runOnUiThread { fixture.becomeReady() }
        compose.waitForIdle()

        compose.onNodeWithTag(TvCardMenuTag).assertDoesNotExist()
    }
}
