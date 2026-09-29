package ui.tv.catalog

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import catalog.CatalogUiState
import catalog.KeptKind
import catalog.resumeLine
import catalog.shelvesOf
import model.ListOfSets
import model.MediaSet
import model.Progress
import model.WatchSnapshot
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ui.tv.profile.TvChosenProfile
import kotlin.test.assertEquals

/**
 * The two kept walls the rail chooses directly, plus Collections' own pill,
 * as [TvCatalogScreen] draws them: what each wall holds, where the remote
 * lands, and the phone's own words when there is nothing to hold. The empty
 * texts are spelled out here, not read back from [KeptKind], so a change to
 * the shared wording shows up as a change to what both surfaces say rather
 * than passing silently.
 *
 * My List and Continue watching have no pill of their own — pressing their
 * rail row (found by its content description, since the rail draws icons
 * alone until the remote reaches it) is how these tests reach them, the
 * same as a real remote pressing OK on either row; `TvMenuTest` covers the
 * rail's own other four rows. Collections stays a pill, so it is still
 * reached the way it always was.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvKeptWallStateTest : TvScreenStateTest() {
    @Test
    fun eachEmptyKeptTabSaysThePhonesWords() {
        showCatalog(ready(films(1)))
        pressRailRow("Continue watching")
        compose.onNodeWithText("Nothing started yet.").assertExists()
        close()

        showCatalog(ready(films(1)))
        pressRailRow("My List")
        compose.onNodeWithText("Nothing on the list.").assertExists()
        close()

        showCatalog(ready(films(1)))
        compose.onNodeWithText("Collections").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("No lists yet.").assertExists()
    }

    @Test
    fun continueHoldsWhatWasStartedCaptionedWithWhereItStoppedAndFocusesIt() {
        val stopped = Progress(setId = "film-1", at = 1_200.0, duration = 6_000.0, updatedAt = 2)
        var opened: String? = null
        showCatalog(withWatch(films(3), WatchSnapshot.Empty.copy(progress = listOf(stopped))), onOpenTitle = { opened = it })
        pressRailRow("Continue watching")

        compose.onNodeWithText("Continue · 1").assertExists()
        compose.onNodeWithText(resumeLine(stopped)).assertExists()
        compose.onNodeWithText("Film 1").assertIsFocused()
        compose.onNodeWithText("Film 1").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals("film-1", opened)
    }

    @Test
    fun watchlistHoldsWhatWasListedNewestFirstAndFocusesTheFirst() {
        showCatalog(withWatch(films(3), WatchSnapshot.Empty.copy(watchlist = listOf("film-2", "film-0"))))
        pressRailRow("My List")

        compose.onNodeWithText("Watchlist · 2").assertExists()
        compose.onNodeWithText("Film 2").assertIsFocused()
        compose.onNodeWithText("Film 1").assertDoesNotExist()
    }

    /** The pill-press rule applies here too: the remote stays on Collections itself, listing the viewer's lists underneath it rather than jumping straight to one. */
    @Test
    fun collectionsListsTheViewersListsWhileKeepingTheRemoteOnThePill() {
        var opened: String? = null
        val lists = listOf(ListOfSets("a", "Sunday", listOf("film-0")), ListOfSets("b", "Later", emptyList()))
        showCatalog(withWatch(films(1), WatchSnapshot.Empty.copy(collections = lists)), onOpenList = { opened = it })

        compose.onNodeWithText("Collections").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithText("Collections").performSemanticsAction(SemanticsActions.OnClick)

        compose.onNodeWithText("Collections").assertIsFocused()
        compose.onNodeWithText("Sunday · 1 title").assertExists()
        // Collections' own hero (`TvDepartmentHero`'s fixed 360dp) leaves
        // this list little of the 540dp screen — a real remote's Down
        // scrolls it same as any lazy list; this test does the same before
        // reaching a row two allotted screens' worth of scrolling away.
        compose.onNode(hasTestTag(TvListsTestTag)).performScrollToIndex(1)
        compose.onNodeWithText("Later · 0 titles").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals("b", opened)
    }

    /**
     * The last title taken off the Watchlist from its own page leaves no
     * plate; the remote goes up to the rail's own My List row, since
     * neither kept wall has a pill of its own to return to (see the class
     * doc).
     */
    @Test
    fun aKeptWallEmptiedUnderTheViewerSendsTheRemoteToItsRailRow() {
        val sets = films(2)
        val state = mutableStateOf(withWatch(sets, WatchSnapshot.Empty.copy(watchlist = listOf("film-0"))))
        show {
            TvCatalogScreen(
                state = state.value,
                profile = TvChosenProfile(name = "Ada", onChoose = {}),
                onOpenTitle = {},
                onOpenCollection = {},
                onOpenList = {},
                onCreateList = {},
            )
        }
        pressRailRow("My List")
        compose.onNodeWithText("Film 0").assertIsFocused()

        compose.runOnUiThread { state.value = withWatch(sets, WatchSnapshot.Empty) }
        compose.waitForIdle()

        compose.onNodeWithText("Nothing on the list.").assertExists()
        // The rail is open now that the remote actually landed on it, so
        // its row reads by its visible label rather than by the content
        // description a collapsed row falls back to.
        compose.onNodeWithText("My List").assertIsFocused()
    }

    private fun pressRailRow(label: String) {
        compose.onNodeWithContentDescription(label).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    private fun withWatch(
        sets: List<MediaSet>,
        watch: WatchSnapshot,
    ) = CatalogUiState.Ready(shelvesOf(sets), watch = watch)

    private fun showCatalog(
        state: CatalogUiState,
        onOpenTitle: (String) -> Unit = {},
        onOpenList: (String) -> Unit = {},
    ) = show {
        TvCatalogScreen(
            state = state,
            profile = TvChosenProfile(name = "Ada", onChoose = {}),
            onOpenTitle = onOpenTitle,
            onOpenCollection = {},
            onOpenList = onOpenList,
            onCreateList = {},
        )
    }
}
