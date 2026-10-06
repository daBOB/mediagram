package ui.tv.player

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.height
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertTrue

/**
 * The up-next card as the stage floats it: between the title along the top
 * and the controls along the bottom, never inside either; reached by Up
 * from the seek bar; to the left of the episode list while that is open;
 * and put away by a Back key as a remote sends it, as a menu is.
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
    fun withTheEpisodesOpenTheCountdownStandsBesideThem() {
        compose.onNodeWithContentDescription("Episodes").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        nearTheEnd()
        val list = compose.onNodeWithTag(TvEpisodeSidebarTag).getBoundsInRoot()
        val card = compose.onNodeWithTag(TvUpNextCardTag).getBoundsInRoot()
        assertTrue(card.right <= list.left, "card ends at ${card.right}, the list starts at ${list.left}")
        compose.onNodeWithText("When this ends").assertIsDisplayed()
    }

    /** Above the whole card, as on the web — never down over the seek row — and under the title. */
    @Test
    fun aMenuOpensBetweenTheTitleAndTheCard() {
        openMenu("Speed")
        val menu = compose.onNodeWithTag(TvCardMenuTag).getBoundsInRoot()
        val bottom = compose.onNodeWithTag(TvBottomBandTag).getBoundsInRoot()
        val top = compose.onNodeWithTag(TvTopBandTag).getBoundsInRoot()
        assertTrue(menu.bottom <= bottom.top, "menu ends at ${menu.bottom}, the card starts at ${bottom.top}")
        assertTrue(menu.top >= top.bottom, "menu starts at ${menu.top}, the title ends at ${top.bottom}")
    }

    /** Its rows alone, as on the web: the tool beneath already names it. */
    @Test
    fun aMenuHasNoHeading() {
        openMenu("Speed")
        compose.onAllNodesWithText("Speed").assertCountEquals(0)
    }

    /** The statistics are a reading, not a place: a menu may cover them, so they leave it its full room. */
    @Test
    fun theStatisticsDoNotShortenAMenu() {
        openMenu("Speed")
        val bare = compose.onNodeWithTag(TvCardMenuTag).getBoundsInRoot().height
        pressBackKey()
        compose.onNodeWithContentDescription("Stats").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithContentDescription("Speed").assertIsFocused()
        press(Key.DirectionCenter)
        val withStats = compose.onNodeWithTag(TvCardMenuTag).getBoundsInRoot().height
        assertTrue(withStats >= bare, "menu is $withStats tall with the statistics on, $bare without")
    }

    @Test
    fun withTheEpisodesOpenTheTitleEndsBesideThem() {
        compose.onNodeWithContentDescription("Episodes").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        val list = compose.onNodeWithTag(TvEpisodeSidebarTag).getBoundsInRoot()
        val top = compose.onNodeWithTag(TvTopBandTag).getBoundsInRoot()
        assertTrue(top.right <= list.left, "title ends at ${top.right}, the list starts at ${list.left}")
    }

    /** No room beside the list for the title and the marks: the marks stay, and the title comes back with the room. */
    @Test
    fun withTheEpisodesOpenTheTitleMakesRoomForTheMarks() {
        compose.onNodeWithTag(TvPlayerTitleTag).assertExists()
        compose.onNodeWithContentDescription("Episodes").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        compose.onNodeWithTag(TvPlayerTitleTag).assertDoesNotExist()
        compose.onNodeWithText("My List").assertExists()
        pressBackKey()
        compose.onNodeWithTag(TvPlayerTitleTag).assertExists()
    }

    /** No room for both beside the list: the statistics wait for it to close, and come back on their own. */
    @Test
    fun withTheEpisodesOpenTheStatisticsWait() {
        compose.onNodeWithContentDescription("Stats").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithContentDescription("Episodes").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        compose.onNodeWithTag(TvStatsOverlayTag).assertDoesNotExist()
        pressBackKey()
        compose.onNodeWithTag(TvEpisodeSidebarTag).assertDoesNotExist()
        compose.onNodeWithTag(TvStatsOverlayTag).assertExists()
    }

    /** Back as a remote sends it — through focus first — closes the menu and only the menu. */
    @Test
    fun aBackKeyClosesAMenuOntoItsTool() {
        openMenu("Speed")
        compose.onNodeWithTag(TvCardMenuTag).assertExists()
        pressBackKey()
        compose.onNodeWithTag(TvCardMenuTag).assertDoesNotExist()
        compose.onNodeWithContentDescription("Speed").assertIsFocused()
        compose.onNodeWithTag(TvSeekBarTag).assertExists()
    }
}
