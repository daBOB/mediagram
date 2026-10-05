package ui.player

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import data.CatalogRepository
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import player.CONTROLS_LINGER_MS
import player.PlayerUiState
import player.UNKNOWN_TITLE
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A playing title behind the real screen, for the tests that drive the card as a viewer would. */
abstract class PlayerCardScreenBase {
    @get:Rule val compose = createEmptyComposeRule()
    internal lateinit var fixture: PlayerLifecycleFixture
    internal lateinit var controller: ActivityController<PlayerTestActivity>

    internal fun open(run: List<String> = emptyList()) {
        compose.runOnUiThread {
            // Nothing in the catalogue: a run's rows list as unknown titles, which is all these need.
            val catalog = mockk<CatalogRepository>(relaxed = true)
            coEvery { catalog.sets() } returns emptyList()
            fixture = PlayerLifecycleFixture(catalog = catalog)
            PlayerTestActivity.fixture = fixture
            PlayerTestActivity.run = run
            PlayerTestActivity.switches.clear()
            controller = Robolectric.buildActivity(PlayerTestActivity::class.java).setup().visible()
        }
        compose.waitForIdle()
        assertEquals(PlayerUiState.Playing, controller.get().playerViewModel.state.value)
    }

    @After
    fun close() {
        compose.runOnUiThread {
            if (::controller.isInitialized) controller.close()
            if (::fixture.isInitialized) fixture.close()
            PlayerTestActivity.run = emptyList()
        }
    }

    /** Back through the dispatcher, as the system's own Back gesture delivers it. */
    internal fun back() {
        compose.runOnUiThread { controller.get().onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    internal fun lingerPast() {
        compose.mainClock.advanceTimeBy(CONTROLS_LINGER_MS + 500)
        compose.waitForIdle()
    }
}

/**
 * The card over a playing title, as the screen wires it: Back closes what
 * the card opened before it leaves anything, the card stays up while a menu
 * or the sidebar is open and only then, a menu opens above its button, and
 * ⏮ ⏭ ☰ follow the run. A tablet's width, so the sidebar stands beside the
 * card rather than over it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1280dp-h800dp")
class PlayerCardScreenTest : PlayerCardScreenBase() {
    @Test
    fun backWithAMenuOpenClosesOnlyTheMenu() {
        open()
        compose.onNodeWithContentDescription("Speed").performClick()
        compose.onNodeWithTag(CardMenuTag).assertExists()

        back()

        compose.onNodeWithTag(CardMenuTag).assertDoesNotExist()
        compose.onNodeWithTag(PlayerCardTag).assertExists()
        compose.onNodeWithText("Library").assertDoesNotExist()
        verify(exactly = 0) { fixture.media.stop() }
    }

    @Test
    fun backClosesTheMenuThenTheSidebar() {
        open(run = listOf("set-one", "set-two"))
        compose.onNodeWithContentDescription("Episodes").performClick()
        compose.onNodeWithContentDescription("Speed").performClick()

        back()
        compose.onNodeWithTag(CardMenuTag).assertDoesNotExist()
        compose.onNodeWithTag(EpisodeSidebarTag).assertExists()

        back()
        compose.onNodeWithTag(EpisodeSidebarTag).assertDoesNotExist()
        compose.onNodeWithTag(PlayerCardTag).assertExists()
        verify(exactly = 0) { fixture.media.stop() }
    }

    @Test
    fun aMenuOpensAboveItsButtonInsideTheCard() {
        open()
        compose.onNodeWithContentDescription("Speed").performClick()

        val menu = compose.onNodeWithTag(CardMenuTag).getBoundsInRoot()
        val button = compose.onNodeWithContentDescription("Speed").getBoundsInRoot()
        val card = compose.onNodeWithTag(PlayerCardTag).getBoundsInRoot()
        assertTrue(menu.bottom <= button.top, "the menu sits above the button that opened it")
        assertTrue(menu.left >= card.left && menu.right <= card.right, "and inside the card's width")
    }

    @Test
    fun aTapOnThePictureClosesTheMenuNotTheCard() {
        open()
        compose.onNodeWithContentDescription("Speed").performClick()

        compose.onNodeWithContentDescription("Speed").performClick()
        compose.onNodeWithTag(CardMenuTag).assertDoesNotExist()
        compose.onNodeWithTag(PlayerCardTag).assertExists()
    }

    @Test
    fun anOpenMenuKeepsTheCardUpWhilePlaying() {
        open()
        compose.onNodeWithContentDescription("Speed").performClick()

        lingerPast()

        compose.onNodeWithTag(PlayerCardTag).assertExists()
        compose.onNodeWithTag(CardMenuTag).assertExists()
    }

    @Test
    fun anOpenSidebarKeepsTheCardUpWhilePlaying() {
        open(run = listOf("set-one", "set-two"))
        compose.onNodeWithContentDescription("Episodes").performClick()

        lingerPast()

        compose.onNodeWithTag(PlayerCardTag).assertExists()
        compose.onNodeWithTag(EpisodeSidebarTag).assertExists()
    }

    @Test
    fun withNothingOpenTheCardTakesItselfAwayWhilePlaying() {
        open()

        lingerPast()

        compose.onNodeWithTag(PlayerCardTag).assertDoesNotExist()
    }

    @Test
    fun aFilmHasNoStepsAndNoEpisodes() {
        open()

        compose.onNodeWithContentDescription("Restart").assertExists()
        compose.onNodeWithContentDescription("Previous").assertDoesNotExist()
        compose.onNodeWithContentDescription("Next").assertDoesNotExist()
        compose.onNodeWithContentDescription("Episodes").assertDoesNotExist()
    }

    @Test
    fun theFirstTitleOfARunOffersNextButNotPrevious() {
        open(run = listOf("set-one", "set-two"))

        compose.onNodeWithContentDescription("Previous").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Next").assertIsEnabled()
        compose.onNodeWithContentDescription("Episodes").assertExists()
    }

    @Test
    fun aRowPickedInTheSidebarSwitchesToItAndClosesTheSidebar() {
        open(run = listOf("set-one", "set-two"))
        compose.onNodeWithContentDescription("Episodes").performClick()

        compose.onNode(hasText(UNKNOWN_TITLE) and hasClickAction()).performClick()

        // The switch reaches the screen through a lifecycle-aware flow, a frame behind the click.
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        assertEquals(listOf("set-two"), PlayerTestActivity.switches)
        compose.onNodeWithTag(EpisodeSidebarTag).assertDoesNotExist()
    }

    @Test
    fun theMarksRideInTheTopBarAboveTheCard() {
        open()

        val marks = compose.onNodeWithText("Add to list").getBoundsInRoot()
        val card = compose.onNodeWithTag(PlayerCardTag).getBoundsInRoot()
        assertTrue(marks.bottom < card.top)
    }
}
