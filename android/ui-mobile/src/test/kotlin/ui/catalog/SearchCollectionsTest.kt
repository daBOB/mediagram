package ui.catalog

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import catalog.SearchDestination
import catalog.SearchFilter
import catalog.SearchGroups
import model.WatchSnapshot
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Search's Collections part as `search-view.js` draws it: the same 4:3 cards as Collections, a franchise counted in films. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h2400dp")
class SearchCollectionsTest : BrowsePageTest() {
    private val groups =
        SearchGroups(
            films = emptyList(), matchedShows = emptyList(), episodes = emptyList(), animeFilms = emptyList(),
            matchedAnimeShows = emptyList(), animeEpisodes = emptyList(), documentaries = emptyList(), lessons = emptyList(),
            people = emptyList(),
            collections =
                listOf(
                    SearchDestination(SearchFilter.COLLECTIONS, "Dune", 2, "dune.jpg", "tmdb-5"),
                    SearchDestination(SearchFilter.COLLECTIONS, "Dune night", 1, null, "l1"),
                ),
            filters = listOf(SearchFilter.COLLECTIONS to 2),
        )

    private fun render(onOpenFranchise: (Long) -> Unit = {}, onOpenList: (String) -> Unit = {}) = show {
        SearchGroupsView(
            groups = groups, filter = SearchFilter.ALL, onFilterChange = {}, watch = WatchSnapshot.Empty,
            onOpenTitle = {}, onOpenCollection = {}, onPlay = {}, onOpenPerson = {},
            onOpenFranchise = onOpenFranchise, onOpenList = onOpenList,
        )
    }

    @Test fun aFranchiseCountsFilmsAndAListCountsTitlesOnFourByThreeCards() {
        render()
        compose.onNodeWithText("two films").assertExists()
        compose.onNodeWithText("one title").assertExists()
        val bounds = compose.onNode(hasText("DUNE") and hasClickAction()).getUnclippedBoundsInRoot()
        assertTrue(abs(bounds.width / bounds.height - 4f / 3f) < 0.02f, "expected 4:3, got ${bounds.width} x ${bounds.height}")
    }

    @Test fun eachCardOpensWhatItNames() {
        var franchise: Long? = null
        var list: String? = null
        render(onOpenFranchise = { franchise = it }, onOpenList = { list = it })
        compose.onNode(hasText("DUNE") and hasClickAction()).performClick()
        compose.onNode(hasText("DUNE NIGHT") and hasClickAction()).performClick()
        assertEquals(5L to "l1", franchise to list)
    }
}
