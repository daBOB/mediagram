package ui.catalog

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import catalog.CollectionKind
import catalog.PersonPage
import model.Person
import model.WatchSnapshot
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A person's page as `cast.js#renderPerson` draws it: the display-face head, "N in your library", then Films and Series. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h2400dp")
class PersonScreenTest : BrowsePageTest() {
    private val page =
        PersonPage(
            person = Person(personId = 7, name = "Ada Lovelace", portraitPath = null, titleKeys = emptyList()),
            films = listOf(film("a"), film("b")),
            shows = listOf(collection("Engines", CollectionKind.SHOW)),
        )

    private fun render(onOpenTitle: (String) -> Unit = {}, onOpenCollection: (String) -> Unit = {}) =
        show { PersonScreen(page, portraitPath = null, watch = WatchSnapshot.Empty, columns = 3, onOpenTitle, onOpenCollection) }

    @Test fun theHeadCountsTitlesTheWayTheWebDoesWithNoNoun() {
        render()
        compose.onNodeWithText("3 IN YOUR LIBRARY").assertExists()
        compose.onAllNodesWithText("titles in your library", substring = true, ignoreCase = true).assertCountEquals(0)
    }

    @Test fun theNameIsSetAtThePageHeadsDisplaySizeNotTheOldHeadline() {
        render()
        assertTrue(boundsOf("Ada Lovelace").height >= 34.dp, "expected the web's 2.2rem floor, got ${boundsOf("Ada Lovelace").height}")
    }

    @Test fun filmsAndSeriesEachHaveTheirOwnPartAndOpenWhatTheyName() {
        var title: String? = null
        var show: String? = null
        render(onOpenTitle = { title = it }, onOpenCollection = { show = it })
        compose.onNodeWithText("Films").assertExists()
        compose.onNodeWithText("Series").assertExists()
        compose.onNode(hasText("Film a") and hasClickAction()).performClick()
        compose.onNode(hasText("Engines") and hasClickAction()).performClick()
        assertEquals("a" to "SHOW/Engines", title to show)
    }
}
