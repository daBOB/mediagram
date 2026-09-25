package ui.tv.player

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import io.mockk.verify
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import playback.Framing
import player.CONTROLS_LINGER_MS
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * The playback settings panel on a title with one audio track and no
 * subtitles: reached from the gear before the statistics toggle, the remote
 * on its first row, its keys its own, the controls held behind it, and Back
 * closing it before it does anything else.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvPlayerSettingsTest : TvPlayerScreenHarness() {
    @Test
    fun theGearSitsBeforeTheStatisticsToggleAndOpensThePanelOnItsFirstRow() {
        press(Key.DirectionRight)
        press(Key.DirectionRight)
        compose.onNodeWithContentDescription("Playback settings").assertIsFocused()

        press(Key.DirectionCenter)
        compose.onNodeWithTag(TvSettingsPanelTag).assertExists()
        inPanel("0.75×").assertIsFocused()
        inPanel("1×").assertIsSelected()
    }

    @Test
    fun theSectionsAreThePhoneSheetsForATitleWithNoExtraTracks() {
        openSettings()
        inPanel("Speed").assertExists()
        inPanel("Framing").assertExists()
        for (framing in Framing.entries) inPanel(framing.label).assertExists()
        compose.onNodeWithText("Audio").assertDoesNotExist()
        compose.onNodeWithText("Subtitles").assertDoesNotExist()
        compose.onNodeWithText("Subtitle style").assertDoesNotExist()
    }

    @Test
    fun leftAndRightInThePanelNeitherSkipNorLeaveIt() {
        openSettings()
        press(Key.DirectionLeft)
        press(Key.DirectionRight)

        assertEquals(42_000L, fixture.positionMs)
        inPanel("0.75×").assertIsFocused()
    }

    @Test
    fun aSpeedChosenPlaysAtItAndIsReadOutBesideTheGear() {
        openSettings()
        press(Key.DirectionDown)
        press(Key.DirectionDown)
        press(Key.DirectionCenter)

        verify { fixture.media.setPlaybackSpeed(1.25f) }
        assertEquals(1.25f, controller.get().playerViewModel.choices.value.speed)
        inPanel("1.25×").assertIsSelected()

        back()
        compose.onNodeWithText("1.25×").assertExists()
    }

    @Test
    fun atTheDefaultSpeedNothingIsReadOut() {
        compose.onNodeWithText("1×").assertDoesNotExist()
    }

    @Test
    fun aFramingChosenIsTheSharedChoice() {
        openSettings()
        inPanel("Fill").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()

        assertEquals(Framing.FILL, controller.get().playerViewModel.choices.value.framing)
        inPanel("Fill").assertIsSelected()
    }

    @Test
    fun backClosesThePanelOntoTheGearThenPutsTheControlsAwayThenLeaves() {
        openSettings()

        back()
        compose.onNodeWithTag(TvSettingsPanelTag).assertDoesNotExist()
        compose.onNodeWithContentDescription("Playback settings").assertIsFocused()
        verify(exactly = 0) { fixture.media.stop() }

        back()
        compose.onNodeWithTag(TvSeekBarTag).assertDoesNotExist()

        back()
        verify(exactly = 1) { fixture.media.stop() }
    }

    @Test
    fun theControlsStayUpWhileThePanelIsOpen() {
        openSettings()
        compose.mainClock.advanceTimeBy(CONTROLS_LINGER_MS + 500)
        compose.waitForIdle()

        compose.onNodeWithTag(TvSeekBarTag).assertExists()
        compose.onNodeWithTag(TvSettingsPanelTag).assertExists()
    }

    @Test
    fun thePlayKeyStillPausesWithThePanelOpen() {
        openSettings()
        press(Key.MediaPlayPause)

        assertFalse(fixture.isPlaying)
        compose.onNodeWithTag(TvSettingsPanelTag).assertExists()
    }

    private fun inPanel(text: String) = compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag(TvSettingsPanelTag)))
}

