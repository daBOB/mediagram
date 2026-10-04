package ui.catalog

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import catalog.GenreIndexEntry
import catalog.MoviesDepartment
import model.WatchSnapshot
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The Movies department's genre tiles and its way into the whole shelf, as `renderMoviesDept` draws them. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h2400dp")
class MoviesDepartmentScreenTest : BrowsePageTest() {
    private val films = listOf(film("a"), film("b"), film("c"))
    private val department =
        MoviesDepartment(
            filmCount = 3, hours = 5, lead = null, featured = films,
            genres = listOf(GenreIndexEntry("Drama", 2, "drama.jpg")), acclaimed = emptyList(), recentlyAdded = emptyList(),
        )

    private fun render(onSeeAllFilms: () -> Unit = {}, onOpenGenre: (String) -> Unit = {}) = show {
        MoviesDepartmentScreen(
            department = department, films = films, watch = WatchSnapshot.Empty,
            onOpenTitle = {}, onOpenGenre = onOpenGenre, onOpenGenresIndex = {}, onOpenLatest = {},
            onSeeAllFilms = onSeeAllFilms, onPlay = {}, titleInfo = { null },
        )
    }

    @Test fun theGenreRowsTilesAreSixteenByEightAndOpenTheirGenre() {
        var opened: String? = null
        render(onOpenGenre = { opened = it })
        val tile = compose.onNode(hasText("Drama") and hasClickAction())
        val bounds = tile.getUnclippedBoundsInRoot()
        assertTrue(abs(bounds.width / bounds.height - 2f) < 0.02f, "expected 16:8, got ${bounds.width} x ${bounds.height}")
        assertTrue(bounds.contains(boundsOf("two titles")))
        tile.performClick()
        assertEquals("Drama", opened)
    }

    @Test fun theWholeShelfIsOfferedAtTheFootAsWellAsBesideFeatured() {
        var opened = 0
        render(onSeeAllFilms = { opened++ })
        compose.onNodeWithText("All 3 films").performClick()
        val foot = compose.onNodeWithText("All 3 films →")
        assertTrue(foot.getUnclippedBoundsInRoot().top > boundsOf("Drama").bottom, "expected the pill below the last row")
        foot.performClick()
        assertEquals(2, opened)
    }
}
