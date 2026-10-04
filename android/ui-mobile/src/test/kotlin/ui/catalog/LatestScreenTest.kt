package ui.catalog

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.unit.width
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import catalog.CollectionKind
import catalog.Entry
import catalog.Shelf
import model.WatchSnapshot
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Latest as `renderLatest` draws it: each part under its department's name, courses as a list rather than plates. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h2400dp")
class LatestScreenTest : BrowsePageTest() {
    private val shelves =
        listOf(
            Shelf("Movies", listOf(Entry.Film(film("old", addedAt = 1)), Entry.Film(film("new", addedAt = 2)))),
            Shelf("Series", listOf(collection("Engines", CollectionKind.SHOW))),
            Shelf("Tutorials", listOf(collection("Forex", CollectionKind.COURSE))),
        )

    private fun render(onOpenTitle: (String) -> Unit = {}, onOpenCollection: (String) -> Unit = {}) =
        show { LatestScreen(shelves, WatchSnapshot.Empty, emptySet(), columns = 3, onOpenTitle, onOpenCollection) }

    @Test fun eachPartIsHeadedByItsDepartmentsName() {
        render()
        compose.onNodeWithText("Latest").assertExists()
        compose.onNodeWithText("NEWEST ARRIVALS FIRST").assertExists()
        listOf("Movies", "Series", "Tutorials").forEach { compose.onNodeWithText(it).assertExists() }
        compose.onAllNodesWithText("Latest ", substring = true).assertCountEquals(0)
    }

    @Test fun theNewestFilmComesFirst() {
        render()
        assertTrue(boundsOf("Film new").left < boundsOf("Film old").left)
    }

    @Test fun coursesAreAListRowAcrossThePageNotAPlate() {
        var opened: String? = null
        render(onOpenCollection = { opened = it })
        compose.onNodeWithText("one lesson · one chapter").assertExists()
        // A plate in a three-across grid is under a third of the 352dp
        // content width; a list row spans all of it.
        val row = compose.onNode(hasText("Forex") and hasClickAction())
        assertTrue(row.getUnclippedBoundsInRoot().width >= 350.dp, "expected a full-width row, got ${row.getUnclippedBoundsInRoot().width}")
        row.performClick()
        assertEquals("COURSE/Forex", opened)
    }
}
