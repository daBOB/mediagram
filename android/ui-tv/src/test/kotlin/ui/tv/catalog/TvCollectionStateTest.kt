package ui.tv.catalog

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import catalog.CollectionKind
import catalog.Division
import catalog.Entry
import model.Kind
import model.MediaSet
import model.WatchSnapshot
import model.Watched
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [TvCollection] and [TvSeason]: a show of several seasons is a wall of
 * season plates, anything else the phone's indented rows, and a document
 * is a line that says why it does not open rather than a thing to press.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvCollectionStateTest : TvScreenStateTest() {
    @Test
    fun aShowOfSeveralSeasonsIsAWallOfSeasonsAndTheFirstIsFocused() {
        var opened: Division? = null
        val show = show(division("Season 1", 1, episode("e1", "Pilot")), division("Season 2", 2, episode("e2", "Return")))
        showCollection(show, onOpenSeason = { opened = it })

        compose.onNodeWithText("A Show").assertExists()
        compose.onNodeWithText("Season 1").assertIsFocused()
        compose.onNodeWithText("Season 2").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals("Season 2", opened?.title)
    }

    @Test
    fun aShowOfOneSeasonListsItsEpisodesAndFocusesTheFirst() {
        var opened: String? = null
        val show = show(division("Season 1", 1, episode("e1", "Pilot"), episode("e2", "Return")))
        showCollection(show, onOpenTitle = { opened = it }, watch = WatchSnapshot.Empty.copy(watched = listOf(Watched("e1", 1))))

        compose.onNodeWithText("1. ✓ Pilot").assertIsFocused()
        compose.onNodeWithText("2. Return").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals("e2", opened)
    }

    @Test
    fun comingBackLandsOnTheEpisodeThatWasOpened() {
        val show = show(division("Season 1", 1, episode("e1", "Pilot"), episode("e2", "Return")))
        showCollection(show, restoreKey = "e2")

        compose.onNodeWithText("2. Return").assertIsFocused()
    }

    @Test
    fun aCourseHeadsEachFolderAndADocumentSaysWhyItDoesNotOpen() {
        val handout = document("d1", "Workbook")
        val course =
            collection(
                CollectionKind.COURSE,
                "A Course",
                Division("Basics", null, listOf(handout, lesson("l1", "Welcome")), listOf(division("Deeper", null, lesson("l2", "More")))),
            )
        showCollection(course)

        compose.onNodeWithText("Basics").assertExists()
        compose.onNodeWithText("Deeper").assertExists()
        compose.onNodeWithText("Document — the television cannot open one yet").assertExists()
        val document = compose.onNodeWithText("1. Workbook", substring = true).fetchSemanticsNode()
        assertFalse(SemanticsActions.OnClick in document.config, "a document offers nothing to press")
        assertFalse(document.config.getOrElse(SemanticsProperties.Focused) { false })
        // The remote passes over the document to the first thing that opens.
        compose.onNodeWithText("2. Welcome").assertIsFocused()
    }

    @Test
    fun aCourseOfDocumentsOnlyFocusesItsFirstDocumentWithoutOfferingAPress() {
        val course = collection(CollectionKind.COURSE, "Handouts", division("Basics", null, document("d1", "Workbook"), document("d2", "Answers")))
        showCollection(course)

        compose.onNodeWithText("1. Workbook", substring = true).assertIsFocused()
        val node = compose.onNodeWithText("1. Workbook", substring = true).fetchSemanticsNode()
        assertFalse(SemanticsActions.OnClick in node.config, "a document offers nothing to press")
        assertTrue(SemanticsProperties.Disabled in node.config, "a document reads as disabled")
    }

    @Test
    fun aSeasonOfDocumentsOnlyFocusesItsFirstDocument() {
        show { TvSeason(division("Extras", null, document("d1", "Script"), document("d2", "Notes")), WatchSnapshot.Empty, onOpenTitle = {}) }

        compose.onNodeWithText("1. Script", substring = true).assertIsFocused()
    }

    @Test
    fun aSeasonListsItsOwnEpisodesAndFocusesTheFirst() {
        var opened: String? = null
        show { TvSeason(division("Season 2", 2, episode("e3", "Late"), episode("e4", "Later")), WatchSnapshot.Empty, onOpenTitle = { opened = it }) }

        compose.onNodeWithText("Season 2").assertExists()
        compose.onNodeWithText("1. Late").assertIsFocused()
        compose.onNodeWithText("2. Later").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals("e4", opened)
    }

    private fun showCollection(
        collection: Entry.Collection,
        watch: WatchSnapshot = WatchSnapshot.Empty,
        onOpenTitle: (String) -> Unit = {},
        onOpenSeason: (Division) -> Unit = {},
        restoreKey: String? = null,
    ) = show {
        TvCollection(
            collection = collection,
            info = null,
            watch = watch,
            posterPath = { null },
            onOpenTitle = onOpenTitle,
            onOpenSeason = onOpenSeason,
            restoreKey = restoreKey,
        )
    }

    private fun show(vararg divisions: Division) = collection(CollectionKind.SHOW, "A Show", *divisions)

    private fun collection(
        kind: CollectionKind,
        name: String,
        vararg divisions: Division,
    ) = Entry.Collection(
        key = "$kind/$name",
        kind = kind,
        name = name,
        posterPath = null,
        posterKey = null,
        count = divisions.sumOf { it.items.size },
        chapters = divisions.size,
        divisions = divisions.toList(),
    )

    private fun division(
        title: String,
        season: Int?,
        vararg items: MediaSet,
    ) = Division(title, season, items.toList(), emptyList())

    private fun episode(
        id: String,
        title: String,
    ) = set(id, Kind.EPISODE, title, show = "A Show", addedAt = 0)

    private fun document(
        id: String,
        title: String,
    ) = lesson(id, title).copy(kind = Kind.DOCUMENT)

    private fun lesson(
        id: String,
        title: String,
    ) = set(id, Kind.TUTORIAL, title, show = "A Course", addedAt = 0)
}
