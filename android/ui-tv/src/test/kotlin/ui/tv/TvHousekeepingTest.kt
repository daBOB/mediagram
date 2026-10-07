package ui.tv

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.hilt.lifecycle.viewmodel.HiltViewModelFactory
import data.WatchStateRepository
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
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
import kotlin.test.assertFalse
import ui.tv.catalog.films

/**
 * The offline badge, Continue's "Mark finished" and Back on the picker,
 * walked through the whole app over the real `CatalogViewModel` and
 * `ProfileViewModel` [TvAppFixture] builds — so what is proved is that each
 * reaches the model and comes back as the screen the viewer sees.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvHousekeepingTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var fixture: TvAppFixture
    private lateinit var controller: ActivityController<TvAppTestActivity>

    @Before
    fun stubHilt() {
        mockkStatic(::HiltViewModelFactory)
        every { HiltViewModelFactory(any(), any()) } answers { secondArg() }
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
    fun aHeldTitleSaysOfflineOnContinueAndItsShelfButNotOnLatest() {
        launch(watch = started("film-0"), heldIds = setOf("film-0"), films = 2)

        // Home: once on Continue's card, never on the Latest row beside it.
        compose.onAllNodes(hasText("Film 0") and hasText("offline")).assertCountEquals(1)
        compose.onAllNodes(hasText("Film 1") and hasText("offline")).assertCountEquals(0)

        press(compose.onNodeWithText("Movies"))
        // The Movies department front page can carry the same film under more
        // than one heading (Featured and Recently added both, with only two
        // films in the shelf) — the web's own `renderMoviesDept` draws the
        // same way, so this checks the badge is offered at all rather than
        // exactly once.
        compose.onAllNodes(hasText("Film 0") and hasText("offline")).fetchSemanticsNodes().let { assert(it.isNotEmpty()) { "Film 0 never says offline" } }
        compose.onAllNodes(hasText("Film 1") and hasText("offline")).assertCountEquals(0)
    }

    @Test
    fun markFinishedTakesTheTitleOffContinueAndTheRemoteToTheOneBesideIt() {
        launch(watch = started("film-0", "film-1"), films = 2)
        openContinueWatching()
        compose.onNodeWithText("Continue · 2").assertExists()
        val first = focusedPlateTitle()

        press(compose.onAllNodesWithText("Mark finished")[0])

        compose.onNodeWithText("Continue · 1").assertExists()
        val other = if (first == "Film 0") "Film 1" else "Film 0"
        compose.onNode(hasText(other) and hasClickAction()).assertIsFocused()
        compose.onAllNodes(hasText(first) and hasClickAction()).assertCountEquals(0)
    }

    /**
     * Continue has no pill of its own — the rail chooses it directly — so
     * the remote it lands on instead is the rail's own Continue
     * row, which is where a wall that empties under the viewer always
     * falls back to (see [ui.tv.catalog.TvKeptWall]'s own doc).
     */
    @Test
    fun markingTheLastTitleFinishedEmptiesContinueAndLandsOnItsRailRow() {
        launch(watch = started("film-0"), films = 1)
        openContinueWatching()

        press(compose.onNodeWithText("Mark finished"))

        compose.onNodeWithText("Nothing started yet.").assertExists()
        // The rail is open now that the remote actually landed on it, so
        // its row reads by its visible label rather than by the
        // content description a collapsed row falls back to.
        compose.onNodeWithText("Continue").assertIsFocused()
    }

    /** Continue is a rail row now, not a masthead tab — see `mastheadTabsOf`. */
    private fun openContinueWatching() {
        press(compose.onNodeWithContentDescription("Continue"))
    }

    /** Back on the reopened picker is "Stay as I am", as on the phone — not a way out of the app. */
    @Test
    fun backOnTheReopenedPickerStaysAsTheViewer() {
        launch(profiles = listOf(Profile("ada", "Ada"), Profile("bo", "Bo")), chosen = "ada", films = 1)
        press(compose.onNodeWithContentDescription("Who's watching: Ada"))
        compose.onNodeWithText("Stay as I am").assertExists()

        compose.runOnUiThread { controller.get().onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()

        compose.onAllNodesWithText("Who's watching?").assertCountEquals(0)
        compose.onNodeWithText("Film 0").assertExists()
        assertFalse(controller.get().isFinishing, "Back left the app")
    }

    private fun launch(
        profiles: List<Profile> = listOf(Profile("ada", "Ada")),
        chosen: String? = "ada",
        films: Int = 0,
        watch: suspend WatchStateRepository.() -> Unit = {},
        heldIds: Set<String> = emptySet(),
    ) {
        compose.runOnUiThread {
            fixture = TvAppFixture(TvSetupStage.READY, profiles, chosen, films(films), watch, heldIds)
            TvAppTestActivity.fixture = fixture
            controller = Robolectric.buildActivity(TvAppTestActivity::class.java).setup().visible()
        }
        val ready = if (chosen == null) "Who's watching?" else "Film 0"
        compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodes(hasText(ready)).fetchSemanticsNodes().isNotEmpty() }
    }

    /** Each title ten minutes in, the first started longest ago. */
    private fun started(vararg ids: String): suspend WatchStateRepository.() -> Unit =
        { ids.forEach { setProgress(it, 600.0, 6_000.0) } }

    /** Which of the two films' plates the remote is on. */
    private fun focusedPlateTitle(): String =
        listOf("Film 0", "Film 1").first { title ->
            compose.onAllNodes(hasText(title) and hasClickAction() and isFocused())
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

    private fun press(node: SemanticsNodeInteraction) {
        node.performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }
}
