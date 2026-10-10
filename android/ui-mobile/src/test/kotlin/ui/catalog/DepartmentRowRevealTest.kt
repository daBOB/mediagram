package ui.catalog

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import catalog.CollectionKind
import catalog.Department
import catalog.GenreIndexEntry
import catalog.MoviesDepartment
import catalog.ShowsDepartment
import catalog.Underway
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import model.WatchSnapshot
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A department's rows rise into place as they arrive (`department-hero.js:46`);
 * its hero stays still. A grid section rises once: the cells that arrive with
 * it rise, and the ones scrolled to after are already in. Read from
 * layer-mapped bounds on a paused clock; the fade is the device walk's.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h2400dp")
class DepartmentRowRevealTest : BrowsePageTest() {
    private fun showStill(content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        show(content)
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
    }

    private fun heroTop() = compose.onNodeWithTag(DEPT_HERO_TITLE_TEST_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot().top.value

    @Test
    fun aMoviesRowRisesAsItArrivesButTheHeroDoesNot() {
        val films = listOf(film("a"), film("b"))
        val department = MoviesDepartment(
            filmCount = 2, hours = 3, lead = null, featured = films,
            genres = listOf(GenreIndexEntry("Drama", 2, "drama.jpg")), acclaimed = emptyList(), recentlyAdded = emptyList(),
        )
        showStill {
            MoviesDepartmentScreen(
                department = department, films = films, watch = WatchSnapshot.Empty, onOpenTitle = {}, onOpenGenre = {},
                onOpenGenresIndex = {}, onOpenLatest = {}, onSeeAllFilms = {}, onPlay = {}, titleInfo = { null },
            )
        }
        val hero = heroTop()
        val heading = boundsOf("Featured Movies").top.value
        val genres = boundsOf("Drama").top.value
        compose.mainClock.advanceTimeBy(1_200)
        assertEquals(hero, heroTop(), 0.5f)
        assertTrue(heading - boundsOf("Featured Movies").top.value > 20f, "the row's heading should rise in")
        assertTrue(genres - boundsOf("Drama").top.value > 20f, "the genre row should rise in")
    }

    @Test
    @Config(qualifiers = "w400dp-h800dp")
    fun aGridSectionRisesOnceAndItsLaterCellsAreAlreadyIn() {
        val shows = (1..20).map { collection("Show %02d".format(it), CollectionKind.SHOW) }
        val department = ShowsDepartment(
            showCount = 20, itemCount = 20, lead = null, underway = Underway(emptyList(), emptyList(), 0, 0),
            categories = emptyList(), popular = emptyList(), newEpisodes = emptyList(), all = shows,
        )
        val grid = LazyGridState()
        showStill {
            ShowsDepartmentScreen(
                dept = Department.SERIES, department = department, watch = WatchSnapshot.Empty, heldIds = emptySet(),
                columns = 2, onOpenTitle = {}, onOpenCollection = {}, onPlay = {}, state = grid,
            )
        }
        val firstRising = boundsOf("Show 01").top.value
        compose.mainClock.advanceTimeBy(1_500)
        assertTrue(firstRising - boundsOf("Show 01").top.value > 20f, "the first cells should rise with their section")

        // Hero 0, "All shows" 1, cells from 2: index 16 is Show 15, lines below the first screen.
        compose.runOnUiThread { grid.requestScrollToItem(16) }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        val arrived = boundsOf("Show 15").top.value
        compose.mainClock.advanceTimeBy(1_200)
        assertEquals(arrived, boundsOf("Show 15").top.value, 0.5f)
    }
}
