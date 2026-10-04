package ui.catalog

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import model.ListOfSets
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertTrue

/** A list's own page opens as the web's `renderList` does: its name in the shelf head, how many titles it names, then its controls. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h2400dp")
class ListScreenTest : BrowsePageTest() {
    // Two named, one still in the library: the head counts what the list names, as the web's `list.items.length`.
    private val list = ListOfSets(id = "l1", name = "Sunday", items = listOf("a", "gone"))

    private fun render(sets: List<model.MediaSet> = listOf(film("a"))) =
        show {
            ListScreen(
                list = list,
                sets = sets,
                onPlay = {},
                onPlayAll = if (sets.isEmpty()) null else ({}),
                onRename = {},
                onDelete = {},
                onRemove = {},
            )
        }

    @Test fun theHeadNamesTheListAndCountsWhatItNames() {
        render()
        compose.onNode(hasHeading("Sunday")).assertExists()
        compose.onNodeWithText("TWO TITLES").assertExists()
        assertTrue(boundsOf("Sunday").height >= 34.dp, "expected the web's 2.2rem floor, got ${boundsOf("Sunday").height}")
    }

    @Test fun theHeadComesBeforeTheControlsAndTheTitles() {
        render()
        assertTrue(boundsOf("TWO TITLES").bottom <= boundsOf("Rename").top, "the head should sit above the controls")
        assertTrue(boundsOf("Rename").bottom <= boundsOf("Film a").top, "the controls should sit above the titles")
    }

    @Test fun anEmptyListStillOpensWithItsHead() {
        render(sets = emptyList())
        compose.onNode(hasHeading("Sunday")).assertExists()
        compose.onAllNodesWithText("Nothing on this list yet", substring = true).assertCountEquals(1)
    }

    private fun hasHeading(text: String) = hasText(text) and isHeading()
}
