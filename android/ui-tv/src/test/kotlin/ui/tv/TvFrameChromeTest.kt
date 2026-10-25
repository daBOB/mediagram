package ui.tv

import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasScrollToNodeAction
import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.hilt.lifecycle.viewmodel.HiltViewModelFactory
import catalog.CatalogTab
import catalog.CollectionKind
import catalog.Department
import catalog.Entry
import catalog.KeptKind
import catalog.Shelf
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import model.Kind
import model.MediaSet
import model.Profile
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import ui.common.FrameKind
import ui.common.RailItem
import ui.tv.catalog.TvSearchFieldTag
import ui.tv.catalog.films
import ui.tv.catalog.set
import ui.tv.chrome.RailCollapsedWidth
import ui.tv.chrome.TvContentGutter
import kotlin.test.assertEquals

/**
 * A page opened over the catalogue sits in the catalogue's own rail and bar,
 * as every web page sits in its shell: its words start where Home's do, Up
 * and Left reach the bar and the rail, and Back closes the page from any of
 * the three.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvFrameChromeTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var fixture: TvAppFixture
    private lateinit var controller: ActivityController<TvAppTestActivity>

    @Before
    fun open() {
        mockkStatic(::HiltViewModelFactory)
        every { HiltViewModelFactory(any(), any()) } answers { secondArg() }
        compose.runOnUiThread {
            fixture = TvAppFixture(TvSetupStage.READY, listOf(Profile(id = "ada", name = "Ada")), "ada", films(2) + show())
            TvAppTestActivity.fixture = fixture
            controller = Robolectric.buildActivity(TvAppTestActivity::class.java).setup().visible()
        }
        compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodes(hasText("Film 1")).fetchSemanticsNodes().isNotEmpty() }
    }

    @After
    fun close() {
        try {
            compose.runOnUiThread {
                if (::controller.isInitialized) controller.close()
                if (::fixture.isInitialized) fixture.close()
            }
        } finally {
            unmockkStatic(::HiltViewModelFactory)
        }
    }

    @Test
    fun aTitlePageStartsWhereHomeDoesUnderTheRailAndBar() {
        press(plate("Film 1"))
        compose.onNodeWithText("▶ Play").assertIsFocused()

        compose.onNode(hasContentDescription("My List") and hasClickAction()).assertExists()
        compose.onNode(hasText("Home") and hasClickAction()).assertExists()
        val left = compose.onNodeWithText("▶ Play").fetchSemanticsNode().boundsInRoot.left
        with(compose.density) { assertEquals((RailCollapsedWidth + TvContentGutter).toPx(), left, 2f) }
    }

    @Test
    fun upReachesTheBarAndBackFromItClosesThePage() {
        press(plate("Film 1"))
        key(KeyEvent.KEYCODE_DPAD_UP)
        // A film lights no pill, so Up lands on the first.
        compose.onNode(hasText("Home") and hasClickAction()).assertIsFocused()

        back()
        compose.onNodeWithText("▶ Play").assertDoesNotExist()
        plate("Film 1").assertIsFocused()
    }

    @Test
    fun leftReachesTheRailAndBackFromItClosesThePage() {
        press(plate("Film 1"))
        key(KeyEvent.KEYCODE_DPAD_LEFT)
        // Open now, so its rows read by their words.
        compose.onNode(hasText("My List") and hasClickAction()).assertIsFocused()

        back()
        compose.onNodeWithText("▶ Play").assertDoesNotExist()
        plate("Film 1").assertIsFocused()
    }

    @Test
    fun rightFromTheRailComesBackToThePage() {
        press(plate("Film 1"))
        key(KeyEvent.KEYCODE_DPAD_LEFT)
        key(KeyEvent.KEYCODE_DPAD_RIGHT)
        compose.onNodeWithText("▶ Play").assertIsFocused()
    }

    @Test
    fun aShowLightsItsDepartmentAndThatPillOpensItWithTheRemoteOnThePill() {
        press(plate("A Show"))
        val series = compose.onNode(hasText(Department.SERIES.label) and hasClickAction())
        series.assertIsSelected()

        press(series)
        compose.onNodeWithText("▶ Play S1 E1").assertDoesNotExist()
        compose.onNode(hasText(Department.SERIES.label) and hasClickAction()).assertIsFocused().assertIsSelected()
    }

    /** Movies has a row to land on, so its arrival would take the remote if the pill's press were forgotten. */
    @Test
    fun aPillOverAFilmKeepsTheRemoteOnThePill() {
        press(plate("Film 1"))
        press(compose.onNode(hasText(Department.MOVIES.label) and hasClickAction()))
        compose.onNodeWithText("▶ Play").assertDoesNotExist()
        compose.onNode(hasText(Department.MOVIES.label) and hasClickAction()).assertIsFocused().assertIsSelected()
    }

    @Test
    fun theBarsSearchOnTheSearchPagePutsTheRemoteInItsField() {
        press(compose.onNode(hasContentDescription("Search") and hasClickAction()))
        key(KeyEvent.KEYCODE_DPAD_UP)
        press(compose.onNode(hasContentDescription("Search") and hasClickAction()))
        compose.onNodeWithTag(TvSearchFieldTag).assertIsFocused()
        back()
        // One search, not a second stacked on the first.
        compose.onNodeWithTag(TvSearchFieldTag).assertDoesNotExist()
    }

    /** Nothing on the page may start left of an episode — a season chip or a tab there took Left from it. */
    @Test
    fun leftFromAnEpisodeReachesTheRailAndRightComesBackToThePage() {
        press(plate("A Show"))
        episode("1. Pilot").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.waitForIdle()
        key(KeyEvent.KEYCODE_DPAD_LEFT)
        compose.onNode(hasText("My List") and hasClickAction()).assertIsFocused()
        key(KeyEvent.KEYCODE_DPAD_RIGHT)
        // The page's first stop: a short page has it composed.
        compose.onNodeWithText("▶ Play S1 E1").assertIsFocused()
    }

    @Test
    fun aRailRowOverAPageRunsOnTheCatalogue() {
        press(plate("Film 1"))
        press(compose.onNode(hasContentDescription("Latest") and hasClickAction()))
        compose.onNodeWithText("▶ Play").assertDoesNotExist()

        back()
        // Latest opened from the catalogue's rail: Back from it lands on that row, over the shelves.
        compose.onNode(hasText("Latest") and hasClickAction()).assertIsFocused()
    }

    @Test
    fun whatAPageLightsIsTheWebsSection() {
        val show = Entry.Collection(key = "a-show", kind = CollectionKind.SHOW, name = "A Show", posterPath = null, posterKey = null, count = 0, chapters = 0, divisions = emptyList())
        val shelves = listOf(Shelf(Department.SERIES, listOf(show)))
        assertEquals(CatalogTab.Dept(Department.SERIES), tvFrameSection(FrameKind.COLLECTION, show, shelves).tab)
        assertEquals(CatalogTab.Dept(Department.MOVIES), tvFrameSection(FrameKind.MOVIES_PAGE, null, shelves).tab)
        assertEquals(CatalogTab.Kept(KeptKind.COLLECTIONS), tvFrameSection(FrameKind.LIST, null, shelves).tab)
        assertEquals(RailItem.LATEST, tvFrameSection(FrameKind.LATEST, null, shelves).rail)
        assertEquals(TvFrameSection(), tvFrameSection(FrameKind.TITLE, null, shelves))
    }

    /** An episode row, scrolled on screen first: the season cards above leave the list below a 540dp screen's fold. */
    private fun episode(name: String): SemanticsNodeInteraction {
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(name))
        return compose.onNodeWithText(name)
    }

    private fun plate(name: String) = compose.onNode(hasText(name) and hasClickAction())

    private fun press(node: SemanticsNodeInteraction) {
        node.performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    private fun key(code: Int) {
        compose.runOnUiThread { controller.get().dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code)) }
        compose.runOnUiThread { controller.get().dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code)) }
        compose.waitForIdle()
    }

    private fun back() {
        compose.runOnUiThread { controller.get().onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    private fun show(): List<MediaSet> =
        listOf(
            set("pilot", Kind.EPISODE, "Pilot", show = "A Show", addedAt = 10, episode = 1).copy(season = 1),
            set("return", Kind.EPISODE, "Return", show = "A Show", addedAt = 11, episode = 1).copy(season = 2),
        )
}
