package ui.tv

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.hilt.lifecycle.viewmodel.HiltViewModelFactory
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
import ui.tv.catalog.films
import ui.tv.catalog.set

/**
 * [TvLibrary] walked the way a remote walks it, over the real
 * `CatalogViewModel` [TvAppFixture] builds: open, go deeper, press Back,
 * and land where the walk came from — on the very plate or row that was
 * pressed, which is the part only a television has to get right.
 *
 * Home's first row is "Latest films", so the remote starts on a film, not
 * on the show; landing back on the show's plate proves Back restored it
 * rather than simply refocusing Home's first stop.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvLibraryTest {
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
    fun backFromAnEpisodeWalksBackToTheHomePlateThatOpenedTheShow() {
        compose.onNodeWithText("Film 1").assertIsFocused()

        press(plate("A Show"))
        compose.onNodeWithText("Season 1").assertIsFocused()
        press(compose.onNodeWithText("Season 1"))
        compose.onNodeWithText("1. Pilot").assertIsFocused()
        press(compose.onNodeWithText("1. Pilot"))
        compose.onNodeWithText("▶ Play").assertIsFocused()

        back()
        compose.onNodeWithText("1. Pilot").assertIsFocused()
        back()
        compose.onNodeWithText("Season 1").assertIsFocused()
        back()
        plate("A Show").assertIsFocused()
    }

    @Test
    fun aCatalogRefreshMidWalkKeepsThePositionAndTheWayBack() {
        press(plate("A Show"))
        press(compose.onNodeWithText("Season 2"))
        compose.onNodeWithText("1. Return").assertIsFocused()

        compose.runOnUiThread { fixture.catalog.reload() }
        compose.waitForIdle()

        compose.onNodeWithText("1. Return").assertExists()
        back()
        compose.onNodeWithText("Season 2").assertIsFocused()
        back()
        plate("A Show").assertIsFocused()
    }

    @Test
    fun playShowsTheStandInAndBackReturnsToTheTitle() {
        press(plate("Film 1"))
        press(compose.onNodeWithText("▶ Play"))
        compose.onNodeWithText("“Film 1” will play here soon.").assertExists()

        back()
        compose.onNodeWithText("▶ Play").assertIsFocused()
        back()
        plate("Film 1").assertIsFocused()
    }

    /**
     * The remembered plate belongs to the tab it was opened from: Film 1
     * is Home's first plate but the second on Movies, where the wall's own
     * first plate is where a viewer arriving at the tab should land.
     */
    @Test
    fun choosingAnotherTabLandsOnItsFirstPlateNotOneOpenedElsewhere() {
        press(plate("Film 1"))
        back()
        plate("Film 1").assertIsFocused()

        compose.onNodeWithText("Movies").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()

        plate("Film 0").assertIsFocused()
    }

    @Test
    fun backAtTheCatalogRootGoesUpToTheMastheadFirst() {
        compose.onNodeWithText("Film 1").assertIsFocused()

        back()

        compose.onNodeWithText("Home").assertIsFocused()
        compose.runOnUiThread {
            val dispatcher = controller.get().onBackPressedDispatcher
            kotlin.test.assertFalse(dispatcher.hasEnabledCallbacks(), "a second Back is left to close the app")
        }
    }

    private fun plate(name: String) = compose.onNode(hasText(name) and hasClickAction())

    private fun press(node: SemanticsNodeInteraction) {
        node.performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    private fun back() {
        compose.runOnUiThread { controller.get().onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    /** One show of two seasons, one episode each — enough for a season wall rather than a flat list. */
    private fun show(): List<MediaSet> =
        listOf(
            set("pilot", Kind.EPISODE, "Pilot", show = "A Show", addedAt = 10, episode = 1).copy(season = 1),
            set("return", Kind.EPISODE, "Return", show = "A Show", addedAt = 11, episode = 1).copy(season = 2),
        )
}
