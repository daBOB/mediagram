package ui.tv.catalog

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithText
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
 * The three kept tabs as [TvCatalogScreen] draws them: what each wall holds,
 * where the remote lands, and the phone's own words when there is nothing
 * to hold. The empty texts are spelled out here, not read back from
 * [KeptKind], so a change to the shared wording shows up as a change to
 * what both surfaces say rather than passing silently.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvKeptWallStateTest : TvScreenStateTest() {
    @Test
    fun eachEmptyKeptTabSaysThePhonesWords() {
        val empty =
            mapOf(
                "Continue" to "Nothing started yet.",
                "Watchlist" to "Nothing on the list.",
                "Collections" to "No lists yet.",
            )
        for ((tab, text) in empty) {
            showCatalog(ready(films(1)))
            compose.onNodeWithText(tab).performSemanticsAction(SemanticsActions.OnClick)
            compose.onNodeWithText(text).assertExists()
            close()
        }
    }

    @Test
    fun continueHoldsWhatWasStartedCaptionedWithWhereItStoppedAndFocusesIt() {
        val stopped = Progress(setId = "film-1", at = 1_200.0, duration = 6_000.0, updatedAt = 2)
        var opened: String? = null
        showCatalog(withWatch(films(3), WatchSnapshot.Empty.copy(progress = listOf(stopped))), onOpenTitle = { opened = it })

        compose.onNodeWithText("Continue").performSemanticsAction(SemanticsActions.OnClick)

        compose.onNodeWithText("Continue · 1").assertExists()
        compose.onNodeWithText(resumeLine(stopped)).assertExists()
        compose.onNodeWithText("Film 1").assertIsFocused()
        compose.onNodeWithText("Film 1").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals("film-1", opened)
    }

    @Test
    fun watchlistHoldsWhatWasListedNewestFirstAndFocusesTheFirst() {
        showCatalog(withWatch(films(3), WatchSnapshot.Empty.copy(watchlist = listOf("film-2", "film-0"))))

        compose.onNodeWithText("Watchlist").performSemanticsAction(SemanticsActions.OnClick)

        compose.onNodeWithText("Watchlist · 2").assertExists()
        compose.onNodeWithText("Film 2").assertIsFocused()
        compose.onNodeWithText("Film 1").assertDoesNotExist()
    }

    @Test
    fun collectionsListsTheViewersListsAndFocusesTheFirst() {
        var opened: String? = null
        val lists = listOf(ListOfSets("a", "Sunday", listOf("film-0")), ListOfSets("b", "Later", emptyList()))
        showCatalog(withWatch(films(1), WatchSnapshot.Empty.copy(collections = lists)), onOpenList = { opened = it })

        compose.onNodeWithText("Collections").performSemanticsAction(SemanticsActions.OnClick)

        compose.onNodeWithText("Sunday · 1 title").assertIsFocused()
        compose.onNodeWithText("Later · 0 titles").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals("b", opened)
    }

    /** The last title taken off the Watchlist from its own page leaves no plate; the remote goes up to the masthead. */
    @Test
    fun aKeptWallEmptiedUnderTheViewerSendsTheRemoteToTheMasthead() {
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
        // Walked to and pressed, as a remote does: the masthead then
        // remembers Watchlist as the tab the remote was last on.
        compose.onNodeWithText("Watchlist").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithText("Watchlist").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("Film 0").assertIsFocused()

        compose.runOnUiThread { state.value = withWatch(sets, WatchSnapshot.Empty) }
        compose.waitForIdle()

        compose.onNodeWithText("Nothing on the list.").assertExists()
        compose.onNodeWithText("Watchlist").assertIsFocused()
    }

    private fun withWatch(
        sets: List<MediaSet>,
        watch: WatchSnapshot,
    ) = CatalogUiState.Ready(shelvesOf(sets), watch = watch)

    private fun showCatalog(
        state: CatalogUiState,
        onOpenTitle: (String) -> Unit = {},
        onOpenList: (String) -> Unit = {},
        restoreKey: String? = null,
    ) = show {
        TvCatalogScreen(
            state = state,
            profile = TvChosenProfile(name = "Ada", onChoose = {}),
            onOpenTitle = onOpenTitle,
            onOpenCollection = {},
            onOpenList = onOpenList,
            onCreateList = {},
            restoreKey = restoreKey,
        )
    }
}
