package ui.tv.player

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
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
import kotlin.test.assertTrue

/**
 * The Speed and Framing menus on a title with one audio track and no
 * subtitles: each opens just above its tool, inside the controls' width,
 * with the remote on the value already chosen; keeps its keys to itself;
 * holds the controls up; and closes on a choice, or on Back — only itself,
 * onto the tool that opened it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvPlayerSettingsTest : TvPlayerScreenHarness() {
    @Test
    fun theSpeedMenuOpensAboveItsToolInsideTheControlsOnTheCurrentSpeed() {
        openMenu("Speed")

        inMenu("1×").assertIsFocused()
        inMenu("1×").assertIsSelected()
        val menu = compose.onNodeWithTag(TvCardMenuTag).getBoundsInRoot()
        val tool = compose.onNodeWithContentDescription("Speed").getBoundsInRoot()
        val card = compose.onNodeWithTag(TvBottomBandTag).getBoundsInRoot()
        assertTrue(menu.bottom <= tool.top, "the menu ends at ${menu.bottom}, its tool starts at ${tool.top}")
        assertTrue(menu.left >= card.left && menu.right <= card.right, "the menu spans ${menu.left}..${menu.right}, the controls ${card.left}..${card.right}")
    }

    @Test
    fun aSpeedAlreadyChosenIsWhereTheMenuOpens() {
        compose.runOnUiThread { controller.get().playerViewModel.setSpeed(1.5f) }
        compose.waitForIdle()
        openMenu("Speed")
        inMenu("1.5×").assertIsFocused()
        compose.runOnUiThread { controller.get().playerViewModel.setSpeed(1f) }
    }

    @Test
    fun eachChoiceIsARadioButtonWhoseMarkIsNotReadAloud() {
        openMenu("Speed")
        inMenu("1×").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        compose.onAllNodesWithText("●").assertCountEquals(0)
        compose.onAllNodesWithText("○").assertCountEquals(0)
    }

    @Test
    fun aTitleWithNoExtraTracksOffersSpeedAndFramingButNoAudio() {
        compose.onNodeWithContentDescription("Speed").assert(hasText("1×"))
        compose.onNodeWithContentDescription("Framing").assert(hasText("Fit"))
        compose.onNodeWithContentDescription("Audio").assertDoesNotExist()

        openMenu("Framing")
        for (framing in Framing.entries) inMenu(framing.label).assertExists()
        inMenu("Fit").assertIsFocused()
    }

    @Test
    fun leftAndRightInTheMenuNeitherSkipNorLeaveIt() {
        openMenu("Speed")
        press(Key.DirectionLeft)
        press(Key.DirectionRight)

        assertEquals(42_000L, fixture.positionMs)
        inMenu("1×").assertIsFocused()
    }

    @Test
    fun aSpeedChosenPlaysAtItAndClosesTheMenuOntoItsToolWhichReadsIt() {
        openMenu("Speed")
        press(Key.DirectionDown)
        press(Key.DirectionCenter)

        verify { fixture.media.setPlaybackSpeed(1.25f) }
        assertEquals(1.25f, controller.get().playerViewModel.choices.value.speed)
        compose.onNodeWithTag(TvCardMenuTag).assertDoesNotExist()
        compose.onNodeWithContentDescription("Speed").assertIsFocused()
        compose.onNodeWithContentDescription("Speed").assert(hasText("1.25×"))
    }

    @Test
    fun aFramingChosenIsTheSharedChoiceAndTheToolReadsIt() {
        openMenu("Framing")
        inMenu("Fill").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()

        assertEquals(Framing.FILL, controller.get().playerViewModel.choices.value.framing)
        compose.onNodeWithTag(TvCardMenuTag).assertDoesNotExist()
        compose.onNodeWithContentDescription("Framing").assert(hasText("Fill"))
    }

    /** Back with a menu open closes that menu and nothing else: the controls, the title and the player all stay. */
    @Test
    fun backClosesOnlyTheMenuOntoItsToolThenPutsTheControlsAwayThenLeaves() {
        openMenu("Speed")

        back()
        compose.onNodeWithTag(TvCardMenuTag).assertDoesNotExist()
        compose.onNodeWithContentDescription("Speed").assertIsFocused()
        compose.onNodeWithTag(TvSeekBarTag).assertExists()
        verify(exactly = 0) { fixture.media.stop() }

        back()
        compose.onNodeWithTag(TvSeekBarTag).assertDoesNotExist()

        back()
        verify(exactly = 1) { fixture.media.stop() }
    }

    @Test
    fun theControlsStayUpWhileAMenuIsOpen() {
        openMenu("Speed")
        compose.mainClock.advanceTimeBy(CONTROLS_LINGER_MS + 500)
        compose.waitForIdle()

        compose.onNodeWithTag(TvSeekBarTag).assertExists()
        compose.onNodeWithTag(TvCardMenuTag).assertExists()
    }

    @Test
    fun thePlayKeyStillPausesWithAMenuOpen() {
        openMenu("Speed")
        press(Key.MediaPlayPause)

        assertFalse(fixture.isPlaying)
        compose.onNodeWithTag(TvCardMenuTag).assertExists()
    }

    private fun inMenu(text: String) = compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag(TvCardMenuTag)))
}
