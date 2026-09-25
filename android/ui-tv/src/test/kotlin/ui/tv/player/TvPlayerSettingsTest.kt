package ui.tv.player

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onAllNodesWithText
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
import player.setSpeed
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
    fun theGearSitsBeforeTheStatisticsToggleAndOpensThePanelOnTheCurrentSpeed() {
        toTool(hasContentDescription("Playback settings"))
        press(Key.DirectionRight)
        compose.onNodeWithContentDescription("Show playback statistics").assertIsFocused()
        press(Key.DirectionLeft)

        press(Key.DirectionCenter)
        compose.onNodeWithTag(TvSettingsPanelTag).assertExists()
        inPanel("1×").assertIsFocused()
        inPanel("1×").assertIsSelected()
    }

    @Test
    fun aSpeedAlreadyChosenIsWhereThePanelOpens() {
        compose.runOnUiThread { controller.get().playerViewModel.setSpeed(1.5f) }
        compose.waitForIdle()
        openSettings()
        inPanel("1.5×").assertIsFocused()
        compose.runOnUiThread { controller.get().playerViewModel.setSpeed(1f) }
    }

    @Test
    fun eachChoiceIsARadioButtonWhoseMarkIsNotReadAloud() {
        openSettings()
        inPanel("1×").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        compose.onAllNodesWithText("●").assertCountEquals(0)
        compose.onAllNodesWithText("○").assertCountEquals(0)
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
        inPanel("1×").assertIsFocused()
    }

    @Test
    fun aSpeedChosenPlaysAtItAndIsReadOutBesideTheGear() {
        openSettings()
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

