package ui.tv.player

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertTrue

/**
 * The up-next card as the stage floats it: between the title along the top
 * and the controls along the bottom, never inside either; reached by Up
 * from the seek bar; to the left of the settings panel while that is open;
 * and put away by a Back key as a remote sends it, as the panel is.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvPlayerOverlayBandsTest : TvPlayerScreenHarness() {
    override val run = THREE_TITLE_RUN

    override fun makeFixture() = runFixture()

    @Test
    fun theCardFloatsBetweenTheTitleAndTheControls() {
        nearTheEnd()
        val card = compose.onNodeWithTag(TvUpNextCardTag).getBoundsInRoot()
        val bottom = compose.onNodeWithTag(TvBottomBandTag).getBoundsInRoot()
        val top = compose.onNodeWithTag(TvTopBandTag).getBoundsInRoot()
        assertTrue(card.bottom <= bottom.top, "card ends at ${card.bottom}, the controls start at ${bottom.top}")
        assertTrue(card.top >= top.bottom, "card starts at ${card.top}, the title ends at ${top.bottom}")
    }

    @Test
    fun upFromTheSeekBarReachesTheCard() {
        nearTheEnd()
        compose.onNodeWithText("Play now").assertIsFocused()
        press(Key.DirectionDown)
        compose.onNodeWithTag(TvSeekBarTag).assertIsFocused()
        press(Key.DirectionUp)
        compose.onNodeWithText("Play now").assertIsFocused()
    }

    @Test
    fun aBackKeyPutsTheCardAwayAndLeavesTheControlsUp() {
        nearTheEnd()
        pressBackKey()
        compose.onNodeWithTag(TvUpNextCardTag).assertDoesNotExist()
        compose.onNodeWithTag(TvSeekBarTag).assertExists()
    }

    @Test
    fun withThePanelOpenTheCountdownStandsBesideIt() {
        openSettings()
        nearTheEnd()
        val panel = compose.onNodeWithTag(TvSettingsPanelTag).getBoundsInRoot()
        val card = compose.onNodeWithTag(TvUpNextCardTag).getBoundsInRoot()
        assertTrue(card.right <= panel.left, "card ends at ${card.right}, the panel starts at ${panel.left}")
        compose.onNodeWithText("When this ends").assertIsDisplayed()
    }

    @Test
    fun aBackKeyClosesThePanelOntoTheGear() {
        openSettings()
        compose.onNodeWithTag(TvSettingsPanelTag).assertExists()
        pressBackKey()
        compose.onNodeWithTag(TvSettingsPanelTag).assertDoesNotExist()
        compose.onNodeWithContentDescription("Playback settings").assertIsFocused()
    }
}
