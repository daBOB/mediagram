package ui.tv

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.hilt.lifecycle.viewmodel.HiltViewModelFactory
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import model.Profile
import model.Progress
import model.WatchSnapshot
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
import ui.tv.setup.TvConfirmDialogCancelTag
import ui.tv.setup.TvConfirmDialogConfirmTag

/**
 * The offline badge, Continue's "Mark finished" and "Remove a profile…",
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
        compose.onNode(hasText("Film 0") and hasText("offline")).assertExists()
        compose.onAllNodes(hasText("Film 1") and hasText("offline")).assertCountEquals(0)
    }

    @Test
    fun markFinishedTakesTheTitleOffContinueAndTheRemoteToTheOneBesideIt() {
        launch(watch = started("film-0", "film-1"), films = 2)
        press(compose.onNodeWithText("Continue"))
        compose.onNodeWithText("Continue · 2").assertExists()
        val first = focusedPlateTitle()

        press(compose.onAllNodesWithText("Mark finished")[0])

        compose.onNodeWithText("Continue · 1").assertExists()
        val other = if (first == "Film 0") "Film 1" else "Film 0"
        compose.onNode(hasText(other) and hasClickAction()).assertIsFocused()
        compose.onAllNodes(hasText(first) and hasClickAction()).assertCountEquals(0)
    }

    @Test
    fun markingTheLastTitleFinishedEmptiesContinueAndLandsOnItsTab() {
        launch(watch = started("film-0"), films = 1)
        compose.onNodeWithText("Continue").performSemanticsAction(SemanticsActions.RequestFocus)
        press(compose.onNodeWithText("Continue"))

        press(compose.onNodeWithText("Mark finished"))

        compose.onNodeWithText("Nothing started yet.").assertExists()
        compose.onNodeWithText("Continue").assertIsFocused()
    }

    @Test
    fun removingAProfileAsksWhichThenWhetherWithCancelFirst() {
        launch(profiles = listOf(Profile("ada", "Ada"), Profile("bo", "Bo")), chosen = null)
        press(compose.onNodeWithText("Remove a profile…"))

        compose.onNodeWithText("Remove which profile?").assertExists()
        compose.onNodeWithText("Everything of theirs goes with it.").assertExists()
        nameInDialog("Ada").assertIsFocused()
        press(nameInDialog("Bo"))

        compose.onNodeWithText("Remove \"Bo\" and everything they have watched?").assertExists()
        compose.onNodeWithTag(TvConfirmDialogCancelTag).assertIsFocused()
        press(compose.onNodeWithTag(TvConfirmDialogConfirmTag))

        compose.onAllNodesWithText("Bo").assertCountEquals(0)
        compose.onNodeWithText("Ada").assertExists()
    }

    @Test
    fun cancellingTheQuestionRemovesNobody() {
        launch(profiles = listOf(Profile("ada", "Ada"), Profile("bo", "Bo")), chosen = null)
        press(compose.onNodeWithText("Remove a profile…"))
        press(nameInDialog("Bo"))

        press(compose.onNodeWithTag(TvConfirmDialogCancelTag))

        compose.onAllNodesWithText("Remove which profile?").assertCountEquals(0)
        compose.onNodeWithText("Bo").assertExists()
    }

    /** The one being watched as can go too, as on the phone — and "Stay as I am" goes with it. */
    @Test
    fun removingTheProfileBeingWatchedStopsOfferingToStayAsIt() {
        launch(profiles = listOf(Profile("ada", "Ada"), Profile("bo", "Bo")), chosen = "ada", films = 1)
        // The masthead's last entry is the viewer's name, and reopens the picker.
        press(compose.onNode(hasText("Ada") and hasClickAction()))
        compose.onNodeWithText("Stay as I am").assertExists()

        press(compose.onNodeWithText("Remove a profile…"))
        press(nameInDialog("Ada"))
        press(compose.onNodeWithTag(TvConfirmDialogConfirmTag))

        compose.onAllNodesWithText("Ada").assertCountEquals(0)
        compose.onAllNodesWithText("Stay as I am").assertCountEquals(0)
    }

    /** With the last profile gone, "Remove a profile…" goes too, and the remote lands on New profile. */
    @Test
    fun removingTheLastProfileLandsOnNewProfile() {
        launch(profiles = listOf(Profile("ada", "Ada")), chosen = null)
        press(compose.onNodeWithText("Remove a profile…"))
        press(nameInDialog("Ada"))
        press(compose.onNodeWithTag(TvConfirmDialogConfirmTag))

        compose.onAllNodesWithText("Remove a profile…").assertCountEquals(0)
        compose.onNode(hasText("New profile") and hasClickAction()).assertIsFocused()
    }

    private fun launch(
        profiles: List<Profile> = listOf(Profile("ada", "Ada")),
        chosen: String? = "ada",
        films: Int = 0,
        watch: WatchSnapshot = WatchSnapshot.Empty,
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

    private fun started(vararg ids: String) =
        WatchSnapshot.Empty.copy(
            progress = ids.mapIndexed { index, id -> Progress(setId = id, at = 600.0, duration = 6_000.0, updatedAt = index + 1L) },
        )

    /** Which of the two films' plates the remote is on. */
    private fun focusedPlateTitle(): String =
        listOf("Film 0", "Film 1").first { title ->
            compose.onAllNodes(hasText(title) and hasClickAction() and isFocused())
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

    /** A name in the dialog's list, not the picker's tile of the same name behind it. */
    private fun nameInDialog(name: String) = compose.onNode(hasText(name) and hasClickAction() and hasAnyAncestor(isDialog()))

    private fun press(node: SemanticsNodeInteraction) {
        node.performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }
}
