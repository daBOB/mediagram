package ui.tv.catalog

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import catalog.CollectionKind
import catalog.DocumentaryLibrary
import catalog.Division
import catalog.Entry
import catalog.documentariesDepartmentOf
import model.Kind
import model.MediaSet
import model.WatchSnapshot
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * [TvDocumentariesDepartmentPage] over real [documentariesDepartmentOf]
 * output — the category rows and folder links this shelf's own plain wall
 * never drew (`docs/web-player.md`'s recorded difference, removed with this
 * page).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1920dp-h1080dp")
class TvDocumentariesDepartmentPageStateTest : TvScreenStateTest() {
    private fun doc(
        id: String,
        category: String? = null,
        addedAt: Long = 0,
    ) = set(id, Kind.DOCUMENTARY, id, addedAt = addedAt).copy(category = category)

    private fun folder(
        name: String,
        episodes: List<MediaSet>,
    ) = Entry.Collection(
        key = "DOCUMENTARY/$name", kind = CollectionKind.COURSE, name = name, posterPath = null, posterKey = null,
        count = episodes.size, chapters = episodes.size,
        divisions = listOf(Division(name, null, episodes, emptyList())),
    )

    @Test
    fun aCategoryRowMixesAFolderThatOpensAndASingleThatPlays() {
        val single = doc("solo", category = "Science")
        val group = folder("Terra X", listOf(doc("terra-e1", category = "Science")))
        val library = DocumentaryLibrary(collections = listOf(group), singles = listOf(single))
        val dept = documentariesDepartmentOf(library, emptyMap(), WatchSnapshot.Empty)!!
        var openedCollection: String? = null
        var played: String? = null

        show { TvDocumentariesDepartmentPage(dept, WatchSnapshot.Empty, onOpenCollection = { openedCollection = it }, onPlay = { played = it }) }

        compose.onNodeWithText("Science").assertIsDisplayed()
        // "Terra X" draws twice — the category row's own plate, and again
        // as the heading of its own dedicated folder row further down; only
        // the plate is clickable.
        compose.onNode(hasText("Terra X") and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        assert(openedCollection == "DOCUMENTARY/Terra X") { "expected the folder to open, got $openedCollection" }
        // "solo" draws on both the category row and Recently added (this
        // library's only two items are both recent arrivals) — either
        // plate plays the same title.
        compose.onAllNodesWithText("solo").onFirst().performSemanticsAction(SemanticsActions.OnClick)
        assert(played == "solo") { "expected the single to play, got played=$played" }
    }

    @Test
    fun recentlyAddedAndStandaloneRowsBothDrawAndPlayOnOk() {
        val library = DocumentaryLibrary(collections = emptyList(), singles = listOf(doc("solo")))
        val dept = documentariesDepartmentOf(library, emptyMap(), WatchSnapshot.Empty)!!
        var played: String? = null

        show { TvDocumentariesDepartmentPage(dept, WatchSnapshot.Empty, onOpenCollection = {}, onPlay = { played = it }) }

        compose.onNodeWithText("Recently added").assertIsDisplayed()
        compose.onNodeWithText("Standalone documentaries").assertIsDisplayed()
        // "solo" is both the library's one recent arrival and its one
        // standalone documentary, so it draws on both rows — either plate
        // plays the same title.
        compose.onAllNodesWithText("solo").onFirst().performSemanticsAction(SemanticsActions.OnClick)
        assert(played == "solo")
    }

    /** A folder holding more than its own preview offers "All N →", opening the collection — the web's own `deptRow` link. */
    @Test
    fun aFolderWithMoreThanItsPreviewOffersAnAllLink() {
        val episodes = (0 until 13).map { doc("terra-e$it") }
        val group = folder("Terra X", episodes)
        val library = DocumentaryLibrary(collections = listOf(group), singles = emptyList())
        val dept = documentariesDepartmentOf(library, emptyMap(), WatchSnapshot.Empty)!!
        var openedCollection: String? = null

        show { TvDocumentariesDepartmentPage(dept, WatchSnapshot.Empty, onOpenCollection = { openedCollection = it }, onPlay = {}) }
        compose.onNodeWithText("All 13 →").performSemanticsAction(SemanticsActions.OnClick)

        assert(openedCollection == "DOCUMENTARY/Terra X") { "expected the folder's own \"All\" link to open it, got $openedCollection" }
    }

    /**
     * The page's own restorer (`TvMoviesDepartmentPage`'s own doc) never
     * outranks a restore key naming a title further down — [singleOnly]'s
     * own age keeps it out of Recently added's top twelve entirely, so this
     * title exists on exactly one row and this test's own restore key can
     * only ever mean the Standalone one.
     */
    @Test
    fun aRestoreKeyNamingAStandaloneDocumentaryWinsOverThePagesOwnRestorer() {
        val singleOnly = doc("solo-old", addedAt = -100)
        val newer = (0 until 12).map { doc("new-$it", addedAt = (it + 1).toLong()) }
        val library = DocumentaryLibrary(collections = emptyList(), singles = listOf(singleOnly) + newer)
        val dept = documentariesDepartmentOf(library, emptyMap(), WatchSnapshot.Empty)!!

        val listState = LazyListState()

        show { TvDocumentariesDepartmentPage(dept, WatchSnapshot.Empty, onOpenCollection = {}, onPlay = {}, restoreKey = "solo-old", listState = listState) }

        compose.onNodeWithText("solo-old").assertIsFocused()
        // The hero, Recently added and Standalone: nothing is underway, so no Continue item for the scroll to count past.
        assertEquals(3, listState.layoutInfo.totalItemsCount)
    }
}
