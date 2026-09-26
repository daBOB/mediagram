package ui.tv.catalog

import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import catalog.Entry
import catalog.allSetsById
import catalog.moviesDepartmentOf
import catalog.shelvesOf
import catalog.showsDepartmentOf
import model.Kind
import model.MediaSet
import model.WatchSnapshot
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [TvShowsDepartmentPage] and [TvMoviesDepartmentPage] over real
 * [showsDepartmentOf]/[moviesDepartmentOf] output — a wide screen and a
 * generous lazy-row cache window so every plate this checks is actually
 * composed, not just scrolled past.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1920dp-h1080dp")
class TvDepartmentPagesStateTest : TvScreenStateTest() {
    /** M2: a header row past the old six-plate Home limit shows every one of its own up-to-a-dozen stops, not just six. */
    @Test
    fun aPopularRowOfMoreThanSixShowsShowsAllOfThem() {
        val shows = (0 until 13).map { i -> set("s$i", Kind.EPISODE, "Ep", show = "Show %02d".format(i), addedAt = i.toLong(), episode = 1) }
        val dept = showsDepartmentOf(Kind.EPISODE, seriesEntriesOf(shows), allSetsById(shelvesOf(shows)), WatchSnapshot.Empty)!!

        show { TvShowsDepartmentPage(dept, WatchSnapshot.Empty, onOpenTitle = {}, onOpenCollection = {}) }

        for (i in 0..7) {
            compose.onAllNodesWithText("Show %02d".format(i)).fetchSemanticsNodes().let { assert(it.isNotEmpty()) { "Show %02d missing".format(i) } }
        }
    }

    /** M3: a focusable hero (a backdrop to show and a title to open) wins arrival focus over the header rows and the wall beneath it. */
    @Test
    fun aFocusableHeroTakesArrivalFocusOverTheWallsFirstPlate() {
        val lead = set("s0-e1", Kind.EPISODE, "Ep", show = "Lead Show", addedAt = 0, episode = 1).copy(backdropPath = "/bd0")
        val other = set("s1-e1", Kind.EPISODE, "Ep", show = "Other Show", addedAt = 1, episode = 1)
        val shows = listOf(lead, other)
        val dept = showsDepartmentOf(Kind.EPISODE, seriesEntriesOf(shows), allSetsById(shelvesOf(shows)), WatchSnapshot.Empty)!!

        show { TvShowsDepartmentPage(dept, WatchSnapshot.Empty, onOpenTitle = {}, onOpenCollection = {}) }

        compose.onNodeWithText("▶ Watch now").assertIsFocused()
    }

    /**
     * [DeptRow]/[GenreTileRow]'s own lazy rewrite (N9) still wires arrival
     * focus correctly: every film watched empties Featured and Acclaimed, so
     * Genres — [GenreTileRow], the row rewritten to a [LazyRow][androidx.compose.foundation.lazy.LazyRow] —
     * is the first non-empty row left, and its own tile takes the remote.
     */
    @Test
    fun theMoviesFrontPagesGenreRowTakesArrivalFocusWhenEarlierRowsAreEmpty() {
        val films = (0 until 3).map { i -> set("f$i", Kind.MOVIE, "Film $i", addedAt = i.toLong()).copy(genres = listOf("Action")) }
        val dept = moviesDepartmentOf(films) { true }!!

        show { TvMoviesDepartmentPage(dept = dept, onOpenTitle = {}, onPlay = {}, onOpenGenre = {}, onOpenAllFilms = {}) }

        compose.onNodeWithText("Action").assertIsFocused()
    }

    private fun seriesEntriesOf(sets: List<MediaSet>): List<Entry.Collection> =
        shelvesOf(sets).first { it.title == "Series" }.entries.filterIsInstance<Entry.Collection>()
}
