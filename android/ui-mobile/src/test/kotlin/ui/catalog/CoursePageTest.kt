package ui.catalog

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import catalog.CollectionKind
import catalog.Division
import catalog.Entry
import model.Kind
import model.WatchSnapshot
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import uniffi.mediagram_core.TitleInfo
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A course's own page as the web's `course-view.js` draws it: the shelf
 * head — the course's name, how many lessons and documents it holds — over
 * its lessons, with no art, facts or overview above them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h2400dp")
class CoursePageTest : BrowsePageTest() {
    private val base = collection("A Course", CollectionKind.COURSE)
    private val welcome = base.divisions.single().items.single().copy(setId = "l1", title = "Welcome", genres = listOf("Education"))
    private val course =
        base.copy(
            posterPath = "/course.jpg",
            divisions =
                listOf(
                    Division("Basics", null, listOf(welcome, welcome.copy(setId = "d1", title = "Workbook", kind = Kind.DOCUMENT)), emptyList()),
                    Division("Deeper", null, listOf(welcome.copy(setId = "l2", title = "More")), emptyList()),
                ),
        )

    private fun render(
        entry: Entry.Collection = course,
        info: TitleInfo? = null,
        onPlay: (String) -> Unit = {},
    ) = show {
        CollectionScreen(
            collection = entry,
            info = info,
            watch = WatchSnapshot.Empty,
            heldIds = emptySet(),
            onOpenTitle = {},
            onOpenGenre = {},
            onPlay = onPlay,
        )
    }

    @Test fun theHeadNamesTheCourseAndCountsItsLessonsAndDocuments() {
        render()
        compose.onNode(hasText("A Course") and isHeading()).assertExists()
        compose.onNodeWithText("TWO LESSONS · ONE DOCUMENT").assertExists()
        assertTrue(boundsOf("A Course").height >= 34.dp, "expected the web's 2.2rem floor, got ${boundsOf("A Course").height}")
    }

    @Test fun theLessonsStartUnderTheHead() {
        render()
        assertTrue(boundsOf("TWO LESSONS · ONE DOCUMENT").bottom <= boundsOf("Basics").top, "the lessons should start under the head")
        compose.onNodeWithText("1. Welcome").assertExists()
        compose.onNodeWithText("Deeper").assertExists()
    }

    /** Even handed an overview, a tagline and art, the page shows none of them — the web's course page has no such header. */
    @Test fun noArtFactsOrOverviewSitAboveTheLessons() {
        render(info = TitleInfo(overview = "All about it.", tagline = "Learn it", genres = null, rating = 7.5, network = null, status = null))
        listOf("All about it.", "“Learn it”", "★ 7.5", "Education").forEach { compose.onNodeWithText(it).assertDoesNotExist() }
        // The name once, in the head — not again over a poster.
        assertEquals(1, compose.onAllNodes(hasText("A Course")).fetchSemanticsNodes().size)
    }

    @Test fun aLessonStillPlaysStraightAway() {
        var played: String? = null
        render(onPlay = { played = it })
        compose.onNodeWithText("1. More").performClick()
        assertEquals("l2", played)
    }

    /** A documentary collection opens as a course and counts documentaries, `countsUnder`'s own rule. */
    @Test fun aDocumentaryCollectionCountsDocumentaries() {
        val docs = course.copy(divisions = listOf(Division("All", null, listOf(welcome.copy(kind = Kind.DOCUMENTARY)), emptyList())))
        render(entry = docs)
        compose.onNodeWithText("ONE DOCUMENTARY").assertExists()
    }
}
